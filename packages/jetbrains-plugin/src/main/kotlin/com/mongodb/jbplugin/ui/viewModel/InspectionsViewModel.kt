package com.mongodb.jbplugin.ui.viewModel

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import com.mongodb.jbplugin.inspections.correctness.MongoDbFieldDoesNotExist
import com.mongodb.jbplugin.inspections.correctness.MongoDbTypeMismatch
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbCollectionDoesNotExist
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbDatabaseDoesNotExist
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbNoCollectionSpecified
import com.mongodb.jbplugin.inspections.environmentmismatch.MongoDbNoDatabaseInferred
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndex
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingIndexEffectively
import com.mongodb.jbplugin.inspections.performance.MongoDbQueryNotUsingProject
import com.mongodb.jbplugin.linting.ALL_MDB_INSPECTIONS
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.Inspection.CollectionDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.DatabaseDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.FieldDoesNotExist
import com.mongodb.jbplugin.linting.Inspection.NoCollectionSpecified
import com.mongodb.jbplugin.linting.Inspection.NoDatabaseInferred
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndex
import com.mongodb.jbplugin.linting.Inspection.NotUsingIndexEffectively
import com.mongodb.jbplugin.linting.Inspection.TypeMismatch
import com.mongodb.jbplugin.linting.InspectionCategory
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.containingFileOrNull
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.meta.withinReadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

fun Inspection.getToolShortName(): String {
    return when (this) {
        NotUsingIndex -> MongoDbQueryNotUsingIndex::class.simpleName!!
        NotUsingIndexEffectively -> MongoDbQueryNotUsingIndexEffectively::class.simpleName!!
        Inspection.NotUsingProject -> MongoDbQueryNotUsingProject::class.simpleName!!
        FieldDoesNotExist -> MongoDbFieldDoesNotExist::class.simpleName!!
        TypeMismatch -> MongoDbTypeMismatch::class.simpleName!!
        DatabaseDoesNotExist -> MongoDbDatabaseDoesNotExist::class.simpleName!!
        CollectionDoesNotExist -> MongoDbCollectionDoesNotExist::class.simpleName!!
        NoDatabaseInferred -> MongoDbNoDatabaseInferred::class.simpleName!!
        NoCollectionSpecified -> MongoDbNoCollectionSpecified::class.simpleName!!
    }
}

fun Inspection.getToolWrapper(
    project: Project,
    profile: InspectionProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
): InspectionToolWrapper<*, *>? {
    return profile.getInspectionTool(getToolShortName(), project)
}

