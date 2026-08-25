package com.cfks.goosedroid.plugins

import android.content.Context
import kotlinx.serialization.json.JsonObject

interface UnitPlugin {
    val name: String
    val description: String
    val parametersJsonSchema: String // E.g., "{ \"type\": \"object\", \"properties\": { ... } }"

    /**
     * Executes the plugin's action.
     * @param context Android Context for system actions.
     * @param args The JSON arguments provided by the LLM.
     * @return A string result to optionally feed back to the LLM (or just log).
     */
    suspend fun execute(context: Context, args: JsonObject): String
}
