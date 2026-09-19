package com.personal.flowreader.ui.library

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.FileAccessAdvice
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTab
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.ui.reader.AppearanceSettings
import com.personal.flowreader.ui.reader.AudioSettingsTab
import com.personal.flowreader.ui.reader.FilterRuleEditorOverlay
import com.personal.flowreader.ui.reader.FiltersSettingsTab
import com.personal.flowreader.ui.reader.ReaderModalScaffold
import com.personal.flowreader.ui.reader.ReaderPanelShape
import com.personal.flowreader.ui.reader.SettingsLocationNote
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LibraryFilterPreviewSample =
    "The quick brown fox jumps over the lazy dog. Names like Alice and Bob can be replaced."

private data class FilterEditorSession(
    val scope: FilterScope,
    val rule: FilterRule,
    val isNew: Boolean,
)

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
    themeMode: ThemeMode,
    accentHue: Float,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenQue: (bookId: String, queId: String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var filterEditor by remember { mutableStateOf<FilterEditorSession?>(null) }
    var pendingSource by remember { mutableStateOf<BookSource?>(null) }
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

    val libraryInset = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtLeast(16.dp)

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
                viewMode = ui.viewMode,
                inset = libraryInset,
                onTab = vm::setTab,
                onViewMode = vm::setViewMode,
                onSettings = { settingsOpen = true },
            )
            when (ui.tab) {
                LibraryTab.Files -> FilesTab(
                    books = ui.books,
                    viewMode = ui.viewMode,
                    busy = ui.busy,
                    inset = libraryInset,
                    onOpen = onOpenBook,
                    modifier = Modifier.weight(1f),
                )
                LibraryTab.Que -> QueTab(
                    entries = ui.que,
                    busy = ui.busy,
                    inset = libraryInset,
                    onOpen = { entry -> onOpenQue(entry.progress.bookId, entry.item.id) },
                    onRemove = { entry -> vm.removeQue(entry.item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (ui.tab == LibraryTab.Files && !settingsOpen && !addDialog && filterEditor == null) {
            FilledIconButton(
                onClick = { if (!ui.busy) addDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(end = libraryInset, bottom = libraryInset)
                    .size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add file", modifier = Modifier.size(28.dp))
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(bottom = libraryInset + 64.dp),
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

        LibrarySettingsOverlay(
            visible = settingsOpen && filterEditor == null,
            themeMode = themeMode,
            accentHue = accentHue,
            engineKey = tts.engineKey,
            voiceId = tts.voiceId,
            engines = tts.engines,
            voices = tts.voices,
            filtersGlobal = ui.filtersGlobal,
            filtersGroups = ui.filtersGroups,
            onTheme = onTheme,
            onAccentHue = onAccentHue,
            onEngine = { vm.tts.setEngine(it) },
            onVoice = { vm.tts.setVoice(it) },
            onAddFilter = { scope ->
                filterEditor = FilterEditorSession(scope = scope, rule = FilterRule(), isNew = true)
            },
            onEditFilter = { scope, rule ->
                filterEditor = FilterEditorSession(scope = scope, rule = rule, isNew = false)
            },
            onSetFilterEnabled = { scope, id, enabled ->
                vm.setFilterEnabled(scope, id, enabled)
            },
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
    tab: LibraryTab,
    viewMode: LibraryViewMode,
    inset: Dp,
    onTab: (LibraryTab) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onSettings: () -> Unit,
) {
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
            if (tab == LibraryTab.Files) {
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
        val tabIndex = if (tab == LibraryTab.Files) 0 else 1
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tab == LibraryTab.Files,
                onClick = { onTab(LibraryTab.Files) },
                text = { Text("Files") },
            )
            Tab(
                selected = tab == LibraryTab.Que,
                onClick = { onTab(LibraryTab.Que) },
                text = { Text("Que") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySettingsOverlay(
    visible: Boolean,
    themeMode: ThemeMode,
    accentHue: Float,
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    filtersGlobal: List<FilterRule>,
    filtersGroups: List<FilterRule>,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onAddFilter: (FilterScope) -> Unit,
    onEditFilter: (FilterScope, FilterRule) -> Unit,
    onSetFilterEnabled: (FilterScope, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = 8.dp),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close settings")
            }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Theme") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Audio") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Filters") })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            when (tab) {
                0 -> {
                    AppearanceSettings(
                        themeMode = themeMode,
                        accentHue = accentHue,
                        onTheme = onTheme,
                        onAccentHue = onAccentHue,
                    )
                    SettingsLocationNote(
                        "Font, spacing, and orientation are in the reader.",
                    )
                }
                1 -> {
                    AudioSettingsTab(
                        engineKey = engineKey,
                        voiceId = voiceId,
                        engines = engines,
                        voices = voices,
                        onEngine = onEngine,
                        onVoice = onVoice,
                        compact = true,
                    )
                    SettingsLocationNote(
                        "Speed, pitch, and playback options are in the reader.",
                    )
                }
                else -> {
                    FiltersSettingsTab(
                        filtersGlobal = filtersGlobal,
                        filtersGroups = filtersGroups,
                        filtersLocal = emptyList(),
                        onAdd = onAddFilter,
                        onEdit = onEditFilter,
                        onSetEnabled = onSetFilterEnabled,
                        scopes = listOf(FilterScope.Global, FilterScope.Groups),
                    )
                    SettingsLocationNote(
                        "Local filters are in the reader.",
                    )
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
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Add a book",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
private fun FilesTab(
    books: List<ProgressEntity>,
    viewMode: LibraryViewMode,
    busy: Boolean,
    inset: Dp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (books.isEmpty() && !busy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No books yet.\nTap + to add an EPUB or TXT.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            when (viewMode) {
                LibraryViewMode.List -> BookList(books, inset, onOpen)
                LibraryViewMode.Shelf -> BookShelf(books, inset, onOpen)
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Que is empty.\nShare text to Flow-Que to add items.",
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
                    bottom = inset,
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
            .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 0.dp),
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
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove from Que",
                tint = muted,
            )
        }
    }
}

@Composable
private fun LibraryBookCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = ReaderPanelShape,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = onBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        content()
    }
}

@Composable
private fun BookProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
    )
}

@Composable
private fun BookList(books: List<ProgressEntity>, inset: Dp, onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = inset,
            top = inset,
            end = inset,
            bottom = inset + 76.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.bookId }) { book ->
            LibraryBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                onClick = { onOpen(book.bookId) },
            ) {
                BookDetailsRow(book)
            }
        }
    }
}

