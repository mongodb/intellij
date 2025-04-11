package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.linting.Inspection
import com.mongodb.jbplugin.linting.InspectionCategory
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.meta.withinReadActionBlocking
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.Card
import com.mongodb.jbplugin.ui.components.utilities.MoreActionItem
import com.mongodb.jbplugin.ui.components.utilities.MoreActionsButton
import com.mongodb.jbplugin.ui.components.utilities.Separator
import com.mongodb.jbplugin.ui.components.utilities.hooks.LocalProject
import com.mongodb.jbplugin.ui.components.utilities.hooks.useProject
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.AnalysisScopeViewModel
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import com.mongodb.jbplugin.ui.viewModel.InspectionsViewModel
import com.mongodb.jbplugin.ui.viewModel.getToolShortName
import com.mongodb.jbplugin.ui.viewModel.getToolWrapper
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.util.thenIf

@Composable
fun InspectionAccordion() {
    val project by useProject()
    val analysisScope by useViewModelState(AnalysisScopeViewModel::analysisScope, AnalysisScope.default())
    val analysisStatus by useViewModelState(AnalysisScopeViewModel::analysisStatus, AnalysisStatus.default())
    val allInsights by useViewModelState(InspectionsViewModel::insights, emptyList())
    val openCategory by useViewModelState(InspectionsViewModel::openCategories, null)
    val inspectionsWithStatus by useViewModelState(InspectionsViewModel::inspectionsWithStatus, emptyMap())

    val onOpenCategory by useViewModelMutator(InspectionsViewModel::openCategory)
    val onNavigateToQueryOfInsight by useViewModelMutator(InspectionsViewModel::visitQueryOfInsightInEditor)
    val onChangeScope by useViewModelMutator(AnalysisScopeViewModel::changeScope)
    val onEnableInspection by useViewModelMutator(InspectionsViewModel::enableInspection)
    val onDisableInspection by useViewModelMutator(InspectionsViewModel::disableInspection)
    val getInspectionDisplayName = { inspection: Inspection ->
        inspection.getToolWrapper(project)?.displayName ?: "Disabled MongoDB Inspection"
    }

    val accordionCallbacks = InspectionAccordionCallbacks(
        onToggleInspectionCategory = onOpenCategory,
        onNavigateToQueryOfInsight = onNavigateToQueryOfInsight,
        onChangeScope = onChangeScope,
        onEnableInspection = onEnableInspection,
        onDisableInspection = onDisableInspection,
        getInspectionDisplayName = getInspectionDisplayName
    )

    CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
        _InspectionAccordion(
            analysisScope = analysisScope,
            analysisStatus = analysisStatus,
            allInsights = allInsights,
            disabledInspections = useDisabledInspections(inspectionsWithStatus),
            openCategory = openCategory,
        )
    }
}

