package com.personal.flowreader.plugin.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.flowreader.MainActivity
import com.personal.flowreader.R

/** Posts one notification per story with new chapters, grouped under a summary. */
object UpdateNotifier {
    const val CHANNEL_ID = "flow_chapter_updates"
    const val ACTION_OPEN_STORY = "com.personal.flowreader.updates.OPEN_STORY"
    const val EXTRA_PLUGIN_ID = "plugin_id"
    const val EXTRA_BOOK_ID = "book_id"
    private const val GROUP = "flow_chapter_updates"
    private const val SUMMARY_ID = 7_100
    private const val EXTRA_COUNT = "flow_new_chapter_count"
    private const val MAX_LINES = 5

    fun post(context: Context, updates: List<ChapterUpdate>) {
        if (updates.isEmpty() || !canPost(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val system = context.getSystemService(NotificationManager::class.java)
        for (update in updates) {
            val id = notificationId(update.bookId)
            // A story notified earlier and not yet opened keeps counting up.
            val shown = system?.activeNotifications?.firstOrNull { it.id == id }
                ?.notification?.extras?.getInt(EXTRA_COUNT, 0) ?: 0
            val count = shown + update.newChapters.size
            val titles = update.newChapters.map { it.title.ifBlank { "Untitled chapter" } }
            val style = NotificationCompat.InboxStyle().also { s ->
                titles.takeLast(MAX_LINES).forEach { s.addLine(it) }
                if (titles.size > MAX_LINES) s.setSummaryText("+${titles.size - MAX_LINES} more")
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flow)
                .setContentTitle("${UpdateDiff.headline(count)} · ${update.title}")
                .setContentText(titles.last())
                .setStyle(style)
                .setContentIntent(openStoryIntent(context, update))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .addExtras(android.os.Bundle().apply { putInt(EXTRA_COUNT, count) })
                .build()
            notifySafely(manager, id, notification)
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle("New chapters")
            .setStyle(NotificationCompat.InboxStyle().also { s -> updates.take(MAX_LINES).forEach { s.addLine(it.title) } })
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notifySafely(manager, SUMMARY_ID, summary)
    }

    private fun notifySafely(manager: NotificationManagerCompat, id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun notificationId(bookId: String): Int = 7_000_000 + (bookId.hashCode() and 0xFFFFF)

    private fun openStoryIntent(context: Context, update: ChapterUpdate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_STORY)
            .putExtra(EXTRA_PLUGIN_ID, update.pluginId)
            .putExtra(EXTRA_BOOK_ID, update.bookId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            notificationId(update.bookId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New chapters", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New chapters of stories with the bell on"
            },
        )
    }
}
