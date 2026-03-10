package com.switchbot.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>) {
    val credentials = loadCredentials(args)
    SwitchBotServer(credentials).run()
}

data class Credentials(
    val token: String,
    val secret: String,
)

@Serializable
private data class CredentialsFile(
    val token: String,
    val secret: String,
)

fun loadCredentials(args: Array<String>): Credentials? {
    // 1. CLI args: --token VALUE --secret VALUE
    val argMap = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size - 1) {
        if (args[i].startsWith("--")) {
            argMap[args[i]] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    val cliToken = argMap["--token"]
    val cliSecret = argMap["--secret"]
    if (cliToken != null && cliSecret != null) {
        return Credentials(cliToken, cliSecret)
    }

    // 2. Environment variables
    val envToken = System.getenv("SWITCHBOT_TOKEN")
    val envSecret = System.getenv("SWITCHBOT_SECRET")
    if (envToken != null && envSecret != null) {
        return Credentials(envToken, envSecret)
    }

    // 3. Config file: ~/.switchbot/credentials.json
    val configFile = File(System.getProperty("user.home"), ".switchbot/credentials.json")
    if (configFile.exists()) {
        return try {
            val config = Json.decodeFromString<CredentialsFile>(configFile.readText())
            Credentials(config.token, config.secret)
        } catch (e: Exception) {
            System.err.println("Warning: Could not parse ${configFile.absolutePath}: ${e.message}")
            null
        }
    }

    return null
}
