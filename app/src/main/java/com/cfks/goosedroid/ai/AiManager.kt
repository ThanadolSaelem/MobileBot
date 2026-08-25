package com.cfks.goosedroid.ai

import android.content.Context
import com.cfks.goosedroid.OverlayLlmDirective
import com.cfks.goosedroid.plugins.ToolRegistry
import kotlinx.serialization.json.Json

class AiManager(private val context: Context) {
    
    private val repository = AiSettingsRepository(context)
    val toolRegistry = ToolRegistry() // MCP-Lite Registry
    
    // Configured Json parser that ignores unknown keys
    private val jsonParser = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    private fun getEngine(): AiEngine {
        val settings = repository.getSettings()
        return when (settings.mode) {
            AiMode.CLOUD_API -> CloudAiEngine(settings)
            AiMode.LOCAL_LLAMA -> LocalAiEngine(settings)
        }
    }

    suspend fun getNextAction(prompt: String, characterPersona: String = "", characterName: String = "Unit"): OverlayLlmDirective {
        val baseRole = if (characterPersona.isNotBlank()) {
            "You are $characterName. $characterPersona"
        } else {
            "You are $characterName, an autonomous desktop unit operating on the user's screen."
        }
        
        val recentHistory = com.cfks.goosedroid.brain.CharacterRegistry.getInteractionContext(characterName)

        val toolPrompt = toolRegistry.getPromptDescription()

        val systemPrompt = """
            $baseRole
            
            RECENT CONVERSATION HISTORY (Use this context for multi-turn replies):
            $recentHistory

            $toolPrompt

            Respond to the user's command by deciding your action and speech.
            Always reply in the following JSON format ONLY:
            {
                "thought": "Your internal reasoning or diagnostic",
                "speech": "What you output to the user (matching your persona/role)",
                "action": "One of: IDLE, WALK, RUN, JUMP",
                "moveset_name": "Optional exact animation name if needed",
                "direction_x": Float (horizontal velocity, negative is left, positive is right),
                "direction_y": Float (vertical velocity, negative is up, positive is down),
                "duration_frames": Integer (how long the action lasts, e.g. 180 frames = 3 seconds),
                "tool_call": { "name": "tool_name", "args": { "param": "value" } } // Optional. Provide if using a tool.
            }
            Do not output any markdown formatting, only pure JSON.
        """.trimIndent()

        val engine = getEngine()
        val jsonString = engine.generateActionJson(prompt, systemPrompt)
        
        // Sanitize string if LLM included markdown blocks
        val cleanedJson = jsonString.replace("```json", "").replace("```", "").trim()
        
        val llmResponse = jsonParser.decodeFromString<LlmResponse>(cleanedJson)
        
        return OverlayLlmDirective(
            action = llmResponse.action,
            movesetName = llmResponse.moveset_name,
            vx = llmResponse.direction_x,
            vy = llmResponse.direction_y,
            durationFrames = llmResponse.duration_frames,
            targetDx = llmResponse.direction_x * llmResponse.duration_frames,
            targetDy = llmResponse.direction_y * llmResponse.duration_frames,
            speech = llmResponse.speech,
            toolCall = llmResponse.tool_call
        )
    }
}
