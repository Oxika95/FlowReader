package com.personal.flowreader.ui.settings

import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope

/** Shared editor session for library and reader filter overlays. */
data class FilterEditorSession(
    val scope: FilterScope,
    val rule: FilterRule,
    val isNew: Boolean,
)
