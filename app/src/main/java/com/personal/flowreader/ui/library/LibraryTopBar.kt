package com.personal.flowreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.personal.flowreader.data.CustomLibraryTab
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** App title, view-mode and settings actions, then the primary tab bar ending in the "+" action tab. */
@Composable
internal fun LibraryTopBar(
    tab: LibraryTabId,
    plugins: List<InstalledPlugin>,
    customTabs: List<CustomLibraryTab>,
    viewMode: LibraryViewMode,
    addTabOpen: Boolean,
    onTab: (LibraryTabId) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onAddTab: () -> Unit,
    onSettings: () -> Unit,
) {
    val newCounts by rememberPluginNewChapterTotals(plugins)
    val pluginIcons = rememberPluginTabIcons(plugins)
    fun selected(id: LibraryTabId) = !addTabOpen && tab == id
    val tabs = buildList {
        add(FlowTab.icon(LibraryTabIcons.Files, "Files", selected(LibraryTabId.Files), { onTab(LibraryTabId.Files) }, key = LibraryTabId.Files))
        add(FlowTab.icon(LibraryTabIcons.Queue, "Queue", selected(LibraryTabId.Que), { onTab(LibraryTabId.Que) }, key = LibraryTabId.Que))
        customTabs.sortedBy { it.order }.forEach {
            val id = LibraryTabId.Custom(it.id)
            add(FlowTab.text(it.title, selected = selected(id), onClick = { onTab(id) }, key = id))
        }
        plugins.forEach { plugin ->
            val id = LibraryTabId.Plugin(plugin.id)
            val badge = newCounts[plugin.id]
            val icon = pluginIcons[plugin.id]
            add(
                if (icon != null) {
                    FlowTab.image(icon, plugin.name, selected(id), { onTab(id) }, key = id, badge = badge)
                } else {
                    FlowTab.text(plugin.name, selected = selected(id), onClick = { onTab(id) }, key = id, badge = badge)
                },
            )
        }
        add(FlowTab.action(Icons.Filled.Add, "Add tab", open = addTabOpen, onClick = onAddTab))
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FlowTokens.Pad.Screen, end = FlowTokens.Space.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Flow Reader", style = FlowType.cardTitle, modifier = Modifier.weight(1f))
            if (tab != LibraryTabId.Que) {
                val accent = MaterialTheme.colorScheme.primary
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                FlowIconButton(
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "List view",
                    onClick = { onViewMode(LibraryViewMode.List) },
                    tint = if (viewMode == LibraryViewMode.List) accent else muted,
                )
                FlowIconButton(
                    icon = Icons.Filled.GridView,
                    contentDescription = "Shelf view",
                    onClick = { onViewMode(LibraryViewMode.Shelf) },
                    tint = if (viewMode == LibraryViewMode.Shelf) accent else muted,
                )
            }
            FlowIconButton(icon = Icons.Filled.Settings, contentDescription = "Settings", onClick = onSettings)
        }
        FlowTabBar(tabs = tabs)
    }
}

@Composable
internal fun AddTabOverlay(
    visible: Boolean,
    plugins: List<InstalledPlugin>,
    enabledIds: Set<String>,
    customTabs: List<CustomLibraryTab>,
    onSetEnabled: (String, Boolean) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customTitle by remember(visible) { mutableStateOf("") }
    FlowFullscreenCard(visible = visible, onDismiss = onDismiss, title = "Add a tab") {
        FlowSection("Custom shelf")
        FlowHint("Create a shelf and choose it as a Router destination under Import settings.")
        FlowTextField(value = customTitle, onValueChange = { customTitle = it }, label = "Tab name")
        FlowActionRow {
            FlowTextAction(
                "Add shelf",
                {
                    val name = customTitle.trim()
                    if (name.isNotEmpty()) {
                        onAddCustom(name)
                        customTitle = ""
                    }
                },
                enabled = customTitle.trim().isNotEmpty(),
            )
        }
        customTabs.sortedBy { it.order }.forEach { tab ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.title, style = FlowType.rowTitle, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveCustom(tab.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${tab.title}")
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = FlowTokens.Space.S))

        FlowSection("Plugins")
        FlowHint(
            if (plugins.isEmpty()) {
                "No plugins installed. Browse repositories in Settings > Plugins."
            } else {
                "Enable a plugin to add its tab next to Files and Queue."
            },
        )
        plugins.forEach { plugin ->
            val on = plugin.id in enabledIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetEnabled(plugin.id, !on) }
                    .padding(vertical = FlowTokens.Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(plugin.name, style = FlowType.rowTitle)
                    if (plugin.manifest.description.isNotBlank()) FlowHint(plugin.manifest.description)
                }
                TextButton(onClick = { onSetEnabled(plugin.id, !on) }) {
                    Text(if (on) "Remove" else "Add", style = FlowType.action)
                }
            }
        }
    }
}
