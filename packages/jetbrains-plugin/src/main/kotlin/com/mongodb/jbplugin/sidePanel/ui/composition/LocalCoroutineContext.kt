package com.mongodb.jbplugin.sidePanel.ui.composition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
private val UI_COMPUTE = Dispatchers.IO.limitedParallelism(1)

@Composable
fun useCoroutineContext(): State<CoroutineContext> {
    return remember { derivedStateOf { UI_COMPUTE } }
}
