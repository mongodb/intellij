import com.mongodb.intellij.CheckSpecUpdatesTask
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
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
    testImplementation(libs.testing.kotlin.coroutines.test)
    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.testing.testContainers.mongodb)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = libs.versions.java.target.get()
        targetCompatibility = libs.versions.java.target.get()
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.java.target.get())
        }
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

tasks.register<CheckSpecUpdatesTask>("checkSpecUpdates")
