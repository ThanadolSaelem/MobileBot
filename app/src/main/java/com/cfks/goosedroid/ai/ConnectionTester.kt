package com.cfks.goosedroid.ai

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Readiness checks for both execution environments.
 * Results are surfaced via AlertBus (user) and EngineLogBus (console).
 */
object ConnectionTester {

    data class Result(val ok: Boolean, val detail: String, val latencyMs: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Cloud readiness — lightweight authenticated GET {base}/models.
     * Works with every OpenAI-compatible provider (OpenAI, DeepSeek,
     * Mistral, Novita, Ollama).
     */
    suspend fun testCloud(settings: AiSettings): Result = withContext(Dispatchers.IO) {
        if (settings.cloudApiKey.isBlank()) {
            return@withContext Result(false, "API key is empty", 0L)
        }
        val start = SystemClock.elapsedRealtime()
        try {
            val url = settings.cloudBaseUrl.trimEnd('/') + "/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${settings.cloudApiKey}")
                .build()
            client.newCall(request).execute().use { response ->
                val ms = SystemClock.elapsedRealtime() - start
                if (response.isSuccessful) {
                    Result(true, "HTTP ${response.code}", ms)
                } else {
                    Result(false, "HTTP ${response.code}", ms)
                }
            }
        } catch (e: Exception) {
            Result(
                false,
                e.message?.take(120) ?: "network error",
                SystemClock.elapsedRealtime() - start
            )
        }
    }

    /**
     * Local readiness — GGUF file exists, readable and carries the
     * "GGUF" magic header bytes.
     */
    suspend fun testLocal(settings: AiSettings): Result = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        fun done(ok: Boolean, detail: String) =
            Result(ok, detail, SystemClock.elapsedRealtime() - start)

        if (settings.localModelPath.isBlank()) {
            return@withContext done(false, "No model selected — open MODEL_HUB")
        }
        val file = File(settings.localModelPath)
        when {
            !file.exists() -> done(false, "File not found: ${file.name}")
            file.length() < 4 -> done(false, "File too small to be GGUF")
            else -> {
                val magic = file.inputStream().use { ins ->
                    val buf = ByteArray(4)
                    val read = ins.read(buf)
                    if (read == 4) String(buf, Charsets.US_ASCII) else ""
                }
                if (magic == "GGUF") {
                    done(true, "${file.name} (${file.length() / 1_048_576}MB)")
                } else {
                    done(false, "Invalid GGUF header")
                }
            }
        }
    }
}
