package com.personal.flowreader.ui.settings

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.PluginUpdatePrefs
import com.personal.flowreader.plugin.updates.UpdateScheduler
import com.personal.flowreader.ui.design.controls.FlowChoiceChips
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import kotlinx.coroutines.launch

/** How often stories with the bell on are checked for new chapters; reschedules on every change. */
@Composable
internal fun PluginUpdatesSection(app: FlowApp) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<PluginUpdatePrefs?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { prefs = app.settings.pluginUpdatePrefsOnce() }
    val current = prefs ?: return

    fun save(next: PluginUpdatePrefs) {
        prefs = next
        scope.launch {
            app.settings.setPluginUpdateInterval(next.intervalHours)
            app.settings.setPluginUpdateWifiOnly(next.wifiOnly)
            UpdateScheduler.apply(app, next)
        }
        if (next.intervalHours > 0) app.requestNotificationPermission()
    }

    FlowLabel("Check every")
    FlowChoiceChips(
        options = PluginUpdatePrefs.INTERVAL_CHOICES,
        selected = current.intervalHours,
        optionLabel = { hours -> intervalLabel(hours) },
        onSelect = { hours -> save(current.copy(intervalHours = hours)) },
    )
    FlowHint("Stories with the bell on (Follow by default) are checked in the background. New chapters are downloaded up to the story's cache level.")
    FlowToggleRow(
        title = "Wi-Fi only",
        subtitle = "Wait for an unmetered network before checking.",
        checked = current.wifiOnly,
        enabled = current.intervalHours > 0,
        onCheckedChange = { on -> save(current.copy(wifiOnly = on)) },
    )
    TextButton(
        enabled = !checking,
        onClick = {
            checking = true
            app.requestNotificationPermission()
            UpdateScheduler.checkNow(app, current.wifiOnly)
        },
    ) { Text(if (checking) "Checking in the background" else "Check now") }
}

private fun intervalLabel(hours: Int): String = when (hours) {
    0 -> "Off"
    24 -> "Daily"
    else -> "${hours}h"
}
