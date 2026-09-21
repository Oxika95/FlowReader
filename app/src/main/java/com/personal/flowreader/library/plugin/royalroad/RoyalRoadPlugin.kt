package com.personal.flowreader.library.plugin.royalroad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.LibrarySourcePlugin
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.ui.library.LibraryBooksPane
import com.personal.flowreader.ui.library.LibraryTabSlots
import com.personal.flowreader.ui.library.loadLibraryCoverBitmap
import com.personal.flowreader.ui.reader.ReaderModalScaffold
import com.personal.flowreader.ui.reader.ReaderPanelFeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RrSubTabMinGap = 16.dp
private val RrSubTabInnerPad = 8.dp
private val RrSubTabBarHeight = 48.dp
private val RrSubTabIndicatorHeight = 3.dp
private val RrSubTabInset = 16.dp

class RoyalRoadPlugin : LibrarySourcePlugin {
    override val id: String = ID
    override val title: String = "Royal Road"
    override val subtitle: String = "Your followed and saved serials"

    @Composable
    override fun TabContent(
        actions: LibraryPluginActions,
        modifier: Modifier,
        viewMode: LibraryViewMode,
    ) {
        val vm: RoyalRoadViewModel = viewModel()
        RoyalRoadTabBody(
            vm = vm,
            actions = actions,
            viewMode = viewMode,
            modifier = modifier,
        )
    }

    @Composable
    override fun OverlayContent(actions: LibraryPluginActions) {
        val vm: RoyalRoadViewModel = viewModel()
        RoyalRoadOverlays(vm = vm, actions = actions)
    }