@Composable
fun _InspectionAccordion(
    analysisScope: AnalysisScope,
    analysisStatus: AnalysisStatus,
    allInsights: List<QueryInsight<PsiElement, *>>,
    disabledInspections: Set<Inspection>,
    openCategory: InspectionCategory?
) {
    val insights = useFilteredInsights(analysisScope, allInsights)
    val sectionState by derivedStateOf {
        InspectionCategory.entries.map {
            SectionState(
                category = it,
                insights = useInsightsOfCategory(insights, it),
                disabledInspections = disabledInspections.filter { inspection ->
                    inspection.category == it
                }
            )
        }
    }

    if (insights.isEmpty() && analysisStatus == AnalysisStatus.NoAnalysis) {
        NoInsightsNotification(analysisScope)
    } else {
        Column {
            sectionState.forEach { section ->
                val isOpen = section.category == openCategory

                val modifier = Modifier.animateContentSize()
                    .padding(vertical = 8.dp)
                    .thenIf(isOpen) { weight(1f) }

                InspectionAccordionSection(modifier, section.category, section.insights.size, isOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (insight in section.insights) {
                            InsightCard(insight)
                        }
                        if (section.disabledInspections.isNotEmpty()) {
                            DisabledInspectionCards(
                                disabledInspections = section.disabledInspections,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisabledInspectionCards(
    disabledInspections: List<Inspection>
) {
    var disabledInspectionsVisible by remember { mutableStateOf(false) }
    ActionLink(
        text = useTranslation(
            if (disabledInspectionsVisible) {
                "side-panel.InspectionAccordian.hide-disabled-inspections"
            } else {
                "side-panel.InspectionAccordian.show-disabled-inspections"
            }
        ),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    ) {
        disabledInspectionsVisible = !disabledInspectionsVisible
    }

    if (disabledInspectionsVisible) {
        disabledInspections.forEach {
            DisabledInspectionCard(it)
        }
    }
}

@Composable
private fun NoInsightsNotification(scope: AnalysisScope) {
    val callbacks = useInspectionAccordionCallbacks()

    Box(Modifier.testTag("NoInsightsNotification")) {
        when (scope) {
            is AnalysisScope.CurrentFile, is AnalysisScope.CurrentQuery -> {
                Column {
                    Text(useTranslation("side-panel.scope.no-insights-message.change-to-recommended.text"))
                    DefaultButton(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = { callbacks.onChangeScope(AnalysisScope.RecommendedInsights()) }
                    ) {
                        Text(useTranslation("side-panel.scope.no-insights-message.change-to-recommended.button"))
                    }
                }
            }

            is AnalysisScope.RecommendedInsights -> {
                Column {
                    Text(useTranslation("side-panel.scope.no-insights-message.change-to-all.text"))
                    DefaultButton(
                        modifier = Modifier.padding(top = 8.dp),
                        onClick = { callbacks.onChangeScope(AnalysisScope.AllInsights()) }
                    ) {
                        Text(useTranslation("side-panel.scope.no-insights-message.change-to-all.button"))
                    }
                }
            }

            else -> {
                Text(useTranslation("side-panel.scope.no-insights-message.generic.text"))
            }
        }
    }
}

@Composable
private fun InspectionAccordionSection(modifier: Modifier, category: InspectionCategory, count: Int, open: Boolean, body: @Composable () -> Unit) {
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

internal data class InspectionAccordionCallbacks(
    val onToggleInspectionCategory: (InspectionCategory) -> Unit = { _ -> },
    val onNavigateToQueryOfInsight: (QueryInsight<PsiElement, *>) -> Unit = { _ -> },
    val onChangeScope: (AnalysisScope) -> Unit = { _ -> },
    val onEnableInspection: (Inspection) -> Unit = { _ -> },
    val onDisableInspection: (Inspection) -> Unit = { _ -> },
    val getInspectionDisplayName: (Inspection) -> String = { "" },
)

internal val LocalInspectionAccordionCallbacks = compositionLocalOf { InspectionAccordionCallbacks() }

@Composable
private fun InsightCard(insight: QueryInsight<PsiElement, *>) {
    val callbacks = useInspectionAccordionCallbacks()
    InsightCardStructure(
        title = useTranslation(
            insight.description,
            *insight.descriptionArguments.toTypedArray()
        ),
        iconKey = AllIconsKeys.General.Warning,
        testTag = "InsightCard::${insight.description}::${queryLocation(insight.query)}",
        moreActionItems = listOf(
            MoreActionItem(label = useTranslation("insight.action.disable-inspection")) {
                callbacks.onDisableInspection(insight.inspection)
            }
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Card.secondaryBackgroundColor)
                .padding(8.dp)
        ) {
            LinkToQueryInsight(insight)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InsightActions(insight)
            }
        }
    }
}

@Composable
fun DisabledInspectionCard(
    inspection: Inspection
) {
    val callbacks = useInspectionAccordionCallbacks()
    val iconImage = Icons.disabledInspectionIcon
    InsightCardStructure(
        title = callbacks.getInspectionDisplayName(inspection),
        iconKey = null,
        iconImage = iconImage,
        iconWidth = iconImage.width.dp,
        iconHeight = iconImage.height.dp,
        testTag = "DisabledInspectionCard::${inspection.getToolShortName()}",
        moreActionItems = listOf(
            MoreActionItem(label = useTranslation("insight.action.enable-inspection")) {
                callbacks.onEnableInspection(inspection)
            }
        )
    )
}

@Composable
fun InsightCardStructure(
    title: String,
    iconKey: IconKey? = null,
    iconImage: ImageBitmap? = null,
    iconWidth: Dp = 16.dp,
    iconHeight: Dp = 16.dp,
    testTag: String,
    moreActionItems: List<MoreActionItem>,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Card.backgroundColor)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (iconKey != null) {
                Icon(
                    key = iconKey,
                    contentDescription = "Warning",
                    modifier = Modifier.size(width = iconWidth, height = iconHeight),
                )
            } else if (iconImage != null) {
                Icon(
                    iconImage,
                    contentDescription = "Disabled",
                    modifier = Modifier.size(width = iconWidth, height = iconHeight),
                )
            }

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = true)
            )

            MoreActionsButton(actions = moreActionItems, testTagPrefix = testTag)
        }

        if (content != null) {
            content()
        }
    }
}

@Composable
private fun LinkToQueryInsight(insight: QueryInsight<PsiElement, *>) {
    val callbacks = LocalInspectionAccordionCallbacks.current

    ActionLink(
        text = queryLocation(insight.query),
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    ) {
        callbacks.onNavigateToQueryOfInsight(insight)
    }
}

private fun queryLocation(query: Node<PsiElement>): String {
    return withinReadActionBlocking {
        val containingFile = query.source.containingFile
        val fileName = containingFile.name
        val lineNumber = containingFile.fileDocument.getLineNumber(query.source.textOffset) + 1

        "$fileName:$lineNumber"
    }
}

@Composable
private fun useInspectionAccordionCallbacks(): InspectionAccordionCallbacks {
    return LocalInspectionAccordionCallbacks.current
}

@Composable
private fun useFilteredInsights(analysisScope: AnalysisScope, allInsights: List<QueryInsight<PsiElement, *>>): List<QueryInsight<PsiElement, *>> {
    val project = LocalProject.current ?: return emptyList()
    return withinReadActionBlocking {
        analysisScope.getFilteredInsights(project, allInsights)
    }
}

private fun useInsightsOfCategory(allInsights: List<QueryInsight<PsiElement, *>>, category: InspectionCategory): List<QueryInsight<PsiElement, *>> {
    return allInsights.filter { it.inspection.category == category }.sortedBy { queryLocation(it.query) }
}

private fun useDisabledInspections(
    inspectionsWithStatus: Map<Inspection, Boolean>
): Set<Inspection> {
    return inspectionsWithStatus.filterValues { isEnabled -> !isEnabled }.keys
}

private data class SectionState(
    val category: InspectionCategory,
    val insights: List<QueryInsight<PsiElement, *>>,
    val disabledInspections: List<Inspection>,
)
