package com.personal.flowreader.plugin.repo

import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.api.PLUGIN_HOST_API_VERSION
import com.personal.flowreader.plugin.api.PLUGIN_MIN_API_VERSION
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.compareVersions
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads, verifies (sha256), and installs plugins listed in a repository index. */
class PluginInstaller(
    private val manager: PluginManager,
    private val download: (url: String, maxBytes: Long) -> ByteArray,
) {
    constructor(manager: PluginManager, repos: RepoManager) : this(manager, repos::download)

    fun isCompatible(plugin: RepoPlugin): Boolean =
        plugin.apiVersion in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION

    fun updateAvailable(installed: InstalledPlugin, plugin: RepoPlugin): Boolean =
        installed.id == plugin.id && compareVersions(plugin.version, installed.manifest.version) > 0

    suspend fun install(plugin: RepoPlugin): InstalledPlugin = withContext(Dispatchers.IO) {
        if (!isCompatible(plugin)) {
            throw PluginException(
                PluginErrorCode.Unsupported,
                if (plugin.apiVersion > PLUGIN_HOST_API_VERSION) {
                    "${plugin.name} needs a newer Flow Reader (plugin API ${plugin.apiVersion})"
                } else {
                    "${plugin.name} uses plugin API ${plugin.apiVersion}, which is no longer supported"
                },
            )
        }
        val manifestBytes = download(plugin.manifestUrl, MAX_MANIFEST_BYTES)
        if (plugin.manifestSha256.isNotEmpty()) verify(manifestBytes, plugin.manifestSha256, "plugin.json")
        val codeBytes = download(plugin.pluginUrl, MAX_CODE_BYTES)
        verify(codeBytes, plugin.sha256, "index.js")
        val manifestJson = String(manifestBytes, Charsets.UTF_8)
        val manifest = PluginManifest.parse(manifestJson)
        if (manifest.id != plugin.id) {
            throw PluginException(PluginErrorCode.Error, "Repository entry ${plugin.id} ships plugin ${manifest.id}")
        }
        manager.install(manifestJson, String(codeBytes, Charsets.UTF_8))
    }

    private fun verify(bytes: ByteArray, expected: String, label: String) {
        val actual = sha256Hex(bytes)
        if (!actual.equals(expected, ignoreCase = true)) {
            throw PluginException(PluginErrorCode.Blocked, "$label checksum mismatch; refusing to install")
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_CODE_BYTES = 4L * 1024 * 1024

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
