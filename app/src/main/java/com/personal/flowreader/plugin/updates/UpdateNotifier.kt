package com.personal.flowreader.plugin.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personal.flowreader.MainActivity
import com.personal.flowreader.R
import com.personal.flowreader.plugin.repo.PluginVersionUpdate

/** Posts one notification per story with new chapters (grouped under a summary), and plugin updates. */
object UpdateNotifier {
    const val CHANNEL_ID = "flow_chapter_updates"
    const val ACTION_OPEN_STORY = "com.personal.flowreader.updates.OPEN_STORY"
    const val EXTRA_PLUGIN_ID = "plugin_id"
    const val EXTRA_BOOK_ID = "book_id"
    private const val GROUP = "flow_chapter_updates"
    private const val SUMMARY_ID = 7_100
    private const val EXTRA_COUNT = "flow_new_chapter_count"
    private const val MAX_LINES = 5
    private const val PLUGIN_CHANNEL_ID = "flow_plugin_updates"
    private const val PLUGIN_UPDATES_ID = 7_200

    fun post(context: Context, updates: List<ChapterUpdate>) {
        if (updates.isEmpty() || !canPost(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        // Stories notified earlier and not yet opened keep counting up.
        val counts = context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
            .filter { it.notification.group == GROUP && it.id != SUMMARY_ID }
            .associate { it.id to it.notification.extras.getInt(EXTRA_COUNT, 0) }
            .toMutableMap()
        for (update in updates) {
            val id = notificationId(update.bookId)
            val count = (counts[id] ?: 0) + update.newChapters.size
            counts[id] = count
            val titles = update.newChapters.map { it.title.ifBlank { "Untitled chapter" } }
            val style = NotificationCompat.InboxStyle().also { s ->
                titles.takeLast(MAX_LINES).forEach { s.addLine(it) }
                if (titles.size > MAX_LINES) s.setSummaryText("+${titles.size - MAX_LINES} more")
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flow)
                .setLargeIcon(update.coverPath?.let { coverBitmap(context, it) })
                .setContentTitle(update.title)
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
        val headline = UpdateDiff.headline(counts.values.sum())
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle(headline)
            .setSubText(headline)
            .setStyle(NotificationCompat.InboxStyle().also { s -> updates.take(MAX_LINES).forEach { s.addLine(it.title) } })
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notifySafely(manager, SUMMARY_ID, summary)
    }

    /** One notification listing every pending plugin update; false when notifications are blocked. */
    fun postPluginUpdates(context: Context, updates: List<PluginVersionUpdate>): Boolean {
        if (updates.isEmpty() || !canPost(context)) return false
        ensureChannel(context, PLUGIN_CHANNEL_ID, "Plugin updates", "Newer versions of installed plugins")
        val lines = updates.map { "${it.name} ${it.from} → ${it.to}" }
        val title = if (updates.size == 1) "${updates[0].name} ${updates[0].to} is available" else "${updates.size} plugin updates available"
        val open = PendingIntent.getActivity(
            context,
            PLUGIN_UPDATES_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PLUGIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flow)
            .setContentTitle(title)
            .setContentText("Update in Settings › Plugins")
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(MAX_LINES).forEach { s.addLine(it) } })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return notifySafely(NotificationManagerCompat.from(context), PLUGIN_UPDATES_ID, notification)
    }

    private fun notifySafely(manager: NotificationManagerCompat, id: Int, notification: android.app.Notification): Boolean =
        try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
            false
        }

    /** Cover decoded at about large-icon size; null when missing or unreadable. */
    private fun coverBitmap(context: Context, path: String): Bitmap? = runCatching {
        val maxEdge = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height) * 2
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

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

    private fun ensureChannel(
        context: Context,
        id: String = CHANNEL_ID,
        name: String = "New chapters",
        description: String = "New chapters of stories with the bell on",
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply { this.description = description },
        )
    }
}
