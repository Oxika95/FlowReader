package com.personal.flowreader.ui.plugin

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.data.PositionDomain
import com.personal.flowreader.data.PositionSource
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.ReadingSessionId
import com.personal.flowreader.plugin.store.toItem
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
import com.personal.flowreader.plugin.store.SyncMode
import com.personal.flowreader.plugin.sync.ProgressReconcile
import com.personal.flowreader.plugin.sync.PushResult
import com.personal.flowreader.plugin.updates.UpdateDiff
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie

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
    /** Reading chapters (app, site) when both moved since the last sync; the user picks one. */
    val positionConflict: Pair<Int, Int>? = null,
) {
    val chapterCount: Int get() = toc.size
}

/** Which sub-tab of the plugin tab is showing. */
sealed interface PluginSection {
    data class Library(val listId: String) : PluginSection
    data object Search : PluginSection
}

data class PluginTabUi(
    val manifest: PluginManifest,
    val books: List<BookItem> = emptyList(),
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
    /** Open browse-list entry page (creator page). */
    val browse: PluginBrowseUi? = null,
    /** Work id → title of the first story list it is on ("In Follow" badges). */
    val storyLists: Map<String, String> = emptyMap(),
    val sync: PluginSyncUi = PluginSyncUi(),
    /** Book id whose position conflict card was put off ("Later") while its story card is open. */
    val conflictDismissedFor: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val visibleBooks: List<BookItem>
        get() = if (listBookIds.isEmpty()) emptyList() else books.filter { it.bookId in listBookIds }

    val anyOverlay: Boolean
        get() = showLogin || showAdd || showAccount || showSettings || syncListId != null || story != null ||
            browse != null
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
    private val listIds: List<String> get() = manifest.lists.filterNot { it.isBrowse }.map { it.id }
    private val _ui = MutableStateFlow(PluginTabUi(manifest = manifest))
    val ui: StateFlow<PluginTabUi> = _ui

    private fun source(): PluginSource = manager.source(pluginId)

    private var downloadJob: Job? = null

    private val browser = PluginBrowseController(viewModelScope, _ui, ::source, ::fail) { localStory(it, catalog = false) }

    private val lists get() = flow.pluginSync.lists

    private val syncer = PluginSyncController(viewModelScope, _ui, flow, pluginId, manifest, ::fail, ::onSyncChanged)

    /** Book id → chapter the open story card is positioned on instead of the saved position. */
    private var chapterFocus: Pair<String, Int>? = null

    /** Browse list id → last silent refresh (elapsed realtime). */
    private val browseRefreshedAt = HashMap<String, Long>()

    init {
        refreshLocal()
        refreshSession()
        viewModelScope.launch {
            manager.sessionChanges.filter { it == pluginId }.collect { refreshSession() }
        }
    }

    fun refreshLocal() {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { flow.pluginCatalog.list(pluginId).map { it.toItem(pluginId) } }
            val (ids, listRows) = withContext(Dispatchers.IO) { membership(_ui.value.section) }
            val meta = withContext(Dispatchers.IO) { books.libraryMetas(rows.map { it.bookId }) }
            val storyLists = withContext(Dispatchers.IO) { storyLists() }
            val posts = withContext(Dispatchers.IO) {
                browser.postsBookIds().mapNotNull { runCatching { loadStory(it) }.getOrNull() }
            }
            _ui.update {
                it.copy(books = rows, libraryMeta = meta, listBookIds = ids, listRows = listRows, storyLists = storyLists)
            }
            posts.forEach(browser::updatePosts)
        }
    }

    private fun storyLists(): Map<String, String> {
        val out = HashMap<String, String>()
        manifest.lists.filterNot { it.isBrowse }.forEach { list ->
            PluginMembershipStore.workIds(dataDir, list.id).forEach { out.putIfAbsent(it, list.title) }
        }
        return out
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
            maybeRefreshBrowse(_ui.value.section)
            syncer.autoSync()
        }
    }

    /** A sync changed lists, positions or conflicts: reload the library and the open story. */
    private fun onSyncChanged() {
        refreshLocal()
        val open = _ui.value.story ?: return
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) { runCatching { loadStory(open.bookId) }.getOrNull() } ?: return@launch
            _ui.update { ui -> if (ui.story?.bookId == open.bookId) ui.copy(story = refreshed) else ui }
        }
    }

    fun syncNow() = syncer.syncNow()

    /** Position conflict on the open story: take the site's chapter or keep the app's. */
    fun resolveConflict(useRemote: Boolean) {
        val story = _ui.value.story ?: return
        syncer.resolveConflict(story.workId, useRemote)
    }

    fun postponeConflict() = _ui.update { it.copy(conflictDismissedFor = it.story?.bookId) }

    private fun membership(section: PluginSection): Pair<Set<String>, Map<String, PluginWork>> {
        val listId = (section as? PluginSection.Library)?.listId ?: return emptySet<String>() to emptyMap()
        val rows = PluginMembershipStore.read(dataDir, listId)
        val byBook = rows.associateBy { manager.bookIdFor(pluginId, it.id) }
        return byBook.keys to byBook
    }

    fun setSection(section: PluginSection) {
        val (ids, rows) = membership(section)
        _ui.update { it.copy(section = section, listBookIds = ids, listRows = rows, error = null) }
        maybeRefreshBrowse(section)
    }

    // --- Browse lists -----------------------------------------------------------------------

    fun openBrowse(listId: String, row: PluginWork) = browser.open(listId, row)

    fun setBrowseTab(tab: String) = browser.selectTab(tab)

    fun setBrowseSort(sort: String) = browser.setSort(sort)

    fun browseMore() = browser.more()

    fun setBrowsePostsOrder(oldestFirst: Boolean) = browser.setPostsOrder(oldestFirst)

    /**
     * Story-pane tab (e.g. a creator's posts): open its card from local data, positioned on the
     * tapped [chapterIndex] (Read and Download start there) until a position is saved or the card closes.
     */
    fun openBrowseStory(workId: String, chapterIndex: Int) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                chapterFocus = manager.bookIdFor(pluginId, workId) to chapterIndex
                val story = withContext(Dispatchers.IO) { localStory(workId, catalog = true) }
                refreshLocal()
                showStory(story, closeAdd = false)
            } catch (t: Throwable) {
                chapterFocus = null
                fail(t, "Could not open story")
            }
        }
    }

    /** Stored story for [workId], fetched once when missing; [catalog] also adds it to the plugin catalog. */
    private suspend fun localStory(workId: String, catalog: Boolean): PluginStory {
        val bookId = manager.bookIdFor(pluginId, workId)
        val session = books.readSplashBundle(bookId)?.first?.takeIf { it.toc.isNotEmpty() }
            ?: books.fetchAndStoreWork(pluginId, workId)
        if (catalog) {
            val cover = PluginSessionStore.readSplash(PluginSessionStore.dir(dataDir, workId))?.cover.orEmpty()
            upsertWork(PluginWork(id = workId, title = session.title, url = session.workUrl, author = session.author, cover = cover))
        }
        return loadStory(bookId)
    }

    /** Visiting an entry can change its row (e.g. clears a "new" badge), so refresh that list. */
    fun closeBrowse() {
        val closed = browser.close() ?: return
        maybeRefreshBrowse(PluginSection.Library(closed.listId), force = true)
    }

    /** Signed-in browse lists re-import silently (rows only) when shown, at most every [BROWSE_REFRESH_MS]. */
    private fun maybeRefreshBrowse(section: PluginSection, force: Boolean = false) {
        val listId = (section as? PluginSection.Library)?.listId ?: return
        val list = manifest.list(listId)?.takeIf { it.isBrowse && it.syncable } ?: return
        if (!_ui.value.session.loggedIn) return
        val now = SystemClock.elapsedRealtime()
        val last = browseRefreshedAt[listId]
        if (!force && last != null && now - last < BROWSE_REFRESH_MS) return
        browseRefreshedAt[listId] = now
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { lists.applyLists(pluginId, mapOf(list.id to lists.fetchList(pluginId, list.id)), SyncMode.Merge) }
            }.onSuccess { refreshLocal() }
        }
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

    fun closeStory() {
        chapterFocus = null
        val closed = _ui.value.story
        _ui.update {
            it.copy(story = null, confirmDownloadAll = false, showPartial = false, showPosition = false, conflictDismissedFor = null)
        }
        if (closed != null && closed.bookId in browser.postsBookIds()) refreshLocal()
    }

    private suspend fun loadStory(bookId: String): PluginStory {
        val (session, splash) = books.readSplashBundle(bookId)
            ?: throw PluginException(PluginErrorCode.Error, "Story is not cached")
        val progress = flow.pluginCatalog.get(bookId)?.position
        val dir = PluginSessionStore.dir(dataDir, session.workId)
        val listedIn = PluginMembershipStore.listsContaining(dataDir, listIds, session.workId)
        val tocUrls = session.toc.map { it.url }
        val conflict = flow.pluginSync.state(pluginId).conflicts[session.workId]?.let { c ->
            val local = ProgressReconcile.tocIndex(tocUrls, c.localUrl)
            val remote = ProgressReconcile.tocIndex(tocUrls, c.remoteUrl)
            if (local != null && remote != null) local to remote else null
        }
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
            readingProgress = progress?.fraction ?: 0f,
            chapterIndex = PluginSessionStore.savedChapter(session.toc, progress?.chapterHref.orEmpty(), progress?.chapterIndex ?: 0),
            hasSavedPosition = progress?.hasProgress == true,
            cacheLevel = session.cacheLevel,
            cleanup = session.cleanup,
            listedIn = listedIn,
            card = splash?.card,
            notify = UpdateDiff.notifyOn(session.notify, listedIn, UpdateDiff.notifyLists(manifest.lists)),
            positionConflict = conflict,
        ).let { s ->
            chapterFocus?.takeIf { it.first == bookId && it.second in s.toc.indices }
                ?.let { s.copy(chapterIndex = it.second) } ?: s
        }
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

    private suspend fun upsertWork(work: PluginWork) = lists.upsertWork(pluginId, work)

    // --- Lists ------------------------------------------------------------------------------

    /** Toggle the open story on/off a list; the site change is queued and tried right away. */
    fun toggleList(listId: String) {
        val story = _ui.value.story ?: return
        val list = manifest.list(listId) ?: return
        val on = listId !in story.listedIn
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val pushed = withContext(Dispatchers.IO) {
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
                    if (list.syncable) flow.pluginSync.membershipChanged(pluginId, story.workId, listId, on) else PushResult.LocalOnly
                }
                val refreshed = withContext(Dispatchers.IO) { runCatching { loadStory(story.bookId) }.getOrNull() }
                refreshLocal()
                syncer.refreshState()
                val where = when (pushed) {
                    PushResult.Site -> " on ${manifest.name}"
                    PushResult.Queued -> " (${manifest.name} updates when online)"
                    PushResult.LocalOnly -> ""
                }
                val verb = if (on) "Added ${story.title} to ${list.title}$where" else "Removed ${story.title} from ${list.title}$where"
                _ui.update { it.copy(busy = false, story = refreshed ?: it.story, message = verb) }
                setSection(_ui.value.section)
            } catch (t: Throwable) {
                fail(t, "Could not update list")
            }
        }
    }

    /** "Replace with site lists": [SYNC_ALL_LISTS] = every syncable list. */
    fun syncList(listId: String, mode: SyncMode) {
        val targets = syncTargets(manifest, listId).ifEmpty { return }
        targets.filter { it.isBrowse }.forEach { browseRefreshedAt[it.id] = SystemClock.elapsedRealtime() }
        syncer.replaceWithSite(listId, mode)
        setSection(PluginSection.Library(targets.first().id))
    }

    // --- Reading ----------------------------------------------------------------------------

    /** Tap a library card: open the reader at saved progress. */
    fun readBook(actions: LibraryPluginActions, bookId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, story = null) }
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    // ToC first: storing it applies a site position waiting for it.
                    val toc = books.openStory(bookId).toc
                    val row = flow.pluginCatalog.get(bookId)?.position
                    val start = if (row == null) 0 else PluginSessionStore.savedChapter(toc, row.chapterHref, row.chapterIndex)
                    startAndSnapshot(bookId, start)
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
                chapterFocus = null
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
        flow.pluginCatalog.upsertBook(
            pluginId = pluginId,
            bookId = started.bookId,
            title = started.title,
            sourceUri = started.workUrl,
            text = text.ifBlank { started.title },
            startChapter = started.startIndex,
            chapterHref = started.toc.getOrNull(started.startIndex)?.url.orEmpty(),
            chapterCount = started.toc.size,
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
                    flow.pluginCatalog.removeMembership(story.bookId)
                    PluginMembershipStore.removeFromAll(dataDir, listIds, story.workId)
                    books.deleteLocalSession(story.bookId)
                    story.listedIn.filter { manifest.list(it)?.syncable == true }.forEach { listId ->
                        flow.pluginSync.membershipChanged(pluginId, story.workId, listId, on = false)
                    }
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
                    if (flow.pluginCatalog.get(story.bookId) == null) {
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
                            session = ReadingSessionId(PositionDomain.Plugin, story.bookId),
                            rowKey = story.bookId,
                            chapterIndex = index,
                            blockIndex = 0,
                            charOffset = 0,
                            fraction = if (story.chapterCount > 0) index.toFloat() / story.chapterCount else 0f,
                            at = System.currentTimeMillis(),
                            anchorText = "",
                            chapterHref = story.toc.getOrNull(index)?.url.orEmpty(),
                            source = PositionSource.PluginSeek,
                        ),
                    )
                    flow.progress.drain()
                    chapterFocus = null
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
                _ui.update {
                    it.copy(
                        busy = false,
                        session = session,
                        loginFields = it.loginFields.filterKeys { k -> k !in secretKeys },
                        showLogin = false,
                        showAccount = true,
                    )
                }
                manager.sessionChanged(pluginId)
                syncAfterSignIn()
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, error = t.message ?: "Sign in failed") }
            }
        }
    }

    /** The sign-in browser saw the session cookie: hand the site's cookies to the plugin, then ask it who is signed in. */
    fun completeWebLogin(cookies: List<Cookie>) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val session = withContext(Dispatchers.IO) {
                    manager.cookieJar(pluginId).import(cookies)
                    source().session()
                }
                if (!session.loggedIn) throw PluginException(PluginErrorCode.AuthRequired, "Sign in failed")
                _ui.update {
                    it.copy(
                        busy = false,
                        session = session,
                        showLogin = false,
                        showAccount = true,
                    )
                }
                manager.sessionChanged(pluginId)
                syncAfterSignIn()
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, showLogin = false, error = t.message ?: "Sign in failed") }
            }
        }
    }

    /** Two-way sync right away; plugins without it get the Merge / Overwrite import choice. */
    private fun syncAfterSignIn() {
        if (flow.pluginSync.supports(manifest)) syncer.syncNow() else _ui.update { it.copy(syncListId = defaultSyncChoice(manifest)) }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { source().logout() } }
            if (manifest.auth?.web != null) WebLoginCookieStore.clear(manifest.allowedHosts)
            _ui.update {
                it.copy(session = PluginSession(), loginFields = emptyMap(), syncListId = null, showAccount = false)
            }
            manager.sessionChanged(pluginId)
        }
    }

    companion object {
        private const val BROWSE_REFRESH_MS = 15 * 60 * 1000L

        fun factory(app: Application, pluginId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PluginTabViewModel(app, pluginId) as T
            }
    }
}
