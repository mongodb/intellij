package com.mongodb.jbplugin.inspections

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.mongodb.jbplugin.Inspection
import com.mongodb.jbplugin.QueryInsight
import com.mongodb.jbplugin.editor.services.MdbPluginDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.swing.event.CaretEvent

sealed interface AnalysisScope {
    val displayName: String
    fun<S, I: Inspection> getFilteredInsights(allInsights: List<QueryInsight<S, I>>): List<QueryInsight<S, I>>
    fun getScopedFiles(project: Project): List<VirtualFile>

    data class RecommendedScope(
      override val displayName: String = "Recommended Insights",
    ): AnalysisScope {
        override fun<S, I: Inspection> getFilteredInsights(allInsights: List<QueryInsight<S, I>>): List<QueryInsight<S, I>> {
            return emptyList()
        }

        override fun getScopedFiles(project: Project): List<VirtualFile> {
            val scope = GlobalSearchScope.projectScope(project)
            val javaFileType = FileTypeManager.getInstance().getStdFileType("JAVA")

            // Only collect Java files
            return FileTypeIndex.getFiles(javaFileType, scope).toList()
        }
    }

    data class ProjectScope(
      override val displayName: String = "All Insights",
    ): AnalysisScope {
        override fun<S, I: Inspection> getFilteredInsights(allInsights: List<QueryInsight<S, I>>): List<QueryInsight<S, I>> {
            return emptyList()
        }

        override fun getScopedFiles(project: Project): List<VirtualFile> {
            TODO("Not yet implemented")
        }
    }

    data class CurrentFileScope(
      override val displayName: String = "Current File",
    ): AnalysisScope {
        override fun<S, I: Inspection> getFilteredInsights(allInsights: List<QueryInsight<S, I>>): List<QueryInsight<S, I>> {
            return emptyList()
        }

        override fun getScopedFiles(project: Project): List<VirtualFile> {
            TODO("Not yet implemented")
        }
    }

    data class CurrentQueryScope(
        override val displayName: String = "Current Query",
    ): AnalysisScope {
        override fun<S, I: Inspection> getFilteredInsights(allInsights: List<QueryInsight<S, I>>): List<QueryInsight<S, I>> {
            return emptyList()
        }

        override fun getScopedFiles(project: Project): List<VirtualFile> {
            TODO("Not yet implemented")
        }
    }
}

@Service(Service.Level.PROJECT)
class AnalysisScopeViewModel(
    private val project: com.intellij.openapi.project.Project,
    private val coroutineScope: CoroutineScope,
): FileEditorManagerListener {
    init {
        val messageBusConnection = project.messageBus.connect(
          MdbPluginDisposable.getInstance(project)
        )
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    private val mutableAnalysisScope: MutableStateFlow<AnalysisScope> = MutableStateFlow(AnalysisScope.RecommendedScope())
    val analysisScope: StateFlow<AnalysisScope>
        get() = mutableAnalysisScope.asStateFlow()

    private val caretListener: CaretListener = object : CaretListener {
        override fun caretPositionChanged(event: com.intellij.openapi.editor.event.CaretEvent) {
            super.caretPositionChanged(event)
            println("Caret at ${event.caret}")
        }
    }

    fun selectScope(scope: AnalysisScope) {
        coroutineScope.launch {
            mutableAnalysisScope.emit(scope)
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        println("???????????????? AnalysisScopeViewModel Selection changed")
        super.selectionChanged(event)
        if (event.oldEditor is TextEditor) {
            val oldEditor = (event.oldEditor as TextEditor).editor
            oldEditor.caretModel.removeCaretListener(caretListener)
        }

        // Handle new editor - safely add caret listener
        if (event.newEditor is TextEditor) {
            val newEditor = (event.newEditor as TextEditor).editor
            newEditor.caretModel.addCaretListener(caretListener)
        }
    }
}
