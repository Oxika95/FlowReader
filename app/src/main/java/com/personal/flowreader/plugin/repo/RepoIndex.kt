package com.personal.flowreader.plugin.repo

import com.personal.flowreader.plugin.api.objects
import java.net.URI
import org.json.JSONObject

/** One plugin entry in a repository `index.json`; URLs are already resolved to absolute. */
data class RepoPlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val apiVersion: Int,
    val lang: String,
    val iconUrl: String,
    val manifestUrl: String,
    val pluginUrl: String,
    val manifestSha256: String,
    val sha256: String,
    val repoUrl: String,
)

data class RepoIndex(
    val url: String,
    val name: String,
    val apiVersion: Int,
    val plugins: List<RepoPlugin>,
) {
    companion object {
        private val ID = Regex("^[a-z0-9][a-z0-9_-]{1,39}$")
        private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

        /** Parses an index; entries missing an id, code URL, or sha256 are skipped. */
        fun parse(json: String, indexUrl: String): RepoIndex {
            val root = JSONObject(json)
            val plugins = root.optJSONArray("plugins").objects().mapNotNull { o ->
                val id = o.optString("id").trim()
                val sha = o.optString("sha256").trim()
                val pluginUrl = o.optString("pluginUrl").trim()
                val manifestUrl = o.optString("manifestUrl").trim()
                if (!ID.matches(id) || !HEX64.matches(sha) || pluginUrl.isEmpty() || manifestUrl.isEmpty()) {
                    return@mapNotNull null
                }
                RepoPlugin(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    description = o.optString("description"),
                    version = o.optString("version").ifBlank { "0" },
                    apiVersion = o.optInt("apiVersion", 1),
                    lang = o.optString("lang"),
                    iconUrl = o.optString("iconUrl").takeIf { it.isNotBlank() }?.let { resolve(indexUrl, it) }.orEmpty(),
                    manifestUrl = resolve(indexUrl, manifestUrl),
                    pluginUrl = resolve(indexUrl, pluginUrl),
                    manifestSha256 = o.optString("manifestSha256").trim().lowercase().takeIf { HEX64.matches(it) }.orEmpty(),
                    sha256 = sha.lowercase(),
                    repoUrl = indexUrl,
                )
            }
            return RepoIndex(
                url = indexUrl,
                name = root.optString("name").ifBlank { URI(indexUrl).host ?: indexUrl },
                apiVersion = root.optInt("apiVersion", 1),
                plugins = plugins,
            )
        }

        fun resolve(base: String, ref: String): String = URI(base).resolve(ref.trim()).toString()
    }
}