@Service(Service.Level.PROJECT)
class InspectionsViewModel(
    val project: Project,
    val coroutineScope: CoroutineScope
) : ProfileChangeAdapter {
    private val mutableInsights = MutableStateFlow<List<QueryInsight<PsiElement, *>>>(emptyList())
    val insights = mutableInsights.asStateFlow()

    private val mutableOpenCategories = MutableStateFlow<InspectionCategory?>(null)
    val openCategories = mutableOpenCategories.asStateFlow()

    private val mutableInspectionsWithStatus = MutableStateFlow(getMdbInspectionsWithStatus())
    val inspectionsWithStatus = mutableInspectionsWithStatus.asStateFlow()

    init {
        val messageBus = project.messageBus.connect(MdbPluginDisposable.getInstance(project))
        messageBus.subscribe(ProfileChangeAdapter.TOPIC, this)

        val connectionStateViewModel by project.service<ConnectionStateViewModel>()
        connectionStateViewModel.connectionState
            .zip(insights) { state, insight -> state to insight }
            .onEach { refreshSidePanelStatusOnInsights(it.first, it.second) }
            .launchIn(coroutineScope)

        coroutineScope.launch(Dispatchers.IO) {
            inspectionsWithStatus.collectLatest { status ->
                flushInsightsForDisabledInspections(status)
            }
        }
    }

    override fun profileChanged(profile: InspectionProfile) {
        coroutineScope.launch(Dispatchers.IO) {
            mutableInspectionsWithStatus.emit(getMdbInspectionsWithStatus())
        }
    }

    suspend fun flushOldInsightsFor(psiFile: PsiFile, inspection: Inspection) {
        withContext(Dispatchers.IO) {
            withinReadAction {
                mutableInsights.update { currentInsights ->
                    currentInsights.filter { currentInsight ->
                        !(
                            currentInsight.inspection == inspection &&
                                currentInsight.query.containingFileOrNull?.isEquivalentTo(
                                    psiFile
                                ) == true
                            )
                        // also remove insights that are not attached to a file anymore
                    }.filter { it.query.containingFileOrNull != null }
                }
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutableInsights.emit(emptyList())
        }
    }

    private suspend fun flushInsightsForDisabledInspections(
        inspectionsWithStatus: Map<Inspection, Boolean>
    ) {
        val enabledInspections = inspectionsWithStatus.filterValues { it }.map { it.key }
        withContext(Dispatchers.IO) {
            mutableInsights.update { currentInsights ->
                currentInsights.filter { currentInsight ->
                    enabledInspections.contains(currentInsight.inspection)
                }
            }
        }
    }

    suspend fun addInsight(insight: QueryInsight<PsiElement, *>) {
        withContext(Dispatchers.IO) {
            val currentState = mutableInsights.value
            val withoutExistingInsight = withinReadAction {
                currentState.filter { !areEquivalent(insight, it) }
            }
            mutableInsights.emit(withoutExistingInsight + insight)
        }
    }

    suspend fun openCategory(category: InspectionCategory) {
        withContext(Dispatchers.IO) {
            mutableOpenCategories.emit(category)
        }
    }

    suspend fun toggleCategory(category: InspectionCategory) {
        withContext(Dispatchers.IO) {
            if (mutableOpenCategories.value == category) {
                mutableOpenCategories.emit(null)
            } else {
                mutableOpenCategories.emit(category)
            }
        }
    }

    suspend fun visitQueryOfInsightInEditor(insight: QueryInsight<PsiElement, *>) {
        val codeEditorViewModel by insight.query.source.project.service<CodeEditorViewModel>()
        codeEditorViewModel.focusQueryInEditor(insight.query)
    }

    fun enableInspection(inspection: Inspection) {
        val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
        profile.setToolEnabled(inspection.getToolShortName(), true)
    }

    fun disableInspection(inspection: Inspection) {
        val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
        profile.setToolEnabled(inspection.getToolShortName(), false)
    }

    @VisibleForTesting
    internal fun getMdbInspectionsWithStatus(): Map<Inspection, Boolean> {
        val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
        return ALL_MDB_INSPECTIONS.associateWith { inspection ->
            profile.isToolEnabled(
                HighlightDisplayKey.find(inspection.getToolShortName()),
                null
            )
        }
    }

    private fun areEquivalent(a: QueryInsight<PsiElement, *>, b: QueryInsight<PsiElement, *>): Boolean {
        return a.description == b.description &&
            a.descriptionArguments == b.descriptionArguments &&
            a.inspection == b.inspection &&
            (a.query.source == b.query.source || a.query.source.isEquivalentTo(b.query.source))
    }

    private fun refreshSidePanelStatusOnInsights(connectionState: ConnectionState, insights: List<QueryInsight<*, *>>) {
        val viewModel by project.service<SidePanelViewModel>()
        if (connectionState.selectedConnectionState is SelectedConnectionState.Initial ||
            connectionState.selectedConnectionState is SelectedConnectionState.Failed
        ) {
            viewModel.setStatus(SidePanelStatus.Warning)
        } else if (insights.isNotEmpty()) {
            viewModel.setStatus(SidePanelStatus.Warning)
        } else {
            viewModel.setStatus(SidePanelStatus.Ok)
        }
    }
}
