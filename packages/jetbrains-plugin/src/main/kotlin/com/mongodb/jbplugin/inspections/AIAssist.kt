package com.mongodb.jbplugin.inspections

import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

data class SuggestionResults(
    val suggestion: String?,
    val suggestionMessage: String?,
    val suggestedProjection: List<String>,
    val suggestedProjectionMessage: String?,
) {
    companion object {
        fun nothing() = SuggestionResults(
            null,
            null,
            emptyList(),
            null
        )
    }
}

data class StoryResults(
    val story: String
)

class MongoGPTRequestBodySerializer {
    data class MongoGPTRequestBody(
        val messages: List<ChatMessage>,
        val model: String = "gpt-4.1",
        val stream: Boolean = false,
    )

    data class ChatMessage(
        val content: String,
        val role: String = "user",
    )

    companion object {
        fun requestBodyWithMessages(messages: List<ChatMessage>): String {
            return Gson().toJson(MongoGPTRequestBody(messages))
        }
    }
}

class MongoGPTResponseDeserializer {
    data class MongoGPTResponse(val choices: List<Choice>)

    data class Choice(val message: Message)

    data class Message(val content: String)

    companion object {
        inline fun <reified T> deserialize(response: String): T {
            val gson = Gson()
            val result = gson.fromJson(
                response,
                MongoGPTResponse::class.java
            )

            return gson.fromJson(result.choices.first().message.content, T::class.java)
        }
    }
}

class AIAssist {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    private fun executeCommand(command: String): Pair<Int, String> {
        val parts = command.split("\\s+".toRegex())
        val processBuilder = ProcessBuilder(parts)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = StringBuilder()

        // Read the output stream
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.append(line).append("\n")
            }
        }

        val exitCode = process.waitFor()
        return Pair(exitCode, output.toString())
    }

    private fun getMongoGPTToken(): String {
        val (exitCode, maybeToken) = executeCommand("kanopy login")
        if (exitCode != 0 || maybeToken.isEmpty()) {
            throw Exception("Failed to get MongoGPT token. Exit code: $exitCode")
        }

        return maybeToken
    }

    private suspend inline fun <reified T> mongoGptPostApiCall(
        body: String
    ): T {
        val response =
            client.post("https://mongogpt.aix.prod.corp.mongodb.com/api/v1/messages") {
                contentType(ContentType.Application.Json)
                headers {
                    append("X-Kanopy-Authorization", "Bearer ${getMongoGPTToken().trim()}")
                }
                setBody(body)
            }

        return MongoGPTResponseDeserializer.deserialize<T>(response.bodyAsText())
    }

    fun suggestProjection(
        queryMethod: String,
        queryContext: String,
        sampleDocuments: List<Map<String, Any>>,
    ): SuggestionResults = runBlocking(Dispatchers.IO) {
        runCatching {
            mongoGptPostApiCall<SuggestionResults>(
                MongoGPTRequestBodySerializer.requestBodyWithMessages(
                    listOf(
                        MongoGPTRequestBodySerializer.ChatMessage(
                            AIPrompts.suggestProjectionPrompt(
                                queryMethod,
                                queryContext,
                                Gson().toJson(sampleDocuments)
                            )
                        )
                    )
                )
            )
        }.getOrNull() ?: SuggestionResults.nothing()
    }
}
