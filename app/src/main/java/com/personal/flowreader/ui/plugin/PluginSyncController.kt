package com.personal.flowreader.ui.plugin

import com.personal.flowreader.FlowApp
import com.personal.flowreader.debug.DebugLog
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
 * "Replace with site lists", and position conflict choices. Sync is user-prompted (or the
 * new-chapter worker) — opening Account does not start a sync.
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

    /** Account sheet "Sync now" (also right after sign-in). */
    fun syncNow() {
        if (!sync.supports(manifest) || ui.value.sync.running) return
        scope.launch {
            setRunning(true)
            ui.update { it.copy(error = null) }
            DebugLog.plugins("$pluginId Sync now — pulling ${manifest.name} lists + positions…")
            try {
                val report = withContext(Dispatchers.IO) { sync.run(pluginId) }
                if (report.signedOut) throw PluginException(PluginErrorCode.AuthRequired, "Sign in to ${manifest.name} to sync")
                val failedTitles = report.failedLists.mapNotNull { manifest.list(it)?.title ?: it }
                val body = summary(report)
                if (failedTitles.isNotEmpty()) {
                    val miss =
                        "Could not load ${failedTitles.joinToString(" and ")}. " +
                            "Open site from Account to refresh the session, then Sync now."
                    // Lists that did load were already applied; don't discard that work as a hard fail.
                    if (report.added == 0 && report.removed == 0 && report.pushed == 0 &&
                        report.positionsPulled == 0 && report.conflicts == 0
                    ) {
                        throw PluginException(PluginErrorCode.Network, miss)
                    }
                    DebugLog.plugins("$pluginId Sync now partial — $body; $miss")
                    ui.update { it.copy(message = "$body. $miss", showAccount = true, error = miss) }
                } else {
                    DebugLog.plugins("$pluginId Sync now finished — $body")
                    ui.update { it.copy(message = body, showAccount = true) }
                }
            } catch (t: Throwable) {
                DebugLog.plugins(
                    "$pluginId Sync now failed: ${t.message ?: t::class.java.simpleName}",
                )
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
        if (ui.value.sync.running || ui.value.busy) return
        scope.launch {
            setRunning(true)
            ui.update { it.copy(busy = true, error = null, syncListId = null, showAccount = true) }
            val modeLabel = when (mode) {
                SyncMode.Merge -> "Merge"
                SyncMode.Overwrite -> "Overwrite"
            }
            DebugLog.plugins(
                "$pluginId Replace ($modeLabel) — fetching $title from ${manifest.name}…",
            )
            try {
                val fetched = LinkedHashMap<String, List<com.personal.flowreader.plugin.api.PluginWork>>()
                val failedTitles = ArrayList<String>()
                for (list in lists) {
                    try {
                        fetched[list.id] = withContext(Dispatchers.IO) {
                            sync.lists.fetchList(pluginId, list.id)
                        }
                    } catch (t: Throwable) {
                        val auth = (t as? PluginException)?.code == PluginErrorCode.AuthRequired
                        if (auth) throw t
                        failedTitles += list.title
                        DebugLog.plugins(
                            "$pluginId Replace skipped \"${list.title}\": " +
                                (t.message ?: t::class.java.simpleName),
                        )
                    }
                }
                if (fetched.isEmpty()) {
                    throw PluginException(
                        PluginErrorCode.Network,
                        "Could not load $title. Open site from Account to refresh the session, then try again.",
                    )
                }
                val (count, positions) = withContext(Dispatchers.IO) {
                    val count = sync.lists.applyLists(pluginId, fetched, mode)
                    sync.resetListBaselines(
                        pluginId,
                        fetched.keys.filter { id -> lists.any { it.id == id && !it.isBrowse } },
                    )
                    count to sync.pullPositions(pluginId, overwrite = mode == SyncMode.Overwrite).positionsPulled
                }
                onChanged()
                val loadedTitle = syncTitle(lists.filter { it.id in fetched })
                val head = when (mode) {
                    SyncMode.Merge -> "Merged $count from $loadedTitle"
                    SyncMode.Overwrite -> "Replaced $loadedTitle with $count stories"
                }
                val miss = if (failedTitles.isEmpty()) {
                    ""
                } else {
                    ". Could not load ${failedTitles.joinToString(" and ")}"
                }
                DebugLog.plugins(
                    "$pluginId Replace ($modeLabel) done — $count stories, $positions positions$miss",
                )
                ui.update {
                    it.copy(
                        busy = false,
                        showAccount = true,
                        message = (if (positions > 0) "$head, $positions reading positions set" else head) + miss,
                        error = if (failedTitles.isEmpty()) {
                            null
                        } else {
                            "Could not load ${failedTitles.joinToString(" and ")}"
                        },
                    )
                }
                refreshState()
            } catch (t: Throwable) {
                // Keep Account open so the error hint is visible (toasts are suppressed under overlays).
                DebugLog.plugins(
                    "$pluginId Replace ($modeLabel) failed: ${t.message ?: t::class.java.simpleName}",
                )
                ui.update { it.copy(busy = false, showAccount = true) }
                fail(t, "Could not sync $title")
            }
            setRunning(false)
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
            if (r.initialPull && r.added > 0) add("imported ${r.added} from ${manifest.name}")
            else if (r.added > 0) add("${r.added} added")
            if (r.removed > 0) add("${r.removed} removed")
            if (r.positionsPulled > 0) add("${r.positionsPulled} positions updated")
            if (r.pushed > 0) add("${r.pushed} sent to ${manifest.name}")
            if (r.conflicts > 0) add("${r.conflicts} to review")
            if (r.queued > 0) add("${r.queued} waiting to send")
        }
        val guarded = r.guardedLists.mapNotNull { manifest.list(it)?.title }
        val head = when {
            parts.isEmpty() && r.initialPull -> "Imported lists from ${manifest.name}"
            parts.isEmpty() -> "Up to date with ${manifest.name}"
            r.initialPull -> "Initial sync: " + parts.joinToString(", ")
            else -> "Synced: " + parts.joinToString(", ")
        }
        return if (guarded.isEmpty()) head else "$head. ${guarded.joinToString(" and ")} looked incomplete, so nothing was removed"
    }
}
