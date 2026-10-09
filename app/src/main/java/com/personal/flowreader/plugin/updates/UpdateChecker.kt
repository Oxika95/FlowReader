package com.personal.flowreader.plugin.updates

import android.util.Log
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginUpdateQuery
import com.personal.flowreader.plugin.store.PluginMembershipStore
import com.personal.flowreader.plugin.store.PluginReadSession
import com.personal.flowreader.plugin.store.PluginSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.personal.flowreader.plugin.sync.TwoWaySync
import kotlinx.coroutines.sync.withLock

/** A monitored story that gained chapters during a check. */
data class ChapterUpdate(
    val pluginId: String,
    val bookId: String,
    val title: String,
    val newChapters: List<PluginChapterRef>,
)

/**
 * Background new-chapter check over every installed plugin's monitored stories (bell on).
 * Plugins with the `updates` capability are asked cheaply first and only changed stories are
 * re-fetched; others get one `loadWork` per story. New ToCs are stored, and chapters within the
 * story's cache level ahead of the saved position are downloaded.
 */
class UpdateChecker(private val app: FlowApp) {
    suspend fun run(): List<ChapterUpdate> = TwoWaySync.lock.withLock {
        val out = ArrayList<ChapterUpdate>()
        for (plugin in app.pluginManager.installed.value) {
            currentCoroutineContext().ensureActive()
            try {
                out += checkPlugin(plugin)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Update check failed for ${plugin.id}", t)
            }
        }
        out
    }

    private suspend fun checkPlugin(plugin: InstalledPlugin): List<ChapterUpdate> {
        val manifest = plugin.manifest
        val dataDir = app.pluginManager.dataDir(plugin.id)
        val members = manifest.lists.filterNot { it.isBrowse }
            .associate { it.id to PluginMembershipStore.workIds(dataDir, it.id) }
        val notifyLists = UpdateDiff.notifyLists(manifest.lists)
        val workIds = LinkedHashSet<String>()
        members.values.forEach { workIds += it }
        app.pluginCatalog.list(plugin.id).mapTo(workIds) { it.workId }

        val monitored = ArrayList<PluginReadSession>()
        val baseline = ArrayList<String>()
        for (workId in workIds) {
            val bookId = app.pluginManager.bookIdFor(plugin.id, workId)
            val session = app.pluginBooks.session(bookId)?.takeIf { it.toc.isNotEmpty() }
            val listedIn = members.filterValues { workId in it }.keys
            if (!UpdateDiff.notifyOn(session?.notify, listedIn, notifyLists)) continue
            if (session == null) baseline += workId else monitored += session
        }

        // Stories followed but never opened have no ToC to compare against yet.
        for (workId in baseline.shuffled().take(MAX_BASELINE_PER_RUN)) {
            currentCoroutineContext().ensureActive()
            runCatching { app.pluginBooks.fetchAndStoreWork(plugin.id, workId) }
                .onFailure { if (it is CancellationException) throw it }
        }
        if (monitored.isEmpty()) return emptyList()

        val toRefresh = if (manifest.has(PluginCapability.Updates)) {
            val queries = monitored.associateBy { it.workId }
            val infos = app.pluginManager.source(plugin.id).checkUpdates(
                monitored.map { s ->
                    PluginUpdateQuery(
                        id = s.workId,
                        url = s.workUrl,
                        chapters = s.toc.size,
                        lastChapterUrl = s.toc.last().url,
                    )
                },
            )
            val changed = infos.filter { info ->
                val s = queries[info.id] ?: return@filter false
                info.isNewer(PluginUpdateQuery(s.workId, s.workUrl, s.toc.size, s.toc.last().url))
            }.map { it.id }.toSet()
            monitored.filter { it.workId in changed }
        } else {
            if (monitored.size <= MAX_LOAD_WORK_PER_RUN) monitored else monitored.shuffled().take(MAX_LOAD_WORK_PER_RUN)
        }

        val out = ArrayList<ChapterUpdate>()
        for (old in toRefresh) {
            currentCoroutineContext().ensureActive()
            try {
                val fresh = app.pluginBooks.fetchAndStoreWork(plugin.id, old.workId)
                val added = UpdateDiff.newChapters(old.toc.map { it.url }, fresh.toc)
                if (added.isEmpty()) continue
                out += ChapterUpdate(plugin.id, fresh.bookId, fresh.title, added)
                downloadAhead(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Refresh failed for ${old.bookId}", t)
            }
        }
        return out
    }

    /** Fill the cache level ahead of the saved position; stories never read are left alone. */
    private suspend fun downloadAhead(session: PluginReadSession) {
        val bookId = session.bookId
        val row = app.pluginCatalog.get(bookId)?.takeIf { it.position.hasProgress } ?: return
        try {
            val locus = PluginSessionStore.savedChapter(session.toc, row.position.chapterHref, row.position.chapterIndex)
            app.pluginBooks.maintainChapterCache(bookId, locus)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Auto-download failed for $bookId", t)
        }
    }

    companion object {
        private const val TAG = "FlowUpdates"
        private const val MAX_BASELINE_PER_RUN = 10
        private const val MAX_LOAD_WORK_PER_RUN = 100
    }
}
