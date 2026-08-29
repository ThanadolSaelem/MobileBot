package com.cfks.goosedroid.download

import android.content.Context
import com.cfks.goosedroid.ai.EngineLogBus
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class ModelDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class Progress(val cur: Long, val tot: Long, val pct: Float, val spd: Long, val eta: Long)
    data class Result(val ok: Boolean, val err: String?)

    /**
     * Download a file directly from the given URL (jsDelivr CDN or any direct link).
     * No mirrors - just direct download with proper headers.
     */
    suspend fun download(
        url: String,
        dst: File,
        progress: Channel<Progress>,
        scope: CoroutineScope
    ): Result = withContext(Dispatchers.IO) {
        var errors = mutableListOf<String>()

        try {
            EngineLogBus.info("Downloader", "GET $url")
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                val msg = "HTTP ${resp.code}"
                errors.add(msg)
                EngineLogBus.error("Downloader", msg)
                resp.close()
                return@withContext Result(false, errors.joinToString(" | "))
            }

            val body = resp.body ?: run { errors.add("Empty body"); return@withContext Result(false, errors.joinToString(" | ")) }
            val total = body.contentLength()
            val src = body.source()
            val snk = dst.sink().buffer()
            val buf = ByteArray(32768)
            var cur = 0L; var last = 0L; var t0 = System.currentTimeMillis()

            while (true) {
                val n = src.read(buf); if (n == -1) break
                snk.write(buf, 0, n); cur += n
                val now = System.currentTimeMillis()
                if (now - t0 >= 400 || cur == total) {
                    val spd = if (now > t0) ((cur - last) * 1000L / (now - t0)) else 0L
                    val pct = if (total > 0) (cur * 100f / total).coerceIn(0f, 100f) else -1f
                    val eta = if (spd > 0 && total > 0) (total - cur) / spd else -1L
                    progress.trySend(Progress(cur, total, pct, spd, eta))
                    last = cur; t0 = now
                }
                scope.coroutineContext.ensureActive()
            }
            snk.close()
            progress.trySend(Progress(cur, total, 100f, 0, 0))
            EngineLogBus.info("Downloader", "Done: ${dst.name} (${cur}B)")
            Result(true, null)

        } catch (e: CancellationException) {
            Result(false, "Cancelled")
        } catch (e: Exception) {
            errors.add(e.message ?: "Unknown error")
            EngineLogBus.error("Downloader", errors.last())
            Result(false, errors.joinToString(" | "))
        } finally {
            progress.close()
        }
    }
}