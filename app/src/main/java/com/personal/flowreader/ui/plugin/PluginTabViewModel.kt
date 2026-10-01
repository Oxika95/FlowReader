package com.personal.flowreader.ui.plugin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.plugin.PluginSource
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginSession
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.plugin.store.PluginCachePolicy
import com.personal.flowreader.plugin.store.PluginLibraryMeta
import com.personal.flowreader.plugin.store.PluginMembershipStore
import com.personal.flowreader.plugin.store.PluginReadSession
import com.personal.flowreader.plugin.store.PluginSessionStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Splash data for [StoryMediaCard]; built entirely from the app-owned cache. */
data class PluginStory(
    val bookId: String,
    val workId: String,
    val workUrl: String,
    val title: String,
    val author: String,
    val synopsis: String,
    val tags: List<String>,
    val views: Long?,
    val rating: String,
    val status: String,
    val cover: String,
    val toc: List<PluginChapterRef>,
    val downloadedCount: Int,
    /** Absolute ToC indices with bodies on disk. */
    val cachedIndices: Set<Int>,
    val readingProgress: Float,
    val chapterIndex: Int,
    val prefetchAhead: Int = PluginCachePolicy.DEFAULT_AHEAD,
    val keepBehind: Int = PluginCachePolicy.DEFAULT_BEHIND,
    /** Plugin list ids this story is tagged with. */
    val listedIn: Set<String> = emptySet(),
    /** apiVersion 2 media card slots; null for v1 plugins. */
    val card: PluginCard? = null,
) {
    val chapterCount: Int get() = toc.size
}

enum class PluginDownloadPane { Menu, Partial }

enum class SyncMode { Merge, Overwrite }

/** Which sub-tab of the plugin tab is showing. */
sealed interface PluginSection {
    data class Library(val listId: String) : PluginSection
    data object Search : PluginSection
}

data class PluginTabUi(
    val manifest: PluginManifest,
    val books: List<ProgressEntity> = emptyList(),
    val libraryMeta: Map<String, PluginLibraryMeta> = emptyMap(),
    val section: PluginSection = manifest.lists.firstOrNull()?.let { PluginSection.Library(it.id) }
        ?: PluginSection.Search,
    /** Book ids in the selected list. */
    val listBookIds: Set<String> = emptySet(),
    /** Row text from the list file (used before a ToC is cached). */
    val listRows: Map<String, PluginWork> = emptyMap(),
    val query: String = "",
    val urlDraft: String = "",
    val searchResults: List<PluginWork> = emptyList(),
    val searchPage: Int = 0,
    val searchHasMore: Boolean = false,
    val story: PluginStory? = null,
    val showDownload: Boolean = false,
    val downloadPane: PluginDownloadPane = PluginDownloadPane.Menu,
    /** 1-based chapter number text; may be blank while editing. */
    val partialStartDraft: String = "1",
    val partialStartIndex: Int = 0,
    val partialCountDraft: String = "",
    /** Chapters held both ahead of and behind the reading position. */
    val cacheLevelDraft: String = PluginCachePolicy.DEFAULT_AHEAD.toString(),
    val downloadProgress: Pair<Int, Int>? = null,
    val session: PluginSession = PluginSession(),
    val loginFields: Map<String, String> = emptyMap(),
    val showLogin: Boolean = false,
    val showAdd: Boolean = false,
    val showAccount: Boolean = false,
    val showSettings: Boolean = false,
    /** List id awaiting a Merge / Overwrite choice. */
    val syncListId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val visibleBooks: List<ProgressEntity>
        get() = if (listBookIds.isEmpty()) emptyList() else books.filter { it.bookId in listBookIds }

    val anyOverlay: Boolean
        get() = showLogin || showAdd || showAccount || showSettings || syncListId != null || story != null
}

/**
 * State and actions for one plugin's library tab. Plugins only supply data; every screen is
 * rendered by the app from this state (see docs/plugins/ui-contract.md).
 */
