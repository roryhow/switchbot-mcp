package com.switchbot.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SwitchBotClient(
    private val credentials: Credentials,
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) {
    private val baseUrl = "https://api.switch-bot.com"

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun addAuthHeaders(builder: io.ktor.client.request.HttpRequestBuilder) {
        val auth = generateAuthHeaders(credentials.token, credentials.secret)
        builder.headers {
            append("Authorization", auth.authorization)
            append("t", auth.t)
            append("sign", auth.sign)
            append("nonce", auth.nonce)
        }
    }

    suspend fun getDevices(): String = request {
        client.get("$baseUrl/v1.1/devices") { addAuthHeaders(this) }
    }

    suspend fun getDeviceStatus(deviceId: String): String = request {
        client.get("$baseUrl/v1.1/devices/$deviceId/status") { addAuthHeaders(this) }
    }

    suspend fun controlDevice(
        deviceId: String,
        command: String,
        parameter: String,
        commandType: String,
    ): String = request {
        client.post("$baseUrl/v1.1/devices/$deviceId/commands") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("command", command)
                    put("parameter", parameter)
                    put("commandType", commandType)
                }
            )
        }
    }

    suspend fun getScenes(): String = request {
        client.get("$baseUrl/v1.1/scenes") { addAuthHeaders(this) }
    }

    suspend fun executeScene(sceneId: String): String = request {
        client.post("$baseUrl/v1.1/scenes/$sceneId/execute") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun setupWebhook(url: String): String = request {
        client.post("$baseUrl/v1.1/webhook/setupWebhook") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("action", "setupWebhook")
                    put("url", url)
                    put("deviceList", "ALL")
                }
            )
        }
    }

    suspend fun queryWebhooks(): String = request {
        client.post("$baseUrl/v1.1/webhook/queryWebhook") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("action", "queryUrl") })
        }
    }

    suspend fun updateWebhook(url: String, enable: Boolean): String = request {
        client.post("$baseUrl/v1.1/webhook/updateWebhook") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("action", "updateWebhook")
                    put(
                        "config",
                        buildJsonObject {
                            put("url", url)
                            put("enable", enable)
                        },
                    )
                }
            )
        }
    }

    suspend fun deleteWebhook(url: String): String = request {
        client.post("$baseUrl/v1.1/webhook/deleteWebhook") {
            addAuthHeaders(this)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("action", "deleteWebhook")
                put("url", url)
            })
        }
    }

    private suspend fun request(block: suspend () -> HttpResponse): String {
        return try {
            val response = block()
            val bodyText = response.bodyAsText()
            try {
                val element = Json.parseToJsonElement(bodyText)
                prettyJson.encodeToString(JsonElement.serializer(), element)
            } catch (_: Exception) {
                bodyText
            }
        } catch (e: Exception) {
            "Error calling SwitchBot API: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}
