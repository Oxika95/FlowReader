package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.api.PluginLibrary
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/** Server address (when `serverSetting`) then username/password; password stays in memory only. */
@Composable
internal fun FieldAuthForm(
    ui: PluginTabUi,
    onField: (String, String) -> Unit,
    onServerChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val auth = ui.manifest.auth ?: return
    if (auth.web != null || auth.fields.isEmpty()) return
    val serverKey = ui.manifest.serverSettingKey()
    val serverSetting = serverKey?.let { key -> ui.manifest.settings.firstOrNull { it.key == key } }
    FlowHint(auth.note.ifBlank { "Passwords are not stored — only the access tokens." })
    if (serverSetting != null) {
        FlowTextField(
            value = ui.serverDraft,
            onValueChange = onServerChange,
            label = serverSetting.label,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        if (serverSetting.description.isNotBlank()) FlowHint(serverSetting.description)
    }
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
}

/** Optional `listLibraries` / `setLibraryEnabled` toggles for multi-library servers. */
@Composable
internal fun LibraryTogglesSection(pluginId: String, onChanged: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as FlowApp
    val scope = rememberCoroutineScope()
    var libs by remember(pluginId) { mutableStateOf<List<PluginLibrary>?>(null) }
    var loadError by remember(pluginId) { mutableStateOf<String?>(null) }
    var supported by remember(pluginId) { mutableStateOf(true) }
    LaunchedEffect(pluginId) {
        runCatching {
            withContext(Dispatchers.IO) {
                val src = app.pluginManager.source(pluginId)
                if (!src.supportsLibraries()) {
                    supported = false
                    emptyList()
                } else {
                    src.listLibraries()
                }
            }
        }.onSuccess {
            libs = it
            loadError = null
        }.onFailure {
            loadError = it.message ?: "Could not load libraries"
            libs = emptyList()
        }
    }
    if (!supported) return
    FlowSection("Libraries")
    loadError?.let { FlowHint(it, error = true) }
    val rows = libs
    if (rows == null) {
        FlowHint("Loading libraries…")
        return
    }
    if (rows.isEmpty() && loadError == null) {
        FlowHint("No libraries on this server.")
        return
    }
    FlowHint("Turn off libraries to hide them from Catalog and search.")
    rows.forEach { lib ->
        FlowToggleRow(
            title = lib.title,
            subtitle = "",
            checked = lib.enabled,
            onCheckedChange = { on ->
                val id = lib.id
                libs = (libs ?: rows).map { if (it.id == id) it.copy(enabled = on) else it }
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            app.pluginManager.source(pluginId).setLibraryEnabled(id, on)
                        }
                        onChanged()
                    }.onFailure { loadError = it.message ?: "Could not update library" }
                }
            },
        )
    }
}
