package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginBrowseGroup
import com.personal.flowreader.plugin.api.PluginBrowseSection
import com.personal.flowreader.plugin.api.PluginWork
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupedRowsTest {
    private fun w(id: String, group: String) = PluginWork(id = id, title = id, group = group)

    @Test
    fun oneGroupHasNoHeaders() {
        val rows = listOf(w("a", "Paid"), w("b", "Paid"))
        assertEquals(rows.map { GroupedRow.Row(it) }, groupedRows(rows))
    }

    @Test
    fun headersStartEachGroupRun() {
        val a = w("a", "Paid")
        val b = w("b", "Free")
        val c = w("c", "Free")
        assertEquals(
            listOf(
                GroupedRow.Header("Paid", first = true),
                GroupedRow.Row(a),
                GroupedRow.Header("Free", first = false),
                GroupedRow.Row(b),
                GroupedRow.Row(c),
            ),
            groupedRows(listOf(a, b, c)),
        )
    }

    @Test
    fun declaredGroupsAlwaysShowWithEmptyText() {
        val tag = w("t", "Tags")
        val other = w("o", "Other")
        val declared = listOf(PluginBrowseGroup("Collections", "No collections."), PluginBrowseGroup("Tags"))
        assertEquals(
            listOf(
                GroupedRow.Header("Collections", first = true),
                GroupedRow.Empty("Collections", "No collections."),
                GroupedRow.Header("Tags", first = false),
                GroupedRow.Row(tag),
                GroupedRow.Header("Other", first = false),
                GroupedRow.Row(other),
            ),
            groupedRows(listOf(other, tag), declared),
        )
        assertEquals(
            listOf(
                GroupedRow.Header("Collections", first = true),
                GroupedRow.Empty("Collections", "No collections."),
                GroupedRow.Header("Tags", first = false),
                GroupedRow.Empty("Tags", "Nothing here yet."),
            ),
            groupedRows(emptyList(), declared),
        )
    }

    @Test
    fun emptyDeclaredGroupsWaitForTheLastPage() {
        val col = w("c", "Collections")
        val declared = listOf(PluginBrowseGroup("Collections"), PluginBrowseGroup("Tags", "No tags."))
        assertEquals(
            listOf(GroupedRow.Header("Collections", first = true), GroupedRow.Row(col)),
            groupedRows(listOf(col), declared, complete = false),
        )
    }

    @Test
    fun sectionsGroupUnderTheirHeading() {
        val intro = PluginBrowseSection(text = "intro")
        val mine = PluginBrowseSection(heading = "Your membership", title = "Fan")
        val more = PluginBrowseSection(heading = "More tiers", title = "Free", collapsed = true)
        val gold = PluginBrowseSection(title = "Gold")
        assertEquals(
            listOf(
                SectionGroup("", false, listOf(intro)),
                SectionGroup("Your membership", false, listOf(mine)),
                SectionGroup("More tiers", true, listOf(more, gold)),
            ),
            sectionGroups(listOf(intro, mine, more, gold)),
        )
    }
}
