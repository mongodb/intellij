package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.components.Service
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.linting.QueryInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class InspectionsViewModel {
    private val mutableInsights = MutableStateFlow<List<QueryInsight<PsiElement, *>>>(emptyList())
    val insights get() = mutableInsights.asStateFlow()

    suspend fun addInsight(insight: QueryInsight<PsiElement, *>) {
        withContext(Dispatchers.IO) {
            val currentState = mutableInsights.value
            val withoutExistingInsight = currentState.filter { !areEquivalent(insight, it) }
            mutableInsights.emit(withoutExistingInsight + insight)
        }
    }

    private fun areEquivalent(a: QueryInsight<PsiElement, *>, b: QueryInsight<PsiElement, *>): Boolean {
        return a.description == b.description &&
            a.descriptionArguments == b.descriptionArguments &&
            a.inspection == b.inspection &&
            (a.query.source == b.query.source || a.query.source.isEquivalentTo(b.query.source))
    }
}
