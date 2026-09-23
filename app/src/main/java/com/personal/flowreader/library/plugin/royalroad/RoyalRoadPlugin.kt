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
import androidx.compose.ui.platform.LocalContext
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
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.LibrarySourcePlugin
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.ui.common.BookHeroPrimaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButtonRow
import com.personal.flowreader.ui.common.BookHeroSplashButtons
import com.personal.flowreader.ui.common.BookHeroSplashShell
import com.personal.flowreader.ui.common.FlowSlotTab
import com.personal.flowreader.ui.common.FlowSlotTabBar
import com.personal.flowreader.ui.common.FlowSlotTabLabel
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.library.LibraryBooksPane
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.chrome.ReaderPanelFeather
import com.personal.flowreader.ui.theme.FlowTokens

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
    val app = LocalContext.current.applicationContext as FlowApp
    val pendingShareUrl by app.pendingRoyalRoadShareUrl.collectAsState()
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshLocal()
        }
    }
    LaunchedEffect(pendingShareUrl) {
        val url = pendingShareUrl ?: return@LaunchedEffect
        app.pendingRoyalRoadShareUrl.value = null
        vm.setUrlDraft(url)
        vm.openUrl()
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
                inset = FlowTokens.ScreenGutter,
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
                    .padding(end = FlowTokens.Space.L, bottom = FlowTokens.Space.L)
                    .size(FlowTokens.FabSize),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add story", modifier = Modifier.size(FlowTokens.FabIcon))
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
    val kinds = RoyalRoadListKind.entries
    val slotTabs = buildList {
        kinds.forEach { kind ->
            val selectedTab = !accountSelected && selected == kind
            add(
                FlowSlotTab(
                    selected = selectedTab,
                    onClick = { onSelect(kind) },
                    measureLabel = kind.label,
                    content = { FlowSlotTabLabel(kind.label, it) },
                ),
            )
        }
        add(
            FlowSlotTab(
                selected = accountSelected,
                onClick = onAccount,
                measureLabel = null,
                content = { sel ->
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = if (loggedIn) "Account" else "Sign in",
                        modifier = Modifier.size(FlowTokens.Icon.L),
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
    FlowSlotTabBar(tabs = slotTabs)
}
