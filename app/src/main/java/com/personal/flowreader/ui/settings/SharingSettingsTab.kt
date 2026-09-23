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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.ShareDestination
import com.personal.flowreader.share.ShareDomainRule
import com.personal.flowreader.share.ShareDomainRules
import com.personal.flowreader.share.ShareParseMode
import com.personal.flowreader.share.SharePrefs
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.settings.ChipRow
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.settings.SettingsSubTabRow
import com.personal.flowreader.ui.settings.ModalHeaderRow
import com.personal.flowreader.ui.settings.SettingsLabel
import com.personal.flowreader.ui.settings.SettingsToggleRow
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SharePluginOption(
    val id: String,
    val title: String,
)

data class DomainRuleEditRequest(
    val initial: ShareDomainRule,
    val isNew: Boolean,
    val plugins: List<SharePluginOption>,
    val onSave: (ShareDomainRule) -> Unit,
)

/**
 * Hoisted so the editor draws as a sibling overlay of the Settings card rather than
 * inside its scrolling content.
 */
class DomainRuleEditorState {
    var request by mutableStateOf<DomainRuleEditRequest?>(null)
}

@Composable
fun DomainRuleEditorHost(state: DomainRuleEditorState) {
    val req = state.request ?: return
    DomainRuleEditorOverlay(
        initial = req.initial,
        isNew = req.isNew,
        plugins = req.plugins,
        onDismiss = { state.request = null },
        onSave = { saved ->
            req.onSave(saved)
            state.request = null
        },
    )
}

@Composable
fun SharingSettingsTab(
    prefs: SharePrefs,
    rules: List<ShareDomainRule>,
    overlayAllowed: Boolean,
    plugins: List<SharePluginOption>,
    editorState: DomainRuleEditorState,
    onShowQue: (Boolean) -> Unit,
    onManualOverride: (Boolean) -> Unit,
    onSaveRules: (List<ShareDomainRule>) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) }

    fun openEditor(existing: ShareDomainRule?) {
        editorState.request = DomainRuleEditRequest(
            initial = existing ?: ShareDomainRule(hostPattern = "", order = rules.size),
            isNew = existing == null,
            plugins = plugins,
            onSave = { saved ->
                val next = if (existing == null) {
                    rules + saved.copy(id = UUID.randomUUID().toString(), order = rules.size)
                } else {
                    rules.map { if (it.id == saved.id) saved else it }
                }
                onSaveRules(next)
            },
        )
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        SettingsSubTabRow(
            selectedTabIndex = subTab,
            labels = listOf("Context", "Parser"),
            onTabSelected = { subTab = it },
        )
        Spacer(Modifier.height(FlowTokens.Space.S))

        when (subTab) {
            0 -> ImportContextPane(
                prefs = prefs,
                overlayAllowed = overlayAllowed,
                onShowQue = onShowQue,
                onManualOverride = onManualOverride,
            )
            else -> ImportParserPane(
                rules = rules,
                plugins = plugins,
                onSaveRules = onSaveRules,
                onAdd = { openEditor(null) },
                onEdit = { openEditor(it) },
            )
        }
    }
}

