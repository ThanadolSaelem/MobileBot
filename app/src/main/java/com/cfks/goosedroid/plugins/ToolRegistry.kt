package com.cfks.goosedroid.plugins

import android.content.Context
import kotlinx.serialization.json.JsonObject

class ToolRegistry {
    private val plugins = mutableMapOf<String, UnitPlugin>()

    init {
        // Register default plugins here as we build them.
        register(ClipboardPlugin())
        register(WebDropZonePlugin())
        register(ScreenReaderPlugin())
    }

    fun register(plugin: UnitPlugin) {
        plugins[plugin.name] = plugin
    }

    fun getPlugin(name: String): UnitPlugin? {
        return plugins[name]
    }

    fun getAllPlugins(): List<UnitPlugin> {
        return plugins.values.toList()
    }

    /**
     * Generates a string representation of available tools to be injected into the LLM's system prompt.
     */
    fun getPromptDescription(): String {
        if (plugins.isEmpty()) return "No external tools available."

        val sb = StringBuilder()
        sb.append("AVAILABLE TOOLS (You can optionally call one tool per response):\n")
        plugins.values.forEach { plugin ->
            sb.append("- Tool Name: \"${plugin.name}\"\n")
            sb.append("  Description: ${plugin.description}\n")
            sb.append("  Parameters Schema: ${plugin.parametersJsonSchema}\n\n")
        }
        return sb.toString()
    }

    suspend fun executeTool(context: Context, name: String, args: JsonObject): String {
        android.util.Log.d("ToolRegistry", "Executing tool: $name with args: $args")
        val plugin = plugins[name] ?: return "Error: Tool '$name' not found."
        return try {
            val result = plugin.execute(context, args)
            android.util.Log.d("ToolRegistry", "Tool result: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("ToolRegistry", "Tool execution failed", e)
            "Error executing tool '$name': ${e.message}"
        }
    }
}
