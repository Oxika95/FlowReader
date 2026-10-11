package com.personal.flowreader.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Plugin id → new chapters across its stories, observed from each plugin's database. */
@Composable
internal fun rememberPluginNewChapterTotals(plugins: List<InstalledPlugin>): State<Map<String, Int>> {
    val app = LocalContext.current.applicationContext as FlowApp
    val ids = plugins.map { it.id }
    return produceState(initialValue = emptyMap(), ids) {
        if (ids.isEmpty()) return@produceState
        val totals = ids.map { id -> app.pluginBooks.newChapterCounts(id).map { counts -> id to counts.values.sum() } }
        combine(totals) { it.toMap() }.collect { value = it }
    }
}
