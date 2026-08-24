package com.cfks.goosedroid.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cfks.goosedroid.model.MemeItem
import com.cfks.goosedroid.model.NotepadNote
import com.cfks.goosedroid.ui.viewmodel.PetViewModel

@Composable
fun StolenLootScreen(
    viewModel: PetViewModel
) {
    val memes by viewModel.memes.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val activeNote by viewModel.activeLootNote.collectAsState()
    val activeMeme by viewModel.activeLootMeme.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var viewingNote by remember { mutableStateOf<NotepadNote?>(null) }
    var viewingMeme by remember { mutableStateOf<MemeItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Goose Loot Backpack 🎒",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Memes & Sticky Notes stolen by your pet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (selectedTab == 0) viewModel.stealRandomMeme() else viewModel.writeRandomNote()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("btn_steal_loot")
            ) {
                Text("🥷 Go Steal!", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("🖼️ Stolen Memes (${memes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("📝 Sticky Notes (${notes.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content
        if (selectedTab == 0) {
            // Memes Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(memes, key = { it.id }) { meme ->
                    val isCurrentlyActive = activeMeme?.id == meme.id
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isCurrentlyActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectLootMeme(meme)
                                viewingMeme = meme
                            }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = "file:///android_asset/Images/Memes/${meme.fileName}",
                                contentDescription = meme.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = meme.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = meme.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            // Sticky Notes List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(notes, key = { it.id }) { note ->
                    val isCurrentlyActive = activeNote?.id == note.id
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
                        border = if (isCurrentlyActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectLootNote(note)
                                viewingNote = note
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📝", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = note.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF713F12)
                                    )
                                }
                                if (isCurrentlyActive) {
                                    Text(
                                        text = "Active on Sandbox",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCA8A04)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.content,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF854D0E),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Note Full View Dialog
        viewingNote?.let { note ->
            AlertDialog(
                onDismissRequest = { viewingNote = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(note.title, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note.content,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF713F12),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.selectLootNote(note)
                            viewingNote = null
                        }
                    ) {
                        Text("Pin to Playground")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewingNote = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Meme Full View Dialog
        viewingMeme?.let { meme ->
            AlertDialog(
                onDismissRequest = { viewingMeme = null },
                title = { Text(meme.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = "file:///android_asset/Images/Memes/${meme.fileName}",
                            contentDescription = meme.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = meme.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.selectLootMeme(meme)
                            viewingMeme = null
                        }
                    ) {
                        Text("Display on Playground")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewingMeme = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
