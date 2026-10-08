package com.personal.flowreader.ui.plugin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.ProgressUpdate
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
import com.personal.flowreader.plugin.updates.UpdateDiff
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
    val cover: String,
    val toc: List<PluginChapterRef>,
    val downloadedCount: Int,
    /** Absolute ToC indices with bodies on disk. */
    val cachedIndices: Set<Int>,
    val readingProgress: Float,
    /** Saved reading position (absolute ToC index); the cache bar locus and download start. */
    val chapterIndex: Int,
    /** A position past the start of chapter 1 is saved. */
    val hasSavedPosition: Boolean = false,
    val cacheLevel: Int = PluginCachePolicy.DEFAULT_LEVEL,
    val cleanup: Boolean = false,
    /** Plugin list ids this story is tagged with. */
    val listedIn: Set<String> = emptySet(),
    /** Media card slots; null when the plugin fills none. */
    val card: PluginCard? = null,
    /** New-chapter notifications for this story (bell on the media card). */
    val notify: Boolean = false,
) {
    val chapterCount: Int get() = toc.size
}

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
    /** "Download all N chapters?" confirm is showing. */
    val confirmDownloadAll: Boolean = false,
    /** Partial download card (long-press Download) is showing. */
    val showPartial: Boolean = false,
    /** Position slider (long-press the cache bar) is showing. */
    val showPosition: Boolean = false,
    /** Chapters kept ahead of the reading position; may be blank while editing. */
    val cacheLevelDraft: String = PluginCachePolicy.DEFAULT_LEVEL.toString(),
    /** Non-null while a download runs. */
    val downloadProgress: Pair<Int, Int>? = null,
    /** Story the running download belongs to. */
    val downloadBookId: String? = null,
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
    private val syncableListIds: Set<String> get() = manifest.lists.filter { it.syncable }.map { it.id }.toSet()

    private val _ui = MutableStateFlow(PluginTabUi(manifest = manifest))
    val ui: StateFlow<PluginTabUi> = _ui

    private fun source(): PluginSource = manager.source(pluginId)

    private var downloadJob: Job? = null

    init {
        refreshLocal()
        refreshSession()
    }

    fun refreshLocal() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { flow.catalog.listPlugin(pluginId) }
            val (ids, listRows) = withContext(Dispatchers.IO) { membership(_ui.value.section) }
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

    private fun showStory(splash: PluginStory, closeAdd: Boolean) {
        _ui.update {
            it.copy(
                busy = false,
                showAdd = if (closeAdd) false else it.showAdd,
                story = splash,
                cacheLevelDraft = splash.cacheLevel.toString(),
            )
        }
    }

    fun closeStory() = _ui.update {
        it.copy(story = null, confirmDownloadAll = false, showPartial = false, showPosition = false)
    }

    private suspend fun loadStory(bookId: String): PluginStory {
        val (session, splash) = books.readSplashBundle(bookId)
            ?: throw PluginException(PluginErrorCode.Error, "Story is not cached")
        val progress = flow.db.progress().get(bookId)
        val dir = PluginSessionStore.dir(dataDir, session.workId)
        val listedIn = PluginMembershipStore.listsContaining(dataDir, listIds, session.workId)
        return PluginStory(
            bookId = bookId,
            workId = session.workId,
            workUrl = session.workUrl,
            title = session.title,
            author = session.author,
            synopsis = splash?.synopsis.orEmpty(),
            tags = splash?.tags.orEmpty(),
            cover = splash?.cover.orEmpty(),
            toc = session.toc,
            downloadedCount = PluginSessionStore.cachedChapterCount(dir, session.toc.size),
            cachedIndices = PluginSessionStore.cachedChapterIndices(dir, session.toc.size),
            readingProgress = progress?.readingProgress ?: 0f,
            chapterIndex = (progress?.chapterIndex ?: 0).coerceIn(0, session.toc.lastIndex.coerceAtLeast(0)),
            hasSavedPosition = progress != null &&
                (progress.chapterIndex > 0 || progress.blockIndex > 0 || progress.charOffset > 0),
            cacheLevel = session.cacheLevel,
            cleanup = session.cleanup,
            listedIn = listedIn,
            card = splash?.card,
            notify = UpdateDiff.notifyOn(session.notify, listedIn, syncableListIds),
        )
    }

    /** Bell on the media card: new-chapter notifications for the open story. */
    fun toggleNotify() {
        val story = _ui.value.story ?: return
        val on = !story.notify
        _ui.update { ui -> ui.copy(story = ui.story?.takeIf { it.bookId == story.bookId }?.copy(notify = on) ?: ui.story) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { books.setNotify(story.bookId, on) } }
            if (on) flow.requestNotificationPermission()
            _ui.update {
                it.copy(
                    message = if (on) "New-chapter alerts on for ${story.title}" else "New-chapter alerts off for ${story.title}",
                )
            }
        }
    }

    /**
     * A plugin-declared media card action was tapped. Applies the returned card
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
            startChapter = started.startIndex,
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

    /** Tap Download: confirm Download all, or cancel the running download. */
    fun onDownloadTap() {
        val story = _ui.value.story ?: return
        if (downloadJob?.isActive == true) {
            if (_ui.value.downloadBookId == story.bookId) {
                downloadJob?.cancel()
            } else {
                _ui.update { it.copy(message = "Another story is still downloading") }
            }
            return
        }
        _ui.update { it.copy(confirmDownloadAll = true, error = null) }
    }

    fun dismissDownloadAll() = _ui.update { it.copy(confirmDownloadAll = false) }

    fun downloadAllChapters() {
        val story = _ui.value.story ?: return
        _ui.update { it.copy(confirmDownloadAll = false) }
        runDownload(story, "Download failed") { onProgress ->
            val count = books.downloadAllChapters(story.bookId, story.chapterIndex, onProgress)
            "Downloaded $count / ${story.chapterCount} chapters"
        }
    }

    /** Long-press Download: the partial download card. */
    fun openPartial() {
        val story = _ui.value.story ?: return
        _ui.update { it.copy(showPartial = true, cacheLevelDraft = story.cacheLevel.toString(), error = null) }
    }

    /** A blank cache level field goes back to the saved level. */
    fun closePartial() = _ui.update {
        it.copy(showPartial = false, cacheLevelDraft = it.story?.cacheLevel?.toString() ?: it.cacheLevelDraft)
    }

    fun setCacheLevelDraft(value: String) {
        val digits = value.filter { c -> c.isDigit() }.take(4)
        _ui.update { it.copy(cacheLevelDraft = digits) }
        val story = _ui.value.story ?: return
        val level = digits.toIntOrNull() ?: return
        saveCachePolicy(story, level, story.cleanup)
    }

    fun setCleanup(on: Boolean) {
        val story = _ui.value.story ?: return
        saveCachePolicy(story, story.cacheLevel, on)
    }

    private fun saveCachePolicy(story: PluginStory, level: Int, cleanup: Boolean) {
        _ui.update { ui -> ui.copy(story = ui.story?.copy(cacheLevel = level, cleanup = cleanup)) }
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    books.setCachePolicy(story.bookId, level, cleanup)
                    if (cleanup) books.pruneChapterCache(story.bookId, story.chapterIndex)
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update { ui -> if (ui.story?.bookId == story.bookId) ui.copy(story = refreshed) else ui }
            } catch (t: Throwable) {
                fail(t, "Could not update cache settings")
            }
        }
    }

    /** Download the saved position and the cache level of chapters after it. */
    fun downloadAhead() {
        val story = _ui.value.story ?: return
        if (downloadJob?.isActive == true) {
            _ui.update { it.copy(message = "A download is already running") }
            return
        }
        runDownload(story, "Partial download failed") { onProgress ->
            val count = books.downloadAhead(story.bookId, story.chapterIndex, onProgress)
            "Cached $count / ${story.chapterCount} chapters"
        }
    }

    private fun runDownload(
        story: PluginStory,
        failure: String,
        block: suspend (onProgress: (Int, Int) -> Unit) -> String,
    ) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _ui.update { it.copy(error = null, downloadProgress = 0 to 1, downloadBookId = story.bookId) }
            try {
                val message = withContext(Dispatchers.IO) {
                    block { done, total -> _ui.update { it.copy(downloadProgress = done to total) } }
                }
                finishDownload(story, message)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { finishDownload(story, "Download cancelled") }
                throw e
            } catch (t: Throwable) {
                fail(t, failure)
                runCatching { finishDownload(story, null) }
            }
        }
    }

    private suspend fun finishDownload(story: PluginStory, message: String?) {
        val refreshed = withContext(Dispatchers.IO) { runCatching { loadStory(story.bookId) }.getOrNull() }
        refreshLocal()
        _ui.update {
            it.copy(
                downloadProgress = null,
                downloadBookId = null,
                showPartial = if (message != null) false else it.showPartial,
                story = if (it.story?.bookId == story.bookId) refreshed ?: it.story else it.story,
                message = message ?: it.message,
            )
        }
    }

    // --- Position ---------------------------------------------------------------------------

    /** Long-press the cache bar: the position slider. */
    fun openPosition() = _ui.update { it.copy(showPosition = it.story != null, error = null) }

    fun closePosition() = _ui.update { it.copy(showPosition = false) }

    /**
     * Save chapter [chapterIndex] as the reading position (start of the chapter). Goes through
     * [com.personal.flowreader.data.ProgressWriter], whose hook syncs the site and downloads ahead.
     */
    fun savePosition(chapterIndex: Int) {
        val story = _ui.value.story ?: return
        val index = chapterIndex.coerceIn(0, (story.chapterCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    if (flow.db.progress().get(story.bookId) == null) {
                        upsertWork(
                            PluginWork(
                                id = story.workId,
                                title = story.title,
                                url = story.workUrl,
                                author = story.author,
                                cover = story.cover,
                            ),
                        )
                    }
                    flow.progress.submit(
                        ProgressUpdate(
                            bookId = story.bookId,
                            chapterIndex = index,
                            blockIndex = 0,
                            charOffset = 0,
                            fraction = if (story.chapterCount > 0) index.toFloat() / story.chapterCount else 0f,
                            at = System.currentTimeMillis(),
                            anchorText = "",
                            chapterHref = story.toc.getOrNull(index)?.url.orEmpty(),
                        ),
                    )
                    flow.progress.drain()
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update {
                    it.copy(
                        busy = false,
                        showPosition = false,
                        story = refreshed,
                        message = "Position saved: chapter ${index + 1}",
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Could not save position")
            }
        }
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
