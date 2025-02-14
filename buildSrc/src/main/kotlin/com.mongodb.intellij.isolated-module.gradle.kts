import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.support.delegates.TaskContainerDelegate.*
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("java")
    kotlin("jvm")
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val libs = the<LibrariesForLibs>()

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.coroutines.core)
    compileOnly(libs.kotlin.reflect)
    testImplementation(libs.testing.jupiter.engine)
    testImplementation(libs.testing.jupiter.params)
    testImplementation(libs.testing.jupiter.vintage.engine)
    testImplementation(libs.testing.mockito.core)
    testImplementation(libs.testing.mockito.kotlin)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.testing.testContainers.mongodb)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = libs.versions.java.target.get()
        targetCompatibility = libs.versions.java.target.get()
    }

    withType<Test> {
        useJUnitPlatform()

        extensions.configure(JacocoTaskExtension::class) {
            isJmx = true
            includes = listOf("com.mongodb.*")
            isIncludeNoLocationClasses = true
        }

        jacoco {
            toolVersion = libs.versions.jacoco.get()
            isScanForTestClasses = true
        }

        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED"
            )
        )
    }

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

tasks.register("checkSpecUpdates") {
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

        val codeChanged = changedFiles.any { it.startsWith("$baseDir/src/main/kotlin/") && it.endsWith(".kt") }
        val specChanged = changedFiles.any { it.startsWith(specsDir) }

        if (codeChanged && !specChanged) {
            logger.error("The specification is not up to date with the latest code changes.")
            logger.error("Please update the relevant files in $specsDir or add the 'skip-spec-check' label to the PR.")
            throw GradleException()
        }
    }
}
