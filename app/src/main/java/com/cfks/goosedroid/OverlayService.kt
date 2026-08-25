package com.cfks.goosedroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.cfks.goosedroid.brain.CharacterRegistry
import com.cfks.goosedroid.brain.PetBrain
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class OverlayLlmDirective(
    val action: String, // "WALK", "RUN", "JUMP", "IDLE", "CUSTOM"
    val movesetName: String? = null,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val durationFrames: Int = 180,
    val targetDx: Float? = null,
    val targetDy: Float? = null,
    val speech: String? = null,
    val toolCall: com.cfks.goosedroid.ai.ToolCall? = null
)

class OverlayService : Service() {

    companion object {
        const val ACTION_SPAWN = "com.cfks.goosedroid.ACTION_SPAWN"
        const val ACTION_REMOVE = "com.cfks.goosedroid.ACTION_REMOVE"
        const val ACTION_REMOVE_ALL = "com.cfks.goosedroid.ACTION_REMOVE_ALL"
        const val ACTION_RECEIVE_SHARE = "com.cfks.goosedroid.ACTION_RECEIVE_SHARE"
        const val MAX_OVERLAY_UNITS = 10
    }

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Active overlay units map: unitId -> OverlayUnitController
    private val activeUnits = ConcurrentHashMap<String, OverlayUnitController>()

