package com.mongodb.jbplugin.ui.components.inspections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.psi.PsiElement
import com.mongodb.jbplugin.inspections.insightAction.InsightAction
import com.mongodb.jbplugin.linting.QueryInsight
import com.mongodb.jbplugin.ui.components.utilities.hooks.useCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun InsightActions(insight: QueryInsight<PsiElement, *>) {
    val coroutineContext by useCoroutineContext()
    val coroutineScope = rememberCoroutineScope()

    val allActions = InsightAction.resolveAllActions(insight)
    if (allActions.isEmpty()) {
        return
    }

    val primaryAction = allActions.first()
    val secondaryActions = allActions.drop(1)

    if (secondaryActions.isEmpty()) {
        OutlinedButton(onClick = {
            coroutineScope.launch(coroutineContext) {
                primaryAction.apply(insight)
            }
        }) {
            Text(text = primaryAction.displayName)
        }
        return
    }

    DropdownButton(
        onClickButton = {
            coroutineScope.launch(coroutineContext) {
                primaryAction.apply(insight)
            }
        },
        buttonContent = {
            Text(text = primaryAction.displayName)
        },
        menuContent = {
            for (action in secondaryActions) {
                selectableItem(false, onClick = {
                    coroutineScope.launch(coroutineContext) {
                        action.apply(insight)
                    }
                }) {
                    Text(modifier = Modifier.padding(8.dp), text = action.displayName)
                }
            }
        }
    )
}

@Composable
internal fun DropdownButton(
    onClickButton: () -> Unit,
    buttonContent: @Composable () -> Unit,
    menuContent: MenuScope.() -> Unit
) {
    Row {
        Dropdown(content = {
            ActionButton(
                modifier = Modifier.offset(x = (-8).dp),
                onClick = onClickButton
            ) {
                buttonContent()
            }
        }, menuContent = {
            menuContent()
        })
    }
}
