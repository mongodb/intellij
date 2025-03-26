package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.linting.InspectionCategory
import com.mongodb.jbplugin.linting.InspectionCategory.PERFORMANCE
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.AnalysisScopeViewModel
import com.mongodb.jbplugin.ui.viewModel.InspectionsViewModel
import fleet.util.letIf
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun InspectionAccordion() {
    val analysisScope by useViewModelState(AnalysisScopeViewModel::analysisScope, AnalysisScope.default())
    val allInsights by useViewModelState(InspectionsViewModel::insights, emptyList())
    val openCategory by useViewModelState(InspectionsViewModel::openCategories, null)
    val onOpenCategory by useViewModelMutator(InspectionsViewModel::openCategory)
    val onNavigateToQueryOfInsight by useViewModelMutator(InspectionsViewModel::visitQueryOfInsightInEditor)

    val accordionCallbacks = InspectionAccordionCallbacks(
        onToggleInspectionCategory = onOpenCategory,
        onNavigateToQueryOfInsight = onNavigateToQueryOfInsight
    )

    CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
        _InspectionAccordion(analysisScope, allInsights, openCategory)
    }
}

@Composable
fun _InspectionAccordion(
    analysisScope: AnalysisScope,
    allInsights: List<QueryInsight<PsiElement, *>>,
    openCategory: InspectionCategory?
) {
    val insights = useFilteredInsights(analysisScope, allInsights)

    val sectionState = mutableStateListOf(
        SectionState(PERFORMANCE, useInsightsOfCategory(insights, PERFORMANCE)),
    )

    Column {
        sectionState.forEach { section ->
            val isOpen = section.category == openCategory

            val modifier = Modifier.animateContentSize()
                .padding(vertical = 8.dp)
                .letIf(isOpen) { it.weight(1f) }

            InspectionAccordionSection(modifier, section.category, section.insights.size, isOpen) {
                Column {
                    for (insight in section.insights) {
                        InsightCard(insight)
                    }
                }
            }
        }
    }
}

@Composable
fun InspectionAccordionSection(modifier: Modifier, category: InspectionCategory, count: Int, open: Boolean, body: @Composable () -> Unit) {
    val callbacks = useInspectionAccordionCallbacks()
    val scrollState = rememberScrollState()

    Column(modifier) {
        Row(Modifier.testTag("InspectionAccordionSection::Opener::${category.name}").offset(x = (-4).dp).clickable { callbacks.onToggleInspectionCategory(category) }, verticalAlignment = Alignment.CenterVertically) {
            if (open) {
                Icon(AllIconsKeys.General.ArrowDown, contentDescription = useTranslation(category.displayName))
            } else {
                Icon(AllIconsKeys.General.ArrowRight, contentDescription = useTranslation(category.displayName))
            }

            Text(useTranslation(category.displayName), Modifier.padding(start = 8.dp))
            Text("($count)", color = Color.Gray, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Separator()
        }

        if (open) {
            Box(
                Modifier
                    .testTag("InspectionAccordionSection::Body::${category.name}")
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp)
            ) {
                body()
            }
        }
    }
}

@Composable
private fun Separator() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).border(BorderStroke(1.dp, Color.DarkGray)))
}

internal data class InspectionAccordionCallbacks(
    val onToggleInspectionCategory: (InspectionCategory) -> Unit = { _ -> },
    val onNavigateToQueryOfInsight: (QueryInsight<PsiElement, *>) -> Unit = { _ -> }
)

internal val LocalInspectionAccordionCallbacks = compositionLocalOf { InspectionAccordionCallbacks() }

@Composable
internal fun InsightCard(insight: QueryInsight<PsiElement, *>) {
    Column(
        modifier = Modifier
            .testTag("InsightCard::${insight.description}::${queryLocation(insight.query)}")
            .padding(bottom = 16.dp)
            .fillMaxWidth(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .padding(12.dp)
    ) {
        Row(Modifier.padding(bottom = 8.dp)) {
            Icon(AllIconsKeys.General.Warning, "Warning")
            Text(useTranslation(insight.description, *insight.descriptionArguments.toTypedArray()), modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x2B, 0x2D, 0x30))
                .padding(12.dp)
        ) {
            LinkToQueryInsight(insight)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InsightActions(insight)
            }
        }
    }
}

@Composable
private fun LinkToQueryInsight(insight: QueryInsight<PsiElement, *>) {
    val callbacks = LocalInspectionAccordionCallbacks.current

    ActionLink(text = queryLocation(insight.query)) {
        callbacks.onNavigateToQueryOfInsight(insight)
    }
}

private fun queryLocation(query: Node<PsiElement>): String {
    return withinReadActionBlocking {
        val fileName = query.source.containingFile.name
        val lineNumber = ApplicationManager.getApplication().runReadAction<Int> {
            query.source.containingFile.fileDocument.getLineNumber(
                query.source.textOffset
            ) + 1
        }

        "$fileName:$lineNumber"
    }
}

@Composable
internal fun useInspectionAccordionCallbacks(): InspectionAccordionCallbacks {
    return LocalInspectionAccordionCallbacks.current
}

@Composable
private fun useFilteredInsights(analysisScope: AnalysisScope, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
    return ApplicationManager.getApplication().runReadAction<List<QueryInsight<PsiElement, *>>> {
        analysisScope.getFilteredInsights(allInsights)
    }
}

@Composable
private fun useInsightsOfCategory(allInsights: List<QueryInsight<PsiElement, *>>, category: InspectionCategory): List<QueryInsight<PsiElement, *>> {
    return allInsights.filter { it.inspection.category == category }.sortedBy { queryLocation(it.query) }
}

internal data class SectionState(val category: InspectionCategory, val insights: List<QueryInsight<PsiElement, *>>)
