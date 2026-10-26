package com.personal.flowreader.plugin.runtime

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearanceOnlyCookieJarTest {
    @Test
    fun isClearanceCookieAllowlist() {
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("techaro.lol-anubis"))
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("techaro.lol-anubis-347ddb4a"))
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("techaro.lol-anubis-cookie-verification-x"))
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("cf_clearance"))
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("__cf_bm"))
        assertTrue(ClearanceOnlyCookieJar.isClearanceCookie("__cfruid"))
        assertFalse(ClearanceOnlyCookieJar.isClearanceCookie(".AspNetCore.Identity.Application"))
        assertFalse(ClearanceOnlyCookieJar.isClearanceCookie("session_id"))
    }

    @Test
    fun loadAndSaveFilterSessionCookies() {
        val url = "https://www.royalroad.com/fiction/1".toHttpUrl()
        val store = MemoryJar()
        store.saveFromResponse(
            url,
            listOf(
                cookie("techaro.lol-anubis", "jwt", url),
                cookie("techaro.lol-anubis-cookie-verification-x", "v", url),
                cookie(".AspNetCore.Identity.Application", "session", url),
            ),
        )
        val jar = ClearanceOnlyCookieJar(store)
        assertEquals(
            listOf("techaro.lol-anubis", "techaro.lol-anubis-cookie-verification-x"),
            jar.loadForRequest(url).map { it.name },
        )
        jar.saveFromResponse(
            url,
            listOf(
                cookie("cf_clearance", "cf", url),
                cookie(".AspNetCore.Identity.Application", "nope", url),
            ),
        )
        val stored = store.loadForRequest(url)
        assertTrue(stored.any { it.name == "cf_clearance" && it.value == "cf" })
        assertFalse(stored.any { it.name == ".AspNetCore.Identity.Application" && it.value == "nope" })
        // Session cookie from a prior full-jar save remains in the delegate; clearance client never sends it.
        assertEquals(
            listOf("techaro.lol-anubis", "techaro.lol-anubis-cookie-verification-x", "cf_clearance"),
            jar.loadForRequest(url).map { it.name },
        )
    }

    private fun cookie(name: String, value: String, url: HttpUrl): Cookie =
        Cookie.Builder().name(name).value(value).domain(url.host).build()

    private class MemoryJar : CookieJar {
        private var cookies: List<Cookie> = emptyList()

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val byName = this.cookies.associateBy { it.name }.toMutableMap()
            cookies.forEach { byName[it.name] = it }
            this.cookies = byName.values.toList()
        }
    }
}
