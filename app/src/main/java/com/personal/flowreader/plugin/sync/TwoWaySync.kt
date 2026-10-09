package com.personal.flowreader.plugin.sync

import android.util.Log
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginUpdateQuery
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.plugin.store.PluginListWriter
import com.personal.flowreader.plugin.store.PluginMembershipStore
import com.personal.flowreader.plugin.store.PluginReadSession
import com.personal.flowreader.plugin.store.PluginSessionStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncReport(
    val added: Int = 0,
    val removed: Int = 0,
    val pushed: Int = 0,
    val positionsPulled: Int = 0,
    val conflicts: Int = 0,
    val queued: Int = 0,
    val guardedLists: Set<String> = emptySet(),
    val signedOut: Boolean = false,
)

/** Result of pushing one membership change right away. */
enum class PushResult { Site, LocalOnly, Queued }

/**
 * Two-way sync of a plugin's syncable lists and reading positions (docs/plugins.md). Each run:
 * flush the outbox, three-way merge every list ([ListReconcile]), then every known position
 * ([ProgressReconcile]). Runs share [lock] with the new-chapter check.
 */
class TwoWaySync(private val app: FlowApp) {
    private val manager get() = app.pluginManager
    val lists = PluginListWriter(app)

    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Plugin ids whose local lists, positions or conflicts a sync changed. */
    val changes: SharedFlow<String> = _changes

    private val flushLocks = HashMap<String, Mutex>()

    private fun flushLock(pluginId: String): Mutex = synchronized(flushLocks) { flushLocks.getOrPut(pluginId) { Mutex() } }

    private fun dataDir(pluginId: String): File = manager.dataDir(pluginId)

    fun state(pluginId: String): SyncState = SyncStateStore.read(dataDir(pluginId))

    /** Plugins that sync story lists (with remote add / remove) or reading positions. */
    fun supports(manifest: PluginManifest): Boolean = syncsLists(manifest) || manifest.has(PluginCapability.ProgressSync)

    private fun syncsLists(manifest: PluginManifest): Boolean =
        manifest.has(PluginCapability.Lists) && manifest.has(PluginCapability.Membership) &&
            manifest.lists.any { it.syncable && !it.isBrowse }

