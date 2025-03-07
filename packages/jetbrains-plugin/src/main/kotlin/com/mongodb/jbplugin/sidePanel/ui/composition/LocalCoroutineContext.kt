package com.mongodb.jbplugin.sidePanel.ui.composition

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
private val UI = Dispatchers.IO.limitedParallelism(1)

val LocalCoroutineContext = compositionLocalOf<CoroutineContext> { UI }
