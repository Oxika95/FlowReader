package com.personal.flowreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.personal.flowreader.FlowApp
import com.personal.flowreader.MainActivity
import com.personal.flowreader.R

/**
 * Thin mediaPlayback FGS that owns the shade / lock-screen media card.
 * Playback stays in [TtsController]; this service only posts MediaStyle + forwards actions.
 */
class TtsPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> tts()?.skipPrev()
            ACTION_NEXT -> tts()?.skipNext()
            ACTION_PLAY -> tts()?.play()
            ACTION_PAUSE -> tts()?.pause()
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                // START / refresh: ensure we are in the foreground with a current card.
            }
        }
        startOrUpdateForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun startOrUpdateForeground() {
        val notification = buildNotification()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (_: Throwable) {
            // Soft-fail if the system rejects FGS (rare while Activity-started).
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val controller = tts()
        val playing = controller?.state?.value?.playing == true
        val title = controller?.mediaTitle().orEmpty().ifBlank { getString(R.string.app_name) }
        val text = controller?.mediaSubtitle().orEmpty()
        val token = controller?.sessionToken()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(
                if (playing) {
                    action(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE)
                } else {
                    action(android.R.drawable.ic_media_play, "Play", ACTION_PLAY)
                },
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))

        controller?.mediaCover()?.let { builder.setLargeIcon(it) }

        if (token != null) {
            @Suppress("DEPRECATION")
            val compat = MediaSessionCompat.Token.fromToken(token)
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(compat)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }

        return builder.build()
    }

    private fun action(icon: Int, title: String, action: String): NotificationCompat.Action {
        val intent = Intent(this, TtsPlaybackService::class.java).setAction(action)
        val pi = PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, title, pi)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "TTS playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Now playing controls for read-aloud"
                setShowBadge(false)
            },
        )
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun tts(): TtsController? =
        (applicationContext as? FlowApp)?.tts

    companion object {
        const val CHANNEL_ID = "flow_tts_playback"
        const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.personal.flowreader.tts.START"
        const val ACTION_STOP = "com.personal.flowreader.tts.STOP"
        const val ACTION_PLAY = "com.personal.flowreader.tts.PLAY"
        const val ACTION_PAUSE = "com.personal.flowreader.tts.PAUSE"
        const val ACTION_NEXT = "com.personal.flowreader.tts.NEXT"
        const val ACTION_PREV = "com.personal.flowreader.tts.PREV"

        @Volatile
        private var instance: TtsPlaybackService? = null

        fun start(context: Context) {
            val intent = Intent(context, TtsPlaybackService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** In-process refresh while FGS is already running (safe from background). */
        fun refresh() {
            instance?.startOrUpdateForeground()
        }

        fun stop() {
            val live = instance
            if (live != null) {
                live.stopForegroundAndSelf()
            }
        }
    }
}
