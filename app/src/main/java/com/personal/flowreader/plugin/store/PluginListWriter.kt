package com.personal.flowreader.plugin.store

import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.api.PluginWork
import java.io.File

enum class SyncMode { Merge, Overwrite }

/**
 * Writes plugin list membership and the catalog rows behind it. A story leaves the library (and
 * its downloaded chapters are deleted) only once it is on none of the plugin's story lists.
 */
class PluginListWriter(private val app: FlowApp) {
    private val manager get() = app.pluginManager

    private fun dataDir(pluginId: String): File = manager.dataDir(pluginId)

    private fun storyListIds(pluginId: String): List<String> =
        manager.get(pluginId)?.manifest?.lists.orEmpty().filterNot { it.isBrowse }.map { it.id }

    /** Every page of `list(listId)`. */
    suspend fun fetchList(pluginId: String, listId: String): List<PluginWork> {
        val source = manager.source(pluginId)
        val remote = ArrayList<PluginWork>()
        var page = 1
        while (page <= MAX_SYNC_PAGES) {
            val result = source.list(listId, page)
            remote += result.items
            if (!result.hasMore || result.items.isEmpty()) break
            page++
        }
        return remote
    }

    /**
     * Writes fetched lists into the list files; story lists also get catalog rows. Overwrite drops
     * a story only after every list is written, so one moving between synced lists keeps its cache.
     */
    suspend fun applyLists(pluginId: String, remote: Map<String, List<PluginWork>>, mode: SyncMode): Int {
        val manifest = manager.get(pluginId)?.manifest ?: return 0
        val dir = dataDir(pluginId)
        val dropped = LinkedHashSet<String>()
        remote.forEach { (listId, items) ->
            val browse = manifest.list(listId)?.isBrowse == true
            val remoteIds = items.map { it.id }.toSet()
            val local = PluginMembershipStore.read(dir, listId)
            val kept = if (mode == SyncMode.Overwrite) emptyList() else local.filter { it.id !in remoteIds }
            if (mode == SyncMode.Overwrite && !browse) local.filter { it.id !in remoteIds }.forEach { dropped += it.id }
            PluginMembershipStore.write(dir, listId, items + kept)
            if (!browse) items.forEach { work -> runCatching { upsertWork(pluginId, work) } }
        }
        dropUnlisted(pluginId, dropped)
        return remote.values.flatten().map { it.id }.distinct().size
    }

    /**
     * Two-way sync result for one list: [remote] rows in site order (minus [skip], removed in the
     * app), then local-only rows not in [remove]. New stories get catalog rows.
     */
    suspend fun mergeList(
        pluginId: String,
        listId: String,
        remote: List<PluginWork>,
        skip: Set<String>,
        remove: Set<String>,
    ) {
        val dir = dataDir(pluginId)
        val local = PluginMembershipStore.read(dir, listId)
        val localIds = local.map { it.id }.toSet()
        val fromSite = remote.filter { it.id !in skip }
        val siteIds = fromSite.map { it.id }.toSet()
        val rows = fromSite + local.filter { it.id !in siteIds && it.id !in remove }
        PluginMembershipStore.write(dir, listId, rows)
        fromSite.filter { it.id !in localIds || app.db.progress().get(manager.bookIdFor(pluginId, it.id)) == null }
            .forEach { work -> runCatching { upsertWork(pluginId, work) } }
    }

    /** Removes the library entry and downloads of each of [workIds] that is on no story list. */
    suspend fun dropUnlisted(pluginId: String, workIds: Collection<String>) {
        val dir = dataDir(pluginId)
        val listIds = storyListIds(pluginId)
        workIds.filter { PluginMembershipStore.listsContaining(dir, listIds, it).isEmpty() }.forEach { workId ->
            val bookId = manager.bookIdFor(pluginId, workId)
            app.catalog.removePluginMembership(bookId)
            app.pluginBooks.deleteLocalSession(bookId)
        }
    }

    /** Library (catalog) row for [work], downloading its cover once. */
    suspend fun upsertWork(pluginId: String, work: PluginWork) {
        val bookId = manager.bookIdFor(pluginId, work.id)
        val existing = app.db.progress().get(bookId)
        var coverBytes: ByteArray? = null
        if (work.cover.isNotBlank()) {
            val path = existing?.storedPath
                ?: File(
                    File(app.booksDir, bookId.replace(Regex("[^A-Za-z0-9._-]"), "_")).apply { mkdirs() },
                    "book.txt",
                ).absolutePath
            coverBytes = app.pluginBooks.downloadCover(work.cover, path)
        }
        app.catalog.upsertPluginCatalogEntry(
            bookId = bookId,
            title = work.title,
            sourceUri = work.url,
            sourceKind = pluginId,
            coverBytes = coverBytes,
        )
    }

    companion object {
        private const val MAX_SYNC_PAGES = 100
    }
}
