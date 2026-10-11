package com.personal.flowreader.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.TabStyle
import com.personal.flowreader.data.setLibraryTabStyle
import com.personal.flowreader.data.setLibraryViewMode
import com.personal.flowreader.data.setSettingsTabStyle
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowImageTabs
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.launch

/** Settings-card state shared with its sections: tab style down, selected sub-tab label up to the header. */
@Stable
internal class SettingsChrome(val textTabs: Boolean) {
    var subTab by mutableStateOf<String?>(null)
}

internal val LocalSettingsChrome = staticCompositionLocalOf { SettingsChrome(textTabs = false) }

/** Layout › UI: library book layout and the tab style of the Library and Settings bars. */
@Composable
internal fun ChromeSettingsPane() {
    val app = LocalContext.current.applicationContext as FlowApp
    val prefs by app.chrome.collectAsState()
    ChoiceRow("Library view", LibraryViewMode.entries, prefs.libraryView, { it.name }) {
        app.appScope.launch { app.settings.setLibraryViewMode(it) }
    }
    ChoiceRow("Library tabs", TabStyle.entries, prefs.libraryTabs, { it.label }) {
        app.appScope.launch { app.settings.setLibraryTabStyle(it) }
    }
    ChoiceRow("Settings tabs", TabStyle.entries, prefs.settingsTabs, { it.label }) {
        app.appScope.launch { app.settings.setSettingsTabStyle(it) }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Spacer(Modifier.height(FlowTokens.Space.M))
    FlowLabel(title)
    FlowChipRow {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** A section's sub-tab bar from silhouette drawables and labels; names the selected one in the header. */
@Composable
internal fun SettingsSubTabBar(tabs: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    val text = LocalSettingsChrome.current.textTabs
    SettingsSubTabBar(flowImageTabs(tabs, selected, text, onSelect), tabs[selected].second)
}

/** A section's sub-tab bar from prebuilt [tabs]; [selectedLabel] is shown in the header. */
@Composable
internal fun SettingsSubTabBar(tabs: List<FlowTab>, selectedLabel: String) {
    val chrome = LocalSettingsChrome.current
    SideEffect { chrome.subTab = selectedLabel }
    FlowTabBar(tabs = tabs, level = FlowTabLevel.Secondary, inset = FlowTokens.Space.None)
}
