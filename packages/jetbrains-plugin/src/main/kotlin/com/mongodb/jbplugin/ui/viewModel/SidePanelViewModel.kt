package com.mongodb.jbplugin.ui.viewModel

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.mongodb.jbplugin.ui.components.MDB_SIDEPANEL_ID
import com.mongodb.jbplugin.ui.viewModel.SidePanelEvents.OpenConnectionComboBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

interface SidePanelEvents {
    data object OpenConnectionComboBox : SidePanelEvents
}

@Service(Service.Level.PROJECT)
class SidePanelViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sidePanelEventsDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val mutableSidePanelEvents = MutableSharedFlow<SidePanelEvents>(
        replay = 0,
    )

    fun subscribeToSidePanelEvents(cb: suspend (SidePanelEvents) -> Unit) {
        coroutineScope.launch(sidePanelEventsDispatcher) {
            mutableSidePanelEvents.collectLatest { event -> cb(event) }
        }
    }

    fun openSidePanel() {
        ToolWindowManager.getInstance(project).getToolWindow(MDB_SIDEPANEL_ID)?.show()
    }

    fun openConnectionComboBox() {
        coroutineScope.launch(sidePanelEventsDispatcher) {
            // A small delay just in case SidePanel is still opening up.
            // Unfortunately there is no way around this even with listening to ToolWindow.shown
            // event because that event still emits way before ToolWindow actually appears.
            delay(250.milliseconds)
            mutableSidePanelEvents.emit(OpenConnectionComboBox)
        }
    }
}
