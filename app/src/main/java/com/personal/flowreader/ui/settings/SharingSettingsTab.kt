package com.personal.flowreader.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.RouterContentKind
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.RouterRule
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.ShareParseMode
import com.personal.flowreader.share.SharePrefs
import com.personal.flowreader.share.ShareUrlMatch
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SharePluginOption(
    val id: String,
    val title: String,
)

sealed class ImportRuleEditRequest {
    data class Router(
        val initial: RouterRule,
        val isNew: Boolean,
        val plugins: List<SharePluginOption>,
        val customTabs: List<SharePluginOption> = emptyList(),
        val onSave: (RouterRule) -> Unit,
    ) : ImportRuleEditRequest()

    data class Parse(
        val initial: ParseRule,
        val isNew: Boolean,
        val onSave: (ParseRule) -> Unit,
    ) : ImportRuleEditRequest()
}

class DomainRuleEditorState {
    var request by mutableStateOf<ImportRuleEditRequest?>(null)
}

@Composable
fun DomainRuleEditorHost(state: DomainRuleEditorState) {
    when (val req = state.request) {
        is ImportRuleEditRequest.Router -> RouterRuleEditorOverlay(
            initial = req.initial,
            isNew = req.isNew,
            plugins = req.plugins,
            customTabs = req.customTabs,
            onDismiss = { state.request = null },
            onSave = { saved ->
                req.onSave(saved)
                state.request = null
            },
        )
        is ImportRuleEditRequest.Parse -> ParseRuleEditorOverlay(
            initial = req.initial,
            isNew = req.isNew,
            onDismiss = { state.request = null },
            onSave = { saved ->
                req.onSave(saved)
                state.request = null
            },
        )
        null -> Unit
    }
}

