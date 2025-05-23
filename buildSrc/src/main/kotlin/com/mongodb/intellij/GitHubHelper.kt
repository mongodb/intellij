package com.mongodb.intellij

import groovy.json.JsonSlurper
import java.net.URI

private const val REPOSITORY_BASE_URL = "https://api.github.com/repos/mongodb/intellij"

class GitHubActionCheckRun(
  val name: String,
  val conclusion: Boolean,
  val status: String,
  val url: String
) {
    val isRelevantForProjectHealth = name != "Prepare Release" && !name.contains("Qodana")

    companion object {
        fun deserialize(check: Map<String, Any>): GitHubActionCheckRun {
            return GitHubActionCheckRun(
              check["name"].toString(),
              check["conclusion"] == "success",
              check["status"].toString(),
              check["html_url"].toString()
            )
        }
    }
}

fun getLastCheckOnMain(): List<GitHubActionCheckRun> {
    val checks = JsonSlurper().parse(URI.create("${REPOSITORY_BASE_URL}/commits/main/check-runs").toURL()) as Map<String, Any>
    val checkRuns = checks["check_runs"] as List<Map<String, Any>>

    return checkRuns.map(GitHubActionCheckRun::deserialize)
}

sealed interface GitHubWorkflowStatus {
    object Success : GitHubWorkflowStatus
    object Fixed: GitHubWorkflowStatus
    data class Failure(val consecutiveFailures: Int) : GitHubWorkflowStatus
}

fun getWorkflowStatus(name: String): GitHubWorkflowStatus {
    val runs = JsonSlurper().parse(URI.create("${REPOSITORY_BASE_URL}/actions/runs").toURL()) as Map<String, Any>
    val workflowRuns = runs["workflow_runs"] as? List<Map<String, Any>>

    val consecutiveFailures = workflowRuns
      ?.filter { it["name"].toString() == name }
      ?.map(GitHubActionCheckRun::deserialize)
      ?.takeWhile { !it.conclusion }
      ?.count()
      ?: 0

    val didFail = workflowRuns?.drop(1)?.take(1)?.map(GitHubActionCheckRun::deserialize)?.first()?.conclusion == false
    return if (consecutiveFailures == 0 && didFail) {
        GitHubWorkflowStatus.Fixed
    } else if (consecutiveFailures == 0) {
        GitHubWorkflowStatus.Success
    } else {
        GitHubWorkflowStatus.Failure(consecutiveFailures)
    }
}
