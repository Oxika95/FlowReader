package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.PluginCacheDefaults
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.PluginSource
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginCardActionResult
import com.personal.flowreader.plugin.api.PluginChapter
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginWorkDetail
import com.personal.flowreader.share.HtmlParagraphs
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * App-owned story cache for every plugin: ToC + splash on disk, chapter bodies from the reading
 * position through the cache level ahead (plus Download all pins), and the in-memory reader
 * stream. Plugins only fetch.
 */
class PluginBookStore(
    private val plugins: PluginManager,
    private val scope: CoroutineScope,
    private val cacheDefaults: suspend () -> PluginCacheDefaults,
) {
    private val coverHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class Ref(val plugin: InstalledPlugin, val workId: String, val root: File) {
        val dir: File get() = PluginSessionStore.dir(root, workId)
    }

    private fun ref(bookId: String): Ref {
        val (plugin, workId) = plugins.resolveBookId(bookId)
            ?: throw PluginException(PluginErrorCode.Unsupported, "No installed plugin for $bookId")
        return Ref(plugin, workId, plugins.dataDir(plugin.id))
    }

    private fun source(r: Ref): PluginSource = plugins.source(r.plugin.id)

    private val fetchLocks = HashMap<String, Mutex>()

    private fun fetchLock(bookId: String): Mutex = synchronized(fetchLocks) { fetchLocks.getOrPut(bookId) { Mutex() } }

    /**
     * Title and text of chapter [index]: from disk, else fetched and written. Reader window, TTS
     * and cache upkeep run concurrently; the lock makes them share one fetch per chapter.
     */
    private suspend fun chapterText(r: Ref, session: PluginReadSession, index: Int): Pair<String, String>? {
        PluginSessionStore.readChapterText(r.dir, index)?.let { return it }
        val chapterRef = session.toc.getOrNull(index) ?: return null
        return fetchLock(session.bookId).withLock {
            PluginSessionStore.readChapterText(r.dir, index)?.let { return@withLock it }
            val fetched = source(r).loadChapter(chapterRef, session.workId, session.workUrl)
            val titled = titleAndText(fetched, chapterRef.title, r.plugin.manifest.bookIdPrefix, index)
            PluginSessionStore.writeChapter(r.dir, index, titled.first, titled.second)
            titled
        }
    }

    private val upkeepJobs = HashMap<String, Job>()
    private val upkeepLocus = HashMap<String, Int>()

    /** Two-way sync's push of a new reading chapter (set by the app). */
    var positionChanged: (suspend (PluginReadSession, Int) -> Unit)? = null

    /**
     * The reading position of [bookId] is now chapter [locus]: when the chapter changed, sync it
     * to the site and fetch / clean up around it. A newer locus cancels the running upkeep.
     */
    fun scheduleMaintain(bookId: String, locus: Int) {
        synchronized(upkeepJobs) {
            if (upkeepLocus[bookId] == locus) return
            upkeepLocus[bookId] = locus
            upkeepJobs.remove(bookId)?.cancel()
            upkeepJobs[bookId] = scope.launch(Dispatchers.IO) {
                try {
                    val r = ref(bookId)
                    read(r)?.let { session -> runCatching { positionChanged?.invoke(session, locus) } }
                    maintainChapterCache(bookId, locus)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Retried on the next chapter change or download.
                    synchronized(upkeepJobs) { if (upkeepLocus[bookId] == locus) upkeepLocus.remove(bookId) }
                }
            }
        }
    }

    private fun read(r: Ref): PluginReadSession? =
        PluginSessionStore.read(r.root, r.workId)

    fun isPluginBook(bookId: String): Boolean = plugins.isPluginBook(bookId)

    fun pluginIdFor(bookId: String): String? = plugins.resolveBookId(bookId)?.first?.id

    /** Fetch the work page and persist ToC + splash (no chapter bodies). */
    suspend fun fetchAndStoreWork(pluginId: String, workId: String): PluginReadSession {
        val detail = plugins.source(pluginId).loadWork(workId)
        return ensureSessionToc(pluginId, detail)
    }

    /**
     * Persist the full ToC + splash metadata. Keeps the stream position, cache policy, pins, and
     * cached bodies, following each chapter URL to its new index when chapters were inserted.
     */
    suspend fun ensureSessionToc(pluginId: String, detail: PluginWorkDetail): PluginReadSession {
        if (detail.chapters.isEmpty()) {
            throw PluginException(PluginErrorCode.Parse, "This story has no chapters")
        }
        val r = ref(plugins.bookIdFor(pluginId, detail.id))
        val existing = read(r)
        val policy = existing?.let { PluginCacheDefaults(it.cacheLevel, it.cleanup) } ?: cacheDefaults()
        val oldUrls = existing?.toc?.map { it.url }.orEmpty()
        val newUrls = detail.chapters.map { it.url }
        val moves = PluginSessionStore.chapterMoves(oldUrls, newUrls)
        val wasAllPinned = existing != null && existing.pinnedRanges == listOf(0..existing.toc.lastIndex)
        val pins = when {
            wasAllPinned -> listOf(0..detail.chapters.lastIndex)
            existing == null -> emptyList()
            else -> PluginSessionStore.remapRanges(existing.pinnedRanges, moves, newUrls.size)
        }
        val start = existing?.let { PluginSessionStore.movedIndex(moves, it.startIndex, newUrls.size) ?: it.startIndex } ?: 0
        val session = PluginReadSession(
            bookId = plugins.bookIdFor(pluginId, detail.id),
            pluginId = pluginId,
            workId = detail.id,
            workUrl = detail.url.ifBlank { existing?.workUrl.orEmpty() },
            title = detail.title,
            author = detail.author,
            toc = detail.chapters,
            startIndex = start.coerceIn(0, detail.chapters.lastIndex),
            loadedThrough = if (existing == null || oldUrls == newUrls) existing?.loadedThrough ?: -1 else start - 1,
            chapters = emptyList(),
            cacheLevel = policy.cacheLevel,
            cleanup = policy.cleanup,
            pinnedRanges = PluginSessionStore.clipPinnedRanges(pins, detail.chapters.size),
            notify = existing?.notify,
        )
        PluginSessionStore.writeMeta(r.dir, session)
        PluginSessionStore.writeSplash(
            r.dir,
            PluginSplashMeta(
                synopsis = detail.synopsis,
                tags = detail.tags,
                cover = detail.cover,
                card = detail.card,
            ),
        )
        PluginSessionStore.reconcileChapterFiles(r.dir, oldUrls, newUrls)
        return session
    }

    fun libraryMeta(bookId: String): PluginLibraryMeta? {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return null
        val session = read(r) ?: return null
        return PluginLibraryMeta(
            author = session.author,
            chapterCount = session.toc.size,
            downloadedCount = PluginSessionStore.cachedChapterCount(r.dir, session.toc.size),
        )
    }

    fun libraryMetas(bookIds: Collection<String>): Map<String, PluginLibraryMeta> =
        bookIds.mapNotNull { id -> libraryMeta(id)?.let { id to it } }.toMap()

    fun readSplashBundle(bookId: String): Pair<PluginReadSession, PluginSplashMeta?>? {
        val r = runCatching { ref(bookId) }.getOrNull() ?: return null
        val session = read(r) ?: return null
        return session to PluginSessionStore.readSplash(r.dir)
    }

    fun cachedChapterIndices(bookId: String): Set<Int> {
        val r = ref(bookId)
        val session = read(r) ?: return emptySet()
        return PluginSessionStore.cachedChapterIndices(r.dir, session.toc.size)
    }

    /**
     * Pin the whole ToC (cleanup never deletes it), then download it. A cancelled or failed run
     * restores the earlier pins so stream upkeep doesn't keep bulk-fetching the rest.
     */
    suspend fun downloadAllChapters(
        bookId: String,
        locusChapter: Int = 0,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val session = ensureSessionForDownload(bookId)
        val last = session.toc.lastIndex
        if (last < 0) return 0
        updatePinnedRanges(bookId, listOf(0..last))
        try {
            return maintainChapterCache(bookId, locusChapter.coerceIn(0, last), onProgress)
        } catch (t: Throwable) {
            updatePinnedRanges(bookId, session.pinnedRanges)
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

    fun setCachePolicy(bookId: String, cacheLevel: Int, cleanup: Boolean) {
        val r = ref(bookId)
        val existing = read(r) ?: return
        PluginSessionStore.writeMeta(
            r.dir,
            existing.copy(cacheLevel = cacheLevel.coerceAtLeast(0), cleanup = cleanup, chapters = emptyList()),
        )
    }

    fun setNotify(bookId: String, on: Boolean) {
        val r = ref(bookId)
        val existing = read(r) ?: return
        PluginSessionStore.writeMeta(r.dir, existing.copy(notify = on, chapters = emptyList()))
    }

    /** Stored story session (ToC, policy, notify flag); null when never opened or followed. */
    fun session(bookId: String): PluginReadSession? = runCatching { read(ref(bookId)) }.getOrNull()

    private fun updatePinnedRanges(bookId: String, ranges: List<IntRange>) {
        val r = ref(bookId)
        val existing = read(r) ?: return
        PluginSessionStore.writeMeta(
            r.dir,
            existing.copy(
                pinnedRanges = PluginSessionStore.clipPinnedRanges(ranges, existing.toc.size),
                chapters = emptyList(),
            ),
        )
    }

    /**
     * Ensure the locus, the cache level ahead of it and any pins are on disk, then delete what
     * cleanup allows (more than the cache level behind, unpinned).
     */
    suspend fun maintainChapterCache(
        bookId: String,
        locusChapter: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val r = ref(bookId)
        val session = read(r) ?: throw PluginException(PluginErrorCode.Error, "Story session missing")
        if (session.toc.isEmpty()) return 0
        val policy = session.cachePolicy
        val fetch = PluginSessionStore.fetchIndices(locusChapter, session.toc.size, policy)
            .filterNot { session.toc[it].locked && !PluginSessionStore.hasChapter(r.dir, it) }
        val total = fetch.size.coerceAtLeast(1)
        var downloaded = fetch.count { PluginSessionStore.hasChapter(r.dir, it) }
        onProgress(downloaded, total)
        for (i in fetch.sorted()) {
            if (PluginSessionStore.hasChapter(r.dir, i)) continue
            chapterText(r, session, i) ?: continue
            if (i > session.loadedThrough) {
                session.loadedThrough = i
                // Re-read so a cache policy saved during a long download isn't overwritten.
                read(r)?.let { PluginSessionStore.writeMeta(r.dir, it.copy(loadedThrough = i, chapters = emptyList())) }
            }
            downloaded++
            onProgress(downloaded, total)
        }
        prune(r, session, locusChapter)
        return PluginSessionStore.cachedChapterCount(r.dir, session.toc.size)
    }

    /** Delete what cleanup allows around [locusChapter] without downloading anything. */
    fun pruneChapterCache(bookId: String, locusChapter: Int) {
        val r = ref(bookId)
        val session = read(r) ?: return
        prune(r, session, locusChapter)
    }

    private fun prune(r: Ref, session: PluginReadSession, locusChapter: Int) {
        val doomed = PluginSessionStore.pruneIndices(
            PluginSessionStore.chapterFileIndices(r.dir),
            locusChapter,
            session.toc.size,
            session.cachePolicy,
        )
        PluginSessionStore.deleteChapters(r.dir, doomed)
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

    fun deleteLocalSession(bookId: String) {
        val r = ref(bookId)
        PluginSessionStore.deleteSession(r.root, r.workId)
    }

    /** Open the stream at [startIndex] (loading the work first if needed). */
    suspend fun startReading(bookId: String, startIndex: Int): PluginReadSession {
        val r = ref(bookId)
        val existing = read(r)?.takeIf { it.toc.isNotEmpty() } ?: fetchAndStoreWork(r.plugin.id, r.workId)
        return seekToChapter(bookId, startIndex.coerceIn(0, existing.toc.lastIndex))
    }

    /** Story metadata for windowed reading (work fetched once if missing); no chapters loaded. */
    suspend fun openStory(bookId: String): PluginReadSession {
        val r = ref(bookId)
        return read(r)?.takeIf { it.toc.isNotEmpty() } ?: fetchAndStoreWork(r.plugin.id, r.workId)
    }

    /** Chapter [index] (absolute ToC index): cached text, else fetched and cached. */
    suspend fun chapter(session: PluginReadSession, index: Int): Chapter {
        val r = ref(session.bookId)
        val (title, text) = chapterText(r, session, index)
            ?: throw PluginException(PluginErrorCode.Error, "Chapter ${index + 1} missing")
        return Chapter(title, chapterBlocks(title, text, r.plugin.manifest.bookIdPrefix, index))
    }

    /** Re-open the stream at an absolute ToC index (reader ToC jumps, splash Read). */
    suspend fun seekToChapter(bookId: String, absoluteIndex: Int): PluginReadSession {
        val r = ref(bookId)
        val existing = read(r) ?: throw PluginException(PluginErrorCode.Error, "Story session missing")
        if (existing.toc.isEmpty()) throw PluginException(PluginErrorCode.Parse, "Story ToC missing")
        val start = absoluteIndex.coerceIn(0, existing.toc.lastIndex)
        val session = existing.copy(startIndex = start, loadedThrough = start - 1, chapters = emptyList())
        PluginSessionStore.writeMeta(r.dir, session)
        val loaded = loadThrough(r, session, start)
        scheduleMaintain(bookId, start)
        return loaded
    }

    private suspend fun loadThrough(r: Ref, session: PluginReadSession, through: Int): PluginReadSession {
        val loaded = session.chapters.toMutableList()
        val prefix = r.plugin.manifest.bookIdPrefix
        val from = session.startIndex + loaded.size
        for (i in from..through) {
            val (title, text) = chapterText(r, session, i) ?: break
            loaded += Chapter(title, chapterBlocks(title, text, prefix, i))
            session.loadedThrough = i
            session.chapters = loaded.toList()
            PluginSessionStore.writeMeta(r.dir, session.copy(chapters = emptyList()))
        }
        return session
    }

    /** Download a cover once; null if blank or the book folder already has one. */
    suspend fun downloadCover(coverUrl: String, destBookPath: String): ByteArray? {
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
                    .build()
                coverHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.bytes()
                }
            }.getOrNull()
        }
    }

    companion object {
        /**
         * Chapter as the reader shows it: a heading with [title] first, as EPUB chapter files
         * open with their own heading, then the paragraphs of [text].
         */
        fun chapterBlocks(title: String, text: String, prefix: String, chapterIndex: Int): List<Block> {
            val body = blocksFromPlain(text, prefix, chapterIndex)
            val heading = title.trim()
            if (heading.isEmpty()) return body
            val first = body.firstOrNull()
            if (first != null && first.text.trim().equals(heading, ignoreCase = true)) {
                return listOf(first.copy(kind = BlockKind.Heading)) + body.drop(1)
            }
            return listOf(Block("$prefix-$chapterIndex-title", BlockKind.Heading, heading)) + body
        }

        fun blocksFromPlain(text: String, prefix: String, chapterIndex: Int): List<Block> {
            val paras = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (paras.isEmpty()) {
                return listOf(Block("$prefix-$chapterIndex-0", BlockKind.Paragraph, text.trim()))
            }
            return paras.mapIndexed { i, p -> Block("$prefix-$chapterIndex-$i", BlockKind.Paragraph, p) }
        }

        fun blocksFor(chapter: PluginChapter, prefix: String, chapterIndex: Int): List<Block> {
            if (chapter.html.isNotBlank()) {
                val paras = HtmlParagraphs.of(Jsoup.parseBodyFragment(chapter.html).body())
                // Chapter index in the id: block ids key the reader list across chapters.
                if (paras.isNotEmpty()) {
                    return paras.mapIndexed { i, p -> Block("$prefix-$chapterIndex-$i", BlockKind.Paragraph, p) }
                }
            }
            return blocksFromPlain(chapter.text, prefix, chapterIndex)
        }

        fun titleAndText(
            chapter: PluginChapter,
            fallbackTitle: String,
            prefix: String,
            chapterIndex: Int,
        ): Pair<String, String> {
            val text = blocksFor(chapter, prefix, chapterIndex).joinToString("\n\n") { it.text }.trim()
            if (text.isBlank()) {
                throw PluginException(PluginErrorCode.Parse, "Chapter page had no readable text")
            }
            return chapter.title.ifBlank { fallbackTitle.ifBlank { "Chapter ${chapterIndex + 1}" } } to text
        }
    }
}
