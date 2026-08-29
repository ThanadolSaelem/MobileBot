package com.cfks.goosedroid.ai

import android.content.Context
import android.util.Log
import com.cfks.goosedroid.ai.local.LlamaBridge
import com.cfks.goosedroid.download.ModelCatalog
import com.cfks.goosedroid.download.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiEngine(
    private val settings: AiSettings,
    private val context: Context
) : AiEngine {

    companion object {
        private const val TAG = "LocalAiEngine"
        private const val MODEL_ID = "smollm-135m-q4"
        private const val MODEL_FILENAME = "SmolLM-135M-Instruct-v0.2-Q4_K_M.gguf"
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

    private val modelCatalog = ModelCatalog.getModelById(MODEL_ID)

    private suspend fun ensureModelDownloaded(): File {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()

        val modelFile = File(modelDir, MODEL_FILENAME)
        
        // For BUNDLED model (SmolLM 135M), ensure it is copied from assets if missing
        if (MODEL_ID == "smollm-135m-q4" && (!modelFile.exists() || modelFile.length() == 0L)) {
            Log.i(TAG, "Bundled model missing from internal storage, copying from assets...")
            try {
                context.assets.open("models/$MODEL_FILENAME").use { input ->
                    java.io.FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Bundled model copied successfully: ${modelFile.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy bundled model from assets: ${e.message}")
            }
        }

        if (modelFile.exists() && modelFile.length() > 0) {
            return modelFile
        }

        // Download from CDN for non-bundled models
        modelCatalog?.let { catalogModel ->
            val downloadUrl = ModelCatalog.getDownloadUrl(catalogModel)
            if (downloadUrl.isNotBlank()) {
                Log.i(TAG, "Model not found locally, downloading from CDN: $downloadUrl")
                ModelDownloadManager.startDownload(context, catalogModel)

                // Wait for download to complete (with timeout)
                var timeout = 300 // 5 minutes
                while (timeout > 0) {
                    val state = ModelDownloadManager.downloadStates.value[catalogModel.id]
                    if (state != null) {
                        Log.i(TAG, "Download state: progress=${state.progress}%, status=${state.status}, downloading=${state.isDownloading}")
                        if (state.status == "Complete") {
                            Log.i(TAG, "Download completed")
                            break
                        } else if (state.status.startsWith("Error")) {
                            Log.e(TAG, "Download failed: ${state.status}")
                            break
                        }
                    } else {
                        Log.i(TAG, "Download state not yet available, waiting...")
                    }
                    delay(1000)
                    timeout--
                }

                if (modelFile.exists() && modelFile.length() > 0) {
                    Log.i(TAG, "Model downloaded successfully: ${modelFile.length()} bytes")
                    return@let modelFile
                } else {
                    Log.e(TAG, "Model file not found after download attempt")
                }
            }
        }

        Log.e(TAG, "Failed to download model from CDN")
        return modelFile
    }

    private fun ensureModelLoaded(): Boolean {
        val modelFile = runBlocking { ensureModelDownloaded() }

        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            return false
        }

        // Only reload if path or context length changed
        if (lastLoadedModel == modelFile.absolutePath && lastLoadedCtx == settings.contextLength) {
            return true
        }

        Log.i(TAG, "Loading model: ${modelFile.name} (ctx=${settings.contextLength})")
        val success = LlamaBridge.loadModel(
            modelPath = modelFile.absolutePath,
            nCtx = settings.contextLength,
            nThreads = 4
        )

        if (success) {
            lastLoadedModel = modelFile.absolutePath
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
                temp = 0.7f, // Higher temp for more variety in responses
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.3f, // Penalize repetition
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
                temp = 0.7f, // Higher temp for more variety
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.3f, // Penalize repetition
                callback = callback
            )
            close()
        }
        awaitClose {
            LlamaBridge.stopCompletion()
        }
    }

    private fun buildPrompt(system: String, user: String): String {
        // SmolLM 135M uses ChatML format - simpler format for better JSON output
        // Keep it in English since model is English-focused
        val prompt = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n{"
        Log.d(TAG, "FULL PROMPT: $prompt")
        return prompt
    }
}