@Composable
private fun BookDetailsRow(book: ProgressEntity) {
    val cover by rememberCover(book, maxEdge = 384)
    val linked = book.sourceKind == BookSource.Linked.name
    val cardBg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f),
            ) {
                CoverFill(
                    book = book,
                    cover = cover,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.4f to Color.Transparent,
                                    1f to cardBg,
                                ),
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    bookSubtitle(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (linked) {
            Icon(
                Icons.Filled.Link,
                contentDescription = "Linked file",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp),
            )
        }
        BookProgressBar(
            progress = book.readingProgress,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BookShelf(books: List<ProgressEntity>, inset: Dp, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(
            start = inset,
            top = inset,
            end = inset,
            bottom = inset + 76.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.bookId }) { book ->
            LibraryBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                onClick = { onOpen(book.bookId) },
            ) {
                BookShelfTile(book)
            }
        }
    }
}

@Composable
private fun BookShelfTile(book: ProgressEntity) {
    val cover by rememberCover(book, maxEdge = 512)
    val linked = book.sourceKind == BookSource.Linked.name
    Box(Modifier.fillMaxSize()) {
        CoverFill(
            book = book,
            cover = cover,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // Fade sits above a solid scrim so darkness always matches title height.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.88f)),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
                )
                BookProgressBar(progress = book.readingProgress)
            }
        }
        if (linked) {
            Icon(
                Icons.Filled.Link,
                contentDescription = "Linked file",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
private fun rememberCover(book: ProgressEntity, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = null, book.bookId, book.storedPath, maxEdge) {
        value = withContext(Dispatchers.IO) {
            EpubCover.loadBitmap(File(book.storedPath), maxEdge)?.asImageBitmap()
        }
    }

@Composable
private fun CoverFill(
    book: ProgressEntity,
    cover: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                book.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun sourceLabel(book: ProgressEntity): String =
    runCatching { BookSource.valueOf(book.sourceKind).label }
        .getOrDefault(BookSource.Imported.label)

private fun bookSubtitle(book: ProgressEntity): String {
    val source = sourceLabel(book)
    val whenRead = DateUtils.getRelativeTimeSpanString(
        book.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "$source · $whenRead"
}
