package com.cfks.goosedroid.ai.local

import android.util.Log

object LlamaBridge {
    private const val TAG = "LlamaBridge"

    interface GenerationCallback {
        fun onToken(text: String)
        fun onProgress(progress: Float)
    }

    init {
        try {
            System.loadLibrary("llama-mobile")
            Log.i(TAG, "Native library 'llama-mobile' loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load native library 'llama-mobile'", e)
        }
    }

    external fun nativeInit(libDir: String)
    external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    external fun freeModel()
    external fun stopCompletion()
    external fun systemInfo(): String

    /**
     * Now supports streaming via callback
     */
    external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        callback: GenerationCallback?
    ): String

    // Keep the old one for compatibility or non-streamed uses
    fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): String {
        return nativeGenerateStream(prompt, maxTokens, temp, topP, topK, repeatPenalty, null)
    }
}
