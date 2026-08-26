package com.cfks.goosedroid.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CloudAiEngine(private val settings: AiSettings) : AiEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun generateActionJson(prompt: String, systemPrompt: String, conversationId: Long?): String = withContext(Dispatchers.IO) {
        if (settings.cloudApiKey.isBlank()) {
            EngineLogBus.error("CloudEngine", "API KEY MISSING — configure it in settings")
            throw Exception("API Key is missing. Please configure it in settings.")
        }

        val jsonBody = JSONObject().apply {
            put("model", settings.cloudModelName)
            
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        
        var baseUrl = settings.cloudBaseUrl
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        val url = "${baseUrl}chat/completions"
        EngineLogBus.info("CloudEngine", "REQUEST → ${settings.cloudModelName} @ ${Request.Builder().url(url).build().url.host}")
        
        fun updateStatus(text: String?) {
            conversationId?.let { ChatEngine.setStatus(it, text) }
            // If conversationId is null (Overlay), we might still want to show it 
            // in a global status, but the plan focused on per-conversation.
        }

        updateStatus("CONTACTING ${settings.cloudModelName}...")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.cloudApiKey}")
            .post(requestBody)
            .build()

        // Retry with exponential backoff on 429 rate limit
        val maxRetries = 3
        var attempt = 0
        while (attempt <= maxRetries) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseData = response.body?.string() ?: throw IOException("Empty response")
                val jsonResponse = JSONObject(responseData)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")
                    EngineLogBus.info("CloudEngine", "RESPONSE OK (${content.length} chars)")
                    updateStatus(null)
                    return@withContext content
                } else {
                    EngineLogBus.error("CloudEngine", "No choices found in response")
                    throw Exception("No choices found in response")
                }
            } else if (response.code == 429 && attempt < 3) {
                attempt++
                val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong() // 1s, 2s, 4s
                EngineLogBus.warn("CloudEngine", "Rate limited (429). Retry $attempt/3 in ${delayMs}ms")
                updateStatus("RETRYING $attempt/3 — RATE LIMITED (${delayMs / 1000}s)")
                delay(delayMs.milliseconds)
                continue
            } else {
                val errorBody = response.body?.string()
                EngineLogBus.error("CloudEngine", "HTTP ${response.code}: ${errorBody?.take(180) ?: "no body"}")
                throw IOException("Unexpected code $response: $errorBody")
            }
        }
        throw IOException("Max retries (3) exceeded for rate limit")
    }
}