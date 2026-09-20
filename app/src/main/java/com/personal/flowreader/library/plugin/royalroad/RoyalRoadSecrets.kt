package com.personal.flowreader.library.plugin.royalroad

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Encrypted email/password + cookies for Royal Road. Falls back to private prefs if Keystore fails. */
class RoyalRoadSecrets(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS,
            key,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) {
        context.getSharedPreferences("${PREFS}_fallback", Context.MODE_PRIVATE)
    }

    fun email(): String = prefs.getString(KEY_EMAIL, "").orEmpty()

    fun password(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()

    fun hasCredentials(): Boolean = email().isNotBlank() && password().isNotBlank()

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
    }

    fun saveCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    fun loadCookies(): List<Cookie> {
        val blob = prefs.getString(KEY_COOKIES, "").orEmpty()
        if (blob.isBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { parseCookie(it) }.toList()
    }

    fun saveCookies(cookies: List<Cookie>) {
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
        prefs.edit().putString(KEY_COOKIES, blob).apply()
    }

    fun clearCookies() {
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
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
