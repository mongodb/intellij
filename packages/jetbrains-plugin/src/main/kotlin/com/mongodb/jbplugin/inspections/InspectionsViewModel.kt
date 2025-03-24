package com.mongodb.jbplugin.inspections

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.GlobalInspectionContextEx
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.database.util.common.replace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.rd.util.printlnError
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.QueryInsight
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.editor.models.ToolbarModel
import com.mongodb.jbplugin.editor.models.getToolbarModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val MDB_INSPECTIONS = listOf(
    "FieldCheckInspectionBridge",
    "IndexCheckInspectionBridge",
    "NameCheckInspectionBridge",
)

@Service(Service.Level.PROJECT)
class InspectionsViewModel<I : Inspection>(
    private val project: com.intellij.openapi.project.Project,
    private val coroutineScope: CoroutineScope,
) {
    internal val insightsContext = Dispatchers.IO.limitedParallelism(1)
    internal val allInsights = MutableStateFlow(emptyList<QueryInsight<PsiElement, I>>())

    suspend fun addInsight(queryInsight: QueryInsight<PsiElement, I>) {
        withContext(insightsContext) {
            val existingInsightIndex = allInsights.value.indexOfFirst { insight: QueryInsight<PsiElement, I> ->
                insight.inspection == queryInsight.inspection && insight.source.isEquivalentTo(queryInsight.source)
            }

            allInsights.emit(
                if (existingInsightIndex != -1) {
                    allInsights.value.mapIndexed { index, insight ->
                        if (index == existingInsightIndex) {
                            queryInsight
                        } else {
                            insight
                        }
                    }
                } else {
                    allInsights.value + queryInsight
                }
            )
        }
    }

    // To be called by ProjectActivity implementation
    fun init() {
        coroutineScope.launch {
            project.getService(ToolbarModel::class.java).toolbarState.combine(
              project.getService(AnalysisScopeViewModel::class.java).analysisScope
            ) { toolbarState, analysisScope ->
                Pair(toolbarState, analysisScope)
            }.collect { (toolbarState, analysisScope) ->
                if (toolbarState.selectedDataSource?.isConnected() == true) {
                    runInspection(analysisScope)
                }
            }
        }
    }

    fun enableInspection(shortName: String) {
        val profile = InspectionProfileManager.getInstance().currentProfile
        profile.setToolEnabled(shortName, true)
    }

    fun disableInspection(shortName: String) {
        val profile = InspectionProfileManager.getInstance().currentProfile
        profile.setToolEnabled(shortName, false)
    }

    private fun getEnabledToolWrappers(): List<InspectionToolWrapper<*, *>> {
        return MDB_INSPECTIONS.mapNotNull {
            val profile = InspectionProfileManager.getInstance().currentProfile
            val toolWrapper = profile.getInspectionTool(
              it, this.project
            )
            if (toolWrapper != null && profile.isToolEnabled(HighlightDisplayKey.find(it), null)) {
                toolWrapper
            } else {
                null
            }
        }
    }

    fun runInspection(scope: AnalysisScope) {
        try {
            val inspectionContext = InspectionManager.getInstance(project).createNewGlobalContext()
            ApplicationManager.getApplication().runReadAction {
                for(file in scope.getScopedFiles(project)) {
                    for (tool in getEnabledToolWrappers()) {
                        val psiFile = file.findPsiFile(project)
                        if (psiFile != null) {
                            val problemDescriptor = InspectionEngine.runInspectionOnFile(
                              psiFile,
                              tool,
                              inspectionContext
                            )
                            println("Problem descriptor: $problemDescriptor for file: ${file.name} and tool - ${tool.shortName}")
                        } else {
                            println("Cannot find psi file in ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            printlnError(e.toString())
        }
    }
}
