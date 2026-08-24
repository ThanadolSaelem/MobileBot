package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.brain.LlmBackendType
import com.cfks.goosedroid.brain.PetBrain
import com.cfks.goosedroid.brain.PromptBuilder
import com.cfks.goosedroid.model.ChatMessage
import com.cfks.goosedroid.model.LlmActionJson
import com.cfks.goosedroid.ui.viewmodel.PetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PetBrainChatScreen(
    viewModel: PetViewModel
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val appearance by viewModel.appearance.collectAsState()
    val telemetry by PetBrain.telemetry.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showPromptInspector by remember { mutableStateOf(false) }
    var showBackendSwitcher by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // AI Telemetry HUD Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFFA855F7).copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(appearance.creatureType.emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${appearance.petName} SLM Brain",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = telemetry.activeBackendName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                            modifier = Modifier.clickable { showBackendSwitcher = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Switch Backend",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = telemetry.activeBackendType.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { showPromptInspector = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Inspect ChatML Prompt",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Latency",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Latency: ${telemetry.lastLatencyMs} ms (${telemetry.totalInferences} calls)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = "Format: ChatML JSON",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "เปิด YouTube 🚀",
                "Honk ดังๆ! 🔊",
                "แอบไปขโมยมีมมาหน่อย 🎒",
                "เขียนโน้ตลับแปะจอ 📝",
                "เล่าเรื่องตลกให้ฟังหน่อย 😂",
                "สวัสดีครับ วันนี้ทำอะไรดี ✨",
                "แตะหน้าจอให้หน่อย 🎯"
            )
            items(suggestions) { query ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        viewModel.sendUserChat(query)
                    }
                ) {
                    Text(
                        text = query,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ChatBubble(
                    msg = msg,
                    petEmoji = appearance.creatureType.emoji,
                    onTriggerAction = { action ->
                        if (action.action == "honk") {
                            viewModel.honkLoud()
                        } else if (action.action == "steal_meme") {
                            viewModel.stealRandomMeme()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Field Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text("สั่งงาน AI เช่น 'เปิด YouTube', 'Honk!', 'ขโมยมีม'...", fontSize = 12.sp)
                },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                maxLines = 3,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendUserChat(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("chat_send_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // System Prompt Inspector Dialog
    if (showPromptInspector) {
        val promptText = remember(appearance) {
            PromptBuilder.buildSystemPrompt(appearance)
        }
        AlertDialog(
            onDismissRequest = { showPromptInspector = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SLM ChatML System Prompt", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = promptText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromptInspector = false }) {
                    Text("ปิด")
                }
            }
        )
    }

    // Backend Switcher Dialog
    if (showBackendSwitcher) {
        AlertDialog(
            onDismissRequest = { showBackendSwitcher = false },
            title = {
                Text("เลือก LLM Inference Backend", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LlmBackendType.values().forEach { type ->
                        val isSelected = telemetry.activeBackendType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PetBrain.setPreferredBackend(type)
                                    showBackendSwitcher = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${type.badge} ${type.displayName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when (type) {
                                        LlmBackendType.ON_DEVICE_GGUF -> "รันโมเดล .gguf ในเครื่องด้วย JNI + llama.cpp"
                                        LlmBackendType.LOCAL_SERVER -> "เชื่อมต่อ llama-server ผ่าน HTTP (Dev Mode)"
                                        LlmBackendType.SMART_RULE_ENGINE -> "Agent อัจฉริยะในตัว (ศูนย์ Latency, ภาษาไทยสมบูรณ์)"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackendSwitcher = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage,
    petEmoji: String,
    onTriggerAction: (LlmActionJson) -> Unit = {}
) {
    val isUser = msg.isFromUser
    val timeStr = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Text(petEmoji, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp, end = 6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = msg.text,
                        fontSize = 13.sp,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Action JSON Inspection Badge
                    msg.action?.let { action ->
                        if (action.action != "chat") {
                            Spacer(modifier = Modifier.height(6.dp))
                            ActionJsonBadge(action, onClick = { onTriggerAction(action) })
                        }
                    }
                }
            }

            Text(
                text = timeStr,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun ActionJsonBadge(
    action: LlmActionJson,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = "Action JSON",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            val actionSummary = when (action.action) {
                "open_app" -> "action: open_app | pkg: ${action.pkg}"
                "tap" -> "action: tap | (${action.x}, ${action.y})"
                "input_text" -> "action: input | \"${action.text}\""
                "steal_meme" -> "action: steal_meme"
                "write_note" -> "action: write_note | \"${action.note}\""
                "honk" -> "action: honk"
                else -> "action: ${action.action}"
            }
            Text(
                text = actionSummary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
