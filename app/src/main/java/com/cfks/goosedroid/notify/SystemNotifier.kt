package com.cfks.goosedroid.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cfks.goosedroid.MainActivity
import com.cfks.goosedroid.R

/**
 * Android system notifications for background events.
 * Monochrome by nature — status bar renders the small icon as a silhouette.
 */
object SystemNotifier {

    const val CHANNEL_ENGINE = "engine_status"
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_AUTONOMOUS = "autonomous"
    const val CHANNEL_REPLIES = "llm_replies"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: String, importance: Int, desc: String) {
            val ch = NotificationChannel(id, name, importance).apply {
                description = desc
            }
            manager.createNotificationChannel(ch)
        }
        channel(
            CHANNEL_ENGINE, "AI Engine Status",
            NotificationManager.IMPORTANCE_DEFAULT,
            "Cloud/local AI connection and readiness results"
        )
        channel(
            CHANNEL_DOWNLOADS, "Model Downloads",
            NotificationManager.IMPORTANCE_DEFAULT,
            "GGUF model download progress and completion"
        )
        channel(
            CHANNEL_AUTONOMOUS, "Autonomous Thoughts",
            NotificationManager.IMPORTANCE_HIGH,
            "Proactive messages from your desktop units"
        )
        channel(
            CHANNEL_REPLIES, "LLM Replies",
            NotificationManager.IMPORTANCE_HIGH,
            "Answers from the AI when you are not on the chat screen"
        )
    }

    private fun smallIcon(context: Context): Int = context.applicationInfo.icon

    /** AI readiness result — used after connection tests and engine init. */
    fun notifyEngineStatus(context: Context, ready: Boolean, provider: String, detail: String) {
        val title = if (ready) "AI ENGINE READY" else "AI ENGINE UNAVAILABLE"
        val text = "$provider · $detail"
        val notification = NotificationCompat.Builder(context, CHANNEL_ENGINE)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(context))
            .build()
        notifySafe(context, provider.hashCode(), notification)
    }

    /** Model download finished (Phase 4). */
    fun notifyDownloadComplete(context: Context, modelName: String, sizeMb: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(smallIcon(context))
            .setContentTitle("MODEL READY")
            .setContentText("$modelName (${sizeMb}MB) downloaded successfully")
            .setAutoCancel(true)
            .setContentIntent(mainIntent(context))
            .build()
        notifySafe(context, modelName.hashCode(), notification)
    }

    /** Proactive AI thought while app is in background (Phase 7). */
    fun notifyProactiveThought(context: Context, characterName: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_AUTONOMOUS)
            .setSmallIcon(smallIcon(context))
            .setContentTitle(characterName.uppercase())
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(context))
            .build()
        notifySafe(context, characterName.hashCode(), notification)
    }

    /**
     * Fired when the LLM finishes a reply for a conversation that is NOT
     * currently on screen — the user asked to always be informed.
     */
    fun notifyReply(context: Context, characterName: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(smallIcon(context))
            .setContentTitle("${characterName.uppercase()} REPLIED")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(mainIntent(context))
            .build()
        notifySafe(context, System.currentTimeMillis().toInt(), notification)
    }

    private fun mainIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notifySafe(context: Context, id: Int, notification: android.app.Notification) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — in-app AlertBus still covers it.
            android.util.Log.w("SystemNotifier", "Notification permission missing", e)
        }
    }
}
