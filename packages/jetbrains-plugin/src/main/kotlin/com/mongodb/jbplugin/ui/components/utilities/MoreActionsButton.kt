package com.mongodb.jbplugin.ui.components.utilities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

data class MoreActionItem(
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun MoreActionsButton(actions: List<MoreActionItem>) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box {
        IconButton(
            onClick = { showMenu = true },
            interactionSource = interactionSource,
        ) {
            Icon(
                key = AllIconsKeys.Actions.More,
                contentDescription = "More actions"
            )
        }

        if (showMenu) {
            PopupContainer(
                onDismissRequest = { showMenu = false },
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(180.dp)
            ) {
                // A small vertical padding on the vertical side helps retain the full size of the
                // hover effect on each row because we have rounded borders on the container.
                Column(Modifier.padding(vertical = 2.dp)) {
                    actions.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    action.onClick()
                                    showMenu = false
                                }
                                .padding(vertical = 12.dp, horizontal = 20.dp),
                        ) {
                            Text(
                                text = action.label,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
