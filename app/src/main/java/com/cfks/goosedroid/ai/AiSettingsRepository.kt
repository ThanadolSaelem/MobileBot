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
    val localModelPath: String = ""
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
            localModelPath = prefs.getString("local_model_path", "") ?: ""
        )
    }

    fun saveSettings(settings: AiSettings) {
        prefs.edit().apply {
            putString("ai_mode", settings.mode.name)
            putString("cloud_api_key", settings.cloudApiKey)
            putString("cloud_base_url", settings.cloudBaseUrl)
            putString("cloud_model_name", settings.cloudModelName)
            putString("local_model_path", settings.localModelPath)
            apply()
        }
    }
}
