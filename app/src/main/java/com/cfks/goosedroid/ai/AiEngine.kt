package com.cfks.goosedroid.ai

import kotlinx.coroutines.flow.Flow

interface AiEngine {
    /**
     * Sends a prompt and the current state to the AI model.
     * Returns a JSON string representing the LlmResponse.
     */
    suspend fun generateActionJson(prompt: String, systemPrompt: String): String
}
