package com.personal.flowreader.library.plugin.royalroad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.library.plugin.SourceWorkDetail
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoyalRoadStorySplash(
    val bookId: String,
    val fictionId: String,
    val fictionUrl: String,
    val title: String,
    val author: String,
    val synopsis: String,
    val tags: List<String>,
    val views: Long?,
    val ratingLabel: String,
    val status: String,
    val coverUrl: String,
    val toc: List<ChapterLink>,
    val chapterCount: Int,
    val downloadedCount: Int,
    /** Absolute ToC indices with bodies on disk. */
    val cachedIndices: Set<Int>,
    val readingProgress: Float,
    val chapterIndex: Int,
    val prefetchAhead: Int = RoyalRoadCachePolicy.DEFAULT_AHEAD,
    val keepBehind: Int = RoyalRoadCachePolicy.DEFAULT_BEHIND,
    /** Personalized lists this story is currently tagged with. */
    val listedIn: Set<RoyalRoadListKind> = emptySet(),
)

enum class RoyalRoadDownloadPane {
    Menu,
    Partial,
}

data class RoyalRoadUi(
    val books: List<ProgressEntity> = emptyList(),
    val libraryMeta: Map<String, RoyalRoadLibraryMeta> = emptyMap(),
    /** Which personalized list is shown in the plugin tab. */
    val libraryList: RoyalRoadListKind = RoyalRoadListKind.Follow,
    /** Book ids currently in [libraryList] (after membership migrate). */
    val listBookIds: Set<String> = emptySet(),
    /** Remote site search query (add overlay only). */
    val remoteQuery: String = "",
    val urlDraft: String = "",
    val searchResults: List<SourceWork> = emptyList(),
    val fiction: SourceWorkDetail? = null,
    val story: RoyalRoadStorySplash? = null,
    val showDownload: Boolean = false,
    val downloadPane: RoyalRoadDownloadPane = RoyalRoadDownloadPane.Menu,
    /** 1-based chapter number text; may be blank while editing. */
    val partialStartDraft: String = "1",
    val partialStartIndex: Int = 0,
    val partialCountDraft: String = "",
    /** Chapters held both ahead of and behind the reading position. */
    val cacheLevelDraft: String = RoyalRoadCachePolicy.DEFAULT_AHEAD.toString(),
    val downloadProgress: Pair<Int, Int>? = null,
    val loggedIn: Boolean = false,
    val loginEmail: String = "",
    val showLogin: Boolean = false,
    val showAdd: Boolean = false,
    val showAccount: Boolean = false,
    val showSyncChoice: Boolean = false,
    val emailDraft: String = "",
    val passwordDraft: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    /** Local books in the selected list. */
    val visibleBooks: List<ProgressEntity>
        get() {
            if (listBookIds.isEmpty()) return emptyList()
            return books.filter { it.bookId in listBookIds }
        }
}

class RoyalRoadViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    private val repo = flow.royalRoad
    private val _ui = MutableStateFlow(
        RoyalRoadUi(
            loggedIn = repo.isLoggedIn(),
            loginEmail = repo.email(),
            emailDraft = repo.email(),
        ),
    )
    val ui: StateFlow<RoyalRoadUi> = _ui

    init {
        refreshLocal()
    }

    fun refreshLocal() {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) {
                flow.catalog.listPlugin(RoyalRoadPlugin.ID)
            }
            val listIds = withContext(Dispatchers.IO) {
                repo.migrateUnlistedMembership(books.map { it.bookId })
                repo.membershipBookIds(_ui.value.libraryList)
            }
            val meta = withContext(Dispatchers.IO) {
                repo.libraryMetas(books.map { it.bookId })
            }
            _ui.value = _ui.value.copy(books = books, libraryMeta = meta, listBookIds = listIds)
        }
    }

    fun setLibraryList(kind: RoyalRoadListKind) {
        val ids = repo.membershipBookIds(kind)
        _ui.value = _ui.value.copy(libraryList = kind, listBookIds = ids, error = null)
    }

    fun setRemoteQuery(value: String) {
        _ui.value = _ui.value.copy(remoteQuery = value)
    }

    fun setUrlDraft(value: String) {
        _ui.value = _ui.value.copy(urlDraft = value)
    }

    fun setEmailDraft(value: String) {
        _ui.value = _ui.value.copy(emailDraft = value)
    }

    fun setPasswordDraft(value: String) {
        _ui.value = _ui.value.copy(passwordDraft = value)
    }

    fun setShowLogin(show: Boolean) {
        _ui.value = _ui.value.copy(
            showLogin = show,
            showAccount = if (show) false else _ui.value.showAccount,
            error = if (show) null else _ui.value.error,
        )
    }

    fun setShowAdd(show: Boolean) {
        _ui.value = _ui.value.copy(
            showAdd = show,
            fiction = if (show) null else _ui.value.fiction,
            searchResults = if (show) _ui.value.searchResults else emptyList(),
            remoteQuery = if (show) _ui.value.remoteQuery else "",
            urlDraft = if (show) _ui.value.urlDraft else "",
            error = if (show) null else _ui.value.error,
        )
    }

    fun setShowAccount(show: Boolean) {
        _ui.value = _ui.value.copy(
            showAccount = show,
            error = if (show) null else _ui.value.error,
        )
    }

    fun setShowSyncChoice(show: Boolean) {
        _ui.value = _ui.value.copy(showSyncChoice = show)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun searchRemote() {
        val q = _ui.value.remoteQuery.trim()
        if (q.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Enter a title to search")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val works = withContext(Dispatchers.IO) { repo.search(q) }
                _ui.value = _ui.value.copy(
                    searchResults = works,
                    busy = false,
                    fiction = null,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Search failed",
                )
            }
        }
    }

    fun openUrl() {
        val raw = _ui.value.urlDraft.trim()
        if (raw.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Paste a Royal Road fiction or chapter URL")
            return
        }
        openFictionUrl(raw)
    }

    fun openSearchResult(work: SourceWork) {
        openFictionUrl(work.url)
    }

    fun follow(work: SourceWork) {
        bookmarkWork(work, RoyalRoadListKind.Follow, preferDetail = _ui.value.fiction)
    }

    fun followCurrent() {
        val detail = _ui.value.fiction ?: return
        bookmarkWork(
            SourceWork(
                title = detail.title,
                url = detail.url,
                author = detail.author,
                coverUrl = detail.coverUrl,
            ),
            RoyalRoadListKind.Follow,
            preferDetail = detail,
        )
    }

    /** Toggle the open splash story on/off a personalized list. */
    fun bookmarkStory(kind: RoyalRoadListKind) {
        val story = _ui.value.story ?: return
        if (kind in story.listedIn) {
            unbookmarkStory(kind)
        } else {
            bookmarkWork(
                SourceWork(
                    title = story.title,
                    url = story.fictionUrl,
                    author = story.author,
                    coverUrl = story.coverUrl,
                ),
                kind,
                preferDetail = null,
            )
        }
    }

    private fun unbookmarkStory(kind: RoyalRoadListKind) {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    repo.forgetListed(story.bookId, kind)
                }
                val splash = withContext(Dispatchers.IO) {
                    runCatching { loadOrRefreshSplash(story.bookId, network = false) }.getOrNull()
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = splash ?: story.copy(listedIn = story.listedIn - kind),
                    message = "Removed ${story.title} from ${kind.label}",
                )
                // Stay on current tab; refresh its membership ids.
                setLibraryList(_ui.value.libraryList)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not update list",
                )
            }
        }
    }

    private fun bookmarkWork(
        work: SourceWork,
        kind: RoyalRoadListKind,
        preferDetail: SourceWorkDetail?,
    ) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val remote = withContext(Dispatchers.IO) {
                    bookmarkAndPersistToc(work, kind, preferDetail = preferDetail)
                }
                val fictionId = RoyalRoadHtml.fictionId(work.url)
                val splash = if (fictionId != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            loadOrRefreshSplash(RoyalRoadHtml.bookIdFor(fictionId), network = false)
                        }.getOrNull()
                    }
                } else {
                    null
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    showAdd = false,
                    fiction = null,
                    story = splash ?: _ui.value.story,
                    message = if (remote) {
                        "Added ${work.title} to ${kind.label}"
                    } else {
                        "Saved ${work.title} to ${kind.label}"
                    },
                )
                setLibraryList(kind)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not add story",
                )
            }
        }
    }

    fun closeFiction() {
        _ui.value = _ui.value.copy(fiction = null)
    }

    fun openStory(bookId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, showAdd = false)
            try {
                val splash = withContext(Dispatchers.IO) { loadOrRefreshSplash(bookId) }
                val pinStart = withContext(Dispatchers.IO) {
                    repo.readSplashBundle(bookId)?.first?.pinnedRanges?.minOfOrNull { it.first }
                }
                // Prefer reading progress; else the start of an existing pin (download start).
                val start = when {
                    splash.chapterIndex > 0 -> splash.chapterIndex
                    pinStart != null -> pinStart
                    else -> 0
                }.coerceIn(0, (splash.chapterCount - 1).coerceAtLeast(0))
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = splash,
                    partialStartDraft = (start + 1).toString(),
                    partialStartIndex = start,
                    cacheLevelDraft = maxOf(splash.prefetchAhead, splash.keepBehind).toString(),
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open story",
                )
            }
        }
    }

    fun closeStory() {
        _ui.value = _ui.value.copy(
            story = null,
            downloadProgress = null,
            showDownload = false,
            downloadPane = RoyalRoadDownloadPane.Menu,
        )
    }

    fun openDownloadOptions() {
        val story = _ui.value.story ?: return
        // Keep the start chapter already chosen on this splash; only seed cache level.
        _ui.value = _ui.value.copy(
            showDownload = true,
            downloadPane = RoyalRoadDownloadPane.Menu,
            partialCountDraft = "",
            cacheLevelDraft = maxOf(story.prefetchAhead, story.keepBehind).toString(),
            error = null,
        )
    }

    fun closeDownloadOptions() {
        resolvePartialStartDraft()
        _ui.value = _ui.value.copy(
            showDownload = false,
            downloadPane = RoyalRoadDownloadPane.Menu,
            downloadProgress = null,
        )
    }

    fun setDownloadPane(pane: RoyalRoadDownloadPane) {
        if (pane == RoyalRoadDownloadPane.Partial) {
            resolvePartialStartDraft()
        }
        _ui.value = _ui.value.copy(downloadPane = pane, error = null)
    }

    fun setPartialStartDraft(value: String) {
        val digits = value.filter { it.isDigit() }.take(5)
        val story = _ui.value.story
        val index = digits.toIntOrNull()?.let { n ->
            if (story == null || story.chapterCount <= 0) 0
            else (n - 1).coerceIn(0, story.chapterCount - 1)
        }
        _ui.value = _ui.value.copy(
            partialStartDraft = digits,
            // Keep last valid index for download until draft is resolved; blank keeps prior.
            partialStartIndex = index ?: _ui.value.partialStartIndex,
        )
    }

    /** Empty / invalid start draft becomes chapter 1. */
    fun resolvePartialStartDraft() {
        val story = _ui.value.story ?: return
        val last = (story.chapterCount - 1).coerceAtLeast(0)
        val parsed = _ui.value.partialStartDraft.toIntOrNull()
        val index = when {
            parsed == null || parsed < 1 -> 0
            else -> (parsed - 1).coerceIn(0, last)
        }
        _ui.value = _ui.value.copy(
            partialStartDraft = (index + 1).toString(),
            partialStartIndex = index,
        )
    }

    fun setPartialCountDraft(value: String) {
        _ui.value = _ui.value.copy(partialCountDraft = value.filter { it.isDigit() }.take(5))
    }

    fun setCacheLevelDraft(value: String) {
        _ui.value = _ui.value.copy(cacheLevelDraft = value.filter { it.isDigit() }.take(4))
        persistCacheLevelIfComplete()
    }

    /** Persist when the field parses; skip while blank mid-edit. Sets ahead and behind equal. */
    private fun persistCacheLevelIfComplete() {
        val story = _ui.value.story ?: return
        val level = _ui.value.cacheLevelDraft.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repo.updateCacheWindow(story.bookId, level, level)
                    repo.maintainChapterCache(story.bookId, story.chapterIndex)
                }
                val refreshed = withContext(Dispatchers.IO) {
                    loadOrRefreshSplash(story.bookId, network = false)
                }
                refreshLocal()
                _ui.value = _ui.value.copy(story = refreshed)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(error = t.message ?: "Could not update cache level")
            }
        }
    }

    fun downloadAllChapters() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, downloadProgress = 0 to story.chapterCount)
            try {
                val count = withContext(Dispatchers.IO) {
                    repo.downloadAllChapters(
                        bookId = story.bookId,
                        fictionUrl = story.fictionUrl,
                        locusChapter = story.chapterIndex,
                    ) { done, total ->
                        _ui.value = _ui.value.copy(downloadProgress = done to total)
                    }
                }
                val refreshed = withContext(Dispatchers.IO) { loadOrRefreshSplash(story.bookId, network = false) }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = refreshed,
                    downloadProgress = null,
                    showDownload = false,
                    downloadPane = RoyalRoadDownloadPane.Menu,
                    message = "Downloaded $count / ${refreshed.chapterCount} chapters",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    downloadProgress = null,
                    error = t.message ?: "Download failed",
                )
            }
        }
    }

    fun downloadPartialChapters(countCapOverride: Int? = null) {
        resolvePartialStartDraft()
        val story = _ui.value.story ?: return
        val cap = countCapOverride
            ?: _ui.value.partialCountDraft.toIntOrNull()?.takeIf { it > 0 }
        val startIndex = _ui.value.partialStartIndex
            .coerceIn(0, (story.chapterCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            // Move locus to the download start first so the strip and cache window follow it.
            _ui.value = _ui.value.copy(
                busy = true,
                error = null,
                downloadProgress = 0 to 1,
                story = story.copy(chapterIndex = startIndex),
                partialStartIndex = startIndex,
                partialStartDraft = (startIndex + 1).toString(),
            )
            try {
                val count = withContext(Dispatchers.IO) {
                    val row = flow.db.progress().get(story.bookId)
                    if (row != null) {
                        flow.db.progress().upsert(
                            row.copy(
                                chapterIndex = startIndex,
                                blockIndex = 0,
                                charOffset = 0,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    repo.downloadPartialChapters(
                        bookId = story.bookId,
                        startIndex = startIndex,
                        countCap = cap,
                        fictionUrl = story.fictionUrl,
                    ) { done, total ->
                        _ui.value = _ui.value.copy(downloadProgress = done to total)
                    }
                }
                val refreshed = withContext(Dispatchers.IO) { loadOrRefreshSplash(story.bookId, network = false) }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = refreshed,
                    partialStartIndex = startIndex,
                    partialStartDraft = (startIndex + 1).toString(),
                    downloadProgress = null,
                    showDownload = false,
                    downloadPane = RoyalRoadDownloadPane.Menu,
                    message = "Cached $count / ${refreshed.chapterCount} chapters",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    downloadProgress = null,
                    error = t.message ?: "Partial download failed",
                )
            }
        }
    }

    /**
     * From cache settings: pin/download from [partialStartDraft] for [cacheLevelDraft] chapters.
     * Blank/zero level is rejected so we never treat it as “through end”.
     */
    fun beginPartialDownloadFromSettings() {
        resolvePartialStartDraft()
        val level = _ui.value.cacheLevelDraft.toIntOrNull()?.takeIf { it > 0 }
        if (level == null) {
            _ui.value = _ui.value.copy(
                error = "Set Cache level to how many chapters to download.",
            )
            return
        }
        downloadPartialChapters(countCapOverride = level)
    }

    /** @deprecated Use [openDownloadOptions] — kept name for older call sites during transition. */
    fun downloadStoryChapters() {
        openDownloadOptions()
    }

    /** Tap library card: open reader at saved progress (no info splash). */
    fun readBook(actions: LibraryPluginActions, bookId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, story = null)
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    val row = flow.db.progress().get(bookId)
                    var bundle = repo.readSplashBundle(bookId)
                    if (bundle == null || bundle.first.toc.isEmpty()) {
                        val url = bundle?.first?.fictionUrl?.takeIf { it.isNotBlank() }
                            ?: row?.sourceUri?.takeIf { it.isNotBlank() }
                            ?: throw IllegalArgumentException("No fiction URL for this story")
                        repo.ensureSessionToc(repo.loadFictionPage(url))
                        bundle = repo.readSplashBundle(bookId)
                    }
                    val session = bundle?.first
                        ?: throw IllegalArgumentException("Story ToC missing")
                    val splash = bundle.second ?: RoyalRoadSplashMeta()
                    val detail = SourceWorkDetail(
                        title = session.title,
                        url = session.fictionUrl,
                        author = session.author,
                        synopsis = splash.synopsis,
                        chapters = session.toc.map {
                            com.personal.flowreader.library.plugin.SourceChapterRef(it.title, it.url)
                        },
                        fictionId = session.fictionId,
                        tags = splash.tags,
                        views = splash.views,
                        ratingLabel = splash.ratingLabel,
                        status = splash.status,
                        coverUrl = splash.coverUrl,
                    )
                    val start = (row?.chapterIndex ?: 0).coerceIn(0, session.toc.lastIndex.coerceAtLeast(0))
                    val started = repo.startReading(detail, start)
                    val text = started.chapters.joinToString("\n\n") { ch ->
                        buildString {
                            if (ch.title.isNotBlank()) {
                                append(ch.title)
                                append("\n\n")
                            }
                            append(ch.blocks.joinToString("\n\n") { it.text })
                        }
                    }
                    flow.catalog.upsertPluginBook(
                        bookId = started.bookId,
                        title = started.title,
                        sourceUri = started.fictionUrl,
                        sourceKind = RoyalRoadPlugin.ID,
                        text = text.ifBlank { started.title },
                    )
                }
                refreshLocal()
                _ui.value = _ui.value.copy(busy = false)
                actions.setBusy(false)
                actions.openBook(bookId)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open fiction",
                )
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open fiction")
            }
        }
    }

    fun readStory(actions: LibraryPluginActions, startIndex: Int? = null) {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    val detail = SourceWorkDetail(
                        title = story.title,
                        url = story.fictionUrl,
                        author = story.author,
                        synopsis = story.synopsis,
                        chapters = story.toc.map {
                            com.personal.flowreader.library.plugin.SourceChapterRef(it.title, it.url)
                        },
                        fictionId = story.fictionId,
                        tags = story.tags,
                        views = story.views,
                        ratingLabel = story.ratingLabel,
                        status = story.status,
                        coverUrl = story.coverUrl,
                    )
                    val index = startIndex
                        ?: story.chapterIndex.coerceIn(0, (story.chapterCount - 1).coerceAtLeast(0))
                    val started = repo.startReading(detail, index)
                    val text = started.chapters.joinToString("\n\n") { ch ->
                        buildString {
                            if (ch.title.isNotBlank()) {
                                append(ch.title)
                                append("\n\n")
                            }
                            append(ch.blocks.joinToString("\n\n") { it.text })
                        }
                    }
                    flow.catalog.upsertPluginBook(
                        bookId = started.bookId,
                        title = started.title,
                        sourceUri = started.fictionUrl,
                        sourceKind = RoyalRoadPlugin.ID,
                        text = text.ifBlank { started.title },
                    )
                }
                refreshLocal()
                _ui.value = _ui.value.copy(busy = false, story = null)
                actions.setBusy(false)
                actions.openBook(story.bookId)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open fiction",
                )
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open fiction")
            }
        }
    }

    fun refreshStoryToc() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) { repo.refreshToc(story.bookId) }
                val refreshed = withContext(Dispatchers.IO) { loadOrRefreshSplash(story.bookId, network = false) }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = refreshed,
                    message = "Updated chapter list (${refreshed.chapterCount})",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not refresh chapter list",
                )
            }
        }
    }

    fun deleteStory() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    flow.catalog.removePluginMembership(story.bookId)
                    repo.removeMembership(story.bookId)
                    repo.deleteLocalSession(story.bookId)
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    story = null,
                    message = "Removed ${story.title}",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not remove story",
                )
            }
        }
    }

    private fun openFictionUrl(url: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val splash = withContext(Dispatchers.IO) {
                    val page = repo.loadFictionPage(url)
                    repo.ensureSessionToc(page)
                    upsertWork(
                        SourceWork(
                            title = page.title,
                            url = page.url,
                            author = page.author,
                            coverUrl = page.coverUrl,
                        ),
                    )
                    loadOrRefreshSplash(
                        RoyalRoadHtml.bookIdFor(page.fictionId.ifBlank {
                            RoyalRoadHtml.fictionId(page.url) ?: throw IllegalArgumentException(
                                "Could not determine fiction id",
                            )
                        }),
                        network = false,
                    )
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    showAdd = false,
                    fiction = null,
                    story = splash,
                    urlDraft = url,
                    partialStartDraft = (splash.chapterIndex + 1).toString(),
                    partialStartIndex = splash.chapterIndex,
                    cacheLevelDraft = maxOf(splash.prefetchAhead, splash.keepBehind).toString(),
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not load fiction",
                )
            }
        }
    }

    fun openChapter(actions: LibraryPluginActions, startIndex: Int) {
        val detail = _ui.value.fiction ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            actions.setBusy(true)
            try {
                val session = withContext(Dispatchers.IO) {
                    // Persist ToC before streaming the chosen chapter.
                    val page = FictionPage(
                        title = detail.title,
                        url = detail.url,
                        author = detail.author,
                        synopsis = detail.synopsis,
                        fictionId = detail.fictionId,
                        chapters = detail.chapters.map { ChapterLink(it.title, it.url) },
                        tags = detail.tags,
                        views = detail.views,
                        ratingLabel = detail.ratingLabel,
                        status = detail.status,
                        coverUrl = detail.coverUrl,
                    )
                    repo.ensureSessionToc(page)
                    upsertWork(
                        SourceWork(
                            title = detail.title,
                            url = detail.url,
                            author = detail.author,
                            coverUrl = detail.coverUrl,
                        ),
                    )
                    val started = repo.startReading(detail, startIndex)
                    val text = started.chapters.joinToString("\n\n") { ch ->
                        buildString {
                            if (ch.title.isNotBlank()) {
                                append(ch.title)
                                append("\n\n")
                            }
                            append(ch.blocks.joinToString("\n\n") { it.text })
                        }
                    }
                    flow.catalog.upsertPluginBook(
                        bookId = started.bookId,
                        title = started.title,
                        sourceUri = started.fictionUrl,
                        sourceKind = RoyalRoadPlugin.ID,
                        text = text.ifBlank { started.title },
                    ).also { row ->
                        if (startIndex == 0 && row.chapterIndex == 0) {
                            flow.db.progress().upsert(
                                row.copy(
                                    chapterIndex = 0,
                                    blockIndex = 0,
                                    charOffset = 0,
                                    readingProgress = 0f,
                                ),
                            )
                        }
                    }
                    started
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    showAdd = false,
                    fiction = null,
                )
                actions.setBusy(false)
                actions.openBook(session.bookId)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open fiction",
                )
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open fiction")
            }
        }
    }

    fun login() {
        val email = _ui.value.emailDraft.trim()
        val password = _ui.value.passwordDraft
        if (email.isEmpty() || password.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Email and password are required")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) { repo.login(email, password) }
                _ui.value = _ui.value.copy(
                    busy = false,
                    loggedIn = true,
                    loginEmail = email,
                    passwordDraft = "",
                    showLogin = false,
                    showAccount = true,
                    showSyncChoice = true,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Sign in failed",
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.logout() }
            _ui.value = _ui.value.copy(
                loggedIn = false,
                loginEmail = "",
                emailDraft = "",
                passwordDraft = "",
                showSyncChoice = false,
                showAccount = false,
            )
        }
    }

    fun syncFollows(mode: FollowsSyncMode) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, showSyncChoice = false)
            try {
                withContext(Dispatchers.IO) {
                    val remote = repo.fetchAllFollows()
                    applyFollows(remote, mode)
                }
                refreshLocal()
                _ui.value = _ui.value.copy(
                    busy = false,
                    message = when (mode) {
                        FollowsSyncMode.Merge -> "Merged follows into library"
                        FollowsSyncMode.Overwrite -> "Replaced library with follows"
                    },
                    showAccount = false,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not sync follows",
                )
            }
        }
    }

    private suspend fun applyFollows(remote: List<FictionListItem>, mode: FollowsSyncMode) {
        val remoteIds = remote.mapNotNull { item ->
            RoyalRoadHtml.fictionId(item.url)?.let { RoyalRoadHtml.bookIdFor(it) }
        }.toSet()
        if (mode == FollowsSyncMode.Overwrite) {
            val local = flow.catalog.listPlugin(RoyalRoadPlugin.ID)
            local.filter { it.bookId !in remoteIds }.forEach { row ->
                flow.catalog.removePluginMembership(row.bookId)
                repo.removeMembership(row.bookId)
                repo.deleteLocalSession(row.bookId)
            }
        }
        for (item in remote) {
            followAndPersistToc(
                SourceWork(
                    title = item.title,
                    url = item.url,
                    author = item.author,
                    latestChapter = item.latestChapter,
                    coverUrl = item.coverUrl,
                ),
                preferDetail = null,
            )
        }
    }

    private suspend fun bookmarkAndPersistToc(
        work: SourceWork,
        kind: RoyalRoadListKind,
        preferDetail: SourceWorkDetail?,
    ): Boolean {
        upsertWork(work)
        val fictionUrl = RoyalRoadHtml.fictionUrlFrom(work.url) ?: work.url
        val page = if (
            preferDetail != null &&
            RoyalRoadHtml.fictionId(preferDetail.url) == RoyalRoadHtml.fictionId(fictionUrl) &&
            preferDetail.chapters.isNotEmpty()
        ) {
            FictionPage(
                title = preferDetail.title,
                url = preferDetail.url,
                author = preferDetail.author,
                synopsis = preferDetail.synopsis,
                fictionId = preferDetail.fictionId,
                chapters = preferDetail.chapters.map { ChapterLink(it.title, it.url) },
                tags = preferDetail.tags,
                views = preferDetail.views,
                ratingLabel = preferDetail.ratingLabel,
                status = preferDetail.status,
                coverUrl = preferDetail.coverUrl.ifBlank { work.coverUrl },
            )
        } else {
            repo.loadFictionPage(fictionUrl)
        }
        repo.ensureSessionToc(page)
        if (page.coverUrl.isNotBlank() && work.coverUrl.isBlank()) {
            upsertWork(work.copy(coverUrl = page.coverUrl, title = page.title, author = page.author))
        }
        val remote = repo.setBookmarkOnSite(fictionUrl, kind)
        repo.rememberListed(
            FictionListItem(
                title = page.title.ifBlank { work.title },
                url = fictionUrl,
                author = page.author.ifBlank { work.author },
                latestChapter = work.latestChapter,
                coverUrl = page.coverUrl.ifBlank { work.coverUrl },
            ),
            kind,
        )
        return remote
    }

    private suspend fun followAndPersistToc(
        work: SourceWork,
        preferDetail: SourceWorkDetail?,
    ): Boolean = bookmarkAndPersistToc(work, RoyalRoadListKind.Follow, preferDetail)


    private suspend fun loadOrRefreshSplash(
        bookId: String,
        network: Boolean = true,
    ): RoyalRoadStorySplash {
        val row = flow.db.progress().get(bookId)
        var bundle = repo.readSplashBundle(bookId)
        if (network && (bundle == null || bundle.first.toc.isEmpty() || bundle.second == null)) {
            val url = bundle?.first?.fictionUrl?.takeIf { it.isNotBlank() }
                ?: row?.sourceUri?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("No fiction URL for this story")
            val page = repo.loadFictionPage(url)
            repo.ensureSessionToc(page)
            if (row == null) {
                upsertWork(
                    SourceWork(
                        title = page.title,
                        url = page.url,
                        author = page.author,
                        coverUrl = page.coverUrl,
                    ),
                )
            }
            bundle = repo.readSplashBundle(bookId)
        }
        val session = bundle?.first
            ?: throw IllegalArgumentException("Story ToC missing")
        val splash = bundle.second ?: RoyalRoadSplashMeta()
        val progress = flow.db.progress().get(bookId)
        val dir = RoyalRoadSessionStore.dir(repo.sessionRoot(), session.fictionId)
        return RoyalRoadStorySplash(
            bookId = session.bookId,
            fictionId = session.fictionId,
            fictionUrl = session.fictionUrl,
            title = session.title,
            author = session.author,
            synopsis = splash.synopsis,
            tags = splash.tags,
            views = splash.views,
            ratingLabel = splash.ratingLabel,
            status = splash.status,
            coverUrl = splash.coverUrl,
            toc = session.toc,
            chapterCount = session.toc.size,
            downloadedCount = RoyalRoadSessionStore.cachedChapterCount(dir, session.toc.size),
            cachedIndices = RoyalRoadSessionStore.cachedChapterIndices(dir, session.toc.size),
            readingProgress = progress?.readingProgress ?: 0f,
            chapterIndex = (progress?.chapterIndex ?: 0).coerceIn(0, session.toc.lastIndex.coerceAtLeast(0)),
            prefetchAhead = session.prefetchAhead,
            keepBehind = session.keepBehind,
            listedIn = repo.membershipKinds(bookId),
        )
    }

    private suspend fun upsertWork(work: SourceWork) {
        val fictionId = RoyalRoadHtml.fictionId(work.url)
            ?: throw IllegalArgumentException("Not a Royal Road fiction URL")
        val bookId = RoyalRoadHtml.bookIdFor(fictionId)
        val fictionUrl = RoyalRoadHtml.fictionUrlFrom(work.url)
            ?: work.url
        val existing = flow.db.progress().get(bookId)
        var coverBytes: ByteArray? = null
        if (work.coverUrl.isNotBlank()) {
            val path = existing?.storedPath
                ?: File(
                    File(
                        flow.booksDir,
                        bookId.replace(Regex("[^A-Za-z0-9._-]"), "_"),
                    ).apply { mkdirs() },
                    "book.txt",
                ).absolutePath
            coverBytes = repo.downloadCover(work.coverUrl, path)
        }
        flow.catalog.upsertPluginCatalogEntry(
            bookId = bookId,
            title = work.title,
            sourceUri = fictionUrl,
            sourceKind = RoyalRoadPlugin.ID,
            coverBytes = coverBytes,
        )
    }
}
