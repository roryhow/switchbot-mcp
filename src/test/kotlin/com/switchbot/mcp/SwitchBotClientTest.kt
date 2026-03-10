package com.switchbot.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SwitchBotClientTest {

    private val credentials = Credentials("test-token", "test-secret")
    private val jsonContentHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun mockClient(
        responseBody: String = """{"statusCode":100,"message":"success","body":{}}""",
        onRequest: (HttpRequestData) -> Unit = {},
    ): SwitchBotClient {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(responseBody, HttpStatusCode.OK, jsonContentHeader)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return SwitchBotClient(credentials, httpClient)
    }

    private fun HttpRequestData.bodyText(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""

    private fun HttpRequestData.bodyJson() =
        Json.parseToJsonElement(bodyText()).jsonObject

    // --- Auth headers ---

    @Test
    fun `requests include auth headers`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).getDevices()

        val req = assertNotNull(captured)
        assertNotNull(req.headers["Authorization"])
        assertNotNull(req.headers["t"])
        assertNotNull(req.headers["sign"])
        assertNotNull(req.headers["nonce"])
    }

    // --- GET endpoints ---

    @Test
    fun `getDevices sends GET to correct path`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).getDevices()

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Get, req.method)
        assertEquals("/v1.1/devices", req.url.encodedPath)
    }

    @Test
    fun `getDeviceStatus sends GET to correct path with device id`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).getDeviceStatus("device-abc")

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Get, req.method)
        assertEquals("/v1.1/devices/device-abc/status", req.url.encodedPath)
    }

    @Test
    fun `getScenes sends GET to correct path`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).getScenes()

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Get, req.method)
        assertEquals("/v1.1/scenes", req.url.encodedPath)
    }

    // --- POST endpoints ---

    @Test
    fun `controlDevice sends POST with correct path and body`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it })
            .controlDevice("device-abc", "turnOn", "default", "command")

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/devices/device-abc/commands", req.url.encodedPath)

        val body = req.bodyJson()
        assertEquals("turnOn", body["command"]?.jsonPrimitive?.content)
        assertEquals("default", body["parameter"]?.jsonPrimitive?.content)
        assertEquals("command", body["commandType"]?.jsonPrimitive?.content)
    }

    @Test
    fun `executeScene sends POST to correct path`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).executeScene("scene-123")

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/scenes/scene-123/execute", req.url.encodedPath)
    }

    @Test
    fun `setupWebhook sends POST with correct body`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).setupWebhook("https://example.com/hook")

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/webhook/setupWebhook", req.url.encodedPath)

        val body = req.bodyJson()
        assertEquals("setupWebhook", body["action"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/hook", body["url"]?.jsonPrimitive?.content)
        assertEquals("ALL", body["deviceList"]?.jsonPrimitive?.content)
    }

    @Test
    fun `queryWebhooks sends POST with correct body`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).queryWebhooks()

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/webhook/queryWebhook", req.url.encodedPath)
        assertEquals("queryUrl", req.bodyJson()["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun `updateWebhook sends POST with correct body`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).updateWebhook("https://example.com/hook", true)

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/webhook/updateWebhook", req.url.encodedPath)

        val body = req.bodyJson()
        assertEquals("updateWebhook", body["action"]?.jsonPrimitive?.content)

        val config = body["config"]?.jsonObject
        assertEquals("https://example.com/hook", config?.get("url")?.jsonPrimitive?.content)
        assertEquals(true, config?.get("enable")?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `deleteWebhook sends POST with correct body`() = runTest {
        var captured: HttpRequestData? = null
        mockClient(onRequest = { captured = it }).deleteWebhook("https://example.com/hook")

        val req = assertNotNull(captured)
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/v1.1/webhook/deleteWebhook", req.url.encodedPath)

        val body = req.bodyJson()
        assertEquals("deleteWebhook", body["action"]?.jsonPrimitive?.content)
        assertEquals("https://example.com/hook", body["url"]?.jsonPrimitive?.content)
    }

    // --- Response handling ---

    @Test
    fun `response is returned as pretty-printed JSON`() = runTest {
        val client = mockClient(responseBody = """{"statusCode":100,"body":{"items":[]}}""")
        val result = client.getDevices()

        assertTrue(result.contains("\n"), "Expected pretty-printed JSON with newlines")
    }

    @Test
    fun `API error response is returned as string`() = runTest {
        val engine = MockEngine { respond("Internal Server Error", HttpStatusCode.InternalServerError) }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val client = SwitchBotClient(credentials, httpClient)
        val result = client.getDevices()

        assertEquals("Internal Server Error", result)
    }
}
