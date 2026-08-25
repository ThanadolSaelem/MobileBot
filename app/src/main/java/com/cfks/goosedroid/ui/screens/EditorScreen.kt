package com.cfks.goosedroid.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cfks.goosedroid.model.AnimationSequence
import com.cfks.goosedroid.model.PhysicsCharacter
import com.cfks.goosedroid.model.SpriteSheetData
import com.cfks.goosedroid.ui.components.SpriteCropPreviewModal
import com.cfks.goosedroid.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ActionPreset(
    val name: String,
    val category: String,
    val defaultDescription: String,
    val defaultDialogue: String
)

val DEFAULT_ACTION_PRESETS = listOf(
    ActionPreset("IDLE", "MOVEMENT", "ยืนนิ่งรอบัญชาการ หรือพร้อมรับคำสั่งจากผู้ใช้", "พร้อมรับคำสั่งครับ!"),
    ActionPreset("WALK", "MOVEMENT", "เดินสำรวจหน้าจอ ลาดตระเวนรอบพื้นที่", "กำลังเดินลาดตระเวนครับ"),
    ActionPreset("WALK_UP", "MOVEMENT", "เดินขึ้นด้านบน", "เดินขึ้นบนครับ"),
    ActionPreset("WALK_DOWN", "MOVEMENT", "เดินลงด้านล่าง", "เดินลงล่างครับ"),
    ActionPreset("WALK_LEFT", "MOVEMENT", "เดินไปทางซ้าย", "เดินซ้ายครับ"),
    ActionPreset("WALK_RIGHT", "MOVEMENT", "เดินไปทางขวา", "เดินขวาครับ"),
    ActionPreset("WALK_UP_LEFT", "MOVEMENT", "เดินเฉียงขึ้นไปทางซ้าย", "เดินเฉียงซ้ายบนครับ"),
    ActionPreset("WALK_UP_RIGHT", "MOVEMENT", "เดินเฉียงขึ้นไปทางขวา", "เดินเฉียงขวาบนครับ"),
    ActionPreset("WALK_DOWN_LEFT", "MOVEMENT", "เดินเฉียงลงไปทางซ้าย", "เดินเฉียงซ้ายล่างครับ"),
    ActionPreset("WALK_DOWN_RIGHT", "MOVEMENT", "เดินเฉียงลงไปทางขวา", "เดินเฉียงขวาล่างครับ"),
    ActionPreset("RUN", "MOVEMENT", "วิ่งอย่างรวดเร็ว ตอบสนองอย่างคล่องแคล่ว", "วิ่งด้วยความเร็วสูง!"),
    ActionPreset("RUN_UP", "MOVEMENT", "วิ่งขึ้นด้านบน", "วิ่งขึ้นบน!"),
    ActionPreset("RUN_DOWN", "MOVEMENT", "วิ่งลงด้านล่าง", "วิ่งลงล่าง!"),
    ActionPreset("RUN_LEFT", "MOVEMENT", "วิ่งไปทางซ้าย", "วิ่งซ้าย!"),
    ActionPreset("RUN_RIGHT", "MOVEMENT", "วิ่งไปทางขวา", "วิ่งขวา!"),
    ActionPreset("RUN_UP_LEFT", "MOVEMENT", "วิ่งเฉียงขึ้นไปทางซ้าย", "วิ่งเฉียงซ้ายบน!"),
    ActionPreset("RUN_UP_RIGHT", "MOVEMENT", "วิ่งเฉียงขึ้นไปทางขวา", "วิ่งเฉียงขวาบน!"),
    ActionPreset("RUN_DOWN_LEFT", "MOVEMENT", "วิ่งเฉียงลงไปทางซ้าย", "วิ่งเฉียงซ้ายล่าง!"),
    ActionPreset("RUN_DOWN_RIGHT", "MOVEMENT", "วิ่งเฉียงลงไปทางขวา", "วิ่งเฉียงขวาล่าง!"),
    ActionPreset("JUMP", "MOVEMENT", "กระโดดข้ามสิ่งกีดขวางหรือดีใจ", "ฮึบ! กระโดดแล้วนะ"),
    ActionPreset("ATTACK", "COMBAT & ACTION", "โจมตี ข่มขู่ หรือต่อสู้เมื่อมีคำสั่งโจมตี", "ย๊ากก! รับการโจมตีไปซะ!"),
    ActionPreset("DEFEND", "COMBAT & ACTION", "ตั้งการ์ดป้องกันตัว หรือหลบภัย", "เปิดโหมดป้องกันตัว!"),
    ActionPreset("HURT", "COMBAT & ACTION", "ได้รับความเสียหายหรือบาดเจ็บ ร้องขอความช่วยเหลือ", "โอ๊ย! โดนโจมตีซะแล้ว"),
    ActionPreset("HAPPY", "EMOTE & FUN", "แสดงความดีใจ ร่าเริง ยิ้มแย้ม", "เย้! วันนี้มีความสุขจัง"),
    ActionPreset("DANCE", "EMOTE & FUN", "เต้นระบำ โชว์สเต็ปตามจังหวะ", "กำลังเต้นโชว์สเต็ปอยู่นะ!"),
    ActionPreset("SLEEP", "EMOTE & FUN", "นอนหลับพักผ่อน ฟื้นฟูพลังงาน", "Zzz... ขอพักสายตาสักครู่..."),
    ActionPreset("ANGRY", "EMOTE & FUN", "โมโห โกรธ หรือไม่พอใจเมื่อโดนแกล้ง", "อย่ายุ่งนะ กำลังโมโหอยู่!")
)

