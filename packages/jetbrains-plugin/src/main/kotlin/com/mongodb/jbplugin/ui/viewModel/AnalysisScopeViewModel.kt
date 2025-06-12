package com.mongodb.jbplugin.ui.viewModel

import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionGlobalTool
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.singleExecutionJob
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.NoAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinInstant
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface AnalysisStatus {
    data object NoAnalysis : AnalysisStatus
    data object CollectingFiles : AnalysisStatus
    data class InProgress(
        val allFiles: Set<String>,
        val processedFiles: Set<String>
    ) : AnalysisStatus

    data class Done(val fileCount: Int, val duration: Duration) : AnalysisStatus

    companion object {
        fun default(): AnalysisStatus = NoAnalysis
    }
}

private val log = logger<AnalysisScopeViewModel>()

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class AnalysisScopeViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope
) {
    private val mutableAnalysisScope = MutableStateFlow(AnalysisScope.default())
    private val mutableAnalysisStatus = MutableStateFlow(AnalysisStatus.default())

    val analysisScope = mutableAnalysisScope.asStateFlow()
    val analysisStatus = mutableAnalysisStatus.asStateFlow()

    private val changeScopeJob = coroutineScope.singleExecutionJob("AnalysisScopeViewModel::ChangeScope")

    init {
        val codeEditorViewModel by project.service<CodeEditorViewModel>()
        codeEditorViewModel.editorState.value.run { refreshAnalysisScopeIfNecessary() }

        // We are listening to relevant parts of EditorState in different CoroutineScopes because
        // we want to refresh the analysis of scope at different pace for different types of changes.

        // When the selected file changes, we want the analysis to happen right away and that why
        // in the Flow subscription below we see that there is no debounce of the flow values
        coroutineScope.launch {
            codeEditorViewModel.editorState
                .distinctUntilChangedBy {
                    Triple(it.focusedFile, it.focusedFiles, it.openFiles)
                }
                .collectLatest {
                    refreshAnalysisScopeIfNecessary()
                }
        }

        // When the carets in the EditorState change, that generally means either user is actively
        // modifying the query. In that situation we don't want to refresh analysis of Scope on
        // every keystroke and hence we debounce for a certain time and use the latest carets to
        // trigger the analysis.
        coroutineScope.launch {
            codeEditorViewModel.editorState
                .distinctUntilChangedBy {
                    Triple(it.focusedFile, it.focusedFiles, it.carets)
                }
                .debounce(500)
                .collectLatest {
                    refreshAnalysisScopeIfNecessary()
                }
        }

        val inspectionsViewModel by project.service<InspectionsViewModel>()
        coroutineScope.launch(Dispatchers.IO) {
            inspectionsViewModel.inspectionsWithStatus.collectLatest {
                refreshAnalysis()
            }
        }
    }

    suspend fun changeScope(scope: AnalysisScope) = changeScopeJob.launch(onCancel = {
        mutableAnalysisStatus.emit(NoAnalysis)
    }) {
        /**
         * We use ensureActive in specific points here because it will stop the execution of the
         * coroutine in that point when the job is cancelled. If we don't do that, the coroutine
         * will continue executing even if we cancel.
         */
        log.info(useLogMessage("Changed Scope to ${scope.displayName}").build())
        mutableAnalysisScope.emit(scope)
        mutableAnalysisStatus.emit(AnalysisStatus.CollectingFiles)
        log.info(useLogMessage("Collecting files...").build())
        ensureActive()

        val files = withinReadAction {
            scope.getAdditionalFilesInScope(project)
        }

        if (files.isEmpty()) {
            mutableAnalysisStatus.emit(NoAnalysis)
            return@launch
        }

        log.info(useLogMessage("Collected ${files.size} to process.").build())
        val start = Instant.now().toKotlinInstant()

        withinReadAction {
            mutableAnalysisStatus.emit(
                AnalysisStatus.InProgress(
                    files.map { it.path }.toSet(),
                    emptySet()
                )
            )
        }

        ensureActive()
        log.info(useLogMessage("Analysis in progress...").build())
        val tools = getEnabledToolWrappers()

        for (file in files) {
            ensureActive()
            val psiFile = withinReadAction { file.findPsiFile(project) }
            if (psiFile != null) {
                ensureActive()
                log.info(useLogMessage("Running inspection for file ${psiFile.name}").build())

                for (tool in tools) {
                    ensureActive()
                    val externalAnnotator = AbstractMongoDbInspectionGlobalTool.toInspectionRunner(coroutineScope, tool)
                    ensureActive()
                    val info = externalAnnotator?.collectInformation(psiFile)
                    ensureActive()
                    externalAnnotator?.doAnnotate(info)
                }

                val currentState = analysisStatus.value as? AnalysisStatus.InProgress ?: continue
                mutableAnalysisStatus.emit(currentState.copy(processedFiles = currentState.processedFiles + file.path))
            }
        }

        val end = Instant.now().toKotlinInstant()
        val duration = end - start
        mutableAnalysisStatus.emit(AnalysisStatus.Done(files.size, duration))
        log.info(useLogMessage("Analysis done!").build())
        delay(3.seconds)
        mutableAnalysisStatus.emit(NoAnalysis)
    }

    suspend fun refreshAnalysis() {
        changeScope(analysisScope.value.refreshed())
    }

    private fun getEnabledToolWrappers(): List<InspectionToolWrapper<*, *>> {
        val inspectionsViewModel by project.service<InspectionsViewModel>()
        return inspectionsViewModel.inspectionsWithStatus.value
            .filter { it.value }
            .mapNotNull { it.key.getToolWrapper(project) }
    }

    fun reanalyzeCurrentScopeAsync() {
        coroutineScope.launch {
            reanalyzeCurrentScope()
        }
    }

    suspend fun reanalyzeCurrentScope() {
        withContext(Dispatchers.IO) {
            mutableAnalysisScope.emit(mutableAnalysisScope.value)
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            codeEditorViewModel.reanalyzeRelevantEditors()
        }
    }

    suspend fun changeScopeToCurrentQuery() {
        withContext(Dispatchers.IO) {
            mutableAnalysisScope.emit(AnalysisScope.CurrentQuery())
        }
    }

    private fun refreshAnalysisScopeIfNecessary() {
        if (mutableAnalysisScope.value.needsRefreshOnEditorChange) {
            coroutineScope.launch { refreshAnalysis() }
        }
    }
}
