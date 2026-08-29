package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cfks.goosedroid.download.ModelCatalog
import com.cfks.goosedroid.download.ModelRepository
import com.cfks.goosedroid.download.ModelDownloadManager
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

// Colors imported from AiSettingsScreen.kt (same package)

// =============================================================================
// Screen
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(navController: NavController) {
    val context = LocalContext.current
    val modelRepository = remember { ModelRepository(context) }
    val viewModel = viewModel<ModelHubViewModel>()

    val localModels by viewModel.localModels.collectAsState(emptyList())
    val downloadStates by ModelDownloadManager.downloadStates.collectAsState(emptyMap())

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
                    "Curated GGUF models for local inference. Tap to download via hf-mirror.com.",
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
                        onDelete = {
                            viewModel.deleteModel(modelRepository, localModel.file) {
                                viewModel.refreshLocalModels(modelRepository)
                            }
                        }
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
                            modifier = Modifier.padding(top = if (category != ModelCatalog.Category.RECOMMENDED) 16.dp else 0.dp)
                        )
                    }
                    items(models) { catalogModel ->
                        CatalogModelCard(
                            catalogModel = catalogModel,
                            localModels = localModels,
                            downloadState = downloadStates[catalogModel.id],
                            onDownload = { viewModel.startDownload(context, catalogModel) },
                            onPause = { viewModel.pauseDownload(catalogModel.id) },
                            onCancel = { viewModel.cancelDownload(catalogModel.id) }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Local model card
// =============================================================================

@Composable
private fun LocalModelCard(
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

// =============================================================================
// Catalog model card — now driven by ModelDownloadManager.DownloadState
// =============================================================================

@Composable
private fun CatalogModelCard(
    catalogModel: ModelCatalog.ModelInfo,
    localModels: List<ModelRepository.LocalModel>,
    downloadState: ModelDownloadManager.DownloadState?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val isDownloaded = localModels.any { it.file.name == catalogModel.filename }
    val isDownloading = downloadState?.isDownloading == true
    val progress = downloadState?.progress ?: 0f
    val status = downloadState?.status ?: ""
    val hasError = status.startsWith("Error")

    Card(
        colors = CardDefaults.cardColors(containerColor = TeslaDarkGrey),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().border(
            1.dp,
            when {
                isDownloading -> TeslaWhite
                hasError -> Color(0xFFFF5252)
                else -> TeslaGrey
            },
            RoundedCornerShape(8.dp)
        )
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

                when {
                    isDownloaded -> {
                        Text("READY", color = Color(0xFF4CAF50), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    isDownloading -> {
                        Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = TeslaWhite,
                                trackColor = TeslaGrey
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("${progress.toInt()}%", color = TeslaWhite, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    hasError -> {
                        Text("FAILED", color = Color(0xFFFF5252), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status details & speed / ETA
            if (isDownloading || hasError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    status,
                    color = if (hasError) Color(0xFFFF5252) else TeslaWhite,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (isDownloading) {
                    Text(
                        "${formatSpeed(downloadState?.speed ?: 0)} · ETA ${formatEta(downloadState?.eta ?: 0)}",
                        color = TeslaLightGrey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Action row
            Spacer(Modifier.height(12.dp))
            when {
                isDownloaded -> {
                    Text(
                        "Tap model in AI Settings > Local to use",
                        color = TeslaGrey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                isDownloading -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(containerColor = TeslaGrey, contentColor = TeslaWhite),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = TeslaWhite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PAUSE", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C0000), contentColor = Color(0xFFFF5252)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Cancel", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasError) Color(0xFF502020) else TeslaWhite,
                            contentColor = if (hasError) Color(0xFFFF5252) else TeslaBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (hasError) Icons.Default.Info else Icons.Default.Download,
                            contentDescription = "Download",
                            tint = if (hasError) Color(0xFFFF5252) else TeslaBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasError) "RETRY DOWNLOAD" else "DOWNLOAD",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// ViewModel — thin delegation to global ModelDownloadManager
// =============================================================================

class ModelHubViewModel : ViewModel() {
    private val _localModels = kotlinx.coroutines.flow.MutableStateFlow<List<ModelRepository.LocalModel>>(emptyList())
    val localModels: kotlinx.coroutines.flow.StateFlow<List<ModelRepository.LocalModel>> = _localModels

    private var repository: ModelRepository? = null

    fun refreshLocalModels(repo: ModelRepository) {
        repository = repo
        viewModelScope.launch {
            _localModels.value = repo.getLocalModels()
        }
    }

    fun startDownload(context: android.content.Context, model: ModelCatalog.ModelInfo) {
        ModelDownloadManager.startDownload(context, model)
    }

    fun pauseDownload(modelId: String) {
        ModelDownloadManager.pauseDownload(modelId)
    }

    fun cancelDownload(modelId: String) {
        ModelDownloadManager.cancelDownload(modelId)
    }

    fun deleteModel(repo: ModelRepository, file: java.io.File, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteModel(file)
            onDeleted()
        }
    }

    init {
        viewModelScope.launch {
            ModelDownloadManager.downloadStates.collect { states ->
                if (states.values.any { it.status == "Complete" }) {
                    repository?.let { refreshLocalModels(it) }
                }
            }
        }
    }
}

// =============================================================================
// Formatting helpers
// =============================================================================

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.1f KB", bytes / 1000.0)
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1000.0)
    else -> "$bytesPerSec B/s"
}

private fun formatEta(seconds: Long): String = when {
    seconds < 0 -> "∞"
    seconds >= 3600 -> String.format(Locale.US, "%dh %dm", seconds / 3600, (seconds % 3600) / 60)
    seconds >= 60 -> String.format(Locale.US, "%dm %ds", seconds / 60, seconds % 60)
    else -> "${seconds}s"
}