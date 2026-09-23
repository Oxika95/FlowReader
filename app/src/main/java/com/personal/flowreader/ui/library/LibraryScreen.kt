package com.personal.flowreader.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.FileAccessAdvice
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.library.plugin.LibrarySourcePlugin
import com.personal.flowreader.ui.common.FlowSlotTab
import com.personal.flowreader.ui.common.FlowSlotTabBar
import com.personal.flowreader.ui.common.FlowSlotTabLabel
import com.personal.flowreader.ui.reader.FilterRuleEditorOverlay
import com.personal.flowreader.ui.reader.ReaderModalScaffold
import com.personal.flowreader.ui.reader.SettingsOverlay
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.settings.FilterEditorSession
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.ModalHeaderRow
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.theme.FlowTokens

private const val LibraryFilterPreviewSample =
    "The quick brown fox jumps over the lazy dog. Names like Alice and Bob can be replaced."

private val BookMimeTypes = arrayOf("application/epub+zip", "text/plain", "*/*")

private class PersistableOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    onOpenBook: (String) -> Unit,
    onOpenQue: (bookId: String, queId: String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
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

    // Top clears the status bar; sides use M3 compact screen margin (16dp).
    val libraryGutter = FlowTokens.ScreenGutter
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        ) {
            LibraryTopBar(
                tab = ui.tab,
                plugins = vm.plugins.enabled(ui.enabledPluginIds),
                viewMode = ui.viewMode,
                inset = libraryGutter,
                addTabOpen = addTabOpen,
                onTab = vm::setTab,
                onViewMode = vm::setViewMode,
                onAddTab = { addTabOpen = true },
                onSettings = { settingsOpen = true },
            )
            when (val tab = ui.tab) {
                LibraryTabId.Files -> LibraryBooksPane(
                    books = ui.books,
                    viewMode = ui.viewMode,
                    busy = ui.busy,
                    inset = libraryGutter,
                    emptyMessage = "No books yet.\nTap + to add an EPUB or TXT.",
                    onOpen = onOpenBook,
                    onLongOpen = { bookId -> filesSplashId = bookId },
                    modifier = Modifier.weight(1f),
                )
                LibraryTabId.Que -> QueTab(
                    entries = ui.que,
                    busy = ui.busy,
                    inset = libraryGutter,
                    onOpen = { entry -> onOpenQue(entry.progress.bookId, entry.item.id) },
                    onRemove = { entry -> vm.removeQue(entry.item.id) },
                    modifier = Modifier.weight(1f),
                )
                is LibraryTabId.Plugin -> {
                    val plugin = vm.plugins.get(tab.pluginId)
                    if (plugin != null) {
                        plugin.TabContent(
                            actions = vm.pluginActions,
                            modifier = Modifier.weight(1f),
                            viewMode = ui.viewMode,
                        )
                    } else {
                        // Plugin tab selected but plugin missing — clear selection rather than
                        // silently rendering the Files list under the wrong tab.
                        LaunchedEffect(tab) {
                            vm.setTab(LibraryTabId.Files)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Plugin unavailable",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (ui.tab == LibraryTabId.Files &&
            !settingsOpen && !addDialog && !addTabOpen && filterEditor == null &&
            filesSplashId == null
        ) {
            FilledIconButton(
                onClick = { if (!ui.busy) addDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(end = libraryGutter, bottom = libraryGutter)
                    .size(FlowTokens.Comp.Fab),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add file",
                    modifier = Modifier.size(FlowTokens.Comp.FabIcon),
                )
            }
        }

        if (ui.tab == LibraryTabId.Que &&
            !settingsOpen && !addDialog && !addTabOpen && filterEditor == null
        ) {
            FilledIconButton(
                onClick = {
                    if (ui.busy) return@FilledIconButton
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    vm.queueFromClipboard(text)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(end = libraryGutter, bottom = libraryGutter)
                    .size(FlowTokens.Comp.Fab),
            ) {
                Icon(
                    Icons.Filled.ContentPaste,
                    contentDescription = "Add from clipboard",
                    modifier = Modifier.size(FlowTokens.Comp.FabIcon),
                )
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = libraryGutter + FlowTokens.Comp.SnackbarFabLift),
        )

        if (ui.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        AddTabOverlay(
            visible = addTabOpen,
            plugins = vm.plugins.available,
            enabledIds = ui.enabledPluginIds,
            onSetEnabled = { id, enabled ->
                vm.setPluginEnabled(id, enabled)
                addTabOpen = false
            },
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

        // Plugin overlays sit above tabs/top bar (same layer as Settings).
        (ui.tab as? LibraryTabId.Plugin)?.let { pluginTab ->
            vm.plugins.get(pluginTab.pluginId)?.OverlayContent(actions = vm.pluginActions)
        }

        SettingsOverlay(
            visible = settingsOpen && filterEditor == null,
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
                doubleTapPlay = tts.doubleTapPlay,
                autoScrollWithTts = tts.autoScrollWithTts,
                keepAliveUnderlay = tts.keepAliveUnderlay,
                sentenceGapMs = tts.sentenceGapMs,
                highlightSyncMs = tts.highlightSyncMs,
            ),
            ttsCallbacks = TtsSettingsCallbacks(
                onEngine = { vm.tts.setEngine(it) },
                onVoice = { vm.tts.setVoice(it) },
                onSpeed = { vm.tts.setSpeed(it) },
                onPitch = { vm.tts.setPitch(it) },
                onPrefetchCount = { vm.tts.setPrefetchCount(it) },
                onDoubleTapPlay = { vm.tts.setDoubleTapPlay(it) },
                onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
                onKeepAliveUnderlay = { vm.tts.setKeepAliveUnderlay(it) },
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
                onSetFilterEnabled = { scope, id, enabled ->
                    vm.setFilterEnabled(scope, id, enabled)
                },
            ),
            onDismiss = { settingsOpen = false },
        )

        val editor = filterEditor
        if (editor != null) {
            FilterRuleEditorOverlay(
                visible = true,
                scope = editor.scope,
                initial = editor.rule,
                sampleSeed = LibraryFilterPreviewSample,
                isNew = editor.isNew,
                previewApply = { sample, draft, mode ->
                    vm.previewApply(sample, draft, editor.scope, mode)
                },
                onSave = { draft ->
                    if (editor.isNew) vm.addFilter(editor.scope, draft)
                    else vm.updateFilter(editor.scope, draft)
                    filterEditor = null
                },
                onDelete = if (editor.isNew) {
                    null
                } else {
                    {
                        vm.deleteFilter(editor.scope, editor.rule.id)
                        filterEditor = null
                    }
                },
                onSpeak = { vm.tts.speakPreview(it) },
                onDismiss = { filterEditor = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    tab: LibraryTabId,
    plugins: List<LibrarySourcePlugin>,
    viewMode: LibraryViewMode,
    inset: Dp,
    addTabOpen: Boolean,
    onTab: (LibraryTabId) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onAddTab: () -> Unit,
    onSettings: () -> Unit,
) {
    val tabs = buildList {
        add(LibraryTabId.Files to "Files")
        add(LibraryTabId.Que to "Queue")
        plugins.forEach { add(LibraryTabId.Plugin(it.id) to it.title) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Flow Reader",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (tab == LibraryTabId.Files || tab is LibraryTabId.Plugin) {
                IconButton(onClick = { onViewMode(LibraryViewMode.List) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "List view",
                        tint = if (viewMode == LibraryViewMode.List) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = { onViewMode(LibraryViewMode.Shelf) }) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = "Shelf view",
                        tint = if (viewMode == LibraryViewMode.Shelf) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        LibraryTabBar(
            tabs = tabs,
            selected = tab,
            addSelected = addTabOpen,
            inset = inset,
            onTab = onTab,
            onAddTab = onAddTab,
        )
    }
}

@Composable
private fun LibraryTabBar(
    tabs: List<Pair<LibraryTabId, String>>,
    selected: LibraryTabId,
    addSelected: Boolean,
    inset: Dp,
    onTab: (LibraryTabId) -> Unit,
    onAddTab: () -> Unit,
) {
    val slotTabs = buildList {
        tabs.forEach { (id, label) ->
            val selectedTab = !addSelected && selected == id
            add(
                FlowSlotTab(
                    selected = selectedTab,
                    onClick = { onTab(id) },
                    measureLabel = label,
                    content = { FlowSlotTabLabel(label, it) },
                ),
            )
        }
        add(
            FlowSlotTab(
                selected = addSelected,
                onClick = onAddTab,
                measureLabel = null,
                content = { sel ->
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add tab",
                        modifier = Modifier.size(22.dp),
                        tint = if (sel) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            ),
        )
    }
    FlowSlotTabBar(tabs = slotTabs, inset = inset)
}

@Composable
private fun AddTabOverlay(
    visible: Boolean,
    plugins: List<LibrarySourcePlugin>,
    enabledIds: Set<String>,
    onSetEnabled: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(
            start = FlowTokens.ModalOuterPadding,
            end = FlowTokens.ModalOuterPadding,
            bottom = FlowTokens.ModalOuterPadding,
        ),
        onDismiss = onDismiss,
    ) {
        ModalHeaderRow(
            title = "Add a tab",
            onDismiss = onDismiss,
            closeContentDescription = "Close",
        )
        Column(
            Modifier.padding(
                horizontal = FlowTokens.ModalBodyPadding,
                vertical = FlowTokens.Space.S,
            ),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        ) {
            Text(
                "Plugins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Enable a plugin to add its tab next to Files and Queue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(plugin.title, style = MaterialTheme.typography.bodyLarge)
                        if (plugin.subtitle.isNotBlank()) {
                            Text(
                                plugin.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onSetEnabled(plugin.id, !on) }) {
                        Text(if (on) "Remove" else "Add")
                    }
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
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(
            start = FlowTokens.ModalOuterPadding,
            end = FlowTokens.ModalOuterPadding,
            bottom = FlowTokens.ModalOuterPadding,
        ),
        onDismiss = onDismiss,
    ) {
        ModalHeaderRow(
            title = "Add a book",
            onDismiss = onDismiss,
            closeContentDescription = "Close",
        )
        Column(
            Modifier.padding(
                horizontal = FlowTokens.ModalBodyPadding,
                vertical = FlowTokens.Space.S,
            ),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        ) {
            Text(FileAccessAdvice.forSdk(), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Import a copy stores the book inside Flow Reader. It stays available if you move or delete the original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Reference in place reads the original file. No extra copy. Access can be lost if the file is moved or the grant is revoked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onLink) { Text("Reference in place") }
                TextButton(onClick = onImport) { Text("Import a copy") }
            }
        }
    }
}

@Composable
private fun QueTab(
    entries: List<QueEntry>,
    busy: Boolean,
    inset: Dp,
    onOpen: (QueEntry) -> Unit,
    onRemove: (QueEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (entries.isEmpty() && !busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = inset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Queue is empty.\nPaste from the clipboard or share text to Flow-Queue.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = inset,
                    top = inset,
                    end = inset,
                    bottom = inset + FlowTokens.FabClearance,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.item.id }) { entry ->
                    QueLineItem(
                        entry = entry,
                        onOpen = { onOpen(entry) },
                        onRemove = { onRemove(entry) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueLineItem(
    entry: QueEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val done = entry.item.done
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(
                start = FlowTokens.Space.XS,
                top = FlowTokens.Pad.RowV,
                bottom = FlowTokens.Pad.RowV,
                end = FlowTokens.Radius.None,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.progress.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (done) muted else Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        if (done) {
            Text(
                "Done",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.padding(end = FlowTokens.Space.XS),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove from Queue",
                tint = muted,
            )
        }
    }
}
