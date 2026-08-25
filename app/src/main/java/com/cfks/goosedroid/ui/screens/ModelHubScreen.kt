package com.cfks.goosedroid.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
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
import androidx.navigation.NavController
import java.io.File

data class GgufModel(
    val name: String,
    val description: String,
    val sizeStr: String,
    val downloadUrl: String,
    val filename: String
)

val availableModels = listOf(
    GgufModel(
        name = "Qwen 1.5 (0.5B)",
        description = "Very small & extremely fast. Good for basic tasks.",
        sizeStr = "398 MB",
        downloadUrl = "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf?download=true",
        filename = "qwen1_5-0_5b-chat-q4_k_m.gguf"
    ),
    GgufModel(
        name = "TinyLlama (1.1B)",
        description = "Lightweight model optimized for low memory usage.",
        sizeStr = "637 MB",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf?download=true",
        filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    ),
    GgufModel(
        name = "Llama-3 (8B Instruct)",
        description = "Powerful but requires >= 6GB RAM. High accuracy.",
        sizeStr = "4.7 GB",
        downloadUrl = "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf?download=true",
        filename = "Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelHubScreen(navController: NavController) {
    val context = LocalContext.current
    
    // Check downloaded models
    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    var refreshTrigger by remember { mutableStateOf(0) }
    
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TeslaBlack
                )
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
                    "Download GGUF models directly to your device for offline inference. Note: Running these models natively requires the C++ inference engine (llama.cpp) to be configured in your build.",
                    color = TeslaLightGrey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            
            items(availableModels) { model ->
                val file = File(downloadsDir, model.filename)
                val isDownloaded = file.exists() && file.length() > 0
                
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
                            Text(model.name, color = TeslaWhite, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(4.dp))
                            Text(model.description, color = TeslaLightGrey, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(4.dp))
                            Text("Size: ${model.sizeStr}", color = Color(0xFF4CAF50), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        
                        Spacer(Modifier.width(16.dp))
                        
                        if (isDownloaded) {
                            Text(
                                "DOWNLOADED",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    downloadModel(context, model)
                                    Toast.makeText(context, "Download started for ${model.name}. Check notifications.", Toast.LENGTH_LONG).show()
                                },
                                modifier = Modifier.background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = TeslaWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun downloadModel(context: Context, model: GgufModel) {
    val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
        .setTitle("Downloading ${model.name}")
        .setDescription("Fetching GGUF model...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, model.filename)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}
