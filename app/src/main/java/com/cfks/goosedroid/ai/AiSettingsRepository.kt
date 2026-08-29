package com.cfks.goosedroid.ai

import android.content.Context
import android.content.SharedPreferences
import java.io.File

enum class AiMode {
    CLOUD_API,
    LOCAL_LLAMA
}

data class AiSettings(
    val mode: AiMode = AiMode.LOCAL_LLAMA,
    val cloudApiKey: String = "",
    val cloudBaseUrl: String = "https://api.openai.com/v1/",
    val cloudModelName: String = "gpt-4o-mini",
    val localModelPath: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val topK: Int = 40,
    val contextLength: Int = 2048,
    val maxTokens: Int = 512,
    val globalSystemPrompt: String = "",
    val fallbackEnabled: Boolean = false,
    val secondaryApiKey: String = "",
    val secondaryBaseUrl: String = "",
    val secondaryModelName: String = ""
)

class AiSettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences

    init {
        // Using plain SharedPreferences instead of EncryptedSharedPreferences
        // to avoid Keystore/Knox issues on some Samsung devices.
        prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    }

    fun getSettings(): AiSettings {
        val modeStr = prefs.getString("ai_mode", AiMode.LOCAL_LLAMA.name) ?: AiMode.LOCAL_LLAMA.name
        val mode = try { AiMode.valueOf(modeStr) } catch (e: Exception) { AiMode.LOCAL_LLAMA }

        var localPath = prefs.getString("local_model_path", "") ?: ""
        if (localPath.isBlank()) {
            val bundled = File(context.filesDir, "models/SmolLM-135M-Instruct-v0.2-Q4_K_M.gguf")
            localPath = bundled.absolutePath
        }

        return AiSettings(
            mode = mode,
            cloudApiKey = prefs.getString("cloud_api_key", "") ?: "",
            cloudBaseUrl = prefs.getString("cloud_base_url", "https://api.openai.com/v1/") ?: "https://api.openai.com/v1/",
            cloudModelName = prefs.getString("cloud_model_name", "gpt-4o-mini") ?: "gpt-4o-mini",
            localModelPath = localPath,
            temperature = prefs.getFloat("temperature", 0.7f),
            topP = prefs.getFloat("top_p", 1.0f),
            topK = prefs.getInt("top_k", 40),
            contextLength = prefs.getInt("context_length", 2048),
            maxTokens = prefs.getInt("max_tokens", 1024),
            globalSystemPrompt = prefs.getString("global_system_prompt", "") ?: "",
            fallbackEnabled = prefs.getBoolean("fallback_enabled", false),
            secondaryApiKey = prefs.getString("secondary_api_key", "") ?: "",
            secondaryBaseUrl = prefs.getString("secondary_base_url", "") ?: "",
            secondaryModelName = prefs.getString("secondary_model_name", "") ?: ""
        )
    }

    fun saveSettings(settings: AiSettings) {
        prefs.edit().apply {
            putString("ai_mode", settings.mode.name)
            putString("cloud_api_key", settings.cloudApiKey)
            putString("cloud_base_url", settings.cloudBaseUrl)
            putString("cloud_model_name", settings.cloudModelName)
            putString("local_model_path", settings.localModelPath)
            putFloat("temperature", settings.temperature)
            putFloat("top_p", settings.topP)
            putInt("top_k", settings.topK)
            putInt("context_length", settings.contextLength)
            putInt("max_tokens", settings.maxTokens)
            putString("global_system_prompt", settings.globalSystemPrompt)
            putBoolean("fallback_enabled", settings.fallbackEnabled)
            putString("secondary_api_key", settings.secondaryApiKey)
            putString("secondary_base_url", settings.secondaryBaseUrl)
            putString("secondary_model_name", settings.secondaryModelName)
            apply()
        }
    }
}
