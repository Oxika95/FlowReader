package com.personal.flowreader.ui.settings

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.CheckInterval
import com.personal.flowreader.data.PluginUpdatePrefs
import com.personal.flowreader.data.PluginVersionCheckPrefs
import com.personal.flowreader.plugin.updates.UpdateScheduler
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowSliderRow
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Settings > About > Notifications: background checks that notify (new chapters, plugin updates). */
@Composable
internal fun NotificationSettingsPane() {
    val app = LocalContext.current.applicationContext as FlowApp
    FlowSection("New chapters")
    ChapterCheckSection(app)
    HorizontalDivider()
    FlowSection("Plugin updates")
    PluginVersionCheckSection(app)
}

/** How often stories with the bell on are checked for new chapters; reschedules on every change. */
@Composable
private fun ChapterCheckSection(app: FlowApp) {
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

    CheckIntervalSlider(current.intervalHours) { save(current.copy(intervalHours = it)) }
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

/** How often repositories are checked for newer plugin versions, and whether that notifies. */
@Composable
private fun PluginVersionCheckSection(app: FlowApp) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<PluginVersionCheckPrefs?>(null) }
    LaunchedEffect(Unit) { prefs = app.settings.pluginVersionCheckPrefsOnce() }
    val current = prefs ?: return

    fun save(next: PluginVersionCheckPrefs) {
        prefs = next
        scope.launch {
            app.settings.setPluginVersionCheckInterval(next.intervalHours)
            app.settings.setPluginVersionNotify(next.notify)
            UpdateScheduler.applyVersionCheck(app, next)
        }
        if (next.intervalHours > 0 && next.notify) app.requestNotificationPermission()
    }

    CheckIntervalSlider(current.intervalHours) { save(current.copy(intervalHours = it)) }
    FlowToggleRow(
        title = "Notify about updates",
        subtitle = "Once per new version. Install updates from Settings › Plugins.",
        checked = current.notify,
        enabled = current.intervalHours > 0,
        onCheckedChange = { on -> save(current.copy(notify = on)) },
    )
}

/** 0–24 h in whole hours (0 = off); saves when the drag ends. */
@Composable
private fun CheckIntervalSlider(hours: Int, onSave: (Int) -> Unit) {
    var draft by remember(hours) { mutableFloatStateOf(hours.toFloat()) }
    val shown = draft.roundToInt()
    FlowSliderRow(
        label = "Check every",
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { if (shown != hours) onSave(shown) },
        valueRange = 0f..CheckInterval.MAX_HOURS.toFloat(),
        steps = CheckInterval.MAX_HOURS - 1,
        valueLabel = if (shown == 0) "Off" else "${shown}h",
        startCaption = "Off",
        endCaption = "24h",
    )
}
