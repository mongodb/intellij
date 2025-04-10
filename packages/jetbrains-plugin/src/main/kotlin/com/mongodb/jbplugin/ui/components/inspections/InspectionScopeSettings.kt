package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.intellij.openapi.util.text.StringUtil
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope.AllInsights
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope.CurrentFile
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope.CurrentQuery
import com.mongodb.jbplugin.inspections.analysisScope.AnalysisScope.RecommendedInsights
import com.mongodb.jbplugin.ui.components.utilities.ActionLink
import com.mongodb.jbplugin.ui.components.utilities.Separator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelMutator
import com.mongodb.jbplugin.ui.components.utilities.hooks.useViewModelState
import com.mongodb.jbplugin.ui.viewModel.AnalysisScopeViewModel
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.CollectingFiles
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.Done
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.InProgress
import com.mongodb.jbplugin.ui.viewModel.AnalysisStatus.NoAnalysis
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Composable
fun InspectionScopeSettings() {
    val currentScope by useViewModelState(AnalysisScopeViewModel::analysisScope, AnalysisScope.default())
    val analysisStatus by useViewModelState(AnalysisScopeViewModel::analysisStatus, AnalysisStatus.default())
    val changeScope by useViewModelMutator(AnalysisScopeViewModel::changeScope)
    val refreshAnalysis by useViewModelMutator(AnalysisScopeViewModel::refreshAnalysis)

    val callbacks = InspectionScopeSettingsCallbacks(
        onScopeChange = changeScope,
        onRefreshAnalysis = refreshAnalysis
    )

    CompositionLocalProvider(LocalInspectionScopeSettingsCallbacks provides callbacks) {
        _InspectionScopeSettings(currentScope, analysisStatus)
    }
}

@Composable
fun _InspectionScopeSettings(
    currentScope: AnalysisScope,
    analysisStatus: AnalysisStatus
) {
    val callbacks = useInspectionScopeCallbacks()

    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = useTranslation("side-panel.scope.insights") + " ")
            ActionLink(
                modifier = Modifier.testTag("InspectionScopeSettings::Refresh"),
                useTranslation("side-panel.scope.refresh")
            ) {
                callbacks.onRefreshAnalysis()
            }
        }

        InspectionScopeComboBox(currentScope)
        InspectionAnalysisProgress(analysisStatus)
    }
}

@Composable
private fun InspectionScopeComboBox(currentScope: AnalysisScope, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = useTranslation("side-panel.scope.scope-to"))
        ComboBox(
            modifier = Modifier.padding(start = 8.dp).fillMaxWidth().testTag("InspectionScopeComboBox"),
            labelText = useTranslation(currentScope.displayName)
        ) {
            Column {
                ScopeOption(AllInsights())
                ScopeOption(RecommendedInsights())
                Separator()
                ScopeOption(CurrentFile())
                ScopeOption(CurrentQuery())
            }
        }
    }
}

@Composable
private fun InspectionAnalysisProgress(analysisStatus: AnalysisStatus) {
    Box(Modifier.padding(top = 4.dp).height(16.dp)) {
        when (analysisStatus) {
            CollectingFiles -> Row {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).height(16.dp).width(16.dp).align(
                        Alignment.CenterVertically
                    ),
                    style = CircularProgressStyle(250.milliseconds, Color.Gray)
                )

                Text(useTranslation("side-panel.scope.status.collecting-files"))
            }
            is InProgress -> Row {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).height(16.dp).width(16.dp).align(
                        Alignment.CenterVertically
                    ),
                    style = CircularProgressStyle(250.milliseconds, Color.Gray)
                )

                Text(useTranslation("side-panel.scope.status.processing-files", analysisStatus.processedFiles.size, analysisStatus.allFiles.size))
            }
            is Done -> Row {
                Icon(
                    AllIconsKeys.General.GreenCheckmark,
                    "Done",
                    modifier = Modifier.padding(end = 8.dp).height(16.dp).width(16.dp).align(
                        Alignment.CenterVertically
                    )
                )

                val duration = StringUtil.formatDuration(analysisStatus.duration.toJavaDuration())
                Text(useTranslation("side-panel.scope.status.done", analysisStatus.fileCount, duration))
            }
            NoAnalysis -> {
            }
        }
    }
}

@Composable
fun ScopeOption(option: AnalysisScope) {
    val callbacks = useInspectionScopeCallbacks()

    Box(
        modifier = Modifier
            .testTag("InspectionScopeComboBox::Item::${option.javaClass.simpleName}")
            .clickable { callbacks.onScopeChange(option) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Text(useTranslation(option.displayName))
        }
    }
}

internal data class InspectionScopeSettingsCallbacks(
    val onScopeChange: (AnalysisScope) -> Unit = { _ -> },
    val onRefreshAnalysis: () -> Unit = {}
)

internal val LocalInspectionScopeSettingsCallbacks = compositionLocalOf { InspectionScopeSettingsCallbacks() }

@Composable
internal fun useInspectionScopeCallbacks(): InspectionScopeSettingsCallbacks {
    return LocalInspectionScopeSettingsCallbacks.current
}
