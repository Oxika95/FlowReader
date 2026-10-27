package com.personal.flowreader.plugin.updates

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Book ids that currently have a chapter-update notification in the system shade.
 * UI dots follow this set; "N new" chips follow [plugin_stories.newChapters] instead.
 */
object ActiveChapterNotifications {
    private val _bookIds = MutableStateFlow<Set<String>>(emptySet())
    val bookIds: StateFlow<Set<String>> = _bookIds.asStateFlow()

    fun markPosted(ids: Collection<String>) {
        if (ids.isEmpty()) return
        _bookIds.update { it + ids }
    }

    fun clear(bookId: String) {
        if (bookId.isBlank()) return
        _bookIds.update { it - bookId }
    }

    /** Reconcile with [NotificationManager.getActiveNotifications] (call on resume / after post). */
    fun syncFromSystem(context: Context) {
        _bookIds.value = UpdateNotifier.activeBookIds(context)
    }
}

/** Clears [ActiveChapterNotifications] when the user swipes a chapter notification away. */
class ChapterNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != UpdateNotifier.ACTION_DISMISSED) return
        val bookId = intent.getStringExtra(UpdateNotifier.EXTRA_BOOK_ID).orEmpty()
        if (bookId.isNotBlank()) ActiveChapterNotifications.clear(bookId)
        ActiveChapterNotifications.syncFromSystem(context.applicationContext)
    }
}
