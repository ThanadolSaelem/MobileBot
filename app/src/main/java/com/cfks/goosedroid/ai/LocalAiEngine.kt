package com.cfks.goosedroid.ai

import android.content.Context
import android.util.Log
import com.cfks.goosedroid.ai.local.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiEngine(
    private val settings: AiSettings,
    context: Context
) : AiEngine {

    companion object {
        private const val TAG = "LocalAiEngine"
        private var lastLoadedModel: String? = null
        private var lastLoadedCtx: Int = 0
        private var backendsInitialized = false
    }

    init {
        if (!backendsInitialized) {
            Log.i(TAG, "INIT: Initializing backends...")
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            LlamaBridge.nativeInit(nativeLibDir)
            backendsInitialized = true
        }
    }

    private fun ensureModelLoaded(): Boolean {
        val modelPath = settings.localModelPath
        val modelFile = File(modelPath)
        
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: $modelPath")
            return false
        }

        // Only reload if path or context length changed
        if (lastLoadedModel == modelPath && lastLoadedCtx == settings.contextLength) {
            return true
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (ctx=${settings.contextLength})")
        val success = LlamaBridge.loadModel(
            modelPath = modelFile.absolutePath,
            nCtx = settings.contextLength,
            nThreads = 4
        )

        if (success) {
            lastLoadedModel = modelPath
            lastLoadedCtx = settings.contextLength
            Log.i(TAG, "Model loaded. System Info: ${LlamaBridge.systemInfo()}")
        } else {
            Log.e(TAG, "Model load failed.")
            lastLoadedModel = null
            lastLoadedCtx = 0
        }

        return success
    }

    override suspend fun generateActionJson(prompt: String, systemPrompt: String, conversationId: Long?): String {
        if (!ensureModelLoaded()) return "{}"

        val fullPrompt = buildPrompt(systemPrompt, prompt)
        
        val result = withContext(Dispatchers.IO) {
            LlamaBridge.nativeGenerateStream(
                prompt = fullPrompt,
                maxTokens = settings.maxTokens,
                temp = 0.2f, // Lower temp for local JSON stability
                topP = settings.topP,
                topK = settings.topK,
                repeatPenalty = 1.1f,
                callback = null
            )
        }
        return "{" + result
    }

    override fun generateActionStream(prompt: String, systemPrompt: String, conversationId: Long?): Flow<String> = callbackFlow {
        if (!ensureModelLoaded()) {
            trySend("Error: Model failed to load")
            close()
            return@callbackFlow
        }

        val fullPrompt = buildPrompt(systemPrompt, prompt)
        trySend("{")

        withContext(Dispatchers.IO) {
            val callback = object : LlamaBridge.GenerationCallback {
                private var buffer = "{"
                override fun onToken(text: String) {
                    buffer += text
                    trySend(text)
                    // Stop if we see the closing brace of the main JSON object
                    if (text.contains("}") && buffer.count { it == '{' } == buffer.count { it == '}' }) {
                        LlamaBridge.stopCompletion()
                    }
                }
                override fun onProgress(progress: Float) {}
            }

            LlamaBridge.nativeGenerateStream(
                prompt = fullPrompt,
                maxTokens = settings.maxTokens,
                temp = 0.2f, // Lower temp for local JSON stability
                topP = settings.topP,
                topK = settings.topK,
                repeatPenalty = 1.1f,
                callback = callback
            )
            close()
        }
        awaitClose { 
            LlamaBridge.stopCompletion()
        }
    }

    private fun buildPrompt(system: String, user: String): String {
        // ChatML format for SmolLM
        // Adding { at the end to force JSON response
        val prompt = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n{"
        Log.d(TAG, "FULL PROMPT: $prompt")
        return prompt
    }
}
