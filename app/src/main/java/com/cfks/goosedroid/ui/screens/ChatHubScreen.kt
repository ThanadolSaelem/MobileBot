package com.cfks.goosedroid.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.data.CharacterRepository
import com.cfks.goosedroid.data.ChatRepository
import com.cfks.goosedroid.model.PhysicsCharacter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat Hub (Phase 1.5, LINE/Telegram-style):
 * one screen listing every deployed bot — tap any to start chatting.
 * Complements per-bot direct chat (double-tap the sprite).
 * Strictly monochrome via MaterialTheme colorScheme.
 */
@Composable
fun ChatHubScreen(
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
) {
    val context = LocalContext.current
    var bots by remember { mutableStateOf<List<PhysicsCharacter>>(emptyList()) }
    var lastChatByBot by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val loaded = CharacterRepository.loadCharacters(context)
        bots = loaded
        val repo = ChatRepository(context)
        val map = mutableMapOf<String, Long>()
        loaded.forEach { character ->
            repo.getLatestConversation(character.spriteSheetData.name)?.let {
                map[it.characterName] = it.updatedAt
            }
        }
        lastChatByBot = map
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar — safe from status bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CHAT HUB // ALL BOTS",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        if (bots.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "> NO BOTS DEPLOYED",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "> deploy a unit from the Studio first",
                    color = MaterialTheme.colorScheme.outline,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bots, key = { it.id }) { bot ->
                    val name = bot.spriteSheetData.name
                    BotChatRow(
                        name = name,
                        persona = bot.spriteSheetData.persona,
                        lastChatText = lastChatByBot[name]?.let {
                            "LAST CHAT ${dateFormat.format(Date(it))}"
                        } ?: "NO CHAT YET",
                        spriteUri = bot.spriteSheetData.uri,
                        spriteColumns = bot.spriteSheetData.columns,
                        spriteRows = bot.spriteSheetData.rows,
                        onClick = { onOpenChat(name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BotChatRow(
    name: String,
    persona: String,
    lastChatText: String,
    spriteUri: String?,
    spriteColumns: Int,
    spriteRows: Int,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monochrome avatar with the bot's initial
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val avatar = rememberSpriteAvatar(spriteUri, spriteColumns, spriteRows)
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = "$name avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                if (persona.isNotBlank()) {
                    Text(
                        persona,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    lastChatText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Loads the FIRST FRAME of a bot's sprite sheet as its profile picture.
 * Supports content:// and file:// uris (the editor copies imported images
 * into filesDir as file:// uris). Returns null when there is no usable
 * image — callers fall back to the letter avatar.
 */
@Composable
private fun rememberSpriteAvatar(
    spriteUri: String?,
    columns: Int,
    rows: Int
): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    return remember(spriteUri, columns, rows) {
        if (spriteUri.isNullOrBlank()) return@remember null
        try {
            val bytes = when {
                spriteUri.startsWith("content:") ->
                    context.contentResolver.openInputStream(Uri.parse(spriteUri))?.use { it.readBytes() }

                spriteUri.startsWith("file:") ->
                    Uri.parse(spriteUri).path?.let { path -> java.io.File(path).takeIf { it.exists() }?.readBytes() }

                else ->
                    java.io.File(spriteUri).takeIf { it.exists() }?.readBytes()
            } ?: return@remember null

            val sheet = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@remember null
            val cols = columns.coerceAtLeast(1)
            val rowCount = rows.coerceAtLeast(1)
            val frameW = sheet.width / cols
            val frameH = sheet.height / rowCount
            if (frameW <= 0 || frameH <= 0) return@remember null

            Bitmap.createBitmap(sheet, 0, 0, frameW, frameH).asImageBitmap()
        } catch (e: Exception) {
            android.util.Log.w("ChatHub", "Avatar load failed for $spriteUri: ${e.message}")
            null
        }
    }
}
