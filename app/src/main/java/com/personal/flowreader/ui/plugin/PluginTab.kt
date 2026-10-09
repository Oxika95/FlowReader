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
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.api.PluginCapability
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.controls.FlowFab
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.library.LibraryBooksPane
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
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refreshLocal() }
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
        if (!ui.anyOverlay) {
            actions.showError(err)
            vm.consumeError()
        }
    }

    Column(modifier.fillMaxSize()) {
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        PluginSubTabs(
            ui = ui,
            onSection = vm::setSection,
            onAccount = {
                if (ui.manifest.auth != null) vm.setShowAccount(true) else vm.setShowSettings(true)
            },
        )
        when (val section = ui.section) {
            is PluginSection.Library -> if (ui.manifest.list(section.listId)?.isBrowse == true) {
                BrowseListPane(
                    ui = ui,
                    listId = section.listId,
                    onOpen = { row -> vm.openBrowse(section.listId, row) },
                    modifier = Modifier.weight(1f),
                    bottomInset = bottomInset,
                )
            } else {
                val listTitle = ui.manifest.list(section.listId)?.title ?: section.listId
                LibraryBooksPane(
                    books = ui.visibleBooks,
                    viewMode = viewMode,
                    busy = ui.busy,
                    emptyMessage = when {
                        ui.books.isEmpty() -> "No stories yet.\nTap + to add one" +
                            if (ui.manifest.auth != null) ", or sign in to import a list." else "."
                        else -> "No stories in $listTitle."
                    },
                    onOpen = { bookId -> vm.readBook(actions, bookId) },
                    onLongOpen = vm::openStory,
                    bottomInset = bottomInset,
                    subtitleFor = { book ->
                        val meta = ui.libraryMeta[book.bookId]
                        val row = ui.listRows[book.bookId]
                        val author = meta?.author?.ifBlank { null } ?: row?.author.orEmpty()
                        val chapters = meta?.chapterCount ?: 0
                        when {
                            author.isNotBlank() && chapters > 0 -> "$author · $chapters chapters"
                            chapters > 0 -> "$chapters chapters"
                            author.isNotBlank() -> author
                            else -> ui.manifest.name
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
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

/** The plugin tab's FAB ("Add story"), placed by the Library screen's FAB slot. */
@Composable
fun PluginTabFab(plugin: InstalledPlugin) {
    val vm = rememberPluginTabViewModel(plugin)
    val ui by vm.ui.collectAsState()
    val canAdd = ui.manifest.has(PluginCapability.ResolveUrl) || ui.manifest.has(PluginCapability.Search)
    if (!canAdd) return
    FlowFab(
        icon = Icons.Filled.Add,
        contentDescription = "Add story",
        onClick = { if (!ui.busy) vm.setShowAdd(true) },
    )
}

@Composable
private fun PluginSubTabs(
    ui: PluginTabUi,
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
            add(FlowTab.text(list.title, selected = !ui.showAccount && ui.section == section, onClick = { onSection(section) }, key = list.id))
        }
        if (manifest.has(PluginCapability.Search)) {
            add(
                FlowTab.icon(
                    Icons.Filled.Search,
                    "Search",
                    selected = !ui.showAccount && ui.section == PluginSection.Search,
                    onClick = { onSection(PluginSection.Search) },
                ),
            )
        }
        if (trailing != null) {
            add(
                FlowTab.action(
                    trailing.first,
                    trailing.second,
                    open = ui.showAccount || ui.showSettings,
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
        onQuery = vm::setQuery,
        onSearch = { vm.search() },
        onUrl = vm::setUrlDraft,
        onOpenUrl = { vm.openUrl() },
        onOpen = vm::openWork,
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
        onSync = { vm.askSync(it) },
        onSettings = { vm.setShowSettings(true) },
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
    PluginSettingsSheet(
        visible = ui.showSettings,
        pluginId = plugin.id,
        onDismiss = { vm.setShowSettings(false) },
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
        onPosition = vm::openPosition,
        onRefreshToc = vm::refreshStoryToc,
        onDelete = vm::deleteStory,
        onToggleList = vm::toggleList,
        onToggleNotify = vm::toggleNotify,
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
}