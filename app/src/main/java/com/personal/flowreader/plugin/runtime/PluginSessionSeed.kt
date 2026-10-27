package com.personal.flowreader.plugin.runtime

import com.personal.flowreader.plugin.api.PluginSession

/**
 * Seeds Account UI from encrypted secrets before a network `session()` round-trip, and soft
 * re-login nudges when the cookie blob is older than a per-plugin TTL (sites expire sooner than
 * the synthetic 1-year jar lifetime).
 */
object PluginSessionSeed {
    const val COOKIES_IMPORTED_AT = "cookiesImportedAt"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Soft nudge only; HTTP / `session()` still decide hard expiry. */
    fun softTtlMs(pluginId: String): Long = when (pluginId) {
        "patreon" -> 14 * DAY_MS
        else -> 30 * DAY_MS // royalroad and future synced plugins
    }

    /**
     * RR stores `loggedIn=true`; Patreon treats a non-blank `account` as signed in.
     * Offline UI prefers the last known account label.
     */
    fun fromSecrets(account: String?, loggedInFlag: String?): PluginSession {
        val name = account.orEmpty()
        val loggedIn = loggedInFlag == "true" || name.isNotBlank()
        return PluginSession(loggedIn = loggedIn, account = name)
    }

    fun staleHint(pluginId: String, importedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
        if (importedAtMs == null || importedAtMs <= 0L) return null
        if (nowMs - importedAtMs < softTtlMs(pluginId)) return null
        return "Session may be stale — sign in again to refresh."
    }

    fun parseImportedAt(raw: String?): Long? = raw?.toLongOrNull()?.takeIf { it > 0L }
}
