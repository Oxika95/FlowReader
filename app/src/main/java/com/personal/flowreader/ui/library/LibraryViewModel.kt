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
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.data.QueueItemEntity
import com.personal.flowreader.data.TextIngestResult
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.PluginShareRequest
import com.personal.flowreader.plugin.updates.UpdateNotifier
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.ShareParseMode
import com.personal.flowreader.share.WebCrawl
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
    val books: List<BookItem> = emptyList(),
    val que: List<QueueItemEntity> = emptyList(),
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
    /** When set, MainActivity should open the Queue stream at this Queue entry. */
    val pendingOpenQueId: String? = null,
    /** A shared page whose rule has a Next selector: ask before crawling. */
    val crawlPrompt: WebImportRequest? = null,
    val crawlProgress: CrawlProgress? = null,
)

data class WebImportRequest(
    val url: String,
    val rule: ParseRule,
    val landing: RouterLanding,
    val autoPlay: Boolean,
)

data class CrawlProgress(
    val pages: Int,
    val limit: Int,
    val lastTitle: String,
    val stopping: Boolean = false,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val tts = flow.tts
    val plugins: StateFlow<List<InstalledPlugin>> = flow.pluginManager.installed
    private val _ui = MutableStateFlow(LibraryUi())
    val ui: StateFlow<LibraryUi> = _ui

    val pluginActions: LibraryPluginActions = object : LibraryPluginActions {
        override fun openBook(bookId: String) {
            _ui.value = _ui.value.copy(
                busy = false,
                error = null,
                pendingOpenBookId = bookId,
            )
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
                .intersect(flow.pluginManager.ids())
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

    private suspend fun booksForTab(tab: LibraryTabId): List<BookItem> =
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
        if (id !in flow.pluginManager.ids()) return
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
            UpdateNotifier.ACTION_OPEN_STORY -> {
                val pluginId = intent.getStringExtra(UpdateNotifier.EXTRA_PLUGIN_ID).orEmpty()
                val bookId = intent.getStringExtra(UpdateNotifier.EXTRA_BOOK_ID).orEmpty()
                if (pluginId.isNotBlank() && bookId.isNotBlank()) {
                    viewModelScope.launch { openPluginTab(PluginShareRequest(pluginId, bookId = bookId)) }
                }
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
                        refreshAfterIngest(tabForShelf(shelf), "Added ${result.title}")
                        autoPlay(result)
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
                        refreshAfterIngest(LibraryTabId.Que, "Queued ${result.title}")
                        autoPlay(result)
                    }
                    ShareDispatch.KIND_CRAWL -> {
                        val url = intent.getStringExtra(ShareDispatch.EXTRA_URL).orEmpty()
                        val landing = RouterLanding.parse(
                            intent.getStringExtra(ShareDispatch.EXTRA_LANDING),
                            RouterLanding.Queue,
                        )
                        startWebImport(WebImportRequest(url, parseRuleFor(intent), landing, autoPlay = true))
                    }
                    ShareDispatch.KIND_PLUGIN -> {
                        val url = intent.getStringExtra(ShareDispatch.EXTRA_URL).orEmpty()
                        val pluginId = intent.getStringExtra(ShareDispatch.EXTRA_PLUGIN_ID)
                        if (!pluginId.isNullOrBlank()) openPluginShare(pluginId, url)
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
                            "Added ${result.title}",
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
                        refreshAfterIngest(LibraryTabId.Que, "Queued ${result.title}")
                    }
                    is ShareAction.Crawl ->
                        startWebImport(WebImportRequest(action.url, action.rule, action.landing, autoPlay = false))
                    is ShareAction.Plugin -> openPluginShare(action.pluginId, action.url)
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

    /** Enable and switch to the plugin tab; the tab consumes [FlowApp.pendingPluginShare]. */
    private suspend fun openPluginShare(pluginId: String, url: String) =
        openPluginTab(PluginShareRequest(pluginId, url))

    private suspend fun openPluginTab(request: PluginShareRequest) {
        val pluginId = request.pluginId
        val plugin = flow.pluginManager.get(pluginId)
        if (plugin == null) {
            _ui.value = _ui.value.copy(
                busy = false,
                error = "The plugin for this link is not installed. Install it from Settings > Plugins.",
            )
            return
        }
        flow.pendingPluginShare.value = request
        val enabled = withContext(Dispatchers.IO) {
            flow.settings.enabledPluginIdsOnce()
        }.toMutableSet().also { it.add(pluginId) }
        withContext(Dispatchers.IO) {
            flow.settings.setEnabledPluginIds(enabled)
            flow.settings.setLibraryTabId(pluginId)
        }
        _ui.value = _ui.value.copy(
            enabledPluginIds = enabled,
            tab = LibraryTabId.Plugin(pluginId),
            busy = false,
            message = "Opening ${plugin.name}…",
        )
    }

    private suspend fun parseRuleFor(intent: Intent): ParseRule {
        val id = intent.getStringExtra(ShareDispatch.EXTRA_PARSE_RULE_ID)
        if (!id.isNullOrBlank()) {
            withContext(Dispatchers.IO) { flow.settings.shareParseRulesOnce() }
                .firstOrNull { it.id == id }
                ?.let { return it }
        }
        val body = intent.getStringExtra(ShareDispatch.EXTRA_SELECTOR)
        return ParseRule(
            hostPattern = "",
            parseMode = if (body.isNullOrBlank()) ShareParseMode.Default else ShareParseMode.Custom,
            contentCss = body,
            titleCss = intent.getStringExtra(ShareDispatch.EXTRA_TITLE_CSS),
            removeCss = intent.getStringExtra(ShareDispatch.EXTRA_REMOVE_CSS),
        )
    }

    /** Rules with a Next selector ask first: this page only, or crawl into one book. */
    private suspend fun startWebImport(request: WebImportRequest) {
        if (ParseRules.canCrawl(request.rule)) {
            _ui.value = _ui.value.copy(busy = false, crawlPrompt = request)
            return
        }
        importWebPage(request)
    }

    fun answerCrawlPrompt(crawl: Boolean) {
        val request = _ui.value.crawlPrompt ?: return
        _ui.value = _ui.value.copy(crawlPrompt = null, busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                if (crawl) crawlWeb(request) else importWebPage(request)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(busy = false, crawlProgress = null, error = t.message ?: "Could not import")
            }
        }
    }

    fun dismissCrawlPrompt() {
        _ui.value = _ui.value.copy(crawlPrompt = null)
    }

    @Volatile
    private var crawlStopRequested = false

    fun stopCrawl() {
        crawlStopRequested = true
        _ui.value.crawlProgress?.let { _ui.value = _ui.value.copy(crawlProgress = it.copy(stopping = true)) }
    }

    private suspend fun importWebPage(request: WebImportRequest) {
        val selectors = ParseRules.effectiveSelectors(request.rule)
        val desktop = request.rule.desktop
        val article = withContext(Dispatchers.IO) {
            WebPageIngest.fetchArticle(request.url, selectors, desktop, request.rule.stripHidden)
        }
        val landing = request.landing
        if (selectors != null) {
            val saved = withContext(Dispatchers.IO) {
                val cover = article.coverUrl?.let { WebPageIngest.fetchCover(it, request.url, desktop) }
                flow.catalog.addEpub(
                    bytes = WebCrawl.toEpub(listOf(article), request.url, cover),
                    title = article.title,
                    inLibrary = !landing.isQueue,
                    enqueue = landing.isQueue,
                    libraryTabId = landing.libraryShelfId,
                    sourceUrl = request.url,
                )
            }
            finishWebImport(request, saved, saved.title)
            return
        }
        val result = withContext(Dispatchers.IO) {
            flow.catalog.addText(
                text = article.text,
                titleHint = article.title,
                inLibrary = !landing.isQueue,
                enqueue = landing.isQueue,
                libraryTabId = landing.libraryShelfId,
                sourceUrl = request.url,
            )
        }
        finishWebImport(request, result, result.title)
    }

    private suspend fun crawlWeb(request: WebImportRequest) {
        val selectors = ParseRules.effectiveSelectors(request.rule) ?: return importWebPage(request)
        val limit = request.rule.crawlLimit
        crawlStopRequested = false
        _ui.value = _ui.value.copy(crawlProgress = CrawlProgress(0, limit, ""))
        try {
            val desktop = request.rule.desktop
            val crawl = WebCrawl(
                selectors,
                limit,
                fetchHtml = { WebPageIngest.fetchHtml(it, desktop) },
                stripHidden = request.rule.stripHidden,
            )
            val result = withContext(Dispatchers.IO) {
                crawl.run(request.url, stopRequested = { crawlStopRequested }) { count, page ->
                    val current = _ui.value.crawlProgress ?: return@run
                    _ui.value = _ui.value.copy(crawlProgress = current.copy(pages = count, lastTitle = page.title))
                }
            }
            val title = WebCrawl.bookTitle(result.pages)
            val landing = request.landing
            val saved = withContext(Dispatchers.IO) {
                val cover = result.pages.first().coverUrl?.let { WebPageIngest.fetchCover(it, request.url, desktop) }
                flow.catalog.addEpub(
                    bytes = WebCrawl.toEpub(result.pages, request.url, cover),
                    title = title,
                    inLibrary = !landing.isQueue,
                    enqueue = landing.isQueue,
                    libraryTabId = landing.libraryShelfId,
                    sourceUrl = request.url,
                )
            }
            val count = result.pages.size
            val label = "${saved.title} ($count ${if (count == 1) "chapter" else "chapters"})"
            finishWebImport(request, saved, label)
            result.error?.let { _ui.value = _ui.value.copy(error = "Crawl stopped early: $it") }
        } finally {
            _ui.value = _ui.value.copy(crawlProgress = null)
        }
    }

    private suspend fun finishWebImport(request: WebImportRequest, result: TextIngestResult, label: String) {
        val landing = request.landing
        refreshAfterIngest(tabForLanding(landing), if (landing.isQueue) "Queued $label" else "Added $label")
        if (request.autoPlay) autoPlay(result)
    }

    /**
     * Auto Play on Share: open the shared item in the reader, which starts TTS once loaded. A
     * queued share opens the Queue at its entry.
     */
    private fun autoPlay(result: TextIngestResult) {
        val state = tts.state.value
        if (!state.autoPlayOnShare || (state.playing && !state.shareInterruptsPlayback)) return
        val queId = result.queItem?.queId
        if (queId != null) {
            flow.pendingSharePlay = queId
            _ui.value = _ui.value.copy(pendingOpenQueId = queId)
        } else {
            flow.pendingSharePlay = result.bookId
            _ui.value = _ui.value.copy(pendingOpenBookId = result.bookId)
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
                    val item = withContext(Dispatchers.IO) { flow.catalog.enqueueExisting(row.bookId) }
                    val books = withContext(Dispatchers.IO) { booksForTab(LibraryTabId.Que) }
                    val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                    flow.settings.setLibraryTabId(LibraryTabId.Que.persistKey)
                    _ui.value = _ui.value.copy(
                        books = books,
                        que = que,
                        tab = LibraryTabId.Que,
                        busy = false,
                        message = "Queued ${row.title}",
                        pendingOpenQueId = item.queId,
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

    fun consumePendingOpenQue() {
        _ui.value = _ui.value.copy(pendingOpenQueId = null)
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

    fun removeQue(ids: Set<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { flow.catalog.removeQue(it) } }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            val books = withContext(Dispatchers.IO) { booksForTab(_ui.value.tab) }
            _ui.value = _ui.value.copy(que = que, books = books)
        }
    }

    /** Queue row ids in their new order. */
    fun reorderQue(ids: List<String>) {
        val byId = _ui.value.que.associateBy { it.queId }
        _ui.value = _ui.value.copy(que = ids.mapNotNull { byId[it] })
        viewModelScope.launch {
            withContext(Dispatchers.IO) { flow.catalog.reorderQue(ids) }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            _ui.value = _ui.value.copy(que = que)
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

    fun deleteFilters(scope: FilterScope, ids: Set<String>) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> persistGlobal(_ui.value.filtersGlobal.filterNot { it.id in ids })
                FilterScope.Groups -> persistGroups(_ui.value.filtersGroups.filterNot { it.id in ids })
                FilterScope.Local -> Unit
            }
        }
    }

    fun reorderFilters(scope: FilterScope, ids: List<String>) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> persistGlobal(TextFilters.reorder(_ui.value.filtersGlobal, ids))
                FilterScope.Groups -> persistGroups(TextFilters.reorder(_ui.value.filtersGroups, ids))
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
        tts.setSpeechFilters(rules, _ui.value.filtersGroups)
    }

    private suspend fun persistGroups(rules: List<FilterRule>) {
        flow.settings.setGroupFilters(rules)
        _ui.value = _ui.value.copy(filtersGroups = rules)
        tts.setSpeechFilters(_ui.value.filtersGlobal, rules)
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
