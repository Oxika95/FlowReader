package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.api.PluginSetting
import com.personal.flowreader.plugin.api.PluginSettingType
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Settings declared in the plugin manifest, persisted by the app (`flow.settings`). */
@Composable
internal fun PluginSettingsSheet(
    visible: Boolean,
    pluginId: String,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as FlowApp
    val manifest = app.pluginManager.get(pluginId)?.manifest
    FlowFullscreenCard(
        visible = visible && manifest != null,
        onDismiss = onDismiss,
        title = "${manifest?.name.orEmpty()} settings",
    ) {
        PluginSettingsForm(pluginId = pluginId)
    }
}

@Composable
fun PluginSettingsForm(pluginId: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as FlowApp
    val manifest = app.pluginManager.get(pluginId)?.manifest ?: return
    var revision by remember(pluginId) { mutableIntStateOf(0) }
    val values = remember(pluginId, revision) { app.pluginManager.settingsValues(pluginId) }
    val save: (String, String) -> Unit = { key, value ->
        app.pluginManager.setSetting(pluginId, key, value)
        revision++
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S)) {
        if (manifest.settings.isEmpty()) FlowHint("This plugin has no settings.")
        manifest.settings.forEach { setting ->
            SettingRow(setting, values.opt(setting.key)?.toString().orEmpty(), save)
        }
    }
}

@Composable
private fun SettingRow(setting: PluginSetting, current: String, save: (String, String) -> Unit) {
    val description = setting.description.takeIf { it.isNotBlank() }
    when (setting.type) {
        PluginSettingType.Toggle -> FlowToggleRow(
            title = setting.label,
            subtitle = setting.description,
            checked = current.equals("true", ignoreCase = true),
            onCheckedChange = { save(setting.key, it.toString()) },
        )
        PluginSettingType.Integer -> {
            var draft by remember(setting.key, current) { mutableStateOf(current) }
            FlowTextField(
                value = draft,
                onValueChange = { raw ->
                    draft = raw.filter { it.isDigit() || it == '-' }.take(9)
                    draft.toIntOrNull()?.let { n ->
                        val clamped = n.coerceIn(setting.min ?: Int.MIN_VALUE, setting.max ?: Int.MAX_VALUE)
                        save(setting.key, clamped.toString())
                    }
                },
                label = setting.label,
                supportingText = description,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        PluginSettingType.Text -> {
            var draft by remember(setting.key, current) { mutableStateOf(current) }
            FlowTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    save(setting.key, it)
                },
                label = setting.label,
                supportingText = description,
            )
        }
        PluginSettingType.Choice -> Column(Modifier.fillMaxWidth()) {
            Text(setting.label, style = FlowType.rowTitle)
            if (description != null) FlowHint(description)
            setting.options.forEach { option ->
                val selected = current == option.value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { save(setting.key, option.value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(option.label, style = FlowType.body)
                }
            }
        }
    }
}
