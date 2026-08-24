package com.cfks.goosedroid.GooseDesktop

import android.util.Log

class PetLlama {

    companion object {
        private const val TAG = "PetLlama"
        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("llama-mobile")
                isLibraryLoaded = true
                Log.i(TAG, "Native library 'llama-mobile' successfully loaded.")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "llama-mobile native library not available on this platform/ABI: ${e.message}")
                isLibraryLoaded = false
            } catch (e: Throwable) {
                Log.w(TAG, "Error loading llama-mobile: ${e.message}")
                isLibraryLoaded = false
            }
        }

        fun isNativeAvailable(): Boolean = isLibraryLoaded

        @JvmStatic
        external fun nativeInit(nativeLibDir: String)

        @JvmStatic
        external fun loadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean

        @JvmStatic
        external fun freeModel()

        @JvmStatic
        external fun stopCompletion()

        @JvmStatic
        external fun systemInfo(): String

        @JvmStatic
        external fun nativeGenerate(
            prompt: String,
            maxTokens: Int,
            temp: Float,
            repeatPenalty: Float
        ): String?
    }
}
