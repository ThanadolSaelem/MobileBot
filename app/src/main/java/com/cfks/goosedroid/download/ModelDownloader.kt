package com.cfks.goosedroid.download

import android.content.Context
import android.os.StatFs
import com.cfks.goosedroid.ai.EngineLogBus
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import okio.Okio
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.Locale

/**
 * Custom model downloader with resume, progress, checksum verification, and atomic write.
 */
class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor(ProgressInterceptor())
        .build()

    /**
     * Download a model with progress updates.
     */
    suspend fun download(
        url: String,
        destinationFile: File,
        expectedSha256: String? = null,
        progressChannel: Channel<DownloadProgress>,
        scope: CoroutineScope
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tmpFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        val downloadsDir = tmpFile.parentFile!!
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        // Disk space preflight (require 2x file size as buffer)
        val stat = StatFs(downloadsDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

        var currentBytes = 0L
        var totalBytes = -1L

        // Resume support: check existing temp file
        if (tmpFile.exists()) {
            currentBytes = tmpFile.length()
            EngineLogBus.info("ModelDownloader", "Resuming download from $currentBytes bytes")
        }

        try {
            // HEAD request to get total size and check resume support
            val headRequest = Request.Builder().url(url).head().build()
            val headResponse = client.newCall(headRequest).execute()
            headResponse.use {
                if (!it.isSuccessful) {
                    return@withContext DownloadResult(false, "HEAD request failed: ${it.code}")
                }
                totalBytes = it.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = it.header("Accept-Ranges")
                val supportsResume = acceptRanges == "bytes"

                if (totalBytes > 0 && availableBytes < totalBytes * 2) {
                    return@withContext DownloadResult(false, "Insufficient disk space. Need ~${formatBytes(totalBytes * 2)} free.")
                }

                if (currentBytes > 0 && !supportsResume) {
                    EngineLogBus.warn("ModelDownloader", "Server doesn't support resume, restarting")
                    tmpFile.delete()
                    currentBytes = 0
                }
            }

            // Build GET request with Range header for resume
            val requestBuilder = Request.Builder().url(url)
            if (currentBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$currentBytes-")
            }
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            val resp = response
            if (!resp.isSuccessful && resp.code != 206) { // 206 = Partial Content
                return@withContext DownloadResult(false, "Download failed: ${resp.code} ${resp.message}")
            }

            val body = resp.body ?: return@withContext DownloadResult(false, "Empty response body")
            val source = body.source()
            val fileSink = tmpFile.sink().buffer()

            val buffer = ByteArray(8192)
            var lastProgressTime = System.currentTimeMillis()
            var lastProgressBytes = currentBytes

            while (true) {
                val read = source.read(buffer)
                if (read == -1) break

                fileSink.write(buffer, 0, read)
                currentBytes += read

                // Throttle progress updates to ~4 FPS
                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= 250 || currentBytes == totalBytes) {
                    val speed = if (now > lastProgressTime) {
                        (currentBytes - lastProgressBytes) * 1000L / (now - lastProgressTime)
                    } else 0L
                    val eta = if (speed > 0 && totalBytes > 0) {
                        (totalBytes - currentBytes) / speed
                    } else -1L

                    val progress = if (totalBytes > 0) (currentBytes * 100F / totalBytes) else -1F
                    progressChannel.trySend(DownloadProgress(
                        currentBytes = currentBytes,
                        totalBytes = totalBytes,
                        progressPercent = progress,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    ))
                    lastProgressTime = now
                    lastProgressBytes = currentBytes
                }

                // Check cancellation
                scope.coroutineContext.ensureActive()
            }

            fileSink.close()

            // Verify checksum
            if (expectedSha256 != null) {
                progressChannel.trySend(DownloadProgress(
                    currentBytes = currentBytes,
                    totalBytes = totalBytes,
                    progressPercent = 100F,
                    speedBytesPerSec = 0,
                    etaSeconds = 0,
                    status = "Verifying checksum..."
                ))
                val actualSha256 = calculateSha256(tmpFile)
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    tmpFile.delete()
                    return@withContext DownloadResult(false, "Checksum mismatch! Expected $expectedSha256, got $actualSha256")
                }
            }

            // Atomic rename
            if (destinationFile.exists()) destinationFile.delete()
            if (!tmpFile.renameTo(destinationFile)) {
                return@withContext DownloadResult(false, "Failed to move temp file to destination")
            }

            progressChannel.trySend(DownloadProgress(
                currentBytes = currentBytes,
                totalBytes = totalBytes,
                progressPercent = 100F,
                speedBytesPerSec = 0,
                etaSeconds = 0,
                status = "Complete"
            ))

            EngineLogBus.info("ModelDownloader", "Download complete: ${destinationFile.name}")
            DownloadResult(true, null)

        } catch (e: Exception) {
            EngineLogBus.error("ModelDownloader", "Download error: ${e.message}")
            DownloadResult(false, e.message ?: "Unknown error")
        }
    }

    private suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02X".format(it) }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
            else -> String.format(Locale.US, "%.1f KB", bytes / 1000.0)
        }
    }

    /** Interceptor to expose response body for progress tracking */
    private class ProgressInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return chain.proceed(chain.request())
        }
    }
}

/** Download progress data class */
data class DownloadProgress(
    val currentBytes: Long,
    val totalBytes: Long,
    val progressPercent: Float, // -1 if unknown
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val status: String = "Downloading"
)

/** Download result */
data class DownloadResult(
    val success: Boolean,
    val errorMessage: String?
)