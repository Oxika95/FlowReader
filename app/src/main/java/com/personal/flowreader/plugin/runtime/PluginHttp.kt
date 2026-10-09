package com.personal.flowreader.plugin.runtime

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
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
 */
class PluginHttp(
    allowedHosts: List<String>,
    private val minIntervalMs: Long,
    val cookieJar: PluginCookieJar,
) {
    private val allowed = allowedHosts.map { it.trim().lowercase().removePrefix("www.") }.filter { it.isNotEmpty() }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()
    /** `flow.fetch(url, { cookies: false })`: no session sent or stored, same request gap. */
    private val anonymousClient: OkHttpClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
    private val rate = Mutex()
    private var lastFetchAt = 0L

    fun isAllowed(host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return allowed.any { h == it || h.endsWith(".$it") }
    }

    suspend fun fetch(requestJson: String): String {
        val req = runCatching { JSONObject(requestJson) }.getOrNull()
            ?: return error("ERROR", "Bad fetch arguments")
        val url = req.optString("url").trim()
        val parsed = url.toHttpUrlOrNull() ?: return error("ERROR", "Invalid URL: $url")
        if (!isAllowed(parsed.host)) {
            return error("BLOCKED", "Host not allowed by plugin manifest: ${parsed.host}")
        }
        val opts = req.optJSONObject("opts") ?: JSONObject()
        val method = opts.optString("method").ifBlank { "GET" }.uppercase()
        val builder = Request.Builder().url(parsed)
            .header("User-Agent", USER_AGENT)
            .header("Accept", DEFAULT_ACCEPT)
            .header("Accept-Language", "en-US,en;q=0.9")
        opts.optJSONObject("headers")?.let { headers ->
            headers.keys().forEach { k -> builder.header(k, headers.optString(k)) }
        }
        val form = opts.optJSONObject("form")
        val body = when {
            form != null -> FormBody.Builder().apply {
                form.keys().forEach { k -> add(k, form.optString(k)) }
            }.build()
            opts.has("body") -> opts.optString("body").toRequestBody(
                opts.optString("contentType").ifBlank { "text/plain; charset=utf-8" }.toMediaTypeOrNull(),
            )
            method == "GET" || method == "HEAD" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        builder.method(method, body)
        val caller = if (opts.optBoolean("cookies", true)) client else anonymousClient
        return withRateLimit {
            try {
                withContext(Dispatchers.IO) {
                    caller.newCall(builder.build()).execute().use { response ->
                        val length = response.body?.contentLength() ?: -1L
                        if (length > MAX_BYTES) {
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
                                if (total > MAX_BYTES) throw IOException("Response too large")
                                out.write(buf, 0, n)
                            }
                            out.toByteArray()
                        } ?: ByteArray(0)
                        val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        val headers = JSONObject()
                        response.headers.names().forEach { name ->
                            headers.put(name.lowercase(), response.headers(name).joinToString(", "))
                        }
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
        private const val MAX_BYTES = 8L * 1024 * 1024
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    }
}
