import com.mongodb.intellij.IntelliJPluginBundle
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("com.mongodb.intellij.isolated-module")
    id("org.gradle.test-retry")
    id("org.jetbrains.intellij.platform")
    id("me.champeau.jmh")
    id("io.morethan.jmhreport")
    id("org.jetbrains.changelog")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
        releases()
    }
}

val libs = the<LibrariesForLibs>()

val pluginBundle: IntelliJPluginBundle =
    project.extensions.create(
        "pluginBundle",
        IntelliJPluginBundle::class.java
    )

pluginBundle.enableBundle.convention(false)

intellijPlatform {
    pluginConfiguration {
        version = rootProject.version.toString()
        description =
            providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText

        ideaVersion {
            sinceBuild = libs.versions.intellij.minRelease
            untilBuild = ""
        }

        val changelog = rootProject.changelog
        changeNotes = with(changelog) {
            renderItem(
              changelog
                .getUnreleased()
                .withHeader(false)
                .withEmptySections(false),
              Changelog.OutputType.HTML,
            )
        }
    }

    signing {
        certificateChain = System.getenv("JB_CERTIFICATE_CHAIN")
        privateKey = System.getenv("JB_PRIVATE_KEY")
        password = System.getenv("JB_PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = System.getenv("JB_PUBLISH_TOKEN")
        channels = when (System.getenv("JB_PUBLISH_CHANNEL")) {
            "ga" -> listOf("Stable")
            "beta" -> listOf("beta")
            else -> listOf("eap")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

project.afterEvaluate {
    val tasksToDisable = arrayOf(
      "buildPlugin",
      "buildSearchableOptions",
      "jarSearchableOptions",
      "prepareJarSearchableOptions",
      "publishPlugin",
      "runIde",
      "signPlugin",
      "testIdePerformance",
      "verifyPlugin",
      "verifyPluginProjectConfiguration",
      "verifyPluginSignature",
      "verifyPluginStructure"
    )

    if (pluginBundle.enableBundle.get() == false) {
        tasks.filter { it.group == "intellij platform" &&
            tasksToDisable.contains(it.name)
        }.forEach {
            it.enabled = false
        }
    }
}

dependencies {
    intellijPlatform {
        create(libs.versions.intellij.type.get(), libs.versions.intellij.min.get())

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.database")

        testFramework(TestFrameworkType.JUnit5)
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.Starter)
    }

    jmh(libs.kotlin.stdlib)
    jmh(libs.testing.jmh.core)
    jmh(libs.testing.jmh.annotationProcessor)
    jmh(libs.testing.jmh.generatorByteCode)

    testImplementation(libs.testing.intellij.ideImpl)
    testImplementation(libs.testing.intellij.coreUi)

    testImplementation(libs.mongodb.driver)
    testImplementation(libs.testing.spring.mongodb)
    testImplementation(libs.testing.assertj.swing)
    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.mongodb)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.owasp.encoder)

    testImplementation(libs.testing.intellij.testingFrameworkCore) {
        exclude("org.jetbrains.teamcity")
    }

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

        publishPlugin {
            dependsOn(patchChangelog)
        }
    }

    named("test", Test::class) {
        useJUnitPlatform()

        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        // val homePath =
        //     project.layout.buildDirectory
        //         .dir("idea-sandbox/config-test")
        //         .get()
        //         .asFile.absolutePath

        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                // "-Dpolyglot.engine.WarnInterpreterOnly=false",
                // "-Dpolyglot.log.level=OFF",
                // "-Didea.home.path=$homePath",
            ),
        )
    }

    if (pluginBundle.enableBundle.get() == true) {
        withType<ProcessResources> {
            dependsOn("buildProperties")
        }
    }
}

changelog {
    version.set(rootProject.version.toString())
    path.set(rootProject.file("CHANGELOG.md").absolutePath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    headerParserRegex.set("""(\d+\.\d+.\d+)""".toRegex())
    introduction.set(
        """
        MongoDB plugin for IntelliJ IDEA.
        """.trimIndent(),
    )
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("Unreleased")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    lineSeparator.set("\n")
    combinePreReleases.set(true)
}
