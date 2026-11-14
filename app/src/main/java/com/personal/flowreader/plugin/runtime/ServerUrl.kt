package com.personal.flowreader.plugin.runtime

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Parses the user-set Audiobookshelf (or similar) server base URL. */
object ServerUrl {
    /**
     * Exact host of [raw], or null when blank, invalid, or the URL carries userinfo
     * (credentials must not sit in the address field).
     */
    fun host(raw: String): String? {
        val url = normalize(raw) ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return url.host.lowercase().removePrefix("www.").ifBlank { null }
    }

    fun allowsHttp(raw: String): Boolean {
        val url = normalize(raw) ?: return false
        return url.scheme == "http" && host(raw) != null
    }

    /** Base URL with no trailing slash, or null when invalid. */
    fun normalize(raw: String): okhttp3.HttpUrl? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()
    }

    fun base(raw: String): String? = normalize(raw)?.toString()?.trimEnd('/')
}
