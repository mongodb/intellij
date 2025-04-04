package com.mongodb.jbplugin.ui.viewModel

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.inspections.correctness.MongoDbFieldDoesNotExist
import com.mongodb.jbplugin.inspections.correctness.MongoDbTypeMismatch
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndex
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndexEffectively
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadAction
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.observability.useLogMessage
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.NoAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

private val MDB_INSPECTIONS: Array<String> = arrayOf(
    MongoDbFieldDoesNotExist::class.simpleName!!,
    MongoDbTypeMismatch::class.simpleName!!,
    MongoDbQueryNotUsingIndex::class.simpleName!!,
    MongoDbQueryNotUsingIndexEffectively::class.simpleName!!
)

private val log = logger<AnalysisScopeViewModel>()

@Service(Service.Level.PROJECT)
class AnalysisScopeViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope
) {
    private val mutableAnalysisScope = MutableStateFlow(AnalysisScope.default())
    private val mutableAnalysisStatus = MutableStateFlow(AnalysisStatus.default())

    val analysisScope = mutableAnalysisScope.asStateFlow()
    val analysisStatus = mutableAnalysisStatus.asStateFlow()

    init {
        coroutineScope.launch {
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            codeEditorViewModel.editorState.value.run { refreshAnalysisScopeIfNecessary() }
            codeEditorViewModel.editorState.collectLatest { refreshAnalysisScopeIfNecessary() }
        }
    }

    suspend fun changeScope(scope: AnalysisScope) = withContext(Dispatchers.IO) {
        log.info(useLogMessage("Changed Scope to ${scope.displayName}").build())
        mutableAnalysisScope.emit(scope)
        mutableAnalysisStatus.emit(AnalysisStatus.CollectingFiles)
        log.info(useLogMessage("Collecting files...").build())
        val files = withinReadAction { scope.getAdditionalFilesInScope(project) }
        if (files.isEmpty()) {
            mutableAnalysisStatus.emit(NoAnalysis)
            return@withContext
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

        log.info(useLogMessage("Analysis in progress...").build())
        withinReadActionBlocking {
            val inspectionContext = InspectionManager.getInstance(project).createNewGlobalContext()
            val tools = getEnabledToolWrappers()

            for (file in files) {
                val psiFile = file.findPsiFile(project)
                if (psiFile != null) {
                    log.info(useLogMessage("Running inspection for file ${psiFile.name}").build())

                    for (tool in tools) {
                        InspectionEngine.runInspectionOnFile(psiFile, tool, inspectionContext)
                    }

                    val currentState = analysisStatus.value as? AnalysisStatus.InProgress ?: continue
                    mutableAnalysisStatus.tryEmit(currentState.copy(processedFiles = currentState.processedFiles + file.path))
                }
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
        changeScope(analysisScope.value)
    }

    private fun getEnabledToolWrappers(): List<InspectionToolWrapper<*, *>> {
        return MDB_INSPECTIONS.mapNotNull { inspection ->
            val profile = InspectionProfileManager.getInstance(project).currentProfile
            val toolWrapper = profile.getInspectionTool(inspection, project)
            toolWrapper?.takeIf {
                profile.isToolEnabled(
                    HighlightDisplayKey.find(inspection),
                    null
                )
            }
        }
    }

    suspend fun reanalyzeCurrentScope() {
        withContext(Dispatchers.IO) {
            mutableAnalysisScope.tryEmit(mutableAnalysisScope.value)
            val codeEditorViewModel by project.service<CodeEditorViewModel>()
            codeEditorViewModel.reanalyzeRelevantEditors()
        }
    }

    private fun refreshAnalysisScopeIfNecessary() {
        val currentScope = mutableAnalysisScope.value
        if (currentScope is AnalysisScope.CurrentFile) {
            mutableAnalysisScope.tryEmit(AnalysisScope.CurrentFile)
        }
    }
}
