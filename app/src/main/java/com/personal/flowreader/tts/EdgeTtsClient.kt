package com.personal.flowreader.tts

import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EdgeAudio(val mp3: ByteArray)

class EdgeTtsClient(
    private val http: OkHttpClient = defaultHttp(),
) {
    suspend fun synthesize(
        text: String,
        voice: String = "en-US-JennyNeural",
        lang: String = "en-US",
        ratePercent: Int = 0,
        pitchPercent: Int = 0,
    ): EdgeAudio {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Nothing to synthesize.")
        }
        if (trimmed.length > MAX_UTTERANCE_CHARS) {
            throw IllegalArgumentException(
                "Utterance too long (${trimmed.length} chars; max $MAX_UTTERANCE_CHARS).",
            )
        }
        return withTimeout(SYNTH_TIMEOUT_MS) {
            synthesizeOnce(trimmed, voice, lang, ratePercent, pitchPercent)
        }
    }

    private suspend fun synthesizeOnce(
        text: String,
        voice: String,
        lang: String,
        ratePercent: Int,
        pitchPercent: Int,
    ): EdgeAudio = suspendCancellableCoroutine { cont ->
        val id = UUID.randomUUID().toString().replace("-", "")
        val url = EdgeHandshake.url(id, System.currentTimeMillis() / 1000)
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0",
            )
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        // Primitive byte buffer — ArrayList<Byte> boxes every sample (~16x heap).
        val audio = ByteArrayOutputStream(16 * 1024)
        val ws = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val date = java.util.Date().toString()
                    val config = buildString {
                        append("Content-Type: application/json; charset=utf-8\r\n")
                        append("Path: speech.config\r\n")
                        append("X-Timestamp: $date\r\n\r\n")
                        append(
                            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":false,"wordBoundaryEnabled":false},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""",
                        )
                    }
                    val escaped = text
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                    val rate = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
                    val pitch = if (pitchPercent >= 0) "+$pitchPercent%" else "$pitchPercent%"
                    val ssml =
                        """<speak version="1.0" xml:lang="$lang"><voice name="$voice"><prosody rate="$rate" pitch="$pitch">$escaped</prosody></voice></speak>"""
                    val content = buildString {
                        append("Content-Type: application/ssml+xml\r\n")
                        append("Path: ssml\r\n")
                        append("X-RequestId: $id\r\n")
                        append("X-Timestamp: $date\r\n\r\n")
                        append(ssml)
                    }
                    webSocket.send(config)
                    webSocket.send(content)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end") || text.contains("Path: turn.end")) {
                        webSocket.close(1000, null)
                        if (audio.size() == 0) {
                            if (cont.isActive) {
                                cont.resumeWithException(IllegalStateException("No audio data received."))
                            }
                        } else if (cont.isActive) {
                            cont.resume(EdgeAudio(audio.toByteArray()))
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val arr = bytes.toByteArray()
                    if (arr.size < 2) return
                    val headerLen = ((arr[0].toInt() and 0xFF) shl 8) or (arr[1].toInt() and 0xFF)
                    val start = 2 + headerLen
                    if (arr.size > start) {
                        if (audio.size() + (arr.size - start) > MAX_AUDIO_BYTES) {
                            webSocket.cancel()
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IllegalStateException("Edge audio exceeded $MAX_AUDIO_BYTES bytes."),
                                )
                            }
                            return
                        }
                        audio.write(arr, start, arr.size - start)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            },
        )
        cont.invokeOnCancellation { ws.cancel() }
    }

    companion object {
        const val MAX_UTTERANCE_CHARS = 4_000
        const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        private const val SYNTH_TIMEOUT_MS = 45_000L

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
