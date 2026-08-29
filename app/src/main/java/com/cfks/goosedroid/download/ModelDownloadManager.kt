package com.cfks.goosedroid.download

import android.content.Context
import android.content.Intent
import com.cfks.goosedroid.ai.EngineLogBus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Global singleton that manages GGUF model downloads.
 *
 * Uses ModelDownloader (OkHttp + hf-mirror.com proxy) under the hood
 * and exposes download progress via StateFlow for the UI to observe.
 */
object ModelDownloadManager {

    data class DownloadState(
        val isDownloading: Boolean,
        val curBytes: Long,
        val totBytes: Long,
        val progress: Float,   // 0-100
        val speed: Long,       // bytes/sec
        val eta: Long,         // seconds remaining, -1 if unknown
        val status: String     // "Starting...", "Complete", "Error: ..."
    )

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startDownload(context: Context, model: ModelCatalog.ModelInfo) {
        if (jobs[model.id]?.isActive == true) {
            EngineLogBus.warn("DM", "${model.id} already running")
            return
        }
        jobs.remove(model.id)

        val app = context.applicationContext
        app.startForegroundService(Intent(app, ModelDownloadService::class.java).apply {
            putExtra("model_id", model.id)
            putExtra("model_name", model.displayName)
        })

        val downloader = ModelDownloader(app)
        val modelsDir = File(app.filesDir, "models").apply { mkdirs() }
        val dst = File(modelsDir, model.filename)
        val ch = Channel<ModelDownloader.Progress>(Channel.BUFFERED)

        val url = ModelCatalog.getDownloadUrl(model)
        if (url.isBlank()) {
            EngineLogBus.warn("DM", "${model.id} is bundled - no download needed")
            return
        }
        EngineLogBus.info("DM", "Downloading $url")

        val job = scope.launch {
            _downloadStates.update { it + (model.id to DownloadState(true, 0L, -1L, 0f, 0L, 0L, "Starting...")) }
            try {
                coroutineScope {
                    val task = async(Dispatchers.IO) {
                        downloader.download(url = url, dst = dst, progress = ch, scope = this)
                    }
                    launch {
                        try {
                            ch.consumeEach { p ->
                                _downloadStates.update {
                                    it + (model.id to DownloadState(true, p.cur, p.tot, p.pct, p.spd, p.eta, ""))
                                }
                            }
                        } catch (_: Exception) { /* channel closed */ }
                    }
                    val r = task.await()
                    _downloadStates.update {
                        if (r.ok) {
                            it + (model.id to DownloadState(false, dst.length(), dst.length(), 100f, 0L, 0L, "Complete"))
                        } else {
                            it + (model.id to DownloadState(false, 0L, 0L, 0f, 0L, 0L, "Error: ${r.err}"))
                        }
                    }
                }
            } catch (_: CancellationException) {
                _downloadStates.update { it + (model.id to DownloadState(false, 0L, 0L, 0f, 0L, 0L, "Cancelled")) }
            } catch (e: Exception) {
                EngineLogBus.error("DM", e.message ?: "unknown error")
                _downloadStates.update { it + (model.id to DownloadState(false, 0L, 0L, 0f, 0L, 0L, "Error: ${e.message}")) }
            } finally {
                jobs.remove(model.id)
            }
        }
        jobs[model.id] = job
    }

    fun pauseDownload(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
    }

    fun cancelDownload(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _downloadStates.update { it - id }
    }
}