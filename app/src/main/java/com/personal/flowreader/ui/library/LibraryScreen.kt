package com.personal.flowreader.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.CustomLibraryTab
import com.personal.flowreader.data.FileAccessAdvice
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowEmptyState
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.controls.FlowFab
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.layer.FlowScreen
import com.personal.flowreader.ui.design.layer.FlowScreenKind
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.plugin.PluginTabContent
import com.personal.flowreader.ui.plugin.PluginTabFab
import com.personal.flowreader.ui.plugin.PluginTabOverlays
import com.personal.flowreader.ui.settings.EditableRuleList
import com.personal.flowreader.ui.settings.RuleListText
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.settings.FilterEditorSession
import com.personal.flowreader.ui.settings.FilterRuleEditorOverlay
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.SettingsOverlay
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

private const val LibraryFilterPreviewSample =
    "The quick brown fox jumps over the lazy dog. Names like Alice and Bob can be replaced."

private val BookMimeTypes = arrayOf("application/epub+zip", "text/plain", "*/*")

private class PersistableOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
}

/**
 * Library primary screen: header + primary tab bar, the active tab's content, a bottom dock
 * with the now-playing card while TTS plays, the tab's FAB, and every Library card overlay.
 */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    onOpenBook: (String) -> Unit,
    onOpenQue: (bookId: String, queId: String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val installedPlugins by vm.plugins.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var addTabOpen by remember { mutableStateOf(false) }
    var filterEditor by remember { mutableStateOf<FilterEditorSession?>(null) }
    var pendingSource by remember { mutableStateOf<BookSource?>(null) }
    var filesSplashId by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PersistableOpenDocument()) { uri: Uri? ->
        val source = pendingSource
        pendingSource = null
        if (uri != null && source != null) vm.add(uri, source)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() }
    }
    LaunchedEffect(ui.message) {
        val text = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }
    LaunchedEffect(ui.error) {
        val text = ui.error ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeError()
    }
    val context = LocalContext.current
    val activePlugin = (ui.tab as? LibraryTabId.Plugin)?.let { t -> installedPlugins.firstOrNull { it.id == t.pluginId } }

    FlowScreen(
        kind = FlowScreenKind.Library,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        bottomDock = {
            Item(visible = tts.sessionActive && tts.bookId.isNotBlank()) {
                NowPlayingCard(
                    title = tts.bookTitle,
                    snippet = tts.snippet,
                    playing = tts.playing,
                    onOpen = { onOpenBook(tts.bookId) },
                    onPlayPause = { if (tts.playing) vm.tts.pause() else vm.tts.play() },
                    onClose = { vm.tts.stop() },
                )
            }
        },
        fab = {
            when (ui.tab) {
                LibraryTabId.Files, is LibraryTabId.Custom -> FlowFab(
                    icon = Icons.Filled.Add,
                    contentDescription = "Add file",
                    onClick = { if (!ui.busy) addDialog = true },
                )
                LibraryTabId.Que -> FlowFab(
                    icon = Icons.Filled.ContentPaste,
                    contentDescription = "Add from clipboard",
                    onClick = {
                        if (ui.busy) return@FlowFab
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        vm.queueFromClipboard(text)
                    },
                )
                is LibraryTabId.Plugin -> activePlugin?.let { PluginTabFab(it) }
            }
        },
        snackbar = { SnackbarHost(snackbar) },
    ) { docks ->
        Column(Modifier.fillMaxSize()) {
            LibraryTopBar(
                tab = ui.tab,
                plugins = installedPlugins.filter { it.id in ui.enabledPluginIds },
                customTabs = ui.customTabs,
                viewMode = ui.viewMode,
                addTabOpen = addTabOpen,
                onTab = vm::setTab,
                onViewMode = vm::setViewMode,
                onAddTab = { addTabOpen = true },
                onSettings = { settingsOpen = true },
            )
            val paneModifier = Modifier.weight(1f)
            when (val tab = ui.tab) {
                LibraryTabId.Files -> LibraryBooksPane(
                    books = ui.books,
                    viewMode = ui.viewMode,
                    busy = ui.busy,
                    emptyMessage = "No books yet.\nTap + to add an EPUB or TXT.",
                    onOpen = onOpenBook,
                    onLongOpen = { bookId -> filesSplashId = bookId },
                    bottomInset = docks.bottom,
                    modifier = paneModifier,
                )
                is LibraryTabId.Custom -> LibraryBooksPane(
                    books = ui.books,
                    viewMode = ui.viewMode,
                    busy = ui.busy,
                    emptyMessage = "Nothing on this shelf yet.\nTap + to add an EPUB or TXT.",
                    onOpen = onOpenBook,
                    onLongOpen = { bookId -> filesSplashId = bookId },
                    bottomInset = docks.bottom,
                    modifier = paneModifier,
                )
                LibraryTabId.Que -> QueTab(
                    entries = ui.que,
                    busy = ui.busy,
                    bottomInset = docks.bottom,
                    onOpen = { entry -> onOpenQue(entry.progress.bookId, entry.item.id) },
                    onReorder = vm::reorderQue,
                    onRemove = vm::removeQue,
                    modifier = paneModifier,
                )
                is LibraryTabId.Plugin -> {
                    if (activePlugin != null) {
                        PluginTabContent(
                            plugin = activePlugin,
                            actions = vm.pluginActions,
                            viewMode = ui.viewMode,
                            bottomInset = docks.bottom,
                            modifier = paneModifier,
                        )
                    } else {
                        // Plugin tab selected but plugin missing: fall back to Files.
                        LaunchedEffect(tab) { vm.setTab(LibraryTabId.Files) }
                        FlowEmptyState("Plugin unavailable", paneModifier)
                    }
                }
            }
        }

        if (ui.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(FlowLayer.ContentScrim.z)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = FlowTokens.Scrim.Busy)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    AddTabOverlay(
        visible = addTabOpen,
        plugins = installedPlugins,
        enabledIds = ui.enabledPluginIds,
        customTabs = ui.customTabs,
        onSetEnabled = { id, enabled ->
            vm.setPluginEnabled(id, enabled)
            addTabOpen = false
        },
        onAddCustom = { title ->
            vm.addCustomTab(title)
            addTabOpen = false
        },
        onRemoveCustom = { id -> vm.removeCustomTab(id) },
        onDismiss = { addTabOpen = false },
    )

    AddBookOverlay(
        visible = addDialog,
        onImport = {
            pendingSource = BookSource.Imported
            addDialog = false
            picker.launch(BookMimeTypes)
        },
        onLink = {
            pendingSource = BookSource.Linked
            addDialog = false
            picker.launch(BookMimeTypes)
        },
        onDismiss = { addDialog = false },
    )

    val splashBook = filesSplashId?.let { id -> ui.books.find { it.bookId == id } }
    LaunchedEffect(filesSplashId, splashBook) {
        if (filesSplashId != null && splashBook == null) filesSplashId = null
    }
    FilesBookSplash(
        book = splashBook,
        busy = ui.busy,
        onDismiss = { filesSplashId = null },
        onOpen = onOpenBook,
        onRemove = vm::removeFromLibrary,
    )

    activePlugin?.let { PluginTabOverlays(plugin = it, actions = vm.pluginActions) }

    WebCrawlCards(
        prompt = ui.crawlPrompt,
        progress = ui.crawlProgress,
        onAnswer = vm::answerCrawlPrompt,
        onDismissPrompt = vm::dismissCrawlPrompt,
        onStop = vm::stopCrawl,
    )

    SettingsOverlay(
        visible = settingsOpen,
        appearance = appearance,
        appearanceCallbacks = appearanceCallbacks,
        tts = TtsSettingsState(
            engineKey = tts.engineKey,
            voiceId = tts.voiceId,
            engines = tts.engines,
            voices = tts.voices,
            speed = tts.speed,
            pitch = tts.pitch,
            prefetchCount = tts.prefetchCount,
            clipTargetChars = tts.clipTargetChars,
            clipFlexChars = tts.clipFlexChars,
            doubleTapPlay = tts.doubleTapPlay,
            mobileDataFallback = tts.mobileDataFallback,
            autoPlayOnShare = tts.autoPlayOnShare,
            shareInterruptsPlayback = tts.shareInterruptsPlayback,
            autoScrollWithTts = tts.autoScrollWithTts,
            minSignal = tts.minSignal,
            underlayBtAddress = tts.underlayBtAddress,
            underlayBtName = tts.underlayBtName,
            underlayBtConnected = tts.underlayBtConnected,
            sentenceGapMs = tts.sentenceGapMs,
            highlightSyncMs = tts.highlightSyncMs,
        ),
        ttsCallbacks = TtsSettingsCallbacks(
            onEngine = { vm.tts.setEngine(it) },
            onVoice = { vm.tts.setVoice(it) },
            onSpeed = { vm.tts.setSpeed(it) },
            onPitch = { vm.tts.setPitch(it) },
            onPrefetchCount = { vm.tts.setPrefetchCount(it) },
            onClipTargetChars = { vm.tts.setClipTargetChars(it) },
            onClipFlexChars = { vm.tts.setClipFlexChars(it) },
            onDoubleTapPlay = { vm.tts.setDoubleTapPlay(it) },
            onMobileDataFallback = { vm.tts.setMobileDataFallback(it) },
            onAutoPlayOnShare = { vm.tts.setAutoPlayOnShare(it) },
            onShareInterruptsPlayback = { vm.tts.setShareInterruptsPlayback(it) },
            onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
            onMinSignal = { level, persist -> vm.tts.setMinSignal(level, persist) },
            onUnderlayBtDevice = { address, name -> vm.tts.setUnderlayBtDevice(address, name) },
            underlayBondedDevices = { vm.tts.underlayBondedDevices() },
            onSentenceGapMs = { vm.tts.setSentenceGapMs(it) },
            onHighlightSyncMs = { vm.tts.setHighlightSyncMs(it) },
        ),
        filters = FilterSettingsState(
            filtersGlobal = ui.filtersGlobal,
            filtersGroups = ui.filtersGroups,
            filtersLocal = emptyList(),
            filterScopes = listOf(FilterScope.Global, FilterScope.Groups),
        ),
        filterCallbacks = FilterSettingsCallbacks(
            onAddFilter = { scope ->
                filterEditor = FilterEditorSession(scope = scope, rule = FilterRule(), isNew = true)
            },
            onEditFilter = { scope, rule ->
                filterEditor = FilterEditorSession(scope = scope, rule = rule, isNew = false)
            },
            onSetFilterEnabled = { scope, id, enabled -> vm.setFilterEnabled(scope, id, enabled) },
            onReorderFilters = { scope, ids -> vm.reorderFilters(scope, ids) },
            onDeleteFilters = { scope, ids -> vm.deleteFilters(scope, ids) },
        ),
        debugEnabled = appearance.debugEnabled,
        onDebugEnabled = appearanceCallbacks.onDebugEnabled,
        onDismiss = { settingsOpen = false },
    )

    // Stacks above Settings; Back closes the editor first.
    val editor = filterEditor
    if (editor != null) {
        FilterRuleEditorOverlay(
            visible = true,
            scope = editor.scope,
            initial = editor.rule,
            sampleSeed = LibraryFilterPreviewSample,
            isNew = editor.isNew,
            previewApply = { sample, draft, mode -> vm.previewApply(sample, draft, editor.scope, mode) },
            onSave = { draft ->
                if (editor.isNew) vm.addFilter(editor.scope, draft) else vm.updateFilter(editor.scope, draft)
                filterEditor = null
            },
            onSpeak = { vm.tts.speakPreview(it) },
            onDismiss = { filterEditor = null },
        )
    }
}

