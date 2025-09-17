package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.jpa.jpb.model.ui.addItemSelectedListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.Companion.scaledToText
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.observability.useLogMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants

private val logger = logger<NamespaceSelector>()

class NamespaceSelector(
    private val project: Project,
    private val dataSource: LocalDataSource,
    coroutineScope: CoroutineScope,
) {
    sealed interface Event {
        data object DatabasesLoading : Event
        data class DatabasesLoaded(val databases: List<String>) : Event
        data class DatabaseSelected(val database: String) : Event
    }

    private val events: MutableSharedFlow<Event> = MutableSharedFlow()

    private val databaseModel = DefaultComboBoxModel<String>(emptyArray())
    val databaseComboBox = ComboBox(databaseModel)
    private val collectionModel = DefaultComboBoxModel<String>(emptyArray())
    val collectionComboBox = ComboBox(collectionModel)

    val selectedDatabase: String?
        get() = databaseModel.selectedItem?.toString()

    val selectedCollection: String?
        get() = collectionModel.selectedItem?.toString()

    private var loadingDatabases = false
    private var loadingCollections = false

    init {
        databaseComboBox.isEnabled = false
        collectionComboBox.isEnabled = false
        databaseComboBox.name = "DatabaseComboBox"
        collectionComboBox.name = "CollectionComboBox"

        databaseComboBox.prototypeDisplayValue = "XXXXXXXXXXXXXXXXXXXXX"
        databaseComboBox.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)

        collectionComboBox.prototypeDisplayValue = "XXXXXXXXXXXXXXXXXXXXX"
        collectionComboBox.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)

        databaseComboBox.setRenderer { _, value, index, _, _ -> renderDatabaseItem(value, index) }
        collectionComboBox.setRenderer { _, value, index, _, _ ->
            renderCollectionItem(value, index)
        }

        coroutineScope.launch(Dispatchers.IO) {
            events.collectLatest(::handleEvent)
        }

        databaseComboBox.addItemSelectedListener {
            coroutineScope.launch(Dispatchers.IO) {
                events.emit(Event.DatabaseSelected(it.item.toString()))
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            loadDatabases()
        }
    }

    private suspend fun loadDatabases() {
        events.emit(Event.DatabasesLoading)
        val readModel by project.service<DataGripBasedReadModelProvider>()
        val result = try {
            readModel.slice(
                dataSource,
                ListDatabases.Slice
            )
        } catch (e: Exception) {
            logger.warn(
                useLogMessage("Failed to get databases").build(),
                e
            )
            null
        }

        events.emit(
            Event.DatabasesLoaded(
                result?.databases?.map { it.name } ?: emptyList()
            )
        )
    }

    private suspend fun handleEvent(event: Event) {
        when (event) {
            is Event.DatabasesLoading -> {
                loadingDatabases = true
                databaseComboBox.isEnabled = false
            }

            is Event.DatabasesLoaded -> {
                loadingDatabases = false
                databaseModel.removeAllElements()
                collectionModel.removeAllElements()
                databaseModel.addAll(event.databases)
                databaseModel.selectedItem = event.databases.firstOrNull()
                databaseComboBox.isEnabled = true
            }

            is Event.DatabaseSelected -> {
                loadingCollections = true

                val readModel by project.service<DataGripBasedReadModelProvider>()
                val result = withContext(Dispatchers.IO) {
                    try {
                        readModel.slice(
                            dataSource,
                            ListCollections.Slice(event.database)
                        )
                    } catch (e: Exception) {
                        logger.warn(
                            useLogMessage("Failed to get collections").build(),
                            e
                        )
                        null
                    }
                }

                val collections = result?.collections?.map { it.name } ?: emptyList()
                loadingCollections = false
                collectionModel.removeAllElements()
                collectionModel.addAll(collections)
                collectionModel.selectedItem = collections.firstOrNull()
                collectionComboBox.isEnabled = true
            }
        }
    }

    private fun renderDatabaseItem(item: String?, index: Int): Component = if (item == null &&
        index == -1 &&
        loadingDatabases
    ) {
        JBLabel("Loading databases...", Icons.instance.loading.scaledToText(), SwingConstants.LEFT)
    } else {
        JBLabel(item ?: "", Icons.instance.databaseAutocompleteEntry, SwingConstants.LEFT)
    }

    private fun renderCollectionItem(item: String?, index: Int): Component = if (item == null &&
        index == -1 &&
        loadingCollections
    ) {
        JBLabel("Loading collections...", Icons.instance.loading.scaledToText(), SwingConstants.LEFT)
    } else {
        JBLabel(item ?: "", Icons.instance.collectionAutocompleteEntry, SwingConstants.LEFT)
    }
}