    // Active Chat Head Modal
    private var chatModalView: FrameLayout? = null
    private var activeChatUnitId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, createNotification(), 1073741824)
        } else {
            startForeground(1, createNotification())
        }

        // WebDropZone Callback
        com.cfks.goosedroid.server.WebDropZoneServer.onFileReceived = { file ->
            mainHandler.post {
                activeUnits.values.firstOrNull()?.let { unit ->
                    unit.speechBubbleView.visibility = View.VISIBLE
                    unit.speechBubbleView.text = "File received: ${file.name}\nSize: ${file.length() / 1024} KB"
                    com.cfks.goosedroid.brain.CharacterRegistry.addInteractionLog(unit.name, "SYSTEM: Received file from Drop Zone: ${file.name}")
                    
                    // Auto-hide bubble after 5 seconds
                    mainHandler.postDelayed({
                        if (!unit.isChatOpen) unit.speechBubbleView.visibility = View.GONE
                    }, 5000)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REMOVE -> {
                val unitId = intent.getStringExtra("id")
                if (unitId != null) {
                    removeUnit(unitId)
                }
            }
            ACTION_REMOVE_ALL -> {
                removeAllUnits()
                stopSelf()
            }
            ACTION_RECEIVE_SHARE -> {
                val sharedText = intent.getStringExtra("shared_text")
                if (sharedText != null && activeUnits.isNotEmpty()) {
                    val unit = activeUnits.values.first()
                    unit.speechBubbleView.visibility = View.VISIBLE
                    unit.speechBubbleView.text = "Received shared content!"
                    com.cfks.goosedroid.brain.CharacterRegistry.addInteractionLog(unit.name, "SYSTEM: Received Shared Content - $sharedText")
                    mainHandler.postDelayed({
                        if (!unit.isChatOpen) unit.speechBubbleView.visibility = View.GONE
                    }, 5000)
                }
            }
            else -> {
                // Default or ACTION_SPAWN
                val rawId = intent?.getStringExtra("id") ?: UUID.randomUUID().toString()
                val rawName = intent?.getStringExtra("name") ?: "GOOSE-UNIT"
                val uriStr = intent?.getStringExtra("sprite_uri") ?: intent?.getStringExtra("uri")
                val cols = intent?.getIntExtra("columns", 0)?.takeIf { it > 0 } ?: intent?.getIntExtra("cols", 1) ?: 1
                val rows = intent?.getIntExtra("rows", 1) ?: 1

                spawnOverlayUnit(rawId, rawName, uriStr, cols, rows)
            }
        }
        return START_NOT_STICKY
    }

    private fun spawnOverlayUnit(
        unitId: String,
        rawName: String,
        uriStr: String?,
        cols: Int,
        rows: Int
    ) {
        // Enforce maximum 10 overlay units constraint
        if (activeUnits.size >= MAX_OVERLAY_UNITS) {
            Toast.makeText(
                this,
                "OVERLAY LIMIT REACHED (MAX $MAX_OVERLAY_UNITS UNITS)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // If this ID already exists, do not duplicate
        if (activeUnits.containsKey(unitId)) {
            return
        }

        val spriteData = CharacterRegistry.getCharacterData(unitId)
        val finalCols = spriteData?.columns ?: cols
        val finalRows = spriteData?.rows ?: rows
        val finalUri = spriteData?.uri ?: uriStr

        // Resolve unique name so no two overlay units share the same name
        val uniqueName = CharacterRegistry.resolveUniqueName(rawName)
        CharacterRegistry.registerUnit(
            unitId,
            uniqueName,
            finalUri,
            finalCols,
            finalRows,
            spriteData?.moveSets ?: emptyList()
        )

        var bitmap: Bitmap? = null
        if (finalUri != null) {
            try {
                val uri = Uri.parse(finalUri)
                val inputStream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    contentResolver.openInputStream(uri)
                }
                bitmap = BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val overlayWidth = 240
        val overlayHeight = 240

        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val floorY = (bounds.height() - overlayHeight - 80).coerceAtLeast(80)

        // Stagger initial spawn positions along the floor
        val spawnIndex = activeUnits.size
        val initialStartX = 100 + ((spawnIndex % 4) * 160)
        val initialStartY = floorY

        val params = WindowManager.LayoutParams(
            overlayWidth, overlayHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialStartX
            y = initialStartY
        }

        val container = FrameLayout(this)
        val charView = CompanionCharacterView(this, bitmap, finalCols, finalRows, spriteData)
        val speechBubble = TextView(this).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(12, 6, 12, 6)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE121212"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#777777"))
            }
            visibility = View.GONE
        }

        val bubbleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val charParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        container.addView(charView, charParams)
        container.addView(speechBubble, bubbleParams)

        val controller = OverlayUnitController(
            id = unitId,
            name = uniqueName,
            containerView = container,
            charView = charView,
            speechBubbleView = speechBubble,
            params = params,
            bitmap = bitmap,
            cols = finalCols,
            rows = finalRows,
            spriteSheetData = spriteData
        )

        // Gesture Detection for this specific unit
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (controller.isChatOpen) return true
                charView.triggerHappyJump()
                val greetings = listOf(
                    "$uniqueName พร้อมปฏิบัติหน้าที่",
                    "หน่วย $uniqueName ออนไลน์",
                    "เมทริกซ์ $uniqueName ปกติ",
                    "ตรวจพบคำสั่ง ยูนิต $uniqueName ประจำการ"
                )
                speechBubble.text = greetings.random()
                speechBubble.visibility = View.VISIBLE

                mainHandler.postDelayed({
                    speechBubble.visibility = View.GONE
                }, 2500)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                openChatHeadModal(controller)
                return true
            }
        })

        container.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    controller.isDragging = false
                    charView.isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        controller.isDragging = true
                        charView.isDragging = true
                    }
                    if (controller.isDragging && !controller.isChatOpen) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {}
                        charView.invalidate()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    controller.isDragging = false
                    charView.isDragging = false
                    charView.invalidate()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Physics Engine Loop: Drag Priority > LLM Directives > Autonomous Random Roll Fallback
        val physicsJob = serviceScope.launch {
            val gravity = 0.55f
            var currentVx = 0f
            var currentVy = 0f
            var currentX = params.x.toFloat()
            var currentY = params.y.toFloat()
            var behaviorState = "IDLE"
            var behaviorTimer = (100..200).random()
            var isJumping = false
            var jumpGroundY = currentY
            var wasDragging = false

            while (isActive) {
                val isCompletelyIdle = behaviorState == "IDLE" && currentVx == 0f && currentVy == 0f && !isJumping && controller.activeLlmDirective == null
                val frameDelay = if (isCompletelyIdle && !controller.isDragging && !controller.isChatOpen) 160L else 16L
                delay(frameDelay)
                
                val timerDecrement = if (frameDelay == 160L) 10 else 1
                
                if (controller.isDragging || controller.isChatOpen) {
                    wasDragging = true
                    continue
                }

                if (wasDragging) {
                    currentX = params.x.toFloat()
                    currentY = params.y.toFloat()
                    isJumping = false
                    controller.activeLlmDirective = null
                    behaviorState = "IDLE"
                    currentVx = 0f
                    currentVy = 0f
                    wasDragging = false
                }

                val currentBounds = windowManager.currentWindowMetrics.bounds
                val maxX = (currentBounds.width() - overlayWidth).toFloat().coerceAtLeast(0f)
                val maxY = (currentBounds.height() - overlayHeight).toFloat().coerceAtLeast(0f)
                val minY = 40f

                // 1. CHECK ACTIVE LLM DIRECTIVE
                val activeLlm = controller.activeLlmDirective
                if (activeLlm != null && controller.llmDirectiveTimer > 0) {
                    controller.llmDirectiveTimer -= timerDecrement
                    
                    // Setup initial state for LLM directive if just starting
                    if (behaviorState != activeLlm.action && behaviorState != activeLlm.movesetName) {
                            val actionName = activeLlm.movesetName ?: activeLlm.action
                            behaviorState = actionName
                            charView.currentMovesetName = actionName
                            
                            val tDx = activeLlm.targetDx ?: (if (activeLlm.vx != 0f) activeLlm.vx * 60f else 0f)
                            val tDy = activeLlm.targetDy ?: (if (activeLlm.vy != 0f) activeLlm.vy * 60f else 0f)
                            
                            val frames = activeLlm.durationFrames.toFloat().coerceAtLeast(1f)
                            
                            if (activeLlm.action == "JUMP") {
                                isJumping = true
                                jumpGroundY = (currentY + tDy).coerceIn(minY, maxY)
                                currentVx = ((currentX + tDx).coerceIn(0f, maxX) - currentX) / frames
                                currentVy = (jumpGroundY - currentY - 0.5f * gravity * frames * frames) / frames
                            } else if (activeLlm.action == "WALK" || activeLlm.action == "RUN") {
                                isJumping = false
                                currentVx = ((currentX + tDx).coerceIn(0f, maxX) - currentX) / frames
                                currentVy = ((currentY + tDy).coerceIn(minY, maxY) - currentY) / frames
                            } else {
                                isJumping = false
                                currentVx = 0f
                                currentVy = 0f
                            }
                        }

                        if (isJumping) {
                            currentVy += gravity
                            currentY += currentVy
                            if (currentVy > 0 && currentY >= jumpGroundY) {
                                currentY = jumpGroundY
                                currentVy = 0f
                                isJumping = false
                                controller.activeLlmDirective = null
                                behaviorState = "IDLE"
                                behaviorTimer = (80..160).random()
                                currentVx = 0f
                            }
                        } else {
                            currentY += currentVy
                        }

                    } else {
                        // Clean up expired directive
                        if (controller.activeLlmDirective != null) {
                            controller.activeLlmDirective = null
                            isJumping = false
                        }

                        // 2. FALLBACK: AUTONOMOUS RANDOM ROLL (WHEN NO ACTIVE LLM INSTRUCTION)
                        if (isJumping) {
                            currentVy += gravity
                            currentY += currentVy
                            if (currentVy > 0 && currentY >= jumpGroundY) {
                                currentY = jumpGroundY
                                currentVy = 0f
                                isJumping = false
                                behaviorState = "IDLE"
                                behaviorTimer = (100..200).random()
                                currentVx = 0f
                            }
                        } else {
                            behaviorTimer -= timerDecrement

                            if (behaviorTimer <= 0) {
                                val availableBehaviors = com.cfks.goosedroid.model.MovesetMatcher.getAvailableAutonomousBehaviors(spriteData?.moveSets ?: emptyList())
                                val chosenBehavior = availableBehaviors.random()

                                when (chosenBehavior) {
                                    "IDLE" -> {
                                        behaviorState = "IDLE"
                                        currentVx = 0f
                                        currentVy = 0f
                                        behaviorTimer = (120..240).random()
                                    }
                                    "WALK" -> {
                                        behaviorState = "WALK"
                                        currentVx = if (listOf(true, false).random()) (Math.random() * 1.5 + 1.0).toFloat() else -(Math.random() * 1.5 + 1.0).toFloat()
                                        currentVy = if (listOf(true, false).random()) (Math.random() * 1.0 + 0.5).toFloat() else -(Math.random() * 1.0 + 0.5).toFloat()
                                        behaviorTimer = (140..260).random()
                                    }
                                    "RUN" -> {
                                        behaviorState = "RUN"
                                        currentVx = if (listOf(true, false).random()) (Math.random() * 2.0 + 3.0).toFloat() else -(Math.random() * 2.0 + 3.0).toFloat()
                                        currentVy = if (listOf(true, false).random()) (Math.random() * 1.5 + 1.5).toFloat() else -(Math.random() * 1.5 + 1.5).toFloat()
                                        behaviorTimer = (80..150).random()
                                    }
                                    "JUMP" -> {
                                        behaviorState = "JUMP"
                                        isJumping = true
                                        val frames = 60f
                                        val tDx = if (listOf(true, false).random()) (Math.random() * 150 + 100).toFloat() else -(Math.random() * 150 + 100).toFloat()
                                        val tDy = if (listOf(true, false).random()) (Math.random() * 100 + 50).toFloat() else -(Math.random() * 100 + 50).toFloat()
                                        
                                        jumpGroundY = (currentY + tDy).coerceIn(minY, maxY)
                                        currentVx = ((currentX + tDx).coerceIn(0f, maxX) - currentX) / frames
                                        currentVy = (jumpGroundY - currentY - 0.5f * gravity * frames * frames) / frames
                                        behaviorTimer = frames.toInt()
                                    }
                                    else -> {
                                        behaviorState = chosenBehavior
                                        currentVx = 0f
                                        currentVy = 0f
                                        behaviorTimer = (100..180).random()
                                    }
                                }
                            } else {
                                if (behaviorState == "IDLE" || (!behaviorState.equals("WALK", true) && !behaviorState.equals("RUN", true) && !behaviorState.equals("JUMP", true))) {
                                    currentVx = 0f
                                    currentVy = 0f
                                }
                                currentY += currentVy
                            }
                        }
                        charView.currentMovesetName = behaviorState
                    }

                    // Bounds Check & Bounce for 2D free roam
                    currentX += currentVx
                    if (currentX < 0f) {
                        currentX = 0f
                        currentVx = abs(currentVx)
                    } else if (currentX > maxX) {
                        currentX = maxX
                        currentVx = -abs(currentVx)
                    }
                    
                    if (!isJumping) {
                        if (currentY < minY) {
                            currentY = minY
                            currentVy = abs(currentVy)
                        } else if (currentY > maxY) {
                            currentY = maxY
                            currentVy = -abs(currentVy)
                        }
                    }

                    params.x = currentX.toInt()
                    params.y = currentY.toInt()

                    charView.vx = currentVx
                    charView.vy = currentVy
                    charView.isAirborne = isJumping
                    charView.invalidate()

                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {}
                // Loop ends here naturally
            }
        }

        controller.physicsJob = physicsJob
        activeUnits[unitId] = controller
        updateNotification()
    }

    private fun openChatHeadModal(unit: OverlayUnitController) {
        if (chatModalView != null) {
            dismissChatHeadModal()
        }
        unit.isChatOpen = true
        activeChatUnitId = unit.id

        val density = resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val modalContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#181818"))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f), Color.parseColor("#333333"))
            }
        }

        val cardParams = FrameLayout.LayoutParams(
            dp(320f),
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 1. Header: Name / Status + Delete Button + Close Button
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "UNIT // ${unit.name.uppercase()}"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        // Recall Button: Recalls this specific unit from overlay back to Playground
        val deleteBtn = TextView(this).apply {
            text = "RECALL"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(4f).toFloat()
                setStroke(dp(1f), Color.parseColor("#555555"))
            }
            setOnClickListener {
                val targetId = unit.id
                val spriteData = CharacterRegistry.getCharacterData(targetId)
                if (spriteData != null) {
                    val pChar = com.cfks.goosedroid.model.PhysicsCharacter(
                        id = targetId,
                        spriteSheetData = spriteData,
                        x = unit.containerView.x,
                        y = unit.containerView.y
                    )
                    CharacterRegistry.recallCharacterToPlayground(pChar)
                }
                dismissChatHeadModal()
                removeUnit(targetId)
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12f), dp(4f), dp(4f), dp(4f))
            setOnClickListener {
                dismissChatHeadModal()
            }
        }

        headerLayout.addView(titleView)
        headerLayout.addView(spacer)
        headerLayout.addView(deleteBtn)
        headerLayout.addView(closeBtn)
        cardLayout.addView(headerLayout)

        // 2. Character Preview at 1.5x scale
        val previewContainer = FrameLayout(this).apply {
            val p = LinearLayout.LayoutParams(dp(120f), dp(120f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8f)
                bottomMargin = dp(8f)
            }
            layoutParams = p
        }
        val previewCharView = CompanionCharacterView(this, unit.bitmap, unit.cols, unit.rows, unit.spriteSheetData)
        previewContainer.addView(previewCharView)
        cardLayout.addView(previewContainer)

        // 3. 1-Turn Message Area: Assistant Bubble (Left)
        val assistantBubble = TextView(this).apply {
            text = "สวัสดีครับผู้บัญชาการ ผมคือยูนิต ${unit.name} พร้อมรับคำสั่งแล้วครับ"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262626"))
                cornerRadius = dp(10f).toFloat()
                setStroke(dp(1f), Color.parseColor("#383838"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                bottomMargin = dp(6f)
            }
            layoutParams = lp
        }
        cardLayout.addView(assistantBubble)

        // 4. 1-Turn Message Area: User Bubble (Right)
        val userBubble = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10f).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                bottomMargin = dp(8f)
            }
            layoutParams = lp
            visibility = View.GONE
        }
        cardLayout.addView(userBubble)

        // Quick Command Suggestions Chips (Dynamic according to character's actual movesets)
        val unitMoves = unit.spriteSheetData?.moveSets ?: emptyList()
        val standardCommands = mutableListOf<String>()
        if (com.cfks.goosedroid.model.MovesetMatcher.hasWalkMoveset(unitMoves)) standardCommands.add("เดิน")
        if (com.cfks.goosedroid.model.MovesetMatcher.hasRunMoveset(unitMoves)) standardCommands.add("วิ่ง")
        if (com.cfks.goosedroid.model.MovesetMatcher.hasJumpMoveset(unitMoves)) standardCommands.add("กระโดด")
        if (com.cfks.goosedroid.model.MovesetMatcher.hasIdleMoveset(unitMoves) || standardCommands.isEmpty()) standardCommands.add("หยุด")

        val customMoves = unitMoves.map { it.name }.filter { name ->
            val lower = name.lowercase()
            !lower.contains("walk") && !lower.contains("run") && !lower.contains("jump") &&
                    !lower.contains("idle") && !lower.contains("stand") && !lower.contains("drag") &&
                    !lower.contains("held") && !lower.contains("เดิน") && !lower.contains("วิ่ง") &&
                    !lower.contains("กระโดด") && !lower.contains("หยุด")
        }
        val allQuickCommands = (standardCommands + customMoves).distinct().take(6)

        val quickChipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8f)
            }
            layoutParams = p
        }
        val quickChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        allQuickCommands.forEach { cmdText ->
            val chip = TextView(this).apply {
                text = cmdText
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#222222"))
                    cornerRadius = dp(12f).toFloat()
                    setStroke(dp(1f), Color.parseColor("#444444"))
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(6f)
                }
                layoutParams = lp
            }
            quickChipRow.addView(chip)
        }
        quickChipScroll.addView(quickChipRow)
        cardLayout.addView(quickChipScroll)

        // 5. Input Bar (EditText + Send Button)
        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val inputEditText = EditText(this).apply {
            hint = "พิมพ์คำสั่งให้ ${unit.name}..."
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#202020"))
                cornerRadius = dp(6f).toFloat()
                setStroke(dp(1f), Color.parseColor("#333333"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Attach chip click listeners to populate and send
        for (i in 0 until quickChipRow.childCount) {
            val chip = quickChipRow.getChildAt(i) as? TextView
            chip?.setOnClickListener {
                inputEditText.setText(chip.text)
            }
        }

        val sendBtn = TextView(this).apply {
            text = "SEND"
            setTextColor(Color.BLACK)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(6f).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f)
            }
            layoutParams = lp

            setOnClickListener {
                val query = inputEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    userBubble.text = query
                    userBubble.visibility = View.VISIBLE
                    inputEditText.setText("")
                    assistantBubble.text = "..."

                    serviceScope.launch {
                        delay(250)
                        val result = com.cfks.goosedroid.brain.PetBrain.processCommand(this@OverlayService, query, unit.name)
                        assistantBubble.text = result.displayReply

                        // LLM DIRECTIVE DISPATCH: High priority override on character
                            when (result.action.action.lowercase()) {
                                "walk" -> {
                                    val dir = if (listOf(true, false).random()) 1.8f else -1.8f
                                    unit.activeLlmDirective = OverlayLlmDirective("WALK", vx = dir, durationFrames = 220, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                    unit.llmDirectiveTimer = 220
                                }
                                "run" -> {
                                    val dir = if (listOf(true, false).random()) 3.5f else -3.5f
                                    unit.activeLlmDirective = OverlayLlmDirective("RUN", vx = dir, durationFrames = 180, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                    unit.llmDirectiveTimer = 180
                                }
                                "jump" -> {
                                    val dir = if (listOf(true, false).random()) 1.5f else -1.5f
                                    unit.activeLlmDirective = OverlayLlmDirective("JUMP", vy = -12f, vx = dir, durationFrames = 90, targetDx = result.action.target_dx, targetDy = result.action.target_dy)
                                    unit.llmDirectiveTimer = 90
                                }
                                "idle" -> {
                                    unit.activeLlmDirective = OverlayLlmDirective("IDLE", vx = 0f, vy = 0f, durationFrames = 300)
                                    unit.llmDirectiveTimer = 300
                                }
                                "custom_action" -> {
                                    val mName = result.action.moveset
                                    unit.activeLlmDirective = OverlayLlmDirective("CUSTOM", movesetName = mName, durationFrames = 180)
                                    unit.llmDirectiveTimer = 180
                                }
                                else -> {
                                    if (result.action.moveset != null) {
                                        unit.activeLlmDirective = OverlayLlmDirective("CUSTOM", movesetName = result.action.moveset, durationFrames = 150)
                                        unit.llmDirectiveTimer = 150
                                    }
                                }
                            }

                            // Execute Android system action if requested
                            when (result.action.action) {
                                "open_app" -> {
                                    result.action.pkg?.let { pkg ->
                                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                        if (launchIntent != null) {
                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(launchIntent)
                                        }
                                    }
                                }
                                "home" -> {
                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(homeIntent)
                                }
                            }
                        }
                    }
                }
            }

        inputBar.addView(inputEditText)
        inputBar.addView(sendBtn)
        cardLayout.addView(inputBar)

        modalContainer.addView(cardLayout, cardParams)

        // Dismiss on clicking scrim
        modalContainer.setOnClickListener {
            dismissChatHeadModal()
        }
        cardLayout.setOnClickListener {
            // Intercept click so it doesn't dismiss modal
        }

        val modalParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        this.chatModalView = modalContainer
        try {
            windowManager.addView(modalContainer, modalParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissChatHeadModal() {
        chatModalView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        chatModalView = null
        activeChatUnitId?.let { id ->
            activeUnits[id]?.isChatOpen = false
        }
        activeChatUnitId = null
    }

    private fun removeUnit(unitId: String) {
        val unit = activeUnits.remove(unitId)
        if (unit != null) {
            unit.physicsJob?.cancel()
            try {
                windowManager.removeView(unit.containerView)
            } catch (e: Exception) {}
            CharacterRegistry.unregisterUnit(unitId)
        }

        updateNotification()

        if (activeUnits.isEmpty()) {
            stopSelf()
        }
    }

    private fun removeAllUnits() {
        for ((id, unit) in activeUnits) {
            val spriteData = CharacterRegistry.getCharacterData(id)
            if (spriteData != null) {
                val pChar = com.cfks.goosedroid.model.PhysicsCharacter(
                    id = id,
                    spriteSheetData = spriteData,
                    x = unit.containerView.x,
                    y = unit.containerView.y
                )
                CharacterRegistry.recallCharacterToPlayground(pChar)
            }
            unit.physicsJob?.cancel()
            try {
                windowManager.removeView(unit.containerView)
            } catch (e: Exception) {}
        }
        activeUnits.clear()
        CharacterRegistry.clearAll()
    }

    private fun updateNotification() {
        val count = activeUnits.size
        val names = activeUnits.values.joinToString(", ") { it.name }
        val notificationText = if (count > 0) {
            "Active Units ($count/$MAX_OVERLAY_UNITS): $names"
        } else {
            "No active units."
        }

        val notification = NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("Desktop Companion Active")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_channel",
                "Desktop Companion Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("Desktop Companion Active")
            .setContentText("Active units deployed.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        dismissChatHeadModal()
        removeAllUnits()
    }
}

class OverlayUnitController(
    val id: String,
    val name: String,
    val containerView: FrameLayout,
    val charView: CompanionCharacterView,
    val speechBubbleView: TextView,
    val params: WindowManager.LayoutParams,
    val bitmap: Bitmap?,
    val cols: Int,
    val rows: Int,
    val spriteSheetData: com.cfks.goosedroid.model.SpriteSheetData? = null,
    var physicsJob: Job? = null,
    var isDragging: Boolean = false,
    var isChatOpen: Boolean = false,
    var activeLlmDirective: OverlayLlmDirective? = null,
    var llmDirectiveTimer: Int = 0
)

class CompanionCharacterView(
    context: Context,
    private val bitmap: Bitmap?,
    private val cols: Int,
    private val rows: Int,
    val spriteSheetData: com.cfks.goosedroid.model.SpriteSheetData? = null
) : View(context) {

    var vx: Float = 0f
    var vy: Float = 0f
    var isAirborne: Boolean = false
    var isDragging: Boolean = false
    var currentMovesetName: String? = null

    private var currentFrame = 0
    private var frameTimer = 0L
    private var jumpOffsetY = 0f
    private var isJumping = false
    private var facingLeft = false

    private val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    fun triggerHappyJump() {
        isJumping = true
        jumpOffsetY = -30f
        postDelayed({
            jumpOffsetY = 0f
            isJumping = false
            invalidate()
        }, 300)
        invalidate()
    }

    private val customBitmaps = mutableMapOf<String, Bitmap>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cy = (height / 2f) + jumpOffsetY
        val cx = width / 2f

        if (vx < -0.1f) {
            facingLeft = true
        } else if (vx > 0.1f) {
            facingLeft = false
        }

        val moves = spriteSheetData?.moveSets ?: emptyList()
        val activeMoveset = com.cfks.goosedroid.model.MovesetMatcher.selectBestMoveset(
            moveSets = moves,
            explicitName = currentMovesetName,
            vx = vx,
            vy = vy,
            isAirborne = isAirborne,
            isDragging = isDragging,
            facingLeft = facingLeft
        )

        // Resolve active bitmap: custom moveset bitmap or main spritesheet bitmap
        var drawBitmap = bitmap
        var drawCols = cols
        var drawRows = rows

        if (activeMoveset?.uri != null) {
            val customUri = activeMoveset.uri!!
            if (customBitmaps.containsKey(customUri)) {
                drawBitmap = customBitmaps[customUri]
                drawCols = if (activeMoveset.columns > 0) activeMoveset.columns else cols
                drawRows = if (activeMoveset.rows > 0) activeMoveset.rows else rows
            } else {
                try {
                    val uri = Uri.parse(customUri)
                    val inputStream = if (uri.scheme == "file") {
                        java.io.File(uri.path ?: "").inputStream()
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    if (bmp != null) {
                        customBitmaps[customUri] = bmp
                        drawBitmap = bmp
                        drawCols = if (activeMoveset.columns > 0) activeMoveset.columns else cols
                        drawRows = if (activeMoveset.rows > 0) activeMoveset.rows else rows
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (drawBitmap != null && drawCols > 0 && drawRows > 0) {
            val frameWidth = drawBitmap.width / drawCols
            val frameHeight = drawBitmap.height / drawRows

            val now = System.currentTimeMillis()
            val speedMs = (activeMoveset?.speedMs ?: 140L).coerceIn(40L, 500L)

            val frameCount = if (activeMoveset != null && activeMoveset.frames.isNotEmpty()) {
                activeMoveset.frames.size
            } else {
                drawCols
            }

            if (now - frameTimer > speedMs) {
                currentFrame = (currentFrame + 1) % frameCount
                frameTimer = now
            }

            val srcRect = if (activeMoveset != null && activeMoveset.frames.isNotEmpty()) {
                val safeFrameIdx = currentFrame % activeMoveset.frames.size
                val gridIndex = activeMoveset.frames[safeFrameIdx].coerceIn(0, (drawCols * drawRows) - 1)
                val c = gridIndex % drawCols
                val r = gridIndex / drawCols
                Rect(
                    c * frameWidth,
                    r * frameHeight,
                    (c + 1) * frameWidth,
                    (r + 1) * frameHeight
                )
            } else {
                val isMoving = abs(vx) > 0.1f || isAirborne
                val row = if (isMoving && drawRows > 1) 1 else 0
                val safeCol = currentFrame % drawCols
                Rect(
                    safeCol * frameWidth,
                    row * frameHeight,
                    (safeCol + 1) * frameWidth,
                    (row + 1) * frameHeight
                )
            }

            val dstRect = Rect(20, (20 + jumpOffsetY).toInt(), width - 20, (height - 20 + jumpOffsetY).toInt())

            var applyFlip = facingLeft
            if (activeMoveset != null) {
                val name = activeMoveset.name.lowercase()
                val hasLeft = name.contains("left") || name.contains("ซ้าย")
                val hasRight = name.contains("right") || name.contains("ขวา")
                if (hasLeft && !hasRight) {
                    applyFlip = !facingLeft
                } else if (hasRight && !hasLeft) {
                    applyFlip = facingLeft
                }
            }

            canvas.save()
            if (applyFlip) {
                canvas.scale(-1f, 1f, cx, cy)
            }
            canvas.drawBitmap(drawBitmap, srcRect, dstRect, paint)
            canvas.restore()
        } else {
            // Built-in Minimal Monochrome Pixel Mascot
            val isStationary = abs(vx) <= 0.1f && !isAirborne && !isDragging

            val now = System.currentTimeMillis()
            if (!isStationary && now - frameTimer > 150) {
                currentFrame = (currentFrame + 1) % 2
                frameTimer = now
            }

            val stepOffset = if (isStationary) 0f else (if (currentFrame == 0) 0f else 4f)

            canvas.save()
            if (facingLeft) {
                canvas.scale(-1f, 1f, cx, cy)
            }

            val mascotPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }

            // Body
            mascotPaint.color = Color.WHITE
            canvas.drawRoundRect(
                RectF(cx - 30f, cy - 10f + stepOffset, cx + 30f, cy + 30f + stepOffset),
                20f, 20f, mascotPaint
            )

            // Neck & Head
            mascotPaint.color = Color.WHITE
            canvas.drawRoundRect(
                RectF(cx + 12f, cy - 40f + stepOffset, cx + 36f, cy + stepOffset),
                12f, 12f, mascotPaint
            )

            // Beak
            mascotPaint.color = Color.parseColor("#AAAAAA")
            canvas.drawRoundRect(
                RectF(cx + 34f, cy - 36f + stepOffset, cx + 52f, cy - 24f + stepOffset),
                6f, 6f, mascotPaint
            )

            // Eye
            mascotPaint.color = Color.BLACK
            canvas.drawCircle(cx + 26f, cy - 32f + stepOffset, 3.5f, mascotPaint)

            // Wing Shading
            mascotPaint.color = Color.parseColor("#CCCCCC")
            canvas.drawRoundRect(
                RectF(cx - 20f, cy - 4f + stepOffset, cx + 16f, cy + 20f + stepOffset),
                12f, 12f, mascotPaint
            )

            canvas.restore()
        }
    }
}