class PluginTabViewModel(app: Application, val pluginId: String) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    private val manager = flow.pluginManager
    private val books = flow.pluginBooks
    private val manifest: PluginManifest =
        manager.get(pluginId)?.manifest ?: throw IllegalStateException("Plugin $pluginId is not installed")
    private val dataDir: File get() = manager.dataDir(pluginId)
    private val listIds: List<String> get() = manifest.lists.map { it.id }

    private val _ui = MutableStateFlow(PluginTabUi(manifest = manifest))
    val ui: StateFlow<PluginTabUi> = _ui

    private fun source(): PluginSource = manager.source(pluginId)

    init {
        refreshLocal()
        refreshSession()
    }

    fun refreshLocal() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { flow.catalog.listPlugin(pluginId) }
            val (ids, listRows) = withContext(Dispatchers.IO) {
                migrateUnlisted(rows)
                membership(_ui.value.section)
            }
            val meta = withContext(Dispatchers.IO) { books.libraryMetas(rows.map { it.bookId }) }
            _ui.update { it.copy(books = rows, libraryMeta = meta, listBookIds = ids, listRows = listRows) }
        }
    }

    private fun refreshSession() {
        if (!manifest.has(PluginCapability.Auth)) return
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { runCatching { source().session() }.getOrNull() }
                ?: return@launch
            _ui.update { ui ->
                val firstPlain = manifest.auth?.fields?.firstOrNull { !it.secret }?.key
                val prefill = if (firstPlain != null && session.account.isNotBlank() &&
                    ui.loginFields[firstPlain].isNullOrBlank()
                ) {
                    ui.loginFields + (firstPlain to session.account)
                } else {
                    ui.loginFields
                }
                ui.copy(session = session, loginFields = prefill)
            }
        }
    }

    /** Catalog stories not on any list (older installs) land on the first list. */
    private fun migrateUnlisted(rows: List<ProgressEntity>) {
        val first = manifest.lists.firstOrNull() ?: return
        if (rows.isEmpty() || PluginMembershipStore.anyListed(dataDir, listIds)) return
        val works = rows.mapNotNull { row ->
            val workId = manager.resolveBookId(row.bookId)?.second ?: return@mapNotNull null
            PluginWork(id = workId, title = row.title, url = row.sourceUri)
        }
        PluginMembershipStore.write(dataDir, first.id, works)
    }

    private fun membership(section: PluginSection): Pair<Set<String>, Map<String, PluginWork>> {
        val listId = (section as? PluginSection.Library)?.listId ?: return emptySet<String>() to emptyMap()
        val rows = PluginMembershipStore.read(dataDir, listId)
        val byBook = rows.associateBy { manager.bookIdFor(pluginId, it.id) }
        return byBook.keys to byBook
    }

    fun setSection(section: PluginSection) {
        val (ids, rows) = membership(section)
        _ui.update { it.copy(section = section, listBookIds = ids, listRows = rows, error = null) }
    }

    fun setQuery(value: String) = _ui.update { it.copy(query = value) }

    fun setUrlDraft(value: String) = _ui.update { it.copy(urlDraft = value) }

    fun setLoginField(key: String, value: String) =
        _ui.update { it.copy(loginFields = it.loginFields + (key to value)) }

    fun setShowLogin(show: Boolean) = _ui.update {
        it.copy(showLogin = show, showAccount = if (show) false else it.showAccount, error = null)
    }

    fun setShowAdd(show: Boolean) = _ui.update {
        it.copy(showAdd = show, urlDraft = if (show) it.urlDraft else "", error = null)
    }

    fun setShowAccount(show: Boolean) = _ui.update { it.copy(showAccount = show, error = null) }

    fun setShowSettings(show: Boolean) = _ui.update {
        it.copy(showSettings = show, showAccount = if (show) false else it.showAccount)
    }

    fun askSync(listId: String?) = _ui.update { it.copy(syncListId = listId) }

    fun consumeError() = _ui.update { it.copy(error = null) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun fail(t: Throwable, fallback: String) {
        if (t is PluginException && t.code == PluginErrorCode.AuthRequired && manifest.auth != null) {
            _ui.update {
                it.copy(
                    busy = false,
                    downloadProgress = null,
                    session = it.session.copy(loggedIn = false),
                    showLogin = true,
                    showAccount = false,
                    error = t.message ?: "Sign in to ${manifest.name} again.",
                )
            }
        } else {
            _ui.update { it.copy(busy = false, downloadProgress = null, error = t.message ?: fallback) }
        }
    }

    // --- Search -----------------------------------------------------------------------------

    fun search(more: Boolean = false) {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) {
            _ui.update { it.copy(error = "Enter a title to search") }
            return
        }
        val page = if (more) _ui.value.searchPage + 1 else 1
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) { source().search(q, page) }
                _ui.update {
                    it.copy(
                        busy = false,
                        searchResults = if (more) (it.searchResults + result.items).distinctBy { w -> w.id }
                        else result.items,
                        searchPage = page,
                        searchHasMore = result.hasMore,
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Search failed")
            }
        }
    }

    // --- Opening stories --------------------------------------------------------------------

    fun openUrl(raw: String = _ui.value.urlDraft) {
        val url = raw.trim()
        if (url.isEmpty()) {
            _ui.update { it.copy(error = "Paste a ${manifest.name} link") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, urlDraft = url) }
            try {
                val workId = withContext(Dispatchers.IO) { source().resolveUrl(url) }
                    ?: throw PluginException(PluginErrorCode.Unsupported, "${manifest.name} does not recognize that link")
                openWorkInternal(workId)
            } catch (t: Throwable) {
                fail(t, "Could not load story")
            }
        }
    }

    fun openWork(work: PluginWork) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                openWorkInternal(work.id, work)
            } catch (t: Throwable) {
                fail(t, "Could not load story")
            }
        }
    }

    private suspend fun openWorkInternal(workId: String, hint: PluginWork? = null) {
        val bookId = manager.bookIdFor(pluginId, workId)
        val splash = withContext(Dispatchers.IO) {
            val session = books.fetchAndStoreWork(pluginId, workId)
            val cover = PluginSessionStore.readSplash(PluginSessionStore.dir(dataDir, workId))?.cover
            upsertWork(
                PluginWork(
                    id = workId,
                    title = session.title,
                    url = session.workUrl,
                    author = session.author,
                    cover = cover?.ifBlank { hint?.cover.orEmpty() } ?: hint?.cover.orEmpty(),
                ),
            )
            loadStory(bookId)
        }
        refreshLocal()
        showStory(splash, closeAdd = true)
    }

    /** Long-press a library card: open the splash from cache (fetching only if missing). */
    fun openStory(bookId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, showAdd = false) }
            try {
                val splash = withContext(Dispatchers.IO) {
                    if (books.readSplashBundle(bookId)?.first?.toc.isNullOrEmpty()) {
                        books.refreshToc(bookId)
                    }
                    loadStory(bookId)
                }
                showStory(splash, closeAdd = false)
            } catch (t: Throwable) {
                fail(t, "Could not open story")
            }
        }
    }

    private suspend fun showStory(splash: PluginStory, closeAdd: Boolean) {
        val pinStart = withContext(Dispatchers.IO) {
            books.readSplashBundle(splash.bookId)?.first?.pinnedRanges?.minOfOrNull { it.first }
        }
        val start = when {
            splash.chapterIndex > 0 -> splash.chapterIndex
            pinStart != null -> pinStart
            else -> 0
        }.coerceIn(0, (splash.chapterCount - 1).coerceAtLeast(0))
        _ui.update {
            it.copy(
                busy = false,
                showAdd = if (closeAdd) false else it.showAdd,
                story = splash,
                partialStartDraft = (start + 1).toString(),
                partialStartIndex = start,
                cacheLevelDraft = maxOf(splash.prefetchAhead, splash.keepBehind).toString(),
            )
        }
    }

    fun closeStory() = _ui.update {
        it.copy(story = null, downloadProgress = null, showDownload = false, downloadPane = PluginDownloadPane.Menu)
    }

    private suspend fun loadStory(bookId: String): PluginStory {
        val (session, splash) = books.readSplashBundle(bookId)
            ?: throw PluginException(PluginErrorCode.Error, "Story is not cached")
        val progress = flow.db.progress().get(bookId)
        val dir = PluginSessionStore.dir(dataDir, session.workId)
        return PluginStory(
            bookId = bookId,
            workId = session.workId,
            workUrl = session.workUrl,
            title = session.title,
            author = session.author,
            synopsis = splash?.synopsis.orEmpty(),
            tags = splash?.tags.orEmpty(),
            views = splash?.views,
            rating = splash?.rating.orEmpty(),
            status = splash?.status.orEmpty(),
            cover = splash?.cover.orEmpty(),
            toc = session.toc,
            downloadedCount = PluginSessionStore.cachedChapterCount(dir, session.toc.size),
            cachedIndices = PluginSessionStore.cachedChapterIndices(dir, session.toc.size),
            readingProgress = progress?.readingProgress ?: 0f,
            chapterIndex = (progress?.chapterIndex ?: 0).coerceIn(0, session.toc.lastIndex.coerceAtLeast(0)),
            prefetchAhead = session.prefetchAhead,
            keepBehind = session.keepBehind,
            listedIn = PluginMembershipStore.listsContaining(dataDir, listIds, session.workId),
            card = splash?.card,
        )
    }

    /**
     * A plugin-declared media card action (apiVersion 2) was tapped. Applies the returned card
     * patch, shows its toast, and re-fetches the story when the plugin asks to reload.
     */
    fun runCardAction(actionId: String, on: Boolean?) {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) { books.runCardAction(story.bookId, actionId, on) }
                if (result.reload) withContext(Dispatchers.IO) { books.refreshToc(story.bookId) }
                val refreshed = withContext(Dispatchers.IO) { loadStory(story.bookId) }
                _ui.update {
                    it.copy(
                        busy = false,
                        story = if (it.story?.bookId == story.bookId) refreshed else it.story,
                        message = result.toast.ifBlank { it.message },
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Action failed")
            }
        }
    }

    private suspend fun upsertWork(work: PluginWork) {
        val bookId = manager.bookIdFor(pluginId, work.id)
        val existing = flow.db.progress().get(bookId)
        var coverBytes: ByteArray? = null
        if (work.cover.isNotBlank()) {
            val path = existing?.storedPath
                ?: File(
                    File(flow.booksDir, bookId.replace(Regex("[^A-Za-z0-9._-]"), "_")).apply { mkdirs() },
                    "book.txt",
                ).absolutePath
            coverBytes = books.downloadCover(work.cover, path)
        }
        flow.catalog.upsertPluginCatalogEntry(
            bookId = bookId,
            title = work.title,
            sourceUri = work.url,
            sourceKind = pluginId,
            coverBytes = coverBytes,
        )
    }

    // --- Lists ------------------------------------------------------------------------------

    /** Toggle the open story on/off a list (site first when the plugin supports membership). */
    fun toggleList(listId: String) {
        val story = _ui.value.story ?: return
        val list = manifest.list(listId) ?: return
        val on = listId !in story.listedIn
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val remote = withContext(Dispatchers.IO) {
                    val remote = source().setMembership(story.workId, listId, on)
                    if (on) {
                        PluginMembershipStore.upsert(
                            dataDir,
                            listId,
                            PluginWork(
                                id = story.workId,
                                title = story.title,
                                url = story.workUrl,
                                author = story.author,
                                cover = story.cover,
                            ),
                        )
                    } else {
                        PluginMembershipStore.remove(dataDir, listId, story.workId)
                    }
                    remote
                }
                val refreshed = withContext(Dispatchers.IO) { runCatching { loadStory(story.bookId) }.getOrNull() }
                refreshLocal()
                val verb = when {
                    !on -> "Removed ${story.title} from ${list.title}"
                    remote -> "Added ${story.title} to ${list.title}"
                    else -> "Saved ${story.title} to ${list.title}"
                }
                _ui.update { it.copy(busy = false, story = refreshed ?: it.story, message = verb) }
                setSection(_ui.value.section)
            } catch (t: Throwable) {
                fail(t, "Could not update list")
            }
        }
    }

    /** Import a remote list (paged via `list(listId, page)`) into local membership. */
    fun syncList(listId: String, mode: SyncMode) {
        val list = manifest.list(listId) ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, syncListId = null) }
            try {
                val count = withContext(Dispatchers.IO) {
                    val remote = ArrayList<PluginWork>()
                    var page = 1
                    while (page <= MAX_SYNC_PAGES) {
                        val result = source().list(listId, page)
                        remote += result.items
                        if (!result.hasMore || result.items.isEmpty()) break
                        page++
                    }
                    val remoteIds = remote.map { it.id }.toSet()
                    val local = PluginMembershipStore.read(dataDir, listId)
                    if (mode == SyncMode.Overwrite) {
                        local.filter { it.id !in remoteIds }.forEach { dropped ->
                            PluginMembershipStore.remove(dataDir, listId, dropped.id)
                            if (PluginMembershipStore.listsContaining(dataDir, listIds, dropped.id).isEmpty()) {
                                val bookId = manager.bookIdFor(pluginId, dropped.id)
                                flow.catalog.removePluginMembership(bookId)
                                books.deleteLocalSession(bookId)
                            }
                        }
                    }
                    val kept = if (mode == SyncMode.Overwrite) emptyList()
                    else PluginMembershipStore.read(dataDir, listId).filter { it.id !in remoteIds }
                    PluginMembershipStore.write(dataDir, listId, remote + kept)
                    remote.forEach { work -> runCatching { upsertWork(work) } }
                    remote.size
                }
                refreshLocal()
                _ui.update {
                    it.copy(
                        busy = false,
                        showAccount = false,
                        message = when (mode) {
                            SyncMode.Merge -> "Merged $count from ${list.title}"
                            SyncMode.Overwrite -> "Replaced ${list.title} with $count stories"
                        },
                    )
                }
                setSection(PluginSection.Library(listId))
            } catch (t: Throwable) {
                fail(t, "Could not sync ${list.title}")
            }
        }
    }

    // --- Reading ----------------------------------------------------------------------------

    /** Tap a library card: open the reader at saved progress. */
    fun readBook(actions: LibraryPluginActions, bookId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, story = null) }
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    val row = flow.db.progress().get(bookId)
                    startAndSnapshot(bookId, row?.chapterIndex ?: 0)
                }
                refreshLocal()
                _ui.update { it.copy(busy = false) }
                actions.setBusy(false)
                actions.openBook(bookId)
            } catch (t: Throwable) {
                fail(t, "Could not open story")
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open story")
            }
        }
    }

    fun readStory(actions: LibraryPluginActions, startIndex: Int? = null) {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    startAndSnapshot(story.bookId, startIndex ?: story.chapterIndex)
                }
                refreshLocal()
                _ui.update { it.copy(busy = false, story = null) }
                actions.setBusy(false)
                actions.openBook(story.bookId)
            } catch (t: Throwable) {
                fail(t, "Could not open story")
                actions.setBusy(false)
                actions.showError(t.message ?: "Could not open story")
            }
        }
    }

    private suspend fun startAndSnapshot(bookId: String, chapterIndex: Int): PluginReadSession {
        val started = books.startReading(bookId, chapterIndex.coerceAtLeast(0))
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
            sourceUri = started.workUrl,
            sourceKind = pluginId,
            text = text.ifBlank { started.title },
        )
        return started
    }

    fun refreshStoryToc() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    books.refreshToc(story.bookId)
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update {
                    it.copy(busy = false, story = refreshed, message = "Updated chapter list (${refreshed.chapterCount})")
                }
            } catch (t: Throwable) {
                fail(t, "Could not refresh chapter list")
            }
        }
    }

    fun deleteStory() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    flow.catalog.removePluginMembership(story.bookId)
                    PluginMembershipStore.removeFromAll(dataDir, listIds, story.workId)
                    books.deleteLocalSession(story.bookId)
                }
                refreshLocal()
                _ui.update { it.copy(busy = false, story = null, message = "Removed ${story.title}") }
            } catch (t: Throwable) {
                fail(t, "Could not remove story")
            }
        }
    }

    // --- Downloads --------------------------------------------------------------------------

    fun openDownloadOptions() {
        val story = _ui.value.story ?: return
        _ui.update {
            it.copy(
                showDownload = true,
                downloadPane = PluginDownloadPane.Menu,
                partialCountDraft = "",
                cacheLevelDraft = maxOf(story.prefetchAhead, story.keepBehind).toString(),
                error = null,
            )
        }
    }

    fun closeDownloadOptions() {
        resolvePartialStartDraft()
        _ui.update { it.copy(showDownload = false, downloadPane = PluginDownloadPane.Menu, downloadProgress = null) }
    }

    fun setDownloadPane(pane: PluginDownloadPane) {
        if (pane == PluginDownloadPane.Partial) resolvePartialStartDraft()
        _ui.update { it.copy(downloadPane = pane, error = null) }
    }

    fun setPartialStartDraft(value: String) {
        val digits = value.filter { it.isDigit() }.take(5)
        val story = _ui.value.story
        val index = digits.toIntOrNull()?.let { n ->
            if (story == null || story.chapterCount <= 0) 0 else (n - 1).coerceIn(0, story.chapterCount - 1)
        }
        _ui.update { it.copy(partialStartDraft = digits, partialStartIndex = index ?: it.partialStartIndex) }
    }

    /** Empty / invalid start draft becomes chapter 1. */
    fun resolvePartialStartDraft() {
        val story = _ui.value.story ?: return
        val last = (story.chapterCount - 1).coerceAtLeast(0)
        val parsed = _ui.value.partialStartDraft.toIntOrNull()
        val index = if (parsed == null || parsed < 1) 0 else (parsed - 1).coerceIn(0, last)
        _ui.update { it.copy(partialStartDraft = (index + 1).toString(), partialStartIndex = index) }
    }

    fun setPartialCountDraft(value: String) =
        _ui.update { it.copy(partialCountDraft = value.filter { c -> c.isDigit() }.take(5)) }

    fun setCacheLevelDraft(value: String) {
        _ui.update { it.copy(cacheLevelDraft = value.filter { c -> c.isDigit() }.take(4)) }
        val story = _ui.value.story ?: return
        val level = _ui.value.cacheLevelDraft.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    books.updateCacheWindow(story.bookId, level, level)
                    books.maintainChapterCache(story.bookId, story.chapterIndex)
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update { it.copy(story = refreshed) }
            } catch (t: Throwable) {
                fail(t, "Could not update cache level")
            }
        }
    }

    fun downloadAllChapters() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, downloadProgress = 0 to story.chapterCount) }
            try {
                val count = withContext(Dispatchers.IO) {
                    books.downloadAllChapters(story.bookId, story.chapterIndex) { done, total ->
                        _ui.update { it.copy(downloadProgress = done to total) }
                    }
                }
                val refreshed = withContext(Dispatchers.IO) { loadStory(story.bookId) }
                refreshLocal()
                _ui.update {
                    it.copy(
                        busy = false,
                        story = refreshed,
                        downloadProgress = null,
                        showDownload = false,
                        downloadPane = PluginDownloadPane.Menu,
                        message = "Downloaded $count / ${refreshed.chapterCount} chapters",
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Download failed")
            }
        }
    }

    fun downloadPartialChapters(countCapOverride: Int? = null) {
        resolvePartialStartDraft()
        val story = _ui.value.story ?: return
        val cap = countCapOverride ?: _ui.value.partialCountDraft.toIntOrNull()?.takeIf { it > 0 }
        val startIndex = _ui.value.partialStartIndex.coerceIn(0, (story.chapterCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            // Move locus to the download start first so the strip and cache window follow it.
            _ui.update {
                it.copy(
                    busy = true,
                    error = null,
                    downloadProgress = 0 to 1,
                    story = story.copy(chapterIndex = startIndex),
                    partialStartIndex = startIndex,
                    partialStartDraft = (startIndex + 1).toString(),
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    flow.db.progress().get(story.bookId)?.let { row ->
                        flow.db.progress().upsert(
                            row.copy(
                                chapterIndex = startIndex,
                                blockIndex = 0,
                                charOffset = 0,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    books.downloadPartialChapters(story.bookId, startIndex, cap) { done, total ->
                        _ui.update { it.copy(downloadProgress = done to total) }
                    }
                }
                val refreshed = withContext(Dispatchers.IO) { loadStory(story.bookId) }
                refreshLocal()
                _ui.update {
                    it.copy(
                        busy = false,
                        story = refreshed,
                        downloadProgress = null,
                        showDownload = false,
                        downloadPane = PluginDownloadPane.Menu,
                        message = "Cached $count / ${refreshed.chapterCount} chapters",
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Partial download failed")
            }
        }
    }

    /** Cache settings: pin/download from the start chapter for "cache level" chapters. */
    fun beginPartialDownloadFromSettings() {
        resolvePartialStartDraft()
        val level = _ui.value.cacheLevelDraft.toIntOrNull()?.takeIf { it > 0 }
        if (level == null) {
            _ui.update { it.copy(error = "Set Cache level to how many chapters to download.") }
            return
        }
        downloadPartialChapters(countCapOverride = level)
    }

    // --- Account ----------------------------------------------------------------------------

    fun login() {
        val auth = manifest.auth ?: return
        val fields = auth.fields.associate { it.key to _ui.value.loginFields[it.key].orEmpty() }
        if (fields.values.any { it.isBlank() }) {
            _ui.update { it.copy(error = auth.fields.joinToString(" and ") { f -> f.label } + " are required") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val session = withContext(Dispatchers.IO) { source().login(fields) }
                if (!session.loggedIn) throw PluginException(PluginErrorCode.AuthRequired, "Sign in failed")
                val secretKeys = auth.fields.filter { it.secret }.map { it.key }.toSet()
                val firstSyncable = manifest.lists.firstOrNull { it.syncable }?.id
                _ui.update {
                    it.copy(
                        busy = false,
                        session = session,
                        loginFields = it.loginFields.filterKeys { k -> k !in secretKeys },
                        showLogin = false,
                        showAccount = true,
                        syncListId = firstSyncable,
                    )
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, error = t.message ?: "Sign in failed") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { source().logout() } }
            _ui.update {
                it.copy(session = PluginSession(), loginFields = emptyMap(), syncListId = null, showAccount = false)
            }
        }
    }

    companion object {
        private const val MAX_SYNC_PAGES = 100

        fun factory(app: Application, pluginId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PluginTabViewModel(app, pluginId) as T
            }
    }
}
