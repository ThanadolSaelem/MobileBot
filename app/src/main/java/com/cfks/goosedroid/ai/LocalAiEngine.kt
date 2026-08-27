package com.cfks.goosedroid.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Local AI Engine using llama.cpp via JNI.
 *
 * NOTE: For Google Play distribution, the GGUF model files must be downloaded
 * post-installation since the App Bundle size limit is 150MB.
 *
 * This is currently a stub for the Local AI architecture. To fully enable this (Phase 5):
 * 1. Add `llama.cpp` Android JNI bindings (CMake/NDK, arm64-v8a).
 * 2. Load the downloaded `.gguf` file path.
 * 3. Pass the prompt to the llama.cpp context and parse the output JSON.
 *
 * All engine lifecycle events are emitted to [EngineLogBus] and rendered
 * live in the AI settings console.
 */
class LocalAiEngine(private val settings: AiSettings) : AiEngine {

    override suspend fun generateActionJson(prompt: String, systemPrompt: String, conversationId: Long?): String {
        if (settings.localModelPath.isBlank()) {
            EngineLogBus.warn("LocalEngine", "NO GGUF MODEL SELECTED — pick one in MODEL_HUB or set path")
            throw Exception("Local model path is not set. Please download a model first.")
        }

        EngineLogBus.info("LocalEngine", "INITIALIZING LLAMA.CPP ENGINE...")
        EngineLogBus.info("LocalEngine", "MODEL: ${File(settings.localModelPath).name}")

        EngineLogBus.warn("LocalEngine", "llama.cpp JNI runtime not integrated yet (Phase 5) — returning stub response")

        // Simulated inference latency for stub
        delay(2000)

        EngineLogBus.info("LocalEngine", "STUB RESPONSE GENERATED (2.0s simulated)")

        return """
            {
                "thought": "I am a local AI running on device!",
                "speech": "Local mode activated!",
                "action": "JUMP",
                "direction_x": 0.0,
                "direction_y": -1.5,
                "duration_frames": 120
            }
        """.trimIndent()
    }

    override fun generateActionStream(prompt: String, systemPrompt: String, conversationId: Long?): Flow<String> = flow {
        // Simulated streaming for stub
        
        // Emit chunks of the speech specifically for testing the partial parser
        emit("""{"thought": "Thinking..." """)
        delay(500)
        emit(""", "speech": "I am a local AI """)
        delay(500)
        emit("""streaming my response!", "action": "IDLE"}""")
    }
}
