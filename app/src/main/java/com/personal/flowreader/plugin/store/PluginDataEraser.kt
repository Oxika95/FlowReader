package com.personal.flowreader.plugin.store

import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.sync.TwoWaySync
import kotlinx.coroutines.sync.withLock

/**
 * Deletes everything the app keeps for one plugin: library entries (progress, covers, filters,
 * Queue rows), downloaded chapters, lists, sync state, `flow.storage`, settings, and sign-in.
 * Nothing on the site changes. WebView sign-in cookies are cleared by the UI caller.
 */
class PluginDataEraser(private val app: FlowApp) {
    /** Returns how many library stories were removed. */
    suspend fun erase(pluginId: String): Int = TwoWaySync.lock.withLock {
        val rows = app.catalog.listPlugin(pluginId)
        app.pluginBooks.cancelUpkeep(rows.map { it.bookId })
        rows.forEach { app.catalog.removePluginMembership(it.bookId) }
        app.pluginManager.eraseData(pluginId)
        app.pluginSync.changed(pluginId)
        rows.size
    }
}
