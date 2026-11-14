package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.PluginCacheDefaults
import com.personal.flowreader.net.HttpClients
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.PluginSource
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginCardActionResult
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginWorkDetail
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * App-owned story cache for every plugin: ToC + splash on disk, chapter bodies (keyed by chapter
 * URL) from the reading position through the cache level ahead, or all of them after Download all.
 * The user's per-story settings live in each plugin's `plugin_stories` table. Plugins only fetch.
 */
class PluginBookStore(
    private val plugins: PluginManager,
    private val dbs: PluginDatabases,
    private val scope: CoroutineScope,
    private val cacheDefaults: suspend () -> PluginCacheDefaults,
) {
    private val coverHttp = HttpClients.create()

    private data class Ref(val plugin: InstalledPlugin, val workId: String, val root: File) {
        val dir: File get() = PluginSessionStore.dir(root, workId)
    }

    private fun ref(bookId: String): Ref {
        val (plugin, workId) = plugins.resolveBookId(bookId)
            ?: throw PluginException(PluginErrorCode.Unsupported, "No installed plugin for $bookId")
        return Ref(plugin, workId, plugins.dataDir(plugin.id))
    }

    private fun source(r: Ref): PluginSource = plugins.source(r.plugin.id)

    private fun stories(r: Ref): PluginStoryDao = dbs.stories(r.plugin.id)

    private val fetchLocks = HashMap<String, Mutex>()

    private fun fetchLock(bookId: String): Mutex = synchronized(fetchLocks) { fetchLocks.getOrPut(bookId) { Mutex() } }

    /**
     * Title and text of chapter [index] of [session]'s ToC: from disk, else fetched and written.
     * Bodies are found by chapter URL, so a session holding an older ToC still reads the right
     * one. Reader window, TTS and cache upkeep run concurrently; the lock shares one fetch.
     * Null for a locked chapter that was never downloaded.
     */
    private suspend fun chapterText(r: Ref, session: PluginReadSession, index: Int): Pair<String, String>? {
        val chapterRef = session.toc.getOrNull(index) ?: return null
        val fallbackTitle = chapterRef.title.ifBlank { "Chapter ${index + 1}" }
        if (session.hasEpub) {
            val chapter = PluginEpub.loadChapter(r.root, session, index) ?: return null
            return chapter.title.ifBlank { fallbackTitle } to chapter.blocks.joinToString("\n\n") { it.text }
        }
        fun stored() = PluginSessionStore.readChapterText(r.dir, chapterRef.url)
            ?.let { (title, text) -> title.ifBlank { fallbackTitle } to text }
        stored()?.let { return it }
        if (chapterRef.locked) return null
        return fetchLock(session.bookId).withLock {
            stored()?.let { return@withLock it }
            val fetched = source(r).loadChapter(chapterRef, session.workId, session.workUrl)
            val titled = PluginChapterText.titleAndText(fetched, chapterRef.title, r.plugin.manifest.bookIdPrefix, index)
            PluginSessionStore.writeChapter(r.dir, chapterRef.url, titled.first, titled.second)
            titled
        }
    }

    /** Spine file sizes for [ReaderBook] progress, or null when the session has no EPUB. */
    fun epubWeights(session: PluginReadSession): LongArray? {
        val r = runCatching { ref(session.bookId) }.getOrNull() ?: return null
        return PluginEpub.weights(r.root, session)
    }

    /** Rewrites the saved position after a ToC change, old chapter URLs first (set by the app). */
    var tocRemapped: (suspend (PluginReadSession, List<String>) -> Unit)? = null

    private val upkeepJobs = HashMap<String, Job>()
    private val upkeepHref = HashMap<String, String>()

    fun cancelUpkeep(bookIds: Collection<String>) {
        synchronized(upkeepJobs) {
            bookIds.forEach { id ->
                upkeepJobs.remove(id)?.cancel()
                upkeepHref.remove(id)
            }
        }
    }

    var positionChanged: (suspend (PluginReadSession, Int) -> Unit)? = null
    var tocStored: (suspend (PluginReadSession, String) -> Unit)? = null

    /** Sync and cache around [chapterHref]. Same href: `locusSync` still pushes locus. */
    fun scheduleMaintain(bookId: String, chapterHref: String) {
        if (chapterHref.isBlank()) return
        synchronized(upkeepJobs) {
            val sameHref = upkeepHref[bookId] == chapterHref
            val locusSync = runCatching {
                ref(bookId).plugin.manifest.has(PluginCapability.LocusSync)
            }.getOrDefault(false)
            if (sameHref && !locusSync) return
            if (!sameHref) {
                upkeepHref[bookId] = chapterHref
                upkeepJobs.remove(bookId)?.cancel()
            }
            upkeepJobs[bookId] = scope.launch(Dispatchers.IO) {
                try {
                    val session = read(ref(bookId)) ?: return@launch
                    val at = session.toc.indexOfFirst { it.url == chapterHref }
                    if (at < 0) return@launch
                    runCatching { positionChanged?.invoke(session, at) }
                    if (!sameHref && !session.hasEpub) maintainChapterCache(bookId, at)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Retried on the next chapter change or download.
                    synchronized(upkeepJobs) { if (upkeepHref[bookId] == chapterHref) upkeepHref.remove(bookId) }
                }
            }
        }
    }

    /** The plugin's data from disk plus the user's settings; null when the story was never stored. */
    private suspend fun read(r: Ref): PluginReadSession? {
        val session = PluginSessionStore.read(r.root, r.workId) ?: return null
        val story = story(r, session.bookId)
        return session.copy(
            cacheLevel = story.cacheLevel,
            cleanup = story.cleanup,
            keepAll = story.keepAll,
            notify = story.notify,
            titleOverride = story.titleOverride,
            newChapters = story.newChapters,
        )
    }

    /**
     * The story's settings row, created on first use from a 1.08 `meta.txt` when present, else
     * with the default cache policy.
     */
    private suspend fun story(r: Ref, bookId: String): PluginStoryEntity {
        val dao = stories(r)
        dao.get(bookId)?.let { return it }
        val legacy = PluginSessionStore.readLegacySettings(r.dir)
        val row = if (legacy != null) {
            PluginStoryEntity(
                bookId = bookId,
                notify = legacy.notify,
                cacheLevel = legacy.cacheLevel,
                cleanup = legacy.cleanup,
                keepAll = legacy.keepAll,
            )
        } else {
            val defaults = cacheDefaults()
            PluginStoryEntity(bookId = bookId, cacheLevel = defaults.cacheLevel, cleanup = defaults.cleanup)
        }
        dao.insert(row)
        return dao.get(bookId) ?: error("Could not store $bookId")
    }

    fun isPluginBook(bookId: String): Boolean = plugins.isPluginBook(bookId)

    fun pluginIdFor(bookId: String): String? = plugins.resolveBookId(bookId)?.first?.id

    /** Fetch the work page and persist ToC + splash (no chapter bodies). */
    suspend fun fetchAndStoreWork(pluginId: String, workId: String): PluginReadSession {
        val detail = plugins.source(pluginId).loadWork(workId)
        return ensureSessionToc(pluginId, detail)
    }

    /**
     * Persist the full ToC + splash metadata. Cached bodies stay valid (they are keyed by chapter
     * URL); bodies of chapters that left the ToC are deleted by the next cache upkeep.
     */
    suspend fun ensureSessionToc(pluginId: String, detail: PluginWorkDetail): PluginReadSession {
        if (detail.chapters.isEmpty()) {
            throw PluginException(PluginErrorCode.Parse, "This story has no chapters")
        }
        val bookId = plugins.bookIdFor(pluginId, detail.id)
        val r = ref(bookId)
        val existing = PluginSessionStore.read(r.root, r.workId)
        val oldUrls = existing?.toc?.map { it.url }.orEmpty()
        // writeMeta drops 1.08 settings from meta.txt; seed the row first.
        if (existing != null) story(r, bookId)
        PluginSessionStore.writeMeta(
            r.dir,
            PluginReadSession(
                bookId = bookId,
                pluginId = pluginId,
                workId = detail.id,
                workUrl = detail.url.ifBlank { existing?.workUrl.orEmpty() },
                title = detail.title,
                author = detail.author,
                toc = detail.chapters,
                epubFile = detail.file.ifBlank { existing?.epubFile.orEmpty() },
            ),
        )
        PluginSessionStore.writeSplash(
            r.dir,
            PluginSplashMeta(
                synopsis = detail.synopsis,
                tags = detail.tags,
                cover = detail.cover,
                card = detail.card,
            ),
        )
        val session = read(r) ?: error("Could not store $bookId")
        if (existing != null && oldUrls != detail.chapters.map { it.url }) {
            runCatching { tocRemapped?.invoke(session, oldUrls) }
        }
        runCatching { tocStored?.invoke(session, detail.readChapterUrl) }
        return session
    }

    suspend fun libraryMeta(bookId: String): PluginLibraryMeta? {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return null
        val session = PluginSessionStore.read(r.root, r.workId) ?: return null
        return PluginLibraryMeta(
            author = session.author,
            chapterCount = session.toc.size,
            downloadedCount = session.cached.size,
        )
    }

    suspend fun libraryMetas(bookIds: Collection<String>): Map<String, PluginLibraryMeta> =
        bookIds.mapNotNull { id -> libraryMeta(id)?.let { id to it } }.toMap()

    suspend fun readSplashBundle(bookId: String): Pair<PluginReadSession, PluginSplashMeta?>? {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return null
        val session = read(r) ?: return null
        return session to PluginSessionStore.readSplash(r.dir)
    }

    /**
     * Keep every chapter (cleanup never deletes them, new ones are fetched too), then download
     * them. A cancelled or failed run turns keep-all back off if it was off.
     */
    suspend fun downloadAllChapters(
        bookId: String,
        locusChapter: Int = 0,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val session = ensureSessionForDownload(bookId)
        val last = session.toc.lastIndex
        if (last < 0) return 0
        val dao = stories(ref(bookId))
        dao.setKeepAll(bookId, true)
        try {
            return maintainChapterCache(bookId, locusChapter.coerceIn(0, last), onProgress)
        } catch (t: Throwable) {
            if (!session.keepAll) withContext(NonCancellable) { dao.setKeepAll(bookId, false) }
            throw t
        }
    }

    /** Download the reading position [locusChapter] and the cache level of chapters after it. */
    suspend fun downloadAhead(
        bookId: String,
        locusChapter: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val session = ensureSessionForDownload(bookId)
        if (session.toc.isEmpty()) return 0
        return maintainChapterCache(bookId, locusChapter.coerceIn(0, session.toc.lastIndex), onProgress)
    }

    suspend fun setCachePolicy(bookId: String, cacheLevel: Int, cleanup: Boolean) {
        val r = ref(bookId)
        story(r, bookId)
        stories(r).setCachePolicy(bookId, cacheLevel.coerceAtLeast(0), cleanup)
    }

    suspend fun setKeepAll(bookId: String, on: Boolean) {
        val r = ref(bookId)
        story(r, bookId)
        stories(r).setKeepAll(bookId, on)
    }

    /** Delete every cached chapter body for [bookId]; keeps the library row and settings. */
    suspend fun clearLocalDownloads(bookId: String): Int {
        val r = ref(bookId)
        val session = read(r) ?: return 0
        if (session.toc.isEmpty()) return 0
        return PluginSessionStore.deleteChapters(r.dir, session.toc, session.toc.indices.toList())
    }

    suspend fun setNotify(bookId: String, on: Boolean) {
        val r = ref(bookId)
        story(r, bookId)
        stories(r).setNotify(bookId, on)
    }

    /** Rename the story locally ([title] blank = back to the plugin's title). Returns the shown title. */
    suspend fun setTitleOverride(bookId: String, title: String): String? {
        val r = ref(bookId)
        val session = read(r) ?: return null
        val override = title.trim()
        stories(r).setTitleOverride(bookId, override)
        return override.ifBlank { session.title }
    }

    /** Book id → new chapters, for every story of [pluginId] that has any. */
    fun newChapterCounts(pluginId: String): Flow<Map<String, Int>> =
        dbs.stories(pluginId).newChapterCounts().map { rows -> rows.associate { it.bookId to it.newChapters } }

    /** Add [count] to the story's new-chapter count. */
    suspend fun addNewChapters(bookId: String, count: Int) {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return
        story(r, bookId)
        stories(r).addNewChapters(bookId, count)
    }

    /** The story was opened in the reader: clear its new-chapter count. */
    suspend fun clearNewChapters(bookId: String) {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return
        stories(r).clearNewChapters(bookId)
    }

    /** Stored story session (ToC, policy, notify flag); null when never opened or followed. */
    suspend fun session(bookId: String): PluginReadSession? {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return null
        return runCatching { read(r) }.getOrNull()
    }

    /**
     * Ensure the locus and the cache level ahead of it (every chapter with keep-all) are on disk,
     * then delete what cleanup allows and bodies of chapters that left the ToC.
     */
    suspend fun maintainChapterCache(
        bookId: String,
        locusChapter: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val r = ref(bookId)
        val session = read(r) ?: throw PluginException(PluginErrorCode.Error, "Story session missing")
        if (session.toc.isEmpty()) return 0
        fun cached(i: Int) = PluginSessionStore.hasChapter(r.dir, session.toc[i].url)
        val fetch = PluginSessionStore.fetchIndices(locusChapter, session.toc.size, session.cachePolicy)
            .filterNot { session.toc[it].locked && !cached(it) }
        val total = fetch.size.coerceAtLeast(1)
        var downloaded = fetch.count(::cached)
        onProgress(downloaded, total)
        for (i in fetch.sorted()) {
            if (cached(i)) continue
            chapterText(r, session, i) ?: continue
            downloaded++
            onProgress(downloaded, total)
        }
        prune(r, session, locusChapter)
        return PluginSessionStore.cachedChapterCount(r.dir, session.toc)
    }

    /** Delete what cleanup allows around [locusChapter] without downloading anything. */
    suspend fun pruneChapterCache(bookId: String, locusChapter: Int) {
        val r = ref(bookId)
        val session = read(r) ?: return
        prune(r, session, locusChapter)
    }

    private fun prune(r: Ref, session: PluginReadSession, locusChapter: Int) {
        val doomed = PluginSessionStore.pruneIndices(
            PluginSessionStore.cachedChapterIndices(r.dir, session.toc),
            locusChapter,
            session.cachePolicy,
        )
        PluginSessionStore.deleteChapters(r.dir, session.toc, doomed)
    }

    private suspend fun ensureSessionForDownload(bookId: String): PluginReadSession {
        val r = ref(bookId)
        val session = read(r)
        if (session != null && session.toc.isNotEmpty()) return session
        return fetchAndStoreWork(r.plugin.id, r.workId)
    }

    /**
     * Run a plugin-declared media card action and persist the patched card.
     * Returns the result so the caller can show its toast or reload.
     */
    suspend fun runCardAction(bookId: String, actionId: String, on: Boolean?): PluginCardActionResult {
        val r = ref(bookId)
        val result = source(r).cardAction(r.workId, actionId, on)
        val patch = result.patch
        if (patch != null) {
            val current = PluginSessionStore.readCard(r.dir) ?: PluginCard()
            PluginSessionStore.writeCard(r.dir, current.apply(patch))
        }
        return result
    }

    suspend fun refreshToc(bookId: String): PluginReadSession {
        val r = ref(bookId)
        return fetchAndStoreWork(r.plugin.id, r.workId)
    }

    suspend fun deleteLocalSession(bookId: String) {
        val r = ref(bookId)
        PluginSessionStore.deleteSession(r.root, r.workId)
        stories(r).delete(bookId)
    }

    /** Open the story at [startIndex] (loading the work first if needed), with that chapter loaded. */
    suspend fun startReading(bookId: String, startIndex: Int): PluginReadSession {
        val r = ref(bookId)
        val existing = read(r)?.takeIf { it.toc.isNotEmpty() } ?: fetchAndStoreWork(r.plugin.id, r.workId)
        val start = startIndex.coerceIn(0, existing.toc.lastIndex)
        val session = existing.copy(startIndex = start, loadedThrough = start - 1, chapters = emptyList())
        chapterText(r, session, start)?.let { (title, text) ->
            session.chapters = listOf(Chapter(title, PluginChapterText.chapterBlocks(title, text, r.plugin.manifest.bookIdPrefix, start)))
            session.loadedThrough = start
        }
        scheduleMaintain(bookId, session.toc[start].url)
        return session
    }

    /** Story metadata for windowed reading (work fetched once if missing); no chapters loaded. */
    suspend fun openStory(bookId: String): PluginReadSession {
        val r = ref(bookId)
        return read(r)?.takeIf { it.toc.isNotEmpty() } ?: fetchAndStoreWork(r.plugin.id, r.workId)
    }

    /**
     * Chapter [index] of [session]'s ToC: cached text, else fetched and cached. Null for a locked
     * chapter that was never downloaded (a ToC entry only).
     */
    suspend fun chapter(session: PluginReadSession, index: Int): Chapter? {
        val r = ref(session.bookId)
        if (index !in session.toc.indices) throw PluginException(PluginErrorCode.Error, "Chapter ${index + 1} missing")
        if (session.hasEpub) return PluginEpub.loadChapter(r.root, session, index)
        val (title, text) = chapterText(r, session, index) ?: return null
        return Chapter(title, PluginChapterText.chapterBlocks(title, text, r.plugin.manifest.bookIdPrefix, index))
    }

    /** Download a cover once; null if blank or the book folder already has one. */
    suspend fun downloadCover(coverUrl: String, destBookPath: String, pluginId: String? = null): ByteArray? {
        if (coverUrl.isBlank()) return null
        val dir = File(destBookPath).parentFile ?: return null
        val existing = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
            .any { File(dir, it).let { f -> f.exists() && f.length() > 0L } }
        if (existing) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(coverUrl)
                    .header("User-Agent", com.personal.flowreader.plugin.runtime.PluginHttp.USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                bearerForCover(pluginId, coverUrl)?.let { request.header("Authorization", "Bearer $it") }
                coverHttp.newCall(request.build()).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.bytes()
                }
            }.getOrNull()
        }
    }

    private fun bearerForCover(pluginId: String?, coverUrl: String): String? {
        if (pluginId.isNullOrBlank()) return null
        val host = coverUrl.toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.") ?: return null
        val server = plugins.serverHost(pluginId) ?: return null
        if (host != server) return null
        return plugins.bearerToken(pluginId)
    }
}
