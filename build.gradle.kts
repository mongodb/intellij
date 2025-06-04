import com.mongodb.intellij.GitHubWorkflowStatus.Failure
import com.mongodb.intellij.GitHubWorkflowStatus.Fixed
import com.mongodb.intellij.GitHubWorkflowStatus.Success
import com.mongodb.intellij.createBuildErrorJiraTask
import com.mongodb.intellij.getLastCheckOnMain
import com.mongodb.intellij.getWorkflowStatus
import com.mongodb.intellij.keyOfCurrentCompatibilityErrorTask

group = "com.mongodb"
// This should be bumped when releasing a new version using the versionBump task:
// ./gradlew versionBump -Pmode={major,minor,patch}
version = "0.0.1"

plugins {
    base
    id("com.github.ben-manes.versions")
    id("jacoco-report-aggregation")
    id("org.jetbrains.changelog")
    id("org.jetbrains.qodana")
}

repositories {
    mavenCentral()
    mavenLocal()
    google()

    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

dependencies {
    jacocoAggregation(project(":packages:jetbrains-plugin")) {
        exclude(group = "org.jetbrains.skiko")
        exclude(group = "org.jetbrains.jewel")
    }

    jacocoAggregation(project(":packages:mongodb-access-adapter"))
    jacocoAggregation(project(":packages:mongodb-access-adapter:datagrip-access-adapter"))
    jacocoAggregation(project(":packages:mongodb-mql-engines"))
    jacocoAggregation(project(":packages:mongodb-dialects"))
    jacocoAggregation(project(":packages:mongodb-dialects:java-driver"))
    jacocoAggregation(project(":packages:mongodb-dialects:mongosh"))
    jacocoAggregation(project(":packages:mongodb-dialects:spring-criteria"))
    jacocoAggregation(project(":packages:mongodb-dialects:spring-@query"))
    jacocoAggregation(project(":packages:mongodb-mql-model"))
}

tasks {
    register("versionBump") {
        group = "versioning"
        description = "Increments the version of the plugin."

        fun generateVersion(): String {
            val updateMode = rootProject.findProperty("mode") ?: "patch"
            val (oldMajor, oldMinor, oldPatch) = rootProject.version.toString().split(".").map(String::toInt)
            var (newMajor, newMinor, newPatch) = arrayOf(oldMajor, oldMinor, 0)

            when (updateMode) {
                "major" -> newMajor = (oldMajor + 1).also { newMinor = 0 }
                "minor" -> newMinor = (oldMinor + 1)
                else -> newPatch = oldPatch + 1
            }
            return "$newMajor.$newMinor.$newPatch"
        }

        val newVersion = rootProject.findProperty("exactVersion") ?: generateVersion()
        val oldContent = buildFile.readText()
        val newContent = oldContent.replace(""" = "$version"""", """ = "$newVersion"""")
        buildFile.writeText(newContent)
    }

    register<Exec>("gitHooks") {
        group = "environment"

        rootProject.file(".git/hooks").mkdirs()
        commandLine("cp", "./gradle/pre-commit", "./.git/hooks")
    }

    register("getVersion") {
        group = "environment"

        println(rootProject.version)
    }

    register("mainStatus") {
        group = "environment"

        val checks = getLastCheckOnMain().filter { it.isRelevantForProjectHealth }
        var success = true

        for (check in checks) {
            if (!check.conclusion) {
                System.err.println("[❌] Check ${check.name} is in status ${check.status}: ${check.url}")
                success = false
            } else {
                println("[✅] Check ${check.name} has finished successfully: ${check.url}")
            }
        }

        if (!success) {
            throw GradleException("Checks in main must be successful.")
        }
    }

    register("verifyNextVersionCompatibility") {
        group = "ideCompat"

        when (val status = getWorkflowStatus("Verify Compatibility with latest IDE version")) {
            is Failure -> {
                if (status.consecutiveFailures == 1) {
                    println("InitialFailure")
                } else if (status.consecutiveFailures % 2 == 0) {
                    println("RepeatedFailure")
                } else {
                    println("RepeatedFailureWithoutNotification")
                }
            }
            Fixed -> {
                println("Fixed")
            }
            Success -> {
                println("Success")
            }
            else -> {
                throw GradleException("Could not verify compatibility.")
            }
        }
    }

    register("registerBuildError") {
        group = "ideCompat"

        doLast {
            val currentKey = keyOfCurrentCompatibilityErrorTask() ?:
                // take only the last 30000 characters of the logs because JIRA has a limit on 32767 chars.
                createBuildErrorJiraTask(
                  "Plugin is not compatible with the latest IDEA Version.",
                  """This task has been created from build.gradle.kts:registerBuildError.
                      |
                      |It means that the pluginVerifier task complained that using the plugin in the
                      |next IDEA version will break, as there is a compatibility issue.
                      |
                      |{code}
                      |${System.getenv("JIRA_ISSUE_DESCRIPTION").takeLast(30_000)}
                      |{code}
                  """.trimMargin()
                )

            println(currentKey)
        }
    }
}
