package com.switchbot.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SwitchBotServer(private val credentials: Credentials?) {
    private val client = credentials?.let { SwitchBotClient(it) }

    fun run() {
        val server = Server(
            Implementation(name = "switchbot-mcp", version = "1.0.0"),
            ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )

        registerTools(server)

        val transport = StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered(),
        )

        runBlocking {
            server.createSession(transport)
            val done = Job()
            server.onClose { done.complete() }
            done.join()
        }

        client?.close()
    }

    private fun noCredentialsResult() = CallToolResult(
        content = listOf(
            TextContent(
                """
                SwitchBot credentials not configured. Set up using one of these methods:

                1. Environment variables (recommended):
                   export SWITCHBOT_TOKEN="your-token"
                   export SWITCHBOT_SECRET="your-secret"

                2. Config file (~/.switchbot/credentials.json):
                   {
                     "token": "your-token",
                     "secret": "your-secret"
                   }

                3. Command-line arguments:
                   java -jar switchbot-mcp.jar --token YOUR_TOKEN --secret YOUR_SECRET

                To get your token and secret from the SwitchBot app:
                Profile > Preferences > About > tap "App Version" 10 times > Developer Options > Get Token
                """.trimIndent(),
            ),
        ),
        isError = true,
    )

    private fun missingParam(name: String) = CallToolResult(
        content = listOf(TextContent("Missing required parameter: $name")),
        isError = true,
    )

    private fun toolSchema(
        vararg properties: Pair<String, Pair<String, String>>,
        required: List<String> = properties.map { it.first },
    ) = ToolSchema(
        properties = buildJsonObject {
            for ((name, typeDef) in properties) {
                put(name, buildJsonObject {
                    put("type", typeDef.first)
                    put("description", typeDef.second)
                })
            }
        },
        required = required,
    )

    private fun registerTools(server: Server) {
        server.addTool(
            name = "switchbot_credentials_status",
            description = "Check whether SwitchBot API credentials are configured and show setup instructions if not",
        ) { _ ->
            if (credentials == null) {
                noCredentialsResult()
            } else {
                CallToolResult(
                    content = listOf(TextContent("Credentials configured. Token: ${credentials.token.take(8)}...")),
                )
            }
        }

        server.addTool(
            name = "switchbot_list_devices",
            description = "List all SwitchBot devices including physical devices (hubs, bots, curtains, locks, sensors, lights, vacuums, fans, thermostats) and virtual IR remote devices",
        ) { _ ->
            if (client == null) return@addTool noCredentialsResult()
            CallToolResult(content = listOf(TextContent(client.getDevices())))
        }

        server.addTool(
            name = "switchbot_get_device_status",
            description = "Get the current status of a SwitchBot device (power state, temperature, humidity, battery, position, lock state, etc.)",
            inputSchema = toolSchema(
                "deviceId" to ("string" to "The device ID from switchbot_list_devices"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val deviceId = request.arguments?.get("deviceId")?.jsonPrimitive?.content
                ?: return@addTool missingParam("deviceId")
            CallToolResult(content = listOf(TextContent(client.getDeviceStatus(deviceId))))
        }

        server.addTool(
            name = "switchbot_control_device",
            description = """
                Send a command to control a SwitchBot device. Common commands by device type:
                - Bot: turnOn, turnOff, press
                - Curtain/Roller Shade/Blind Tilt: turnOn, turnOff, setPosition (parameter: "index,mode,position" e.g. "0,ff,50")
                - Color Bulb/Strip Light/Floor Lamp: turnOn, turnOff, toggle, setBrightness (1-100), setColor ("R:G:B"), setColorTemperature (2700-6500)
                - Lock/Lock Pro/Lock Ultra: lock, unlock
                - Plug/Plug Mini: turnOn, turnOff
                - Fan/Circulator Fan: turnOn, turnOff, setMode, setFanSpeed (1-100), setOscillation, setNightLight
                - Robot Vacuum: start, stop, dock, PowLevel (0-3)
                - Air Purifier/Humidifier: turnOn, turnOff, setMode, setChildLock
                - Ceiling Light: turnOn, turnOff, toggle, setBrightness, setColorTemperature
                - Relay Switch: turnOn, turnOff
                - Garage Door Opener: turnOn (open), turnOff (close)
                - Keypad: createKey, deleteKey
                - IR Remote devices: turnOn, turnOff, or any custom button name (commandType: "customize")
            """.trimIndent(),
            inputSchema = toolSchema(
                "deviceId" to ("string" to "The device ID from switchbot_list_devices"),
                "command" to ("string" to "Command to send (e.g. turnOn, turnOff, press, setPosition, setBrightness, setColor)"),
                "parameter" to ("string" to "Command parameter. Use \"default\" when not needed. Examples: \"50\" for brightness, \"255:128:0\" for color, \"0,ff,50\" for curtain position"),
                "commandType" to ("string" to "\"command\" for physical/standard IR commands (default), \"customize\" for custom IR remote buttons"),
                required = listOf("deviceId", "command"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val deviceId = request.arguments?.get("deviceId")?.jsonPrimitive?.content
                ?: return@addTool missingParam("deviceId")
            val command = request.arguments?.get("command")?.jsonPrimitive?.content
                ?: return@addTool missingParam("command")
            val parameter = request.arguments?.get("parameter")?.jsonPrimitive?.content ?: "default"
            val commandType = request.arguments?.get("commandType")?.jsonPrimitive?.content ?: "command"
            CallToolResult(content = listOf(TextContent(client.controlDevice(deviceId, command, parameter, commandType))))
        }

        server.addTool(
            name = "switchbot_list_scenes",
            description = "List all manual scenes configured in the SwitchBot app",
        ) { _ ->
            if (client == null) return@addTool noCredentialsResult()
            CallToolResult(content = listOf(TextContent(client.getScenes())))
        }

        server.addTool(
            name = "switchbot_execute_scene",
            description = "Execute a manual scene in SwitchBot",
            inputSchema = toolSchema(
                "sceneId" to ("string" to "The scene ID from switchbot_list_scenes"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val sceneId = request.arguments?.get("sceneId")?.jsonPrimitive?.content
                ?: return@addTool missingParam("sceneId")
            CallToolResult(content = listOf(TextContent(client.executeScene(sceneId))))
        }

        server.addTool(
            name = "switchbot_setup_webhook",
            description = "Configure a webhook URL to receive real-time device state change events from SwitchBot (motion, door, temperature, lock state changes, etc.)",
            inputSchema = toolSchema(
                "url" to ("string" to "Publicly accessible HTTPS URL to receive webhook POST events"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val url = request.arguments?.get("url")?.jsonPrimitive?.content
                ?: return@addTool missingParam("url")
            CallToolResult(content = listOf(TextContent(client.setupWebhook(url))))
        }

        server.addTool(
            name = "switchbot_query_webhooks",
            description = "Get all configured webhook URLs for SwitchBot device events",
        ) { _ ->
            if (client == null) return@addTool noCredentialsResult()
            CallToolResult(content = listOf(TextContent(client.queryWebhooks())))
        }

        server.addTool(
            name = "switchbot_update_webhook",
            description = "Enable or disable a configured SwitchBot webhook URL",
            inputSchema = toolSchema(
                "url" to ("string" to "The webhook URL to update"),
                "enable" to ("boolean" to "true to enable the webhook, false to disable it"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val url = request.arguments?.get("url")?.jsonPrimitive?.content
                ?: return@addTool missingParam("url")
            val enable = request.arguments?.get("enable")?.jsonPrimitive?.boolean
                ?: return@addTool missingParam("enable")
            CallToolResult(content = listOf(TextContent(client.updateWebhook(url, enable))))
        }

        server.addTool(
            name = "switchbot_delete_webhook",
            description = "Delete a configured SwitchBot webhook URL",
            inputSchema = toolSchema(
                "url" to ("string" to "The webhook URL to delete"),
            ),
        ) { request ->
            if (client == null) return@addTool noCredentialsResult()
            val url = request.arguments?.get("url")?.jsonPrimitive?.content
                ?: return@addTool missingParam("url")
            CallToolResult(content = listOf(TextContent(client.deleteWebhook(url))))
        }
    }
}
