package com.personal.flowreader.tts

import java.security.MessageDigest

/**
 * Edge read-aloud websocket handshake. Protocol is public (Sec-MS-GEC);
 * this is an original Kotlin implementation, not a copy of Readest.
 */
object EdgeHandshake {
    const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    const val WS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    const val VOICES = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    const val CHROMIUM = "143.0.3650.75"
    private const val WIN_EPOCH = 11_644_473_600L

    fun secMsGec(nowEpochSeconds: Long): String {
        var ticks = nowEpochSeconds + WIN_EPOCH
        ticks -= ticks % 300
        val fileTime = ticks * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${fileTime}$TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun url(connectionId: String, nowEpochSeconds: Long): String {
        val gec = secMsGec(nowEpochSeconds)
        return "$WS?ConnectionId=$connectionId" +
            "&TrustedClientToken=$TOKEN" +
            "&Sec-MS-GEC=$gec" +
            "&Sec-MS-GEC-Version=1-$CHROMIUM"
    }

    fun voicesUrl(nowEpochSeconds: Long): String =
        "$VOICES?trustedclienttoken=$TOKEN" +
            "&Sec-MS-GEC=${secMsGec(nowEpochSeconds)}" +
            "&Sec-MS-GEC-Version=1-$CHROMIUM"
}
