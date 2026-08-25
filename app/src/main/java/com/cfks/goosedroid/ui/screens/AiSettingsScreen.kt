package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cfks.goosedroid.ai.AiMode
import com.cfks.goosedroid.ai.AiSettings
import com.cfks.goosedroid.ai.AiSettingsRepository

val TeslaBlack = Color(0xFF000000)
val TeslaDarkGrey = Color(0xFF1A1A1A)
val TeslaGrey = Color(0xFF333333)
val TeslaLightGrey = Color(0xFFAAAAAA)
val TeslaWhite = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { AiSettingsRepository(context) }
    
    var settings by remember { mutableStateOf(repository.getSettings()) }

    Scaffold(
        containerColor = TeslaBlack,
        topBar = {
            TopAppBar(
                title = { Text("AI_BRAIN_CONFIG", fontFamily = FontFamily.Monospace, color = TeslaWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TeslaBlack,
                    titleContentColor = TeslaWhite
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .background(TeslaBlack),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "// CHOOSE EXECUTION ENVIRONMENT",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TeslaLightGrey
            )

            // Mode Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = settings.mode == AiMode.CLOUD_API,
                    onClick = { settings = settings.copy(mode = AiMode.CLOUD_API) },
                    label = { Text("CLOUD_API", fontFamily = FontFamily.Monospace) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = TeslaDarkGrey,
                        labelColor = TeslaLightGrey,
                        selectedContainerColor = TeslaWhite,
                        selectedLabelColor = TeslaBlack
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = TeslaGrey,
                        selectedBorderColor = TeslaWhite,
                        enabled = true,
                        selected = settings.mode == AiMode.CLOUD_API
                    )
                )
                FilterChip(
                    selected = settings.mode == AiMode.LOCAL_LLAMA,
                    onClick = { settings = settings.copy(mode = AiMode.LOCAL_LLAMA) },
                    label = { Text("LOCAL_ONBOARD", fontFamily = FontFamily.Monospace) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = TeslaDarkGrey,
                        labelColor = TeslaLightGrey,
                        selectedContainerColor = TeslaWhite,
                        selectedLabelColor = TeslaBlack
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = TeslaGrey,
                        selectedBorderColor = TeslaWhite,
                        enabled = true,
                        selected = settings.mode == AiMode.LOCAL_LLAMA
                    )
                )
            }

            if (settings.mode == AiMode.CLOUD_API) {
                CloudSettingsPanel(settings) { updated -> settings = updated }
            } else {
                LocalSettingsPanel(settings, navController) { updated -> settings = updated }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "// SYSTEM CAPABILITIES",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TeslaLightGrey
            )
            
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TeslaWhite,
                    containerColor = TeslaDarkGrey
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, TeslaGrey)
            ) {
                Text(
                    if (com.cfks.goosedroid.services.GooseAccessibilityService.isServiceEnabled()) 
                        "SCREEN_READ_ACCESS: [GRANTED]" 
                    else "GRANT_SCREEN_READ_ACCESS", 
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    repository.saveSettings(settings)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TeslaWhite,
                    containerColor = TeslaDarkGrey
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, TeslaGrey)
            ) {
                Text("SAVE_AND_INITIALIZE", fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsPanel(settings: AiSettings, onUpdate: (AiSettings) -> Unit) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TeslaWhite,
        unfocusedBorderColor = TeslaGrey,
        focusedLabelColor = TeslaWhite,
        unfocusedLabelColor = TeslaLightGrey,
        cursorColor = TeslaWhite,
        focusedTextColor = TeslaWhite,
        unfocusedTextColor = TeslaLightGrey,
        focusedContainerColor = TeslaBlack,
        unfocusedContainerColor = TeslaBlack
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = settings.cloudBaseUrl,
            onValueChange = { onUpdate(settings.copy(cloudBaseUrl = it)) },
            label = { Text("API_BASE_URL", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = settings.cloudModelName,
            onValueChange = { onUpdate(settings.copy(cloudModelName = it)) },
            label = { Text("MODEL_NAME", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = settings.cloudApiKey,
            onValueChange = { onUpdate(settings.copy(cloudApiKey = it)) },
            label = { Text("API_KEY (SECURE)", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = "> SYSTEM: API KEY IS ENCRYPTED IN HARDWARE-BACKED KEYSTORE (AES256-GCM).",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TeslaLightGrey
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSettingsPanel(settings: AiSettings, navController: NavController, onUpdate: (AiSettings) -> Unit) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TeslaWhite,
        unfocusedBorderColor = TeslaGrey,
        focusedLabelColor = TeslaWhite,
        unfocusedLabelColor = TeslaLightGrey,
        cursorColor = TeslaWhite,
        focusedTextColor = TeslaWhite,
        unfocusedTextColor = TeslaLightGrey,
        focusedContainerColor = TeslaBlack,
        unfocusedContainerColor = TeslaBlack
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "> INITIALIZING LLAMA.CPP ENGINE...",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = TeslaLightGrey
        )
        OutlinedTextField(
            value = settings.localModelPath,
            onValueChange = { onUpdate(settings.copy(localModelPath = it)) },
            label = { Text("GGUF_MODEL_PATH", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
        )
        OutlinedButton(
            onClick = { navController.navigate("model_hub") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TeslaWhite,
                containerColor = TeslaDarkGrey
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, TeslaWhite)
        ) {
            Text("MODEL_HUB (One-Tap Downloads)", fontFamily = FontFamily.Monospace)
        }
        Text(
            text = "> NOTE: MODEL DOWNLOAD REQUIRED POST-INSTALL TO COMPLY WITH PLAY STORE 150MB LIMIT.",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TeslaGrey
        )
    }
}
