package com.personal.flowreader.ui.plugin

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.api.PluginSitePage
import com.personal.flowreader.plugin.store.SyncMode
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowChip
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowListRow
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import okhttp3.Cookie

/** Sub-tabs inside the Account hub card. */
private enum class AccountHubTab { Account, Urls, Manage }

/** Sign-in form generated from the manifest's `auth.fields`, or the site's page for `auth.web`. */
@Composable
internal fun LoginSheet(
    ui: PluginTabUi,
    onField: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onWebSignedIn: (List<Cookie>) -> Unit,
    onDismiss: () -> Unit,
) {
    val auth = ui.manifest.auth ?: return
    auth.web?.let { web ->
        if (ui.showLogin) {
            WebLoginOverlay(
                title = "${ui.manifest.name} sign in",
                web = web,
                allowedHosts = ui.manifest.allowedHosts,
                busy = ui.busy,
                error = ui.error,
                onSignedIn = onWebSignedIn,
                onDismiss = onDismiss,
            )
        }
        return
    }
    FlowFullscreenCard(
        visible = ui.showLogin,
        onDismiss = onDismiss,
        dismissible = !ui.busy,
        title = "${ui.manifest.name} sign in",
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss, enabled = !ui.busy)
                FlowTextAction("Sign in", onSubmit, enabled = !ui.busy)
            }
        },
    ) {
        FlowHint(auth.note.ifBlank { "Optional. Passwords are not stored — only the site's session cookies." })
        auth.fields.forEachIndexed { i, field ->
            val last = i == auth.fields.lastIndex
            FlowTextField(
                value = ui.loginFields[field.key].orEmpty(),
                onValueChange = { onField(field.key, it) },
                label = field.label,
                visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        field.secret -> KeyboardType.Password
                        field.type == "email" -> KeyboardType.Email
                        else -> KeyboardType.Text
                    },
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
        }
        ui.error?.let { FlowHint(it, error = true) }
    }
}

/**
 * Account hub: Account (sign-in), URLs (site pages), Manage (settings / data / sync).
 * Opening the card makes no network calls; actions are explicit.
 */