@Composable
fun SharingSettingsTab(
    prefs: SharePrefs,
    routerRules: List<RouterRule>,
    parseRules: List<ParseRule>,
    overlayAllowed: Boolean,
    plugins: List<SharePluginOption>,
    customTabs: List<SharePluginOption> = emptyList(),
    editorState: DomainRuleEditorState,
    onManualOverride: (Boolean) -> Unit,
    onSaveRouterRules: (List<RouterRule>) -> Unit,
    onSaveParseRules: (List<ParseRule>) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) }
    val customTitles = remember(customTabs) {
        customTabs.associate { it.id to it.title }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        SettingsSubTabRow(
            selectedTabIndex = subTab,
            labels = listOf("Router", "Parser"),
            onTabSelected = { subTab = it },
        )
        Spacer(Modifier.height(FlowTokens.Space.S))

        when (subTab) {
            0 -> RouterPane(
                prefs = prefs,
                rules = routerRules,
                overlayAllowed = overlayAllowed,
                plugins = plugins,
                customTitles = customTitles,
                onManualOverride = onManualOverride,
                onSaveRules = onSaveRouterRules,
                onAdd = {
                    editorState.request = ImportRuleEditRequest.Router(
                        initial = RouterRule(
                            kind = RouterContentKind.Url,
                            hostPattern = "*",
                            parseUrl = true,
                            destination = RouterLanding.Queue,
                            order = routerRules.size,
                        ),
                        isNew = true,
                        plugins = plugins,
                        customTabs = customTabs,
                        onSave = { saved ->
                            onSaveRouterRules(
                                routerRules + saved.copy(
                                    id = UUID.randomUUID().toString(),
                                    order = routerRules.size,
                                ),
                            )
                        },
                    )
                },
                onEdit = { rule ->
                    editorState.request = ImportRuleEditRequest.Router(
                        initial = rule,
                        isNew = false,
                        plugins = plugins,
                        customTabs = customTabs,
                        onSave = { saved ->
                            onSaveRouterRules(routerRules.map { if (it.id == saved.id) saved else it })
                        },
                    )
                },
            )
            else -> ParserPane(
                rules = parseRules,
                onSaveRules = onSaveParseRules,
                onAdd = {
                    editorState.request = ImportRuleEditRequest.Parse(
                        initial = ParseRule(hostPattern = "", order = parseRules.size),
                        isNew = true,
                        onSave = { saved ->
                            onSaveParseRules(
                                parseRules + saved.copy(
                                    id = UUID.randomUUID().toString(),
                                    order = parseRules.size,
                                ),
                            )
                        },
                    )
                },
                onEdit = { rule ->
                    editorState.request = ImportRuleEditRequest.Parse(
                        initial = rule,
                        isNew = false,
                        onSave = { saved ->
                            onSaveParseRules(parseRules.map { if (it.id == saved.id) saved else it })
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun RouterPane(
    prefs: SharePrefs,
    rules: List<RouterRule>,
    overlayAllowed: Boolean,
    plugins: List<SharePluginOption>,
    customTitles: Map<String, String>,
    onManualOverride: (Boolean) -> Unit,
    onSaveRules: (List<RouterRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RouterRule) -> Unit,
) {
    val manualOverride = prefs.askMode == ShareAskMode.Ask

    SettingsLabel("Share")
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manual Override", style = MaterialTheme.typography.bodyLarge)
            Text(
                "When a URL matches a Plugin rule, ask Plugin vs the next URL rule. Needs display-over permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (manualOverride && !overlayAllowed) {
                Text(
                    "Overlay permission still needed — grant it when prompted, or toggle again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = FlowTokens.Space.XS),
                )
            }
        }
        Switch(checked = manualOverride, onCheckedChange = onManualOverride)
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    RuleListHeader(
        title = "Rules",
        emptyHint = "No rules. Seed covers book files → Files, raw text → Queue, URLs → Parse → Queue, and Royal Road → Plugin.",
        rulesEmpty = rules.isEmpty(),
        onAdd = onAdd,
        content = {
            ReorderableRouterList(
                rules = rules,
                plugins = plugins,
                customTitles = customTitles,
                onSave = onSaveRules,
                onEdit = onEdit,
            )
        },
    )
}

@Composable
private fun ParserPane(
    rules: List<ParseRule>,
    onSaveRules: (List<ParseRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ParseRule) -> Unit,
) {
    RuleListHeader(
        title = "Parse rules",
        emptyHint = "No parse rules. URL rules with Parse on use Default heuristics when nothing matches.",
        rulesEmpty = rules.isEmpty(),
        onAdd = onAdd,
        content = {
            ReorderableParseList(
                rules = rules,
                onSave = onSaveRules,
                onEdit = onEdit,
            )
        },
    )
}

@Composable
private fun RuleListHeader(
    title: String,
    emptyHint: String,
    rulesEmpty: Boolean,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(FlowTokens.Icon.M),
            )
            Spacer(Modifier.width(FlowTokens.Space.XS))
            Text("Add")
        }
    }
    if (rulesEmpty) {
        Text(
            emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    content()
}

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
private fun ReorderableRouterList(
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
private fun RouterRuleEditorOverlay(
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

    ReaderModalScaffold(
        visible = true,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
    ) {
        ModalHeaderRow(
            title = if (isNew) "New Router Rule" else "Edit Router Rule",
            onDismiss = onDismiss,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FlowTokens.Pad.CardIn,
                    vertical = FlowTokens.Space.S,
                ),
        ) {
            SettingsLabel("Content")
            ChipRow {
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
                SettingsToggleRow(
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
            SettingsLabel("Destination")
            ChipRow {
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
                SettingsLabel("Plugin")
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
                        shape = FlowTokens.PanelShape,
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

            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = {
                        if (kind == RouterContentKind.Url && matchText.isBlank()) return@TextButton
                        if (destination.id == RouterLanding.PLUGIN && plugins.isEmpty()) return@TextButton
                        onSave(build())
                    },
                    enabled = (kind != RouterContentKind.Url || matchText.isNotBlank()) &&
                        (destination.id != RouterLanding.PLUGIN || plugins.isNotEmpty()),
                ) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableParseList(
    rules: List<ParseRule>,
    onSave: (List<ParseRule>) -> Unit,
    onEdit: (ParseRule) -> Unit,
) {
    fun saveOrdered(list: List<ParseRule>) {
        onSave(list.mapIndexed { index, rule -> rule.copy(order = index) })
    }
    var menuRuleId by remember { mutableStateOf<String?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val working = remember { mutableStateListOf<ParseRule>() }
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
                            ShareUrlMatch.formatMatch(rule),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${rule.parseMode.label} → Queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun <T> DragHandle(
    ruleId: String,
    working: MutableList<T>,
    rowHeights: Map<String, Int>,
    idOf: (T) -> String,
    draggingId: (String?) -> Unit,
    dragOffset: Float,
    onDragOffset: (Float) -> Unit,
) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = FlowTokens.Space.S)
            .pointerInput(ruleId) {
                detectDragGestures(
                    onDragStart = {
                        draggingId(ruleId)
                        onDragOffset(0f)
                    },
                    onDragEnd = {
                        draggingId(null)
                        onDragOffset(0f)
                    },
                    onDragCancel = {
                        draggingId(null)
                        onDragOffset(0f)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        var offset = dragOffset + amount.y
                        onDragOffset(offset)
                        val index = working.indexOfFirst { idOf(it) == ruleId }
                        if (index < 0) return@detectDragGestures
                        val next = working.getOrNull(index + 1)
                        val prev = working.getOrNull(index - 1)
                        val nextH = next?.let { rowHeights[idOf(it)] } ?: 0
                        val prevH = prev?.let { rowHeights[idOf(it)] } ?: 0
                        if (next != null && offset > nextH / 2f) {
                            working.add(index + 1, working.removeAt(index))
                            offset -= nextH
                            onDragOffset(offset)
                        } else if (prev != null && offset < -prevH / 2f) {
                            working.add(index - 1, working.removeAt(index))
                            offset += prevH
                            onDragOffset(offset)
                        }
                    },
                )
            },
    )
}

@Composable
private fun ParseRuleEditorOverlay(
    initial: ParseRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ParseRule) -> Unit,
) {
    var matchText by remember(initial.id) {
        mutableStateOf(ShareUrlMatch.formatMatch(initial))
    }
    var matchIsRegex by remember(initial.id) { mutableStateOf(initial.pathIsRegex) }
    var allowWildcard by remember(initial.id) { mutableStateOf(initial.matchSubdomains) }
    var parseMode by remember(initial.id) { mutableStateOf(initial.parseMode) }
    var contentCss by remember(initial.id) { mutableStateOf(initial.contentCss.orEmpty()) }
    var titleCss by remember(initial.id) { mutableStateOf(initial.titleCss.orEmpty()) }
    var removeCss by remember(initial.id) { mutableStateOf(initial.removeCss.orEmpty()) }
    var testUrl by remember(initial.id) { mutableStateOf(initial.testUrl.orEmpty()) }
    var testBusy by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    var testTitle by remember { mutableStateOf<String?>(null) }
    var testPreview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val showCustom = parseMode == ShareParseMode.Custom

    fun build(): ParseRule {
        val parsed = ShareUrlMatch.parseMatchInput(matchText, matchIsRegex)
        return initial.copy(
            hostPattern = parsed.host,
            pathPattern = parsed.path,
            pathIsRegex = parsed.isRegex,
            matchSubdomains = !parsed.isRegex && allowWildcard,
            parseMode = parseMode,
            contentCss = contentCss.trim().ifBlank { null },
            titleCss = titleCss.trim().ifBlank { null },
            removeCss = removeCss.trim().ifBlank { null },
            testUrl = testUrl.trim().ifBlank { null },
        )
    }

    ReaderModalScaffold(
        visible = true,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
    ) {
        ModalHeaderRow(
            title = if (isNew) "New Parse Rule" else "Edit Parse Rule",
            onDismiss = onDismiss,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FlowTokens.Pad.CardIn,
                    vertical = FlowTokens.Space.S,
                ),
        ) {
            MatchFields(
                matchText = matchText,
                onMatchText = { matchText = it },
                matchIsRegex = matchIsRegex,
                onMatchIsRegex = { matchIsRegex = it },
                allowWildcard = allowWildcard,
                onAllowWildcard = { allowWildcard = it },
            )
            Spacer(Modifier.height(FlowTokens.Space.M))
            SettingsLabel("Parser")
            ChipRow {
                ShareParseMode.entries.forEach { mode ->
                    FilterChip(
                        selected = parseMode == mode,
                        onClick = { parseMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                when (parseMode) {
                    ShareParseMode.Default ->
                        "Uses built-in page heuristics (article / main / body). Lands in Queue."
                    ShareParseMode.Custom ->
                        "CSS selectors for content, title, and removals. Lands in Queue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FlowTokens.Space.XS),
            )
            if (showCustom) {
                Spacer(Modifier.height(FlowTokens.Space.M))
                SettingsLabel("Content CSS")
                OutlinedTextField(
                    value = contentCss,
                    onValueChange = { contentCss = it },
                    singleLine = true,
                    placeholder = { Text("article, div.post_content, …") },
                    shape = FlowTokens.PanelShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FlowTokens.Space.S))
                SettingsLabel("Title CSS (optional)")
                OutlinedTextField(
                    value = titleCss,
                    onValueChange = { titleCss = it },
                    singleLine = true,
                    placeholder = { Text("h1, h2.post-title, …") },
                    shape = FlowTokens.PanelShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FlowTokens.Space.S))
                SettingsLabel("Remove CSS (optional)")
                OutlinedTextField(
                    value = removeCss,
                    onValueChange = { removeCss = it },
                    singleLine = true,
                    placeholder = { Text(".share, .ads, …") },
                    shape = FlowTokens.PanelShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(FlowTokens.Space.S))
                SettingsLabel("Test URL")
                OutlinedTextField(
                    value = testUrl,
                    onValueChange = { testUrl = it },
                    singleLine = true,
                    placeholder = { Text("https://…/chapter/1") },
                    shape = FlowTokens.PanelShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                testError?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                testTitle?.let { title ->
                    Spacer(Modifier.height(FlowTokens.Space.S))
                    SettingsLabel("Preview")
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    testPreview?.let { body ->
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 12,
                        )
                    }
                }
            }
            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showCustom) {
                    TextButton(
                        onClick = {
                            val url = testUrl.trim()
                            if (url.isBlank()) {
                                testError = "Enter a test URL"
                                testTitle = null
                                testPreview = null
                                return@TextButton
                            }
                            testBusy = true
                            testError = null
                            val (c, t, r) = ParseRules.effectiveSelectors(build())
                            scope.launch {
                                try {
                                    val article = withContext(Dispatchers.IO) {
                                        WebPageIngest.fetchArticle(url, c, t, r)
                                    }
                                    testTitle = article.title
                                    testPreview = article.text.take(800)
                                    testError = null
                                } catch (err: Throwable) {
                                    testError = err.message ?: "Test failed"
                                    testTitle = null
                                    testPreview = null
                                } finally {
                                    testBusy = false
                                }
                            }
                        },
                        enabled = !testBusy,
                    ) {
                        Text(if (testBusy) "Testing…" else "Test")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { if (matchText.isNotBlank()) onSave(build()) },
                    enabled = matchText.isNotBlank(),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun MatchFields(
    matchText: String,
    onMatchText: (String) -> Unit,
    matchIsRegex: Boolean,
    onMatchIsRegex: (Boolean) -> Unit,
    allowWildcard: Boolean,
    onAllowWildcard: (Boolean) -> Unit,
) {
    SettingsLabel("URL match")
    OutlinedTextField(
        value = matchText,
        onValueChange = onMatchText,
        singleLine = true,
        placeholder = {
            Text(
                if (matchIsRegex) {
                    "royalroad\\.com/fiction/\\d+/[^/]+/chapter.*"
                } else {
                    "*.example.com/fiction/1/story"
                },
            )
        },
        shape = FlowTokens.PanelShape,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!matchIsRegex) {
        SettingsToggleRow(
            title = "Allow Wildcards",
            subtitle = "Use * to represent one or more unknown characters.",
            checked = allowWildcard,
            onCheckedChange = onAllowWildcard,
        )
    }
    SettingsToggleRow(
        title = "Enable RegEx",
        subtitle = "Use Regular Expression to match against the URL.",
        checked = matchIsRegex,
        onCheckedChange = onMatchIsRegex,
    )
}
