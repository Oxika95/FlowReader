package com.personal.flowreader.plugin.runtime

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * For `flow.fetch(..., { cookies: false })`: send and store bot-clearance cookies only
 * (Anubis / Cloudflare), never session or identity cookies.
 */
class ClearanceOnlyCookieJar(private val delegate: CookieJar) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        delegate.loadForRequest(url).filter { isClearanceCookie(it.name) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        delegate.saveFromResponse(url, cookies.filter { isClearanceCookie(it.name) })
    }

    companion object {
        fun isClearanceCookie(name: String): Boolean =
            AnubisCookies.isAnubisAuthCookie(name) ||
                name == "cf_clearance" ||
                name == "__cf_bm" ||
                name == "__cfruid"
    }
}
