package com.personal.flowreader.ui.settings

import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.ui.reader.FilterPreviewMode

/** List edits for the filter rules of one scope; both view models apply these. */
object FilterEdits {
    fun add(rules: List<FilterRule>, rule: FilterRule): List<FilterRule> =
        rules + rule.copy(order = (rules.maxOfOrNull { it.order } ?: -1) + 1)

    fun update(rules: List<FilterRule>, rule: FilterRule): List<FilterRule> =
        rules.map { if (it.id == rule.id) rule else it }

    fun setEnabled(rules: List<FilterRule>, id: String, enabled: Boolean): List<FilterRule> =
        rules.map { if (it.id == id) it.copy(enabled = enabled) else it }

    fun delete(rules: List<FilterRule>, ids: Set<String>): List<FilterRule> =
        rules.filterNot { it.id in ids }

    /** Runs [sample] through the rules chosen by [mode]; [draft] replaces its saved copy in [scope]. */
    fun preview(
        sample: String,
        draft: FilterRule,
        scope: FilterScope,
        mode: FilterPreviewMode,
        global: List<FilterRule>,
        groups: List<FilterRule>,
        local: List<FilterRule>,
    ): FilterApplyResult {
        val rules = when (mode) {
            FilterPreviewMode.Original -> emptyList()
            FilterPreviewMode.ThisRule -> listOf(draft.copy(enabled = true, order = 0))
            FilterPreviewMode.AllEnabled -> {
                val withDraft: (List<FilterRule>) -> List<FilterRule> = { list ->
                    list.map { if (it.id == draft.id) draft else it }.let { mapped ->
                        if (mapped.any { it.id == draft.id }) mapped else mapped + draft
                    }
                }
                when (scope) {
                    FilterScope.Global -> TextFilters.merge(withDraft(global), groups, local)
                    FilterScope.Groups -> TextFilters.merge(global, withDraft(groups), local)
                    FilterScope.Local -> TextFilters.merge(global, groups, withDraft(local))
                }
            }
        }
        return TextFilters.apply(sample, rules)
    }
}