@Composable
internal fun AccountSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onOpenSite: (title: String, url: String) -> Unit,
    onSync: (String) -> Unit,
    onSyncNow: () -> Unit,
    onDeleteData: () -> Unit,
) {
    val manifest = ui.manifest
    val twoWay = ui.sync.supported
    val sitePages = remember(manifest) { manifest.resolvedSitePages() }
    val syncChoice = defaultSyncChoice(manifest)
    val showDataActions = ui.session.loggedIn && (twoWay || syncChoice != null)
    var tab by remember { mutableStateOf(AccountHubTab.Account) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(ui.showAccount) {
        if (ui.showAccount) {
            tab = AccountHubTab.Account
            confirmDelete = false
        }
    }
    LaunchedEffect(sitePages.isEmpty()) {
        if (sitePages.isEmpty() && tab == AccountHubTab.Urls) tab = AccountHubTab.Account
    }
    val trail = when (tab) {
        AccountHubTab.Account -> "Account"
        AccountHubTab.Urls -> "URLs"
        AccountHubTab.Manage -> "Manage"
    }
    FlowFullscreenCard(
        visible = ui.showAccount,
        onDismiss = onDismiss,
        title = manifest.name,
        titleTrail = listOf(trail),
        tabs = {
            FlowTabBar(
                tabs = buildList {
                    add(
                        FlowTab.icon(
                            Icons.Filled.Person,
                            "Account",
                            selected = tab == AccountHubTab.Account,
                            onClick = { tab = AccountHubTab.Account },
                            key = "account",
                        ),
                    )
                    if (sitePages.isNotEmpty()) {
                        add(
                            FlowTab.icon(
                                Icons.Filled.Public,
                                "URLs",
                                selected = tab == AccountHubTab.Urls,
                                onClick = { tab = AccountHubTab.Urls },
                                key = "urls",
                            ),
                        )
                    }
                    add(
                        FlowTab.icon(
                            Icons.Filled.Tune,
                            "Manage",
                            selected = tab == AccountHubTab.Manage,
                            onClick = { tab = AccountHubTab.Manage },
                            key = "manage",
                        ),
                    )
                },
                level = FlowTabLevel.Secondary,
                inset = FlowTokens.Pad.CardBody,
            )
        },
    ) {
        ui.error?.let { FlowHint(it, error = true) }
        when (tab) {
            AccountHubTab.Account -> AccountIdentityTab(
                ui = ui,
                onShowLogin = onShowLogin,
                onLogout = onLogout,
            )
            AccountHubTab.Urls -> AccountUrlsTab(
                ui = ui,
                sitePages = sitePages,
                onOpenSite = onOpenSite,
            )
            AccountHubTab.Manage -> AccountManageTab(
                ui = ui,
                twoWay = twoWay,
                syncChoice = syncChoice,
                showDataActions = showDataActions,
                onSync = onSync,
                onSyncNow = onSyncNow,
                onDelete = { confirmDelete = true },
            )
        }
    }
    FlowConfirmCard(
        visible = confirmDelete && ui.showAccount,
        title = "Delete ${manifest.name} data?",
        message = pluginDataSummary(manifest),
        confirmLabel = "Delete",
        destructive = true,
        onConfirm = {
            confirmDelete = false
            onDeleteData()
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun AccountIdentityTab(
    ui: PluginTabUi,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    if (ui.manifest.auth == null) {
        FlowHint("This plugin has no account sign-in.")
        return
    }
    if (ui.session.loggedIn) {
        Text(
            if (ui.session.account.isBlank()) "Signed in" else "Signed in as ${ui.session.account}",
            style = FlowType.rowTitle,
        )
        ui.sessionStaleHint?.let { FlowHint(it) }
        TextButton(onClick = onLogout, enabled = !ui.busy && !ui.sync.running) {
            Text("Sign out", style = FlowType.action)
        }
        TextButton(onClick = onShowLogin, enabled = !ui.busy && !ui.sync.running) {
            Text("Sign in again", style = FlowType.action)
        }
    } else {
        Text("Not signed in", style = FlowType.rowTitle)
        TextButton(onClick = onShowLogin, enabled = !ui.busy) {
            Text("Sign in", style = FlowType.action)
        }
    }
}

@Composable
private fun AccountUrlsTab(
    ui: PluginTabUi,
    sitePages: List<PluginSitePage>,
    onOpenSite: (title: String, url: String) -> Unit,
) {
    FlowHint(
        "Open a page to clear a bot check or refresh the session, then tap Done. " +
            "List sync still runs from Manage → Data.",
    )
    sitePages.forEach { page ->
        FlowListRow(
            title = page.url,
            meta = page.title,
            actions = {
                FlowIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open ${page.title}",
                    onClick = { onOpenSite(page.title, page.url) },
                    enabled = !ui.busy,
                )
            },
        )
    }
}

@Composable
private fun AccountManageTab(
    ui: PluginTabUi,
    twoWay: Boolean,
    syncChoice: String?,
    showDataActions: Boolean,
    onSync: (String) -> Unit,
    onSyncNow: () -> Unit,
    onDelete: () -> Unit,
) {
    val manifest = ui.manifest
    val idle = !ui.busy && !ui.sync.running
    if (manifest.settings.isNotEmpty()) {
        FlowSection("Settings")
        PluginSettingsForm(pluginId = manifest.id)
    }
    FlowSection("Data")
    if (showDataActions) {
        if (twoWay) {
            FlowHint(syncStatus(ui))
            FlowChipRow {
                FlowChip(
                    label = if (ui.sync.running) "Syncing…" else "Sync",
                    selected = ui.sync.running,
                    onClick = onSyncNow,
                    enabled = idle,
                )
                FlowChip(
                    label = "Delete",
                    selected = false,
                    onClick = onDelete,
                    enabled = idle,
                )
            }
            FlowHint(
                "Sync merges lists and reading positions with ${manifest.name}. " +
                    "Delete clears this device’s copy (site unchanged); Sync after that pulls a fresh copy. " +
                    "Also runs after sign-in, pull-to-refresh, and the new-chapter check.",
            )
        } else {
            FlowHint("Import a list from the site. Local stories stay on this device.")
            syncChoice?.let { choice ->
                TextButton(onClick = { onSync(choice) }, enabled = idle) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                    Text(
                        when (choice) {
                            SYNC_ALL_LISTS -> "Import / Replace lists"
                            else -> "Import / Replace ${syncTitle(syncTargets(manifest, choice))}"
                        },
                        style = FlowType.action,
                    )
                }
            }
            FlowChipRow {
                FlowChip(label = "Delete", selected = false, onClick = onDelete, enabled = idle)
            }
            FlowHint("Delete clears this device’s copy (site unchanged).")
        }
    } else {
        FlowHint(pluginDataSummary(manifest))
        FlowChipRow {
            FlowChip(label = "Delete", selected = false, onClick = onDelete, enabled = idle)
        }
    }
}

/** "Last synced 5 minutes ago · 1 reading position to choose · 2 changes waiting to send". */
private fun syncStatus(ui: PluginTabUi): String {
    val s = ui.sync
    val now = System.currentTimeMillis()
    val last = when {
        s.lastSyncedAt <= 0L -> "Not synced yet"
        now - s.lastSyncedAt < DateUtils.MINUTE_IN_MILLIS -> "Last synced just now"
        else -> "Last synced " + DateUtils.getRelativeTimeSpanString(s.lastSyncedAt, now, DateUtils.MINUTE_IN_MILLIS)
    }
    val parts = buildList {
        add(last)
        val conflicts = s.conflicts.size
        if (conflicts > 0) add("$conflicts reading ${if (conflicts == 1) "position" else "positions"} to choose")
        if (s.queued > 0) add("${s.queued} ${if (s.queued == 1) "change" else "changes"} waiting to send")
        val guarded = s.guardedLists.mapNotNull { ui.manifest.list(it)?.title }
        if (guarded.isNotEmpty()) add("${guarded.joinToString(" and ")} looked incomplete; removals skipped")
    }
    return parts.joinToString(" · ")
}

/** Settings > Plugins > {plugin}: sign-in status with Sign in / Sign out. */
@Composable
internal fun PluginAccountSettings(plugin: InstalledPlugin) {
    val app = LocalContext.current.applicationContext as Application
    val vm: PluginTabViewModel = viewModel(
        key = "plugin-account:${plugin.id}:${plugin.manifest.version}",
        factory = PluginTabViewModel.factory(app, plugin.id),
    )
    val ui by vm.ui.collectAsState()
    if (ui.manifest.auth == null) return
    FlowSection("Account")
    Text(
        when {
            !ui.session.loggedIn -> "Not signed in"
            ui.session.account.isBlank() -> "Signed in"
            else -> "Signed in as ${ui.session.account}"
        },
        style = FlowType.rowTitle,
    )
    if (ui.session.loggedIn) {
        ui.sessionStaleHint?.let { FlowHint(it) }
        TextButton(onClick = vm::logout, enabled = !ui.busy) { Text("Sign out", style = FlowType.action) }
    } else {
        TextButton(onClick = { vm.setShowLogin(true) }, enabled = !ui.busy) { Text("Sign in", style = FlowType.action) }
    }
    if (!ui.showLogin) ui.error?.let { FlowHint(it, error = true) }
    LoginSheet(
        ui = ui,
        onField = vm::setLoginField,
        onSubmit = vm::login,
        onWebSignedIn = vm::completeWebLogin,
        onDismiss = { vm.setShowLogin(false) },
    )
}

/** Merge keeps local additions; Overwrite makes the list match the site (also the two-way sync recovery). */
@Composable
internal fun SyncChoiceSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onChoose: (String, SyncMode) -> Unit,
) {
    val listId = ui.syncListId ?: return
    val title = syncTitle(syncTargets(ui.manifest, listId)).ifEmpty { return }
    FlowConfirmCard(
        visible = true,
        title = if (ui.sync.supported) "Replace $title" else "Sync $title",
        message = if (ui.sync.supported) {
            "Overwrite makes $title match ${ui.manifest.name} and removes stories only in the app. " +
                "Merge keeps them and sends them to ${ui.manifest.name} on the next sync."
        } else {
            "Merge keeps stories you added locally. Overwrite makes $title match ${ui.manifest.name}."
        },
        confirmLabel = "Merge",
        onConfirm = { if (!ui.busy) onChoose(listId, SyncMode.Merge) },
        onDismiss = { if (!ui.busy) onDismiss() },
        extraActions = {
            FlowTextAction("Overwrite", { onChoose(listId, SyncMode.Overwrite) }, enabled = !ui.busy)
        },
    )
}
