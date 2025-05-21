package com.mongodb.jbplugin.inspections

import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.database.dialects.base.startOffset
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.mongodb.jbplugin.dialects.javadriver.glossary.findAllChildrenOfType
import com.mongodb.jbplugin.editor.CachedQueryService
import com.mongodb.jbplugin.i18n.InspectionsAndInlaysMessages
import com.mongodb.jbplugin.i18n.SidePanelMessages
import com.mongodb.jbplugin.inspections.correctness.MongoDbFieldDoesNotExist
import com.mongodb.jbplugin.inspections.correctness.MongoDbTypeMismatch
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbCollectionDoesNotExist
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbDatabaseDoesNotExist
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbNoCollectionSpecified
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbNoDatabaseInferred
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndex
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndexEffectively
import com.mongodb.jbplugin.inspections.quickfixes.LocalQuickFixBridge
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.NoCollectionSpecified
import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.linting.QueryInsightsHolder
import com.mongodb.jbplugin.linting.QueryInspection
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.viewModel.InspectionsViewModel
import com.mongodb.jbplugin.ui.viewModel.getToolShortName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

abstract class AbstractMongoDbInspectionGlobalTool(
    private val inspection: Inspection
) : GlobalInspectionTool() {
    override fun getGroupDisplayName() = SidePanelMessages.message(inspection.category.displayName)
    override fun getDisplayName() = inspection.getToolShortName()
    override fun getShortName(): String = inspection.javaClass.simpleName

    override fun isEnabledByDefault() = true
    override fun getStaticDescription() = inspection.getToolShortName()
    override fun worksInBatchModeOnly() = false

    companion object {
        fun toInspectionRunner(coroutineScope: CoroutineScope, tool: InspectionToolWrapper<*, *>): AbstractMongoDbInspectionBridge<*, *>? {
            return when (tool.shortName) {
                NotUsingIndex::class.simpleName -> MongoDbQueryNotUsingIndex(coroutineScope)
                NotUsingIndexEffectively::class.simpleName -> MongoDbQueryNotUsingIndexEffectively(coroutineScope)
                FieldDoesNotExist::class.simpleName -> MongoDbFieldDoesNotExist(coroutineScope)
                TypeMismatch::class.simpleName -> MongoDbTypeMismatch(coroutineScope)
                DatabaseDoesNotExist::class.simpleName -> MongoDbDatabaseDoesNotExist(coroutineScope)
                CollectionDoesNotExist::class.simpleName -> MongoDbCollectionDoesNotExist(coroutineScope)
                NoDatabaseInferred::class.simpleName -> MongoDbNoDatabaseInferred(coroutineScope)
                NoCollectionSpecified::class.simpleName -> MongoDbNoCollectionSpecified(coroutineScope)
                else -> null
            }
        }
    }
}

abstract class AbstractMongoDbInspectionBridge<Settings, I : Inspection>(
    private val coroutineScope: CoroutineScope,
    private val queryInspection: QueryInspection<Settings, I>,
    private val inspection: I
) : ExternalAnnotator<PsiFile, List<QueryInsight<PsiElement, I>>>(), ExternalAnnotatorBatchInspection {
    protected abstract fun buildSettings(query: Node<PsiElement>): Settings
    protected abstract fun emitFinishedInspectionTelemetryEvent(queryInsights: List<QueryInsight<PsiElement, I>>)
    protected open fun afterInsight(queryInsight: QueryInsight<PsiElement, I>) {}
    override fun getPairedBatchInspectionShortName(): String = inspection.javaClass.simpleName
    override fun getShortName(): String = inspection.javaClass.simpleName

    // 1st step: collect basic information to trigger the annotator
    // this runs in the EDT, so don't do anything slow
    override fun collectInformation(file: PsiFile): PsiFile? {
        val inspectionsViewModel by file.project.service<InspectionsViewModel>()
        coroutineScope.launch {
            inspectionsViewModel.flushOldInsightsFor(file, inspection)
        }
        return file
    }

    // 2nd step: generate insights, this can block because happens in a thread pool
    override fun doAnnotate(psiFile: PsiFile?): List<QueryInsight<PsiElement, I>>? {
        if (psiFile == null) return null // early return if no file is provided

        // get all relevant method calls
        val queryService by psiFile.project.service<CachedQueryService>()
        val inspectionsViewModel by psiFile.project.service<InspectionsViewModel>()

        val allQueriesInFile = withinReadActionBlocking {
            psiFile.findAllChildrenOfType(PsiMethodCallExpression::class.java)
                .mapNotNull { queryService.queryAt(it) }
                .distinctBy { it.source.startOffset }
        }

        val problemsHolder = InspectionViewModelQueryInsightsHolder<I>(inspectionsViewModel, mutableListOf())

        runCatching {
            runBlocking(Dispatchers.IO) {
                for (it in allQueriesInFile) {
                    ProgressManager.checkCanceled()

                    val settings = withinReadActionBlocking { buildSettings(it) }
                    queryInspection.run(it, problemsHolder, settings)
                }
            }
        }

        return problemsHolder.currentInsights()
    }

    // 3rd step (optional): show the warnings in the code editor
    // this is only necessary for open files
    override fun apply(
        file: PsiFile,
        queryInsights: List<QueryInsight<PsiElement, I>>?,
        annotationHolder: AnnotationHolder
    ) {
        if (queryInsights == null) return

        for (insight in queryInsights) {
            val problemDescription = InspectionsAndInlaysMessages.message(
                insight.description,
                *insight.descriptionArguments.toTypedArray()
            )
            val problemHighlightType = when (insight.inspection) {
                NotUsingIndex, NotUsingIndexEffectively -> HighlightSeverity.INFORMATION
                else -> HighlightSeverity.WARNING
            }
            var annotation = annotationHolder.newAnnotation(problemHighlightType, problemDescription)
                .range(insight.source)
                .needsUpdateOnTyping()

            val problemDescriptor = ProblemDescriptorImpl(
                insight.source,
                insight.source,
                insight.description,
                LocalQuickFixBridge.allQuickFixes(coroutineScope, insight),
                if (problemHighlightType == HighlightSeverity.INFORMATION) ProblemHighlightType.POSSIBLE_PROBLEM else ProblemHighlightType.WARNING,
                false,
                null,
                true
            )

            for (quickfix in LocalQuickFixBridge.allQuickFixes(coroutineScope, insight)) {
                annotation = annotation
                    .newLocalQuickFix(quickfix, problemDescriptor)
                    .registerFix()
            }
            annotation.create()
            afterInsight(insight)
        }

        emitFinishedInspectionTelemetryEvent(queryInsights)
    }
}

internal class InspectionViewModelQueryInsightsHolder<I : Inspection>(
    private val inspectionsViewModel: InspectionsViewModel,
    // We are also storing a shallow copy of the insights
    // so we can return them during the doAnnotate phase.
    private val insights: MutableList<QueryInsight<PsiElement, I>>
) : QueryInsightsHolder<PsiElement, I> {
    override suspend fun register(insight: QueryInsight<PsiElement, I>) {
        inspectionsViewModel.addInsight(insight)
        insights += insight
    }

    fun currentInsights() = insights
}
