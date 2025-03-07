package com.mongodb.jbplugin.sidePanel.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.sidePanel.ui.composition.useCoroutineContext
import com.mongodb.jbplugin.sidePanel.ui.composition.useProject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val FPS: Int = 30
val MAX_REFRESH_RATIO = 1.seconds / FPS

@Composable
inline fun <reified V> useViewModel(): State<V> {
    val project by useProject()
    val viewModel by project.service<V>()
    return remember { derivedStateOf { viewModel } }
}

@OptIn(FlowPreview::class)
@Composable
inline fun <reified V, reified S> useViewModelState(prop: (V) -> StateFlow<S>, initial: S): State<S> {
    val coroutineContext by useCoroutineContext()
    val viewModel by useViewModel<V>()
    return prop(viewModel).debounce(MAX_REFRESH_RATIO).collectAsState(initial, coroutineContext)
}

@Composable
inline fun <reified V, reified P> useViewModelMutator(crossinline prop: suspend V.(P) -> Unit): State<(P) -> Unit> {
    val coroutineContext by useCoroutineContext()
    val viewModel by useViewModel<V>()
    val coroutineScope = rememberCoroutineScope { coroutineContext }

    val callback = { p: P ->
        coroutineScope.launch {
            prop(viewModel, p)
        }
        Unit
    }

    return remember { derivedStateOf { callback } }
}
