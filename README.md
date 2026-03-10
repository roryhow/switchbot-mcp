# SwitchBot MCP Server

An MCP (Model Context Protocol) server for SwitchBot home automation, built with the [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk).

## Prerequisites

- JDK 17 or later
- Gradle 8.x (or use `./gradlew` after bootstrapping)
- A SwitchBot account with API access

## Getting Your API Credentials

1. Open the SwitchBot app on your phone
2. Go to **Profile → Preferences → About**
3. Tap **"App Version"** 10 times rapidly
4. Go to **Developer Options → Get Token**
5. Copy both your **Token** and **Secret Key**

## Installation

### Homebrew (recommended)

```bash
brew tap roryhow/switchbot-mcp
brew install switchbot-mcp
```

## Setup

### Option 1: Environment Variables (recommended)

```bash
export SWITCHBOT_TOKEN="your-token-here"
export SWITCHBOT_SECRET="your-secret-here"
```

### Option 2: Config File

Create `~/.switchbot/credentials.json`:

```json
{
  "token": "your-token-here",
  "secret": "your-secret-here"
}
```

### Option 3: CLI Arguments

```bash
java -jar switchbot-mcp.jar --token YOUR_TOKEN --secret YOUR_SECRET
```

## Building

Bootstrap the Gradle wrapper (first time only):

```bash
gradle wrapper
```

Build the fat JAR:

```bash
./gradlew shadowJar
```

The JAR is output to `build/libs/switchbot-mcp-1.0.0.jar`.

## Running

```bash
# With environment variables set:
java -jar build/libs/switchbot-mcp-1.0.0.jar

# With CLI arguments:
java -jar build/libs/switchbot-mcp-1.0.0.jar --token YOUR_TOKEN --secret YOUR_SECRET
```

## Claude Desktop Integration

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "switchbot": {
      "command": "switchbot-mcp",
      "env": {
        "SWITCHBOT_TOKEN": "your-token-here",
        "SWITCHBOT_SECRET": "your-secret-here"
      }
    }
  }
}
```

> If you installed via JAR instead of Homebrew, use `"command": "java"` with `"args": ["-jar", "/absolute/path/to/switchbot-mcp-1.0.0.jar"]`.

## Available Tools

| Tool | Description |
|------|-------------|
| `switchbot_credentials_status` | Check if credentials are configured |
| `switchbot_list_devices` | List all physical and IR remote devices |
| `switchbot_get_device_status` | Get current status of a device |
| `switchbot_control_device` | Send a command to a device |
| `switchbot_list_scenes` | List all manual scenes |
| `switchbot_execute_scene` | Execute a scene |
| `switchbot_setup_webhook` | Configure a webhook URL for events |
| `switchbot_query_webhooks` | List configured webhook URLs |
| `switchbot_update_webhook` | Enable or disable a webhook |
| `switchbot_delete_webhook` | Delete a webhook URL |

### Common Device Commands

**Lights & Bulbs:** `turnOn`, `turnOff`, `toggle`, `setBrightness` (1–100), `setColor` ("R:G:B"), `setColorTemperature` (2700–6500)

**Curtains/Blinds:** `turnOn` (open), `turnOff` (close), `setPosition` (parameter: "index,mode,position" e.g. "0,ff,50")

**Locks:** `lock`, `unlock`

**Plugs:** `turnOn`, `turnOff`

**Robot Vacuums:** `start`, `stop`, `dock`, `PowLevel` (parameter: 0–3)

**Fans:** `turnOn`, `turnOff`, `setMode`, `setFanSpeed` (1–100)

**IR Remotes:** `turnOn`, `turnOff`, or any custom button (commandType: "customize")

## Disclaimer

This MCP server is based on the publicly accessible [SwitchBot API documentation](https://github.com/OpenWonderLabs/SwitchBotAPI). This repository does not actively track changes to the SwitchBot API, and updates to the upstream API may introduce breaking changes without notice.

If you encounter a breakage due to an API change, issues and PRs to this repository are welcome.

## API Rate Limits

SwitchBot allows 10,000 API calls per day. The server does not enforce rate limiting internally.
