package com.cfks.goosedroid.plugins

import android.content.Context
import com.cfks.goosedroid.server.WebDropZoneServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class WebDropZonePlugin : UnitPlugin {
    override val name = "web_drop_zone"
    override val description = "Start the secure local web server to receive a file from a PC or another device via Wi-Fi."
    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["start"] }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(context: Context, args: JsonObject): String {
        val action = args["action"]?.jsonPrimitive?.content ?: "start"
        
        return when (action) {
            "start" -> {
                val url = WebDropZoneServer.startServer(context)
                "Server started. Tell the user exactly this message: 'Please go to this URL on your PC or other device to send me the file: \$url (The link expires in 60 seconds)'"
            }
            else -> "Error: Unknown action '\$action'."
        }
    }
}
