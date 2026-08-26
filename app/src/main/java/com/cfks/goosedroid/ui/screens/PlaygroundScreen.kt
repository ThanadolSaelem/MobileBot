package com.cfks.goosedroid.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.cfks.goosedroid.brain.PetBrain
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cfks.goosedroid.model.PhysicsCharacter
import com.cfks.goosedroid.ui.theme.*
import com.cfks.goosedroid.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

data class LlmDirective(
    val action: String, // "WALK", "RUN", "JUMP", "IDLE", "CUSTOM"
    val movesetName: String? = null,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val durationFrames: Int = 180,
    val targetDx: Float? = null,
    val targetDy: Float? = null
)

enum class ChatDialogState {
    IDLE,
    TRANSITIONING,
    OPEN
}

@Composable
fun PlaygroundScreen(
    viewModel: MainViewModel,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToChatHub: () -> Unit = {},
    onLaunchOverlay: (PhysicsCharacter) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val physicsCharacters by viewModel.physicsCharacters.collectAsState()
    val hudMessage by viewModel.hudMessage.collectAsState()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsState()

    var boxWidth by remember { mutableStateOf(0) }
    var boxHeight by remember { mutableStateOf(0) }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Wallpaper state
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var bgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(customWallpaperUri) {
        if (customWallpaperUri != null) {
            try {
                val uri = Uri.parse(customWallpaperUri)
                val inputStream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                val bitmap = BitmapFactory.decodeStream(inputStream)
                bgBitmap = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
                bgBitmap = null
            }
        } else {
            bgBitmap = null
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomWallpaper(uri.toString())
        }
    }

    // Interactive 1-Turn NPC Chat Modal State
    var activeChatChar by remember { mutableStateOf<PhysicsCharacter?>(null) }
    var chatDialogState by remember { mutableStateOf(ChatDialogState.IDLE) }
    var chatInputText by remember { mutableStateOf("") }
    var assistantBubbleText by remember { mutableStateOf<String?>(null) }
    var userBubbleText by remember { mutableStateOf<String?>(null) }
    var isAssistantTyping by remember { mutableStateOf(false) }

    // Register active units with CharacterRegistry for LLM context & actions
    LaunchedEffect(physicsCharacters) {
        physicsCharacters.forEach { char ->
            com.cfks.goosedroid.brain.CharacterRegistry.registerUnit(
                id = char.id,
                uniqueName = char.spriteSheetData.name,
                spriteUri = char.spriteSheetData.uri,
                cols = char.spriteSheetData.columns,
                rows = char.spriteSheetData.rows,
                moveSets = char.spriteSheetData.moveSets
            )
        }
    }

    val density = LocalDensity.current
    val charSizePx = with(density) { 90.dp.toPx() }
    val trashThresholdPx = with(density) { 75.dp.toPx() }

    // LLM Directive storage: Commands issued by LLM / PetBrain have high priority over autonomous roll
    val llmDirectiveMap = remember { mutableStateMapOf<String, LlmDirective>() }
    val llmDirectiveTimerMap = remember { mutableStateMapOf<String, Int>() }

    // Behavior states for autonomous characters: "IDLE", "WALK", "RUN", "JUMP"
    val behaviorStateMap = remember { mutableStateMapOf<String, String>() }
    val behaviorTimerMap = remember { mutableStateMapOf<String, Int>() }

    // Physics Engine Loop: Drag Priority > LLM Directives > Autonomous Random Roll Fallback
    LaunchedEffect(chatDialogState, boxWidth, boxHeight) {
        while (chatDialogState == ChatDialogState.IDLE) {
            val allIdle = physicsCharacters.all { char -> 
                !char.isDragging && 
                behaviorStateMap[char.id] == "IDLE" && 
                char.vx == 0f && char.vy == 0f &&
                llmDirectiveMap[char.id] == null
            }
            
            val frameDelay = if (allIdle && physicsCharacters.isNotEmpty()) 160L else 16L
            delay(frameDelay)
            
            val timerDecrement = if (frameDelay == 160L) 10 else 1
            
            if (boxWidth > 0 && boxHeight > 0) {
                val groundY = (boxHeight - charSizePx - 100f).coerceAtLeast(80f)
                val ceilingY = 80f
                val gravity = 0.55f

                physicsCharacters.forEach { char ->
                    // 1. DRAG PRIORITY (ABSOLUTE HIGHEST): User is holding/dragging this character.
                    // Physics, LLM directives, and autonomous wander are suspended while dragged.
                    if (!char.isDragging) {
                        var vx = char.vx
                        var vy = char.vy
                        var x = char.x
                        var y = char.y

                        val isAirborne = y < (groundY - 1f)

                        // 2. CHECK ACTIVE LLM DIRECTIVE
                        val activeLlmDirective = llmDirectiveMap[char.id]
                        val llmTimer = (llmDirectiveTimerMap[char.id] ?: 0) - timerDecrement

                        if (activeLlmDirective != null && llmTimer > 0) {
                            // === EXECUTING ACTIVE LLM COMMAND ===
                            llmDirectiveTimerMap[char.id] = llmTimer

                            if (isAirborne) {
                                // Airborne physics under LLM directive (e.g. LLM commanded a JUMP)
                                vy += gravity
                                y += vy
                                if (y >= groundY) {
                                    y = groundY
                                    vy = 0f
                                    if (activeLlmDirective.action == "JUMP") {
                                        // Landed after LLM Jump -> Finish directive and enter brief idle
                                        llmDirectiveMap.remove(char.id)
                                        llmDirectiveTimerMap.remove(char.id)
                                        behaviorStateMap[char.id] = "IDLE"
                                        behaviorTimerMap[char.id] = (80..160).random()
                                        vx = 0f
                                    }
                                } else if (y < ceilingY) {
                                    y = ceilingY
                                    vy = abs(vy) * 0.4f
                                }
                            } else {
                                // Ground physics under LLM directive
                                y = groundY
                                when (activeLlmDirective.action) {
                                    "IDLE" -> {
                                        vx = 0f
                                        vy = 0f
                                    }
                                    "WALK" -> {
                                        val frames = activeLlmDirective.durationFrames.toFloat().coerceAtLeast(1f)
                                        vx = activeLlmDirective.targetDx?.let { it / frames } ?: (if (activeLlmDirective.vx != 0f) activeLlmDirective.vx else 1.8f)
                                        vy = 0f
                                    }
                                    "RUN" -> {
                                        val frames = activeLlmDirective.durationFrames.toFloat().coerceAtLeast(1f)
                                        vx = activeLlmDirective.targetDx?.let { it / frames } ?: (if (activeLlmDirective.vx != 0f) activeLlmDirective.vx else 3.5f)
                                        vy = 0f
                                    }
                                    "JUMP" -> {
                                        val frames = activeLlmDirective.durationFrames.toFloat().coerceAtLeast(1f)
                                        val tDx = activeLlmDirective.targetDx ?: (if (activeLlmDirective.vx != 0f) activeLlmDirective.vx * 60f else 90f)
                                        val tDy = activeLlmDirective.targetDy ?: -150f
                                        vy = (tDy - 0.5f * gravity * frames * frames) / frames
                                        vx = tDx / frames
                                        y += vy
                                    }
                                    else -> { // "CUSTOM" / specific moveset
                                        vx = 0f
                                        vy = 0f
                                    }
                                }
                            }

                            val currentAction = activeLlmDirective.movesetName ?: activeLlmDirective.action
                            behaviorStateMap[char.id] = currentAction

                            // Horizontal movement and wall collision
                            x += vx
                            if (x < 0f) {
                                x = 0f
                                vx = abs(vx)
                            } else if (x > boxWidth - charSizePx) {
                                x = (boxWidth - charSizePx).coerceAtLeast(0f)
                                vx = -abs(vx)
                            }

                            viewModel.updateCharacterPhysics(char.id, x, y, vx, vy, false, currentAction)
                        } else {
                            // Clean up expired LLM directive
                            if (activeLlmDirective != null) {
                                llmDirectiveMap.remove(char.id)
                                llmDirectiveTimerMap.remove(char.id)
                            }

                            // 3. FALLBACK: AUTONOMOUS RANDOM ROLL STATE MACHINE (WHEN NO LLM INSTRUCTION)
                            if (isAirborne) {
                                // Apply Gravity Physics
                                vy += gravity
                                y += vy
                                if (y >= groundY) {
                                    // Landed firmly on the ground floor!
                                    y = groundY
                                    vy = 0f
                                    // Transition to IDLE upon landing
                                    behaviorStateMap[char.id] = "IDLE"
                                    behaviorTimerMap[char.id] = (100..200).random()
                                    vx = 0f
                                } else if (y < ceilingY) {
                                    y = ceilingY
                                    vy = abs(vy) * 0.4f
                                }
                            } else {
                                // On Ground: Run fallback autonomous random roll
                                y = groundY
                                vy = 0f

                                val currentTimer = (behaviorTimerMap[char.id] ?: 0) - timerDecrement
                                var currentState = behaviorStateMap[char.id] ?: "IDLE"

                                if (currentTimer <= 0) {
                                    // Decide next fallback movement state based ONLY on movesets the character actually has
                                    val availableBehaviors = com.cfks.goosedroid.model.MovesetMatcher.getAvailableAutonomousBehaviors(char.spriteSheetData.moveSets)
                                    val chosenBehavior = availableBehaviors.random()

                                    when (chosenBehavior) {
                                        "IDLE" -> {
                                            currentState = "IDLE"
                                            vx = 0f
                                            behaviorTimerMap[char.id] = (120..240).random() // 2 to 4 seconds
                                        }
                                        "WALK" -> {
                                            currentState = "WALK"
                                            val dir = if (listOf(true, false).random()) 1f else -1f
                                            vx = dir * 1.5f
                                            behaviorTimerMap[char.id] = (140..260).random() // 2.3 to 4.3 seconds
                                        }
                                        "RUN" -> {
                                            currentState = "RUN"
                                            val dir = if (listOf(true, false).random()) 1f else -1f
                                            vx = dir * 3.2f
                                            behaviorTimerMap[char.id] = (80..150).random() // 1.3 to 2.5 seconds
                                        }
                                        "JUMP" -> {
                                            currentState = "JUMP"
                                            vy = -11.5f
                                            val dir = if (listOf(true, false).random()) 1f else -1f
                                            vx = dir * 1.4f
                                            behaviorTimerMap[char.id] = 60
                                        }
                                        else -> {
                                            // Custom named moveset (e.g. Dance, Sleep, Attack)
                                            currentState = chosenBehavior
                                            vx = 0f
                                            behaviorTimerMap[char.id] = (100..180).random()
                                        }
                                    }
                                    behaviorStateMap[char.id] = currentState
                                } else {
                                    behaviorTimerMap[char.id] = currentTimer
                                    if (currentState == "IDLE" || (!currentState.equals("WALK", true) && !currentState.equals("RUN", true) && !currentState.equals("JUMP", true))) {
                                        vx = 0f // Guarantee 100% stationary during IDLE / Custom static gestures
                                    }
                                }
                            }

                            // Horizontal movement and wall collision
                            x += vx
                            if (x < 0f) {
                                x = 0f
                                vx = abs(vx)
                            } else if (x > boxWidth - charSizePx) {
                                x = (boxWidth - charSizePx).coerceAtLeast(0f)
                                vx = -abs(vx)
                            }

                            viewModel.updateCharacterPhysics(char.id, x, y, vx, vy, false, behaviorStateMap[char.id])
                        }
                    }
                }
            }
        }
    }

    val isDraggingAny = physicsCharacters.any { it.isDragging }
    val draggedChar = physicsCharacters.firstOrNull { it.isDragging }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trashBinCenter by remember { mutableStateOf(Offset.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }

    LaunchedEffect(draggedChar?.x, draggedChar?.y, isDraggingAny, trashBinCenter, boxWidth, boxHeight) {
        if (isDraggingAny && draggedChar != null) {
            val charCenterX = draggedChar.x + (charSizePx / 2f)
            val charCenterY = draggedChar.y + (charSizePx / 2f)
            val effectiveTrashCenter = if (trashBinCenter != Offset.Zero) {
                trashBinCenter
            } else {
                Offset(boxWidth - with(density) { 52.dp.toPx() }, boxHeight - with(density) { 52.dp.toPx() })
            }
            val distToTrash = sqrt((charCenterX - effectiveTrashCenter.x).pow(2) + (charCenterY - effectiveTrashCenter.y).pow(2))
            isOverTrash = distToTrash < trashThresholdPx
        } else {
            isOverTrash = false
        }
    }

    // Portal animation & closing sequence
    var wasDragging by remember { mutableStateOf(false) }
    var showCloseAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(isDraggingAny) {
        if (isDraggingAny) {
            wasDragging = true
            showCloseAnimation = false
        } else if (wasDragging) {
            showCloseAnimation = true
            delay(1200)
            showCloseAnimation = false
            wasDragging = false
        }
    }

    // Cosmic vortex rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "portalRings")
    val ringAngle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isDraggingAny) 3000 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAngle1"
    )
    val ringAngle2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isDraggingAny) 4000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAngle2"
    )

    // Handle back button when chat is open
    BackHandler(enabled = chatDialogState != ChatDialogState.IDLE) {
        scope.launch {
            chatDialogState = ChatDialogState.TRANSITIONING
            delay(200)
            chatDialogState = ChatDialogState.IDLE
            activeChatChar = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TdsmBackground)
            .onGloballyPositioned { coordinates ->
                boxWidth = coordinates.size.width
                boxHeight = coordinates.size.height
                rootCoordinates = coordinates
            }
    ) {
        // 1. Wallpaper (Custom or Default Technical Grid)
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap!!,
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Default Minimal Monochrome Grid Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSize = 32.dp.toPx()
                val width = size.width
                val height = size.height
                val gridColor = Color(0xFF161616)

                var x = 0f
                while (x < width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                    x += gridSize
                }

                var y = 0f
                while (y < height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                    y += gridSize
                }
            }
        }

        // 2. Center Cosmic / Video Warp Portal
        val portalSize = if (isDraggingAny) 260.dp else 210.dp
        val portalPx = with(density) { (if (isDraggingAny) 260.dp else 210.dp).toPx() }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(portalSize),
            contentAlignment = Alignment.Center
        ) {
            // Cosmic Rotating Rings (Canvas)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius1 = size.width * 0.46f
                val radius2 = size.width * 0.38f
                val radius3 = size.width * 0.28f

                // Outer geometric ring
                drawCircle(
                    color = if (isDraggingAny) Color(0xFF888888) else Color(0xFF333333),
                    radius = radius1,
                    center = center,
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f), ringAngle1)
                    )
                )

                // Middle counter-rotating ring
                drawCircle(
                    color = if (isDraggingAny) Color(0xFFFFFFFF) else Color(0xFF444444),
                    radius = radius2,
                    center = center,
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 20f), ringAngle2)
                    )
                )

                // Inner core ring
                drawCircle(
                    color = if (isDraggingAny) Color(0xFFAAAAAA) else Color(0xFF222222),
                    radius = radius3,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            // Video Portal Views
            if (isDraggingAny) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            try {
                                val uri = Uri.parse("android.resource://${ctx.packageName}/raw/portal_open")
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                setOnErrorListener { _, _, _ -> true }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                )
            } else if (showCloseAnimation) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            try {
                                val uri = Uri.parse("android.resource://${ctx.packageName}/raw/portal_close")
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    start()
                                }
                                setOnErrorListener { _, _, _ -> true }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                )
            } else {
                // Idle Portal Core Minimalist Glyph
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF080808))
                        .border(1.dp, Color(0xFF2E2E2E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AllInclusive,
                            contentDescription = "Portal",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "WARP PORTAL",
                            color = Color(0xFF555555),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // 3. Characters in Playground
        physicsCharacters.forEach { char ->
            val isCurrentDragging = char.isDragging
            SpriteCharacterView(
                char = char,
                modifier = Modifier
                    .offset { IntOffset(char.x.roundToInt(), char.y.roundToInt()) }
                    .size(90.dp)
                    .pointerInput(char.id) {
                        var lastTapTime = 0L
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 350L) {
                                lastTapTime = 0L
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                activeChatChar = char
                                val customGreeting = char.spriteSheetData.moveSets.firstOrNull { it.dialogue.isNotBlank() }?.dialogue
                                assistantBubbleText = if (!customGreeting.isNullOrBlank()) {
                                    "${char.spriteSheetData.name}: $customGreeting"
                                } else {
                                    "${char.spriteSheetData.name}: สวัสดีครับผู้บัญชาการ ${char.spriteSheetData.name} พร้อมรับคำสั่งแล้วครับ"
                                }
                                userBubbleText = null
                                chatInputText = ""
                                isAssistantTyping = false
                                scope.launch {
                                    chatDialogState = ChatDialogState.TRANSITIONING
                                    delay(300)
                                    chatDialogState = ChatDialogState.OPEN
                                }
                                down.consume()
                                return@awaitEachGesture
                            } else {
                                lastTapTime = currentTime
                            }

                            var isDragStarted = false
                            var dragOffset = Offset.Zero
                            val touchSlop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (isDragStarted) {
                                        val latest = viewModel.physicsCharacters.value.firstOrNull { it.id == char.id } ?: char
                                        val centerX = boxWidth / 2f
                                        val centerY = boxHeight / 2f
                                        val charCenterX = latest.x + (charSizePx / 2f)
                                        val charCenterY = latest.y + (charSizePx / 2f)

                                        val effectiveTrashCenter = if (trashBinCenter != Offset.Zero) {
                                            trashBinCenter
                                        } else {
                                            Offset(boxWidth - with(density) { 52.dp.toPx() }, boxHeight - with(density) { 52.dp.toPx() })
                                        }
                                        val distToTrash = sqrt((charCenterX - effectiveTrashCenter.x).pow(2) + (charCenterY - effectiveTrashCenter.y).pow(2))
                                        if (distToTrash < (trashThresholdPx * 1.3f)) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.removeCharacter(char.id)
                                        } else {
                                            val distanceToPortal = sqrt((charCenterX - centerX).pow(2) + (charCenterY - centerY).pow(2))
                                            val isInPortal = distanceToPortal < (portalPx * 0.75f)
                                            if (isInPortal) {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                if (com.cfks.goosedroid.brain.CharacterRegistry.isMaxLimitReached()) {
                                                    viewModel.showHud("OVERLAY LIMIT REACHED (MAX 10 UNITS)")
                                                    viewModel.updateCharacterPhysics(char.id, latest.x, latest.y, listOf(-1.5f, 1.5f).random(), listOf(-1f, 1f).random(), false)
                                                } else {
                                                    val warped = viewModel.warpCharacter(char.id)
                                                    if (warped != null) {
                                                        onLaunchOverlay(warped)
                                                    }
                                                }
                                            } else {
                                                behaviorStateMap[char.id] = "IDLE"
                                                behaviorTimerMap[char.id] = 60
                                                viewModel.updateCharacterPhysics(char.id, latest.x, latest.y, 0f, 0f, false)
                                            }
                                        }
                                    }
                                    break
                                }

                                val dragDelta = change.position - change.previousPosition
                                dragOffset += dragDelta

                                if (!isDragStarted && dragOffset.getDistance() > touchSlop) {
                                    isDragStarted = true
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    val latest = viewModel.physicsCharacters.value.firstOrNull { it.id == char.id } ?: char
                                    viewModel.updateCharacterPhysics(char.id, latest.x, latest.y, 0f, 0f, true)
                                }

                                if (isDragStarted) {
                                    val latest = viewModel.physicsCharacters.value.firstOrNull { it.id == char.id } ?: char
                                    val newX = (latest.x + dragDelta.x).coerceIn(0f, (boxWidth - charSizePx).coerceAtLeast(0f))
                                    val newY = (latest.y + dragDelta.y).coerceIn(0f, (boxHeight - charSizePx).coerceAtLeast(0f))
                                    viewModel.updateCharacterPhysics(char.id, newX, newY, 0f, 0f, true)
                                    change.consume()
                                }
                            }
                        }
                    }
            )
        }

        // 4. Top Telemetry & Unit Counter Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            color = TdsmSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, TdsmBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TdsmTextPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PROJECT: GOOSE DROID",
                        color = TdsmTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ACTIVE UNITS: ",
                        color = TdsmTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${physicsCharacters.size}",
                        color = TdsmTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = onNavigateToChatHub,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = "Chat Hub",
                            tint = TdsmTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 5. Overlay Permission Warning Banner
        AnimatedVisibility(
            visible = !hasOverlayPermission,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TdsmSurfaceElevated),
                border = BorderStroke(1.dp, TdsmBorderLight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Permission Alert",
                        tint = TdsmTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "OVERLAY PERMISSION REQUIRED",
                            color = TdsmTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "Tap here to enable display over other apps for desktop companion mode.",
                            color = TdsmTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Settings",
                        tint = TdsmTextSecondary
                    )
                }
            }
        }

        // 6. HUD Toast Notification
        AnimatedVisibility(
            visible = hudMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (!hasOverlayPermission) 130.dp else 64.dp)
        ) {
            hudMessage?.let { msg ->
                Surface(
                    color = TdsmSurfaceElevated,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, TdsmTextPrimary),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TdsmTextPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = TdsmTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // 7. Delete Bin Zone (Visible during Drag)
        AnimatedVisibility(
            visible = isDraggingAny,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 24.dp)
        ) {
            val binSize = if (isOverTrash) 72.dp else 56.dp
            Box(
                modifier = Modifier
                    .size(binSize)
                    .onGloballyPositioned { coordinates ->
                        val localPos = rootCoordinates?.localPositionOf(coordinates, Offset.Zero) ?: Offset.Zero
                        trashBinCenter = Offset(
                            localPos.x + coordinates.size.width / 2f,
                            localPos.y + coordinates.size.height / 2f
                        )
                    }
                    .clip(CircleShape)
                    .background(if (isOverTrash) Color(0xFF333333) else TdsmSurfaceElevated)
                    .border(
                        width = if (isOverTrash) 2.dp else 1.dp,
                        color = if (isOverTrash) TdsmTextPrimary else TdsmBorderLight,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Trash Bin",
                    tint = TdsmTextPrimary,
                    modifier = Modifier.size(if (isOverTrash) 32.dp else 24.dp)
                )
            }
        }

        // 8. Bottom Dock Buttons
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            color = TdsmSurface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, TdsmBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wallpaper Dialog Button
                IconButton(
                    onClick = { showWallpaperDialog = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Wallpaper,
                        contentDescription = "Manage Wallpaper",
                        tint = TdsmTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // AI Settings Button
                IconButton(
                    onClick = { onNavigateToSettings() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "AI Settings",
                        tint = TdsmTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Studio Editor Button
                IconButton(
                    onClick = { onNavigateToEditor(null) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Studio Editor",
                        tint = TdsmTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 9. Wallpaper Management Dialog
        if (showWallpaperDialog) {
            AlertDialog(
                onDismissRequest = { showWallpaperDialog = false },
                containerColor = TdsmSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        "CANVAS BACKGROUND",
                        color = TdsmTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Choose a custom image from storage or reset to default grid.",
                            color = TdsmTextSecondary,
                            fontSize = 12.sp
                        )

                        // Option 1: Pick from Device
                        OutlinedButton(
                            onClick = {
                                showWallpaperDialog = false
                                wallpaperPicker.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TdsmBorderLight)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = TdsmTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CHOOSE FROM DEVICE",
                                color = TdsmTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Option 2: Reset to Default Grid
                        OutlinedButton(
                            onClick = {
                                showWallpaperDialog = false
                                viewModel.setCustomWallpaper(null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TdsmBorderLight)
                        ) {
                            Icon(
                                Icons.Default.GridOn,
                                contentDescription = null,
                                tint = TdsmTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "RESET TO DEFAULT GRID",
                                color = TdsmTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWallpaperDialog = false }) {
                        Text("CLOSE", color = TdsmTextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        // 10. 1-Turn NPC Chat Transition & Modal Overlay (State Diagram Implementation)
        if (chatDialogState != ChatDialogState.IDLE && activeChatChar != null) {
            val char = activeChatChar!!
            val animatedScale by animateFloatAsState(
                targetValue = if (chatDialogState == ChatDialogState.OPEN) 1.5f else 1.0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "charScale"
            )

            // Dim 60% background overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TdsmOverlayDim)
                    .clickable {
                        // Dismiss on tap outside
                        scope.launch {
                            chatDialogState = ChatDialogState.TRANSITIONING
                            delay(200)
                            chatDialogState = ChatDialogState.IDLE
                            activeChatChar = null
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable(enabled = false) {}, // Prevent dismiss when tapping dialog content
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Character Display (Scaled 1.5x, Idle Animation)
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(animatedScale),
                        contentAlignment = Alignment.Center
                    ) {
                        SpriteCharacterView(
                            char = char,
                            modifier = Modifier.size(90.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1-Turn NPC Dialog Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = TdsmSurfaceElevated,
                        border = BorderStroke(1.dp, TdsmBorderLight),
                        shadowElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Character Designation
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
                                            .background(TdsmTextPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        char.spriteSheetData.name.uppercase(),
                                        color = TdsmTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Full persistent chat (Phase 1)
                                    TextButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onNavigateToChat(char.spriteSheetData.name)
                                            chatDialogState = ChatDialogState.IDLE
                                            activeChatChar = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            "CHAT",
                                            color = TdsmTextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Edit button in chat head
                                    TextButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onNavigateToEditor(char.id)
                                            chatDialogState = ChatDialogState.IDLE
                                            activeChatChar = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            "EDIT",
                                            color = TdsmTextPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Delete button in chat head
                                    TextButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.removeCharacter(char.id)
                                            chatDialogState = ChatDialogState.IDLE
                                            activeChatChar = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            "DELETE",
                                            color = Color(0xFFFF5555),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                chatDialogState = ChatDialogState.TRANSITIONING
                                                delay(200)
                                                chatDialogState = ChatDialogState.IDLE
                                                activeChatChar = null
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = TdsmTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 1-Turn Message Area: Assistant Bubble (Left)
                            if (assistantBubbleText != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
                                            .background(Color(0xFF2A2A2A))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        if (isAssistantTyping) {
                                            Text(
                                                "...",
                                                color = TdsmTextPrimary,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            Text(
                                                assistantBubbleText ?: "",
                                                color = TdsmTextPrimary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // 1-Turn Message Area: User Bubble (Right)
                            if (userBubbleText != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp))
                                            .background(Color(0xFFFFFFFF))
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            userBubbleText ?: "",
                                            color = Color.Black,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Command Chips
                            val quickMoves = char.spriteSheetData.moveSets.map { it.name }.distinct()
                            val standardDirectives = listOf("เดิน", "วิ่ง", "กระโดด", "หยุด")
                            val allQuickSuggestions = (standardDirectives + quickMoves).distinct().take(6)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allQuickSuggestions.forEach { chipText ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(TdsmSurface)
                                            .border(1.dp, TdsmBorder, RoundedCornerShape(12.dp))
                                            .clickable {
                                                chatInputText = chipText
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            chipText,
                                            color = TdsmTextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Input Bar (Auto-Focus Ready)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = chatInputText,
                                    onValueChange = { chatInputText = it },
                                    placeholder = {
                                        Text("Type a command (e.g. เดิน, วิ่ง, กระโดด)...", color = TdsmMuted, fontSize = 12.sp)
                                    },
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

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (chatInputText.isNotBlank()) {
                                            val query = chatInputText
                                            userBubbleText = query
                                            chatInputText = ""
                                            isAssistantTyping = true

                                            scope.launch {
                                                delay(250)
                                                val result = PetBrain.processCommand(context, query, char.spriteSheetData.name)
                                                isAssistantTyping = false
                                                assistantBubbleText = "${char.spriteSheetData.name}: ${result.displayReply}"

                                                // LLM DIRECTIVE DISPATCH: High priority override on character
                                                when (result.action.action.lowercase()) {
                                                    "walk" -> {
                                                        val dir = if (listOf(true, false).random()) 1.8f else -1.8f
                                                        llmDirectiveMap[char.id] = LlmDirective("WALK", vx = dir, durationFrames = 220, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                                        llmDirectiveTimerMap[char.id] = 220
                                                    }
                                                    "run" -> {
                                                        val dir = if (listOf(true, false).random()) 3.5f else -3.5f
                                                        llmDirectiveMap[char.id] = LlmDirective("RUN", vx = dir, durationFrames = 180, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                                        llmDirectiveTimerMap[char.id] = 180
                                                    }
                                                    "jump" -> {
                                                        val dir = if (listOf(true, false).random()) 1.5f else -1.5f
                                                        llmDirectiveMap[char.id] = LlmDirective("JUMP", vy = -12f, vx = dir, durationFrames = 90, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                                        llmDirectiveTimerMap[char.id] = 90
                                                    }
                                                    "idle" -> {
                                                        llmDirectiveMap[char.id] = LlmDirective("IDLE", vx = 0f, vy = 0f, durationFrames = 300)
                                                        llmDirectiveTimerMap[char.id] = 300
                                                    }
                                                    "custom_action" -> {
                                                        val mName = result.action.moveset
                                                        llmDirectiveMap[char.id] = LlmDirective("CUSTOM", movesetName = mName, durationFrames = 180)
                                                        llmDirectiveTimerMap[char.id] = 180
                                                    }
                                                    else -> {
                                                        if (result.action.moveset != null) {
                                                            llmDirectiveMap[char.id] = LlmDirective("CUSTOM", movesetName = result.action.moveset, durationFrames = 150)
                                                            llmDirectiveTimerMap[char.id] = 150
                                                        }
                                                    }
                                                }
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
            }
        }
    }
}

@Composable
fun SpriteCharacterView(char: PhysicsCharacter, modifier: Modifier) {
    val context = LocalContext.current
    var currentAnimIndex by remember { mutableStateOf(0) }
    var currentFrameIndex by remember { mutableStateOf(0) }

    LaunchedEffect(char.vx, char.vy, char.isDragging, char.currentMovesetName) {
        val moves = char.spriteSheetData.moveSets
        val facingLeft = char.facingLeft
        val isAirborne = char.vy < -1.2f || char.vy > 2.0f

        val selectedMoveset = com.cfks.goosedroid.model.MovesetMatcher.selectBestMoveset(
            moveSets = moves,
            explicitName = char.currentMovesetName,
            vx = char.vx,
            vy = char.vy,
            isAirborne = isAirborne,
            isDragging = char.isDragging,
            facingLeft = facingLeft
        )

        val newAnimIndex = if (selectedMoveset != null) {
            moves.indexOf(selectedMoveset).coerceAtLeast(0)
        } else 0

        if (currentAnimIndex != newAnimIndex) {
            currentAnimIndex = newAnimIndex
            currentFrameIndex = 0
        }
    }

    val animSequence = char.spriteSheetData.moveSets.getOrNull(currentAnimIndex) ?: char.spriteSheetData.moveSets.firstOrNull()
    // Fallback hierarchy for URI: moveset URI -> main character URI -> first moveset with non-null URI
    val activeUri = animSequence?.uri 
        ?: char.spriteSheetData.uri 
        ?: char.spriteSheetData.moveSets.firstOrNull { !it.uri.isNullOrBlank() }?.uri

    var imageBitmap by remember(activeUri) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(activeUri) {
        if (activeUri != null) {
            try {
                val uri = Uri.parse(activeUri)
                val inputStream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                val bitmap = BitmapFactory.decodeStream(inputStream)
                imageBitmap = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            imageBitmap = null
        }
    }

    LaunchedEffect(currentAnimIndex, animSequence) {
        if (animSequence != null && animSequence.frames.isNotEmpty()) {
            while (true) {
                delay(animSequence.speedMs)
                currentFrameIndex = (currentFrameIndex + 1) % animSequence.frames.size
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageBitmap != null && animSequence != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val effectiveCols = if (animSequence.columns > 0) animSequence.columns else if (char.spriteSheetData.columns > 0) char.spriteSheetData.columns else 1
                val effectiveRows = if (animSequence.rows > 0) animSequence.rows else if (char.spriteSheetData.rows > 0) char.spriteSheetData.rows else 1

                val totalCells = effectiveCols * effectiveRows
                val safeFrameIdx = if (animSequence.frames.isNotEmpty()) {
                    currentFrameIndex % animSequence.frames.size
                } else {
                    0
                }
                val rawFrameVal = animSequence.frames.getOrNull(safeFrameIdx) ?: 0
                val frameValue = rawFrameVal.coerceIn(0, (totalCells - 1).coerceAtLeast(0))

                val col = frameValue % effectiveCols
                val row = (frameValue / effectiveCols).coerceIn(0, effectiveRows - 1)

                val frameWidth = (imageBitmap!!.width / effectiveCols).coerceAtLeast(1)
                val frameHeight = (imageBitmap!!.height / effectiveRows).coerceAtLeast(1)

                val srcX = (col * frameWidth).coerceIn(0, (imageBitmap!!.width - 1).coerceAtLeast(0))
                val srcY = (row * frameHeight).coerceIn(0, (imageBitmap!!.height - 1).coerceAtLeast(0))
                val drawW = frameWidth.coerceAtMost(imageBitmap!!.width - srcX).coerceAtLeast(1)
                val drawH = frameHeight.coerceAtMost(imageBitmap!!.height - srcY).coerceAtLeast(1)

                var finalFlip = char.facingLeft
                if (animSequence != null) {
                    val name = animSequence.name.lowercase()
                    val hasLeft = name.contains("left") || name.contains("ซ้าย")
                    val hasRight = name.contains("right") || name.contains("ขวา")
                    if (hasLeft && !hasRight) {
                        finalFlip = !char.facingLeft
                    } else if (hasRight && !hasLeft) {
                        finalFlip = char.facingLeft
                    }
                }

                scale(scaleX = if (finalFlip) -1f else 1f, scaleY = 1f) {
                    drawImage(
                        image = imageBitmap!!,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(drawW, drawH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        } else {
            // Built-in Minimalist Pixel Mascot (Goose Unit)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val flip = char.facingLeft
                val frameOffset = if (currentFrameIndex % 2 == 0) 0f else 3f

                scale(scaleX = if (flip) -1f else 1f, scaleY = 1f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f + frameOffset

                    // Body
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cx - 24f, cy - 8f),
                        size = Size(48f, 32f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                    )
                    // Neck & Head
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(cx + 10f, cy - 32f),
                        size = Size(18f, 32f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f, 9f)
                    )
                    // Beak
                    drawRoundRect(
                        color = Color(0xFFAAAAAA),
                        topLeft = Offset(cx + 26f, cy - 28f),
                        size = Size(14f, 8f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    // Eye
                    drawCircle(
                        color = Color.Black,
                        radius = 2.5f,
                        center = Offset(cx + 20f, cy - 26f)
                    )
                    // Wings (Subtle gray shading)
                    drawRoundRect(
                        color = Color(0xFFCCCCCC),
                        topLeft = Offset(cx - 16f, cy - 4f),
                        size = Size(28f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )
                }
            }
        }
    }
}
