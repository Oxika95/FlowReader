package com.personal.flowreader.tts

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Edge read-aloud websocket handshake. Protocol is public (Sec-MS-GEC);
 * this is an original Kotlin implementation, not a copy of Readest.
 * Host is the one Edge 155+ uses; `speech.platform.bing.com` 403s every handshake since 2026-10-12.
 */
object EdgeHandshake {
    const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val WS = "wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
    const val VOICES = "https://api.msedgeservices.com/tts/cognitiveservices/voices/list"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
    const val CHROMIUM = "143.0.3650.75"
    private const val WIN_EPOCH = 11_644_473_600L

    /** Server clock minus device clock, from the `Date` of a rejected handshake. */
    @Volatile
    var skewSeconds: Long = 0L

    private val random = SecureRandom()

    /** Device time corrected by [skewSeconds]; the token is only valid in the server's window. */
    fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000 + skewSeconds

    fun secMsGec(nowEpochSeconds: Long): String {
        var ticks = nowEpochSeconds + WIN_EPOCH
        ticks -= ticks % 300
        val fileTime = ticks * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${fileTime}$TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun auth(nowEpochSeconds: Long): String =
        "Ocp-Apim-Subscription-Key=$TOKEN" +
            "&Sec-MS-GEC=${secMsGec(nowEpochSeconds)}" +
            "&Sec-MS-GEC-Version=1-$CHROMIUM"

    fun url(connectionId: String, nowEpochSeconds: Long): String =
        "$WS?${auth(nowEpochSeconds)}&ConnectionId=$connectionId"

    fun voicesUrl(nowEpochSeconds: Long): String = "$VOICES?${auth(nowEpochSeconds)}"

    /** Browser-like request headers; [muid] is a fresh [newMuid] per connection. */
    fun headers(muid: String, websocket: Boolean = true): Map<String, String> = buildMap {
        put("User-Agent", USER_AGENT)
        if (websocket) {
            put("Origin", ORIGIN)
            put("Pragma", "no-cache")
            put("Cache-Control", "no-cache")
        }
        put("Accept-Language", "en-US,en;q=0.9")
        put("Cookie", "muid=$muid;")
    }

    fun newMuid(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /** Seconds to add to [nowEpochSeconds] to match an HTTP `Date` header; null if unparseable. */
    fun skewFromDate(httpDate: String?, nowEpochSeconds: Long): Long? {
        if (httpDate.isNullOrBlank()) return null
        val server = runCatching {
            ZonedDateTime.parse(httpDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
        }.getOrNull() ?: return null
        return server - nowEpochSeconds
    }
}
