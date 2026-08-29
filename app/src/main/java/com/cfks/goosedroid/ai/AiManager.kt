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
import kotlinx.coroutines.flow.onEach
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
            AiMode.LOCAL_LLAMA -> LocalAiEngine(settings, context)
        }
    }

    /**
     * Build a safe fallback directive when JSON parsing fails.
     */
    private fun safeFallback(message: String, e: Throwable? = null): OverlayLlmDirective {
        if (e != null) {
            EngineLogBus.warn("AiManager", "Fallback response: ${e.message?.take(140)}")
        }
        return OverlayLlmDirective(
            action = "IDLE",
            speech = message,
            targetDx = 0f,
            targetDy = 0f
        )
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
            "You are $characterName, an AI desktop pet."
        }

        val historyLimit = if (settings.mode == AiMode.LOCAL_LLAMA) 500 else 1500
        val recentHistory = if (conversationId != null) {
            MemoryManager.buildContext(ChatRepository(context), conversationId).promptBlock
        } else {
            com.cfks.goosedroid.brain.CharacterRegistry.getInteractionContext(characterName)
        }.take(historyLimit)

        val toolPrompt = if (settings.mode == AiMode.CLOUD_API) {
            toolRegistry.getPromptDescription()
        } else ""


        val localPromptPrefix = if (settings.mode == AiMode.LOCAL_LLAMA) {
            "user: hi\nassistant: {\"thought\":\"greeting\",\"speech\":\"Hello! How can I help you today?\",\"action\":\"JUMP\"}\nuser: what can you do?\nassistant: {\"thought\":\"info\",\"speech\":\"I can walk around your screen, open apps, and chat with you!\",\"action\":\"WALK\"}\nuser: "
        } else ""

        val toolInstruction = "Only call web_drop_zone if user explicitly says they want to send/upload a file. For normal chat, do NOT call any tool. Reply naturally in JSON format with action=IDLE."

        val systemPrompt = if (settings.mode == AiMode.LOCAL_LLAMA) {
            "You are an AI pet. Reply ONLY JSON. Keys: thought, speech, action, tool_call. Match the language of the user's input. $toolInstruction"
        } else {
            """
                $baseRole
                $toolInstruction
                RECENT CONVERSATION HISTORY:
                $recentHistory
                $toolPrompt
                Respond in JSON ONLY:
                {
                    "thought": "brief reasoning",
                    "speech": "your reply text",
                    "action": "One of: IDLE, WALK, RUN, JUMP",
                    "tool_call": { "name": "tool_name", "args": { "key": "value" } }
                }
                No markdown.
            """.trimIndent()
        }

        val engine = getEngine()
        val finalPrompt = localPromptPrefix + prompt

        val jsonString = try {
            engine.generateActionJson(finalPrompt, systemPrompt, conversationId)
        } catch (e: Exception) {
            if (repository.getSettings().fallbackEnabled) {
                EngineLogBus.warn("AiManager", "PRIMARY FAILED -> FALLBACK: ${e.message}")
                try {
                    getEngine(true).generateActionJson(finalPrompt, systemPrompt, conversationId)
                } catch (e2: Exception) {
                    return safeFallback("AI engine unavailable: ${e2.message?.take(80) ?: "unknown error"}", e2)
                }
            } else {
                return safeFallback("AI engine unavailable: ${e.message?.take(80) ?: "unknown error"}", e)
            }
        }

        // Sanitize string if LLM included markdown blocks
        val cleanedJson = jsonString.replace("```json", "").replace("```", "").trim()

        if (cleanedJson.isBlank() || cleanedJson == "{}") {
            return safeFallback("...")
        }

        val llmResponse = try {
            jsonParser.decodeFromString<LlmResponse>(cleanedJson)
        } catch (e: Exception) {
            EngineLogBus.error("AiManager", "JSON PARSE FAILED: ${e.message?.take(160) ?: "unknown"}")
            return safeFallback("Could not parse AI response", e)
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
            "You are $characterName, an AI desktop pet."
        }

        val historyLimit = if (settings.mode == AiMode.LOCAL_LLAMA) 500 else 1500
        val recentHistory = if (conversationId != null) {
            MemoryManager.buildContext(ChatRepository(context), conversationId).promptBlock
        } else {
            com.cfks.goosedroid.brain.CharacterRegistry.getInteractionContext(characterName)
        }.take(historyLimit)

        val toolPrompt = if (settings.mode == AiMode.CLOUD_API) {
            toolRegistry.getPromptDescription()
        } else ""

        val toolInstruction = "Only call web_drop_zone if user explicitly says they want to send/upload a file. For normal chat, do NOT call any tool. Reply naturally in JSON format with action=IDLE."

        val systemPrompt = if (settings.mode == AiMode.LOCAL_LLAMA) {
            "You are an AI pet. Reply ONLY JSON. Keys: thought, speech, action, tool_call. Match the language of the user's input. $toolInstruction"
        } else {
            """
                $baseRole
                $toolInstruction
                RECENT CONVERSATION HISTORY:
                $recentHistory
                $toolPrompt
                Respond in JSON ONLY:
                {
                    "thought": "brief reasoning",
                    "speech": "your reply text",
                    "action": "One of: IDLE, WALK, RUN, JUMP",
                    "tool_call": { "name": "tool_name", "args": { "key": "value" } }
                }
                No markdown.
            """.trimIndent()
        }

        val promptPrefix = """
            user: hi
            assistant: {"thought":"greeting","speech":"Hello! How can I help you?","action":"JUMP"}
            user: what can you do?
            assistant: {"thought":"info","speech":"I can perform actions on your screen and chat with you.","action":"IDLE"}
            user:
        """.trimIndent()

        val finalPrompt = promptPrefix + prompt
        val engine = getEngine()

        return engine.generateActionStream(finalPrompt, systemPrompt, conversationId)
            .catch { e ->
                EngineLogBus.warn("AiManager", "STREAM ERROR: ${e.message?.take(140)}")
                // Emit a safe JSON fallback instead of crashing the flow
                emit("\"thought\":\"error\",\"speech\":\"AI stream failed: ${e.message?.take(60)?.replace("\"", "") ?: "unknown"}\",\"action\":\"IDLE\"")
            }
            .scan("") { accumulated, delta -> accumulated + delta }
            .onEach { fullString ->
                if (fullString.length % 50 == 0) { // Log every 50 chars to avoid spam
                    android.util.Log.d("AiManager", "RAW STREAM: $fullString")
                }
            }
            .map { fullString ->
                val trimmed = fullString.trim()

                // Find the JSON object by counting braces to handle nesting
                var cleanString = trimmed
                val firstBrace = trimmed.indexOf("{")
                if (firstBrace != -1) {
                    var braceCount = 0
                    var lastMatch = -1
                    for (i in firstBrace until trimmed.length) {
                        if (trimmed[i] == '{') braceCount++
                        else if (trimmed[i] == '}') braceCount--

                        if (braceCount == 0) {
                            lastMatch = i
                            break
                        }
                    }
                    if (lastMatch != -1) {
                        cleanString = trimmed.substring(firstBrace, lastMatch + 1)
                    }
                }

                val partialSpeech = PartialJsonParser.extractSpeech(cleanString)
                val finalSpeech = if (partialSpeech.isBlank() && cleanString.isNotBlank() && !cleanString.contains("\"speech\"")) {
                    // Fallback for non-JSON models: strip ChatML tags
                    cleanString.replace("\uD83D\uDDE3", "").replace("\uD83D\uDD28", "").trim()
                } else {
                    partialSpeech
                }

                // Try to parse full JSON if it seems complete
                if (cleanString.endsWith("}")) {
                    try {
                        val sanitized = cleanString.replace("```json", "").replace("```", "").trim()
                        val res = jsonParser.decodeFromString<LlmResponse>(sanitized)
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
                    speech = finalSpeech
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
