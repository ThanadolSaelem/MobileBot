package com.cfks.goosedroid.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolCall(
    val name: String,
    val args: JsonObject
)

@Serializable
data class LlmResponse(
    val thought: String = "",
    val speech: String = "",
    val action: String = "IDLE", // "WALK", "RUN", "JUMP", "IDLE"
    val moveset_name: String? = null,
    val direction_x: Float = 0f,
    val direction_y: Float = 0f,
    val duration_frames: Int = 180,
    val tool_call: ToolCall? = null
)
