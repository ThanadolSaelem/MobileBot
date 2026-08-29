package com.cfks.goosedroid.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cfks.goosedroid.R
import com.cfks.goosedroid.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service to handle model downloads and show system notifications.
 */
class ModelDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationId = 1001
    private val channelId = "model_downloads"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra("model_id") ?: return START_NOT_STICKY
        val modelName = intent.getStringExtra("model_name") ?: "Model"

        startForeground(notificationId, createNotification(modelName, 0))

        serviceScope.launch {
            ModelDownloadManager.downloadStates.collectLatest { states ->
                val state = states[modelId]
                if (state != null) {
                    if (state.status == "Complete" || state.status.startsWith("Error")) {
                        val finalMsg = if (state.status == "Complete") "Download Complete" else state.status
                        updateNotification(modelName, state.progress.toInt(), finalMsg, true)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    } else {
                        updateNotification(modelName, state.progress.toInt(), state.status, false)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun createNotification(name: String, progress: Int, status: String = "Starting..."): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Downloading AI Model")
            .setContentText("$name: $status")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(name: String, progress: Int, status: String, finished: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(name, progress, status)
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