/** App title, view-mode and settings actions, then the primary tab bar ending in the "+" action tab. */
@Composable
private fun LibraryTopBar(
    tab: LibraryTabId,
    plugins: List<InstalledPlugin>,
    customTabs: List<CustomLibraryTab>,
    viewMode: LibraryViewMode,
    addTabOpen: Boolean,
    onTab: (LibraryTabId) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onAddTab: () -> Unit,
    onSettings: () -> Unit,
) {
    val destinations = buildList {
        add(LibraryTabId.Files to "Files")
        add(LibraryTabId.Que to "Queue")
        customTabs.sortedBy { it.order }.forEach { add(LibraryTabId.Custom(it.id) to it.title) }
        plugins.forEach { add(LibraryTabId.Plugin(it.id) to it.name) }
    }
    val tabs = destinations.map { (id, label) ->
        FlowTab.text(label, selected = !addTabOpen && tab == id, onClick = { onTab(id) }, key = id)
    } + FlowTab.action(Icons.Filled.Add, "Add tab", open = addTabOpen, onClick = onAddTab)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FlowTokens.Pad.Screen, end = FlowTokens.Space.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Flow Reader", style = FlowType.cardTitle, modifier = Modifier.weight(1f))
            if (tab != LibraryTabId.Que) {
                val accent = MaterialTheme.colorScheme.primary
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                FlowIconButton(
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "List view",
                    onClick = { onViewMode(LibraryViewMode.List) },
                    tint = if (viewMode == LibraryViewMode.List) accent else muted,
                )
                FlowIconButton(
                    icon = Icons.Filled.GridView,
                    contentDescription = "Shelf view",
                    onClick = { onViewMode(LibraryViewMode.Shelf) },
                    tint = if (viewMode == LibraryViewMode.Shelf) accent else muted,
                )
            }
            FlowIconButton(icon = Icons.Filled.Settings, contentDescription = "Settings", onClick = onSettings)
        }
        FlowTabBar(tabs = tabs)
    }
}

