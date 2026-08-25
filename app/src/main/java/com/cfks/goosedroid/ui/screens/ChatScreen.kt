package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.cfks.goosedroid.brain.PetBrain
import com.cfks.goosedroid.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: String,
    val text: String,
    val isMe: Boolean,
    val actionBadge: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ChatScreen(
    characterName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    sender = characterName,
                    text = "สวัสดีครับผู้บัญชาการ $characterName ออนไลน์และพร้อมรับคำสั่งภาษาไทยแล้วครับ",
                    isMe = false,
                    actionBadge = "SYSTEM // READY"
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TdsmBackground)
    ) {
        // 1. Top App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TdsmSurface,
            border = BorderStroke(1.dp, TdsmBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TdsmTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TdsmTextPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "COMM LINK // ${characterName.uppercase()}",
                    color = TdsmTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // 2. Chat Message Stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (msg.isMe) Color(0xFFFFFFFF) else TdsmSurfaceElevated,
                        border = BorderStroke(1.dp, if (msg.isMe) Color(0xFFFFFFFF) else TdsmBorder),
                        shape = if (msg.isMe) {
                            RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
                        } else {
                            RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
                        },
                        shadowElevation = 2.dp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!msg.isMe) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        msg.sender.uppercase(),
                                        color = TdsmTextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (msg.actionBadge != null) {
                                        Surface(
                                            color = Color(0xFF262626),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, TdsmBorderLight)
                                        ) {
                                            Text(
                                                msg.actionBadge,
                                                color = TdsmTextPrimary,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                msg.text,
                                color = if (msg.isMe) Color.Black else TdsmTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (isTyping) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(
                            color = TdsmSurfaceElevated,
                            border = BorderStroke(1.dp, TdsmBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "...",
                                color = TdsmTextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Input Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TdsmSurface,
            border = BorderStroke(1.dp, TdsmBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text("Transmitting command / message...", color = TdsmMuted, fontSize = 12.sp)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TdsmTextPrimary,
                        unfocusedTextColor = TdsmTextPrimary,
                        focusedBorderColor = TdsmTextPrimary,
                        unfocusedBorderColor = TdsmBorder,
                        focusedContainerColor = TdsmSurfaceElevated,
                        unfocusedContainerColor = TdsmSurfaceElevated
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val userMsg = inputText.trim()
                            messages = messages + ChatMessage(sender = "Commander", text = userMsg, isMe = true)
                            inputText = ""
                            isTyping = true

                            scope.launch {
                                delay(350)
                                val result = PetBrain.processCommand(context, userMsg, characterName)
                                isTyping = false
                                messages = messages + ChatMessage(
                                    sender = characterName,
                                    text = result.displayReply,
                                    actionBadge = result.actionBadge,
                                    isMe = false
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TdsmTextPrimary)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
