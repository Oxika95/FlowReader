package com.personal.flowreader.ui.plugin

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.store.SyncMode
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import okhttp3.Cookie

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

/** Account status, two-way sync (or per-list import), plugin settings, sign out. */
@Composable
internal fun AccountSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: (String) -> Unit,
    onSyncNow: () -> Unit,
    onSettings: () -> Unit,
) {
    val manifest = ui.manifest
    val twoWay = ui.sync.supported
    FlowFullscreenCard(
        visible = ui.showAccount,
        onDismiss = onDismiss,
        title = if (manifest.auth != null) "${manifest.name} account" else manifest.name,
    ) {
        FlowHint(
            if (twoWay) {
                "Lists and reading positions sync both ways with ${manifest.name}: on the new-chapter check, " +
                    "when you open this tab, and on Sync now."
            } else {
                "Local stories stay on this device. Sync imports a list from the site when you choose."
            },
        )
        if (manifest.auth != null) {
            if (ui.session.loggedIn) {
                Text(ui.session.account.ifBlank { "Signed in" }, style = FlowType.rowTitle)
                if (twoWay) {
                    TextButton(onClick = onSyncNow, enabled = !ui.busy && !ui.sync.running) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                        Text(if (ui.sync.running) "Syncing…" else "Sync now", style = FlowType.action)
                    }
                    FlowHint(syncStatus(ui))
                }
                defaultSyncChoice(manifest)?.let { choice ->
                    TextButton(onClick = { onSync(choice) }, enabled = !ui.busy && !ui.sync.running) {
                        if (!twoWay) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                        }
                        Text(
                            when {
                                twoWay -> "Replace with site lists"
                                choice == SYNC_ALL_LISTS -> "Sync lists"
                                else -> "Sync ${syncTitle(syncTargets(manifest, choice))}"
                            },
                            style = FlowType.action,
                        )
                    }
                }
                TextButton(onClick = onLogout, enabled = !ui.busy) { Text("Sign out", style = FlowType.action) }
            } else {
                TextButton(onClick = onShowLogin) { Text("Sign in", style = FlowType.action) }
            }
        }
        if (manifest.settings.isNotEmpty()) {
            TextButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                Text("Plugin settings", style = FlowType.action)
            }
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
