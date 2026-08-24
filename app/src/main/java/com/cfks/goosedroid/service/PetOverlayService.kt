package com.cfks.goosedroid.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cfks.goosedroid.MainActivity
import com.cfks.goosedroid.MobileBotApp
import com.cfks.goosedroid.R
import com.cfks.goosedroid.brain.PetBrain
import com.cfks.goosedroid.model.ChatMessage
import com.cfks.goosedroid.model.PetAppearance
import com.cfks.goosedroid.ui.components.OverlayCompanionView
import com.cfks.goosedroid.ui.theme.MobileBotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PetOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    private lateinit var windowParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val isHonkingState = MutableStateFlow(false)
    private val isNappingState = MutableStateFlow(false)
    private val currentSpeechState = MutableStateFlow<String?>("Honk! Peace was never an option! 🪿")
    private val overlayChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val appearanceState = MutableStateFlow(PetAppearance())

    private var speechResetJob: Job? = null
    private var honkResetJob: Job? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val app = application as MobileBotApp
        appearanceState.value = app.petPreferences.loadPetAppearance()

        overlayChatMessages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "ฮ้องงงง! น้องห่านพร้อมรับคำสั่งแล้วจ้า 🪿✨",
                isFromUser = false
            )
        )

        startForeground(1001, createNotification())
        initFloatingOverlay()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MobileBotApp.CHANNEL_ID)
            .setContentTitle("MobileBot AI Companion 🪿")
            .setContentText("Desktop Pet is active on screen. Tap to open app.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun initFloatingOverlay() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 80
                y = 400
            }

            val app = application as MobileBotApp

            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@PetOverlayService)
                setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
                setContent {
                    MobileBotTheme(darkTheme = true) {
                        val appearance by appearanceState.collectAsState()
                        val isHonking by isHonkingState.collectAsState()
                        val isNapping by isNappingState.collectAsState()
                        val speech by currentSpeechState.collectAsState()
                        val messages by overlayChatMessages.collectAsState()

                        OverlayCompanionView(
                            appearance = appearance,
                            isHonking = isHonking,
                            isNapping = isNapping,
                            currentSpeech = speech,
                            chatMessages = messages,
                            onPet = {
                                app.soundManager.playPat()
                                showSpeech("งื้อออ สบายจัง ลูบอีกสิ! ❤️")
                            },
                            onPoke = {
                                app.soundManager.playHonk()
                                triggerHonkAnimation()
                            },
                            onHonk = {
                                app.soundManager.playHonk()
                                triggerHonkAnimation()
                                showSpeech("HONK!! HONK HONK!! 🪿🔊")
                            },
                            onFeed = {
                                app.soundManager.playBite()
                                showSpeech("งั่มๆๆ! ขนมปังกรอบอร่อยมากกก! 🍞")
                            },
                            onToggleNap = {
                                isNappingState.value = !isNappingState.value
                                showSpeech(if (isNappingState.value) "คร่อกฟี้... 💤" else "ตื่นแล้วจ้า! พร้อมลุย ✨")
                            },
                            onSendChat = { query ->
                                handleChatCommand(query)
                            },
                            onOpenMainActivity = {
                                val launchIntent = Intent(this@PetOverlayService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(launchIntent)
                            },
                            onDismissOverlay = {
                                stopSelf()
                            }
                        )
                    }
                }
            }

            // Dragging handler
            composeView.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = windowParams.x
                            initialY = windowParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true
                                windowParams.x = initialX + dx
                                windowParams.y = initialY + dy
                                windowManager?.updateViewLayout(composeView, windowParams)
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isDragging) {
                                isDragging = false
                                return true
                            }
                        }
                    }
                    return false
                }
            })

            floatingView = composeView
            windowManager?.addView(floatingView, windowParams)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSpeech(text: String, durationMs: Long = 4500) {
        currentSpeechState.value = text
        speechResetJob?.cancel()
        speechResetJob = serviceScope.launch {
            delay(durationMs)
            currentSpeechState.value = null
        }
    }

    private fun triggerHonkAnimation() {
        isHonkingState.value = true
        honkResetJob?.cancel()
        honkResetJob = serviceScope.launch {
            delay(500)
            isHonkingState.value = false
        }
    }

    private fun handleChatCommand(userQuery: String) {
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = userQuery,
            isFromUser = true
        )
        overlayChatMessages.value = overlayChatMessages.value + userMsg

        serviceScope.launch {
            val app = application as MobileBotApp
            val action = PetBrain.processUserCommand(userQuery, appearanceState.value)
            
            val replyText = action.reply ?: "รับทราบคำสั่งครับ!"
            val botMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = replyText,
                isFromUser = false,
                action = action
            )
            overlayChatMessages.value = overlayChatMessages.value + botMsg
            showSpeech(replyText)

            if (action.action == "honk") {
                app.soundManager.playHonk()
                triggerHonkAnimation()
            } else if (action.action == "open_app" && action.pkg != null) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(action.pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
