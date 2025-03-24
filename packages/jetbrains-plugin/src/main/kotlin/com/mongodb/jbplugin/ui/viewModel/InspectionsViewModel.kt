package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.InspectionCategory
import com.mongodb.jbplugin.linting.QueryInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class InspectionsViewModel {
    private val mutableInsights = MutableStateFlow<List<QueryInsight<PsiElement, *>>>(emptyList())
    val insights = mutableInsights.asStateFlow()

    private val mutableOpenCategories = MutableStateFlow<InspectionCategory?>(null)
    val openCategories = mutableOpenCategories.asStateFlow()

    suspend fun addInsight(insight: QueryInsight<PsiElement, *>) {
        withContext(Dispatchers.IO) {
            val currentState = mutableInsights.value
            val withoutExistingInsight = currentState.filter { !areEquivalent(insight, it) }
            mutableInsights.emit(withoutExistingInsight + insight)
        }
    }

    suspend fun openCategory(category: InspectionCategory) {
        if (mutableOpenCategories.value == category) {
            mutableOpenCategories.emit(null)
        } else {
            mutableOpenCategories.emit(category)
        }
    }

    suspend fun visitQueryOfInsightInEditor(insight: QueryInsight<PsiElement, *>) {
        withContext(Dispatchers.EDT) {
            val vFile = insight.query.source.containingFile.virtualFile

            val manager = FileEditorManager.getInstance(insight.query.source.project)
            val editorOfFile = manager.selectedTextEditor?.takeIf { it.virtualFile == vFile }
                ?: manager.openTextEditor(OpenFileDescriptor(insight.query.source.project, vFile, 0), true)
                ?: return@withContext

            editorOfFile.caretModel.moveToOffset(insight.query.source.textOffset)
        }
    }

    private fun areEquivalent(a: QueryInsight<PsiElement, *>, b: QueryInsight<PsiElement, *>): Boolean {
        return a.description == b.description &&
            a.descriptionArguments == b.descriptionArguments &&
            a.inspection == b.inspection &&
            (a.query.source == b.query.source || a.query.source.isEquivalentTo(b.query.source))
    }
}
