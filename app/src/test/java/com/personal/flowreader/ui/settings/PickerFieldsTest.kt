package com.personal.flowreader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerFieldsTest {
    @Test
    fun singleFieldTakesTheLatestPick() {
        val f = PickerFields(body = "div.old").withPick(PickField.Body, "div.new", replacing = "div.old")
        assertEquals("div.new", f.body)
    }

    @Test
    fun removeAddsNewPicksAndReplacesAdjustedOne() {
        var f = PickerFields(remove = ".ads")
        f = f.withPick(PickField.Remove, "span.note")
        assertEquals(".ads, span.note", f.remove)
        f = f.withPick(PickField.Remove, "div.note-wrap", replacing = "span.note")
        assertEquals(".ads, div.note-wrap", f.remove)
        f = f.withPick(PickField.Remove, ".ads")
        assertEquals(".ads, div.note-wrap", f.remove)
    }
}
