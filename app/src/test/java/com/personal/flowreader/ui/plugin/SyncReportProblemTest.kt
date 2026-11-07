package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.sync.SyncReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncReportProblemTest {
    private val title = { id: String -> id.replaceFirstChar { it.uppercase() } }

    @Test
    fun aCleanReportHasNoProblem() {
        assertNull(SyncReport(added = 2, removed = 1).problem("Royal Road", title))
    }

    @Test
    fun signedOutAsksForSignIn() {
        val p = SyncReport(signedOut = true).problem("Royal Road", title)!!
        assertEquals(PluginErrorCode.AuthRequired, p.code)
        assertEquals("Sign in to Royal Road to sync", p.message)
    }

    @Test
    fun failedListsAreNamedWithTheNextStep() {
        val p = SyncReport(failedLists = linkedSetOf("follow", "favorite")).problem("Royal Road", title)!!
        assertEquals(PluginErrorCode.Network, p.code)
        assertEquals(
            "Could not load Follow and Favorite. Open site from Account to refresh the session, then Sync now.",
            p.message,
        )
    }

    @Test
    fun signedOutWinsOverFailedLists() {
        val p = SyncReport(signedOut = true, failedLists = setOf("follow")).problem("Royal Road", title)!!
        assertEquals(PluginErrorCode.AuthRequired, p.code)
    }
}
