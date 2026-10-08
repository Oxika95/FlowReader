package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowTextAction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.ShareParseMode
import com.personal.flowreader.share.ShareUrlMatch
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Parse rules; the protected Default rule stays last. Hold a rule to reorder or delete. */
@Composable
internal fun ParseRuleList(
    rules: List<ParseRule>,
    onSave: (List<ParseRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ParseRule) -> Unit,
) {
    EditableRuleList(
        rules = rules,
        idOf = { it.id },
        nameOf = ::parseRuleTitle,
        text = RuleListText(
            title = "Parse rules",
            empty = "No parse rules.",
            hint = "First match wins. Default parses every other URL and can't be deleted. Hold a rule to reorder or delete.",
        ),
        onAdd = onAdd,
        onEdit = onEdit,
        onReorder = { ids ->
            val byId = rules.associateBy { it.id }
            onSave(ids.mapNotNull(byId::get))
        },
        onDelete = { ids -> onSave(rules.filterNot { it.id in ids && !ParseRules.isProtected(it) }) },
        locked = ParseRules::isProtected,
        trailing = { rule ->
            if (!ParseRules.isProtected(rule)) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        onSave(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                    },
                )
            }
        },
    ) { rule, modifier ->
        Column(modifier) {
            Text(
                parseRuleTitle(rule),
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
    }
}

private fun parseRuleTitle(rule: ParseRule): String =
    if (ParseRules.isProtected(rule)) "Default · any URL" else ShareUrlMatch.formatMatch(rule)


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
    var coverCss by remember(initial.id) { mutableStateOf(initial.coverCss.orEmpty()) }
    var desktop by remember(initial.id) { mutableStateOf(initial.desktop) }
    var removeCss by remember(initial.id) { mutableStateOf(initial.removeCss.orEmpty()) }
    var prevCss by remember(initial.id) { mutableStateOf(initial.prevCss.orEmpty()) }
    var nextCss by remember(initial.id) { mutableStateOf(initial.nextCss.orEmpty()) }
    var crawlLimit by remember(initial.id) { mutableStateOf(initial.crawlLimit.toString()) }
    var testUrl by remember(initial.id) { mutableStateOf(initial.testUrl.orEmpty()) }
    var testBusy by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    var testTitle by remember { mutableStateOf<String?>(null) }
    var testPreview by remember { mutableStateOf<String?>(null) }
    var testSummary by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val showCustom = parseMode == ShareParseMode.Custom
    val protected = ParseRules.isProtected(initial)

    fun build(): ParseRule {
        val fields = initial.copy(
            parseMode = parseMode,
            contentCss = contentCss.trim().ifBlank { null },
            titleCss = titleCss.trim().ifBlank { null },
            coverCss = coverCss.trim().ifBlank { null },
            desktop = desktop,
            removeCss = removeCss.trim().ifBlank { null },
            prevCss = prevCss.trim().ifBlank { null },
            nextCss = nextCss.trim().ifBlank { null },
            crawlLimit = (crawlLimit.trim().toIntOrNull() ?: ParseRule.DEFAULT_CRAWL_LIMIT)
                .coerceIn(1, ParseRule.MAX_CRAWL_LIMIT),
            testUrl = testUrl.trim().ifBlank { null },
        )
        if (protected) return fields
        val parsed = ShareUrlMatch.parseMatchInput(matchText, matchIsRegex)
        return fields.copy(
            hostPattern = parsed.host,
            pathPattern = parsed.path,
            pathIsRegex = parsed.isRegex,
            matchSubdomains = !parsed.isRegex && allowWildcard,
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
        val rule = build()
        val selectors = ParseRules.effectiveSelectors(rule)
        scope.launch {
            try {
                val article = withContext(Dispatchers.IO) {
                    WebPageIngest.fetchArticle(url, selectors, rule.desktop)
                }
                testTitle = article.title
                testPreview = article.text.take(800)
                testSummary = article.diagnostics?.summary()
                testError = null
            } catch (err: Throwable) {
                testError = err.message ?: "Test failed"
                testTitle = null
                testPreview = null
                testSummary = null
            } finally {
                testBusy = false
            }
        }
    }
    FlowFullscreenCard(
        visible = true,
        onDismiss = onDismiss,
        title = when {
            protected -> "Default Parser"
            isNew -> "New Parse Rule"
            else -> "Edit Parse Rule"
        },
        bodySpacing = Arrangement.Top,
        footer = {
            val canSave = protected || matchText.isNotBlank()
            FlowActionRow(
                start = {
                    FlowTextAction(if (testBusy) "Testing…" else "Test", runTest, enabled = !testBusy)
                    if (showCustom) {
                        FlowTextAction("Pick", { pickerOpen = true }, enabled = testUrl.isNotBlank())
                    }
                },
            ) {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Save", { if (canSave) onSave(build()) }, enabled = canSave)
            }
        },
    ) {
        FlowLabel("Test URL")
        OutlinedTextField(
            value = testUrl,
            onValueChange = { testUrl = it },
            singleLine = true,
            placeholder = { Text("https://…/chapter/1") },
            shape = FlowTokens.Shape.Field,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FlowTokens.Space.S))
        if (protected) {
            FlowLabel("URL match")
            Text(
                "Any URL no rule above matches. Always on; can't be deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MatchFields(
                matchText = matchText,
                onMatchText = { matchText = it },
                matchIsRegex = matchIsRegex,
                onMatchIsRegex = { matchIsRegex = it },
                allowWildcard = allowWildcard,
                onAllowWildcard = { allowWildcard = it },
                matches = testUrl.trim().takeIf { it.isNotBlank() && matchText.isNotBlank() }?.let { url ->
                    runCatching { ParseRules.appliesTo(build(), url) }.getOrDefault(false)
                },
            )
        }
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
                    "CSS selectors matched against the page as downloaded (no scripts run). " +
                        "Body keeps every match; nothing matched is an error. Pick selects on the Test URL."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = FlowTokens.Space.XS),
        )
        FlowToggleRow(
            title = "Desktop site",
            subtitle = "Load pages as a desktop browser (Test, Pick and imports). Some sites hide parts on mobile.",
            checked = desktop,
            onCheckedChange = { desktop = it },
        )
        if (showCustom) {
            Spacer(Modifier.height(FlowTokens.Space.M))
            CssField("Title (optional)", titleCss, { titleCss = it }, "h1, h2.chapter-title")
            CssField("Cover image (optional)", coverCss, { coverCss = it }, "img.cover, meta[property=og:image]")
            CssField("Body", contentCss, { contentCss = it }, "div.chapter-content")
            CssField("Previous Button (optional)", prevCss, { prevCss = it }, "a.prev, a[rel=prev]")
            CssField("Next Button (optional)", nextCss, { nextCss = it }, "a.next, a[rel=next]")
            CssField("Remove (optional, comma-separated)", removeCss, { removeCss = it }, ".ads, .share, .author-note")
            if (nextCss.isNotBlank()) {
                FlowLabel("Crawl limit (pages)")
                OutlinedTextField(
                    value = crawlLimit,
                    onValueChange = { v -> crawlLimit = v.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = FlowTokens.Shape.Field,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        testError?.let { err ->
            Spacer(Modifier.height(FlowTokens.Space.S))
            Text(
                err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        testTitle?.let { title ->
            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Preview")
            testSummary?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
    if (pickerOpen) {
        PagePickerOverlay(
            url = testUrl.trim(),
            initial = PickerFields(
                title = titleCss,
                body = contentCss,
                cover = coverCss,
                prev = prevCss,
                next = nextCss,
                remove = removeCss,
            ),
            desktop = desktop,
            onDesktopChange = { desktop = it },
            onUrlChange = { testUrl = it },
            onDismiss = { pickerOpen = false },
            onDone = { f ->
                titleCss = f.title
                coverCss = f.cover
                contentCss = f.body
                prevCss = f.prev
                nextCss = f.next
                removeCss = f.remove
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun CssField(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    FlowLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = FlowTokens.Shape.Field,
        modifier = Modifier.fillMaxWidth(),
    )
}
