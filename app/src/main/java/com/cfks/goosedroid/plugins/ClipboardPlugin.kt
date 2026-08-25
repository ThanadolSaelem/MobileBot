package com.cfks.goosedroid.plugins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClipboardPlugin : UnitPlugin {
    override val name = "clipboard_manager"
    override val description = "Read or write to the device clipboard."
    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {
            "action": { "type": "string", "enum": ["read", "write"] },
            "text": { "type": "string", "description": "The text to write (if action is write)." }
          },
          "required": ["action"]
        }
    """.trimIndent()

    override suspend fun execute(context: Context, args: JsonObject): String {
        val action = args["action"]?.jsonPrimitive?.content ?: return "Error: Missing action."
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return when (action) {
            "read" -> {
                if (clipboard.hasPrimaryClip()) {
                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    "Clipboard contains: ${text ?: "[Empty/Not Text]"}"
                } else {
                    "Clipboard is empty."
                }
            }
            "write" -> {
                val text = args["text"]?.jsonPrimitive?.content ?: return "Error: Missing text for write action."
                val clip = ClipData.newPlainText("Copied via Unit", text)
                clipboard.setPrimaryClip(clip)
                "Successfully wrote to clipboard."
            }
            else -> "Error: Unknown action '$action'."
        }
    }
}
