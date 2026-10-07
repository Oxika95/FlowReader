package com.personal.flowreader.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.data.FilterRule

/** Filter rules of one scope; tap edits, the switch enables, hold to reorder or delete. */
@Composable
internal fun FilterRuleList(
    rules: List<FilterRule>,
    onAdd: () -> Unit,
    onEdit: (FilterRule) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    val sorted = remember(rules) { rules.sortedBy { it.order } }
    EditableRuleList(
        rules = sorted,
        idOf = { it.id },
        nameOf = ::ruleTitle,
        text = RuleListText(
            title = "Rules",
            empty = "No filters yet. Add a rule to replace text in the reader and TTS.",
            hint = "Rules run top to bottom. Hold a rule to reorder or delete.",
            noun = "filter",
        ),
        onAdd = onAdd,
        onEdit = onEdit,
        onReorder = onReorder,
        onDelete = onDelete,
        footer = "Enabled ${sorted.count { it.enabled }} of ${sorted.size}",
        trailing = { rule -> Switch(checked = rule.enabled, onCheckedChange = { onSetEnabled(rule.id, it) }) },
    ) { rule, modifier -> FilterRuleText(rule, modifier) }
}

@Composable
private fun FilterRuleText(rule: FilterRule, modifier: Modifier) {
    val replacementLabel = rule.replacement.ifEmpty { "(empty)" }
    Column(modifier = modifier) {
        Text(
            ruleTitle(rule),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${rule.pattern} → $replacementLabel" + if (rule.ttsOnly) " · TTS only" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ruleTitle(rule: FilterRule): String = rule.title.ifBlank { rule.pattern.ifBlank { "Untitled rule" } }
