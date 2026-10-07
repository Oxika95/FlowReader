package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowTextAction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.share.RouterContentKind
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.RouterRule
import com.personal.flowreader.share.ShareUrlMatch
import com.personal.flowreader.ui.theme.FlowTokens

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

/** Router rules; tap edits, the switch enables, hold to reorder or delete. */
@Composable
internal fun RouterRuleList(
    rules: List<RouterRule>,
    plugins: List<SharePluginOption>,
    customTitles: Map<String, String>,
    onSave: (List<RouterRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RouterRule) -> Unit,
) {
    fun saveOrdered(list: List<RouterRule>) {
        onSave(list.mapIndexed { index, rule -> rule.copy(order = index) })
    }
    EditableRuleList(
        rules = rules,
        idOf = { it.id },
        nameOf = ::routerRuleTitle,
        text = RuleListText(
            title = "Rules",
            empty = "No rules. Seed covers book files → Files, raw text → Queue, URLs → Parse → Queue, and plugin sites → Plugin.",
            hint = "First match wins: put specific rules above broad ones. Hold a rule to reorder or delete.",
        ),
        onAdd = onAdd,
        onEdit = onEdit,
        onReorder = { ids ->
            val byId = rules.associateBy { it.id }
            saveOrdered(ids.mapNotNull(byId::get))
        },
        onDelete = { ids -> saveOrdered(rules.filterNot { it.id in ids }) },
        trailing = { rule ->
            Switch(
                checked = rule.enabled,
                onCheckedChange = { enabled ->
                    onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                },
            )
        },
    ) { rule, modifier ->
        Column(modifier) {
            Text(
                routerRuleTitle(rule),
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
    }
}

private fun routerRuleTitle(rule: RouterRule): String = when (rule.kind) {
    RouterContentKind.Url -> ShareUrlMatch.formatMatch(rule)
    else -> rule.kind.label
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