@Composable
fun EditorScreen(
    onNavigateBack: () -> Unit,
    onSpawn: (PhysicsCharacter) -> Unit,
    existingNames: List<String> = emptyList(),
    editTarget: PhysicsCharacter? = null
) {
    val context = LocalContext.current

    // Auto-generate next available unit designation (e.g., UNIT-01, UNIT-02)
    val initialSuggestedName = remember(existingNames, editTarget) {
        if (editTarget != null) return@remember editTarget.spriteSheetData.name
        var num = 1
        var candidate = "UNIT-${String.format("%02d", num)}"
        while (existingNames.any { it.trim().equals(candidate, ignoreCase = true) } ||
            com.cfks.goosedroid.brain.CharacterRegistry.isNameTaken(candidate)
        ) {
            num++
            candidate = "UNIT-${String.format("%02d", num)}"
        }
        candidate
    }
    var unitName by remember { mutableStateOf(initialSuggestedName) }

    // Multi-Moveset State List
    val moveSets = remember(editTarget) {
        if (editTarget != null) {
            val list = mutableStateListOf<AnimationSequence>()
            list.addAll(editTarget.spriteSheetData.moveSets)
            list
        } else {
            mutableStateListOf(
                AnimationSequence(
                    name = "IDLE",
                    frames = listOf(0, 1, 2, 3),
                    speedMs = 180L,
                    columns = 4,
                    rows = 1,
                    description = "ยืนนิ่งรอบัญชาการ หรือพร้อมรับคำสั่งจากผู้ใช้",
                    dialogue = "พร้อมรับคำสั่งครับ!"
                ),
                AnimationSequence(
                    name = "WALK",
                    frames = listOf(0, 1, 2, 3),
                    speedMs = 120L,
                    columns = 4,
                    rows = 1,
                    description = "เดินสำรวจหน้าจอ ลาดตระเวนรอบพื้นที่",
                    dialogue = "กำลังเดินลาดตระเวนครับ"
                )
            )
        }
    }

    var selectedMovesetIndex by remember { mutableIntStateOf(0) }
    var isCropModalOpen by remember { mutableStateOf(false) }
    var isSimulationPlaying by remember { mutableStateOf(true) }
    var isPresetMenuExpanded by remember { mutableStateOf(false) }

    // Ensure selected index is always valid
    val safeIndex = selectedMovesetIndex.coerceIn(0, (moveSets.size - 1).coerceAtLeast(0))
    val currentMoveset = moveSets.getOrNull(safeIndex)

    // Base moveset (first moveset that has an image)
    val baseMoveset = moveSets.firstOrNull { it.uri != null }
    val baseSpriteUri = baseMoveset?.uri

    // Effective URI for currently selected moveset
    val effectiveUri = currentMoveset?.uri

    // Memory Bitmap Cache for all loaded/cropped movesets
    val bitmapCache = remember { mutableStateMapOf<String, Bitmap>() }
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val activeImageBitmap by remember {
        derivedStateOf { activeBitmap?.asImageBitmap() }
    }
    var originalImportBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(effectiveUri) {
        if (effectiveUri != null) {
            if (bitmapCache.containsKey(effectiveUri)) {
                activeBitmap = bitmapCache[effectiveUri]
            } else {
                try {
                    val uri = Uri.parse(effectiveUri)
                    val inputStream = if (uri.scheme == "file") {
                        File(uri.path ?: "").inputStream()
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bmp != null) {
                        bitmapCache[effectiveUri] = bmp
                        activeBitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            activeBitmap = null
        }
    }

    // Live Animation Loop for the active moveset preview
    var previewFrameIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(safeIndex, currentMoveset?.frames, currentMoveset?.speedMs, isSimulationPlaying) {
        val frames = currentMoveset?.frames ?: listOf(0)
        val speed = currentMoveset?.speedMs ?: 120L
        if (frames.isNotEmpty() && isSimulationPlaying) {
            while (true) {
                delay(speed.coerceAtLeast(40L))
                previewFrameIndex = (previewFrameIndex + 1) % frames.size
            }
        }
    }

    // Unique Name Validation
    val isDuplicateName by remember(unitName, existingNames, editTarget) {
        derivedStateOf {
            val clean = unitName.trim().lowercase()
            clean.isNotEmpty() && (
                existingNames.any { it.trim().lowercase() == clean } ||
                com.cfks.goosedroid.brain.CharacterRegistry.isNameTaken(clean, editTarget?.id)
            )
        }
    }

    // Image Picker Launcher specifically for the active moveset
    val movesetImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && currentMoveset != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "moveset_${currentMoveset.name}_${System.currentTimeMillis()}.png")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val savedUri = Uri.fromFile(file).toString()
                val bmp = BitmapFactory.decodeFile(file.absolutePath)

                if (bmp != null) {
                    bitmapCache[savedUri] = bmp
                    originalImportBitmap = bmp
                    activeBitmap = bmp

                    // Update ONLY current moveset with its new sprite and frames
                    val cols = currentMoveset.columns.coerceAtLeast(1)
                    val rows = currentMoveset.rows.coerceAtLeast(1)
                    val totalFrames = cols * rows
                    moveSets[safeIndex] = currentMoveset.copy(
                        uri = savedUri,
                        columns = cols,
                        rows = rows,
                        frames = (0 until totalFrames).toList()
                    )

                    // Auto-open crop & precision zoom modal immediately
                    isCropModalOpen = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TdsmBackground)
    ) {
        // 1. Top Header Bar
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
                Column {
                    Text(
                        "SPRITE & MOVESET STUDIO",
                        color = TdsmTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "MULTI-ACTION SPRITE SYSTEM",
                        color = TdsmTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Scrollable Body Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. Character Designation (Unit Name)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = TdsmSurfaceElevated,
                border = BorderStroke(1.dp, if (isDuplicateName) Color(0xFFFF5555) else TdsmBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "CHARACTER DESIGNATION",
                        color = TdsmTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        placeholder = { Text("e.g. CYBER-CAT, UNIT-01", color = TdsmMuted, fontSize = 12.sp) },
                        isError = isDuplicateName,
                        enabled = editTarget == null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TdsmTextPrimary,
                            unfocusedTextColor = TdsmTextPrimary,
                            disabledTextColor = TdsmTextSecondary,
                            focusedBorderColor = if (isDuplicateName) Color(0xFFFF5555) else TdsmTextPrimary,
                            unfocusedBorderColor = if (isDuplicateName) Color(0xFFFF5555) else TdsmBorder,
                            disabledBorderColor = TdsmBorderLight,
                            errorBorderColor = Color(0xFFFF5555),
                            focusedContainerColor = TdsmSurface,
                            unfocusedContainerColor = TdsmSurface,
                            disabledContainerColor = TdsmBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    if (isDuplicateName) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "NAME ALREADY EXISTS",
                            color = Color(0xFFFF5555),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Moveset Action Selector (Horizontal Tabs with Add Button)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ACTION MOVESETS (${moveSets.size})",
                        color = TdsmTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "SELECT OR ADD ACTION",
                        color = TdsmMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(moveSets) { index, moveset ->
                        val isSelected = index == safeIndex
                        val hasImage = moveset.uri != null

                        Surface(
                            modifier = Modifier
                                .clickable { selectedMovesetIndex = index },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TdsmTextPrimary else TdsmSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) TdsmTextPrimary else TdsmBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    moveset.name.uppercase(),
                                    color = if (isSelected) Color.Black else TdsmTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasImage) Color(0xFF10B981) else if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Gray
                                        )
                                )
                            }
                        }
                    }

                    // Add Moveset Button
                    item {
                        OutlinedButton(
                            onClick = {
                                val newIndex = moveSets.size + 1
                                val availablePreset = DEFAULT_ACTION_PRESETS.firstOrNull { preset ->
                                    moveSets.none { it.name.equals(preset.name, ignoreCase = true) }
                                }
                                val chosenName = availablePreset?.name ?: "ACTION_$newIndex"
                                val chosenDesc = availablePreset?.defaultDescription ?: ""
                                val chosenDialogue = availablePreset?.defaultDialogue ?: ""

                                moveSets.add(
                                    AnimationSequence(
                                        name = chosenName,
                                        frames = listOf(0, 1, 2, 3),
                                        speedMs = 120L,
                                        columns = 4,
                                        rows = 1,
                                        description = chosenDesc,
                                        dialogue = chosenDialogue
                                    )
                                )
                                selectedMovesetIndex = moveSets.size - 1
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TdsmBorderLight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TdsmTextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Moveset", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD ACTION", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. Active Moveset Editor Panel
            if (currentMoveset != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = TdsmSurfaceElevated,
                    border = BorderStroke(1.dp, TdsmBorderLight)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header of the selected moveset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CONFIGURING: ${currentMoveset.name.uppercase()}",
                                color = TdsmTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            if (moveSets.size > 1) {
                                TextButton(
                                    onClick = {
                                        moveSets.removeAt(safeIndex)
                                        selectedMovesetIndex = (safeIndex - 1).coerceAtLeast(0)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5555),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "DELETE",
                                        color = Color(0xFFFF5555),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // A. Action Identity, Presets & AI Custom Context
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Action Name & Compact Space-Saving Preset Menu
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = currentMoveset.name,
                                    onValueChange = { newName ->
                                        moveSets[safeIndex] = currentMoveset.copy(name = newName.uppercase())
                                    },
                                    label = { Text("ACTION NAME", fontSize = 9.sp, color = TdsmTextSecondary, fontFamily = FontFamily.Monospace) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TdsmTextPrimary,
                                        unfocusedTextColor = TdsmTextPrimary,
                                        focusedBorderColor = TdsmTextPrimary,
                                        unfocusedBorderColor = TdsmBorder,
                                        focusedContainerColor = TdsmSurface,
                                        unfocusedContainerColor = TdsmSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                // Space-Saving Quick Preset Dropdown
                                Box {
                                    OutlinedButton(
                                        onClick = { isPresetMenuExpanded = true },
                                        modifier = Modifier.height(54.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, TdsmBorderLight),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = TdsmSurface,
                                            contentColor = TdsmTextPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text("PRESET", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Presets", modifier = Modifier.size(16.dp))
                                    }

                                    DropdownMenu(
                                        expanded = isPresetMenuExpanded,
                                        onDismissRequest = { isPresetMenuExpanded = false },
                                        modifier = Modifier
                                            .background(Color(0xFF161616))
                                            .border(1.dp, TdsmBorderLight, RoundedCornerShape(8.dp))
                                            .widthIn(max = 280.dp)
                                    ) {
                                        val groupedPresets = DEFAULT_ACTION_PRESETS.groupBy { it.category }
                                        groupedPresets.forEach { (category, presets) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "// $category",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color.Gray
                                                    )
                                                },
                                                onClick = { },
                                                enabled = false,
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                            )
                                            presets.forEach { preset ->
                                                val isSelected = currentMoveset.name.equals(preset.name, ignoreCase = true)
                                                DropdownMenuItem(
                                                    text = {
                                                        Column(
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                preset.name,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = if (isSelected) Color(0xFF10B981) else TdsmTextPrimary
                                                            )
                                                            Text(
                                                                preset.defaultDescription,
                                                                fontSize = 8.sp,
                                                                color = TdsmTextSecondary,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        isPresetMenuExpanded = false
                                                        moveSets[safeIndex] = currentMoveset.copy(
                                                            name = preset.name,
                                                            description = preset.defaultDescription,
                                                            dialogue = preset.defaultDialogue
                                                        )
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Row 2: Action Description / Prompt Context for LLM
                            OutlinedTextField(
                                value = currentMoveset.description,
                                onValueChange = { newDesc ->
                                    moveSets[safeIndex] = currentMoveset.copy(description = newDesc)
                                },
                                label = {
                                    Text(
                                        "ACTION DESCRIPTION (AI / BEHAVIOR CONTEXT)",
                                        fontSize = 9.sp,
                                        color = TdsmTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "เช่น ท่าทางเมื่อตัวละครดีใจ หรือเมื่อสั่งให้เต้น (ให้ AI เข้าใจ)",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TdsmTextPrimary,
                                    unfocusedTextColor = TdsmTextPrimary,
                                    focusedBorderColor = TdsmTextPrimary,
                                    unfocusedBorderColor = TdsmBorder,
                                    focusedContainerColor = TdsmSurface,
                                    unfocusedContainerColor = TdsmSurface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Row 3: Custom Dialogue / Balloon Text (Optional)
                            OutlinedTextField(
                                value = currentMoveset.dialogue,
                                onValueChange = { newDialogue ->
                                    moveSets[safeIndex] = currentMoveset.copy(dialogue = newDialogue)
                                },
                                label = {
                                    Text(
                                        "CUSTOM DIALOGUE / BALLOON TEXT (OPTIONAL)",
                                        fontSize = 9.sp,
                                        color = TdsmTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "คำพูดที่ตัวละครจะพูดเมื่อแสดงท่านี้ เช่น 'พร้อมลุย!', 'อย่ายุ่งนะ'",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = TdsmTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TdsmTextPrimary,
                                    unfocusedTextColor = TdsmTextPrimary,
                                    focusedBorderColor = TdsmTextPrimary,
                                    unfocusedBorderColor = TdsmBorder,
                                    focusedContainerColor = TdsmSurface,
                                    unfocusedContainerColor = TdsmSurface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // AI Context hint text
                            Text(
                                "// AI CONTEXT: ระบบจะส่ง Description และ Dialogue ให้ LLM เข้าใจท่าทางและตอบสนองได้ตรงจุด",
                                fontSize = 8.sp,
                                color = Color(0xFF888888),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // B. Live Deploy Simulation & Character Model Display (Primary Centerpiece)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0C0C0C),
                            border = BorderStroke(1.dp, TdsmBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Title bar of Live Deploy Sim Box
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (activeImageBitmap != null) Color(0xFF10B981) else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "LIVE DEPLOY SIM",
                                            color = TdsmTextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    // Action Status Tag
                                    Surface(
                                        color = Color(0xDD1A1A1A),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, TdsmBorderLight)
                                    ) {
                                        Text(
                                            if (currentMoveset.uri != null) "ACTION: ${currentMoveset.name}" else if (baseSpriteUri != null) "BASE SPRITE: ${currentMoveset.name}" else "NO SPRITE",
                                            color = if (activeImageBitmap != null) Color(0xFF10B981) else TdsmMuted,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Simulation Canvas Viewport
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF050505))
                                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activeImageBitmap != null) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Animated Playback Canvas
                                            val effectiveMoveset = if (currentMoveset.uri != null) currentMoveset else (baseMoveset ?: currentMoveset)
                                            Canvas(
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .align(Alignment.Center)
                                            ) {
                                                val cols = effectiveMoveset.columns.coerceAtLeast(1)
                                                val rows = effectiveMoveset.rows.coerceAtLeast(1)
                                                val frameW = (activeImageBitmap!!.width / cols).coerceAtLeast(1)
                                                val frameH = (activeImageBitmap!!.height / rows).coerceAtLeast(1)

                                                val frames = effectiveMoveset.frames
                                                val currentFrameVal = if (frames.isNotEmpty()) {
                                                    frames[previewFrameIndex % frames.size]
                                                } else {
                                                    0
                                                }

                                                val col = currentFrameVal % cols
                                                val row = (currentFrameVal / cols).coerceIn(0, rows - 1)

                                                val srcX = (col * frameW).coerceIn(0, (activeImageBitmap!!.width - 1).coerceAtLeast(0))
                                                val srcY = (row * frameH).coerceIn(0, (activeImageBitmap!!.height - 1).coerceAtLeast(0))
                                                val drawW = frameW.coerceAtMost(activeImageBitmap!!.width - srcX).coerceAtLeast(1)
                                                val drawH = frameH.coerceAtMost(activeImageBitmap!!.height - srcY).coerceAtLeast(1)

                                                drawImage(
                                                    image = activeImageBitmap!!,
                                                    srcOffset = IntOffset(srcX, srcY),
                                                    srcSize = IntSize(drawW, drawH),
                                                    dstOffset = IntOffset.Zero,
                                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                                )
                                            }

                                            // Top Left Info Pill: Frame & Grid info
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(8.dp),
                                                color = Color(0xDD000000),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "FRAME ${previewFrameIndex + 1}/${currentMoveset.frames.size.coerceAtLeast(1)} (${currentMoveset.columns}x${currentMoveset.rows} Grid)",
                                                    color = TdsmTextSecondary,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }

                                            // Bottom Playback Controls
                                            // Bottom Controls: PLAY, STEP (Left) and CROP, IMPORT (Right)
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    modifier = Modifier
                                                        .height(28.dp)
                                                        .clickable {
                                                            isSimulationPlaying = !isSimulationPlaying
                                                        },
                                                    color = Color(0xDD111111),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, TdsmBorderLight)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            if (isSimulationPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                            contentDescription = if (isSimulationPlaying) "Pause" else "Play",
                                                            tint = TdsmTextPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            if (isSimulationPlaying) "PAUSE" else "PLAY",
                                                            color = TdsmTextPrimary,
                                                            fontSize = 8.5.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                if (!isSimulationPlaying && (currentMoveset.frames.isNotEmpty() || (baseMoveset?.frames?.isNotEmpty() == true))) {
                                                    val frameCount = if (currentMoveset.frames.isNotEmpty()) currentMoveset.frames.size else (baseMoveset?.frames?.size ?: 1)
                                                    Surface(
                                                        modifier = Modifier
                                                            .height(28.dp)
                                                            .clickable {
                                                                previewFrameIndex = (previewFrameIndex + 1) % frameCount.coerceAtLeast(1)
                                                            },
                                                        color = Color(0xDD111111),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(1.dp, TdsmBorderLight)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                "STEP >",
                                                                color = TdsmTextPrimary,
                                                                fontSize = 8.5.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Bottom Right Badges: CROP & IMPORT
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    modifier = Modifier
                                                        .height(28.dp)
                                                        .clickable { isCropModalOpen = true },
                                                    color = Color(0xDD111111),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, TdsmBorderLight)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Crop,
                                                            contentDescription = "Crop",
                                                            tint = TdsmTextPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("CROP", color = TdsmTextPrimary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                Surface(
                                                    modifier = Modifier
                                                        .height(28.dp)
                                                        .clickable { movesetImagePicker.launch("image/*") },
                                                    color = Color(0xDD111111),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, TdsmBorderLight)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Refresh,
                                                            contentDescription = "Change",
                                                            tint = TdsmTextPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("IMPORT", color = TdsmTextPrimary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                val copyCandidates = moveSets.filter { it.uri != null && it != currentMoveset }
                                                if (copyCandidates.isNotEmpty()) {
                                                    var isCopyMenuExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        Surface(
                                                            modifier = Modifier
                                                                .height(28.dp)
                                                                .clickable { isCopyMenuExpanded = true },
                                                            color = Color(0xDD111111),
                                                            shape = RoundedCornerShape(4.dp),
                                                            border = BorderStroke(1.dp, TdsmBorderLight)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.ContentCopy,
                                                                    contentDescription = "Copy",
                                                                    tint = TdsmTextPrimary,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("COPY", color = TdsmTextPrimary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        DropdownMenu(
                                                            expanded = isCopyMenuExpanded,
                                                            onDismissRequest = { isCopyMenuExpanded = false },
                                                            modifier = Modifier
                                                                .background(Color(0xFF161616))
                                                                .border(1.dp, TdsmBorderLight, RoundedCornerShape(8.dp))
                                                        ) {
                                                            copyCandidates.forEach { candidate ->
                                                                DropdownMenuItem(
                                                                    text = { Text(candidate.name.uppercase(), color = TdsmTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                                                    onClick = {
                                                                        isCopyMenuExpanded = false
                                                                        moveSets[safeIndex] = currentMoveset.copy(
                                                                            uri = candidate.uri,
                                                                            columns = candidate.columns,
                                                                            rows = candidate.rows,
                                                                            frames = candidate.frames
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Empty state when no sprite is loaded for current action
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Import",
                                                tint = TdsmTextSecondary,
                                                modifier = Modifier.size(38.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "NO SPRITE FOR ${currentMoveset.name.uppercase()}",
                                                color = TdsmTextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "IMPORT DEDICATED SPRITE OR REUSE EXISTING",
                                                color = TdsmMuted,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { movesetImagePicker.launch("image/*") },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = TdsmTextPrimary,
                                                        contentColor = Color.Black
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("IMPORT FOR ${currentMoveset.name.uppercase()}", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                }

                                                val copyCandidates = moveSets.filter { it.uri != null && it != currentMoveset }
                                                if (copyCandidates.isNotEmpty()) {
                                                    var isCopyMenuExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        OutlinedButton(
                                                            onClick = { isCopyMenuExpanded = true },
                                                            shape = RoundedCornerShape(6.dp),
                                                            border = BorderStroke(1.dp, TdsmBorderLight),
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TdsmTextPrimary),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                            modifier = Modifier.height(36.dp)
                                                        ) {
                                                            Text("COPY FROM...", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Moveset", modifier = Modifier.size(14.dp))
                                                        }
                                                        DropdownMenu(
                                                            expanded = isCopyMenuExpanded,
                                                            onDismissRequest = { isCopyMenuExpanded = false },
                                                            modifier = Modifier
                                                                .background(Color(0xFF161616))
                                                                .border(1.dp, TdsmBorderLight, RoundedCornerShape(8.dp))
                                                        ) {
                                                            copyCandidates.forEach { candidate ->
                                                                DropdownMenuItem(
                                                                    text = { Text(candidate.name.uppercase(), color = TdsmTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                                                    onClick = {
                                                                        isCopyMenuExpanded = false
                                                                        moveSets[safeIndex] = currentMoveset.copy(
                                                                            uri = candidate.uri,
                                                                            columns = candidate.columns,
                                                                            rows = candidate.rows,
                                                                            frames = candidate.frames
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Crop Modal for current moveset (Interactive Precision Crop + Pan + Zoom + Live Deploy Sim)
                        val cropSourceBitmap = originalImportBitmap ?: activeBitmap
                        if (isCropModalOpen && cropSourceBitmap != null) {
                            SpriteCropPreviewModal(
                                originalBitmap = cropSourceBitmap,
                                initialCols = currentMoveset.columns,
                                initialRows = currentMoveset.rows,
                                onDismiss = { isCropModalOpen = false },
                                onApplyCrop = { croppedBmp, newCols, newRows ->
                                    try {
                                        val file = File(context.filesDir, "crop_${currentMoveset.name}_${System.currentTimeMillis()}.png")
                                        val outputStream = FileOutputStream(file)
                                        croppedBmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                        outputStream.flush()
                                        outputStream.close()

                                        val savedUri = Uri.fromFile(file).toString()
                                        bitmapCache[savedUri] = croppedBmp
                                        activeBitmap = croppedBmp

                                        val totalFrames = newCols * newRows
                                        moveSets[safeIndex] = currentMoveset.copy(
                                            uri = savedUri,
                                            columns = newCols,
                                            rows = newRows,
                                            frames = (0 until totalFrames).toList()
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    isCropModalOpen = false
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // C. Grid Steppers: Frame Columns & Frame Rows
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Columns (Frames per Row)
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = TdsmSurface,
                                border = BorderStroke(1.dp, TdsmBorder)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("COLUMNS (FRAMES)", color = TdsmTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val newCols = (currentMoveset.columns - 1).coerceAtLeast(1)
                                                val total = newCols * currentMoveset.rows
                                                moveSets[safeIndex] = currentMoveset.copy(
                                                    columns = newCols,
                                                    frames = (0 until total).toList()
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            "${currentMoveset.columns}",
                                            color = TdsmTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        IconButton(
                                            onClick = {
                                                val newCols = (currentMoveset.columns + 1).coerceAtMost(32)
                                                val total = newCols * currentMoveset.rows
                                                moveSets[safeIndex] = currentMoveset.copy(
                                                    columns = newCols,
                                                    frames = (0 until total).toList()
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Rows (Grid Rows)
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = TdsmSurface,
                                border = BorderStroke(1.dp, TdsmBorder)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("ROWS (GRID)", color = TdsmTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val newRows = (currentMoveset.rows - 1).coerceAtLeast(1)
                                                val total = currentMoveset.columns * newRows
                                                moveSets[safeIndex] = currentMoveset.copy(
                                                    rows = newRows,
                                                    frames = (0 until total).toList()
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                        }

                                        Text(
                                            "${currentMoveset.rows}",
                                            color = TdsmTextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )

                                        IconButton(
                                            onClick = {
                                                val newRows = (currentMoveset.rows + 1).coerceAtMost(16)
                                                val total = currentMoveset.columns * newRows
                                                moveSets[safeIndex] = currentMoveset.copy(
                                                    rows = newRows,
                                                    frames = (0 until total).toList()
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // D. Animation Speed Control
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TdsmSurface,
                            border = BorderStroke(1.dp, TdsmBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("ANIMATION SPEED", color = TdsmTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    Text("${currentMoveset.speedMs}ms / FRAME", color = TdsmTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val newSpeed = (currentMoveset.speedMs - 30L).coerceAtLeast(40L)
                                            moveSets[safeIndex] = currentMoveset.copy(speedMs = newSpeed)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Faster", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val newSpeed = (currentMoveset.speedMs + 30L).coerceAtMost(600L)
                                            moveSets[safeIndex] = currentMoveset.copy(speedMs = newSpeed)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Slower", tint = TdsmTextPrimary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Bottom Sticky Deploy Action
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TdsmSurface,
            border = BorderStroke(1.dp, TdsmBorder)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                val isDeployable = !isDuplicateName && unitName.isNotBlank()

                Button(
                    onClick = {
                        if (!isDeployable) return@Button

                        // Primary moveset and URI resolution
                        val baseMoveset = moveSets.firstOrNull { it.uri != null } ?: moveSets.firstOrNull()
                        val firstUri = baseMoveset?.uri
                        val baseCols = baseMoveset?.columns ?: 4
                        val baseRows = baseMoveset?.rows ?: 1

                        val finalMoveSetsList = moveSets.map { moveset ->
                            val hasOwnUri = moveset.uri != null && moveset.uri != firstUri
                            val effectiveCols = if (hasOwnUri) moveset.columns else baseCols
                            val effectiveRows = if (hasOwnUri) moveset.rows else baseRows
                            val totalFrames = effectiveCols * effectiveRows
                            val effectiveFrames = if (moveset.frames.isNotEmpty() && moveset.frames.all { it < totalFrames }) {
                                moveset.frames
                            } else {
                                (0 until totalFrames).toList()
                            }
                            moveset.copy(
                                uri = moveset.uri ?: firstUri,
                                columns = effectiveCols,
                                rows = effectiveRows,
                                frames = effectiveFrames
                            )
                        }.toMutableList()

                        val finalId = editTarget?.id ?: UUID.randomUUID().toString()
                        val finalSpriteId = editTarget?.spriteSheetData?.id ?: UUID.randomUUID().toString()
                        
                        val newChar = PhysicsCharacter(
                            id = finalId,
                            spriteSheetData = SpriteSheetData(
                                id = finalSpriteId,
                                name = unitName.trim(),
                                uri = firstUri,
                                columns = baseCols,
                                rows = baseRows,
                                moveSets = finalMoveSetsList
                            ),
                            x = editTarget?.x ?: 120f,
                            y = editTarget?.y ?: 200f,
                            vx = editTarget?.vx ?: listOf(-1.5f, 1.5f).random(),
                            vy = editTarget?.vy ?: listOf(-1.0f, 1.0f).random()
                        )
                        onSpawn(newChar)
                    },
                    enabled = isDeployable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TdsmTextPrimary,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF222222),
                        disabledContentColor = Color(0xFF666666)
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isDeployable) Color.Black else Color(0xFF666666),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isDuplicateName) "NAME ALREADY EXISTS" else if (editTarget != null) "UPDATE ${unitName.trim().uppercase()} (${moveSets.size} ACTIONS)" else "DEPLOY ${unitName.trim().uppercase()} (${moveSets.size} ACTIONS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
