package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionSeedTest {
    @Test
    fun rrLoggedInFlagSeedsSession() {
        val s = PluginSessionSeed.fromSecrets("Alice", "true")
        assertTrue(s.loggedIn)
        assertEquals("Alice", s.account)
    }

    @Test
    fun patreonAccountAloneCountsAsSignedIn() {
        val s = PluginSessionSeed.fromSecrets("bob@example.com", null)
        assertTrue(s.loggedIn)
        assertEquals("bob@example.com", s.account)
    }

    @Test
    fun blankSecretsAreSignedOut() {
        val s = PluginSessionSeed.fromSecrets(null, null)
        assertFalse(s.loggedIn)
        assertEquals("", s.account)
    }

    @Test
    fun staleHintAfterSoftTtl() {
        val now = 1_000_000_000_000L
        assertNull(PluginSessionSeed.staleHint("royalroad", now - 10 * DAY, now))
        assertEquals(
            "Session may be stale — sign in again to refresh.",
            PluginSessionSeed.staleHint("royalroad", now - 31 * DAY, now),
        )
        assertEquals(
            "Session may be stale — sign in again to refresh.",
            PluginSessionSeed.staleHint("patreon", now - 15 * DAY, now),
        )
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
