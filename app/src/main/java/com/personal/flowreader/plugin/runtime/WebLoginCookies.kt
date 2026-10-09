package com.personal.flowreader.plugin.runtime

import okhttp3.Cookie

/**
 * Cookies captured from the in-app sign-in browser. WebView's `CookieManager.getCookie` returns
 * only `name=value` pairs, so domain and lifetime are reconstructed for the plugin jar.
 */
object WebLoginCookies {
    /** Default lifetime of an imported cookie; the plugin's `session()` notices an earlier expiry. */
    const val LIFETIME_MS = 365L * 24 * 60 * 60 * 1000

    /** Origins to read for [allowedHosts] (bare and `www.`). */
    fun origins(allowedHosts: List<String>): List<String> =
        allowedHosts.map { it.trim().lowercase().removePrefix("www.") }
            .filter { it.isNotEmpty() }
            .distinct()
            .flatMap { listOf("https://$it/", "https://www.$it/") }

    /** `a=1; b=2` from [header] as cookies for [host] and its subdomains. */
    fun parse(header: String?, host: String, now: Long): List<Cookie> {
        if (header.isNullOrBlank()) return emptyList()
        val domain = host.trim().lowercase().removePrefix("www.")
        return header.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val name = part.substring(0, eq).trim()
            val value = part.substring(eq + 1).trim()
            if (name.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .expiresAt(now + LIFETIME_MS)
                    .secure()
                    .httpOnly()
                    .build()
            }.getOrNull()
        }
    }

    /** True when [header] carries a non-empty cookie named [name]. */
    fun has(header: String?, name: String): Boolean =
        header.orEmpty().split(';').any { part ->
            val eq = part.indexOf('=')
            eq > 0 && part.substring(0, eq).trim() == name && part.substring(eq + 1).isNotBlank()
        }
}
