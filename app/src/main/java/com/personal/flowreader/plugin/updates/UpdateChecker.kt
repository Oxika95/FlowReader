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
    /** The story's bell is on: post a system notification. */
    val notify: Boolean = true,
    /** Local cover image (sidecar beside the catalog entry); null when the story has none. */
    val coverPath: String? = null,
)

/**
 * Background new-chapter check over every installed plugin's monitored stories: those on a story
 * list plus bell-on library stories. Plugins with the `updates` capability are asked cheaply first
 * and only changed stories are re-fetched; others get one `loadWork` per story. New ToCs are
 * stored, the story's new-chapter count grows (library badge), and chapters within the story's
 * cache level ahead of the saved position are downloaded. Only bell-on stories notify.
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
        val notifying = HashSet<String>()
        for (workId in workIds) {
            val bookId = app.pluginManager.bookIdFor(plugin.id, workId)
            val session = app.pluginBooks.session(bookId)?.takeIf { it.toc.isNotEmpty() }
            val listedIn = members.filterValues { workId in it }.keys
            val notify = UpdateDiff.notifyOn(session?.notify, listedIn, notifyLists)
            if (!UpdateDiff.monitored(notify, listedIn)) continue
            if (notify) notifying += workId
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
                app.pluginBooks.addNewChapters(fresh.bookId, added.size)
                out += ChapterUpdate(
                    plugin.id, fresh.bookId, fresh.displayTitle, added,
                    notify = old.workId in notifying,
                    coverPath = coverPath(fresh.bookId),
                )
                downloadAhead(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Refresh failed for ${old.bookId}", t)
            }
        }
        return out
    }

    /**
     * Debug mode: one random opened library story per plugin with its last 1..3 chapters (random) posed
     * as new, for previewing notifications. Fetches and stores nothing.
     */
    suspend fun sample(): List<ChapterUpdate> =
        app.pluginManager.installed.value.mapNotNull { plugin ->
            val session = app.pluginCatalog.list(plugin.id).shuffled().firstNotNullOfOrNull { row ->
                app.pluginBooks.session(row.bookId)?.takeIf { it.toc.isNotEmpty() }
            } ?: return@mapNotNull null
            ChapterUpdate(
                plugin.id, session.bookId, session.displayTitle,
                session.toc.takeLast((1..3).random()),
                coverPath = coverPath(session.bookId),
            )
        }

    private suspend fun coverPath(bookId: String): String? =
        app.pluginCatalog.get(bookId)?.let { app.pluginCatalog.coverFile(it.storedPath) }?.absolutePath

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
        private const val MAX_LOAD_WORK_PER_RUN = 100    }
}
