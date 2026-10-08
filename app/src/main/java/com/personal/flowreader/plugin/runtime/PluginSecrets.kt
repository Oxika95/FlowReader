package com.personal.flowreader.plugin.runtime

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Per-plugin encrypted key-value store (`flow.secrets`) plus the plugin's cookie jar.
 * Fails closed: if EncryptedSharedPreferences is unavailable nothing is persisted.
 */
class PluginSecrets(context: Context, pluginId: String) {
    private val prefs: SharedPreferences? = run {
        val app = context.applicationContext
        val name = prefsName(pluginId)
        fun open() = EncryptedSharedPreferences.create(
            name,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        // A file restored from another device cannot be decrypted with this device's key.
        runCatching { open() }.recoverCatching {
            app.deleteSharedPreferences(name)
            open()
        }.getOrNull()
    }

    fun isAvailable(): Boolean = prefs != null

    fun get(key: String): String? = prefs?.getString(userKey(key), null)

    fun set(key: String, value: String) {
        requireAvailable()
        prefs?.edit()?.putString(userKey(key), value)?.apply()
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(userKey(key))?.apply()
    }

    /** Clears plugin values; cookies are cleared separately via the jar. */
    fun clear() {
        val p = prefs ?: return
        val edit = p.edit()
        p.all.keys.filter { it.startsWith(USER_PREFIX) }.forEach { edit.remove(it) }
        edit.apply()
    }

    fun loadCookies(): List<Cookie> {
        val blob = prefs?.getString(KEY_COOKIES, "").orEmpty()
        if (blob.isBlank()) return emptyList()
        return blob.lineSequence().mapNotNull { decodeCookie(it) }.toList()
    }

    fun saveCookies(cookies: List<Cookie>) {
        val p = prefs ?: return
        p.edit().putString(KEY_COOKIES, cookies.joinToString("\n") { encodeCookie(it) }).apply()
    }

    fun clearCookies() {
        prefs?.edit()?.remove(KEY_COOKIES)?.apply()
    }

    private fun requireAvailable() {
        if (prefs == null) throw IllegalStateException("Encrypted storage unavailable; sign-in is disabled.")
    }

    companion object {
        private const val USER_PREFIX = "u:"
        private const val KEY_COOKIES = "__cookies"

        fun prefsName(pluginId: String): String = "plugin_secret_$pluginId"

        private fun userKey(key: String): String = USER_PREFIX + key

        fun encodeCookie(cookie: Cookie): String = listOf(
            cookie.name,
            cookie.value,
            cookie.domain,
            cookie.path,
            cookie.expiresAt.toString(),
            cookie.secure.toString(),
            cookie.httpOnly.toString(),
            cookie.hostOnly.toString(),
        ).joinToString("\t")

        fun decodeCookie(line: String): Cookie? {
            val p = line.split('\t')
            if (p.size < 8) return null
            return runCatching {
                val builder = Cookie.Builder()
                    .name(p[0])
                    .value(p[1])
                    .path(p[3].ifBlank { "/" })
                    .expiresAt(p[4].toLong())
                if (p[7].toBoolean()) builder.hostOnlyDomain(p[2]) else builder.domain(p[2])
                if (p[5].toBoolean()) builder.secure()
                if (p[6].toBoolean()) builder.httpOnly()
                builder.build()
            }.getOrNull()
        }
    }
}

class PluginCookieJar(private val secrets: PluginSecrets) : CookieJar {
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
