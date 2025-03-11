package com.mongodb.jbplugin.inspections

import com.intellij.openapi.components.Service
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.QueryInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class InspectionsViewModel {
    internal val insightsContext = Dispatchers.IO.limitedParallelism(1)
    internal val currentSessionInsights = mutableListOf<QueryInsight<PsiElement>>()
    internal val insights = MutableStateFlow(emptyList<QueryInsight<PsiElement>>())

    suspend fun addInsight(queryInsight: QueryInsight<PsiElement>) {
        withContext(insightsContext) {
            currentSessionInsights.add(queryInsight)
        }
    }
}
