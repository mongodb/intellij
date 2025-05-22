package com.mongodb.intellij

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

const val JIRA_URL = "https://jira.mongodb.org"
const val IDE_COMPAT_LABEL = "next-ide-compat"
const val GHA_LABEL = "gha"

fun keyOfCurrentCompatibilityErrorTask(): String? {
    val jql = URLEncoder.encode("project = INTELLIJ AND type = 'Build Failure' AND status != Closed AND labels = '${IDE_COMPAT_LABEL}'", StandardCharsets.UTF_8)
    val endpointUrl = URI.create("${JIRA_URL}/rest/api/2/search?jql=${jql}&maxResults=1")
    val client = HttpClient.newHttpClient()

    val searchForTicketRequest = HttpRequest.newBuilder()
      .uri(endpointUrl)
      .GET()
      .header("Authorization", System.getenv("JIRA_TOKEN"))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .build()

    val response = client.send(searchForTicketRequest, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() >= 400) {
        return response.body()
    }

    val issues = JsonSlurper().parse(response.body().toCharArray()) as Map<String, Any>
    val total = issues["total"].toString().toInt()
    if (total == 0) {
        return null
    }

    val issueList = issues["issues"] as? List<Map<String, Any>>
    return issueList?.get(0)?.get("key")?.toString()
}

fun createBuildErrorJiraTask(summary: String, logs: String): String? {
    val endpointUrl = URI.create("${JIRA_URL}/rest/api/2/issue/")
    val client = HttpClient.newHttpClient()
    val issue = mapOf(
      "fields" to mapOf(
        "project" to mapOf("key" to "INTELLIJ"),
        "summary" to "[GHA] $summary",
        "description" to logs,
        "issuetype" to mapOf("name" to "Build Failure"),
        "labels" to arrayOf(IDE_COMPAT_LABEL, GHA_LABEL)
      )
    )

    val createTicketRequest = HttpRequest.newBuilder()
      .uri(endpointUrl)
      .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(issue)))
      .header("Authorization", System.getenv("JIRA_TOKEN"))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .build()

    val response = client.send(createTicketRequest, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() >= 400) {
        return response.body()
    }

    val responseJson = JsonSlurper().parse(response.body().toCharArray()) as Map<String, Any>
    return responseJson["key"].toString()
}