@Composable
private fun AddTabOverlay(
    visible: Boolean,
    plugins: List<InstalledPlugin>,
    enabledIds: Set<String>,
    customTabs: List<CustomLibraryTab>,
    onSetEnabled: (String, Boolean) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customTitle by remember(visible) { mutableStateOf("") }
    FlowFullscreenCard(visible = visible, onDismiss = onDismiss, title = "Add a tab") {
        FlowSection("Custom shelf")
        FlowHint("Create a shelf and choose it as a Router destination under Import settings.")
        FlowTextField(value = customTitle, onValueChange = { customTitle = it }, label = "Tab name")
        FlowActionRow {
            FlowTextAction(
                "Add shelf",
                {
                    val name = customTitle.trim()
                    if (name.isNotEmpty()) {
                        onAddCustom(name)
                        customTitle = ""
                    }
                },
                enabled = customTitle.trim().isNotEmpty(),
            )
        }
        customTabs.sortedBy { it.order }.forEach { tab ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, style = FlowType.rowTitle, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveCustom(tab.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${tab.title}")
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = FlowTokens.Space.S))

        FlowSection("Plugins")
        FlowHint(
            if (plugins.isEmpty()) {
                "No plugins installed. Browse repositories in Settings > Import > Plugins."
            } else {
                "Enable a plugin to add its tab next to Files and Queue."
            },
        )
        plugins.forEach { plugin ->
            val on = plugin.id in enabledIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetEnabled(plugin.id, !on) }
                    .padding(vertical = FlowTokens.Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(plugin.name, style = FlowType.rowTitle)
                    if (plugin.manifest.description.isNotBlank()) FlowHint(plugin.manifest.description)
                }
                TextButton(onClick = { onSetEnabled(plugin.id, !on) }) {
                    Text(if (on) "Remove" else "Add", style = FlowType.action)
                }
            }
        }
    }
}

