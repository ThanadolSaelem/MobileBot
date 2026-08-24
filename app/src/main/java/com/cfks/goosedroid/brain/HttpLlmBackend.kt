package com.cfks.goosedroid.brain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpLlmBackend(
    var endpointUrl: String = "http://127.0.0.1:8080/completion"
) : LlmBackend {

    private val TAG = "HttpLlmBackend"

    override val backendName: String = "llama-server (Dev HTTP)"
    override val backendType: LlmBackendType = LlmBackendType.LOCAL_SERVER

    override fun isAvailable(): Boolean {
        return endpointUrl.isNotBlank() && (endpointUrl.startsWith("http://") || endpointUrl.startsWith("https://"))
    }

    override fun getStatusDetails(): String = "Endpoint: $endpointUrl"

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        repeatPenalty: Float
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 3500
            conn.readTimeout = 8000
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("prompt", prompt)
                put("n_predict", maxTokens)
                put("temperature", temp.toDouble())
                put("repeat_penalty", repeatPenalty.toDouble())
                put("stop", listOf("<|im_end|>", "<|endoftext|>"))
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val responseStr = reader.readText()
                    val respJson = JSONObject(responseStr)
                    return@withContext respJson.optString("content", respJson.optString("text", null))
                }
            } else {
                Log.w(TAG, "HTTP server returned code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "llama-server not reachable (${e.message})")
            null
        }
    }
}
