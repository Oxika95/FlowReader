package com.personal.flowreader.plugin.runtime

/**
 * Anubis cookie name rules (Techaro). Auth cookies are `techaro.lol-anubis` or
 * `techaro.lol-anubis-<suffix>`; verification / referer cookies are not auth.
 */
object AnubisCookies {
    fun isAnubisAuthCookie(name: String): Boolean =
        name.startsWith("techaro.lol-anubis") &&
            !name.contains("cookie-verification") &&
            !name.contains("original-referer")

    /** Non-blank auth cookie name → value. */
    fun authCookieMap(cookies: Iterable<Pair<String, String>>): Map<String, String> =
        cookies
            .filter { (name, value) -> isAnubisAuthCookie(name) && value.isNotBlank() }
            .associate { (name, value) -> name to value }

    /** True when [current] has an auth cookie that is new or whose value changed vs [baseline]. */
    fun hasNewAuthCookie(baseline: Map<String, String>, current: Map<String, String>): Boolean =
        current.any { (name, value) -> baseline[name] != value }
}
