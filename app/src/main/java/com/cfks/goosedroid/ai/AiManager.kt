package com.cfks.goosedroid.ai

import android.content.Context
import com.cfks.goosedroid.OverlayLlmDirective
import com.cfks.goosedroid.brain.MemoryManager
import com.cfks.goosedroid.data.ChatRepository
import com.cfks.goosedroid.plugins.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.serialization.json.Json

class AiManager(private val context: Context) {
    
    private val repository = AiSettingsRepository(context)
    val toolRegistry = ToolRegistry() // MCP-Lite Registry
    
    // Configured Json parser that ignores unknown keys
    private val jsonParser = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    private fun getEngine(secondary: Boolean = false): AiEngine {
        val settings = repository.getSettings()
        if (secondary && settings.fallbackEnabled && settings.secondaryApiKey.isNotBlank()) {
            val secondarySettings = settings.copy(
                cloudApiKey = settings.secondaryApiKey,
                cloudBaseUrl = settings.secondaryBaseUrl,
                cloudModelName = settings.secondaryModelName
            )
            return CloudAiEngine(secondarySettings)
        }
        return when (settings.mode) {
            AiMode.CLOUD_API -> CloudAiEngine(settings)
            AiMode.LOCAL_LLAMA -> LocalAiEngine(settings)
        }
    }

    suspend fun getNextAction(
        prompt: String,
        characterPersona: String = "",
        characterName: String = "Unit",
        conversationId: Long? = null,
    ): OverlayLlmDirective {
        val settings = repository.getSettings()
        EngineLogBus.debug("AiManager", "MODE=${settings.mode} UNIT=$characterName")
        val baseRole = if (characterPersona.isNotBlank()) {
            "You are $characterName. $characterPersona"
        } else {
            "You are $characterName, an autonomous desktop unit operating on the user's screen."
        }
        
        val recentHistory = if (conversationId != null) {
            // Phase 2: Room-backed memory (summaries + sliding window)
            MemoryManager.buildContext(ChatRepository(context), conversationId).promptBlock
        } else {
            // Overlay/inline paths without a conversation still get in-RAM context
            com.cfks.goosedroid.brain.CharacterRegistry.getInteractionContext(characterName)
        }

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
        val jsonString = try {
            engine.generateActionJson(prompt, systemPrompt, conversationId)
        } catch (e: Exception) {
            if (repository.getSettings().fallbackEnabled) {
                EngineLogBus.warn("AiManager", "PRIMARY FAILED -> FALLBACK: ${e.message}")
                getEngine(true).generateActionJson(prompt, systemPrompt, conversationId)
            } else throw e
        }
        
        // Sanitize string if LLM included markdown blocks
        val cleanedJson = jsonString.replace("```json", "").replace("```", "").trim()
        
        val llmResponse = try {
            jsonParser.decodeFromString<LlmResponse>(cleanedJson)
        } catch (e: Exception) {
            EngineLogBus.error("AiManager", "JSON PARSE FAILED: ${e.message?.take(160) ?: "unknown"}")
            throw e
        }
        
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

    suspend fun getNextActionStream(
        prompt: String,
        characterPersona: String = "",
        characterName: String = "Unit",
        conversationId: Long? = null,
    ): Flow<OverlayLlmDirective> {
        val settings = repository.getSettings()
        val baseRole = if (characterPersona.isNotBlank()) {
            "You are $characterName. $characterPersona"
        } else {
            "You are $characterName, an autonomous desktop unit operating on the user's screen."
        }
        
        val recentHistory = if (conversationId != null) {
            MemoryManager.buildContext(ChatRepository(context), conversationId).promptBlock
        } else {
            com.cfks.goosedroid.brain.CharacterRegistry.getInteractionContext(characterName)
        }

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
                "direction_x": Float,
                "direction_y": Float,
                "duration_frames": Integer,
                "tool_call": { "name": "tool_name", "args": { "param": "value" } }
            }
            Do not output any markdown formatting, only pure JSON.
        """.trimIndent()

        val engine = getEngine()
        var receivedAnyData = false
        
        return engine.generateActionStream(prompt, systemPrompt, conversationId)
            .onEach { receivedAnyData = true }
            .catch { e ->
                if (!receivedAnyData) {
                    EngineLogBus.warn("AiManager", "STREAM FAILED: ${e.message} — TRYING NON-STREAM FALLBACK")
                    try {
                        val fullResponse = engine.generateActionJson(prompt, systemPrompt, conversationId)
                        emit(fullResponse)
                    } catch (jsonErr: Exception) {
                        if (repository.getSettings().fallbackEnabled) {
                            EngineLogBus.warn("AiManager", "FALLBACK FAILED -> SECONDARY ENGINE")
                            emitAll(getEngine(true).generateActionStream(prompt, systemPrompt, conversationId))
                        } else throw jsonErr
                    }
                } else {
                    EngineLogBus.error("AiManager", "STREAM INTERRUPTED: ${e.message}")
                    throw e
                }
            }
            .scan("") { accumulated, delta -> accumulated + delta }
            .map { fullString ->
                val partialSpeech = PartialJsonParser.extractSpeech(fullString)
                
                // Try to parse full JSON if it seems complete
                if (fullString.trim().endsWith("}")) {
                    try {
                        val cleaned = fullString.replace("```json", "").replace("```", "").trim()
                        val res = jsonParser.decodeFromString<LlmResponse>(cleaned)
                        return@map OverlayLlmDirective(
                            action = res.action,
                            movesetName = res.moveset_name,
                            vx = res.direction_x,
                            vy = res.direction_y,
                            durationFrames = res.duration_frames,
                            targetDx = res.direction_x * res.duration_frames,
                            targetDy = res.direction_y * res.duration_frames,
                            speech = res.speech,
                            toolCall = res.tool_call
                        )
                    } catch (e: Exception) {
                        // Not fully valid yet or failed parse
                    }
                }
                
                // Emitting partial directive with current speech
                OverlayLlmDirective(
                    action = "IDLE", // Default until full JSON parsed
                    speech = partialSpeech
                )
            }
    }

    /**
     * Phase 2: freeform completion used by the memory compactor.
     * Returns the raw model output (no JSON parsing), or null on failure.
     */
    suspend fun summarizeTranscript(transcript: String, characterName: String): String? {
        return try {
            val system = """
                You are the long-term memory compactor for an AI desktop pet named $characterName.
                Condense the conversation transcript into a compact factual summary written in Thai.
                Keep: user preferences, personal facts, commitments, and unresolved topics.
                Be brief (max ~120 words). Output ONLY the summary text — no markdown, no JSON.
            """.trimIndent()
            val out = getEngine().generateActionJson(transcript, system)
            EngineLogBus.debug("AiManager", "SUMMARY GENERATED (${out.length} chars)")
            out.replace("```json", "").replace("```", "").trim().ifBlank { null }
        } catch (e: Exception) {
            EngineLogBus.warn("AiManager", "SUMMARIZE FAILED: ${e.message?.take(120)}")
            null
        }
    }
}
