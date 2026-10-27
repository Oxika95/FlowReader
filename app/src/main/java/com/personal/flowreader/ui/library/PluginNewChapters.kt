package com.personal.flowreader.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.updates.ActiveChapterNotifications

/**
 * Plugin id → 1 when any of its stories still has a chapter-update notification in the shade
 * (drives plain tab dots). Independent of "N new" chips ([plugin_stories.newChapters]).
 */
@Composable
internal fun rememberPluginNotifyDots(plugins: List<InstalledPlugin>): State<Map<String, Int>> {
    val app = LocalContext.current.applicationContext as FlowApp
    val active by ActiveChapterNotifications.bookIds.collectAsState()
    val ids = plugins.map { it.id }
    return remember(ids, active) {
        derivedStateOf {
            ids.associateWith { pluginId ->
                val prefix = app.pluginManager.get(pluginId)?.manifest?.bookIdPrefix ?: pluginId
                if (active.any { bookId -> bookId.startsWith("$prefix:") }) 1 else 0
            }
        }
    }
}
