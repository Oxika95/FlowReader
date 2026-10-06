package com.personal.flowreader.ui.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.ShareParseMode
import com.personal.flowreader.share.ShareUrlMatch
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReorderableParseList(
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
internal fun ParseRuleEditorOverlay(
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

    val runTest: () -> Unit = runTest@{
        val url = testUrl.trim()
        if (url.isBlank()) {
            testError = "Enter a test URL"
            testTitle = null
            testPreview = null
            return@runTest
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
    }
    FlowFullscreenCard(
        visible = true,
        onDismiss = onDismiss,
        title = if (isNew) "New Parse Rule" else "Edit Parse Rule",
        bodySpacing = Arrangement.Top,
        footer = {
            FlowActionRow(
                start = {
                    if (showCustom) FlowTextAction(if (testBusy) "Testing…" else "Test", runTest, enabled = !testBusy)
                },
            ) {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Save", { if (matchText.isNotBlank()) onSave(build()) }, enabled = matchText.isNotBlank())
            }
        },
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
        FlowLabel("Parser")
        FlowChipRow {
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
            FlowLabel("Content CSS")
            OutlinedTextField(
                value = contentCss,
                onValueChange = { contentCss = it },
                singleLine = true,
                placeholder = { Text("article, div.post_content, …") },
                shape = FlowTokens.Shape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Title CSS (optional)")
            OutlinedTextField(
                value = titleCss,
                onValueChange = { titleCss = it },
                singleLine = true,
                placeholder = { Text("h1, h2.post-title, …") },
                shape = FlowTokens.Shape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Remove CSS (optional)")
            OutlinedTextField(
                value = removeCss,
                onValueChange = { removeCss = it },
                singleLine = true,
                placeholder = { Text(".share, .ads, …") },
                shape = FlowTokens.Shape.Field,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Test URL")
            OutlinedTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                singleLine = true,
                placeholder = { Text("https://…/chapter/1") },
                shape = FlowTokens.Shape.Field,
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
                FlowLabel("Preview")
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
    }
}
