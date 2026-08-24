package com.cfks.goosedroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.model.ChatMessage
import com.cfks.goosedroid.model.PetAppearance

@Composable
fun OverlayCompanionView(
    appearance: PetAppearance,
    isHonking: Boolean,
    isNapping: Boolean,
    currentSpeech: String?,
    chatMessages: List<ChatMessage>,
    onPet: () -> Unit,
    onPoke: () -> Unit,
    onHonk: () -> Unit,
    onFeed: () -> Unit,
    onToggleNap: () -> Unit,
    onSendChat: (String) -> Unit,
    onOpenMainActivity: () -> Unit,
    onDismissOverlay: () -> Unit
) {
    var showQuickMenu by remember { mutableStateOf(false) }
    var showMiniChat by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }

    val chatListState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(
        modifier = Modifier.padding(8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Floating Mini Chat Dialog on Overlay
            AnimatedVisibility(
                visible = showMiniChat,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A).copy(alpha = 0.94f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 340.dp)
                        .padding(bottom = 8.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF38BDF8).copy(alpha = 0.6f), Color(0xFFA855F7).copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(appearance.creatureType.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${appearance.petName} On-Device AI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { showMiniChat = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Mini Chat",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quicks = listOf(
                                "เปิด YouTube 🚀",
                                "Honk ดังๆ! 🔊",
                                "เล่าเรื่องตลก 😂",
                                "ขโมยมีม 🎒",
                                "สวัสดีครับ ✨"
                            )
                            items(quicks) { q ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.clickable {
                                        onSendChat(q)
                                    }
                                ) {
                                    Text(
                                        text = q,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chat messages stream
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(chatMessages.takeLast(10), key = { it.id }) { msg ->
                                val isUser = msg.isFromUser
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 12.dp
                                        ),
                                        color = if (isUser) Color(0xFF2563EB) else Color(0xFF334155),
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Input field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = {
                                    Text("พิมพ์สั่งงานหรือคุย...", fontSize = 11.sp, color = Color.Gray)
                                },
                                maxLines = 2,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF38BDF8),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedContainerColor = Color(0xFF1E293B),
                                    unfocusedContainerColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        onSendChat(chatInputText)
                                        chatInputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Frosted Glass Speech Bubble
            AnimatedVisibility(
                visible = !currentSpeech.isNullOrBlank() && !showMiniChat,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showMiniChat = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("💬", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentSpeech ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 3
                        )
                    }
                }
            }

            // Quick Radial / Pill Actions when Tapped
            AnimatedVisibility(
                visible = showQuickMenu,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .border(
                            width = 1.dp,
                            color = Color(0xFF64748B).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OverlayIconButton(
                            icon = Icons.Default.Psychology,
                            label = "AI Chat",
                            tint = Color(0xFF38BDF8)
                        ) {
                            showMiniChat = !showMiniChat
                            showQuickMenu = false
                        }
                        OverlayIconButton(
                            icon = Icons.Default.Restaurant,
                            label = "Feed",
                            tint = Color(0xFFF59E0B)
                        ) {
                            onFeed()
                        }
                        OverlayIconButton(
                            icon = Icons.Default.VolumeUp,
                            label = "Honk",
                            tint = Color(0xFFEF4444)
                        ) {
                            onHonk()
                        }
                        OverlayIconButton(
                            icon = if (isNapping) Icons.Default.LightMode else Icons.Default.DarkMode,
                            label = if (isNapping) "Wake" else "Nap",
                            tint = Color(0xFFA855F7)
                        ) {
                            onToggleNap()
                        }
                        OverlayIconButton(
                            icon = Icons.Default.Launch,
                            label = "App",
                            tint = Color(0xFF10B981)
                        ) {
                            onOpenMainActivity()
                            showQuickMenu = false
                        }
                        OverlayIconButton(
                            icon = Icons.Default.Close,
                            label = "Hide",
                            tint = Color(0xFF94A3B8)
                        ) {
                            onDismissOverlay()
                        }
                    }
                }
            }

            // Pet Mascot View
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showQuickMenu = !showQuickMenu
                                onPoke()
                            },
                            onDoubleTap = {
                                onPet()
                            },
                            onLongPress = {
                                showMiniChat = true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                GoosePetView(
                    appearance = appearance,
                    isHonking = isHonking,
                    isNapping = isNapping,
                    isExcited = showQuickMenu || showMiniChat,
                    onPet = onPet,
                    onPoke = onPoke
                )
            }
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
