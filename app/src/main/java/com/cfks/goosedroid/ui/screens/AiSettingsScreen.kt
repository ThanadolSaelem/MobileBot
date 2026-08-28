package com.cfks.goosedroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.util.Locale
import com.cfks.goosedroid.ai.AiMode
import com.cfks.goosedroid.ai.AiSettings
import com.cfks.goosedroid.ai.AiSettingsRepository
import com.cfks.goosedroid.ai.AiProvider
import com.cfks.goosedroid.ai.ProviderRegistry
import com.cfks.goosedroid.ai.ConnectionTester
import com.cfks.goosedroid.ai.EngineLogLevel
import com.cfks.goosedroid.ai.EngineLogBus
import com.cfks.goosedroid.ui.alert.AlertBus
import com.cfks.goosedroid.ui.alert.AlertType
import kotlinx.coroutines.launch

val TeslaBlack = Color(0xFF000000)
val TeslaDarkGrey = Color(0xFF1A1A1A)
val TeslaGrey = Color(0xFF333333)
val TeslaLightGrey = Color(0xFFAAAAAA)
val TeslaWhite = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(navController: NavController, viewModel: com.cfks.goosedroid.ui.viewmodel.MainViewModel? = null) {
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
                    titleContentColor = TeslaWhite,
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
            // Scrollable section — settings content can exceed one screen
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            Text(
                text = "// CHOOSE EXECUTION ENVIRONMENT",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TeslaLightGrey
            )

            // Mode Selection: Switch style
            Surface(
                color = TeslaDarkGrey,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, TeslaGrey),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (settings.mode == AiMode.CLOUD_API) "ENGINE: CLOUD (REMOTE)" else "ENGINE: LOCAL (OFFLINE)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TeslaWhite
                        )
                        Text(
                            text = if (settings.mode == AiMode.CLOUD_API) "> Using high-performance API" else "> Using on-device GGUF",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TeslaLightGrey
                        )
                    }
                    Switch(
                        checked = settings.mode == AiMode.CLOUD_API,
                        onCheckedChange = { isCloud ->
                            if (isCloud) {
                                if (settings.cloudApiKey.isBlank()) {
                                    AlertBus.show(AlertType.WARNING, "CLOUD NOT CONFIGURED", "Please enter your API Key below.")
                                }
                                settings = settings.copy(mode = AiMode.CLOUD_API)
                            } else {
                                settings = settings.copy(mode = AiMode.LOCAL_LLAMA)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TeslaBlack,
                            checkedTrackColor = TeslaWhite,
                            uncheckedThumbColor = TeslaWhite,
                            uncheckedTrackColor = TeslaGrey
                        )
                    )
                }
            }

            if (settings.mode == AiMode.CLOUD_API) {
                CloudSettingsPanel(settings) { updated -> settings = updated }
            } else {
                LocalSettingsPanel(settings, navController) { updated -> settings = updated }
            }
            
            ParametersPanel(settings) { updated -> settings = updated }
            
            EngineLogConsole()
            
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
                border = BorderStroke(1.dp, TeslaGrey)
            ) {
                Text(
                    if (com.cfks.goosedroid.services.GooseAccessibilityService.isServiceEnabled()) 
                        "SCREEN_READ_ACCESS: [GRANTED]" 
                    else "GRANT_SCREEN_READ_ACCESS", 
                    fontFamily = FontFamily.Monospace
                )
            }

            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    repository.saveSettings(settings)
                    viewModel?.refreshAiMode()
                    EngineLogBus.info("AiSettings", "SETTINGS SAVED (mode=${settings.mode})")
                    AlertBus.show(
                        AlertType.SUCCESS,
                        "SETTINGS SAVED",
                        "Engine mode: ${settings.mode}"
                    )
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TeslaWhite,
                    containerColor = TeslaDarkGrey
                ),
                border = BorderStroke(1.dp, TeslaGrey)
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
        Text("// PROVIDER PRESETS", fontFamily = FontFamily.Monospace, color = TeslaLightGrey, fontSize = 10.sp)
        
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderRegistry.providers.forEach { provider ->
                FilterChip(
                    selected = settings.cloudBaseUrl == provider.baseUrl,
                    onClick = {
                        onUpdate(settings.copy(
                            cloudBaseUrl = provider.baseUrl,
                            cloudModelName = provider.defaultModel
                        ))
                    },
                    label = { Text(provider.name, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
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
                        selected = settings.cloudBaseUrl == provider.baseUrl
                    )
                )
            }
        }

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
        var showApiKey by remember { mutableStateOf(value = false) }
        OutlinedTextField(
            value = settings.cloudApiKey,
            onValueChange = { onUpdate(settings.copy(cloudApiKey = it)) },
            label = { Text("API_KEY (SECURE)", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showApiKey) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showApiKey = !showApiKey }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                        tint = TeslaLightGrey
                    )
                }
            }
        )

        HorizontalDivider(color = TeslaGrey, thickness = 1.dp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.fallbackEnabled,
                onCheckedChange = { onUpdate(settings.copy(fallbackEnabled = it)) },
                colors = CheckboxDefaults.colors(checkedColor = TeslaWhite, uncheckedColor = TeslaGrey, checkmarkColor = TeslaBlack)
            )
            Text("ENABLE_SECONDARY_FALLBACK", fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
        }

        if (settings.fallbackEnabled) {
            OutlinedTextField(
                value = settings.secondaryBaseUrl,
                onValueChange = { onUpdate(settings.copy(secondaryBaseUrl = it)) },
                label = { Text("SECONDARY_BASE_URL", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = settings.secondaryModelName,
                onValueChange = { onUpdate(settings.copy(secondaryModelName = it)) },
                label = { Text("SECONDARY_MODEL", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
            )
            var showSecondaryKey by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = settings.secondaryApiKey,
                onValueChange = { onUpdate(settings.copy(secondaryApiKey = it)) },
                label = { Text("SECONDARY_API_KEY", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                visualTransformation = if (showSecondaryKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showSecondaryKey = !showSecondaryKey }) {
                        Icon(if (showSecondaryKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TeslaLightGrey)
                    }
                }
            )
        }

        val scope = rememberCoroutineScope()
        OutlinedButton(
            onClick = {
                scope.launch {
                    EngineLogBus.info("ConnectionTester", "Testing CLOUD connection...")
                    val result = ConnectionTester.testCloud(settings)
                    if (result.ok) {
                        EngineLogBus.info("CloudEngine", "CONNECTION TEST PASSED (${result.latencyMs}ms)")
                        AlertBus.show(
                            AlertType.SUCCESS,
                            "CLOUD CONNECTION OK",
                            "HTTP OK · ${result.latencyMs}ms",
                            autoDismissMs = 8000L
                        )
                    } else {
                        EngineLogBus.error("CloudEngine", "CONNECTION TEST FAILED: ${result.detail} (${result.latencyMs}ms)")
                        AlertBus.show(
                            AlertType.ERROR,
                            "CLOUD CONNECTION FAILED",
                            "${result.detail} · ${result.latencyMs}ms",
                            autoDismissMs = 8000L
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TeslaWhite,
                containerColor = TeslaDarkGrey
            ),
            border = BorderStroke(1.dp, TeslaGrey)
        ) {
            Text("TEST_CONNECTION", fontFamily = FontFamily.Monospace)
        }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { com.cfks.goosedroid.download.ModelRepository(context) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    EngineLogBus.info("AiSettings", "Importing model file...")
                    val importedFile = repository.importModelFromUri(it)
                    if (importedFile != null) {
                        EngineLogBus.info("AiSettings", "Imported: ${importedFile.name}")
                        onUpdate(settings.copy(localModelPath = importedFile.absolutePath))
                        AlertBus.show(AlertType.SUCCESS, "MODEL IMPORTED", importedFile.name)
                    } else {
                        EngineLogBus.error("AiSettings", "Failed to import model")
                        AlertBus.show(AlertType.ERROR, "IMPORT FAILED", "Check logs for details")
                    }
                }
            }
        }
    )

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = settings.localModelPath,
                onValueChange = { onUpdate(settings.copy(localModelPath = it)) },
                label = { Text("GGUF_MODEL_PATH", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.weight(1f),
                colors = textFieldColors,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
            )
            IconButton(
                onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .size(56.dp)
                    .padding(top = 8.dp)
                    .background(TeslaDarkGrey, RoundedCornerShape(4.dp))
                    .border(1.dp, TeslaGrey, RoundedCornerShape(4.dp))
            ) {
                Icon(Icons.Default.Folder, contentDescription = "Browse", tint = TeslaWhite)
            }
        }
        val scope = rememberCoroutineScope()
        OutlinedButton(
            onClick = {
                scope.launch {
                    EngineLogBus.info("ConnectionTester", "Testing LOCAL model readiness...")
                    val result = ConnectionTester.testLocal(settings)
                    if (result.ok) {
                        EngineLogBus.info("LocalEngine", "MODEL READY: ${result.detail}")
                        AlertBus.show(
                            AlertType.SUCCESS,
                            "LOCAL MODEL READY",
                            result.detail,
                            autoDismissMs = 8000L
                        )
                    } else {
                        EngineLogBus.warn("LocalEngine", "MODEL NOT READY: ${result.detail}")
                        AlertBus.show(
                            AlertType.ERROR,
                            "LOCAL MODEL NOT READY",
                            result.detail,
                            autoDismissMs = 8000L
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TeslaWhite,
                containerColor = TeslaDarkGrey
            ),
            border = BorderStroke(1.dp, TeslaGrey)
        ) {
            Text("TEST_MODEL_READINESS", fontFamily = FontFamily.Monospace)
        }
        Text(
            text = "> NOTE: SELECT A .GGUF MODEL FROM STORAGE OR USE THE BUNDLED ONE.",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TeslaGrey
        )
    }
}

@Composable
fun ParametersPanel(settings: AiSettings, onUpdate: (AiSettings) -> Unit) {
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
            text = "// GENERATION PARAMETERS",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = TeslaLightGrey
        )

        // Temperature Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TEMPERATURE", fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
                Text(String.format(Locale.US, "%.1f", settings.temperature), fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
            }
            Slider(
                value = settings.temperature,
                onValueChange = { onUpdate(settings.copy(temperature = it)) },
                valueRange = 0f..2f,
                steps = 20,
                colors = SliderDefaults.colors(
                    thumbColor = TeslaWhite,
                    activeTrackColor = TeslaWhite,
                    inactiveTrackColor = TeslaGrey
                )
            )
        }

        // Top-P Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOP_P", fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
                Text(String.format(Locale.US, "%.1f", settings.topP), fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
            }
            Slider(
                value = settings.topP,
                onValueChange = { onUpdate(settings.copy(topP = it)) },
                valueRange = 0f..1f,
                steps = 10,
                colors = SliderDefaults.colors(
                    thumbColor = TeslaWhite,
                    activeTrackColor = TeslaWhite,
                    inactiveTrackColor = TeslaGrey
                )
            )
        }

        // Top-K Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOP_K", fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
                Text("${settings.topK}", fontFamily = FontFamily.Monospace, color = TeslaWhite, fontSize = 12.sp)
            }
            Slider(
                value = settings.topK.toFloat(),
                onValueChange = { onUpdate(settings.copy(topK = it.toInt())) },
                valueRange = 1f..100f,
                steps = 99,
                colors = SliderDefaults.colors(
                    thumbColor = TeslaWhite,
                    activeTrackColor = TeslaWhite,
                    inactiveTrackColor = TeslaGrey
                )
            )
        }

        // Context Length
        OutlinedTextField(
            value = settings.contextLength.toString(),
            onValueChange = { newValue ->
                val intVal = newValue.filter { it.isDigit() }.toIntOrNull() ?: 0
                onUpdate(settings.copy(contextLength = intVal))
            },
            label = { Text("CONTEXT_LENGTH", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // Max Tokens
        OutlinedTextField(
            value = settings.maxTokens.toString(),
            onValueChange = { newValue ->
                val intVal = newValue.filter { it.isDigit() }.toIntOrNull() ?: 0
                onUpdate(settings.copy(maxTokens = intVal))
            },
            label = { Text("MAX_TOKENS", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // Global System Prompt
        OutlinedTextField(
            value = settings.globalSystemPrompt,
            onValueChange = { onUpdate(settings.copy(globalSystemPrompt = it)) },
            label = { Text("GLOBAL_SYSTEM_PROMPT (APP-WIDE)", fontFamily = FontFamily.Monospace) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            colors = textFieldColors,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            maxLines = 5
        )
    }
}

/**
 * Live monochrome console fed by [EngineLogBus].
 * Shows real AI engine events: init, requests, responses, tool calls, errors.
 * ERROR rows render inverted (black-on-white) — still strictly grayscale.
 */
@Composable
fun EngineLogConsole(modifier: Modifier = Modifier) {
    val entries by EngineLogBus.entries.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "> ENGINE LOG (LIVE)",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = TeslaWhite
            )
            TextButton(onClick = { EngineLogBus.clear() }) {
                Text(
                    "CLEAR",
                    fontFamily = FontFamily.Monospace,
                    color = TeslaLightGrey,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Surface(
            color = TeslaBlack,
            border = BorderStroke(1.dp, TeslaGrey),
            shape = RoundedCornerShape(4.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = "> waiting for engine events...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TeslaGrey
                        )
                    }
                }
                items(count = entries.size) { i ->
                    val e = entries[i]
                    val line = "[${e.level.name}] ${e.source}: ${e.message}"
                    if (e.level == EngineLogLevel.ERROR) {
                        Surface(
                            color = TeslaWhite,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TeslaBlack,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        val color = when (e.level) {
                            EngineLogLevel.DEBUG -> TeslaGrey
                            EngineLogLevel.INFO -> TeslaLightGrey
                            else -> TeslaWhite
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
    }
}