    companion object {
        const val ID = "royalroad"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoyalRoadTabBody(
    vm: RoyalRoadViewModel,
    actions: LibraryPluginActions,
    viewMode: LibraryViewMode,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshLocal()
        }
    }
    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        actions.showMessage(msg)
        vm.consumeMessage()
    }
    LaunchedEffect(ui.error) {
        val err = ui.error ?: return@LaunchedEffect
        if (!ui.showLogin && !ui.showAdd && !ui.showAccount && !ui.showSyncChoice && ui.story == null) {
            actions.showError(err)
            vm.consumeError()
        }
    }

    val emptyMessage = when {
        ui.books.isEmpty() ->
            "No stories yet.\nTap + to add one, or sign in to import follows."
        ui.visibleBooks.isEmpty() ->
            "No stories in ${ui.libraryList.label}."
        else -> ""
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (ui.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            RoyalRoadListTabs(
                selected = ui.libraryList,
                accountSelected = ui.showAccount,
                loggedIn = ui.loggedIn,
                onSelect = vm::setLibraryList,
                onAccount = { vm.setShowAccount(true) },
            )
            LibraryBooksPane(
                books = ui.visibleBooks,
                viewMode = viewMode,
                busy = ui.busy,
                inset = 16.dp,
                emptyMessage = emptyMessage,
                onOpen = { bookId -> vm.readBook(actions, bookId) },
                onLongOpen = vm::openStory,
                subtitleFor = { book ->
                    val meta = ui.libraryMeta[book.bookId]
                    when {
                        meta == null -> "Royal Road"
                        meta.author.isNotBlank() && meta.chapterCount > 0 ->
                            "${meta.author} · ${meta.chapterCount} chapters"
                        meta.chapterCount > 0 -> "${meta.chapterCount} chapters"
                        meta.author.isNotBlank() -> meta.author
                        else -> "Royal Road"
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (!ui.showAdd && !ui.showLogin && !ui.showAccount && ui.story == null) {
            FilledIconButton(
                onClick = { if (!ui.busy) vm.setShowAdd(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add story", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun RoyalRoadListTabs(
    selected: RoyalRoadListKind,
    accountSelected: Boolean,
    loggedIn: Boolean,
    onSelect: (RoyalRoadListKind) -> Unit,
    onAccount: () -> Unit,
) {
    val scroll = rememberScrollState()
    val indicator = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val kinds = RoyalRoadListKind.entries
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val innerPadPx = with(density) { RrSubTabInnerPad.roundToPx() }
        val minWidths = IntArray(kinds.size + 1) { i ->
            if (i < kinds.size) {
                measurer.measure(
                    text = kinds[i].label,
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width + innerPadPx * 2
            } else {
                with(density) { 22.dp.roundToPx() } + innerPadPx * 2
            }
        }
        val layout = LibraryTabSlots.layout(
            availablePx = constraints.maxWidth,
            insetPx = with(density) { RrSubTabInset.roundToPx() },
            gapPx = with(density) { RrSubTabMinGap.roundToPx() },
            minWidthsPx = minWidths,
        )
        Row(
            modifier = Modifier
                .height(RrSubTabBarHeight)
                .padding(horizontal = RrSubTabInset)
                .then(
                    if (layout.overflow) {
                        Modifier.horizontalScroll(scroll)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(RrSubTabMinGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            kinds.forEachIndexed { index, kind ->
                val selectedTab = !accountSelected && selected == kind
                RoyalRoadSubTabHeader(
                    selected = selectedTab,
                    onClick = { onSelect(kind) },
                    indicator = indicator,
                    modifier = Modifier
                        .width(with(density) { layout.slotWidthsPx[index].toDp() })
                        .fillMaxHeight(),
                ) {
                    Text(
                        kind.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selectedTab) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
            RoyalRoadSubTabHeader(
                selected = accountSelected,
                onClick = onAccount,
                indicator = indicator,
                modifier = Modifier
                    .width(with(density) { layout.slotWidthsPx.last().toDp() })
                    .fillMaxHeight(),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = if (loggedIn) "Account" else "Sign in",
                    modifier = Modifier.size(22.dp),
                    tint = if (accountSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun RoyalRoadSubTabHeader(
    selected: Boolean,
    onClick: () -> Unit,
    indicator: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = RrSubTabInnerPad),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(RrSubTabIndicatorHeight)
                .background(if (selected) indicator else Color.Transparent),
        )
    }
}

@Composable
internal fun RoyalRoadOverlays(
    vm: RoyalRoadViewModel,
    actions: LibraryPluginActions,
) {
    val ui by vm.ui.collectAsState()

    AddStoryOverlay(
        visible = ui.showAdd,
        ui = ui,
        onDismiss = { vm.setShowAdd(false) },
        onRemoteQuery = vm::setRemoteQuery,
        onSearchRemote = vm::searchRemote,
        onUrl = vm::setUrlDraft,
        onOpenUrl = vm::openUrl,
        onOpenResult = vm::openSearchResult,
    )

    StorySplashOverlay(
        visible = ui.story != null,
        ui = ui,
        onDismiss = vm::closeStory,
        onRead = { vm.readStory(actions) },
        onDownload = vm::openDownloadOptions,
        onRefreshToc = vm::refreshStoryToc,
        onDelete = vm::deleteStory,
        onBookmark = vm::bookmarkStory,
    )

    DownloadOptionsOverlay(
        visible = ui.showDownload && ui.story != null,
        ui = ui,
        onDismiss = vm::closeDownloadOptions,
        onPane = vm::setDownloadPane,
        onDownloadAll = vm::downloadAllChapters,
        onPartialStartDraft = vm::setPartialStartDraft,
        onResolvePartialStart = vm::resolvePartialStartDraft,
        onPartialCount = vm::setPartialCountDraft,
        onDownloadPartial = vm::downloadPartialChapters,
        onCacheLevel = vm::setCacheLevelDraft,
        onBeginPartial = vm::beginPartialDownloadFromSettings,
    )

    AccountOverlay(
        visible = ui.showAccount,
        ui = ui,
        onDismiss = { vm.setShowAccount(false) },
        onShowLogin = { vm.setShowLogin(true) },
        onLogout = vm::logout,
        onSync = { vm.setShowSyncChoice(true) },
    )

    if (ui.showLogin) {
        AlertDialog(
            onDismissRequest = { if (!ui.busy) vm.setShowLogin(false) },
            title = { Text("Royal Road sign in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Optional. Sign in to sync your followed stories into this tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = ui.emailDraft,
                        onValueChange = vm::setEmailDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = ui.passwordDraft,
                        onValueChange = vm::setPasswordDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.login() }),
                    )
                    ui.error?.takeIf { ui.showLogin }?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::login, enabled = !ui.busy) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { vm.setShowLogin(false) }, enabled = !ui.busy) {
                    Text("Cancel")
                }
            },
        )
    }

    if (ui.showSyncChoice) {
        AlertDialog(
            onDismissRequest = { if (!ui.busy) vm.setShowSyncChoice(false) },
            title = { Text("Sync follows") },
            text = {
                Text(
                    "Merge keeps stories you added locally. Overwrite makes this tab match your Royal Road follows.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.syncFollows(FollowsSyncMode.Merge) },
                    enabled = !ui.busy,
                ) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { vm.syncFollows(FollowsSyncMode.Overwrite) },
                        enabled = !ui.busy,
                    ) { Text("Overwrite") }
                    TextButton(
                        onClick = { vm.setShowSyncChoice(false) },
                        enabled = !ui.busy,
                    ) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun AddStoryOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onRemoteQuery: (String) -> Unit,
    onSearchRemote: () -> Unit,
    onUrl: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onOpenResult: (SourceWork) -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Add from Royal Road",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "Search the site or paste a fiction URL. Opening a story shows its cover card — " +
                    "then choose Follow, Favorite, or Read Later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = ui.remoteQuery,
                onValueChange = onRemoteQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                label = { Text("Title on Royal Road") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchRemote() }),
                trailingIcon = {
                    IconButton(onClick = onSearchRemote) {
                        Icon(Icons.Filled.Search, contentDescription = "Search site")
                    }
                },
            )
            OutlinedTextField(
                value = ui.urlDraft,
                onValueChange = onUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Fiction or chapter URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpenUrl() }),
                trailingIcon = {
                    TextButton(onClick = onOpenUrl) { Text("Go") }
                },
            )
            ui.error?.takeIf { ui.showAdd }?.let { err ->
                Text(
                    err,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ui.searchResults.forEach { work ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !ui.busy) { onOpenResult(work) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(work.title, style = MaterialTheme.typography.titleMedium)
                    val sub = listOf(work.author, work.latestChapter)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            TextButton(onClick = onDismiss, enabled = !ui.busy) { Text("Close") }
        }
    }
}

@Composable
private fun AccountOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(16.dp),
        onDismiss = onDismiss,
    ) {
        Text(
            "Royal Road account",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Local stories stay on this device. Sync imports your follows when you choose.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (ui.loggedIn) {
            Text(ui.loginEmail.ifBlank { "Signed in" }, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onSync, enabled = !ui.busy) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Sync follows")
            }
            TextButton(onClick = onLogout, enabled = !ui.busy) { Text("Sign out") }
        } else {
            TextButton(onClick = onShowLogin) { Text("Sign in") }
        }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

@Composable
private fun StorySplashOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onRefreshToc: () -> Unit,
    onDelete: () -> Unit,
    onBookmark: (RoyalRoadListKind) -> Unit,
) {
    val story = ui.story
    val context = androidx.compose.ui.platform.LocalContext.current
    val book = story?.let { s -> ui.books.find { it.bookId == s.bookId } }
    val cover by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, book?.bookId, book?.storedPath) {
        value = book?.let { row ->
            withContext(Dispatchers.IO) {
                loadLibraryCoverBitmap(row, maxEdge = 768)?.asImageBitmap()
            }
        }
    }
    val canBlur = android.os.Build.VERSION.SDK_INT >= 31
    val cardBg = MaterialTheme.colorScheme.background
    ReaderModalScaffold(
        visible = visible && story != null,
        contentPadding = PaddingValues(0.dp),
        onDismiss = onDismiss,
        feather = ReaderPanelFeather,
        scrimAlpha = 0.66f,
    ) {
        if (story == null) return@ReaderModalScaffold
        val art = cover
        val onCoverMuted = Color.White.copy(alpha = 0.78f)
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(art?.let { it.width.toFloat() / it.height.toFloat() } ?: (2f / 3f)),
            ) {
                // Cap synopsis so title/meta/offline + description stay within the cover.
                val synopsisMaxHeight = (maxHeight * 0.42f).coerceAtLeast(40.dp)
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (canBlur) Modifier.blur(6.dp) else Modifier),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
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
                            .background(Color.Black.copy(alpha = 0.88f))
                            .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                story.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    lineHeight = MaterialTheme.typography.titleLarge.fontSize * 1.15f,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (story.author.isNotBlank()) {
                                Text(
                                    story.author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        val stats = listOfNotNull(
                            story.status.takeIf { it.isNotBlank() },
                            story.ratingLabel.takeIf { it.isNotBlank() }?.let { "★ $it" },
                            story.views?.let { "${formatCount(it)} views" },
                            "${story.chapterCount} chapters",
                        ).joinToString(" · ")
                        if (stats.isNotEmpty()) {
                            Text(
                                stats,
                                style = MaterialTheme.typography.bodySmall,
                                color = onCoverMuted,
                                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                            )
                        }
                        if (story.tags.isNotEmpty()) {
                            Text(
                                story.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                            )
                        }
                        if (story.synopsis.isNotBlank()) {
                            Text(
                                story.synopsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = onCoverMuted,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 20,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = synopsisMaxHeight)
                                    .padding(top = 4.dp, end = 8.dp, bottom = 4.dp),
                            )
                        }
                        ChapterCacheStrip(
                            chapterCount = story.chapterCount,
                            chapterIndex = ui.partialStartIndex,
                            cachedIndices = story.cachedIndices,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(10.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoyalRoadListKind.entries.forEach { kind ->
                        SplashCircleIconButton(
                            icon = kind.splashIcon,
                            contentDescription = kind.label,
                            selected = kind in story.listedIn,
                            enabled = !ui.busy,
                            onClick = { onBookmark(kind) },
                        )
                    }
                    SplashCircleIconButton(
                        icon = Icons.Filled.Share,
                        contentDescription = "Share",
                        selected = false,
                        enabled = story.fictionUrl.isNotBlank(),
                        onClick = {
                            val send = android.content.Intent(
                                android.content.Intent.ACTION_SEND,
                            ).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_SUBJECT,
                                    story.title,
                                )
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    story.fictionUrl,
                                )
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(
                                    send,
                                    "Share story",
                                ),
                            )
                        },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val dl = ui.downloadProgress
                Text(
                    if (dl != null) {
                        "Downloading ${dl.first} / ${dl.second}"
                    } else {
                        "Cached ${story.downloadedCount} / ${story.chapterCount} chapters" +
                            " · cache level ${maxOf(story.keepBehind, story.prefetchAhead)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDownload,
                            enabled = !ui.busy && story.chapterCount > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Download", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onRefreshToc,
                            enabled = !ui.busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Refresh", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = !ui.busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Delete", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Button(
                        onClick = onRead,
                        enabled = !ui.busy && story.chapterCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) {
                        Text("Read")
                    }
                }
                ui.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = if (selected) accent else Color.Black.copy(alpha = 0.72f)
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    val ring = if (selected) accent else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(1.dp, ring, CircleShape)
            .background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private val RoyalRoadListKind.splashIcon: ImageVector
    get() = when (this) {
        RoyalRoadListKind.Follow -> Icons.Filled.Add
        RoyalRoadListKind.Favorite -> Icons.Filled.Favorite
        RoyalRoadListKind.ReadLater -> Icons.Filled.Schedule
    }

@Composable
private fun DownloadOptionsOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onPane: (RoyalRoadDownloadPane) -> Unit,
    onDownloadAll: () -> Unit,
    onPartialStartDraft: (String) -> Unit,
    onResolvePartialStart: () -> Unit,
    onPartialCount: (String) -> Unit,
    onDownloadPartial: () -> Unit,
    onCacheLevel: (String) -> Unit,
    onBeginPartial: () -> Unit,
) {
    val story = ui.story
    var settingsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible, ui.downloadPane) {
        if (!visible || ui.downloadPane != RoyalRoadDownloadPane.Menu) {
            if (settingsExpanded) {
                onResolvePartialStart()
            }
            settingsExpanded = false
        }
    }
    ReaderModalScaffold(
        visible = visible && story != null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onDismiss = onDismiss,
        feather = ReaderPanelFeather,
        scrimAlpha = 0.66f,
    ) {
        if (story == null) return@ReaderModalScaffold
        when (ui.downloadPane) {
            RoyalRoadDownloadPane.Menu -> {
                Text(
                    "Download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                Text(
                    "Streaming keeps a small window around your reading position. " +
                        "Pin chapters here for offline reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Button(
                    onClick = onDownloadAll,
                    enabled = !ui.busy && story.chapterCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text("Download all")
                }
                PartialDownloadSplitButton(
                    enabled = !ui.busy && story.chapterCount > 0,
                    settingsSelected = settingsExpanded,
                    onPartial = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = false
                        onPane(RoyalRoadDownloadPane.Partial)
                    },
                    onSettings = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = !settingsExpanded
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                AnimatedVisibility(
                    visible = settingsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PartialCacheSettingsPanel(
                        story = story,
                        ui = ui,
                        enabled = !ui.busy,
                        onPartialStartDraft = onPartialStartDraft,
                        onCacheLevel = onCacheLevel,
                        onBeginPartial = onBeginPartial,
                    )
                }
                TextButton(onClick = onDismiss, enabled = !ui.busy) {
                    Text("Cancel")
                }
            }
            RoyalRoadDownloadPane.Partial -> {
                Text(
                    "Partial download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                Text(
                    "Pins and downloads from the configured start chapter through the end, " +
                        "or a chapter count cap if set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                val startTitle = story.toc.getOrNull(ui.partialStartIndex)?.title.orEmpty()
                Text(
                    "Starting at chapter ${ui.partialStartIndex + 1}" +
                        if (startTitle.isNotBlank()) " — $startTitle" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = ui.partialCountDraft,
                    onValueChange = onPartialCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    label = { Text("Chapter count (blank = through end)") },
                    supportingText = {
                        Text("Leave blank to download from the start chapter to the latest chapter.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = onDownloadPartial,
                    enabled = !ui.busy && story.chapterCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text("Download range")
                }
                TextButton(
                    onClick = { onPane(RoyalRoadDownloadPane.Menu) },
                    enabled = !ui.busy,
                ) {
                    Text("Back")
                }
            }
        }
        ui.downloadProgress?.let { (done, total) ->
            Text(
                "Working $done / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        ui.error?.takeIf { ui.showDownload }?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PartialDownloadSplitButton(
    enabled: Boolean,
    settingsSelected: Boolean,
    onPartial: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(50)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier
                .height(40.dp)
                .border(1.dp, outline, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Partial download",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onPartial)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                maxLines = 1,
            )
            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = outline,
            )
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Cache settings",
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    settingsSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onSettings)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun PartialCacheSettingsPanel(
    story: RoyalRoadStorySplash,
    ui: RoyalRoadUi,
    enabled: Boolean,
    onPartialStartDraft: (String) -> Unit,
    onCacheLevel: (String) -> Unit,
    onBeginPartial: () -> Unit,
) {
    val draftNum = ui.partialStartDraft.toIntOrNull()
    val startTitle = if (draftNum != null && draftNum >= 1) {
        story.toc.getOrNull(draftNum - 1)?.title.orEmpty()
    } else {
        ""
    }
    val startLabel = if (startTitle.isNotBlank()) {
        "Start at chapter: $startTitle"
    } else {
        "Start at chapter:"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Cache settings",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedFieldWithInfo(
            value = ui.partialStartDraft,
            onValueChange = onPartialStartDraft,
            enabled = enabled && story.chapterCount > 0,
            label = startLabel,
            info = "Chapter where a partial download begins.",
        )
        OutlinedFieldWithInfo(
            value = ui.cacheLevelDraft,
            onValueChange = onCacheLevel,
            enabled = enabled,
            label = "Cache level",
            info = "How many chapters to keep ahead of and behind your reading position. Begin partial download also uses this count from the start chapter.",
        )
        Button(
            onClick = onBeginPartial,
            enabled = enabled && story.chapterCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Begin partial download")
        }
    }
}

@Composable
private fun OutlinedFieldWithInfo(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    info: String,
) {
    var showInfo by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Surface(
            onClick = { showInfo = true },
            enabled = true,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-6).dp)
                .size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "About $label",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = {
                Text(info, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            },
        )
    }
}

/** Continuous chapter bar: gray behind, desaturated accent ahead, saturated accent at locus. */
@Composable
private fun ChapterCacheStrip(
    chapterCount: Int,
    chapterIndex: Int,
    cachedIndices: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ahead = accent.copy(alpha = 0.40f)
    val behind = CacheBehindGray
    val track = Color.White.copy(alpha = 0.14f)
    Box(
        modifier = modifier.drawBehind {
            if (chapterCount <= 0) return@drawBehind
            val barH = size.height * 0.55f
            val barTop = (size.height - barH) / 2f
            val locus = chapterIndex.coerceIn(0, chapterCount - 1)
            val slot = size.width / chapterCount

            fun colorFor(i: Int): Color {
                if (i !in cachedIndices) return track
                return if (i < locus) behind else ahead
            }

            // Merge adjacent same-color runs into continuous segments (no gaps/dots).
            var runStart = 0
            var runColor = colorFor(0)
            for (i in 1..chapterCount) {
                val next = if (i < chapterCount) colorFor(i) else null
                if (next != runColor) {
                    drawRect(
                        color = runColor,
                        topLeft = Offset(runStart * slot, barTop),
                        size = Size((i - runStart) * slot, barH),
                    )
                    if (next != null) {
                        runStart = i
                        runColor = next
                    }
                }
            }

            val markW = max(slot, 3.dp.toPx())
            val markH = size.height
            val markX = (locus * slot + (slot - markW) / 2f).coerceIn(0f, size.width - markW)
            drawRect(
                color = accent,
                topLeft = Offset(markX, 0f),
                size = Size(markW, markH),
            )
        },
    )
}

private val CacheBehindGray = Color(0xFF8A8A8A)

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}
