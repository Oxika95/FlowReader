package com.personal.flowreader.ui.plugin

import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.store.SyncMode
import com.personal.flowreader.plugin.sync.SyncReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sync status shown in the account sheet and on story cards. */
data class PluginSyncUi(
    val supported: Boolean = false,
    val running: Boolean = false,
    val lastSyncedAt: Long = 0L,
    /** Work id → chapter URLs (app, site) waiting for the user's choice. */
    val conflicts: Map<String, Pair<String, String>> = emptyMap(),
    /** Library book ids of [conflicts] (card badge). */
    val conflictBookIds: Set<String> = emptySet(),
    val queued: Int = 0,
    val guardedLists: Set<String> = emptySet(),
    /** Library book id → site's last-read chapter title, for stories whose ToC isn't loaded yet. */
    val pendingTitles: Map<String, String> = emptyMap(),
)

/**
 * Plugin tab side of two-way sync ([com.personal.flowreader.plugin.sync.TwoWaySync]): Sync now,
 * the throttled sync on tab open, "Replace with site lists", and position conflict choices.
 */
internal class PluginSyncController(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<PluginTabUi>,
    private val app: FlowApp,
    private val pluginId: String,
    private val manifest: PluginManifest,
    private val fail: (Throwable, String) -> Unit,
    private val onChanged: () -> Unit,
) {
    private val sync get() = app.pluginSync

    init {
        refreshState()
        scope.launch {
            sync.changes.filter { it == pluginId }.collect {
                refreshState()
                onChanged()
            }
        }
    }

    fun refreshState() {
        scope.launch {
            val state = withContext(Dispatchers.IO) { sync.state(pluginId) }
            ui.update {
                it.copy(
                    sync = it.sync.copy(
                        supported = sync.supports(manifest),
                        lastSyncedAt = state.lastSyncedAt,
                        conflicts = state.conflicts.mapValues { (_, c) -> c.localUrl to c.remoteUrl },
                        conflictBookIds = state.conflicts.keys.map { id -> app.pluginManager.bookIdFor(pluginId, id) }.toSet(),
                        queued = state.outbox.size,
                        guardedLists = state.guardedLists,
                        pendingTitles = state.pending.values
                            .filter { p -> p.chapterTitle.isNotBlank() }
                            .associate { p -> app.pluginManager.bookIdFor(pluginId, p.workId) to p.chapterTitle },
                    ),
                )
            }
        }
    }

    /** Tab open / session confirmed: sync quietly when the last run is older than [AUTO_SYNC_MS]. */
    fun autoSync() {
        if (!sync.supports(manifest) || !ui.value.session.loggedIn || ui.value.sync.running) return
        scope.launch {
            setRunning(true)
            runCatching { withContext(Dispatchers.IO) { sync.runIfStale(pluginId, AUTO_SYNC_MS) } }
            setRunning(false)
            refreshState()
        }
    }

    /** Account sheet "Sync now" (also right after sign-in). */
    fun syncNow() {
        if (!sync.supports(manifest) || ui.value.sync.running) return
        scope.launch {
            setRunning(true)
            try {
                val report = withContext(Dispatchers.IO) { sync.run(pluginId) }
                if (report.signedOut) throw PluginException(PluginErrorCode.AuthRequired, "Sign in to ${manifest.name} to sync")
                ui.update { it.copy(message = summary(report)) }
            } catch (t: Throwable) {
                fail(t, "Could not sync with ${manifest.name}")
            }
            setRunning(false)
            refreshState()
        }
    }

    /** Recovery: make [listId] (or every syncable list) match the site; the result becomes the sync baseline. */
    fun replaceWithSite(listId: String, mode: SyncMode) {
        val lists = syncTargets(manifest, listId).ifEmpty { return }
        val title = syncTitle(lists)
        scope.launch {
            ui.update { it.copy(busy = true, error = null, syncListId = null) }
            try {
                val (count, positions) = withContext(Dispatchers.IO) {
                    val count = sync.lists.applyLists(pluginId, lists.associate { it.id to sync.lists.fetchList(pluginId, it.id) }, mode)
                    sync.resetListBaselines(pluginId, lists.filterNot { it.isBrowse }.map { it.id })
                    count to sync.pullPositions(pluginId, overwrite = mode == SyncMode.Overwrite).positionsPulled
                }
                onChanged()
                val head = when (mode) {
                    SyncMode.Merge -> "Merged $count from $title"
                    SyncMode.Overwrite -> "Replaced $title with $count stories"
                }
                ui.update {
                    it.copy(
                        busy = false,
                        showAccount = false,
                        message = if (positions > 0) "$head, $positions reading positions set" else head,
                    )
                }
                refreshState()
            } catch (t: Throwable) {
                fail(t, "Could not sync $title")
            }
        }
    }

    fun resolveConflict(workId: String, useRemote: Boolean) {
        scope.launch {
            ui.update { it.copy(busy = true, error = null) }
            try {
                withContext(Dispatchers.IO) { sync.resolveConflict(pluginId, workId, useRemote) }
                ui.update { it.copy(busy = false) }
                refreshState()
                onChanged()
            } catch (t: Throwable) {
                fail(t, "Could not update the reading position")
            }
        }
    }

    private fun setRunning(on: Boolean) = ui.update { it.copy(sync = it.sync.copy(running = on)) }

    private fun summary(r: SyncReport): String {
        val parts = buildList {
            if (r.added > 0) add("${r.added} added")
            if (r.removed > 0) add("${r.removed} removed")
            if (r.positionsPulled > 0) add("${r.positionsPulled} positions updated")
            if (r.pushed > 0) add("${r.pushed} sent to ${manifest.name}")
            if (r.conflicts > 0) add("${r.conflicts} to review")
            if (r.queued > 0) add("${r.queued} waiting to send")
        }
        val guarded = r.guardedLists.mapNotNull { manifest.list(it)?.title }
        val head = if (parts.isEmpty()) "Up to date with ${manifest.name}" else "Synced: " + parts.joinToString(", ")
        return if (guarded.isEmpty()) head else "$head. ${guarded.joinToString(" and ")} looked incomplete, so nothing was removed"
    }

    companion object {
        const val AUTO_SYNC_MS = 10 * 60 * 1000L
    }
}
