package com.mongodb.jbplugin.codeActions.impl.runQuery

import com.intellij.collaboration.ui.selectFirst
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildBackground
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JPanel
import javax.swing.SwingConstants

class NamespaceSelector(
    private val project: Project,
    private val dataSource: LocalDataSource,
    private val coroutineScope: CoroutineScope,
) : JPanel() {
    sealed interface Event {
        data object DatabasesLoading : Event
        data class DatabasesLoaded(val databases: List<String>) : Event
        data class DatabaseSelected(val database: String) : Event
        data object CollectionsLoading : Event
        data class CollectionsLoaded(val collections: List<String>) : Event
        data class CollectionSelected(val collection: String) : Event
    }

    private val events: MutableSharedFlow<Event> = MutableSharedFlow(extraBufferCapacity = 1)

    private val databaseModel = DefaultComboBoxModel<String>(emptyArray())
    private val databaseComboBox = ComboBox<String?>(databaseModel)
    private val collectionModel = DefaultComboBoxModel<String>(emptyArray())
    private val collectionComboBox = ComboBox<String?>(collectionModel)

    val selectedDatabase: String?
        get() = databaseModel.selectedItem?.toString()

    val selectedCollection: String?
        get() = collectionModel.selectedItem?.toString()

    private val loadingDatabases: Boolean by events.latest(
        onNewEvent = { event, state ->
            if (event is Event.DatabasesLoading) {
                true
            } else if (event is Event.DatabasesLoaded) {
                false
            } else {
                state
            }
        },
        onChange = {},
        defaultValue = true
    )

    private val loadingCollections: Boolean by events.latest(
        onNewEvent = { event, state ->
            if (event is Event.CollectionsLoaded) {
                false
            } else if (event is Event.CollectionsLoading) {
                true
            } else {
                state
            }
        },
        onChange = {},
        defaultValue = false
    )

    init {
        add(databaseComboBox)
        add(collectionComboBox)

        databaseComboBox.isEnabled = false
        collectionComboBox.isEnabled = false

        databaseComboBox.prototypeDisplayValue = "XXXXXXXXXXXXXX"
        databaseComboBox.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)

        collectionComboBox.prototypeDisplayValue = "XXXXXXXXXXXXXX"
        collectionComboBox.putClientProperty(ANIMATION_IN_RENDERER_ALLOWED, true)

        databaseComboBox.setRenderer { _, value, index, _, _ -> renderDatabaseItem(value, index) }
        collectionComboBox.setRenderer { _, value, index, _, _ ->
            renderCollectionItem(value, index)
        }

        coroutineScope.launch {
            events.collect(::handleEvent)
        }

        databaseComboBox.addItemListener {
            coroutineScope.launchChildBackground {
                events.emit(Event.DatabaseSelected(it.item.toString()))
            }
        }

        collectionComboBox.addItemListener {
            coroutineScope.launchChildBackground {
                events.emit(Event.CollectionSelected(it.item.toString()))
            }
        }

        loadDatabases()
    }

    private fun loadDatabases() {
        coroutineScope.launchChildBackground {
            events.emit(Event.DatabasesLoading)
            val readModel by project.service<DataGripBasedReadModelProvider>()
            val result = readModel.slice(
                dataSource,
                ListDatabases.Slice
            )

            events.emit(Event.DatabasesLoaded(result.databases.map { it.name }))
        }
    }

    private fun handleEvent(event: Event) {
        when (event) {
            is Event.DatabasesLoading -> {
                databaseComboBox.isEnabled = false
            }

            is Event.DatabasesLoaded -> {
                databaseModel.removeAllElements()
                collectionModel.removeAllElements()
                databaseModel.addAll(event.databases)
                collectionModel.selectFirst()
                databaseComboBox.isEnabled = true
            }
            is Event.DatabaseSelected -> {
                coroutineScope.launchChildBackground {
                    events.emit(Event.CollectionsLoading)
                    val readModel by project.service<DataGripBasedReadModelProvider>()
                    val result = readModel.slice(
                        dataSource,
                        ListCollections.Slice(event.database)
                    )

                    events.emit(Event.CollectionsLoaded(result.collections.map { it.name }))
                }
            }
            is Event.CollectionsLoading -> {
                collectionModel.removeAllElements()
                collectionComboBox.isEnabled = false
            }

            is Event.CollectionsLoaded -> {
                collectionModel.addAll(event.collections)
                collectionModel.selectFirst()
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
