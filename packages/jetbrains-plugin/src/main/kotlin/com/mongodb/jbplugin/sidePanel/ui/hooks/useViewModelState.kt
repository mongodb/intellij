package com.mongodb.jbplugin.sidePanel.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.sidePanel.ui.composition.LocalCoroutineContext
import com.mongodb.jbplugin.sidePanel.ui.composition.LocalProject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
inline fun <reified V> useViewModel(): V {
    val project = LocalProject.current ?: throw IllegalStateException("Project is not set up. This is a bug and must not happen.")
    val viewModel by project.service<V>()
    return viewModel
}

@Composable
inline fun <reified V, reified S> useViewModelState(prop: (V) -> StateFlow<S>): State<S> {
    val coroutineContext = LocalCoroutineContext.current
    val viewModel = useViewModel<V>()
    return prop(viewModel).collectAsState(coroutineContext)
}

@Composable
inline fun <reified V, reified P> useViewModelMutator(crossinline prop: suspend V.(P) -> Unit): (P) -> Unit {
    val coroutineContext = LocalCoroutineContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel = useViewModel<V>()

    return { p ->
        coroutineScope.launch(coroutineContext) {
            prop(viewModel, p)
        }
    }
}
