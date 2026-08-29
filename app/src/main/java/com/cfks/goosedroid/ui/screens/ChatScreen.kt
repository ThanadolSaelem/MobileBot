package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cfks.goosedroid.ai.ChatEngine
import com.cfks.goosedroid.ui.viewmodel.ChatViewModel

data class ChatMessage(
    val sender: String,
    val text: String,
    val isMe: Boolean,
    val actionBadge: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@Composable
fun ChatScreen(
    characterName: String,
    onNavigateBack: () -> Unit,
    onOpenConversations: () -> Unit = {},
    conversationId: Long? = null
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        key = "chat_$characterName#${conversationId ?: 0}",
        factory = ChatViewModel.factory(context, characterName, conversationId)
    )
    val persistedMessages by viewModel.messages.collectAsState()
    val activeConversationId by viewModel.activeConversationId.collectAsState()
    val typingConversationIds by ChatEngine.typingConversationIds.collectAsState()
    // Typing state lives in the app-scoped ChatEngine, so a recreated screen
    // still shows the indicator while an in-flight request continues.
    val isTyping = activeConversationId != null &&
        typingConversationIds.contains(activeConversationId)
    var inputText by remember { mutableStateOf("") }

    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val messages = persistedMessages.map {
        ChatMessage(
            sender = it.sender,
            text = it.text,
            isMe = it.isFromUser,
            actionBadge = it.actionBadge,
            timestamp = it.timestamp
        )
    }

    // Auto-scroll to bottom when messages arrive, typing status changes, 
    // OR the content of the last message grows (streaming).
    val listState = rememberLazyListState()
    val lastMessageText = messages.lastOrNull()?.text ?: ""
    LaunchedEffect(messages.size, isTyping, lastMessageText) {
        if (messages.isNotEmpty() || isTyping) {
            listState.animateScrollToItem(
                if (isTyping) messages.size else (messages.size - 1).coerceAtLeast(0)
            )
        }
    }

    // Live engine status (THINKING / RETRYING / OFFLINE — FALLBACK MODE)
    val statusMap by ChatEngine.statusMap.collectAsState()
    val engineStatus = activeConversationId?.let { statusMap[it] }

    // Tell ChatEngine which conversation is on screen so reply notifications
    // only fire when the user is somewhere else.
    DisposableEffect(activeConversationId) {
        ChatEngine.setVisibleConversation(activeConversationId)
        onDispose { ChatEngine.setVisibleConversation(null) }
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // 1. Top App Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "COMM LINK // ${characterName.uppercase()}",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onOpenConversations,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = "Conversations",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.startNewChat() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear Messages", fontFamily = FontFamily.Monospace) },
                            onClick = {
                                showMenu = false
                                showClearConfirm = true
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    "Delete History", 
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ) 
                            },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
        
        // 1.1 Dialogs
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("CLEAR MESSAGES?", fontFamily = FontFamily.Monospace) },
                text = { Text("This will remove all messages in THIS chat room but keep the room entry.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearCurrentChat()
                        showClearConfirm = false
                    }) {
                        Text("CLEAR", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        if (showDeleteConfirm) {
            var confirmText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { 
                    showDeleteConfirm = false
                    confirmText = ""
                },
                containerColor = MaterialTheme.colorScheme.surface,
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { 
                    Text(
                        "DANGEROUS ZONE", 
                        fontFamily = FontFamily.Monospace, 
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                text = {
                    Column {
                        Text(
                            "This action CANNOT be undone.",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This will permanently delete this conversation and all its messages.")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "Type 'DELETE' to confirm:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.error,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = confirmText.uppercase() == "DELETE",
                        onClick = {
                            activeConversationId?.let { viewModel.deleteConversation(it) }
                            showDeleteConfirm = false
                            confirmText = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DELETE PERMANENTLY")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { 
                        showDeleteConfirm = false
                        confirmText = ""
                    }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // 2. Chat Message Stream
        LazyColumn(
            state = listState,
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
                        color = if (msg.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (msg.isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (msg.actionBadge != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                msg.actionBadge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            
                            val displayText = if (!msg.isMe && msg.text.isBlank() && isTyping && messages.lastOrNull() == msg) {
                                engineStatus ?: "... THINKING ..."
                            } else {
                                msg.text
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = { 
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("chat_message", displayText)
                                                clipboard.setPrimaryClip(clip)
                                            }
                                        )
                                    }
                            ) {
                                Text(
                                    displayText,
                                    color = if (msg.isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Only show the global indicator if the last message is NOT from assistant (i.e. thinking started but no bubble yet)
            val lastIsAssistant = messages.lastOrNull()?.isMe == false
            if (isTyping && !lastIsAssistant) {
                item {
                    Text(
                        engineStatus ?: "... THINKING ...",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Input Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                        Text("Transmitting command / message...", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.send(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
