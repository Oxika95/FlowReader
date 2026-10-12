package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeHandshakeTest {
    @Test
    fun gecIsUpperHex64() {
        val gec = EdgeHandshake.secMsGec(1_700_000_000L)
        assertEquals(64, gec.length)
        assertTrue(gec.matches(Regex("[0-9A-F]+")))
    }

    @Test
    fun gecStableForSameFiveMinuteWindow() {
        val a = EdgeHandshake.secMsGec(1_700_000_010L)
        val b = EdgeHandshake.secMsGec(1_700_000_080L)
        assertEquals(a, b)
    }

    @Test
    fun urlUsesNewHostSubscriptionKeyAndV1Token() {
        val gec = EdgeHandshake.secMsGec(1_700_000_000L)
        assertEquals(
            "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1" +
                "?Ocp-Apim-Subscription-Key=${EdgeHandshake.TOKEN}" +
                "&Sec-MS-GEC=$gec&Sec-MS-GEC-Version=1-143.0.3650.75&ConnectionId=abc",
            EdgeHandshake.url("abc", 1_700_000_000L),
        )
    }

    @Test
    fun voicesUrlUsesNewHost() {
        val url = EdgeHandshake.voicesUrl(1_700_000_000L)
        assertTrue(
            url.startsWith(
                "https://api.msedgeservices.com/tts/cognitiveservices/voices/list" +
                    "?Ocp-Apim-Subscription-Key=${EdgeHandshake.TOKEN}",
            ),
        )
        assertTrue(url.contains("Sec-MS-GEC-Version=1-143.0.3650.75"))
        assertFalse(url.contains("speech.platform.bing.com"))
    }

    @Test
    fun skewFromHttpDate() {
        val server = 1_791_774_969L // Mon, 12 Oct 2026 03:16:09 GMT
        assertEquals(600L, EdgeHandshake.skewFromDate("Mon, 12 Oct 2026 03:16:09 GMT", server - 600))
        assertEquals(-30L, EdgeHandshake.skewFromDate("Mon, 12 Oct 2026 03:16:09 GMT", server + 30))
        assertNull(EdgeHandshake.skewFromDate("not a date", server))
        assertNull(EdgeHandshake.skewFromDate(null, server))
        assertNull(EdgeHandshake.skewFromDate("", server))
    }

    @Test
    fun skewMovesTokenIntoServerWindow() {
        val server = 1_791_774_969L
        val device = server - 900
        val skew = EdgeHandshake.skewFromDate("Mon, 12 Oct 2026 03:16:09 GMT", device)!!
        assertNotEquals(EdgeHandshake.secMsGec(server), EdgeHandshake.secMsGec(device))
        assertEquals(EdgeHandshake.secMsGec(server), EdgeHandshake.secMsGec(device + skew))
    }

    @Test
    fun muidIsUpperHex32() {
        val a = EdgeHandshake.newMuid()
        assertTrue(a.matches(Regex("[0-9A-F]{32}")))
        assertNotEquals(a, EdgeHandshake.newMuid())
    }

    @Test
    fun headersCarryMuidCookie() {
        val ws = EdgeHandshake.headers("ABC")
        assertEquals("muid=ABC;", ws["Cookie"])
        assertEquals(EdgeHandshake.ORIGIN, ws["Origin"])
        assertEquals("no-cache", ws["Pragma"])
        val get = EdgeHandshake.headers("ABC", websocket = false)
        assertFalse("Origin" in get)
        assertEquals(EdgeHandshake.USER_AGENT, get["User-Agent"])
    }
}
