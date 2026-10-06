package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowTextAction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.personal.flowreader.share.RouterContentKind
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.RouterRule
import com.personal.flowreader.share.ShareUrlMatch
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.UUID

private fun routerRuleSummary(
    rule: RouterRule,
    plugins: List<SharePluginOption>,
    customTitles: Map<String, String>,
): String {
    val dest = when (rule.destination.id) {
        RouterLanding.PLUGIN -> {
            val name = plugins.firstOrNull { it.id == rule.pluginId }?.title
                ?: rule.pluginId
                ?: "Plugin"
            "Plugin · $name"
        }
        else -> rule.destination.label(customTitles)
    }
    return when (rule.kind) {
        RouterContentKind.BookFile -> "Book files → $dest"
        RouterContentKind.RawText -> "Raw text → $dest"
        RouterContentKind.Url -> {
            val parse = if (rule.parseUrl) "Parse" else "Pass-through"
            "$parse → $dest"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReorderableRouterList(
    rules: List<RouterRule>,
    plugins: List<SharePluginOption>,
    customTitles: Map<String, String>,
    onSave: (List<RouterRule>) -> Unit,
    onEdit: (RouterRule) -> Unit,
) {
    fun saveOrdered(list: List<RouterRule>) {
        onSave(list.mapIndexed { index, rule -> rule.copy(order = index) })
    }
    var menuRuleId by remember { mutableStateOf<String?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val working = remember { mutableStateListOf<RouterRule>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    if (reordering) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    saveOrdered(working.toList())
                    reordering = false
                    draggingId = null
                    dragOffset = 0f
                },
            ) { Text("Done") }
        }
    }

    val shown = if (reordering) working else rules
    shown.forEach { rule ->
        key(rule.id) {
            val dragging = reordering && draggingId == rule.id
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                    .onSizeChanged { rowHeights[rule.id] = it.height },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (reordering) Modifier
                            else Modifier.combinedClickable(
                                onClick = { onEdit(rule) },
                                onLongClick = { menuRuleId = rule.id },
                            ),
                        )
                        .padding(vertical = FlowTokens.Space.S),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (reordering) {
                        DragHandle(
                            ruleId = rule.id,
                            working = working,
                            rowHeights = rowHeights,
                            idOf = { it.id },
                            draggingId = { draggingId = it },
                            dragOffset = dragOffset,
                            onDragOffset = { dragOffset = it },
                        )
                    }
                    Column(Modifier.weight(1f).padding(end = FlowTokens.Space.S)) {
                        Text(
                            when (rule.kind) {
                                RouterContentKind.Url -> ShareUrlMatch.formatMatch(rule)
                                else -> rule.kind.label
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            routerRuleSummary(rule, plugins, customTitles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!reordering) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 16.dp,
                        ) {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                                },
                            )
                            IconButton(
                                onClick = { saveOrdered(rules.filterNot { it.id == rule.id }) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuRuleId == rule.id,
                    onDismissRequest = { menuRuleId = null },
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            menuRuleId = null
                            val index = rules.indexOfFirst { it.id == rule.id }
                            val copy = rule.copy(id = UUID.randomUUID().toString())
                            saveOrdered(rules.toMutableList().apply { add(index + 1, copy) })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move") },
                        onClick = {
                            menuRuleId = null
                            working.clear()
                            working.addAll(rules)
                            reordering = true
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouterRuleEditorOverlay(
    initial: RouterRule,
    isNew: Boolean,
    plugins: List<SharePluginOption>,
    customTabs: List<SharePluginOption>,
    onDismiss: () -> Unit,
    onSave: (RouterRule) -> Unit,
) {
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var matchText by remember(initial.id) {
        mutableStateOf(ShareUrlMatch.formatMatch(initial))
    }
    var matchIsRegex by remember(initial.id) { mutableStateOf(initial.pathIsRegex) }
    var allowWildcard by remember(initial.id) { mutableStateOf(initial.matchSubdomains) }
    var parseUrl by remember(initial.id) { mutableStateOf(initial.parseUrl) }
    var destination by remember(initial.id) { mutableStateOf(initial.destination) }
    var pluginId by remember(initial.id) {
        mutableStateOf(initial.pluginId ?: plugins.firstOrNull()?.id.orEmpty())
    }
    var pluginMenuOpen by remember { mutableStateOf(false) }
    val pluginTitle = plugins.firstOrNull { it.id == pluginId }?.title
        ?: plugins.firstOrNull()?.title
        ?: "No plugins"
    val customTitles = remember(customTabs) {
        customTabs.associate { it.id to it.title }
    }
    val destinations = remember(customTabs) {
        RouterLanding.tabChoices(customTabs.map { it.id })
    }

    fun build(): RouterRule {
        val parsed = if (kind == RouterContentKind.Url) {
            ShareUrlMatch.parseMatchInput(matchText, matchIsRegex)
        } else {
            ShareUrlMatch.ParsedMatch(host = "*", path = null, isRegex = false)
        }
        return initial.copy(
            kind = kind,
            hostPattern = if (kind == RouterContentKind.Url) parsed.host else "*",
            pathPattern = if (kind == RouterContentKind.Url) parsed.path else null,
            pathIsRegex = kind == RouterContentKind.Url && parsed.isRegex,
            matchSubdomains = kind == RouterContentKind.Url && !parsed.isRegex && allowWildcard,
            parseUrl = kind == RouterContentKind.Url && parseUrl,
            destination = destination,
            pluginId = if (destination.id == RouterLanding.PLUGIN) {
                pluginId.ifBlank { plugins.firstOrNull()?.id }
            } else {
                null
            },
        )
    }

    val canSave = (kind != RouterContentKind.Url || matchText.isNotBlank()) &&
        (destination.id != RouterLanding.PLUGIN || plugins.isNotEmpty())
    FlowFullscreenCard(
        visible = true,
        onDismiss = onDismiss,
        title = if (isNew) "New Router Rule" else "Edit Router Rule",
        bodySpacing = Arrangement.Top,
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Save", { if (canSave) onSave(build()) }, enabled = canSave)
            }
        },
    ) {
        FlowLabel("Content")
        FlowChipRow {
            RouterContentKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = {
                        kind = k
                        if (k != RouterContentKind.Url && destination.id == RouterLanding.PLUGIN) {
                            destination = RouterLanding.Queue
                        }
                    },
                    label = { Text(k.label) },
                )
            }
        }

        if (kind == RouterContentKind.Url) {
            Spacer(Modifier.height(FlowTokens.Space.M))
            MatchFields(
                matchText = matchText,
                onMatchText = { matchText = it },
                matchIsRegex = matchIsRegex,
                onMatchIsRegex = { matchIsRegex = it },
                allowWildcard = allowWildcard,
                onAllowWildcard = { allowWildcard = it },
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowToggleRow(
                title = "Parse page",
                subtitle = "Fetch the URL and extract text (Parser tab CSS). Off = pass the URL string through.",
                checked = parseUrl,
                onCheckedChange = { parseUrl = it },
            )
        } else {
            Spacer(Modifier.height(FlowTokens.Space.S))
            Text(
                when (kind) {
                    RouterContentKind.BookFile ->
                        "ePub / TXT from the library picker or Open with."
                    RouterContentKind.RawText ->
                        "Clipboard paste and shared text with no URL."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(FlowTokens.Space.M))
        FlowLabel("Destination")
        FlowChipRow {
            destinations.forEach { dest ->
                val enabled = dest.id != RouterLanding.PLUGIN || plugins.isNotEmpty()
                FilterChip(
                    selected = destination.id == dest.id,
                    onClick = { if (enabled) destination = dest },
                    enabled = enabled,
                    label = { Text(dest.label(customTitles)) },
                )
            }
        }

        if (destination.id == RouterLanding.PLUGIN) {
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Plugin")
            ExposedDropdownMenuBox(
                expanded = pluginMenuOpen,
                onExpandedChange = { pluginMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = pluginTitle,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pluginMenuOpen) },
                    shape = FlowTokens.Shape.Field,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = pluginMenuOpen,
                    onDismissRequest = { pluginMenuOpen = false },
                ) {
                    plugins.forEach { plugin ->
                        DropdownMenuItem(
                            text = { Text(plugin.title) },
                            onClick = {
                                pluginId = plugin.id
                                pluginMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}
