package com.personal.flowreader.plugin.runtime

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoginCookiesTest {
    @Test
    fun parsesPairsForHostAndSubdomains() {
        val cookies = WebLoginCookies.parse("session_id=abc; __cf_bm=x=y; bad; =v", "www.patreon.com", 1_000L)
        assertEquals(listOf("session_id", "__cf_bm"), cookies.map { it.name })
        assertEquals("x=y", cookies[1].value)
        assertEquals("patreon.com", cookies[0].domain)
        assertEquals(1_000L + WebLoginCookies.LIFETIME_MS, cookies[0].expiresAt)
        assertTrue(cookies[0].matches("https://www.patreon.com/api/posts".toHttpUrl()))
    }

    @Test
    fun hasNeedsNonEmptyValue() {
        assertTrue(WebLoginCookies.has("a=1; session_id=abc", "session_id"))
        assertFalse(WebLoginCookies.has("session_id=", "session_id"))
        assertFalse(WebLoginCookies.has(null, "session_id"))
    }

    @Test
    fun originsCoverBareAndWww() {
        assertEquals(
            listOf("https://patreon.com/", "https://www.patreon.com/"),
            WebLoginCookies.origins(listOf("www.patreon.com", "patreon.com")),
        )
    }
}
