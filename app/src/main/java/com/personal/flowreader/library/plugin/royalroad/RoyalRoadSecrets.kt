package com.personal.flowreader.library.plugin.royalroad

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookies (+ optional remembered email for the login form) for Royal Road.
 * Passwords are never persisted. Fail closed if EncryptedSharedPreferences is unavailable
 * (no plaintext fallback): sign-in is disabled until encryption works.
 */
class RoyalRoadSecrets(context: Context) {
    private val prefs: SharedPreferences?
    private val disabledReason: String?

    init {
        // Wipe any leftover plaintext fallback from older builds.
        runCatching {
            context.getSharedPreferences("${PREFS}_fallback", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        var loaded: SharedPreferences? = null
        var reason: String? = null
        try {
            val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            loaded = EncryptedSharedPreferences.create(
                PREFS,
                key,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            // Migration: drop any password left from older builds.
            if (loaded.contains(KEY_PASSWORD)) {
                loaded.edit().remove(KEY_PASSWORD).apply()
            }
        } catch (t: Throwable) {
            reason = "Encrypted storage unavailable; Royal Road sign-in is disabled."
        }
        prefs = loaded
        disabledReason = reason
    }

    fun isAvailable(): Boolean = prefs != null

    fun unavailableMessage(): String =
        disabledReason ?: "Royal Road sign-in is unavailable."

    private fun prefsOrNull(): SharedPreferences? = prefs

    fun email(): String = prefsOrNull()?.getString(KEY_EMAIL, "").orEmpty()

    fun isLoggedIn(): Boolean = prefsOrNull()?.getBoolean(KEY_LOGGED_IN, false) == true

    fun setLoggedIn(value: Boolean) {
        prefsOrNull()?.edit()?.putBoolean(KEY_LOGGED_IN, value)?.apply()
    }

    /** Remember email for the login form only — never the password. */
    fun saveEmail(email: String) {
        prefsOrNull()?.edit()?.putString(KEY_EMAIL, email.trim())?.apply()
    }

    fun clearEmail() {
        prefsOrNull()?.edit()?.remove(KEY_EMAIL)?.apply()
    }

    fun loadCookies(): List<Cookie> {
        val blob = prefsOrNull()?.getString(KEY_COOKIES, "").orEmpty()
        if (blob.isBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { parseCookie(it) }.toList()
    }

    fun saveCookies(cookies: List<Cookie>) {
        val p = prefsOrNull() ?: return
        val blob = cookies.joinToString("\n") { cookie ->
            listOf(
                cookie.name,
                cookie.value,
                cookie.domain,
                cookie.path,
                cookie.expiresAt.toString(),
                cookie.secure.toString(),
                cookie.httpOnly.toString(),
                cookie.hostOnly.toString(),
            ).joinToString("\t")
        }
        p.edit().putString(KEY_COOKIES, blob).apply()
    }

    fun clearCookies() {
        prefsOrNull()?.edit()?.remove(KEY_COOKIES)?.apply()
    }

    fun clearAll() {
        prefsOrNull()?.edit()?.clear()?.apply()
    }

    fun requireAvailable() {
        if (prefs == null) throw IllegalStateException(unavailableMessage())
    }

    private fun parseCookie(line: String): Cookie? {
        val p = line.split('\t')
        if (p.size < 8) return null
        return runCatching {
            val builder = Cookie.Builder()
                .name(p[0])
                .value(p[1])
                .path(p[3].ifBlank { "/" })
                .expiresAt(p[4].toLong())
            if (p[7].toBoolean()) {
                builder.hostOnlyDomain(p[2])
            } else {
                builder.domain(p[2])
            }
            if (p[5].toBoolean()) builder.secure()
            if (p[6].toBoolean()) builder.httpOnly()
            builder.build()
        }.getOrNull()
    }

    companion object {
        private const val PREFS = "royalroad_secret"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_LOGGED_IN = "logged_in"
    }
}

/** Session expired or protected page requires a fresh sign-in. */
class RoyalRoadAuthExpired(message: String = "Sign in to Royal Road again.") : Exception(message)

class PersistentCookieJar(private val secrets: RoyalRoadSecrets) : CookieJar {
    private val lock = Any()
    private var cookies: List<Cookie> = secrets.loadCookies()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val byKey = this.cookies.associateBy { it.name to it.domain }.toMutableMap()
            cookies.forEach { byKey[it.name to it.domain] = it }
            val now = System.currentTimeMillis()
            this.cookies = byKey.values.filter { it.expiresAt > now }
            secrets.saveCookies(this.cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cookies = cookies.filter { it.expiresAt > now }
            return cookies.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(lock) {
            cookies = emptyList()
            secrets.clearCookies()
        }
    }
}
