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
        ReaderModalScaffold(
            visible = true,
            contentPadding = PaddingValues(FlowTokens.ModalBodyPadding),
            onDismiss = { if (!ui.busy) vm.setShowLogin(false) },
            scrimAlpha = FlowTokens.ScrimStandard,
        ) {
            Text(
                "Royal Road sign in",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            Text(
                "Optional. Sign in to sync your followed stories into this tab. " +
                    "Password is not stored — only session cookies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(FlowTokens.Space.M))
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
            Spacer(Modifier.height(FlowTokens.Space.S))
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
                Spacer(Modifier.height(FlowTokens.Space.S))
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { vm.setShowLogin(false) }, enabled = !ui.busy) {
                    Text("Cancel")
                }
                TextButton(onClick = vm::login, enabled = !ui.busy) { Text("Sign in") }
            }
        }
    }

    if (ui.showSyncChoice) {
        ReaderModalScaffold(
            visible = true,
            contentPadding = PaddingValues(FlowTokens.ModalBodyPadding),
            onDismiss = { if (!ui.busy) vm.setShowSyncChoice(false) },
            scrimAlpha = FlowTokens.ScrimStandard,
        ) {
            Text(
                "Sync follows",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            Text(
                "Merge keeps stories you added locally. Overwrite makes this tab match your Royal Road follows.",
            )
            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { vm.setShowSyncChoice(false) },
                    enabled = !ui.busy,
                ) { Text("Cancel") }
                TextButton(
                    onClick = { vm.syncFollows(FollowsSyncMode.Overwrite) },
                    enabled = !ui.busy,
                ) { Text("Overwrite") }
                TextButton(
                    onClick = { vm.syncFollows(FollowsSyncMode.Merge) },
                    enabled = !ui.busy,
                ) { Text("Merge") }
            }
        }
    }
}

@Composable
internal fun AddStoryOverlay(
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
        contentPadding = PaddingValues(
            start = FlowTokens.ModalOuterPadding,
            end = FlowTokens.ModalOuterPadding,
            bottom = FlowTokens.ModalOuterPadding,
        ),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Add from Royal Road",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.S),
            )
            Text(
                "Search the site or paste a fiction URL. Opening a story shows its cover card — " +
                    "then choose Follow, Favorite, or Read Later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = FlowTokens.Space.L,
                    end = FlowTokens.Space.L,
                    bottom = FlowTokens.Space.S,
                ),
            )
            OutlinedTextField(
                value = ui.remoteQuery,
                onValueChange = onRemoteQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlowTokens.Space.L),
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
                    .padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.S),
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
                    modifier = Modifier.padding(horizontal = FlowTokens.Space.L),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ui.searchResults.forEach { work ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !ui.busy) { onOpenResult(work) }
                        .padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.M),
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
internal fun AccountOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(FlowTokens.ModalBodyPadding),
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
            modifier = Modifier.padding(top = FlowTokens.Space.S, bottom = FlowTokens.Space.L),
        )
        if (ui.loggedIn) {
            Text(ui.loginEmail.ifBlank { "Signed in" }, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onSync, enabled = !ui.busy) {
                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                Text("Sync follows")
            }
            TextButton(onClick = onLogout, enabled = !ui.busy) { Text("Sign out") }
        } else {
            TextButton(onClick = onShowLogin) { Text("Sign in") }
        }
        TextButton(onClick = onDismiss) { Text("Close") }
    }
}

