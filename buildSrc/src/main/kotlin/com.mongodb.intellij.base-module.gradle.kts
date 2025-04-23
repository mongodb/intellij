import org.gradle.accessors.dm.LibrariesForLibs
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint")
}

version = rootProject.version
val libs = the<LibrariesForLibs>()

tasks {
    withType<JacocoReport> {
        reports {
            xml.required = true
            csv.required = false
            html.outputLocation = layout.buildDirectory.dir("reports/jacocoHtml")
        }

        executionData(
          files(withType(Test::class.java)).filter { it.name.endsWith(".exec") && it.exists() }
        )
    }

    register("checkSpecUpdates") {
        group = "verification"
        description = "Fails if a Kotlin file changed but no specification file was updated"

        doLast {
            val rootDir = "${rootDir.absolutePath}"
            val baseDir = "${project.projectDir.path}"
            val specsDir = "$baseDir/src/docs/"

            if (!File(specsDir).exists()) {
                logger.lifecycle("Skipping checkSpecUpdates: $specsDir does not exist.")
                return@doLast
            } else {
                logger.lifecycle("Verifying specifications.")
            }

            val outputStream = ByteArrayOutputStream()
            exec {
                workingDir = project.rootDir
                standardOutput = outputStream
                commandLine("git", "diff", "--name-only", "origin/main")
            }

            val changedFiles = outputStream.toString().trim().lines().map { "$rootDir/$it" }

            logger.quiet("List of changed files:")
            changedFiles.forEach { file ->
                logger.quiet(file)
            }

            val codeChanged =
                changedFiles.any { it.startsWith("$baseDir/src/main/kotlin/") && it.endsWith(".kt") }
            val specChanged = changedFiles.any { it.startsWith(specsDir) }

            if (codeChanged && !specChanged) {
                logger.error("The specification is not up to date with the latest code changes.")
                logger.error("Please update the relevant files in $specsDir or add the 'skip-spec-check' label to the PR.")
                throw GradleException()
            }
        }
    }
}

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.tool)
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)

    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }

    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
