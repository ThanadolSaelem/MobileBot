package com.cfks.goosedroid.brain

import android.util.Log
import com.cfks.goosedroid.GooseDesktop.PetLlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PetLlamaBackend(
    private var modelPath: String = "",
    private val nCtx: Int = 1024,
    private val nThreads: Int = 4
) : LlmBackend {

    private val TAG = "PetLlamaBackend"
    private var isModelLoaded = false

    override val backendName: String = "PetLlama On-Device (GGUF)"
    override val backendType: LlmBackendType = LlmBackendType.ON_DEVICE_GGUF

    override fun isAvailable(): Boolean {
        if (!PetLlama.isNativeAvailable()) return false
        val file = File(modelPath)
        return file.exists() && file.length() > 0
    }

    override fun getStatusDetails(): String {
        return if (!PetLlama.isNativeAvailable()) {
            "Native library (ARM64) waiting for on-device hardware"
        } else if (!File(modelPath).exists()) {
            "Model file not found at: $modelPath (Upload .gguf to activate)"
        } else if (isModelLoaded) {
            "Model loaded in RAM (ctx: $nCtx, threads: $nThreads)"
        } else {
            "Ready to load: ${File(modelPath).name}"
        }
    }

    fun setModelPath(path: String) {
        modelPath = path
        isModelLoaded = false
    }

    suspend fun loadModelIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        if (isModelLoaded) return@withContext true

        try {
            Log.i(TAG, "Loading GGUF model: $modelPath")
            isModelLoaded = PetLlama.loadModel(modelPath, nCtx, nThreads)
            isModelLoaded
        } catch (e: Throwable) {
            Log.e(TAG, "Failed loading GGUF model", e)
            false
        }
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        repeatPenalty: Float
    ): String? = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext null
        if (!isModelLoaded) {
            val loaded = loadModelIfNeeded()
            if (!loaded) return@withContext null
        }

        try {
            PetLlama.nativeGenerate(prompt, maxTokens, temp, repeatPenalty)
        } catch (e: Throwable) {
            Log.e(TAG, "Inference error in nativeGenerate", e)
            null
        }
    }
}
