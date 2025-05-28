package com.mongodb.intellij

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

open class CheckSpecUpdatesTask @Inject constructor(
  private val execOperations: ExecOperations,
  private val projectLayout: ProjectLayout
) : DefaultTask() {
    init {
        group = "verification"
        description = "Fails if a Kotlin file changed but no specification file was updated."
    }

    @TaskAction
    fun performSpecCheck() {
        val rootDirFile = projectLayout.projectDirectory.asFile.parentFile
        val baseDirFile = projectLayout.projectDirectory.asFile
        val specsDirFile = baseDirFile.resolve("src/docs/")

        if (!specsDirFile.exists()) {
            logger.lifecycle("Skipping checkSpecUpdates: ${specsDirFile.absolutePath} does not exist.")
            return
        } else {
            logger.lifecycle("Verifying specifications in ${specsDirFile.absolutePath}")
        }

        val outputStream = ByteArrayOutputStream()
        execOperations.exec {
            workingDir = rootDirFile
            standardOutput = outputStream
            commandLine("git", "diff", "--name-only", "origin/main")
        }.assertNormalExitValue()

        val changedFiles = outputStream.toString().trim().lines().map { "$rootDirFile/$it" }

        logger.quiet("List of changed files")
        for (changedFile in changedFiles) {
            logger.quiet(changedFile)
        }

        val baseDirPath = baseDirFile.absolutePath
        val codeChanged = changedFiles.any { it.startsWith("$baseDirPath/src/main/kotlin/") && it.endsWith(".kt") }
        val specChanged = changedFiles.any { it.startsWith(specsDirFile.absolutePath) }

        if (codeChanged && !specChanged) {
            logger.error("The specification is not up to date with the latest code changes.")
            logger.error("Please update the relevant files in ${specsDirFile.absolutePath} or add the `skip-spec-check` label to the PR.")
            throw GradleException()
        }
    }
}