    /** Every installed plugin that supports sync; failures are logged per plugin. */
    suspend fun runAll() {
        for (plugin in manager.installed.value) {
            currentCoroutineContext().ensureActive()
            if (!supports(plugin.manifest)) continue
            try {
                run(plugin.id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Sync failed for ${plugin.id}", t)
            }
        }
    }

    /** Runs unless the last sync is younger than [minAgeMs]; null when skipped. */
    suspend fun runIfStale(pluginId: String, minAgeMs: Long): SyncReport? {
        val last = state(pluginId).lastSyncedAt
        if (last > 0L && System.currentTimeMillis() - last < minAgeMs) return null
        return run(pluginId)
    }

    suspend fun run(pluginId: String): SyncReport = lock.withLock {
        val manifest = manager.get(pluginId)?.manifest ?: return@withLock SyncReport()
        if (!supports(manifest)) return@withLock SyncReport()
        if (manifest.auth != null && !manager.source(pluginId).session().loggedIn) {
            return@withLock SyncReport(signedOut = true)
        }
        flush(pluginId)
        var report = if (syncsLists(manifest)) syncLists(pluginId, manifest) else SyncReport()
        if (manifest.has(PluginCapability.ProgressSync)) report = syncPositions(pluginId, manifest, report)
        val queued = flush(pluginId)
        SyncStateStore.update(dataDir(pluginId)) { it.copy(lastSyncedAt = System.currentTimeMillis()) }
        _changes.tryEmit(pluginId)
        report.copy(queued = queued, conflicts = state(pluginId).conflicts.size)
    }

    private suspend fun syncLists(pluginId: String, manifest: PluginManifest): SyncReport {
        val dir = dataDir(pluginId)
        val planned = HashMap<String, Set<String>>()
        val dropped = LinkedHashSet<String>()
        val guarded = LinkedHashSet<String>()
        var added = 0
        var pushed = 0
        for (list in manifest.lists.filter { it.syncable && !it.isBrowse }) {
            currentCoroutineContext().ensureActive()
            val remote = try {
                lists.fetchList(pluginId, list.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PluginException) {
                if (e.code == PluginErrorCode.AuthRequired) throw e
                Log.w(TAG, "Could not read ${list.id} for $pluginId", e)
                continue
            }
            val state = SyncStateStore.read(dir)
            val pending = state.outbox.filterIsInstance<SyncOp.Membership>().filter { it.listId == list.id }
            val plan = ListReconcile.plan(
                local = PluginMembershipStore.workIds(dir, list.id),
                remote = remote.map { it.id }.toSet(),
                baseline = state.lists[list.id],
                pendingAdd = pending.filter { it.on }.map { it.workId }.toSet(),
                pendingRemove = pending.filterNot { it.on }.map { it.workId }.toSet(),
            )
            if (plan.guarded) {
                Log.w(TAG, "$pluginId ${list.id}: site list looks truncated (${remote.size}); skipped its removals")
                guarded += list.id
            }
            lists.mergeList(pluginId, list.id, remote, skip = plan.pushRemove, remove = plan.removeLocal)
            SyncStateStore.update(dir) { s ->
                var next = s
                plan.pushAdd.forEach { next = next.enqueue(SyncOp.Membership(it, list.id, on = true)) }
                plan.pushRemove.forEach { next = next.enqueue(SyncOp.Membership(it, list.id, on = false)) }
                next
            }
            planned[list.id] = plan.baseline
            dropped += plan.removeLocal
            added += plan.addLocal.size
            pushed += plan.pushAdd.size + plan.pushRemove.size
        }
        flush(pluginId)
        SyncStateStore.update(dir) { s ->
            s.copy(lists = s.lists + planned.mapValues { (id, p) -> s.listBaseline(id, p) }, guardedLists = guarded)
        }
        lists.dropUnlisted(pluginId, dropped)
        return SyncReport(added = added, removed = dropped.size, pushed = pushed, guardedLists = guarded)
    }

    private suspend fun syncPositions(pluginId: String, manifest: PluginManifest, report: SyncReport): SyncReport {
        val dir = dataDir(pluginId)
        val sessions = knownWorkIds(pluginId, manifest).mapNotNull { workId ->
            app.pluginBooks.session(manager.bookIdFor(pluginId, workId))?.takeIf { it.toc.isNotEmpty() }
        }
        if (sessions.isEmpty()) return report
        val remote = manager.source(pluginId).readPositions(
            sessions.map { PluginUpdateQuery(it.workId, it.workUrl, it.toc.size, it.toc.last().url) },
        ).associate { it.id to it.chapterUrl }
        var pulled = 0
        var pushed = 0
        for (session in sessions) {
            val remoteUrl = remote[session.workId] ?: continue
            val urls = session.toc.map { it.url }
            val row = app.db.progress().get(session.bookId)
            val localUrl = row?.takeIf(::hasSavedPosition)
                ?.let { urls[PluginSessionStore.savedChapter(session.toc, it.chapterHref, it.chapterIndex)] }
            val decision = ProgressReconcile.decide(
                local = localUrl,
                remote = remoteUrl,
                baseline = SyncStateStore.read(dir).positions[session.workId],
            ) { ProgressReconcile.tocIndex(urls, it) }
            val workId = session.workId
            when (val action = decision.action) {
                is ProgressAction.Conflict -> SyncStateStore.update(dir) {
                    it.copy(conflicts = it.conflicts + (workId to PositionConflict(workId, action.localUrl, action.remoteUrl)))
                }
                is ProgressAction.ApplyRemote -> {
                    saveBaseline(dir, workId, action.chapterUrl)
                    ProgressReconcile.tocIndex(urls, action.chapterUrl)?.let { applyPosition(pluginId, session, it, row) }
                    pulled++
                }
                is ProgressAction.PushLocal -> {
                    saveBaseline(dir, workId, action.chapterUrl)
                    val title = session.toc.getOrNull(urls.indexOf(action.chapterUrl))?.title.orEmpty()
                    SyncStateStore.update(dir) { it.enqueue(SyncOp.Position(workId, action.chapterUrl, title)) }
                    pushed++
                }
                ProgressAction.NoOp -> saveBaseline(dir, workId, decision.baseline)
            }
        }
        return report.copy(positionsPulled = pulled, pushed = report.pushed + pushed)
    }

    private fun saveBaseline(dir: File, workId: String, url: String?) {
        SyncStateStore.update(dir) { s ->
            s.copy(
                positions = if (url == null) s.positions else s.positions + (workId to url),
                conflicts = s.conflicts - workId,
            )
        }
    }

    /** Stories on any story list or in the library. */
    private suspend fun knownWorkIds(pluginId: String, manifest: PluginManifest): Set<String> {
        val dir = dataDir(pluginId)
        val out = LinkedHashSet<String>()
        manifest.lists.filterNot { it.isBrowse }.forEach { out += PluginMembershipStore.workIds(dir, it.id) }
        app.catalog.listPlugin(pluginId).mapNotNullTo(out) { row ->
            manager.resolveBookId(row.bookId)?.takeIf { it.first.id == pluginId }?.second
        }
        return out
    }

    /** Writes chapter [index] through the progress writer, whose hook downloads ahead. */
    private suspend fun applyPosition(pluginId: String, session: PluginReadSession, index: Int, row: ProgressEntity?) {
        if (row == null) {
            val cover = PluginSessionStore.readSplash(PluginSessionStore.dir(dataDir(pluginId), session.workId))?.cover.orEmpty()
            lists.upsertWork(
                pluginId,
                PluginWork(id = session.workId, title = session.title, url = session.workUrl, author = session.author, cover = cover),
            )
        }
        app.progress.submit(
            ProgressUpdate(
                bookId = session.bookId,
                chapterIndex = index,
                blockIndex = 0,
                charOffset = 0,
                fraction = index.toFloat() / session.toc.size,
                at = System.currentTimeMillis(),
                anchorText = "",
                chapterHref = session.toc[index].url,
            ),
        )
        app.progress.drain()
    }

    /**
     * The app's reading chapter of [session] moved to [index] (progress hook). Queues and tries a
     * push unless it is the synced chapter, the story has an open conflict, or it is chapter 1
     * of a story never synced (opening an unread story must not reset the site).
     */
    suspend fun localPositionChanged(session: PluginReadSession, index: Int) {
        val manifest = manager.get(session.pluginId)?.manifest ?: return
        if (!manifest.has(PluginCapability.ProgressSync)) return
        val chapter = session.toc.getOrNull(index) ?: return
        val dir = dataDir(session.pluginId)
        val state = SyncStateStore.read(dir)
        if (session.workId in state.conflicts) return
        val baseline = state.positions[session.workId]
        if (baseline == null && index == 0) return
        if (baseline != null && ProgressReconcile.tocIndex(session.toc.map { it.url }, baseline) == index) return
        SyncStateStore.update(dir) { it.enqueue(SyncOp.Position(session.workId, chapter.url, chapter.title)) }
        flush(session.pluginId)
    }

    /** List toggle in the app: queue the site change and try it now. */
    suspend fun membershipChanged(pluginId: String, workId: String, listId: String, on: Boolean): PushResult {
        val manifest = manager.get(pluginId)?.manifest ?: return PushResult.LocalOnly
        if (!manifest.has(PluginCapability.Membership)) return PushResult.LocalOnly
        val op = SyncOp.Membership(workId, listId, on)
        SyncStateStore.update(dataDir(pluginId)) { it.enqueue(op) }
        return flushLock(pluginId).withLock {
            when (val outcome = attempt(pluginId, op)) {
                is Outcome.Done -> if (outcome.site) PushResult.Site else PushResult.LocalOnly
                is Outcome.Failed -> PushResult.Queued
            }
        }
    }

    /** "Use Royal Road": take the site's chapter; "Keep mine": push the app's. */
    suspend fun resolveConflict(pluginId: String, workId: String, useRemote: Boolean) {
        val dir = dataDir(pluginId)
        val conflict = SyncStateStore.read(dir).conflicts[workId] ?: return
        val bookId = manager.bookIdFor(pluginId, workId)
        val session = app.pluginBooks.session(bookId)?.takeIf { it.toc.isNotEmpty() } ?: return
        val urls = session.toc.map { it.url }
        if (useRemote) {
            saveBaseline(dir, workId, conflict.remoteUrl)
            ProgressReconcile.tocIndex(urls, conflict.remoteUrl)?.let {
                applyPosition(pluginId, session, it, app.db.progress().get(bookId))
            }
        } else {
            saveBaseline(dir, workId, conflict.localUrl)
            val title = session.toc.getOrNull(urls.indexOf(conflict.localUrl))?.title.orEmpty()
            SyncStateStore.update(dir) { it.enqueue(SyncOp.Position(workId, conflict.localUrl, title)) }
            flush(pluginId)
        }
        _changes.tryEmit(pluginId)
    }

    /** "Replace with site lists" wrote [listIds] from the site: that is the new baseline. */
    fun resetListBaselines(pluginId: String, listIds: Collection<String>) {
        val dir = dataDir(pluginId)
        SyncStateStore.update(dir) { s ->
            val ids = listIds.toSet()
            s.copy(
                lists = s.lists + ids.associateWith { PluginMembershipStore.workIds(dir, it) },
                outbox = s.outbox.filterNot { it is SyncOp.Membership && it.listId in ids },
                guardedLists = s.guardedLists - ids,
            )
        }
    }

    /** Pushes the outbox in order; returns how many ops are still queued. Stops at the first network or sign-in failure. */
    suspend fun flush(pluginId: String): Int = flushLock(pluginId).withLock {
        val dir = dataDir(pluginId)
        for (op in SyncStateStore.read(dir).outbox) {
            currentCoroutineContext().ensureActive()
            val outcome = attempt(pluginId, op)
            if (outcome is Outcome.Failed && outcome.offline) break
        }
        SyncStateStore.read(dir).outbox.size
    }

    private sealed interface Outcome {
        /** Settled; [site] is the plugin's answer (false = the site was not changed). */
        data class Done(val site: Boolean) : Outcome

        /** Still queued; [offline] failures (network, sign-in) are not counted against the op. */
        data class Failed(val offline: Boolean) : Outcome
    }

    private suspend fun attempt(pluginId: String, op: SyncOp): Outcome {
        val dir = dataDir(pluginId)
        val source = manager.source(pluginId)
        return try {
            val site = when (op) {
                is SyncOp.Membership -> source.setMembership(op.workId, op.listId, op.on)
                is SyncOp.Position -> {
                    source.syncProgress(op.workId, PluginChapterRef(title = op.chapterTitle, url = op.chapterUrl))
                    true
                }
            }
            SyncStateStore.update(dir) { it.settle(op) }
            Outcome.Done(site)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val code = (t as? PluginException)?.code
            val offline = code == null || code == PluginErrorCode.Network || code == PluginErrorCode.Timeout ||
                code == PluginErrorCode.AuthRequired
            if (!offline) SyncStateStore.update(dir) { it.retry(op, MAX_ATTEMPTS) }
            Log.w(TAG, "Push failed for $pluginId ${op.key}", t)
            Outcome.Failed(offline)
        }
    }

    private fun hasSavedPosition(row: ProgressEntity): Boolean =
        row.chapterIndex > 0 || row.blockIndex > 0 || row.charOffset > 0

    companion object {
        private const val TAG = "FlowSync"

        /** Plugin errors (not network or sign-in) before a queued push is dropped. */
        private const val MAX_ATTEMPTS = 5

        /** Shared with [com.personal.flowreader.plugin.updates.UpdateChecker]: one plugin job at a time. */
        val lock = Mutex()
    }
}
