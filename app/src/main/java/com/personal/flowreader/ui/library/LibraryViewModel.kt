package com.personal.flowreader.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.CustomLibraryTab
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.LibraryPluginRegistry
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadPlugin
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.ShareAction
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.ShareDispatch
import com.personal.flowreader.share.SharePayload
import com.personal.flowreader.share.ShareRouter
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.reader.FilterPreviewMode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUi(
    val books: List<ProgressEntity> = emptyList(),
    val que: List<QueEntry> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.List,
    val tab: LibraryTabId = LibraryTabId.Files,
    val filtersGlobal: List<FilterRule> = emptyList(),
    val filtersGroups: List<FilterRule> = emptyList(),
    val enabledPluginIds: Set<String> = emptySet(),
    val customTabs: List<CustomLibraryTab> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** When set, MainActivity should navigate to the reader for this bookId. */
    val pendingOpenBookId: String? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val tts = flow.tts
    val plugins: LibraryPluginRegistry = flow.plugins
    private val _ui = MutableStateFlow(LibraryUi())
    val ui: StateFlow<LibraryUi> = _ui

    val pluginActions: LibraryPluginActions = object : LibraryPluginActions {
        override fun ingestAndOpen(title: String, text: String) {
            ingestPluginText(title, text, enqueue = false, open = true)
        }

        override fun ingestAndQueue(title: String, text: String) {
            ingestPluginText(title, text, enqueue = true, open = false)
        }

        override fun openBook(bookId: String) {
            _ui.value = _ui.value.copy(
                busy = false,
                error = null,
                pendingOpenBookId = bookId,
            )
        }

        override fun queueBook(bookId: String) {
            viewModelScope.launch {
                _ui.value = _ui.value.copy(busy = true, error = null, message = null)
                try {
                    val row = withContext(Dispatchers.IO) {
                        flow.catalog.enqueueExisting(bookId)
                        flow.db.progress().get(bookId)
                    }
                    val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                    _ui.value = _ui.value.copy(
                        que = que,
                        busy = false,
                        message = "Queued ${row?.title ?: "book"}",
                    )
                } catch (t: Throwable) {
                    _ui.value = _ui.value.copy(
                        busy = false,
                        error = t.message ?: "Could not queue book",
                    )
                }
            }
        }

        override fun setBusy(busy: Boolean) {
            _ui.value = _ui.value.copy(busy = busy, error = if (busy) null else _ui.value.error)
        }

        override fun showMessage(text: String) {
            _ui.value = _ui.value.copy(message = text)
        }

        override fun showError(text: String) {
            _ui.value = _ui.value.copy(error = text)
        }
    }

    init {
        viewModelScope.launch {
            val mode = flow.settings.libraryViewModeOnce()
            val enabled = flow.settings.enabledPluginIdsOnce()
                .intersect(flow.plugins.pluginIds)
            val customTabs = flow.settings.customLibraryTabsOnce()
            val tab = LibraryTabId.parse(
                flow.settings.libraryTabIdOnce(),
                enabled,
                customTabs.map { it.id }.toSet(),
            )
            _ui.value = _ui.value.copy(
                viewMode = mode,
                tab = tab,
                enabledPluginIds = enabled,
                customTabs = customTabs,
            )
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val tab = _ui.value.tab
            val books = withContext(Dispatchers.IO) { booksForTab(tab) }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            _ui.value = _ui.value.copy(
                books = books,
                que = que,
                filtersGlobal = global,
                filtersGroups = groups,
            )
        }
    }

    private suspend fun booksForTab(tab: LibraryTabId): List<ProgressEntity> =
        when (tab) {
            is LibraryTabId.Custom -> flow.catalog.listTab(tab.tabId)
            else -> flow.catalog.list()
        }

    fun setTab(tab: LibraryTabId) {
        _ui.value = _ui.value.copy(tab = tab)
        viewModelScope.launch {
            flow.settings.setLibraryTabId(tab.persistKey)
            val books = withContext(Dispatchers.IO) { booksForTab(tab) }
            _ui.value = _ui.value.copy(books = books)
        }
    }

    fun addCustomTab(title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val next = _ui.value.customTabs + CustomLibraryTab(
                id = id,
                title = name,
                order = _ui.value.customTabs.size,
            )
            withContext(Dispatchers.IO) { flow.settings.setCustomLibraryTabs(next) }
            val tab = LibraryTabId.Custom(id)
            withContext(Dispatchers.IO) { flow.settings.setLibraryTabId(tab.persistKey) }
            _ui.value = _ui.value.copy(
                customTabs = next,
                tab = tab,
                books = emptyList(),
                message = "Added $name",
            )
        }
    }

    fun removeCustomTab(tabId: String) {
        viewModelScope.launch {
            val next = _ui.value.customTabs.filterNot { it.id == tabId }
                .mapIndexed { index, tab -> tab.copy(order = index) }
            withContext(Dispatchers.IO) {
                flow.catalog.clearLibraryTab(tabId)
                flow.settings.setCustomLibraryTabs(next)
                val rules = flow.settings.shareRouterRulesOnce().map { rule ->
                    if (rule.destination.id == tabId) {
                        rule.copy(destination = RouterLanding.Files)
                    } else {
                        rule
                    }
                }
                flow.settings.setShareRouterRules(rules)
            }
            val tab = when (val cur = _ui.value.tab) {
                is LibraryTabId.Custom ->
                    if (cur.tabId == tabId) LibraryTabId.Files else cur
                else -> cur
            }
            withContext(Dispatchers.IO) { flow.settings.setLibraryTabId(tab.persistKey) }
            val books = withContext(Dispatchers.IO) { booksForTab(tab) }
            _ui.value = _ui.value.copy(
                customTabs = next,
                tab = tab,
                books = books,
                message = "Removed tab",
            )
        }
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        if (id !in flow.plugins.pluginIds) return
        val next = if (enabled) {
            _ui.value.enabledPluginIds + id
        } else {
            _ui.value.enabledPluginIds - id
        }
        val tab = when {
            enabled -> LibraryTabId.Plugin(id)
            _ui.value.tab == LibraryTabId.Plugin(id) -> LibraryTabId.Files
            else -> _ui.value.tab
        }
        _ui.value = _ui.value.copy(enabledPluginIds = next, tab = tab)
        viewModelScope.launch {
            flow.settings.setEnabledPluginIds(next)
            flow.settings.setLibraryTabId(tab.persistKey)
        }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _ui.value = _ui.value.copy(viewMode = mode)
        viewModelScope.launch { flow.settings.setLibraryViewMode(mode) }
    }

    /**
     * Handle ACTION_SEND / ACTION_VIEW from the share sheet or "Open with".
     * @return true if the intent was consumed (async work may still be running)
     */
    fun handleIncomingIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        when (intent.action) {
            ShareDispatch.ACTION_EXECUTE -> {
                executeShareIntent(intent)
                return true
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return false
                openExternalUri(uri)
                return true
            }
            Intent.ACTION_SEND -> {
                // Text shares go through ShareIngressActivity; file SEND still lands here.
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) {
                    openExternalUri(stream)
                    return true
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isEmpty()) {
                    _ui.value = _ui.value.copy(error = "Nothing to share")
                    return true
                }
                // Fallback if something still delivers text SEND to MainActivity.
                routeImportText(text)
                return true
            }
            else -> return false
        }
    }

    fun executeShareIntent(intent: Intent) {
        val kind = intent.getStringExtra(ShareDispatch.EXTRA_KIND) ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                when (kind) {
                    ShareDispatch.KIND_FILES -> {
                        val text = intent.getStringExtra(ShareDispatch.EXTRA_TEXT).orEmpty()
                        val title = intent.getStringExtra(ShareDispatch.EXTRA_TITLE)
                        val shelf = intent.getStringExtra(ShareDispatch.EXTRA_LIBRARY_TAB).orEmpty()
                        val result = withContext(Dispatchers.IO) {
                            flow.catalog.addText(
                                text = text,
                                titleHint = title,
                                inLibrary = true,
                                enqueue = false,
                                libraryTabId = shelf,
                            )
                        }
                        refreshAfterIngest(tabForShelf(shelf), "Added ${result.progress.title}")
                    }
                    ShareDispatch.KIND_QUEUE -> {
                        val text = intent.getStringExtra(ShareDispatch.EXTRA_TEXT).orEmpty()
                        val title = intent.getStringExtra(ShareDispatch.EXTRA_TITLE)
                        val result = withContext(Dispatchers.IO) {
                            flow.catalog.addText(
                                text = text,
                                titleHint = title,
                                inLibrary = false,
                                enqueue = true,
                            )
                        }
                        refreshAfterIngest(LibraryTabId.Que, "Queued ${result.progress.title}")
                    }
                    ShareDispatch.KIND_CRAWL -> {
                        val url = intent.getStringExtra(ShareDispatch.EXTRA_URL).orEmpty()
                        val landing = RouterLanding.parse(
                            intent.getStringExtra(ShareDispatch.EXTRA_LANDING),
                            RouterLanding.Queue,
                        )
                        val contentCss = intent.getStringExtra(ShareDispatch.EXTRA_SELECTOR)
                        val titleCss = intent.getStringExtra(ShareDispatch.EXTRA_TITLE_CSS)
                        val removeCss = intent.getStringExtra(ShareDispatch.EXTRA_REMOVE_CSS)
                        val article = withContext(Dispatchers.IO) {
                            WebPageIngest.fetchArticle(url, contentCss, titleCss, removeCss)
                        }
                        val result = withContext(Dispatchers.IO) {
                            if (landing.isQueue) {
                                flow.catalog.addText(
                                    text = article.text,
                                    titleHint = article.title,
                                    inLibrary = false,
                                    enqueue = true,
                                )
                            } else {
                                flow.catalog.addText(
                                    text = article.text,
                                    titleHint = article.title,
                                    inLibrary = true,
                                    enqueue = false,
                                    libraryTabId = landing.libraryShelfId,
                                )
                            }
                        }
                        val tab = tabForLanding(landing)
                        val msg = if (landing.isQueue) {
                            "Queued ${result.progress.title}"
                        } else {
                            "Added ${result.progress.title}"
                        }
                        refreshAfterIngest(tab, msg)
                    }
                    ShareDispatch.KIND_RR_PLUGIN -> {
                        val url = intent.getStringExtra(ShareDispatch.EXTRA_URL).orEmpty()
                        flow.pendingRoyalRoadShareUrl.value = url
                        val enabled = withContext(Dispatchers.IO) {
                            flow.settings.enabledPluginIdsOnce()
                        }.toMutableSet().also { it.add(RoyalRoadPlugin.ID) }
                        withContext(Dispatchers.IO) {
                            flow.settings.setEnabledPluginIds(enabled)
                            flow.settings.setLibraryTabId(RoyalRoadPlugin.ID)
                        }
                        _ui.value = _ui.value.copy(
                            enabledPluginIds = enabled,
                            tab = LibraryTabId.Plugin(RoyalRoadPlugin.ID),
                            busy = false,
                            message = "Opening Royal Road…",
                        )
                    }
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not handle shared content",
                )
            }
        }
    }

    /** Clipboard / fallback text: same Import → Router path as share. */
    fun routeImportText(text: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val prefs = withContext(Dispatchers.IO) { flow.settings.shareOnce() }
                val routerRules = withContext(Dispatchers.IO) { flow.settings.shareRouterRulesOnce() }
                val parseRules = withContext(Dispatchers.IO) { flow.settings.shareParseRulesOnce() }
                // In-app has no overlay chooser — always Auto for clipboard / fallback.
                val action = ShareRouter.decide(
                    SharePayload(text = text),
                    prefs.copy(askMode = ShareAskMode.Auto),
                    routerRules,
                    parseRules,
                )
                when (action) {
                    is ShareAction.ToFiles -> {
                        val result = withContext(Dispatchers.IO) {
                            flow.catalog.addText(
                                text = action.text,
                                titleHint = action.titleHint,
                                inLibrary = true,
                                enqueue = false,
                                libraryTabId = action.libraryTabId,
                            )
                        }
                        refreshAfterIngest(
                            tabForShelf(action.libraryTabId),
                            "Added ${result.progress.title}",
                        )
                    }
                    is ShareAction.ToQueue -> {
                        val result = withContext(Dispatchers.IO) {
                            flow.catalog.addText(
                                text = action.text,
                                titleHint = action.titleHint,
                                inLibrary = false,
                                enqueue = true,
                            )
                        }
                        refreshAfterIngest(LibraryTabId.Que, "Queued ${result.progress.title}")
                    }
                    is ShareAction.Crawl -> {
                        val (content, titleCss, remove) = ParseRules.effectiveSelectors(action.rule)
                        val article = withContext(Dispatchers.IO) {
                            WebPageIngest.fetchArticle(action.url, content, titleCss, remove)
                        }
                        val landing = action.landing
                        val result = withContext(Dispatchers.IO) {
                            if (landing.isQueue) {
                                flow.catalog.addText(
                                    text = article.text,
                                    titleHint = article.title,
                                    inLibrary = false,
                                    enqueue = true,
                                )
                            } else {
                                flow.catalog.addText(
                                    text = article.text,
                                    titleHint = article.title,
                                    inLibrary = true,
                                    enqueue = false,
                                    libraryTabId = landing.libraryShelfId,
                                )
                            }
                        }
                        val tab = tabForLanding(landing)
                        val msg = if (landing.isQueue) {
                            "Queued ${result.progress.title}"
                        } else {
                            "Added ${result.progress.title}"
                        }
                        refreshAfterIngest(tab, msg)
                    }
                    is ShareAction.RoyalRoadPlugin -> {
                        flow.pendingRoyalRoadShareUrl.value = action.url
                        val enabled = withContext(Dispatchers.IO) {
                            flow.settings.enabledPluginIdsOnce()
                        }.toMutableSet().also { it.add(RoyalRoadPlugin.ID) }
                        withContext(Dispatchers.IO) {
                            flow.settings.setEnabledPluginIds(enabled)
                            flow.settings.setLibraryTabId(RoyalRoadPlugin.ID)
                        }
                        _ui.value = _ui.value.copy(
                            enabledPluginIds = enabled,
                            tab = LibraryTabId.Plugin(RoyalRoadPlugin.ID),
                            busy = false,
                            message = "Opening Royal Road…",
                        )
                    }
                    is ShareAction.ShowChooser -> error("Auto mode must not show chooser")
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not import",
                )
            }
        }
    }

    private suspend fun refreshAfterIngest(tab: LibraryTabId, message: String) {
        val books = withContext(Dispatchers.IO) { booksForTab(tab) }
        val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
        withContext(Dispatchers.IO) { flow.settings.setLibraryTabId(tab.persistKey) }
        _ui.value = _ui.value.copy(
            books = books,
            que = que,
            tab = tab,
            busy = false,
            message = message,
        )
    }

    private fun tabForLanding(landing: RouterLanding): LibraryTabId =
        when {
            landing.isQueue -> LibraryTabId.Que
            landing.libraryShelfId.isNotEmpty() -> LibraryTabId.Custom(landing.libraryShelfId)
            else -> LibraryTabId.Files
        }

    private fun tabForShelf(libraryTabId: String): LibraryTabId =
        if (libraryTabId.isBlank()) LibraryTabId.Files else LibraryTabId.Custom(libraryTabId)

    private fun shelfForAdd(): String =
        (_ui.value.tab as? LibraryTabId.Custom)?.tabId.orEmpty()

    /** Import or link an external book URI, then request the reader to open it. */
    fun openExternalUri(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val routerRules = withContext(Dispatchers.IO) { flow.settings.shareRouterRulesOnce() }
                val landing = ShareRouter.bookFileLanding(routerRules)
                val shelf = if (landing.isLibrary) landing.libraryShelfId else ""
                val row = withContext(Dispatchers.IO) {
                    try {
                        flow.catalog.add(uri, BookSource.Linked, shelf)
                    } catch (_: Throwable) {
                        flow.catalog.add(uri, BookSource.Imported, shelf)
                    }
                }
                if (landing.isQueue) {
                    withContext(Dispatchers.IO) { flow.catalog.enqueueExisting(row.bookId) }
                    val books = withContext(Dispatchers.IO) { booksForTab(LibraryTabId.Que) }
                    val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                    flow.settings.setLibraryTabId(LibraryTabId.Que.persistKey)
                    _ui.value = _ui.value.copy(
                        books = books,
                        que = que,
                        tab = LibraryTabId.Que,
                        busy = false,
                        message = "Queued ${row.title}",
                        pendingOpenBookId = row.bookId,
                    )
                } else {
                    val tab = tabForLanding(landing)
                    val books = withContext(Dispatchers.IO) { booksForTab(tab) }
                    flow.settings.setLibraryTabId(tab.persistKey)
                    _ui.value = _ui.value.copy(
                        books = books,
                        tab = tab,
                        busy = false,
                        message = "Opened ${row.title}",
                        pendingOpenBookId = row.bookId,
                    )
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open file",
                )
            }
        }
    }

    fun consumePendingOpen() {
        _ui.value = _ui.value.copy(pendingOpenBookId = null)
    }

    private fun ingestPluginText(title: String, text: String, enqueue: Boolean, open: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    flow.catalog.addText(
                        text = text,
                        displayTitle = title,
                        inLibrary = false,
                        enqueue = enqueue,
                    )
                }
                val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                val tab = if (enqueue) LibraryTabId.Que else _ui.value.tab
                if (enqueue) {
                    flow.settings.setLibraryTabId(tab.persistKey)
                }
                val msg = if (enqueue) {
                    "Queued ${result.progress.title}"
                } else {
                    "Opened ${result.progress.title}"
                }
                _ui.value = _ui.value.copy(
                    que = que,
                    tab = tab,
                    busy = false,
                    message = msg,
                    pendingOpenBookId = if (open) result.progress.bookId else null,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not import plugin text",
                )
            }
        }
    }

    fun ingestSharedText(text: String, toQue: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    flow.catalog.addText(
                        text = text,
                        inLibrary = !toQue,
                        enqueue = toQue,
                    )
                }
                val books = withContext(Dispatchers.IO) { flow.catalog.list() }
                val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                val tab = if (toQue) LibraryTabId.Que else LibraryTabId.Files
                flow.settings.setLibraryTabId(tab.persistKey)
                val msg = if (toQue) {
                    "Queued ${result.progress.title}"
                } else {
                    "Added ${result.progress.title}"
                }
                _ui.value = _ui.value.copy(
                    books = books,
                    que = que,
                    tab = tab,
                    busy = false,
                    message = msg,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not import shared text",
                )
            }
        }
    }

    /** Queue / route clipboard text through the Import router. */
    fun queueFromClipboard(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _ui.value = _ui.value.copy(error = "Clipboard is empty")
            return
        }
        routeImportText(trimmed)
    }

    fun removeQue(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { flow.catalog.removeQue(id) }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            val books = withContext(Dispatchers.IO) { booksForTab(_ui.value.tab) }
            _ui.value = _ui.value.copy(que = que, books = books)
        }
    }

    fun removeFromLibrary(bookId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) { flow.catalog.removeFromLibrary(bookId) }
                val books = withContext(Dispatchers.IO) { booksForTab(_ui.value.tab) }
                val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                _ui.value = _ui.value.copy(
                    books = books,
                    que = que,
                    busy = false,
                    message = "Removed from library",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not remove book",
                )
            }
        }
    }

    fun addFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal + rule.copy(order = nextOrder(_ui.value.filtersGlobal))
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups + rule.copy(order = nextOrder(_ui.value.filtersGroups))
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun updateFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.map { if (it.id == rule.id) rule else it }
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map { if (it.id == rule.id) rule else it }
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun setFilterEnabled(scope: FilterScope, id: String, enabled: Boolean) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun deleteFilter(scope: FilterScope, id: String) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> persistGlobal(_ui.value.filtersGlobal.filterNot { it.id == id })
                FilterScope.Groups -> persistGroups(_ui.value.filtersGroups.filterNot { it.id == id })
                FilterScope.Local -> Unit
            }
        }
    }

    fun previewApply(
        sample: String,
        draft: FilterRule,
        scope: FilterScope,
        mode: FilterPreviewMode,
    ): FilterApplyResult {
        val rules = when (mode) {
            FilterPreviewMode.Original -> emptyList()
            FilterPreviewMode.ThisRule -> listOf(draft.copy(enabled = true, order = 0))
            FilterPreviewMode.AllEnabled -> {
                val global = _ui.value.filtersGlobal
                val groups = _ui.value.filtersGroups
                val withDraft: (List<FilterRule>) -> List<FilterRule> = { list ->
                    list.map { if (it.id == draft.id) draft else it }.let { mapped ->
                        if (mapped.any { it.id == draft.id }) mapped else mapped + draft
                    }
                }
                when (scope) {
                    FilterScope.Global -> TextFilters.merge(withDraft(global), groups, emptyList())
                    FilterScope.Groups -> TextFilters.merge(global, withDraft(groups), emptyList())
                    FilterScope.Local -> TextFilters.merge(global, groups, listOf(draft))
                }
            }
        }
        return TextFilters.apply(sample, rules)
    }

    private suspend fun persistGlobal(rules: List<FilterRule>) {
        flow.settings.setGlobalFilters(rules)
        _ui.value = _ui.value.copy(filtersGlobal = rules)
    }

    private suspend fun persistGroups(rules: List<FilterRule>) {
        flow.settings.setGroupFilters(rules)
        _ui.value = _ui.value.copy(filtersGroups = rules)
    }

    private fun nextOrder(rules: List<FilterRule>): Int =
        (rules.maxOfOrNull { it.order } ?: -1) + 1

    fun add(uri: Uri, source: BookSource) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val routerRules = withContext(Dispatchers.IO) { flow.settings.shareRouterRulesOnce() }
                val landing = ShareRouter.bookFileLanding(routerRules)
                // Manual add from a custom shelf stays on that shelf; otherwise follow router.
                val shelf = when {
                    landing.isQueue -> ""
                    shelfForAdd().isNotEmpty() -> shelfForAdd()
                    else -> landing.libraryShelfId
                }
                val row = withContext(Dispatchers.IO) {
                    flow.catalog.add(uri, source, shelf)
                }
                if (landing.isQueue && shelfForAdd().isEmpty()) {
                    withContext(Dispatchers.IO) { flow.catalog.enqueueExisting(row.bookId) }
                    val books = withContext(Dispatchers.IO) { booksForTab(_ui.value.tab) }
                    val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                    _ui.value = _ui.value.copy(
                        books = books,
                        que = que,
                        tab = LibraryTabId.Que,
                        busy = false,
                        message = "Queued ${row.title}",
                    )
                } else {
                    val tab = if (shelf.isNotEmpty()) {
                        LibraryTabId.Custom(shelf)
                    } else {
                        _ui.value.tab
                    }
                    val books = withContext(Dispatchers.IO) { booksForTab(tab) }
                    val verb = if (source == BookSource.Linked) "Linked" else "Imported"
                    _ui.value = _ui.value.copy(
                        books = books,
                        tab = tab,
                        busy = false,
                        message = "$verb ${row.title}",
                    )
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not add file",
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(error = null)
    }
}