@Composable
private fun AddBookOverlay(
    visible: Boolean,
    onImport: () -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Add a book",
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Reference in place", onLink)
                FlowTextAction("Import a copy", onImport)
            }
        },
    ) {
        Text(FileAccessAdvice.forSdk(), style = FlowType.body)
        FlowHint(
            "Import a copy stores the book inside Flow Reader. It stays available if you move or delete the original.",
        )
        FlowHint(
            "Reference in place reads the original file. No extra copy. Access can be lost if the file is moved or the grant is revoked.",
        )
    }
}

@Composable
private fun QueTab(
    entries: List<QueEntry>,
    busy: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpen: (QueEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (entries.isEmpty() && !busy) {
            FlowEmptyState("Queue is empty.\nPaste from the clipboard or share text to Flow Reader.")
            return@Box
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(flowDisplayListPadding(bottomExtra = bottomInset)),
        ) {
            EditableRuleList(
                rules = entries,
                idOf = { it.item.id },
                nameOf = { it.progress.title },
                text = RuleListText(
                    title = "Queue",
                    empty = "",
                    hint = "Plays top to bottom as one document. Hold an item to reorder or remove.",
                    editingHint = "Drag the handle to change the order. The Queue plays top to bottom.",
                    noun = "item",
                ),
                onAdd = null,
                onEdit = onOpen,
                onReorder = onReorder,
                onDelete = onRemove,
                trailing = { entry ->
                    if (entry.item.done) {
                        Text(
                            "Done",
                            style = FlowType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FlowTokens.Space.XS),
                        )
                    }
                },
            ) { entry, rowModifier ->
                Text(
                    entry.progress.title,
                    style = FlowType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.item.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = rowModifier.padding(start = FlowTokens.Space.XS),
                )
            }
        }
    }
}
