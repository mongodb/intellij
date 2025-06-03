import com.mongodb.intellij.IntelliJPluginBundle
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("com.mongodb.intellij.isolated-module")
    id("org.gradle.test-retry")
    id("org.jetbrains.intellij.platform")
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
    buildSearchableOptions = false

    pluginConfiguration {
        version = rootProject.version.toString()
        description =
            providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText

        ideaVersion {
            sinceBuild = libs.versions.intellij.minRelease
            // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration-vendor
            untilBuild = provider { null }
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
        failureLevel = listOf(
          VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
          VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )

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
        bundledPlugin("com.intellij.spring.data")

        testFramework(TestFrameworkType.Plugin.Java)
    }

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
}

tasks {
    if (pluginBundle.enableBundle.get() == true) {
        publishPlugin {
            dependsOn(patchChangelog)
        }
    }

    named("test", Test::class) {
        useJUnitPlatform()

        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "-Dpolyglot.log.level=OFF"
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
