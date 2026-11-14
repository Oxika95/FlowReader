package com.personal.flowreader.plugin.runtime

import com.personal.flowreader.debug.DebugLog
import com.personal.flowreader.net.HttpClients
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * `flow.fetch` backend for one plugin: host allowlist, per-plugin cookies, and a request gap.
 * Never throws to JS; failures come back as `{ error: { code, message } }`.
 *
 * Anubis challenge pages trigger at most one WebView clearance ([PluginClearanceGate]) and one retry.
 *
 * Optional [serverHost] adds that exact host (no subdomain rule). Optional [bearerToken] is sent
 * as `Authorization: Bearer` unless the call sets `auth: false`. `saveAs` writes bytes under
 * [filesDir] instead of returning text.
 */
class PluginHttp(
    private val pluginId: String,
    allowedHosts: List<String>,
    private val minIntervalMs: Long,
    val cookieJar: PluginCookieJar,
    private val serverHost: () -> String? = { null },
    private val serverAllowsHttp: () -> Boolean = { false },
    private val bearerToken: () -> String? = { null },
    val filesDir: File? = null,
) {
    private val allowedHostsList = allowedHosts.map { it.trim() }.filter { it.isNotEmpty() }
    private val allowed = allowedHostsList.map { it.lowercase().removePrefix("www.") }
    private val client: OkHttpClient = HttpClients.create {
        followRedirects(true)
        cookieJar(cookieJar)
    }
    /** `cookies: false`: clearance cookies only (Anubis / CF), not session cookies. */
    private val clearanceClient: OkHttpClient =
        client.newBuilder().cookieJar(ClearanceOnlyCookieJar(cookieJar)).build()
    private val rate = Mutex()
    private var lastFetchAt = 0L

    fun isAllowed(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        val dynamic = serverHost()?.lowercase()?.removePrefix("www.").orEmpty()
        if (dynamic.isNotEmpty() && h == dynamic) return true
        return allowed.any { h == it || h.endsWith(".$it") }
    }

    /** Whether [url] may use http (cleartext) given the configured server. */
    fun allowsHttp(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.isHttps) return true
        if (parsed.scheme != "http") return false
        val h = parsed.host.lowercase().removePrefix("www.")
        val dynamic = serverHost()?.lowercase()?.removePrefix("www.").orEmpty()
        return dynamic.isNotEmpty() && h == dynamic && serverAllowsHttp()
    }

    suspend fun fetch(requestJson: String): String {
        val req = runCatching { JSONObject(requestJson) }.getOrNull()
            ?: return logError("ERROR", "Bad fetch arguments", method = "?", url = "?")
        val url = req.optString("url").trim()
        val parsed = url.toHttpUrlOrNull()
            ?: return logError("ERROR", "Invalid URL: $url", method = "?", url = url)
        if (!isAllowed(parsed.host)) {
            return logError(
                "BLOCKED",
                "Host not allowed by plugin manifest: ${parsed.host}",
                method = "?",
                url = parsed.toString(),
            )
        }
        if (parsed.scheme == "http" && !allowsHttp(parsed.toString())) {
            return logError(
                "BLOCKED",
                "Cleartext HTTP is only allowed for the configured server",
                method = "?",
                url = parsed.toString(),
            )
        }
        val opts = req.optJSONObject("opts") ?: JSONObject()
        return fetchAttempt(parsed.toString(), opts, alreadyRetried = false)
    }

    private suspend fun fetchAttempt(url: String, opts: JSONObject, alreadyRetried: Boolean): String {
        val method = opts.optString("method").ifBlank { "GET" }.uppercase()
        val builder = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", DEFAULT_ACCEPT)
            .header("Accept-Language", "en-US,en;q=0.9")
        opts.optJSONObject("headers")?.let { headers ->
            headers.keys().forEach { k -> builder.header(k, headers.optString(k)) }
        }
        val hasAuthHeader = opts.optJSONObject("headers")?.keys()?.asSequence()
            ?.any { it.equals("Authorization", ignoreCase = true) } == true
        if (opts.optBoolean("auth", true) && !hasAuthHeader) {
            bearerToken()?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        }
        val form = opts.optJSONObject("form")
        val headerContentType = opts.optJSONObject("headers")?.keys()?.asSequence()
            ?.firstOrNull { it.equals("Content-Type", ignoreCase = true) }
            ?.let { opts.optJSONObject("headers")!!.optString(it).trim() }
            ?.takeIf { it.isNotEmpty() }
        val body = when {
            form != null -> FormBody.Builder().apply {
                form.keys().forEach { k -> add(k, form.optString(k)) }
            }.build()
            opts.has("body") -> opts.optString("body").toRequestBody(
                opts.optString("contentType").ifBlank { headerContentType.orEmpty() }
                    .ifBlank { "text/plain; charset=utf-8" }
                    .toMediaTypeOrNull(),
            )
            method == "GET" || method == "HEAD" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        builder.method(method, body)
        val caller = if (opts.optBoolean("cookies", true)) client else clearanceClient
        val saveAs = opts.optString("saveAs").trim()
        val retryNote = if (alreadyRetried) " (retry after bot-check)" else ""
        DebugLog.plugins("$pluginId → $method $url$retryNote")
        val result = withRateLimit {
            try {
                withContext(Dispatchers.IO) {
                    caller.newCall(builder.build()).execute().use { response ->
                        val length = response.body?.contentLength() ?: -1L
                        val maxBytes = if (saveAs.isNotEmpty()) MAX_FILE_BYTES else MAX_BYTES
                        if (length > maxBytes) {
                            return@use error("NETWORK", "Response too large")
                        }
                        val bytes = response.body?.byteStream()?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > maxBytes) throw IOException("Response too large")
                                out.write(buf, 0, n)
                            }
                            out.toByteArray()
                        } ?: ByteArray(0)
                        val headers = JSONObject()
                        response.headers.names().forEach { name ->
                            headers.put(name.lowercase(), response.headers(name).joinToString(", "))
                        }
                        if (saveAs.isNotEmpty()) {
                            val dest = resolveSavePath(saveAs)
                                ?: return@use error("ERROR", "Invalid saveAs path")
                            dest.parentFile?.mkdirs()
                            dest.writeBytes(bytes)
                            return@use JSONObject()
                                .put("status", response.code)
                                .put("url", response.request.url.toString())
                                .put("headers", headers)
                                .put("path", saveAs.trimStart('/'))
                                .put("bytes", bytes.size)
                                .toString()
                        }
                        val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        JSONObject()
                            .put("status", response.code)
                            .put("url", response.request.url.toString())
                            .put("headers", headers)
                            .put("text", String(bytes, charset))
                            .toString()
                    }
                }
            } catch (t: IOException) {
                error("NETWORK", t.message ?: "Network error")
            }
        }
        logFetchResult(method, url, result, retried = alreadyRetried)
        if (!alreadyRetried && saveAs.isEmpty()) {
            val text = runCatching { JSONObject(result).optString("text") }.getOrNull().orEmpty()
            if (AnubisDetector.isChallenge(text)) {
                DebugLog.plugins(
                    "$pluginId bot-check page — opening clearance browser for $url",
                )
                val cleared = PluginClearanceGate.await(pluginId, url, allowedHostsList)
                DebugLog.plugins(
                    if (cleared) {
                        "$pluginId bot-check cleared — retrying fetch"
                    } else {
                        "$pluginId bot-check clearance failed — not retrying"
                    },
                )
                if (cleared) return fetchAttempt(url, opts, alreadyRetried = true)
            }
        }
        return result
    }

    private fun resolveSavePath(relative: String): File? {
        val root = filesDir ?: return null
        val cleaned = relative.trim().trimStart('/').replace('\\', '/')
        if (cleaned.isEmpty() || cleaned.contains("..")) return null
        val dest = File(root, cleaned).canonicalFile
        val rootCanon = root.canonicalFile
        if (!dest.path.startsWith(rootCanon.path + File.separator) && dest != rootCanon) return null
        return dest
    }

    private fun logFetchResult(method: String, requestedUrl: String, resultJson: String, retried: Boolean) {
        val parsed = runCatching { JSONObject(resultJson) }.getOrNull() ?: return
        val retry = if (retried) " after-retry" else ""
        val err = parsed.optJSONObject("error")
        if (err != null) {
            DebugLog.plugins(
                "$pluginId ← $method failed$retry " +
                    "${err.optString("code")}: ${err.optString("message")} ($requestedUrl)",
            )
            return
        }
        val finalUrl = parsed.optString("url").ifBlank { requestedUrl }
        val status = parsed.optInt("status", -1)
        val path = parsed.optString("path")
        if (path.isNotEmpty()) {
            DebugLog.plugins(
                "$pluginId ← HTTP $status$retry saved ${parsed.optInt("bytes")}b → $path ($finalUrl)",
            )
            return
        }
        val text = parsed.optString("text")
        val contentType = parsed.optJSONObject("headers")?.optString("content-type")
        val probe = PluginResponseProbe.summarize(text, contentType)
        val redirect = if (finalUrl != requestedUrl) " final=$finalUrl" else ""
        DebugLog.plugins("$pluginId ← HTTP $status$retry$redirect $probe")
    }

    private fun logError(code: String, message: String, method: String, url: String): String {
        DebugLog.plugins("$pluginId ← $method blocked: $code $message ($url)")
        return error(code, message)
    }

    private suspend fun <T> withRateLimit(block: suspend () -> T): T = rate.withLock {
        val wait = lastFetchAt + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastFetchAt = System.currentTimeMillis()
        }
    }

    private fun error(code: String, message: String): String =
        JSONObject().put("error", JSONObject().put("code", code).put("message", message)).toString()

    companion object {
        /** Chapter HTML and JSON responses. git log -S '8L * 1024' — original text cap. */
        private const val MAX_BYTES = 8L * 1024 * 1024
        /**
         * EPUB downloads via `saveAs`. Larger than [MAX_BYTES] because illustrated books exceed
         * chapter HTML size. Tuned 2026-11-13 with the Audiobookshelf plugin.
         */
        private const val MAX_FILE_BYTES = 80L * 1024 * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }
}
