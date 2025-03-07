package com.mongodb.jbplugin.sidePanel.ui.composition

import androidx.compose.runtime.compositionLocalOf
import com.intellij.openapi.project.Project

val LocalProject = compositionLocalOf<Project?> { null }
