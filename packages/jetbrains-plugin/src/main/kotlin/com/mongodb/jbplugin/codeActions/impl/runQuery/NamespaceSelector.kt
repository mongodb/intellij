package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.i18n.Icons
import com.mongodb.jbplugin.i18n.Icons.scaledToText
import com.mongodb.jbplugin.meta.latest
import com.mongodb.jbplugin.meta.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants

class NamespaceSelector(
    private val project: Project,
    private val dataSource: LocalDataSource,
    coroutineScope: CoroutineScope,
) {
    sealed interface Event {
        data object DatabasesLoading : Event
        data class DatabasesLoaded(val databases: List<String>) : Event
        data class DatabaseSelected(val database: String) : Event
        data object CollectionsLoading : Event
        data class CollectionsLoaded(val collections: List<String>) : Event
        data class CollectionSelected(val collection: String) : Event
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

    private val loadingDatabases: Boolean by events.latest(
        onNewEvent = { event, state ->
            when (event) {
                is Event.DatabasesLoading -> true
                is Event.DatabasesLoaded -> false
                else -> state
            }
        },
        onChange = {},
        defaultValue = true
    )

    private val loadingCollections: Boolean by events.latest(
        onNewEvent = { event, state ->
            when (event) {
                is Event.CollectionsLoading -> true
                is Event.CollectionsLoaded -> false
                else -> state
            }
        },
        onChange = {},
        defaultValue = false
    )

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

        databaseComboBox.addItemListener {
            runBlocking {
                events.emit(Event.DatabaseSelected(it.item.toString()))
            }
        }

        collectionComboBox.addItemListener {
            runBlocking {
                events.emit(Event.CollectionSelected(it.item.toString()))
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            loadDatabases()
        }
    }

    private suspend fun loadDatabases() {
        events.emit(Event.DatabasesLoading)
        val readModel by project.service<DataGripBasedReadModelProvider>()
        val result = readModel.slice(
            dataSource,
            ListDatabases.Slice
        )

        events.emit(Event.DatabasesLoaded(result.databases.map { it.name }))
    }

    private suspend fun handleEvent(event: Event) {
        when (event) {
            is Event.DatabasesLoading -> withContext(Dispatchers.EDT) {
                databaseComboBox.isEnabled = false
            }

            is Event.DatabasesLoaded -> withContext(Dispatchers.EDT) {
                databaseModel.removeAllElements()
                collectionModel.removeAllElements()
                databaseModel.addAll(event.databases)
                databaseModel.selectedItem = event.databases.firstOrNull()
                databaseComboBox.isEnabled = true
            }
            is Event.DatabaseSelected -> withContext(Dispatchers.IO) {
                events.tryEmit(Event.CollectionsLoading)

                val readModel by project.service<DataGripBasedReadModelProvider>()
                val result = readModel.slice(
                    dataSource,
                    ListCollections.Slice(event.database)
                )

                events.emit(Event.CollectionsLoaded(result.collections.map { it.name }))
            }
            is Event.CollectionsLoading -> withContext(Dispatchers.EDT) {
                collectionModel.removeAllElements()
                collectionComboBox.isEnabled = false
            }

            is Event.CollectionsLoaded -> withContext(Dispatchers.EDT) {
                collectionModel.addAll(event.collections)
                collectionModel.selectedItem = event.collections.firstOrNull()
                collectionComboBox.isEnabled = true
            }
            else -> {}
        }
    }

    private fun renderDatabaseItem(item: String?, index: Int): Component = if (item == null &&
        index == -1 &&
        loadingDatabases
    ) {
        JBLabel("Loading databases...", Icons.loading.scaledToText(), SwingConstants.LEFT)
    } else {
        JBLabel(item ?: "", Icons.databaseAutocompleteEntry, SwingConstants.LEFT)
    }

    private fun renderCollectionItem(item: String?, index: Int): Component = if (item == null &&
        index == -1 &&
        loadingCollections
    ) {
        JBLabel("Loading collections...", Icons.loading.scaledToText(), SwingConstants.LEFT)
    } else {
        JBLabel(item ?: "", Icons.collectionAutocompleteEntry, SwingConstants.LEFT)
    }
}
