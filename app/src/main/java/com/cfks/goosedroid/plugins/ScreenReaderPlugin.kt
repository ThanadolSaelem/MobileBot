package com.cfks.goosedroid.plugins

import android.content.Context
import com.cfks.goosedroid.services.GooseAccessibilityService
import kotlinx.serialization.json.JsonObject

class ScreenReaderPlugin : UnitPlugin {
    override val name = "screen_reader"
    override val description = "Reads the text currently visible on the user's screen. Requires Accessibility permission to be enabled."
    override val parametersJsonSchema = """
        {
          "type": "object",
          "properties": {},
          "required": []
        }
    """.trimIndent()

    override suspend fun execute(context: Context, args: JsonObject): String {
        if (!GooseAccessibilityService.isServiceEnabled()) {
            return "Error: Could not read screen. Tell the user they need to enable the Accessibility Service for Goose Desktop in their device settings."
        }

        val text = GooseAccessibilityService.captureScreenText()
        if (text.isBlank()) {
            return "Screen appears to be empty or content is not readable."
        }
        
        // Return up to ~5000 characters to prevent overflowing context unexpectedly,
        // though Gemini can handle large contexts.
        val maxLength = 5000
        if (text.length > maxLength) {
            return "Screen content (truncated):\n${text.substring(0, maxLength)}"
        }
        
        return "Screen content:\n$text"
    }
}
