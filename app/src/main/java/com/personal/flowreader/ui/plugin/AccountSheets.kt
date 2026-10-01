package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Sign-in form generated from the manifest's `auth.fields`. */
@Composable
internal fun LoginSheet(
    ui: PluginTabUi,
    onField: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val auth = ui.manifest.auth ?: return
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

/** Account status, per-list sync, plugin settings, sign out. */
@Composable
internal fun AccountSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onSync: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val manifest = ui.manifest
    FlowFullscreenCard(
        visible = ui.showAccount,
        onDismiss = onDismiss,
        title = if (manifest.auth != null) "${manifest.name} account" else manifest.name,
    ) {
        FlowHint("Local stories stay on this device. Sync imports a list from the site when you choose.")
        if (manifest.auth != null) {
            if (ui.session.loggedIn) {
                Text(ui.session.account.ifBlank { "Signed in" }, style = FlowType.rowTitle)
                manifest.lists.filter { it.syncable }.forEach { list ->
                    TextButton(onClick = { onSync(list.id) }, enabled = !ui.busy) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = FlowTokens.Space.S))
                        Text("Sync ${list.title}", style = FlowType.action)
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

/** Merge keeps local additions; Overwrite makes the list match the site. */
@Composable
internal fun SyncChoiceSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onChoose: (String, SyncMode) -> Unit,
) {
    val listId = ui.syncListId ?: return
    val list = ui.manifest.list(listId) ?: return
    FlowConfirmCard(
        visible = true,
        title = "Sync ${list.title}",
        message = "Merge keeps stories you added locally. Overwrite makes ${list.title} match ${ui.manifest.name}.",
        confirmLabel = "Merge",
        onConfirm = { if (!ui.busy) onChoose(listId, SyncMode.Merge) },
        onDismiss = { if (!ui.busy) onDismiss() },
        extraActions = {
            FlowTextAction("Overwrite", { onChoose(listId, SyncMode.Overwrite) }, enabled = !ui.busy)
        },
    )
}
