package com.cfks.goosedroid.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiMode {
    CLOUD_API,
    LOCAL_LLAMA
}

data class AiSettings(
    val mode: AiMode = AiMode.CLOUD_API,
    val cloudApiKey: String = "",
    val cloudBaseUrl: String = "https://api.openai.com/v1/",
    val cloudModelName: String = "gpt-4o-mini",
    val localModelPath: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 1024,
    val globalSystemPrompt: String = "",
    val fallbackEnabled: Boolean = false,
    val secondaryApiKey: String = "",
    val secondaryBaseUrl: String = "",
    val secondaryModelName: String = ""
)

class AiSettingsRepository(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            "ai_secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getSettings(): AiSettings {
        val modeStr = prefs.getString("ai_mode", AiMode.CLOUD_API.name) ?: AiMode.CLOUD_API.name
        val mode = try { AiMode.valueOf(modeStr) } catch (e: Exception) { AiMode.CLOUD_API }
        return AiSettings(
            mode = mode,
            cloudApiKey = prefs.getString("cloud_api_key", "") ?: "",
            cloudBaseUrl = prefs.getString("cloud_base_url", "https://api.openai.com/v1/") ?: "https://api.openai.com/v1/",
            cloudModelName = prefs.getString("cloud_model_name", "gpt-4o-mini") ?: "gpt-4o-mini",
            localModelPath = prefs.getString("local_model_path", "") ?: "",
            temperature = prefs.getFloat("temperature", 0.7f),
            topP = prefs.getFloat("top_p", 1.0f),
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
