import com.mongodb.intellij.IntelliJPluginBundle
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask

plugins {
    id("com.mongodb.intellij.isolated-module")
    id("org.gradle.test-retry")
    id("org.jetbrains.intellij")
    id("me.champeau.jmh")
    id("io.morethan.jmhreport")
    id("org.jetbrains.changelog")
}

repositories {
    mavenCentral()

    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

val libs = the<LibrariesForLibs>()

val pluginBundle: IntelliJPluginBundle =
    project.extensions.create(
        "pluginBundle",
        IntelliJPluginBundle::class.java
    )

pluginBundle.enableBundle.convention(false)

intellij {
    version.set(libs.versions.intellij.min) // Target IDE Version
    type.set(libs.versions.intellij.type) // Target IDE Platform

    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
}

project.afterEvaluate {
    if (pluginBundle.enableBundle.get() == false) {
        tasks.filter { it.group == "intellij" && (
          it.name.startsWith("runIde") ||
          it.name.startsWith("verify") ||
          it.name.startsWith("build") ||
          it.name.startsWith("publish") ||
          it.name.startsWith("sign")
          )}.forEach {
            it.enabled = false
        }
    }
}

dependencies {
    jmh(libs.kotlin.stdlib)
    jmh(libs.testing.jmh.core)
    jmh(libs.testing.jmh.annotationProcessor)
    jmh(libs.testing.jmh.generatorByteCode)

    testImplementation(libs.testing.intellij.ideImpl)
    testImplementation(libs.testing.intellij.coreUi)

    testImplementation(libs.mongodb.driver)
    testImplementation(libs.testing.spring.mongodb)
    testImplementation(libs.testing.jsoup)
    testImplementation(libs.testing.video.recorder)
    testImplementation(libs.testing.assertj.swing)
    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.mongodb)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.owasp.encoder)

    testImplementation(libs.testing.intellij.testingFramework) {
        exclude("ai.grazie.spell")
        exclude("ai.grazie.utils")
        exclude("ai.grazie.nlp")
        exclude("ai.grazie.model")
        exclude("org.jetbrains.teamcity")
    }
}

jmh {
    benchmarkMode.set(listOf("thrpt"))
    iterations.set(10)
    timeOnIteration.set("6s")
    timeUnit.set("s")

    warmup.set("1s")
    warmupIterations.set(3)
    warmupMode.set("INDI")
    fork.set(1)
    threads.set(1)
    failOnError.set(false)
    forceGC.set(true)

    humanOutputFile.set(rootProject.layout.buildDirectory.file("reports/jmh/human.txt"))
    resultsFile.set(rootProject.layout.buildDirectory.file("reports/jmh/results.json"))
    resultFormat.set("json")
    profilers.set(listOf("gc"))

    zip64.set(true)
}

jmhReport {
    jmhResultPath =
        rootProject.layout.buildDirectory
            .file("reports/jmh/results.json")
            .get()
            .asFile.absolutePath

    jmhReportOutput =
        rootProject.layout.buildDirectory
            .dir("reports/jmh/")
            .get()
            .asFile.absolutePath
}

tasks {
    if (pluginBundle.enableBundle.get() == true) {
        register("buildProperties", WriteProperties::class) {
            group = "build"

            val segmentApiKey = System.getenv("BUILD_SEGMENT_API_KEY")
            if (segmentApiKey == null) {
                throw GradleException("Environment variable 'BUILD_SEGMENT_API_KEY' is not set. For local builds set it to empty.")
            }

            destinationFile.set(project.layout.projectDirectory.file("src/main/resources/build.properties"))
            property("pluginVersion", rootProject.version)
            property("segmentApiKey", segmentApiKey)
        }
    }

    named("test", Test::class) {
        useJUnitPlatform {
            excludeTags("UI")
        }

        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        val homePath =
            project.layout.buildDirectory
                .dir("idea-sandbox/config-test")
                .get()
                .asFile.absolutePath

        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "-Dpolyglot.log.level=OFF",
                "-Didea.home.path=$homePath",
            ),
        )
    }

    if (pluginBundle.enableBundle.get() == true) {
        withType<ProcessResources> {
            dependsOn("buildProperties")
        }
    }

    patchPluginXml {
        // minimum version that our plugin works with
        sinceBuild.set(libs.versions.intellij.minRelease)
        // maximum version that our plugin is expected to work with
        // setting this to empty string results in `<idea-version>` tag generated without until-build attribute
        // which essentially means that we are expected to work with all the future releases of the IDE
        // References: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#build-number-format
        untilBuild.set("")
        version.set(rootProject.version.toString())

        changeNotes.set(
            provider {
                changelog.renderItem(
                    changelog
                        .getUnreleased()
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            },
        )
    }

    signPlugin {
        certificateChain.set(System.getenv("JB_CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("JB_PRIVATE_KEY"))
        password.set(System.getenv("JB_PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        channels =
            when (System.getenv("JB_PUBLISH_CHANNEL")) {
                "ga" -> listOf("Stable")
                "beta" -> listOf("beta")
                else -> listOf("eap")
            }
        token.set(System.getenv("JB_PUBLISH_TOKEN"))
    }
}

changelog {
    version.set(rootProject.version.toString())
    path.set(rootProject.file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    headerParserRegex.set("""(\d+\.\d+.\d+)""".toRegex())
    introduction.set(
        """
        MongoDB plugin for IntelliJ IDEA.
        """.trimIndent(),
    )
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    lineSeparator.set("\n")
    combinePreReleases.set(true)
}
