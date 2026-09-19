package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFiltersTest {
    @Test
    fun caseInsensitiveWholeWord() {
        val rules = listOf(
            FilterRule(
                pattern = "foo",
                replacement = "bar",
                matchType = FilterMatchType.CaseInsensitive,
                wholeWords = true,
            ),
        )
        val result = TextFilters.apply("Foo food FOO", rules)
        assertEquals("bar food bar", result.text)
        assertEquals(listOf(0 until 3, 9 until 12), result.replacedRanges)
    }

    @Test
    fun blankReplacementHasNoRanges() {
        val rules = listOf(
            FilterRule(pattern = "x", replacement = "", wholeWords = false),
        )
        val result = TextFilters.apply("a x b", rules)
        assertEquals("a  b", result.text)
        assertTrue(result.replacedRanges.isEmpty())
    }

    @Test
    fun regexAndOrder() {
        val rules = listOf(
            FilterRule(order = 1, pattern = "\\d+", replacement = "#", matchType = FilterMatchType.RegEx),
            FilterRule(order = 0, pattern = "cat", replacement = "dog", matchType = FilterMatchType.CaseSensitive),
        )
        val result = TextFilters.apply("cat 12", TextFilters.merge(rules, emptyList(), emptyList()))
        assertEquals("dog #", result.text)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val rules = listOf(
            FilterRule(
                id = "abc",
                title = "t",
                enabled = false,
                matchType = FilterMatchType.RegEx,
                wholeWords = false,
                pattern = "a+",
                replacement = "b",
                order = 2,
            ),
        )
        val decoded = TextFilters.decodeRules(TextFilters.encodeRules(rules))
        assertEquals(1, decoded.size)
        assertEquals("abc", decoded[0].id)
        assertEquals("t", decoded[0].title)
        assertEquals(false, decoded[0].enabled)
        assertEquals(FilterMatchType.RegEx, decoded[0].matchType)
        assertEquals("a+", decoded[0].pattern)
        assertEquals("b", decoded[0].replacement)
        assertEquals(2, decoded[0].order)
    }

    @Test
    fun invalidRegexSkipped() {
        val rules = listOf(
            FilterRule(pattern = "[", replacement = "x", matchType = FilterMatchType.RegEx),
        )
        assertEquals("hello", TextFilters.apply("hello", rules).text)
        assertTrue(TextFilters.validatePattern(rules[0]) != null)
    }
}
