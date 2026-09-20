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
    private val minIntervalMs: Long = 500L,
    filesDir: File = File(context.applicationContext.filesDir, "plugins/royalroad"),
    secrets: RoyalRoadSecrets = RoyalRoadSecrets(context.applicationContext),
    http: OkHttpClient? = null,
) : SourceRepository {
    private val root = filesDir
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

    fun email(): String = secrets.email()

    fun isLoggedIn(): Boolean = secrets.isLoggedIn()

    override suspend fun search(query: String): List<SourceWork> {
        val html = get(RoyalRoadHtml.searchUrl(query))
        return RoyalRoadHtml.parseFictionList(html, RoyalRoadHtml.searchUrl(query)).map { it.toWork() }
    }

    override suspend fun browse(page: Int, order: String?): List<SourceWork> {
        val url = RoyalRoadHtml.browseUrl(order.orEmpty(), page)
        val html = get(url)
        return RoyalRoadHtml.parseFictionList(html, url).map { it.toWork() }
    }

    override suspend fun follows(): List<SourceWork> {
        val url = RoyalRoadHtml.followsUrl()
        val html = get(url)
        val items = RoyalRoadHtml.parseFictionList(html, url)
        if (items.isEmpty() && RoyalRoadHtml.looksLikeLoginPage(html)) {
            throw IllegalArgumentException("Sign in to see follows")
        }
        return items.map { it.toWork() }
    }

    override suspend fun loadWork(url: String): SourceWorkDetail {
        val trimmed = url.trim()
        val fictionUrl = if (RoyalRoadHtml.isChapterUrl(trimmed)) {
            RoyalRoadHtml.fictionUrlFrom(trimmed) ?: trimmed
        } else {
            trimmed
        }
        val html = get(fictionUrl)
        val page = RoyalRoadHtml.parseFictionPage(html, fictionUrl)
        return page.toDetail()
    }

    override suspend fun loadChapter(url: String): SourceChapter {
        val html = get(url)
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
            val fictionHtml = get(trimmed)
            RoyalRoadHtml.firstChapterUrl(fictionHtml, trimmed)
                ?: throw IllegalArgumentException("Could not find a chapter list on this page")
        }
        return loadChapter(chapterUrl)
    }

    override suspend fun login(username: String, password: String) {
        val loginUrl = RoyalRoadHtml.loginUrl()
        val html = get(loginUrl, allowRelogin = false)
        val token = RoyalRoadHtml.loginToken(html)
            ?: throw IllegalArgumentException("Could not read Royal Road login form")
        acquireRateLimit()
        val body = FormBody.Builder()
            .add("Email", username.trim())
            .add("Password", password)
            .add("Remember", "true")
            .add("ReturnUrl", "/")
            .add("__RequestVerificationToken", token)
            .build()
        val request = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", loginUrl)
            .post(body)
            .build()
        val result = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val location = response.request.url.toString()
                Triple(response.code, location, text)
            }
        }
        val page = result.third
        val location = result.second
        val ok = !location.contains("/account/login", ignoreCase = true) ||
            RoyalRoadHtml.isLoggedIn(page)
        if (!ok || RoyalRoadHtml.looksLikeLoginPage(page)) {
            throw IllegalArgumentException("Sign in failed. Check email and password.")
        }
        secrets.saveCredentials(username.trim(), password)
        secrets.setLoggedIn(true)
    }

    override suspend fun logout() {
        jar.clear()
        secrets.clearAll()
        runCatching { get("${RoyalRoadHtml.ORIGIN}/account/logout", allowRelogin = false) }
    }

    override suspend fun syncProgress(workId: String, chapterUrl: String) {
        if (chapterUrl.isBlank()) return
        runCatching { get(chapterUrl) }
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
        )
        RoyalRoadSessionStore.writeMeta(dir, session)
        return loadThrough(session, start)
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
        val target = session.loadedThrough.coerceAtLeast(session.startIndex)
        return loadThrough(session, target)
    }

    suspend fun appendNext(session: RoyalRoadReadSession): RoyalRoadReadSession? {
        val next = session.nextIndex() ?: return null
        return loadThrough(session, next)
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
            RoyalRoadSessionStore.writeMeta(dir, session)
        }
        return session
    }

    private suspend fun get(url: String, allowRelogin: Boolean = true): String {
        acquireRateLimit()
        val html = executeGet(url)
        if (allowRelogin &&
            !url.contains("/account/login", ignoreCase = true) &&
            RoyalRoadHtml.looksLikeLoginPage(html) &&
            secrets.hasCredentials()
        ) {
            login(secrets.email(), secrets.password())
            acquireRateLimit()
            return executeGet(url)
        }
        return html
    }

    private suspend fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", RoyalRoadHtml.ORIGIN)
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("Royal Road returned HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        }
    }

    private suspend fun acquireRateLimit() {
        rate.withLock {
            val wait = lastFetchAt + minIntervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastFetchAt = System.currentTimeMillis()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Flow Reader) AppleWebKit/537.36 " +
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
)

private fun FictionPage.toDetail() = SourceWorkDetail(
    title = title,
    url = url,
    author = author,
    synopsis = synopsis,
    chapters = chapters.map { SourceChapterRef(it.title, it.url) },
    fictionId = fictionId,
)
