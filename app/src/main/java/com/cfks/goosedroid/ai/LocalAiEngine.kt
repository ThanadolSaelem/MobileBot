package com.cfks.goosedroid.ai

import kotlinx.coroutines.delay

/**
 * Local AI Engine using llama.cpp via JNI.
 * 
 * NOTE: For Google Play distribution, the GGUF model files must be downloaded
 * post-installation since the App Bundle size limit is 150MB.
 * 
 * This is currently a stub for the Local AI architecture. To fully enable this:
 * 1. Add `llama.cpp` Android JNI bindings (e.g. from chatfire/llama.cpp-android or similar).
 * 2. Load the downloaded `.gguf` file path.
 * 3. Pass the prompt to the llama.cpp context and parse the output JSON.
 */
class LocalAiEngine(private val settings: AiSettings) : AiEngine {

    override suspend fun generateActionJson(prompt: String, systemPrompt: String): String {
        if (settings.localModelPath.isBlank()) {
            throw Exception("Local model path is not set. Please download a model first.")
        }
        
        // TODO: Initialize llama.cpp context with settings.localModelPath
        // TODO: Evaluate prompt
        // TODO: Return JSON string

        // Simulated delay for stub
        delay(2000)

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
}
