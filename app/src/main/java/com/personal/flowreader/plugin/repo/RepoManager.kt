package com.personal.flowreader.plugin.repo

import com.personal.flowreader.data.SettingsStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A configured repository and the result of its last fetch. */
data class PluginRepo(
    val url: String,
    val index: RepoIndex? = null,
    val error: String? = null,
) {
    val official: Boolean get() = url == RepoManager.OFFICIAL_REPO
    val name: String get() = index?.name ?: url
}

/**
 * User-managed plugin repositories (Tachiyomi / LNReader style). Each repo is an `index.json`
 * URL; the official repo is pre-added and can be removed like any other.
 */
class RepoManager(private val settings: SettingsStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _repos = MutableStateFlow<List<PluginRepo>>(emptyList())
    val repos: StateFlow<List<PluginRepo>> = _repos

    suspend fun urls(): List<String> = settings.pluginReposOnce() ?: listOf(OFFICIAL_REPO)

    /** Validates and saves [raw]; returns the normalized URL. */
    suspend fun add(raw: String): String {
        val url = normalize(raw)
        val current = urls()
        if (url !in current) settings.setPluginRepos(current + url)
        refresh()
        return url
    }

    suspend fun remove(url: String) {
        settings.setPluginRepos(urls() - url)
        _repos.value = _repos.value.filterNot { it.url == url }
    }

    suspend fun refresh(): List<PluginRepo> = coroutineScope {
        val list = urls().map { url -> async { fetch(url) } }.awaitAll()
        _repos.value = list
        list
    }

    private suspend fun fetch(url: String): PluginRepo = withContext(Dispatchers.IO) {
        runCatching {
            val body = download(url, MAX_INDEX_BYTES)
            PluginRepo(url, index = RepoIndex.parse(String(body, Charsets.UTF_8), url))
        }.getOrElse { PluginRepo(url, error = it.message ?: "Could not load repository") }
    }

    internal fun download(url: String, maxBytes: Long): ByteArray {
        val request = Request.Builder().url(url).header("Accept", "application/json, */*").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IllegalStateException("Empty response from $url")
            val length = body.contentLength()
            if (length > maxBytes) throw IllegalStateException("$url is too large")
            val bytes = body.byteStream().use { it.readNBytesCompat(maxBytes + 1) }
            if (bytes.size > maxBytes) throw IllegalStateException("$url is too large")
            return bytes
        }
    }

    companion object {
        const val OFFICIAL_REPO = "https://oxika95.github.io/flow-reader-plugins/index.json"
        private const val MAX_INDEX_BYTES = 1L * 1024 * 1024

        fun normalize(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) throw IllegalArgumentException("Enter a repository URL")
            if (!url.contains("://")) url = "https://$url"
            if (!url.startsWith("https://", ignoreCase = true)) {
                throw IllegalArgumentException("Repositories must use https://")
            }
            if (!url.substringBefore('?').endsWith(".json", ignoreCase = true)) {
                url = url.trimEnd('/') + "/index.json"
            }
            return url
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
