package com.personal.flowreader.ui.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.personal.flowreader.FlowApp
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import kotlinx.coroutines.launch

/** Settings > About > Data: storage and download defaults. */
@Composable
internal fun DataSettingsPane() {
    val app = LocalContext.current.applicationContext as FlowApp
    FlowSection("Plugin story downloads")
    PluginCacheDefaultsSection(app)
}

/** Cache level and cleanup new plugin stories start with; each story can change its own. */
@Composable
private fun PluginCacheDefaultsSection(app: FlowApp) {
    val scope = rememberCoroutineScope()
    var levelDraft by remember { mutableStateOf<String?>(null) }
    var cleanup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val defaults = app.settings.pluginCacheDefaultsOnce()
        levelDraft = defaults.cacheLevel.toString()
        cleanup = defaults.cleanup
    }
    val draft = levelDraft ?: return
    FlowHint("Defaults for stories added from a plugin. Long-press a story's Download to change its own.")
    FlowTextField(
        value = draft,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            levelDraft = digits
            digits.toIntOrNull()?.let { scope.launch { app.settings.setPluginCacheLevel(it) } }
        },
        label = "Cache level",
        supportingText = "Chapters downloaded ahead of your reading position.",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    FlowToggleRow(
        title = "Clean up old chapters",
        subtitle = "Delete chapters more than the cache level behind your position.",
        checked = cleanup,
        onCheckedChange = { on ->
            cleanup = on
            scope.launch { app.settings.setPluginCacheCleanup(on) }
        },
    )
}
