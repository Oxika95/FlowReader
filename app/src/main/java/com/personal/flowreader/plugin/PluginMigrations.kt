package com.personal.flowreader.plugin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.plugin.runtime.PluginSecrets
import com.personal.flowreader.plugin.store.PluginMembershipStore
import java.io.File

/**
 * One-time moves from the built-in Kotlin Royal Road plugin to the JS plugin layout.
 * Idempotent; guarded by a marker file under `filesDir/plugins`.
 */
object PluginMigrations {
    private const val RR_ID = "royalroad"
    private const val MARKER = ".migrated-builtin-rr-v1"
    private val FICTION_ID = Regex("/fiction/(\\d+)")

    private val legacyLists = listOf(
        "follows_membership.txt" to "follow",
        "favorites_membership.txt" to "favorite",
        "readlater_membership.txt" to "readlater",
    )

    fun run(context: Context, pluginsRoot: File) {
        val marker = File(pluginsRoot, MARKER)
        if (marker.exists()) return
        pluginsRoot.mkdirs()
        runCatching { migrateRoyalRoadData(pluginsRoot) }
        runCatching { migrateRoyalRoadSecrets(context) }
        marker.writeText("1")
    }

    private fun migrateRoyalRoadData(pluginsRoot: File) {
        val legacy = File(pluginsRoot, RR_ID)
        if (!legacy.isDirectory) return
        val target = File(File(pluginsRoot, "data"), RR_ID).apply { mkdirs() }
        legacy.listFiles()?.forEach { child ->
            val list = legacyLists.firstOrNull { it.first == child.name }
            when {
                list != null -> {
                    val works = readLegacyList(child)
                    if (works.isNotEmpty()) PluginMembershipStore.write(target, list.second, works)
                    child.delete()
                }
                child.isDirectory -> {
                    val dest = File(target, child.name)
                    if (!dest.exists() && !child.renameTo(dest)) {
                        child.copyRecursively(dest, overwrite = true)
                        child.deleteRecursively()
                    }
                }
            }
        }
        legacy.deleteRecursively()
    }

    private fun readLegacyList(file: File): List<PluginWork> = file.readLines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 2) return@mapNotNull null
        val url = p[1]
        val id = FICTION_ID.find(url)?.groupValues?.get(1) ?: return@mapNotNull null
        PluginWork(
            id = id,
            title = legacyUnescape(p[0]).ifBlank { id },
            url = url,
            author = p.getOrNull(2)?.let(::legacyUnescape).orEmpty(),
            cover = p.getOrNull(4).orEmpty(),
            subtitle = p.getOrNull(3)?.let(::legacyUnescape).orEmpty(),
        )
    }

    private fun legacyUnescape(value: String): String =
        value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")

    private fun migrateRoyalRoadSecrets(context: Context) {
        val old = runCatching {
            EncryptedSharedPreferences.create(
                "royalroad_secret",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull() ?: return
        val email = old.getString("email", "").orEmpty()
        val loggedIn = old.getBoolean("logged_in", false)
        val cookies = old.getString("cookies", "").orEmpty()
        if (email.isBlank() && !loggedIn && cookies.isBlank()) return
        val values = buildMap {
            if (email.isNotBlank()) put("email", email)
            put("loggedIn", loggedIn.toString())
        }
        PluginSecrets(context, RR_ID).importRaw(values, cookies)
        old.edit().clear().apply()
    }
}
