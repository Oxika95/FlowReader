package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnubisCookiesTest {
    @Test
    fun isAnubisAuthCookie() {
        assertTrue(AnubisCookies.isAnubisAuthCookie("techaro.lol-anubis"))
        assertTrue(AnubisCookies.isAnubisAuthCookie("techaro.lol-anubis-347ddb4a"))
        assertFalse(AnubisCookies.isAnubisAuthCookie("techaro.lol-anubis-cookie-verification"))
        assertFalse(AnubisCookies.isAnubisAuthCookie("techaro.lol-anubis-cookie-verification-x"))
        assertFalse(AnubisCookies.isAnubisAuthCookie("techaro.lol-anubis-original-referer"))
        assertFalse(AnubisCookies.isAnubisAuthCookie("cf_clearance"))
        assertFalse(AnubisCookies.isAnubisAuthCookie("session_id"))
    }

    @Test
    fun authCookieMapSkipsBlankAndVerification() {
        val map = AnubisCookies.authCookieMap(
            listOf(
                "techaro.lol-anubis" to "jwt",
                "techaro.lol-anubis" to "",
                "techaro.lol-anubis-cookie-verification" to "v",
                "session_id" to "s",
            ),
        )
        assertEquals(mapOf("techaro.lol-anubis" to "jwt"), map)
    }

    @Test
    fun hasNewAuthCookieDetectsNewAndChanged() {
        val baseline = mapOf("techaro.lol-anubis" to "old")
        assertFalse(AnubisCookies.hasNewAuthCookie(baseline, baseline))
        assertFalse(AnubisCookies.hasNewAuthCookie(baseline, emptyMap()))
        assertTrue(AnubisCookies.hasNewAuthCookie(baseline, mapOf("techaro.lol-anubis" to "new")))
        assertTrue(AnubisCookies.hasNewAuthCookie(baseline, mapOf("techaro.lol-anubis-abc" to "jwt")))
        assertTrue(AnubisCookies.hasNewAuthCookie(emptyMap(), mapOf("techaro.lol-anubis" to "jwt")))
    }
}
