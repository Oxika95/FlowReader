package com.personal.flowreader.ui.settings

import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.TextFilters

/** Shared editor session for library and reader filter overlays. */
data class FilterEditorSession(
    val scope: FilterScope,
    val rule: FilterRule,
    val isNew: Boolean,
)

/**
 * The rule edits a screen's view model offers to the Filters tab and the rule editor. The view model
 * supplies [editFilters] (store the scope's list, then refresh whatever reads it); the edits are shared.
 */
interface FilterRuleEditing {
    fun editFilters(scope: FilterScope, edit: (List<FilterRule>) -> List<FilterRule>)

    fun addFilter(scope: FilterScope, rule: FilterRule) =
        editFilters(scope) { FilterEdits.add(it, rule) }

    fun updateFilter(scope: FilterScope, rule: FilterRule) =
        editFilters(scope) { FilterEdits.update(it, rule) }

    fun setFilterEnabled(scope: FilterScope, id: String, enabled: Boolean) =
        editFilters(scope) { FilterEdits.setEnabled(it, id, enabled) }

    fun deleteFilters(scope: FilterScope, ids: Set<String>) =
        editFilters(scope) { FilterEdits.delete(it, ids) }

    fun reorderFilters(scope: FilterScope, ids: List<String>) =
        editFilters(scope) { TextFilters.reorder(it, ids) }
}

fun FilterSettingsCallbacks(
    editing: FilterRuleEditing,
    openEditor: (FilterEditorSession) -> Unit,
) = FilterSettingsCallbacks(
    onAddFilter = { scope -> openEditor(FilterEditorSession(scope = scope, rule = FilterRule(), isNew = true)) },
    onEditFilter = { scope, rule -> openEditor(FilterEditorSession(scope = scope, rule = rule, isNew = false)) },
    onSetFilterEnabled = editing::setFilterEnabled,
    onReorderFilters = editing::reorderFilters,
    onDeleteFilters = editing::deleteFilters,
)
