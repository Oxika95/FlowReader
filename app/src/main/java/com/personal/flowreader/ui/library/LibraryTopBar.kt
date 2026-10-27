package com.personal.flowreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.data.CustomLibraryTab
import com.personal.flowreader.data.LibraryTabId
import com.personal.flowreader.data.TabStyle
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.flowBreadcrumb
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

/** App title naming the current tab, settings action, then the primary tab bar ending in the "+" action tab. */
@Composable
internal fun LibraryTopBar(
    tab: LibraryTabId,
    plugins: List<InstalledPlugin>,
    customTabs: List<CustomLibraryTab>,
    tabStyle: TabStyle,
    addTabOpen: Boolean,
    onTab: (LibraryTabId) -> Unit,
    onAddTab: () -> Unit,
    onSettings: () -> Unit,
) {
    val notifyDots by rememberPluginNotifyDots(plugins)
    val pluginIcons = rememberPluginTabIcons(plugins)
    val text = tabStyle == TabStyle.Text
    fun selected(id: LibraryTabId) = !addTabOpen && tab == id
    fun fixed(icon: ImageVector, label: String, id: LibraryTabId) =
        if (text) {
            FlowTab.text(label, selected = selected(id), onClick = { onTab(id) }, key = id)
        } else {
            FlowTab.icon(icon, label, selected(id), { onTab(id) }, key = id)
        }
    val tabs = buildList {
        add(fixed(LibraryTabIcons.Files, "Files", LibraryTabId.Files))
        add(fixed(LibraryTabIcons.Queue, "Queue", LibraryTabId.Que))
        customTabs.sortedBy { it.order }.forEach {
            val id = LibraryTabId.Custom(it.id)
            add(FlowTab.text(it.title, selected = selected(id), onClick = { onTab(id) }, key = id))
        }
        plugins.forEach { plugin ->
            val id = LibraryTabId.Plugin(plugin.id)
            val badge = notifyDots[plugin.id]
            val icon = pluginIcons[plugin.id]?.takeUnless { text }
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
            val current = when (tab) {
                LibraryTabId.Files -> "Files"
                LibraryTabId.Que -> "Queue"
                is LibraryTabId.Custom -> customTabs.firstOrNull { it.id == tab.tabId }?.title
                is LibraryTabId.Plugin -> plugins.firstOrNull { it.id == tab.pluginId }?.name
            }
            Text(
                flowBreadcrumb("Flow Reader", listOfNotNull(current)),
                style = FlowType.cardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
