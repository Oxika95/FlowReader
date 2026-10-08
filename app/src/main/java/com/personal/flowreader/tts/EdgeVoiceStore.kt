package com.personal.flowreader.tts

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Edge voice list: last download in [cacheFile], else the bundled snapshot. Blocking; call off
 * the main thread.
 */
class EdgeVoiceStore(
    private val cacheFile: File,
    private val http: OkHttpClient,
) {
    fun load(): List<EdgeVoice> {
        val cached = runCatching { if (cacheFile.isFile) EdgeVoiceCatalog.parse(cacheFile.readText()) else null }
            .getOrNull()
        return cached?.takeIf { it.size >= MIN_VOICES } ?: EdgeVoiceCatalog.bundled()
    }

    /** Downloads a fresh list when the cache is older than [MAX_AGE_MS]; null if not refreshed. */
    fun refreshIfStale(nowMs: Long = System.currentTimeMillis()): List<EdgeVoice>? {
        if (cacheFile.isFile && nowMs - cacheFile.lastModified() < MAX_AGE_MS) return null
        val request = Request.Builder()
            .url(EdgeHandshake.voicesUrl(nowMs / 1000))
            .header("User-Agent", EdgeHandshake.USER_AGENT)
            .build()
        val body = runCatching {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
        }.getOrNull() ?: return null
        val voices = EdgeVoiceCatalog.parse(body)
        if (voices.size < MIN_VOICES) return null
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }
        return voices
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        /** Fewer voices than this means a broken or partial response. */
        const val MIN_VOICES = 50
    }
}
