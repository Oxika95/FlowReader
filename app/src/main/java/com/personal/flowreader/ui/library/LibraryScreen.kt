package com.personal.flowreader.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.FileAccessAdvice
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowEmptyState
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowFab
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.layer.FlowScreen
import com.personal.flowreader.ui.design.layer.FlowScreenKind
import com.personal.flowreader.ui.plugin.PluginTabContent
import com.personal.flowreader.ui.plugin.PluginTabFab
import com.personal.flowreader.ui.plugin.PluginTabOverlays
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
    onOpenQue: (bookId: String, queId: String, chapter: Int?) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val installedPlugins by vm.plugins.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val chrome by (LocalContext.current.applicationContext as FlowApp).chrome.collectAsState()
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
                tabStyle = chrome.libraryTabs,
                addTabOpen = addTabOpen,
                onTab = vm::setTab,
                onAddTab = { addTabOpen = true },
                onSettings = { settingsOpen = true },
            )
            val paneModifier = Modifier.weight(1f)
            when (val tab = ui.tab) {
                LibraryTabId.Files -> LibraryBooksPane(
                    books = ui.books,
                    viewMode = chrome.libraryView,
                    busy = ui.busy,
                    emptyMessage = "No books yet.\nTap + to add an EPUB or TXT.",
                    onOpen = onOpenBook,
                    onLongOpen = { bookId -> filesSplashId = bookId },
                    bottomInset = docks.bottom,
                    modifier = paneModifier,
                )
                is LibraryTabId.Custom -> LibraryBooksPane(
                    books = ui.books,
                    viewMode = chrome.libraryView,
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
                    onOpen = { entry, chapter -> onOpenQue(entry.bookId, entry.queId, chapter) },
                    onReorder = vm::reorderQue,
                    onRemove = vm::removeQue,
                    modifier = paneModifier,
                )
                is LibraryTabId.Plugin -> {
                    if (activePlugin != null) {
                        PluginTabContent(
                            plugin = activePlugin,
                            actions = vm.pluginActions,
                            viewMode = chrome.libraryView,
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
            underlayMode = tts.underlayMode,
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
            onUnderlayMode = { vm.tts.setUnderlayMode(it) },
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