@Composable
private fun ImportContextPane(
    prefs: SharePrefs,
    overlayAllowed: Boolean,
    onShowQue: (Boolean) -> Unit,
    onManualOverride: (Boolean) -> Unit,
) {
    val manualOverride = prefs.askMode == ShareAskMode.Ask

    SettingsLabel("Share sheet")
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show Flow-Queue", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Separate share target for the Queue playlist",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = prefs.showQueInShareSheet, onCheckedChange = onShowQue)
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manual Override", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Choose to send content directly to Files, Queue or Plugins. Requires display permissions",
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportParserPane(
    rules: List<ShareDomainRule>,
    plugins: List<SharePluginOption>,
    onSaveRules: (List<ShareDomainRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ShareDomainRule) -> Unit,
) {
    fun saveOrdered(list: List<ShareDomainRule>) {
        onSaveRules(list.mapIndexed { index, rule -> rule.copy(order = index) })
    }

    var menuRuleId by remember { mutableStateOf<String?>(null) }
    var reordering by remember { mutableStateOf(false) }
    val working = remember { mutableStateListOf<ShareDomainRule>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Domain rules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (reordering) {
            TextButton(
                onClick = {
                    saveOrdered(working.toList())
                    reordering = false
                    draggingId = null
                    dragOffset = 0f
                },
            ) { Text("Done") }
        } else {
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
    }
    if (rules.isEmpty()) {
        Text(
            "No domain rules yet. Add a host to crawl or route shared URLs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                            if (reordering) {
                                Modifier
                            } else {
                                Modifier.combinedClickable(
                                    onClick = { onEdit(rule) },
                                    onLongClick = { menuRuleId = rule.id },
                                )
                            },
                        )
                        .padding(vertical = FlowTokens.Space.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (reordering) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(end = FlowTokens.Space.S)
                                .pointerInput(rule.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = rule.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val index = working.indexOfFirst { it.id == rule.id }
                                            if (index < 0) return@detectDragGestures
                                            val next = working.getOrNull(index + 1)
                                            val prev = working.getOrNull(index - 1)
                                            val nextH = next?.let { rowHeights[it.id] } ?: 0
                                            val prevH = prev?.let { rowHeights[it.id] } ?: 0
                                            if (next != null && dragOffset > nextH / 2f) {
                                                working.add(index + 1, working.removeAt(index))
                                                dragOffset -= nextH
                                            } else if (prev != null && dragOffset < -prevH / 2f) {
                                                working.add(index - 1, working.removeAt(index))
                                                dragOffset += prevH
                                            }
                                        },
                                    )
                                },
                        )
                    }
                    Column(Modifier.weight(1f).padding(end = FlowTokens.Space.S)) {
                        Text(
                            ShareDomainRules.formatMatch(rule),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            ruleSummary(rule, plugins),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!reordering) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { enabled ->
                                onSaveRules(
                                    rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it },
                                )
                            },
                        )
                        IconButton(
                            onClick = { saveOrdered(rules.filterNot { it.id == rule.id }) },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
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
                            saveOrdered(
                                rules.toMutableList().apply { add(index + 1, copy) },
                            )
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

private fun ruleSummary(rule: ShareDomainRule, plugins: List<SharePluginOption>): String {
    val dest = when (rule.destination) {
        ShareDestination.Plugin -> {
            val name = plugins.firstOrNull { it.id == rule.pluginId }?.title
                ?: rule.pluginId
                ?: "Plugin"
            "Plugin · $name"
        }
        else -> rule.destination.label
    }
    val parse = if (rule.destination == ShareDestination.Plugin) {
        null
    } else {
        rule.parseMode.label
    }
    return listOfNotNull(parse, dest).joinToString(" → ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainRuleEditorOverlay(
    initial: ShareDomainRule,
    isNew: Boolean,
    plugins: List<SharePluginOption>,
    onDismiss: () -> Unit,
    onSave: (ShareDomainRule) -> Unit,
) {
    var matchText by remember(initial.id) {
        mutableStateOf(ShareDomainRules.formatMatch(initial))
    }
    var matchIsRegex by remember(initial.id) { mutableStateOf(initial.pathIsRegex) }
    var allowWildcard by remember(initial.id) { mutableStateOf(initial.matchSubdomains) }
    var parseMode by remember(initial.id) { mutableStateOf(initial.parseMode) }
    var destination by remember(initial.id) { mutableStateOf(initial.destination) }
    var pluginId by remember(initial.id) {
        mutableStateOf(initial.pluginId ?: plugins.firstOrNull()?.id.orEmpty())
    }
    var contentCss by remember(initial.id) { mutableStateOf(initial.contentCss.orEmpty()) }
    var titleCss by remember(initial.id) { mutableStateOf(initial.titleCss.orEmpty()) }
    var removeCss by remember(initial.id) { mutableStateOf(initial.removeCss.orEmpty()) }
    var testUrl by remember(initial.id) { mutableStateOf(initial.testUrl.orEmpty()) }
    var pluginMenuOpen by remember { mutableStateOf(false) }
    var testBusy by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    var testTitle by remember { mutableStateOf<String?>(null) }
    var testPreview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val showParser = destination != ShareDestination.Plugin
    val showCustomFields = showParser && parseMode == ShareParseMode.Custom
    val pluginTitle = plugins.firstOrNull { it.id == pluginId }?.title
        ?: plugins.firstOrNull()?.title
        ?: "No plugins"

    fun buildRule(): ShareDomainRule {
        val parsed = ShareDomainRules.parseMatchInput(matchText, matchIsRegex)
        return initial.copy(
            hostPattern = parsed.host,
            pathPattern = parsed.path,
            pathIsRegex = parsed.isRegex,
            matchSubdomains = !parsed.isRegex && allowWildcard,
            parseMode = parseMode,
            destination = destination,
            pluginId = if (destination == ShareDestination.Plugin) {
                pluginId.ifBlank { plugins.firstOrNull()?.id }
            } else {
                null
            },
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
            title = if (isNew) "New Domain Rule" else "Edit Domain Rule",
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
            SettingsLabel("URL match")
            OutlinedTextField(
                value = matchText,
                onValueChange = { matchText = it },
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
                    onCheckedChange = { allowWildcard = it },
                )
            }
            SettingsToggleRow(
                title = "Enable RegEx",
                subtitle = "Use Regular Expression to match against the URL.",
                checked = matchIsRegex,
                onCheckedChange = { matchIsRegex = it },
            )

            Spacer(Modifier.height(FlowTokens.Space.M))
            SettingsLabel("Add to…")
            ChipRow {
                ShareDestination.entries.forEach { dest ->
                    FilterChip(
                        selected = destination == dest,
                        onClick = { destination = dest },
                        label = { Text(dest.label) },
                    )
                }
            }

            if (destination == ShareDestination.Plugin) {
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
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(pluginMenuOpen)
                        },
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

            if (showParser) {
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
                            "Uses built-in page heuristics (article / main / body)."
                        ShareParseMode.Custom ->
                            "Manually define CSS selectors for content, title, and removals."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = FlowTokens.Space.XS),
                )
            }

            if (showCustomFields) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showCustomFields) {
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
                            val draftRule = buildRule()
                            val (c, t, r) = ShareDomainRules.effectiveSelectors(draftRule)
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
                    onClick = {
                        if (matchText.isBlank()) return@TextButton
                        if (destination == ShareDestination.Plugin && plugins.isEmpty()) {
                            return@TextButton
                        }
                        onSave(buildRule())
                    },
                    enabled = matchText.isNotBlank() &&
                        (destination != ShareDestination.Plugin || plugins.isNotEmpty()),
                ) { Text("Save") }
            }
        }
    }
}
