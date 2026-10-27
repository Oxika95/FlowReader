package com.personal.flowreader.ui.plugin

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.TabStyle
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.updates.ActiveChapterNotifications
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.FlowIcons
import com.personal.flowreader.ui.design.controls.FlowBadgeTone
import com.personal.flowreader.ui.design.controls.FlowFab
import com.personal.flowreader.ui.design.layer.FlowPullRefresh
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.library.LibraryBooksPane
import com.personal.flowreader.ui.library.LibraryRowText
import com.personal.flowreader.ui.library.LibrarySort
import com.personal.flowreader.ui.library.LibrarySortOverlay
import com.personal.flowreader.ui.theme.FlowTokens

/** One view model per installed plugin version, shared by the tab body and its overlays. */
@Composable
fun rememberPluginTabViewModel(plugin: InstalledPlugin): PluginTabViewModel {
    val app = LocalContext.current.applicationContext as Application
    return viewModel(
        key = "plugin-tab:${plugin.id}:${plugin.manifest.version}",
        factory = PluginTabViewModel.factory(app, plugin.id),
    )
}

/** Library tab body for any plugin: secondary tabs (lists, Search, Account) + list/search pane. */
@Composable
fun PluginTabContent(
    plugin: InstalledPlugin,
    actions: LibraryPluginActions,
    viewMode: LibraryViewMode,
    modifier: Modifier = Modifier,
    bottomInset: Dp = FlowTokens.Space.None,
) {
    val vm = rememberPluginTabViewModel(plugin)
    val ui by vm.ui.collectAsState()
    val app = LocalContext.current.applicationContext as FlowApp
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingShare by app.pendingPluginShare.collectAsState()
    val chrome by app.chrome.collectAsState()
    val notifyBookIds by ActiveChapterNotifications.bookIds.collectAsState()
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshLocal()
            vm.refreshSessionPublic()
        }
    }
    LaunchedEffect(pendingShare) {
        val share = pendingShare?.takeIf { it.pluginId == plugin.id } ?: return@LaunchedEffect
        app.pendingPluginShare.value = null
        if (share.bookId != null) vm.openStory(share.bookId) else vm.openUrl(share.url)
    }
    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        actions.showMessage(msg)
        vm.consumeMessage()
    }
    LaunchedEffect(ui.error) {
        val err = ui.error ?: return@LaunchedEffect
        // Always toast; Account sheet also shows the hint when open (errors used to be silent under overlays).
        actions.showError(err)
        if (!ui.showAccount) vm.consumeError()
    }

    Column(modifier.fillMaxSize()) {
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        PluginSubTabs(
            ui = ui,
            text = chrome.libraryTabs == TabStyle.Text,
            notifyBookIds = notifyBookIds,
            onSection = vm::setSection,
            onAccount = { vm.setShowAccount(true) },
        )
        when (val section = ui.section) {
            is PluginSection.Library -> {
                val isBrowse = ui.manifest.list(section.listId)?.isBrowse == true
                if (isBrowse) {
                    FlowPullRefresh(
                        refreshing = ui.refreshing,
                        onRefresh = vm::refresh,
                        modifier = Modifier.weight(1f),
                    ) {
                        BrowseListPane(
                            ui = ui,
                            listId = section.listId,
                            onOpen = { row -> vm.openBrowse(section.listId, row) },
                            bottomInset = bottomInset,
                        )
                    }
                } else {
                    val listTitle = ui.manifest.list(section.listId)?.title ?: section.listId
                    LibrarySortOverlay(
                        modes = LibrarySort.pluginModes,
                        selected = ui.sortMode,
                        onSelect = vm::setSortMode,
                        bottomInset = bottomInset,
                        modifier = Modifier.weight(1f),
                    ) {
                        FlowPullRefresh(
                            refreshing = ui.refreshing,
                            onRefresh = vm::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LibraryBooksPane(
                                books = ui.visibleBooks,
                                viewMode = viewMode,
                                busy = ui.busy || ui.listsLoading,
                                emptyMessage = when {
                                    ui.books.isEmpty() -> "No stories yet.\nTap + to add one" +
                                        if (ui.manifest.auth != null) {
                                            ", or sign in to import a list."
                                        } else {
                                            "."
                                        }
                                    else -> "No stories in $listTitle."
                                },
                                onOpen = vm::openStory,
                                onDoubleOpen = { bookId -> vm.readBook(actions, bookId) },
                                badgesFor = { book ->
                                    if (book.bookId in ui.sync.conflictBookIds) {
                                        listOf(POSITION_CONFLICT_BADGE to FlowBadgeTone.Negative)
                                    } else {
                                        emptyList()
                                    }
                                },
                                bottomInset = bottomInset,
                                authorFor = { book ->
                                    val meta = ui.libraryMeta[book.bookId]
                                    val row = ui.listRows[book.bookId]
                                    meta?.author?.ifBlank { null }
                                        ?: row?.author?.ifBlank { null }
                                        ?: book.author.ifBlank { ui.manifest.name }
                                },
                                chapterCountFor = { book ->
                                    ui.libraryMeta[book.bookId]?.chapterCount?.takeIf { it > 0 }
                                        ?: book.chapterCount
                                },
                                unreadNewFor = { book ->
                                    val chapters = ui.libraryMeta[book.bookId]?.chapterCount?.takeIf { it > 0 }
                                        ?: book.chapterCount
                                    LibraryRowText.unreadNew(chapters, book.chapterIndex)
                                },
                                notifyDotFor = { book -> book.bookId in notifyBookIds },
                            )
                        }
                    }
                }
            }
            PluginSection.Search -> PluginSearchPane(
                ui = ui,
                onQuery = vm::setQuery,
                onSearch = vm::search,
                onOpen = vm::openWork,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The plugin tab's FAB (Search section, or Add-by-link when search is absent). */
@Composable
fun PluginTabFab(plugin: InstalledPlugin) {
    val vm = rememberPluginTabViewModel(plugin)
    val ui by vm.ui.collectAsState()
    val canSearch = ui.manifest.has(PluginCapability.Search)
    val canResolve = ui.manifest.has(PluginCapability.ResolveUrl)
    if (!canSearch && !canResolve) return
    if (canSearch) {
        FlowFab(
            icon = Icons.Filled.Search,
            contentDescription = "Search ${ui.manifest.name}",
            onClick = { if (!ui.busy) vm.setSection(PluginSection.Search) },
        )
    } else {
        FlowFab(
            icon = Icons.Filled.Add,
            contentDescription = "Add story",
            onClick = { if (!ui.busy) vm.setShowAdd(true) },
        )
    }
}

@Composable
private fun PluginSubTabs(
    ui: PluginTabUi,
    text: Boolean,
    notifyBookIds: Set<String>,
    onSection: (PluginSection) -> Unit,
    onAccount: () -> Unit,
) {
    val manifest = ui.manifest
    val trailing: Pair<ImageVector, String>? = when {
        manifest.auth != null -> Icons.Filled.Person to if (ui.session.loggedIn) "Account" else "Sign in"
        manifest.settings.isNotEmpty() -> Icons.Filled.Settings to "Plugin settings"
        else -> null
    }
    val tabs = buildList {
        manifest.lists.forEach { list ->
            val section = PluginSection.Library(list.id)
            val selected = !ui.showAccount && ui.section == section
            val members = ui.listMembers[list.id].orEmpty()
            val badge = if (members.any { it in notifyBookIds }) 1 else null
            add(
                if (text || list.icon.isBlank()) {
                    FlowTab.text(list.title, selected = selected, onClick = { onSection(section) }, key = list.id, badge = badge)
                } else {
                    FlowTab.icon(FlowIcons.forToken(list.icon), list.title, selected, { onSection(section) }, key = list.id, badge = badge)
                },
            )
        }
        if (trailing != null) {
            add(
                FlowTab.action(
                    trailing.first,
                    trailing.second,
                    open = ui.showAccount,
                    onClick = onAccount,
                ),
            )
        }
    }
    if (tabs.isNotEmpty()) FlowTabBar(tabs = tabs, level = FlowTabLevel.Secondary)
}

/** Plugin cards (add, media, download, account, sign in, sync, settings); they stack in the window overlay. */
@Composable
fun PluginTabOverlays(plugin: InstalledPlugin, actions: LibraryPluginActions) {
    val vm = rememberPluginTabViewModel(plugin)
    val ui by vm.ui.collectAsState()

    AddFromSourceSheet(
        ui = ui,
        onDismiss = { vm.setShowAdd(false) },
        onUrl = vm::setUrlDraft,
        onOpenUrl = { vm.openUrl() },
    )
    CreatorPageOverlay(
        ui = ui,
        actions = remember(vm) {
            CreatorPageActions(
                onDismiss = vm::closeBrowse,
                onTab = vm::setBrowseTab,
                onSort = vm::setBrowseSort,
                onMore = vm::browseMore,
                onPostsOrder = vm::setBrowsePostsOrder,
                onOpen = vm::openWork,
                onOpenStory = vm::openBrowseStory,
            )
        },
    )
    PluginStoryOverlays(vm = vm, onRead = { vm.readStory(actions) })
    AccountSheet(
        ui = ui,
        onDismiss = { vm.setShowAccount(false) },
        onShowLogin = { vm.setShowLogin(true) },
        onLogout = vm::logout,
        onOpenSite = vm::openSite,
        onSync = { vm.askSync(it) },
        onSyncNow = vm::syncNow,
        onDeleteData = vm::eraseLocalData,
    )
    SiteWebViewCard(
        visible = ui.showSiteWeb,
        title = ui.siteWebTitle.ifBlank { ui.manifest.name },
        startUrl = ui.siteWebUrl,
        allowedHosts = ui.manifest.allowedHosts,
        seedCookies = ui.siteSeedCookies,
        busy = ui.busy,
        onDone = vm::completeSiteWeb,
        onDismiss = vm::dismissSiteWeb,
    )
    PluginListCrawlCard(
        crawl = ui.listCrawl,
        pluginName = ui.manifest.name,
        allowedHosts = ui.manifest.allowedHosts,
        onParse = vm::parseCrawledListPage,
        onFinished = vm::finishListCrawl,
        onCancel = vm::cancelListCrawl,
        onError = vm::onListCrawlError,
    )
    LoginSheet(
        ui = ui,
        onField = vm::setLoginField,
        onSubmit = vm::login,
        onWebSignedIn = vm::completeWebLogin,
        onDismiss = { vm.setShowLogin(false) },
    )
    SyncChoiceSheet(
        ui = ui,
        onDismiss = { vm.askSync(null) },
        onChoose = vm::syncList,
    )
}

/**
 * Story media card with its download and position cards; shared by the plugin tab and the reader
 * title card.
 */
@Composable
fun PluginStoryOverlays(
    vm: PluginTabViewModel,
    onRead: () -> Unit,
    hiddenActions: Set<String> = emptySet(),
) {
    val ui by vm.ui.collectAsState()
    StoryMediaCard(
        visible = ui.story != null,
        ui = ui,
        onDismiss = vm::closeStory,
        onRead = onRead,
        onDownload = vm::onDownloadTap,
        onDownloadOptions = vm::openPartial,
        onOpenPosition = vm::openPosition,
        onRefreshToc = vm::refreshStoryToc,
        onDelete = vm::deleteStory,
        onToggleList = vm::toggleList,
        onToggleNotify = vm::toggleNotify,
        onRename = vm::openRename,
        onPluginAction = vm::runCardAction,
        hiddenActions = hiddenActions,
    )
    DownloadAllCard(
        visible = ui.confirmDownloadAll,
        story = ui.story,
        onConfirm = vm::downloadAllChapters,
        onDismiss = vm::dismissDownloadAll,
    )
    PartialDownloadCard(
        visible = ui.showPartial,
        ui = ui,
        onDismiss = vm::closePartial,
        onCacheLevel = vm::setCacheLevelDraft,
        onCleanup = vm::setCleanup,
        onDownload = vm::downloadAhead,
    )
    PositionSliderCard(
        visible = ui.showPosition,
        story = ui.story,
        busy = ui.busy,
        onDismiss = vm::closePosition,
        onSave = vm::savePosition,
    )
    RenameStoryCard(
        visible = ui.showRename,
        story = ui.story,
        error = ui.error,
        onDismiss = vm::closeRename,
        onSave = vm::renameStory,
    )
    PositionConflictCard(
        ui = ui,
        onUseRemote = { vm.resolveConflict(useRemote = true) },
        onKeepMine = { vm.resolveConflict(useRemote = false) },
        onLater = vm::postponeConflict,
    )
}