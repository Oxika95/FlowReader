package com.personal.flowreader.library.plugin.royalroad

import android.content.Context
import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.library.plugin.SourceChapter
import com.personal.flowreader.library.plugin.SourceChapterRef
import com.personal.flowreader.library.plugin.SourceRepository
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.library.plugin.SourceWorkDetail
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class RoyalRoadRepository(
    context: Context,
    private val minIntervalMs: Long = 700L,
    filesDir: File = File(context.applicationContext.filesDir, "plugins/royalroad"),
    secrets: RoyalRoadSecrets = RoyalRoadSecrets(context.applicationContext),
    http: OkHttpClient? = null,
) : SourceRepository {
    private val root = filesDir.apply { mkdirs() }
    private val secrets = secrets
    private val jar = PersistentCookieJar(this.secrets)
    private val http: OkHttpClient = http ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(jar)
        .build()
    private val rate = Mutex()
    private var lastFetchAt = 0L
    /** Last in-site URL used as Referer for the next request. */
    private var lastPageUrl: String = RoyalRoadHtml.ORIGIN

    fun email(): String = secrets.email()

    fun isLoggedIn(): Boolean = secrets.isLoggedIn()

    fun sessionRoot(): File = root

    override suspend fun search(query: String): List<SourceWork> {
        val url = RoyalRoadHtml.searchUrl(query)
        val html = get(url, referer = RoyalRoadHtml.ORIGIN)
        return RoyalRoadHtml.parseFictionList(html, url).map { it.toWork() }
    }

    override suspend fun browse(page: Int, order: String?): List<SourceWork> {
        val url = RoyalRoadHtml.browseUrl(order.orEmpty(), page)
        val html = get(url, referer = lastPageUrl)
        return RoyalRoadHtml.parseFictionList(html, url).map { it.toWork() }
    }

    override suspend fun follows(): List<SourceWork> = fetchAllFollows().map { it.toWork() }

    /** Paginated follows fetch. Sequential; caches membership on success. */
    suspend fun fetchAllFollows(): List<FictionListItem> {
        val all = ArrayList<FictionListItem>()
        var page = 1
        var referer = RoyalRoadHtml.ORIGIN
        while (page <= MAX_FOLLOWS_PAGES) {
            val url = RoyalRoadHtml.followsUrl(page)
            val html = get(url, referer = referer)
            if (RoyalRoadHtml.looksLikeLoginPage(html)) {
                throw IllegalArgumentException("Sign in to sync follows")
            }
            val items = RoyalRoadHtml.parseFictionList(html, url)
            if (items.isEmpty()) break
            all += items
            referer = url
            page++
        }
        RoyalRoadMembershipStore.write(root, all, RoyalRoadListKind.Follow)
        return all
    }

    fun cachedFollows(): List<FictionListItem> =
        RoyalRoadMembershipStore.read(root, RoyalRoadListKind.Follow)

    fun membershipBookIds(kind: RoyalRoadListKind): Set<String> =
        RoyalRoadMembershipStore.bookIds(root, kind)

    fun membershipKinds(bookId: String): Set<RoyalRoadListKind> =
        RoyalRoadListKind.entries.filter { bookId in membershipBookIds(it) }.toSet()

    fun rememberListed(item: FictionListItem, kind: RoyalRoadListKind) {
        RoyalRoadMembershipStore.upsert(root, item, kind)
    }

    fun forgetListed(bookId: String, kind: RoyalRoadListKind) {
        RoyalRoadMembershipStore.remove(root, bookId, kind)
    }

    fun rememberFollowed(item: FictionListItem) {
        rememberListed(item, RoyalRoadListKind.Follow)
    }

    fun migrateUnlistedMembership(catalogBookIds: Collection<String>) {
        RoyalRoadMembershipStore.migrateUnlistedToFollow(root, catalogBookIds)
    }

    fun removeMembership(bookId: String) {
        RoyalRoadMembershipStore.removeFromAll(root, bookId)
    }

    override suspend fun loadWork(url: String): SourceWorkDetail = loadFictionPage(url).toDetail()

    suspend fun loadFictionPage(url: String): FictionPage {
        val trimmed = url.trim()
        val fictionUrl = if (RoyalRoadHtml.isChapterUrl(trimmed)) {
            RoyalRoadHtml.fictionUrlFrom(trimmed) ?: trimmed
        } else {
            trimmed
        }
        val html = get(fictionUrl, referer = lastPageUrl)
        return RoyalRoadHtml.parseFictionPage(html, fictionUrl)
    }

    /**
     * Persist full ToC + splash metadata without downloading chapter bodies.
     * Preserves [RoyalRoadReadSession.loadedThrough] and cached chapter files.
     */
    suspend fun ensureSessionToc(page: FictionPage): RoyalRoadReadSession {
        val fictionId = page.fictionId.ifBlank {
            RoyalRoadHtml.fictionId(page.url)
        } ?: throw IllegalArgumentException("Could not determine fiction id")
        if (page.chapters.isEmpty()) {
            throw IllegalArgumentException("This fiction has no chapters")
        }
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        val existing = RoyalRoadSessionStore.read(root, fictionId)
        val session = RoyalRoadReadSession(
            bookId = RoyalRoadHtml.bookIdFor(fictionId),
            fictionId = fictionId,
            fictionUrl = page.url,
            title = page.title,
            author = page.author,
            toc = page.chapters,
            startIndex = existing?.startIndex ?: 0,
            loadedThrough = existing?.loadedThrough ?: -1,
            chapters = emptyList(),
            prefetchAhead = existing?.prefetchAhead ?: RoyalRoadCachePolicy.DEFAULT_AHEAD,
            keepBehind = existing?.keepBehind ?: RoyalRoadCachePolicy.DEFAULT_BEHIND,
            pinnedRanges = RoyalRoadSessionStore.clipPinnedRanges(
                existing?.pinnedRanges.orEmpty(),
                page.chapters.size,
            ),
        )
        RoyalRoadSessionStore.writeMeta(dir, session)
        RoyalRoadSessionStore.writeSplash(
            dir,
            RoyalRoadSplashMeta(
                synopsis = page.synopsis,
                tags = page.tags,
                views = page.views,
                ratingLabel = page.ratingLabel,
                status = page.status,
                coverUrl = page.coverUrl,
            ),
        )
        // Evict chapter bodies whose URL at this index changed (inserts/reorders).
        reconcileChapterCache(dir, existing?.toc.orEmpty(), page.chapters)
        return session
    }

    private fun reconcileChapterCache(
        dir: File,
        oldToc: List<ChapterLink>,
        newToc: List<ChapterLink>,
    ) {
        val oldUrls = oldToc.map { it.url }
        for (i in newToc.indices) {
            val oldUrl = oldUrls.getOrNull(i)
            if (oldUrl != null && oldUrl != newToc[i].url) {
                RoyalRoadSessionStore.deleteChapter(dir, i)
            }
        }
        for (i in newToc.size until oldUrls.size) {
            RoyalRoadSessionStore.deleteChapter(dir, i)
        }
    }

    fun libraryMeta(bookId: String): RoyalRoadLibraryMeta? {
        val fictionId = bookId.removePrefix("rr:")
        return RoyalRoadSessionStore.libraryMeta(root, fictionId)
    }

    fun libraryMetas(bookIds: Collection<String>): Map<String, RoyalRoadLibraryMeta> =
        bookIds.mapNotNull { id -> libraryMeta(id)?.let { id to it } }.toMap()

    fun readSplashBundle(bookId: String): Pair<RoyalRoadReadSession, RoyalRoadSplashMeta?>? {
        val fictionId = bookId.removePrefix("rr:")
        val session = RoyalRoadSessionStore.read(root, fictionId) ?: return null
        val splash = RoyalRoadSessionStore.readSplash(RoyalRoadSessionStore.dir(root, fictionId))
        return session to splash
    }

    /** Download every missing chapter body for [bookId]. Pins the full ToC, then maintains. */
    suspend fun downloadAllChapters(
        bookId: String,
        fictionUrl: String? = null,
        locusChapter: Int = 0,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val session = ensureSessionForDownload(bookId, fictionUrl)
        val last = session.toc.lastIndex
        if (last < 0) return 0
        updatePinnedRanges(bookId, listOf(0..last))
        return maintainChapterCache(bookId, locusChapter.coerceIn(0, last), onProgress)
    }

    /**
     * Pin and download from [startIndex] through end of ToC, or [startIndex] + [countCap] - 1
     * when [countCap] is non-null and positive. Cache window locus is [startIndex].
     */
    suspend fun downloadPartialChapters(
        bookId: String,
        startIndex: Int,
        countCap: Int? = null,
        fictionUrl: String? = null,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val session = ensureSessionForDownload(bookId, fictionUrl)
        if (session.toc.isEmpty()) return 0
        val start = startIndex.coerceIn(0, session.toc.lastIndex)
        val end = if (countCap != null && countCap > 0) {
            (start + countCap - 1).coerceAtMost(session.toc.lastIndex)
        } else {
            session.toc.lastIndex
        }
        if (end < start) return RoyalRoadSessionStore.cachedChapterCount(
            RoyalRoadSessionStore.dir(root, session.fictionId),
            session.toc.size,
        )
        // Replace pins with this range so older partials (e.g. from chapter 1) do not linger.
        updatePinnedRanges(bookId, listOf(start..end))
        // Cache window is centered on the download start (locus), not prior reading progress.
        return maintainChapterCache(
            bookId,
            start,
            onProgress,
        )
    }

    fun updateCacheWindow(bookId: String, prefetchAhead: Int, keepBehind: Int) {
        val fictionId = bookId.removePrefix("rr:")
        val existing = RoyalRoadSessionStore.read(root, fictionId) ?: return
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        RoyalRoadSessionStore.writeMeta(
            dir,
            existing.copy(
                prefetchAhead = prefetchAhead.coerceAtLeast(0),
                keepBehind = keepBehind.coerceAtLeast(0),
                chapters = emptyList(),
            ),
        )
    }

    private fun updatePinnedRanges(bookId: String, ranges: List<IntRange>) {
        val fictionId = bookId.removePrefix("rr:")
        val existing = RoyalRoadSessionStore.read(root, fictionId) ?: return
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        val clipped = RoyalRoadSessionStore.clipPinnedRanges(ranges, existing.toc.size)
        RoyalRoadSessionStore.writeMeta(
            dir,
            existing.copy(pinnedRanges = clipped, chapters = emptyList()),
        )
    }

    /**
     * Ensure every chapter in the stream window ∪ pins is on disk; prune the rest.
     * Returns the final cached chapter count.
     */
    suspend fun maintainChapterCache(
        bookId: String,
        locusChapter: Int,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val fictionId = bookId.removePrefix("rr:")
        val session = RoyalRoadSessionStore.read(root, fictionId)
            ?: throw IllegalArgumentException("Story session missing")
        if (session.toc.isEmpty()) return 0
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        val desired = RoyalRoadSessionStore.desiredChapterIndices(
            locus = locusChapter,
            tocSize = session.toc.size,
            policy = session.cachePolicy,
        )
        val total = desired.size
        var downloaded = desired.count { RoyalRoadSessionStore.hasChapter(dir, it) }
        onProgress(downloaded, total.coerceAtLeast(1))
        for (i in desired.sorted()) {
            if (RoyalRoadSessionStore.hasChapter(dir, i)) continue
            val ref = session.toc.getOrNull(i) ?: continue
            val fetched = loadChapter(ref.url)
            RoyalRoadSessionStore.writeChapter(dir, i, fetched.title, fetched.text)
            if (i > session.loadedThrough) {
                session.loadedThrough = i
                RoyalRoadSessionStore.writeMeta(dir, session.copy(chapters = emptyList()))
            }
            downloaded++
            onProgress(downloaded, total.coerceAtLeast(1))
        }
        RoyalRoadSessionStore.pruneChaptersOutside(dir, session.toc.size, desired)
        return RoyalRoadSessionStore.cachedChapterCount(dir, session.toc.size)
    }

    fun cachedChapterIndices(bookId: String): Set<Int> {
        val fictionId = bookId.removePrefix("rr:")
        val session = RoyalRoadSessionStore.read(root, fictionId) ?: return emptySet()
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        return RoyalRoadSessionStore.cachedChapterIndices(dir, session.toc.size)
    }

    private suspend fun ensureSessionForDownload(
        bookId: String,
        fictionUrl: String?,
    ): RoyalRoadReadSession {
        val fictionId = bookId.removePrefix("rr:")
        var session = RoyalRoadSessionStore.read(root, fictionId)
        if (session == null || session.toc.isEmpty()) {
            val url = fictionUrl?.takeIf { it.isNotBlank() }
                ?: session?.fictionUrl?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Story session missing")
            session = ensureSessionToc(loadFictionPage(url))
        }
        return session
    }

    suspend fun refreshToc(bookId: String): RoyalRoadReadSession {
        val fictionId = bookId.removePrefix("rr:")
        val existing = RoyalRoadSessionStore.read(root, fictionId)
        val url = existing?.fictionUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("No fiction URL for refresh")
        return ensureSessionToc(loadFictionPage(url))
    }

    fun deleteLocalSession(bookId: String) {
        RoyalRoadSessionStore.deleteSession(root, bookId.removePrefix("rr:"))
    }

    override suspend fun loadChapter(url: String): SourceChapter {
        val fiction = RoyalRoadHtml.fictionUrlFrom(url) ?: lastPageUrl
        val html = get(url, referer = fiction)
        val inner = RoyalRoadHtml.chapterInnerHtml(html)
            ?: throw IllegalArgumentException(
                "Could not find chapter text. Royal Road markup may have changed.",
            )
        val blocks = RoyalRoadHtml.blocksFromChapterHtml(inner)
        val text = RoyalRoadHtml.blocksToPlainText(blocks)
        if (text.isBlank()) {
            throw IllegalArgumentException("Chapter page had no readable text")
        }
        val title = RoyalRoadHtml.chapterTitle(html) ?: "Royal Road chapter"
        return SourceChapter(title = title, url = url, html = inner, text = text)
    }

    suspend fun fetchOneChapter(url: String): SourceChapter {
        val trimmed = url.trim()
        if (!RoyalRoadHtml.isRoyalRoadUrl(trimmed)) {
            throw IllegalArgumentException("Not a Royal Road URL")
        }
        val chapterUrl = if (RoyalRoadHtml.isChapterUrl(trimmed)) {
            trimmed
        } else {
            val fictionHtml = get(trimmed, referer = lastPageUrl)
            RoyalRoadHtml.firstChapterUrl(fictionHtml, trimmed)
                ?: throw IllegalArgumentException("Could not find a chapter list on this page")
        }
        return loadChapter(chapterUrl)
    }

    /**
     * Download a cover image once. Returns null if [coverUrl] is blank or the
     * destination already has a cover (skip). Single-flight with other GETs.
     */
    suspend fun downloadCover(coverUrl: String, destBookPath: String): ByteArray? {
        if (coverUrl.isBlank()) return null
        val dir = File(destBookPath).parentFile ?: return null
        val existing = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
            .map { File(dir, it) }
            .any { it.exists() && it.length() > 0L }
        if (existing) return null
        return getBytes(coverUrl, referer = lastPageUrl)
    }

    /**
     * Bookmark a fiction on royalroad.com when signed in (`follow`, `favorite`, or `readlater`).
     * No-op (returns false) if not logged in. Throws if logged in but the form fails.
     */
    suspend fun setBookmarkOnSite(fictionUrl: String, kind: RoyalRoadListKind): Boolean {
        if (!isLoggedIn()) return false
        val html = get(fictionUrl, referer = lastPageUrl)
        if (RoyalRoadHtml.looksLikeLoginPage(html)) {
            throw IllegalArgumentException("Sign in to update Royal Road lists")
        }
        val form = RoyalRoadHtml.bookmarkForm(html, fictionUrl, kind.bookmarkType)
            ?: return false // Already bookmarked / form absent — do not claim success
        return withRateLimit {
            val body = FormBody.Builder()
                .add("type", form.type)
                .add("__RequestVerificationToken", form.token)
                .build()
            val request = browserRequest(form.actionUrl, referer = fictionUrl)
                .post(body)
                .build()
            val code = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    response.body?.string()
                    response.code
                }
            }
            lastPageUrl = fictionUrl
            if (code !in 200..399) {
                throw IllegalArgumentException("Could not update Royal Road list (HTTP $code)")
            }
            true
        }
    }

    suspend fun followOnSite(fictionUrl: String): Boolean =
        setBookmarkOnSite(fictionUrl, RoyalRoadListKind.Follow)

    override suspend fun login(username: String, password: String) {
        secrets.requireAvailable()
        val loginUrl = RoyalRoadHtml.loginUrl()
        val html = get(loginUrl, referer = RoyalRoadHtml.ORIGIN, allowAuthCheck = false)
        val token = RoyalRoadHtml.loginToken(html)
            ?: throw IllegalArgumentException("Could not read Royal Road login form")
        val page = withRateLimit {
            val body = FormBody.Builder()
                .add("Email", username.trim())
                .add("Password", password)
                .add("Remember", "true")
                .add("ReturnUrl", "/")
                .add("__RequestVerificationToken", token)
                .build()
            val request = browserRequest(loginUrl, referer = loginUrl)
                .post(body)
                .build()
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val location = response.request.url.toString()
                    lastPageUrl = location
                    Triple(response.code, location, text)
                }
            }
        }
        val location = page.second
        val body = page.third
        val ok = !location.contains("/account/login", ignoreCase = true) ||
            RoyalRoadHtml.isLoggedIn(body)
        if (!ok || RoyalRoadHtml.looksLikeLoginPage(body)) {
            throw IllegalArgumentException("Sign in failed. Check email and password.")
        }
        // Cookies only — never persist the password.
        secrets.saveEmail(username.trim())
        secrets.setLoggedIn(true)
    }

    override suspend fun logout() {
        jar.clear()
        secrets.clearAll()
        runCatching {
            get("${RoyalRoadHtml.ORIGIN}/account/logout", referer = lastPageUrl, allowAuthCheck = false)
        }
    }

    override suspend fun syncProgress(workId: String, chapterUrl: String) {
        if (chapterUrl.isBlank()) return
        val fiction = RoyalRoadHtml.fictionUrlFrom(chapterUrl) ?: lastPageUrl
        runCatching { get(chapterUrl, referer = fiction) }
    }

    suspend fun startReading(detail: SourceWorkDetail, startIndex: Int): RoyalRoadReadSession {
        val fictionId = detail.fictionId.ifBlank {
            RoyalRoadHtml.fictionId(detail.url)
        } ?: throw IllegalArgumentException("Could not determine fiction id")
        if (detail.chapters.isEmpty()) {
            throw IllegalArgumentException("This fiction has no chapters")
        }
        val start = startIndex.coerceIn(0, detail.chapters.lastIndex)
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        val existing = RoyalRoadSessionStore.read(root, fictionId)
        val session = RoyalRoadReadSession(
            bookId = RoyalRoadHtml.bookIdFor(fictionId),
            fictionId = fictionId,
            fictionUrl = detail.url,
            title = detail.title,
            author = detail.author,
            toc = detail.chapters.map { ChapterLink(it.title, it.url) },
            startIndex = start,
            loadedThrough = start - 1,
            chapters = emptyList(),
            prefetchAhead = existing?.prefetchAhead ?: RoyalRoadCachePolicy.DEFAULT_AHEAD,
            keepBehind = existing?.keepBehind ?: RoyalRoadCachePolicy.DEFAULT_BEHIND,
            pinnedRanges = RoyalRoadSessionStore.clipPinnedRanges(
                existing?.pinnedRanges.orEmpty(),
                detail.chapters.size,
            ),
        )
        RoyalRoadSessionStore.writeMeta(dir, session)
        val loaded = loadThrough(session, start)
        maintainChapterCache(loaded.bookId, start)
        return loaded
    }

    suspend fun resumeRead(bookId: String): RoyalRoadReadSession {
        val fictionId = bookId.removePrefix("rr:")
        val existing = RoyalRoadSessionStore.read(root, fictionId)
            ?: throw IllegalArgumentException("Royal Road session missing")
        var session = existing
        if (session.toc.isEmpty()) {
            val detail = loadWork(session.fictionUrl)
            session = session.copy(toc = detail.chapters.map { ChapterLink(it.title, it.url) })
        }
        // Only hydrate the current reading chapter into memory — never 0..loadedThrough.
        val target = session.startIndex.coerceIn(0, (session.toc.size - 1).coerceAtLeast(0))
        session = session.copy(chapters = emptyList(), loadedThrough = target - 1)
        val loaded = loadThrough(session, target)
        runCatching { maintainChapterCache(loaded.bookId, target) }
        return loaded
    }

    /**
     * Re-open the stream at an absolute ToC index (for reader ToC jumps).
     * Loads that chapter into memory and maintains the disk cache window around it.
     */
    suspend fun seekToChapter(bookId: String, absoluteIndex: Int): RoyalRoadReadSession {
        val fictionId = bookId.removePrefix("rr:")
        val existing = RoyalRoadSessionStore.read(root, fictionId)
            ?: throw IllegalArgumentException("Royal Road session missing")
        if (existing.toc.isEmpty()) {
            throw IllegalArgumentException("Story ToC missing")
        }
        val start = absoluteIndex.coerceIn(0, existing.toc.lastIndex)
        val session = existing.copy(
            startIndex = start,
            loadedThrough = start - 1,
            chapters = emptyList(),
        )
        session.chapters = emptyList()
        session.loadedThrough = start - 1
        val dir = RoyalRoadSessionStore.dir(root, fictionId)
        RoyalRoadSessionStore.writeMeta(dir, session)
        val loaded = loadThrough(session, start)
        maintainChapterCache(loaded.bookId, start)
        return loaded
    }

    suspend fun appendNext(session: RoyalRoadReadSession): RoyalRoadReadSession? {
        val next = session.nextIndex() ?: return null
        val loaded = loadThrough(session, next)
        // Prefetch one ahead of the newly loaded chapter when the window allows.
        val locus = next
        runCatching { maintainChapterCache(loaded.bookId, locus) }
        return loaded
    }

    private suspend fun loadThrough(session: RoyalRoadReadSession, through: Int): RoyalRoadReadSession {
        val dir = RoyalRoadSessionStore.dir(root, session.fictionId)
        val loaded = session.chapters.toMutableList()
        val from = session.startIndex + loaded.size
        for (i in from..through) {
            val ref = session.toc.getOrNull(i) ?: break
            val cached = RoyalRoadSessionStore.readChapterText(dir, i)
            val chapter = if (cached != null) {
                Chapter(cached.first, blocksFromPlain(cached.second, i))
            } else {
                val fetched = loadChapter(ref.url)
                RoyalRoadSessionStore.writeChapter(dir, i, fetched.title, fetched.text)
                runCatching { syncProgress(session.bookId, ref.url) }
                Chapter(fetched.title, RoyalRoadHtml.blocksFromChapterHtml(fetched.html).ifEmpty {
                    blocksFromPlain(fetched.text, i)
                })
            }
            loaded += chapter
            session.loadedThrough = i
            session.chapters = loaded.toList()
            RoyalRoadSessionStore.writeMeta(
                dir,
                session.copy(chapters = emptyList()),
            )
        }
        return session
    }

    private suspend fun get(
        url: String,
        referer: String,
        allowAuthCheck: Boolean = true,
    ): String {
        val html = withRateLimit { executeGet(url, referer) }
        lastPageUrl = url
        if (allowAuthCheck &&
            !url.contains("/account/login", ignoreCase = true) &&
            RoyalRoadHtml.looksLikeLoginPage(html)
        ) {
            secrets.setLoggedIn(false)
            throw RoyalRoadAuthExpired()
        }
        return html
    }

    private suspend fun getBytes(url: String, referer: String): ByteArray? {
        return withRateLimit {
            val request = browserRequest(url, referer)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .get()
                .build()
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                }
            }
        }
    }

    private suspend fun executeGet(url: String, referer: String): String {
        val request = browserRequest(url, referer).get().build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("Royal Road returned HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        }
    }

    private fun browserRequest(url: String, referer: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Referer", referer.ifBlank { RoyalRoadHtml.ORIGIN })

    /** Hold the mutex across the whole request so concurrent callers cannot burst. */
    private suspend fun <T> withRateLimit(block: suspend () -> T): T = rate.withLock {
        val wait = lastFetchAt + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastFetchAt = System.currentTimeMillis()
        }
    }

    companion object {
        private const val MAX_FOLLOWS_PAGES = 50
        /** Generic Chrome-on-Android UA; no app name. */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun blocksFromPlain(text: String, chapterIndex: Int): List<Block> {
            val paras = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (paras.isEmpty()) {
                return listOf(Block("rr-$chapterIndex-0", BlockKind.Paragraph, text.trim()))
            }
            return paras.mapIndexed { i, p -> Block("rr-$chapterIndex-$i", BlockKind.Paragraph, p) }
        }
    }
}

private fun FictionListItem.toWork() = SourceWork(
    title = title,
    url = url,
    author = author,
    latestChapter = latestChapter,
    coverUrl = coverUrl,
)

private fun FictionPage.toDetail() = SourceWorkDetail(
    title = title,
    url = url,
    author = author,
    synopsis = synopsis,
    chapters = chapters.map { SourceChapterRef(it.title, it.url) },
    fictionId = fictionId,
    tags = tags,
    views = views,
    ratingLabel = ratingLabel,
    status = status,
    coverUrl = coverUrl,
)
