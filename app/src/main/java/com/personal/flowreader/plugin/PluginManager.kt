package com.personal.flowreader.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.personal.flowreader.plugin.api.PLUGIN_HOST_API_VERSION
import com.personal.flowreader.plugin.api.PLUGIN_MIN_API_VERSION
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginSettingType
import com.personal.flowreader.plugin.api.compareVersions
import com.personal.flowreader.plugin.runtime.JsPluginRuntime
import com.personal.flowreader.plugin.runtime.PluginCookieJar
import com.personal.flowreader.plugin.runtime.PluginHttp
import com.personal.flowreader.plugin.runtime.PluginKvStore
import com.personal.flowreader.plugin.runtime.PluginSecrets
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class InstalledPlugin(
    val manifest: PluginManifest,
    val dir: File,
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name
}

/**
 * Installed JS plugins: disk layout, bundled seeding, runtimes, and per-plugin settings.
 *
 * Layout under `filesDir/plugins/`:
 * - `installed/{id}/plugin.json`, `installed/{id}/index.js`
 * - `data/{id}/` app-owned story cache, lists, settings, and key-value store
 */
class PluginManager(private val context: Context) {
    val root: File = File(context.filesDir, "plugins")
    private val installedRoot = File(root, "installed")
    private val dataRoot = File(root, "data")
    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed
    private val sources = HashMap<String, Pair<PluginSource, JsPluginRuntime>>()

    fun initialize() {
        installedRoot.mkdirs()
        dataRoot.mkdirs()
        installBundled()
        reload()
    }

    fun get(id: String): InstalledPlugin? = _installed.value.firstOrNull { it.id == id }

    fun ids(): Set<String> = _installed.value.map { it.id }.toSet()

    fun dataDir(pluginId: String): File = File(dataRoot, pluginId).apply { mkdirs() }

    fun source(pluginId: String): PluginSource = synchronized(sources) {
        sources[pluginId]?.first?.let { return it }
        val plugin = get(pluginId)
            ?: throw PluginException(PluginErrorCode.Unsupported, "Plugin $pluginId is not installed")
        val code = File(plugin.dir, CODE_FILE).readText()
        val secrets = PluginSecrets(context, pluginId)
        val http = PluginHttp(
            allowedHosts = plugin.manifest.allowedHosts,
            minIntervalMs = plugin.manifest.minRequestIntervalMs,
            cookieJar = PluginCookieJar(secrets),
        )
        val runtime = JsPluginRuntime(
            manifest = plugin.manifest,
            code = code,
            http = http,
            secrets = secrets,
            kv = PluginKvStore.forFile(File(dataDir(pluginId), "_kv.preferences_pb")),
            settingsJson = { settingsValues(pluginId).toString() },
        )
        val source = PluginSource(plugin.manifest, runtime)
        sources[pluginId] = source to runtime
        source
    }

    /** `{prefix}:{workId}` book id, or null when no installed plugin owns the prefix. */
    fun resolveBookId(bookId: String): Pair<InstalledPlugin, String>? {
        val sep = bookId.indexOf(':')
        if (sep <= 0) return null
        val prefix = bookId.substring(0, sep)
        val plugin = _installed.value.firstOrNull { it.manifest.bookIdPrefix == prefix } ?: return null
        return plugin to bookId.substring(sep + 1)
    }

    fun bookIdFor(pluginId: String, workId: String): String {
        val prefix = get(pluginId)?.manifest?.bookIdPrefix ?: pluginId
        return "$prefix:$workId"
    }

    fun isPluginBook(bookId: String): Boolean = resolveBookId(bookId) != null

    /** Typed values for `flow.settings`, defaults merged with saved edits. */
    fun settingsValues(pluginId: String): JSONObject {
        val manifest = get(pluginId)?.manifest ?: return JSONObject()
        val saved = readSavedSettings(pluginId)
        val out = JSONObject()
        manifest.settings.forEach { s ->
            val raw = if (saved.has(s.key)) saved.optString(s.key) else s.default
            when (s.type) {
                PluginSettingType.Toggle -> out.put(s.key, raw.equals("true", ignoreCase = true))
                PluginSettingType.Integer -> out.put(s.key, raw.toIntOrNull() ?: s.default.toIntOrNull() ?: 0)
                PluginSettingType.Choice, PluginSettingType.Text -> out.put(s.key, raw)
            }
        }
        return out
    }

    fun setSetting(pluginId: String, key: String, value: String) {
        val saved = readSavedSettings(pluginId)
        saved.put(key, value)
        File(dataDir(pluginId), SETTINGS_FILE).writeText(saved.toString())
    }

