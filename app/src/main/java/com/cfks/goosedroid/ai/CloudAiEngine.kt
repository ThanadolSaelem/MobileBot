package com.cfks.goosedroid.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun generateActionJson(prompt: String, systemPrompt: String): String = withContext(Dispatchers.IO) {
        if (settings.cloudApiKey.isBlank()) {
            throw Exception("API Key is missing. Please configure it in settings.")
        }

        val jsonBody = JSONObject().apply {
            put("model", settings.cloudModelName)
            
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        
        var baseUrl = settings.cloudBaseUrl
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }
        val url = "${baseUrl}chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.cloudApiKey}")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw IOException("Unexpected code $response: $errorBody")
            }

            val responseData = response.body?.string() ?: throw IOException("Empty response")
            val jsonResponse = JSONObject(responseData)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                return@withContext message.getString("content")
            } else {
                throw Exception("No choices found in response")
            }
        }
    }
}
