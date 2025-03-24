package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fleet.util.letIf
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
fun InspectionAccordion() {
    val sectionState = mutableStateListOf(
        SectionState("Performance Warnings", 0, false),
        SectionState("Correctness Warnings", 1, false),
        SectionState("Environment Mismatches", 2, false),
        SectionState("Other", 3, false)
    )

    val accordionCallbacks = InspectionAccordionCallbacks(
        onToggleSection = { section, newState ->
            sectionState.forEachIndexed { idx, value ->
                sectionState[idx] = value.copy(open = (value.name == section && newState))
            }
        }
    )

    CompositionLocalProvider(LocalInspectionAccordionCallbacks provides accordionCallbacks) {
        Column {
            sectionState.forEach { section ->
                val modifier = Modifier.animateContentSize()
                    .padding(vertical = 4.dp)
                    .letIf(section.open) { it.weight(1f) }

                InspectionAccordionSection(modifier, section.name, section.count, section.open) {}
            }
        }
    }
}

@Composable
fun InspectionAccordionSection(modifier: Modifier, title: String, count: Int, open: Boolean, body: @Composable () -> Unit) {
    val callbacks = useInspectionAccordionCallbacks()

    Column(modifier) {
        Row(Modifier.testTag("InspectionAccordionSection::Opener::$title").clickable { callbacks.onToggleSection(title, !open) }, verticalAlignment = Alignment.CenterVertically) {
            if (open) {
                Icon(AllIconsKeys.General.ArrowDown, contentDescription = title)
            } else {
                Icon(AllIconsKeys.General.ArrowRight, contentDescription = title)
            }

            Text(title, Modifier.padding(start = 8.dp))
            Text("($count)", color = Color.Gray, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Separator()
        }

        if (open) {
            Box(Modifier.testTag("InspectionAccordionSection::Body::$title")) {
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
    val onToggleSection: (String, Boolean) -> Unit = { _, _, -> }
)

internal val LocalInspectionAccordionCallbacks = compositionLocalOf { InspectionAccordionCallbacks() }

@Composable
internal fun useInspectionAccordionCallbacks(): InspectionAccordionCallbacks {
    return LocalInspectionAccordionCallbacks.current
}

internal data class SectionState(val name: String, val count: Int, val open: Boolean)