    /** Atomic install/update: write to a temp dir, then swap. Restarts the plugin runtime. */
    fun install(manifestJson: String, code: String): InstalledPlugin {
        val manifest = PluginManifest.parse(manifestJson)
        if (manifest.apiVersion > PLUGIN_HOST_API_VERSION) {
            throw PluginException(
                PluginErrorCode.Unsupported,
                "${manifest.name} needs a newer Flow Reader (plugin API ${manifest.apiVersion})",
            )
        }
        if (manifest.apiVersion < PLUGIN_MIN_API_VERSION) {
            throw PluginException(
                PluginErrorCode.Unsupported,
                "${manifest.name} uses plugin API ${manifest.apiVersion}, which is no longer supported",
            )
        }
        val clash = _installed.value.firstOrNull {
            it.id != manifest.id && it.manifest.bookIdPrefix == manifest.bookIdPrefix
        }
        if (clash != null) {
            throw PluginException(
                PluginErrorCode.Unsupported,
                "${manifest.name} uses the same book id prefix as ${clash.name}",
            )
        }
        installedRoot.mkdirs()
        val tmp = File(installedRoot, ".tmp-${manifest.id}")
        tmp.deleteRecursively()
        tmp.mkdirs()
        File(tmp, MANIFEST_FILE).writeText(manifestJson)
        File(tmp, CODE_FILE).writeText(code)
        val dest = File(installedRoot, manifest.id)
        val old = File(installedRoot, ".old-${manifest.id}")
        old.deleteRecursively()
        if (dest.exists() && !dest.renameTo(old)) {
            tmp.deleteRecursively()
            throw PluginException(PluginErrorCode.Error, "Could not replace ${manifest.name}")
        }
        if (!tmp.renameTo(dest)) {
            old.renameTo(dest)
            throw PluginException(PluginErrorCode.Error, "Could not install ${manifest.name}")
        }
        old.deleteRecursively()
        closeRuntime(manifest.id)
        reload()
        return get(manifest.id) ?: throw PluginException(PluginErrorCode.Error, "Install failed")
    }

    /** Removes plugin code. Story data and sign-in stay so a reinstall restores the library. */
    fun uninstall(pluginId: String) {
        closeRuntime(pluginId)
        File(installedRoot, pluginId).deleteRecursively()
        reload()
    }

    fun reload() {
        val list = installedRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { dir ->
                runCatching {
                    val manifest = PluginManifest.parse(File(dir, MANIFEST_FILE).readText())
                    if (manifest.id != dir.name || !File(dir, CODE_FILE).exists()) return@runCatching null
                    InstalledPlugin(manifest, dir)
                }.onFailure { Log.w(TAG, "Skipping plugin dir ${dir.name}", it) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        _installed.value = list
        displayNames = list.associate { it.id to it.name }
    }

    private fun closeRuntime(pluginId: String) {
        synchronized(sources) { sources.remove(pluginId) }?.second?.close()
    }

    private fun readSavedSettings(pluginId: String): JSONObject {
        val f = File(dataDir(pluginId), SETTINGS_FILE)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    /**
     * Seeds plugins shipped in `assets/plugins/{dir}/` (debug builds bundle a local
     * flow-reader-plugins checkout). Release builds only install or upgrade; debuggable builds
     * mirror the bundled files exactly so local plugin edits apply without a version bump.
     */
    private fun installBundled() {
        val assets = context.assets
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val dirs = runCatching { assets.list(BUNDLED_DIR) }.getOrNull().orEmpty()
        for (name in dirs) {
            runCatching {
                val manifestJson = assets.open("$BUNDLED_DIR/$name/$MANIFEST_FILE").bufferedReader().use { it.readText() }
                val code = assets.open("$BUNDLED_DIR/$name/$CODE_FILE").bufferedReader().use { it.readText() }
                val manifest = PluginManifest.parse(manifestJson)
                val installedDir = File(installedRoot, manifest.id)
                val current = File(installedDir, MANIFEST_FILE)
                val installedVersion = current.takeIf { it.exists() }
                    ?.let { runCatching { PluginManifest.parse(it.readText()).version }.getOrNull() }
                val seededBefore = File(installedRoot, ".seeded-${manifest.id}").exists()
                val shouldInstall = when {
                    installedVersion == null -> !seededBefore
                    compareVersions(manifest.version, installedVersion) > 0 -> true
                    debuggable -> current.readText() != manifestJson ||
                        File(installedDir, CODE_FILE).let { !it.exists() || it.readText() != code }
                    else -> false
                }
                if (shouldInstall) install(manifestJson, code)
                File(installedRoot, ".seeded-${manifest.id}").writeText(manifest.version)
            }.onFailure { Log.w(TAG, "Bundled plugin $name failed", it) }
        }
    }

    companion object {
        private const val TAG = "PluginManager"
        const val MANIFEST_FILE = "plugin.json"
        const val CODE_FILE = "index.js"
        private const val SETTINGS_FILE = "_settings.json"
        private const val BUNDLED_DIR = "plugins"

        /** Plugin id to display name, for non-composable labels (library card subtitles). */
        @Volatile
        var displayNames: Map<String, String> = emptyMap()
            private set
    }
}
