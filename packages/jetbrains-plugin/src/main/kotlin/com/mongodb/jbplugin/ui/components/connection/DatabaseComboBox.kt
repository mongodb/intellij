package com.mongodb.jbplugin.ui.components.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mongodb.jbplugin.ui.components.utilities.hooks.useTranslation
import com.mongodb.jbplugin.ui.viewModel.DatabasesLoadingState
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.ComboBox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun DatabaseComboBox(
    databasesLoadingState: DatabasesLoadingState,
    selectedDatabase: String?,
) {
    val scrollState = rememberScrollState()
    ComboBox(
        menuModifier = Modifier.verticalScroll(scrollState),
        modifier = Modifier.testTag("DatabaseComboBox"),
        labelText = selectedDatabase
            ?: if (databasesLoadingState is DatabasesLoadingState.Loading) {
                useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.loading.databases")
            } else {
                useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.choose-a-database")
            },
        outline = if (databasesLoadingState is DatabasesLoadingState.Failed) {
            Outline.Error
        } else {
            Outline.None
        }
    ) {
        Column {
            if (selectedDatabase != null) {
                UnselectItem()
            }

            for (database in databasesLoadingState.databases) {
                DatabaseItem(database)
            }
        }
    }
}

@Composable
internal fun UnselectItem() {
    val databaseCallbacks = useDatabaseCallbacks()

    Box(
        modifier = Modifier
            .testTag("UnselectItem")
            .clickable { databaseCallbacks.unselectSelectedDatabase() }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Icon(AllIconsKeys.General.Close, "", modifier = Modifier.padding(end = 8.dp))
            Text(useTranslation("side-panel.connection.ConnectionBootstrapCard.combobox.unselect"))
        }
    }
}

@Composable
internal fun DatabaseItem(database: String) {
    val databaseCallbacks = useDatabaseCallbacks()

    Box(
        modifier = Modifier
            .testTag("DatabaseItem::$database")
            .clickable { databaseCallbacks.selectDatabase(database) }
            .padding(4.dp)
            .fillMaxWidth(1f)
    ) {
        Row {
            Text(database)
        }
    }
}

@Composable
internal fun useDatabaseCallbacks(): DatabaseCallbacks {
    return LocalDatabaseCallbacks.current
}

internal data class DatabaseCallbacks(
    val selectDatabase: (String) -> Unit = {},
    val unselectSelectedDatabase: () -> Unit = {},
)

internal val LocalDatabaseCallbacks = compositionLocalOf { DatabaseCallbacks() }
