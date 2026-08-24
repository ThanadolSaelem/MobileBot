package com.cfks.goosedroid.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.brain.LlmBackendType
import com.cfks.goosedroid.brain.PetBrain
import com.cfks.goosedroid.ui.viewmodel.PetViewModel

@Composable
fun SettingsScreen(
    viewModel: PetViewModel
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(viewModel.isSoundMuted()) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    var wanderSpeed by remember { mutableFloatStateOf(1.0f) }
    var showResetDialog by remember { mutableStateOf(false) }

    val telemetry by PetBrain.telemetry.collectAsState()

    var modelPathInput by remember { mutableStateOf("/data/local/tmp/model.gguf") }
    var httpEndpointInput by remember { mutableStateOf("http://127.0.0.1:8080/completion") }
    var temperature by remember { mutableFloatStateOf(0.7f) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Settings & AI Brain ⚙️",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Configure on-device SLM inference, overlay mascot, and audio",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AI SLM Model Manager Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Brain",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "On-Device SLM & Inference Engine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Active Engine: ${telemetry.activeBackendName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = telemetry.statusMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Engine Selection Chips
                Text("Select Inference Engine:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LlmBackendType.values().forEach { type ->
                        val isSelected = telemetry.activeBackendType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { PetBrain.setPreferredBackend(type) }
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = type.badge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when (type) {
                                        LlmBackendType.ON_DEVICE_GGUF -> "Native JNI"
                                        LlmBackendType.LOCAL_SERVER -> "HTTP Dev"
                                        LlmBackendType.SMART_RULE_ENGINE -> "Smart Agent"
                                    },
                                    fontSize = 9.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (telemetry.activeBackendType == LlmBackendType.ON_DEVICE_GGUF) {
                    OutlinedTextField(
                        value = modelPathInput,
                        onValueChange = {
                            modelPathInput = it
                            PetBrain.setModelPath(it)
                        },
                        label = { Text("GGUF Model File Path (.gguf)", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (telemetry.activeBackendType == LlmBackendType.LOCAL_SERVER) {
                    OutlinedTextField(
                        value = httpEndpointInput,
                        onValueChange = {
                            httpEndpointInput = it
                            PetBrain.setHttpEndpoint(it)
                        },
                        label = { Text("llama-server HTTP URL", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Temperature Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Creativity (Temp): ${(temperature * 100).toInt() / 100f}", fontSize = 12.sp)
                }
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.1f..1.5f
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sound Settings Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Audio & Haptics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Sound FX",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Sound Effects (Honk & Bite)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Play audio from assets library", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = !isMuted,
                        onCheckedChange = {
                            isMuted = !it
                            viewModel.setSoundMuted(!it)
                            if (it) viewModel.poke()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Vibration",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Haptic Feedback", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Vibrate on honk, tap, and bite", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Overlay Permission Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Desktop Overlay Permission",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable 'Display over other apps' to let MobileBot roam freely across your Android home screen and inside any app.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Overlay Permission", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // About & Reset Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Information",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("MobileBot Desktop Pet & AI Assistant v1.0.0", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Powered by Jetpack Compose & Thai SLM Action Engine (llama.cpp)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Zero Cloud Tracking • 100% Private On-Device Pet", fontSize = 11.sp, color = Color(0xFF10B981))

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Pet Stats & Data", fontSize = 12.sp)
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Pet?") },
                text = { Text("Are you sure you want to reset your pet stats back to level 1?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetPet()
                            showResetDialog = false
                        }
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
