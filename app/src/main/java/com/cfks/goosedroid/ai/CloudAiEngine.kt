package com.cfks.goosedroid.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

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
            put("temperature", settings.temperature)
            put("top_p", settings.topP)
            put("max_tokens", settings.maxTokens)
            
            val messages = JSONArray().apply {
                if (settings.globalSystemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", settings.globalSystemPrompt)
                    })
                }
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
        if (!baseUrl.endsWith("/")) baseUrl += "/"
        val url = "${baseUrl}chat/completions"
        EngineLogBus.info("CloudEngine", "REQUEST → ${settings.cloudModelName} @ ${Request.Builder().url(url).build().url.host}")
        
        fun updateStatus(text: String?) {
            conversationId?.let { ChatEngine.setStatus(it, text) }
        }

        updateStatus("CONTACTING ${settings.cloudModelName}...")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.cloudApiKey}")
            .addHeader("HTTP-Referer", "https://github.com/cfks/goosedroid")
            .addHeader("X-Title", "GooseDroid")
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
            } else if (response.code == 429 && attempt < maxRetries) {
                attempt++
                val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                EngineLogBus.warn("CloudEngine", "Rate limited (429). Retry $attempt/$maxRetries in ${delayMs}ms")
                updateStatus("RETRYING $attempt/$maxRetries — RATE LIMITED (${delayMs / 1000}s)")
                delay(delayMs.milliseconds)
                continue
            } else {
                val status = response.code
                val errorBody = response.body?.string()
                
                val parsedError = if (errorBody != null && errorBody.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(errorBody)
                        if (json.has("error")) {
                            val errorObj = json.get("error")
                            if (errorObj is JSONObject) errorObj.optString("message", "Unknown error")
                            else errorObj.toString()
                        } else errorBody
                    } catch (e: Exception) { errorBody }
                } else errorBody ?: "No error body"

                val finalMsg = when (status) {
                    401 -> "API Key is invalid or expired (401)"
                    402 -> "Payment required — check your credits (402)"
                    429 -> "Rate limit exceeded (429) after retries"
                    404 -> "Model not found or URL incorrect (404)"
                    else -> "Server Error ($status): $parsedError"
                }

                EngineLogBus.error("CloudEngine", finalMsg)
                throw IOException(finalMsg)
            }
        }
        throw IOException("Max retries (3) exceeded for rate limit")
    }

    override fun generateActionStream(prompt: String, systemPrompt: String, conversationId: Long?): Flow<String> = callbackFlow {
        if (settings.cloudApiKey.isBlank()) {
            close(Exception("API Key is missing"))
            return@callbackFlow
        }

        val jsonBody = JSONObject().apply {
            put("model", settings.cloudModelName)
            put("temperature", settings.temperature)
            put("top_p", settings.topP)
            put("max_tokens", settings.maxTokens)
            put("stream", true)
            
            val messages = JSONArray().apply {
                if (settings.globalSystemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", settings.globalSystemPrompt)
                    })
                }
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
        if (!baseUrl.endsWith("/")) baseUrl += "/"
        val url = "${baseUrl}chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.cloudApiKey}")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("HTTP-Referer", "https://github.com/cfks/goosedroid")
            .addHeader("X-Title", "GooseDroid")
            .post(requestBody)
            .build()

        val eventSourceListener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                EngineLogBus.info("CloudEngine", "SSE CONNECTION OPENED")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    close()
                } else {
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            if (delta != null && delta.has("content")) {
                                trySend(delta.getString("content"))
                            }
                        }
                    } catch (e: Exception) {
                        // Keep going on malformed chunks
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val status = response?.code ?: -1
                val errBody = try { response?.peekBody(2048)?.string() } catch (e: Exception) { null }
                
                val parsedError = if (errBody != null && errBody.trim().startsWith("{")) {
                    try {
                        val json = JSONObject(errBody)
                        if (json.has("error")) {
                            val errorObj = json.get("error")
                            if (errorObj is JSONObject) {
                                errorObj.optString("message", "Unknown error")
                            } else {
                                errorObj.toString()
                            }
                        } else {
                            errBody
                        }
                    } catch (e: Exception) { errBody }
                } else {
                    errBody ?: t?.message ?: "Unknown SSE error"
                }

                val finalMsg = when (status) {
                    401 -> "API Key is invalid or expired (401)"
                    402 -> "Payment required — check your credits (402)"
                    429 -> "Rate limit exceeded (429) — slow down"
                    404 -> "Model not found or URL incorrect (404)"
                    -1 -> "Network error: ${t?.message}"
                    else -> "Server Error ($status): $parsedError"
                }

                EngineLogBus.error("CloudEngine", finalMsg)
                close(IOException(finalMsg))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val sseClient = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            
        val eventSource = EventSources.createFactory(sseClient).newEventSource(request, eventSourceListener)

        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
