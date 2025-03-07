package com.mongodb.jbplugin.sidePanel.ui.composition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import com.intellij.openapi.project.Project

val LocalProject = compositionLocalOf<Project?> { null }

@Composable
fun useProject(): State<Project> {
    val project = LocalProject.current ?: throw IllegalStateException("Project is not set up. This is a bug and must not happen.")
    return remember { derivedStateOf { project } }
}
