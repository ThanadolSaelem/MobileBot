package com.cfks.goosedroid.ui.screens

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cfks.goosedroid.download.ModelCatalog
import com.cfks.goosedroid.download.ModelDownloader
import com.cfks.goosedroid.download.ModelRepository
import com.cfks.goosedroid.download.DownloadProgress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import android.app.DownloadManager
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(navController: NavController) {
    val context = LocalContext.current
    val modelRepository = remember { ModelRepository(context) }
    val modelDownloader = remember { ModelDownloader(context) }
    val viewModel = viewModel<ModelHubViewModel>()

    val localModels by viewModel.localModels.collectAsState(emptyList())
    val downloadStates by viewModel.downloadStates.collectAsState(emptyMap())

    // Refresh local models on screen enter
    LaunchedEffect(Unit) {
        viewModel.refreshLocalModels(modelRepository)
    }

    Scaffold(
        containerColor = TeslaBlack,
        topBar = {
            TopAppBar(
                title = { Text("MODEL_HUB", fontFamily = FontFamily.Monospace, color = TeslaWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TeslaWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TeslaBlack),
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshLocalModels(modelRepository) },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Refresh", tint = TeslaWhite)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "ONE-TAP MODEL DOWNLOADS",
                    color = TeslaWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "Curated GGUF models for local inference. Tap to download with resume support.",
                    color = TeslaLightGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            // Local models section
            if (localModels.isNotEmpty()) {
                item {
                    Text(
                        "// LOCAL MODELS",
                        color = TeslaWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                items(localModels) { localModel ->
                    LocalModelCard(
                        localModel = localModel,
                        onDelete = { viewModel.refreshLocalModels(modelRepository) }
                    )
                }
            }

            // Catalog sections
            ModelCatalog.categories.forEach { category ->
                val models = ModelCatalog.getModelsByCategory(category)
                if (models.isNotEmpty()) {
                    item {
                        Text(
                            "// ${category.displayName}",
                            color = TeslaWhite,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    items(models) { catalogModel ->
                        CatalogModelCard(
                            catalogModel = catalogModel,
                            localModels = localModels,
                            modelDownloader = modelDownloader,
                            downloadStates = downloadStates,
                            onDownloadAction = { action, model ->
                                when (action) {
                                    "START" -> viewModel.startDownload(modelDownloader, model, context)
                                    "PAUSE" -> viewModel.pauseDownload(model.id)
                                    "RESUME" -> viewModel.resumeDownload(modelDownloader, model, context)
                                    "CANCEL" -> viewModel.cancelDownload(model.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalModelCard(
    localModel: ModelRepository.LocalModel,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TeslaDarkGrey),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, TeslaGrey, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(localModel.name, color = TeslaWhite, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = TeslaLightGrey, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Size: ${formatBytes(localModel.sizeBytes)} | ${localModel.catalogInfo?.quantization ?: "Unknown"}",
                        color = TeslaLightGrey,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (localModel.sha256 != null) {
                    Row {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "SHA256: ${localModel.sha256!!.substring(0, 16)}...",
                            color = Color(0xFF4CAF50),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.background(Color(0xFF3C0000), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
            }
        }
    }
}

@Composable
fun CatalogModelCard(
    catalogModel: ModelCatalog.ModelInfo,
    localModels: List<ModelRepository.LocalModel>,
    modelDownloader: ModelDownloader,
    downloadStates: Map<String, DownloadState>,
    onDownloadAction: (String, ModelCatalog.ModelInfo) -> Unit
) {
    val localMatch = localModels.find { it.file.name == catalogModel.filename }
    val isDownloaded = localMatch != null
    val downloadState = downloadStates[catalogModel.id]
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress ?: 0f
    val status = downloadState?.status ?: ""

    Card(
        colors = CardDefaults.cardColors(containerColor = TeslaDarkGrey),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (isDownloading) TeslaWhite else TeslaGrey, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(catalogModel.displayName, color = TeslaWhite, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Text(catalogModel.description, color = TeslaLightGrey, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("Size: ${formatBytes(catalogModel.sizeBytes)}", color = Color(0xFF4CAF50), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(16.dp))
                        Text("Quant: ${catalogModel.quantization}", color = TeslaLightGrey, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                if (isDownloaded) {
                    Text(
                        "READY",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isDownloading) {
                    // Progress indicator
                    Column(modifier = Modifier.width(100.dp)) {
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = TeslaWhite,
                            trackColor = TeslaGrey
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${progress.toInt()}% · ${formatSpeed(downloadState?.speed ?: 0)} · ETA ${formatEta(downloadState?.eta ?: 0)}",
                            color = TeslaWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Progress bar if downloading
            if (isDownloading) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status.isNotBlank()) {
                        Text(
                            status,
                            color = TeslaWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Action buttons
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDownloaded) {
                    // Already downloaded - no action needed
                    Text("Tap model in AI Settings > Local to use", color = TeslaGrey, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                } else if (isDownloading) {
                    Button(
                        onClick = { onDownloadAction("PAUSE", catalogModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = TeslaGrey, contentColor = TeslaWhite),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = TeslaWhite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PAUSE", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onDownloadAction("CANCEL", catalogModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C0000), contentColor = Color(0xFFFF5252)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Cancel", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                } else {
                    // Not downloaded - show download button
                    Button(
                        onClick = { onDownloadAction("START", catalogModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = TeslaWhite, contentColor = TeslaBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = TeslaBlack, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("DOWNLOAD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

data class DownloadState(
    val isDownloading: Boolean,
    val progress: Float,
    val speed: Long,
    val eta: Long,
    val status: String
)

// ViewModel for ModelHub
class ModelHubViewModel : androidx.lifecycle.ViewModel() {
    private val _localModels = kotlinx.coroutines.flow.MutableStateFlow<List<ModelRepository.LocalModel>>(emptyList())
    val localModels: kotlinx.coroutines.flow.StateFlow<List<ModelRepository.LocalModel>> = _localModels

    private val _downloadStates = kotlinx.coroutines.flow.MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: kotlinx.coroutines.flow.StateFlow<Map<String, DownloadState>> = _downloadStates

    private val activeDownloads = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun refreshLocalModels(repository: ModelRepository) {
        viewModelScope.launch {
            _localModels.value = repository.getLocalModels()
        }
    }

    fun startDownload(downloader: ModelDownloader, model: ModelCatalog.ModelInfo, appContext: Context) {
        if (activeDownloads.containsKey(model.id)) return

        val progressChannel = Channel<DownloadProgress>(kotlinx.coroutines.channels.Channel.BUFFERED)
        val job = viewModelScope.launch {
            _downloadStates.update { it + (model.id to DownloadState(true, 0f, 0, 0, "Starting...")) }

            val destinationDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(destinationDir, model.filename)

            val result = downloader.download(
                url = "https://huggingface.co/${model.repo}/resolve/main/${model.filename}?download=true",
                destinationFile = destinationFile,
                expectedSha256 = model.sha256,
                progressChannel = progressChannel,
                scope = this
            )

            progressChannel.consumeEach { prog ->
                _downloadStates.update { it + (model.id to DownloadState(
                    true,
                    prog.progressPercent,
                    prog.speedBytesPerSec,
                    prog.etaSeconds,
                    prog.status
                )) }
            }

            if (result.success) {
                _downloadStates.update { it + (model.id to DownloadState(false, 100f, 0, 0, "Complete")) }
                refreshLocalModels(ModelRepository(appContext))
            } else {
                _downloadStates.update { it + (model.id to DownloadState(false, 0f, 0, 0, "Error: ${result.errorMessage}")) }
            }
            activeDownloads.remove(model.id)
        }
        activeDownloads[model.id] = job
    }

    fun pauseDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        _downloadStates.update { it + (modelId to DownloadState(false, it[modelId]?.progress ?: 0f, 0, 0, "Paused")) }
        activeDownloads.remove(modelId)
    }

    fun resumeDownload(downloader: ModelDownloader, model: ModelCatalog.ModelInfo, appContext: Context) {
        startDownload(downloader, model, appContext)
    }

    fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        _downloadStates.update { it - modelId }
        activeDownloads.remove(modelId)
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        else -> String.format(Locale.US, "%.1f KB", bytes / 1000.0)
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
        bytesPerSec >= 1_000 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1000.0)
        else -> "$bytesPerSec B/s"
    }
}

fun formatEta(seconds: Long): String {
    return when {
        seconds < 0 -> "∞"
        seconds >= 3600 -> String.format(Locale.US, "%dh %dm", seconds / 3600, (seconds % 3600) / 60)
        seconds >= 60 -> String.format(Locale.US, "%dm %ds", seconds / 60, seconds % 60)
        else -> "${seconds}s"
    }
}