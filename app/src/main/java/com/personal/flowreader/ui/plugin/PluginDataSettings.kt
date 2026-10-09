package com.personal.flowreader.ui.plugin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.store.PluginDataEraser
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.theme.FlowType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deletes all of [plugin]'s data on this device, WebView sign-in cookies included; returns stories removed. */
internal suspend fun erasePluginData(app: FlowApp, plugin: InstalledPlugin): Int {
    val removed = withContext(Dispatchers.IO) { PluginDataEraser(app).erase(plugin.id) }
    if (plugin.manifest.auth?.web != null) WebLoginCookieStore.clear(plugin.manifest.allowedHosts)
    return removed
}

/** What "delete data" removes, for confirm cards. */
internal fun pluginDataSummary(plugin: InstalledPlugin): String =
    "Removes its stories, downloads, lists, reading positions, sync history, settings" +
        (if (plugin.manifest.auth != null) " and sign-in" else "") +
        " from this device. Nothing changes on ${plugin.name}."

/** Settings > Plugins > (plugin): Data section with "Delete data". */
@Composable
internal fun PluginDataSettings(plugin: InstalledPlugin) {
    val app = LocalContext.current.applicationContext as FlowApp
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    FlowSection("Data")
    TextButton(onClick = { confirm = true }, enabled = !working) {
        Text("Delete ${plugin.name} data", style = FlowType.action, color = MaterialTheme.colorScheme.error)
    }
    status?.let { FlowHint(it) }
    FlowConfirmCard(
        visible = confirm,
        title = "Delete ${plugin.name} data?",
        message = pluginDataSummary(plugin),
        confirmLabel = "Delete",
        destructive = true,
        onConfirm = {
            confirm = false
            working = true
            scope.launch {
                status = runCatching { erasePluginData(app, plugin) }.fold(
                    onSuccess = { n -> "Deleted ${plugin.name} data ($n ${if (n == 1) "story" else "stories"})" },
                    onFailure = { it.message ?: "Could not delete ${plugin.name} data" },
                )
                working = false
            }
        },
        onDismiss = { confirm = false },
    )
}
