package com.personal.flowreader.ui.settings

import com.personal.flowreader.data.FilterMatchType
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.ui.reader.FilterPreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FilterEditsTest {
    private fun rule(id: String, pattern: String, replacement: String, order: Int = 0) = FilterRule(
        id = id,
        pattern = pattern,
        replacement = replacement,
        matchType = FilterMatchType.CaseSensitive,
        wholeWords = false,
        order = order,
    )

    @Test
    fun addAppendsAfterHighestOrder() {
        val rules = listOf(rule("a", "x", "y", order = 3), rule("b", "p", "q", order = 1))
        val next = FilterEdits.add(rules, rule("c", "m", "n"))
        assertEquals(listOf("a", "b", "c"), next.map { it.id })
        assertEquals(4, next.last().order)
        assertEquals(0, FilterEdits.add(emptyList(), rule("c", "m", "n", order = 9)).single().order)
    }

    @Test
    fun updateReplacesOnlyTheMatchingId() {
        val rules = listOf(rule("a", "x", "y"), rule("b", "p", "q"))
        val next = FilterEdits.update(rules, rule("b", "p", "z"))
        assertEquals(listOf("y", "z"), next.map { it.replacement })
    }

    @Test
    fun setEnabledAndDelete() {
        val rules = listOf(rule("a", "x", "y"), rule("b", "p", "q"))
        val off = FilterEdits.setEnabled(rules, "a", false)
        assertFalse(off[0].enabled)
        assertEquals(true, off[1].enabled)
        assertEquals(listOf("b"), FilterEdits.delete(rules, setOf("a")).map { it.id })
    }

    @Test
    fun previewModes() {
        val global = listOf(rule("g", "a", "1"))
        val groups = listOf(rule("s", "b", "2"))
        val local = listOf(rule("l", "c", "3"))
        val draft = rule("d", "d", "4")
        fun run(mode: FilterPreviewMode, scope: FilterScope) =
            FilterEdits.preview("abcd", draft, scope, mode, global, groups, local).text
        assertEquals("abcd", run(FilterPreviewMode.Original, FilterScope.Global))
        assertEquals("abc4", run(FilterPreviewMode.ThisRule, FilterScope.Global))
        assertEquals("1234", run(FilterPreviewMode.AllEnabled, FilterScope.Groups))
    }

    @Test
    fun previewUsesTheDraftInPlaceOfTheSavedRule() {
        val saved = listOf(rule("g", "a", "1"))
        val edited = rule("g", "a", "9")
        val text = FilterEdits.preview(
            "a", edited, FilterScope.Global, FilterPreviewMode.AllEnabled, saved, emptyList(), emptyList(),
        ).text
        assertEquals("9", text)
    }
}
