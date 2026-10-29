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
import com.personal.flowreader.plugin.runtime.PluginSecrets
import com.personal.flowreader.plugin.runtime.PluginSessionSeed
import com.personal.flowreader.plugin.store.PluginCachePolicy
import com.personal.flowreader.plugin.store.PluginLibraryMeta
import com.personal.flowreader.plugin.store.PluginMembershipStore
import com.personal.flowreader.plugin.store.PluginReadSession
import com.personal.flowreader.plugin.store.PluginSessionStore
import com.personal.flowreader.plugin.store.SyncMode
import com.personal.flowreader.plugin.sync.ProgressReconcile
import com.personal.flowreader.plugin.sync.PushResult
import com.personal.flowreader.plugin.updates.UpdateChecker
import com.personal.flowreader.plugin.updates.UpdateDiff
import com.personal.flowreader.ui.library.LibrarySort
import com.personal.flowreader.ui.library.LibrarySortMode
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    /** Shown title: the user's rename, else [pluginTitle]. */
    val title: String,
    /** The plugin's title (what list files and library rows store). */
    val pluginTitle: String,
    val author: String,
    val synopsis: String,
    val tags: List<String>,
    val cover: String,
    val toc: List<PluginChapterRef>,
    val downloadedCount: Int,
    /** Absolute ToC indices with bodies on disk. */
    val cachedIndices: Set<Int>,
    val readingProgress: Float,
    /** Saved reading position (absolute ToC index); the position slider mark and download start. */
    val chapterIndex: Int,
    /** A position past the start of chapter 1 is saved. */
    val hasSavedPosition: Boolean = false,
    val cacheLevel: Int = PluginCachePolicy.DEFAULT_LEVEL,
    val cleanup: Boolean = false,
    /** Download-all / keep every chapter on disk. */
    val keepAll: Boolean = false,
    /** Plugin list ids this story is tagged with. */
    val listedIn: Set<String> = emptySet(),
    /** Media card slots; null when the plugin fills none. */
    val card: PluginCard? = null,
    /** New-chapter notifications for this story (bell on the media card). */
    val notify: Boolean = false,
    /** Reading chapters (app, site) when both moved since the last sync; the user picks one. */
    val positionConflict: Pair<Int, Int>? = null,
    /** Chapters added since the story was last opened in the reader. */
    val newChapters: Int = 0,
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
    /** Book id → chapters added since the story was last opened (library badges). */
    val newCounts: Map<String, Int> = emptyMap(),
    /** Story list id → book ids on it. */
    val listMembers: Map<String, Set<String>> = emptyMap(),
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
    /** Story Manage card (download settings / clear / remove) is showing. */
    val showManage: Boolean = false,
    /** Contents overlay from the media card ToC rail. */
    val showToc: Boolean = false,
    /** Reading-position card (−/+ steps) is showing. */
    val showPosition: Boolean = false,
    val showRename: Boolean = false,
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
    /** Account → Open site WebView. */
    val showSiteWeb: Boolean = false,
    /** Page title for the Open site WebView. */
    val siteWebTitle: String = "",
    /** Start URL for the Open site WebView. */
    val siteWebUrl: String = "",
    /** Jar cookies to seed into the Open site WebView. */
    val siteSeedCookies: List<Cookie> = emptyList(),
    /** WebView crawl of sitePages with listId (Sync / Replace / Site Done). */
    val listCrawl: PluginListCrawlUi? = null,
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
    /** Soft ToC refresh on the open media card (pull or open-in-background); does not block actions. */
    val storyRefreshing: Boolean = false,
    /** True until the first local membership + catalog paint finishes. */
    val listsLoading: Boolean = true,
    /** Pull to refresh running on the list. */
    val refreshing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Soft re-login nudge when the cookie blob is older than the plugin's soft TTL. */
    val sessionStaleHint: String? = null,
    /** Card order for story lists (not browse / search). */
    val sortMode: LibrarySortMode = LibrarySort.defaultFor(plugin = true),
) {
    val visibleBooks: List<BookItem>
        get() {
            if (listBookIds.isEmpty()) return emptyList()
            val rows = books.filter { it.bookId in listBookIds }
            return LibrarySort.sort(
                rows,
                sortMode,
                chapterCountOf = { book ->
                    libraryMeta[book.bookId]?.chapterCount?.takeIf { it > 0 } ?: book.chapterCount
                },
                authorOf = { book ->
                    libraryMeta[book.bookId]?.author?.ifBlank { null }
                        ?: listRows[book.bookId]?.author?.ifBlank { null }
                        ?: book.author
                },
            )
        }

    val anyOverlay: Boolean
        get() = showLogin || showAdd || showAccount || showSiteWeb ||
            listCrawl != null || syncListId != null || story != null || browse != null
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
    private var storyRefreshJob: Job? = null

    private val browser = PluginBrowseController(viewModelScope, _ui, ::source, ::fail) { localStory(it, catalog = false) }

    private val lists get() = flow.pluginSync.lists

    private val syncer = PluginSyncController(viewModelScope, _ui, flow, pluginId, manifest, ::fail, ::onSyncChanged)

    /** Book id → chapter the open story card is positioned on instead of the saved position. */
    private var chapterFocus: Pair<String, Int>? = null

    /** Browse list id → last silent refresh (elapsed realtime). */
    private val browseRefreshedAt = HashMap<String, Long>()

    init {
        viewModelScope.launch {
            val sort = LibrarySort.parse(
                flow.settings.librarySortOnce(pluginId),
                LibrarySort.pluginModes,
                LibrarySort.defaultFor(plugin = true),
            )
            _ui.update { it.copy(sortMode = sort) }
        }
        refreshLocal()
        refreshSession()
        viewModelScope.launch {
            manager.sessionChanges.filter { it == pluginId }.collect { refreshSession() }
        }
        viewModelScope.launch {
            books.newChapterCounts(pluginId).collect { counts -> _ui.update { it.copy(newCounts = counts) } }
        }
    }

    fun setSortMode(mode: LibrarySortMode) {
        if (mode !in LibrarySort.pluginModes) return
        _ui.update { it.copy(sortMode = mode) }
        viewModelScope.launch { flow.settings.setLibrarySort(pluginId, mode.name) }
    }

    fun refreshLocal() {
        viewModelScope.launch {
            // Paint membership + catalog first so the library is not empty while meta/posts load.
            val (ids, listRows) = withContext(Dispatchers.IO) { membership(_ui.value.section) }
            val rows = withContext(Dispatchers.IO) { flow.pluginCatalog.list(pluginId).map { it.toItem(pluginId) } }
            val storyLists = withContext(Dispatchers.IO) { storyLists() }
            val members = withContext(Dispatchers.IO) { listMembers() }
            _ui.update {
                it.copy(
                    books = rows,
                    listMembers = members,
                    listBookIds = ids,
                    listRows = listRows,
                    storyLists = storyLists,
                    listsLoading = false,
                )
            }
            val meta = withContext(Dispatchers.IO) { books.libraryMetas(rows.map { it.bookId }) }
            val posts = withContext(Dispatchers.IO) {
                browser.postsBookIds().mapNotNull { runCatching { loadStory(it) }.getOrNull() }
            }
            _ui.update { it.copy(libraryMeta = meta) }
            posts.forEach(browser::updatePosts)
        }
    }

    private fun listMembers(): Map<String, Set<String>> =
        manifest.lists.filterNot { it.isBrowse }.associate { list ->
            list.id to PluginMembershipStore.workIds(dataDir, list.id).mapTo(HashSet()) { manager.bookIdFor(pluginId, it) }
        }

    private fun storyLists(): Map<String, String> {
        val out = HashMap<String, String>()
        manifest.lists.filterNot { it.isBrowse }.forEach { list ->
            PluginMembershipStore.workIds(dataDir, list.id).forEach { out.putIfAbsent(it, list.title) }
        }
        return out
    }

    /** Tab resume / Account open: re-check sign-in (secrets first, then network). */
    fun refreshSessionPublic() = refreshSession()

    private fun refreshSession() {
        if (!manifest.has(PluginCapability.Auth)) return
        viewModelScope.launch {
            val secrets = PluginSecrets(getApplication(), pluginId)
            val seeded = PluginSessionSeed.fromSecrets(
                secrets.get("account"),
                secrets.get("loggedIn"),
            )
            val importedAt = PluginSessionSeed.parseImportedAt(secrets.get(PluginSessionSeed.COOKIES_IMPORTED_AT))
            _ui.update {
                it.copy(
                    session = seeded,
                    sessionStaleHint = if (seeded.loggedIn) {
                        PluginSessionSeed.staleHint(pluginId, importedAt)
                    } else {
                        null
                    },
                )
            }
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
                ui.copy(
                    session = session,
                    loginFields = prefill,
                    sessionStaleHint = if (session.loggedIn) {
                        PluginSessionSeed.staleHint(pluginId, importedAt)
                    } else {
                        null
                    },
                )
            }
            maybeRefreshBrowse(_ui.value.section)
            // Sync is manual (Sync now / Replace / pull-to-refresh) or the new-chapter worker — not
            // on Account open or session refresh.
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
        if (manifest.list(listId)?.takeIf { it.isBrowse && it.syncable } == null || !_ui.value.session.loggedIn) return
        val last = browseRefreshedAt[listId]
        if (!force && last != null && SystemClock.elapsedRealtime() - last < BROWSE_REFRESH_MS) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { reimportBrowse(listId) } }.onSuccess { refreshLocal() }
        }
    }

    private suspend fun reimportBrowse(listId: String) {
        browseRefreshedAt[listId] = SystemClock.elapsedRealtime()
        lists.applyLists(pluginId, mapOf(listId to lists.fetchList(pluginId, listId)), SyncMode.Merge)
    }

    /**
     * Pull to refresh on a list: re-import it from the site (browse list) or run two-way sync,
     * then check this plugin's stories for new chapters (badges; no notification).
     */
    fun refresh() {
        if (_ui.value.refreshing || _ui.value.sync.running) return
        val listId = (_ui.value.section as? PluginSection.Library)?.listId ?: return
        val list = manifest.list(listId) ?: return
        val loggedIn = _ui.value.session.loggedIn
        _ui.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when {
                        !loggedIn && manifest.auth != null -> Unit
                        list.isBrowse -> if (list.syncable) reimportBrowse(listId)
                        flow.pluginSync.supports(manifest) -> flow.pluginSync.run(pluginId)
                    }
                    UpdateChecker(flow).run(pluginId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fail(t, "Could not refresh ${list.title}")
            }
            _ui.update { it.copy(refreshing = false) }
            refreshLocal()
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

    /** Open/close Account only — no network (session refresh stays on tab start / sign-in). */
    fun setShowAccount(show: Boolean) =
        _ui.update {
            it.copy(
                showAccount = show,
                showSiteWeb = if (show) it.showSiteWeb else false,
                siteWebTitle = if (show) it.siteWebTitle else "",
                siteWebUrl = if (show) it.siteWebUrl else "",
                siteSeedCookies = if (show) it.siteSeedCookies else emptyList(),
                error = if (show) null else it.error,
            )
        }

    /** Account → Site page: seed WebView from the jar; no network until the page loads. */
    fun openSite(title: String, url: String) {
        if (!url.startsWith("https://")) return
        val cookies = runCatching { manager.cookieJar(pluginId).snapshot() }.getOrDefault(emptyList())
        _ui.update {
            it.copy(
                showSiteWeb = true,
                siteWebTitle = title.ifBlank { manifest.name },
                siteWebUrl = url,
                siteSeedCookies = cookies,
                error = null,
            )
        }
    }

    fun dismissSiteWeb() =
        _ui.update {
            it.copy(showSiteWeb = false, siteWebTitle = "", siteWebUrl = "", siteSeedCookies = emptyList())
        }

    /**
     * Open site Done: import WebView cookies, stamp import time, one user-initiated `session()`.
     * Does not pull lists — use Sync / Replace for that.
     */
    fun completeSiteWeb() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    busy = true,
                    error = null,
                    showSiteWeb = false,
                    siteWebTitle = "",
                    siteWebUrl = "",
                    siteSeedCookies = emptyList(),
                )
            }
            try {
                val session = withContext(Dispatchers.IO) {
                    manager.cookieJar(pluginId).import(WebLoginCookieStore.read(manifest.allowedHosts))
                    PluginSecrets(getApplication(), pluginId).set(
                        PluginSessionSeed.COOKIES_IMPORTED_AT,
                        System.currentTimeMillis().toString(),
                    )
                    source().session()
                }
                _ui.update {
                    it.copy(
                        busy = false,
                        session = session,
                        sessionStaleHint = null,
                        showAccount = true,
                        message = if (session.loggedIn) {
                            "Site session updated. Use Sync to pull lists."
                        } else {
                            "Cookies saved, but still signed out — open Sign in or try Sign in again."
                        },
                    )
                }
                manager.sessionChanged(pluginId)
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        busy = false,
                        showAccount = true,
                        error = t.message ?: "Could not update site session",
                    )
                }
            }
        }
    }

    /** Account → Delete local data (site unchanged). */
    fun eraseLocalData() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val removed = erasePluginData(flow, pluginId, manifest)
                _ui.update {
                    it.copy(
                        busy = false,
                        session = PluginSession(),
                        sessionStaleHint = null,
                        loginFields = emptyMap(),
                        syncListId = null,
                        message = "Deleted ${manifest.name} data ($removed ${if (removed == 1) "story" else "stories"})",
                    )
                }
                manager.sessionChanged(pluginId)
                refreshLocal()
                syncer.refreshState()
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(busy = false, error = t.message ?: "Could not delete ${manifest.name} data")
                }
            }
        }
    }

    fun askSync(listId: String?) = _ui.update { it.copy(syncListId = listId) }

    fun consumeError() = _ui.update { it.copy(error = null) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun fail(t: Throwable, fallback: String) {
        if (t is PluginException && t.code == PluginErrorCode.AuthRequired && manifest.auth != null) {
            _ui.update {
                it.copy(
                    busy = false,
                    storyRefreshing = false,
                    downloadProgress = null,
                    session = it.session.copy(loggedIn = false),
                    sessionStaleHint = null,
                    showLogin = true,
                    showAccount = false,
                    error = t.message ?: "Sign in to ${manifest.name} again.",
                )
            }
            manager.sessionChanged(pluginId)
        } else {
            _ui.update {
                it.copy(busy = false, storyRefreshing = false, downloadProgress = null, error = t.message ?: fallback)
            }
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

    /**
     * Open a library story's media card from local cache first. For plugins with syncable lists
     * or [PluginCapability.Updates], refresh the ToC in the background without blocking the card.
     * A missing local ToC still fetches before showing (cold open).
     */
    fun openStory(bookId: String) {
        storyRefreshJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(error = null, showAdd = false, storyRefreshing = false) }
            try {
                val emptyToc = withContext(Dispatchers.IO) {
                    books.readSplashBundle(bookId)?.first?.toc.isNullOrEmpty()
                }
                if (emptyToc) {
                    _ui.update { it.copy(busy = true) }
                    val splash = withContext(Dispatchers.IO) {
                        books.refreshToc(bookId)
                        loadStory(bookId)
                    }
                    showStory(splash, closeAdd = false)
                } else {
                    val splash = withContext(Dispatchers.IO) { loadStory(bookId) }
                    showStory(splash, closeAdd = false)
                    if (shouldBackgroundRefreshToc()) refreshStoryTocInBackground(bookId, silent = true)
                }
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
        storyRefreshJob?.cancel()
        storyRefreshJob = null
        chapterFocus = null
        val closed = _ui.value.story
        _ui.update {
            it.copy(
                story = null,
                showManage = false,
                showToc = false,
                showPosition = false,
                showRename = false,
                storyRefreshing = false,
                conflictDismissedFor = null,
            )
        }
        if (closed != null && closed.bookId in browser.postsBookIds()) refreshLocal()
    }

    private fun shouldBackgroundRefreshToc(): Boolean =
        manifest.lists.any { it.syncable } || manifest.has(PluginCapability.Updates)

    private suspend fun loadStory(bookId: String): PluginStory {
        val (session, splash) = books.readSplashBundle(bookId)
            ?: throw PluginException(PluginErrorCode.Error, "Story is not cached")
        val progress = flow.pluginCatalog.get(bookId)?.position
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
            title = session.displayTitle,
            pluginTitle = session.title,
            author = session.author,
            synopsis = splash?.synopsis.orEmpty(),
            tags = splash?.tags.orEmpty(),
            cover = splash?.cover.orEmpty(),
            toc = session.toc,
            downloadedCount = session.cached.size,
            cachedIndices = session.cached,
            readingProgress = progress?.fraction ?: 0f,
            chapterIndex = session.resumeChapter(progress),
            hasSavedPosition = progress?.hasProgress == true,
            cacheLevel = session.cacheLevel,
            cleanup = session.cleanup,
            keepAll = session.keepAll,
            listedIn = listedIn,
            card = splash?.card,
            notify = UpdateDiff.notifyOn(session.notify, listedIn, UpdateDiff.notifyLists(manifest.lists)),
            positionConflict = conflict,
            newChapters = session.newChapters,
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

    fun openRename() = _ui.update { it.copy(showRename = it.story != null, error = null) }

    fun closeRename() = _ui.update { it.copy(showRename = false) }

    /** Rename the open story on this device; blank restores the plugin's title. */
    fun renameStory(title: String) {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            val shown = withContext(Dispatchers.IO) {
                runCatching { books.setTitleOverride(story.bookId, title) }.getOrNull()
            }
            if (shown == null) {
                _ui.update { it.copy(error = "Open the story once before renaming it") }
                return@launch
            }
            _ui.update { ui ->
                ui.copy(
                    showRename = false,
                    story = ui.story?.takeIf { it.bookId == story.bookId }?.copy(title = shown) ?: ui.story,
                )
            }
            refreshLocal()
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
                                title = story.pluginTitle,
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

    /**
     * Start WebView list crawl when the plugin declares list sitePages and implements
     * `listFromHtml`. [mode] null = Sync now (three-way); otherwise Replace Merge/Overwrite.
     */
    private suspend fun startListCrawlIfSupported(
        targets: List<ListCrawlTarget>,
        mode: SyncMode?,
    ): Boolean {
        if (targets.isEmpty()) return false
        if (!withContext(Dispatchers.IO) { runCatching { source().supportsListFromHtml() }.getOrDefault(false) }) {
            return false
        }
        val cookies = runCatching { manager.cookieJar(pluginId).snapshot() }.getOrDefault(emptyList())
        _ui.update {
            it.copy(
                listCrawl = PluginListCrawlUi(targets, mode, cookies),
                showSiteWeb = false,
                siteWebTitle = "",
                siteWebUrl = "",
                siteSeedCookies = emptyList(),
                syncListId = null,
                busy = true,
                error = null,
                sync = it.sync.copy(running = true),
            )
        }
        return true
    }

    fun cancelListCrawl() {
        _ui.update {
            it.copy(
                listCrawl = null,
                busy = false,
                sync = it.sync.copy(running = false),
                showAccount = true,
            )
        }
    }

    fun onListCrawlError(message: String) {
        _ui.update {
            it.copy(
                listCrawl = null,
                busy = false,
                sync = it.sync.copy(running = false),
                showAccount = true,
                error = message,
            )
        }
    }

    /** WebView crawl finished: import cookies, write memberships, refresh UI. */
    fun finishListCrawl(remotes: Map<String, List<com.personal.flowreader.plugin.api.PluginWork>>) {
        val crawl = _ui.value.listCrawl
        viewModelScope.launch {
            _ui.update { it.copy(listCrawl = null) }
            try {
                withContext(Dispatchers.IO) {
                    manager.cookieJar(pluginId).import(WebLoginCookieStore.read(manifest.allowedHosts))
                    PluginSecrets(getApplication(), pluginId).set(
                        PluginSessionSeed.COOKIES_IMPORTED_AT,
                        System.currentTimeMillis().toString(),
                    )
                }
                val mode = crawl?.mode
                val message = withContext(Dispatchers.IO) {
                    when (mode) {
                        SyncMode.Merge -> {
                            val count = lists.applyLists(pluginId, remotes, SyncMode.Merge)
                            flow.pluginSync.resetListBaselines(pluginId, remotes.keys)
                            val positions = flow.pluginSync.pullPositions(pluginId, overwrite = false).positionsPulled
                            val head = "Merged $count from crawled lists"
                            if (positions > 0) "$head, $positions reading positions set" else head
                        }
                        SyncMode.Overwrite -> {
                            val report = flow.pluginSync.ingestCrawledLists(pluginId, remotes, overwrite = true)
                            crawlSummary(report, overwrite = true)
                        }
                        null -> {
                            val report = flow.pluginSync.ingestCrawledLists(pluginId, remotes, overwrite = false)
                            crawlSummary(report, overwrite = false)
                        }
                    }
                }
                val session = withContext(Dispatchers.IO) { runCatching { source().session() }.getOrNull() }
                _ui.update {
                    it.copy(
                        busy = false,
                        sync = it.sync.copy(running = false),
                        showAccount = true,
                        session = session ?: it.session,
                        sessionStaleHint = if (session?.loggedIn == true) null else it.sessionStaleHint,
                        message = message,
                    )
                }
                manager.sessionChanged(pluginId)
                syncer.refreshState()
                refreshLocal()
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        busy = false,
                        sync = it.sync.copy(running = false),
                        showAccount = true,
                        error = t.message ?: "Could not import crawled lists",
                    )
                }
            }
        }
    }

    private fun crawlSummary(report: com.personal.flowreader.plugin.sync.SyncReport, overwrite: Boolean): String {
        val parts = buildList {
            if (overwrite && report.added > 0) add("imported ${report.added}")
            else if (report.added > 0) add("${report.added} added")
            if (report.removed > 0) add("${report.removed} removed")
            if (report.positionsPulled > 0) add("${report.positionsPulled} positions updated")
            if (report.pushed > 0) add("${report.pushed} queued for ${manifest.name}")
        }
        return when {
            parts.isEmpty() && overwrite -> "Imported lists from ${manifest.name}"
            parts.isEmpty() -> "Up to date with ${manifest.name}"
            overwrite -> "Imported: " + parts.joinToString(", ")
            else -> "Synced: " + parts.joinToString(", ")
        }
    }

    suspend fun parseCrawledListPage(listId: String, html: String, pageUrl: String) =
        withContext(Dispatchers.IO) { source().listFromHtml(listId, html, pageUrl) }

    // --- Reading ----------------------------------------------------------------------------

    /** Tap a library card: open the reader at saved progress. */
    fun readBook(actions: LibraryPluginActions, bookId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, story = null) }
            actions.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    // ToC first: storing it applies a site position waiting for it.
                    val story = books.openStory(bookId)
                    startAndSnapshot(bookId, story.resumeChapter(flow.pluginCatalog.get(bookId)?.position))
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
        val bookId = _ui.value.story?.bookId ?: return
        refreshStoryTocInBackground(bookId, silent = false)
    }

    /**
     * Reload ToC + splash for [bookId] without setting [PluginTabUi.busy]. Pull-to-refresh
     * ([silent] false) toasts on success; open-card background refresh stays quiet unless auth fails.
     */
    private fun refreshStoryTocInBackground(bookId: String, silent: Boolean) {
        storyRefreshJob?.cancel()
        storyRefreshJob = viewModelScope.launch {
            _ui.update { it.copy(storyRefreshing = true, error = null) }
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    books.refreshToc(bookId)
                    loadStory(bookId)
                }
                if (_ui.value.story?.bookId != bookId) return@launch
                refreshLocal()
                _ui.update {
                    it.copy(
                        storyRefreshing = false,
                        story = refreshed,
                        message = if (silent) {
                            it.message
                        } else {
                            "Updated chapter list (${refreshed.chapterCount})"
                        },
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (_ui.value.story?.bookId != bookId) return@launch
                _ui.update { it.copy(storyRefreshing = false) }
                val auth = t is PluginException && t.code == PluginErrorCode.AuthRequired
                if (!silent || auth) fail(t, "Could not refresh chapter list")
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

    /**
     * Tap Download: cancel if this story is downloading; else keep-all → full download,
     * otherwise cache level ahead of the saved position.
     */
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
        if (story.keepAll) {
            runDownload(story, "Download failed") { onProgress ->
                val count = books.downloadAllChapters(story.bookId, story.chapterIndex, onProgress)
                "Downloaded $count / ${story.chapterCount} chapters"
            }
        } else {
            runDownload(story, "Download failed") { onProgress ->
                val count = books.downloadAhead(story.bookId, story.chapterIndex, onProgress)
                "Cached $count / ${story.chapterCount} chapters"
            }
        }
    }

    fun openManage() {
        val story = _ui.value.story ?: return
        _ui.update {
            it.copy(
                showManage = true,
                showToc = false,
                cacheLevelDraft = story.cacheLevel.toString(),
                error = null,
            )
        }
    }

    fun closeManage() = _ui.update {
        it.copy(
            showManage = false,
            cacheLevelDraft = it.story?.cacheLevel?.toString() ?: it.cacheLevelDraft,
        )
    }

    fun openToc() = _ui.update { it.copy(showToc = it.story != null, error = null) }

    fun closeToc() = _ui.update { it.copy(showToc = false) }

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

    fun setKeepAll(on: Boolean) {
        val story = _ui.value.story ?: return
        _ui.update { ui -> ui.copy(story = ui.story?.copy(keepAll = on)) }
        viewModelScope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    books.setKeepAll(story.bookId, on)
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update { ui -> if (ui.story?.bookId == story.bookId) ui.copy(story = refreshed) else ui }
            } catch (t: Throwable) {
                fail(t, "Could not update keep-all")
            }
        }
    }

    fun clearLocalDownloads() {
        val story = _ui.value.story ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                val removed = withContext(Dispatchers.IO) {
                    books.clearLocalDownloads(story.bookId)
                    loadStory(story.bookId)
                }
                refreshLocal()
                _ui.update {
                    it.copy(
                        busy = false,
                        story = removed,
                        message = "Cleared downloads for ${story.title}",
                    )
                }
            } catch (t: Throwable) {
                fail(t, "Could not clear downloads")
            }
        }
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
                story = if (it.story?.bookId == story.bookId) refreshed ?: it.story else it.story,
                message = message ?: it.message,
            )
        }
    }

    // --- Position ---------------------------------------------------------------------------

    /** Open the reading-position card (−/+ steps) from the media card progress bar. */
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
                                title = story.pluginTitle,
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
                    PluginSecrets(getApplication(), pluginId).set(
                        PluginSessionSeed.COOKIES_IMPORTED_AT,
                        System.currentTimeMillis().toString(),
                    )
                    source().session()
                }
                if (!session.loggedIn) throw PluginException(PluginErrorCode.AuthRequired, "Sign in failed")
                _ui.update {
                    it.copy(
                        busy = false,
                        session = session,
                        sessionStaleHint = null,
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
                it.copy(
                    session = PluginSession(),
                    loginFields = emptyMap(),
                    syncListId = null,
                    showAccount = false,
                    sessionStaleHint = null,
                )
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
