package com.personal.flowreader.plugin.runtime

import android.net.Network
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-shot WebSocket collect for TTS plugins over a raw TLS socket (RFC 6455), matching
 * Readest's Workers path: OkHttp's upgrade is answered with HTTP 200 by
 * api.msedgeservices.com; a manual handshake gets 101.
 *
 * Optional [audioDir]: when the plugin asks for `binaryFormat: "edge-audio"`, binary frames are
 * stripped of Edge headers and written as one MP3 under that dir; the JSON returns `audioPath`
 * instead of shipping `binariesBase64` into QuickJS.
 */
class PluginWs(
    private val allowedHosts: List<String>,
    private val audioDir: File? = null,
) {
    /** Bound Android [Network] for the next collect (race lanes); cleared after each call. */
    private val boundNetwork = AtomicReference<Network?>(null)

    fun bindNetwork(network: Network?) {
        boundNetwork.set(network)
    }

    suspend fun collect(requestJson: String): String {
        val req = JSONObject(requestJson)
        val url = req.optString("url").trim()
        require(url.startsWith("wss://") || url.startsWith("ws://")) { "flow.ws needs a ws(s) url" }
        requireHostAllowed(url)
        val headers = req.optJSONObject("headers") ?: JSONObject()
        val messages = req.optJSONArray("messages") ?: JSONArray()
        val endPath = req.optString("endPath").ifBlank { "turn.end" }
        val timeoutMs = req.optLong("timeoutMs", 15_000L).coerceIn(1_000L, 60_000L)
        val edgeAudio = req.optString("binaryFormat") == BINARY_FORMAT_EDGE_AUDIO
        val network = boundNetwork.getAndSet(null)

        return withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) {
                collectBlocking(url, headers, messages, endPath, edgeAudio, network)
            }
        }
    }

    private fun collectBlocking(
        url: String,
        headers: JSONObject,
        messages: JSONArray,
        endPath: String,
        edgeAudio: Boolean,
        network: Network?,
    ): String {
        val uri = URI(url)
        val host = uri.host ?: error("missing host")
        val secure = url.startsWith("wss://")
        val port = when {
            uri.port > 0 -> uri.port
            secure -> 443
            else -> 80
        }
        val path = buildString {
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            if (!uri.rawQuery.isNullOrEmpty()) append('?').append(uri.rawQuery)
        }

        val socket = openSocket(host, port, secure, network)
        try {
            socket.soTimeout = 30_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            handshake(output, input, host, path, headers)

            for (i in 0 until messages.length()) {
                writeFrame(output, OP_TEXT, messages.optString(i).toByteArray(StandardCharsets.UTF_8))
            }

            val texts = ArrayList<String>()
            val binaries = ArrayList<ByteArray>()
            val reader = FrameReader(input)
            while (true) {
                val frame = reader.next() ?: break
                when (frame.opcode) {
                    OP_TEXT -> {
                        val text = String(frame.payload, StandardCharsets.UTF_8)
                        texts += text
                        if (headerPath(text).equals(endPath, ignoreCase = true)) {
                            writeFrame(output, OP_CLOSE, ByteArray(0))
                            break
                        }
                    }
                    OP_BINARY -> binaries += frame.payload
                    OP_CLOSE -> break
                    OP_PING -> writeFrame(output, OP_PONG, frame.payload)
                    OP_PONG -> Unit
                }
            }
            return envelope(texts, binaries, edgeAudio)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun openSocket(host: String, port: Int, secure: Boolean, network: Network?): Socket {
        val plain = Socket()
        if (network != null) {
            runCatching { network.bindSocket(plain) }
        }
        plain.connect(InetSocketAddress(host, port), 10_000)
        if (!secure) return plain
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(plain, host, port, true) as javax.net.ssl.SSLSocket
        ssl.startHandshake()
        return ssl
    }

    private fun handshake(
        output: OutputStream,
        input: InputStream,
        host: String,
        path: String,
        headers: JSONObject,
    ) {
        val key = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        val lines = ArrayList<String>()
        lines += "GET $path HTTP/1.1"
        lines += "Host: $host"
        lines += "Upgrade: websocket"
        lines += "Connection: Upgrade"
        lines += "Sec-WebSocket-Key: $key"
        lines += "Sec-WebSocket-Version: 13"
        for (name in headers.keys()) {
            val lower = name.lowercase()
            if (lower in RESERVED_HEADERS) continue
            lines += "$name: ${headers.optString(name)}"
        }
        output.write((lines.joinToString("\r\n") + "\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
        output.flush()

        val headerBytes = readUntilHeaders(input)
        val headerText = String(headerBytes, StandardCharsets.US_ASCII)
        val statusLine = headerText.lineSequence().firstOrNull().orEmpty()
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
        if (status != 101) {
            throw IllegalStateException("WebSocket failed status=$status: $statusLine")
        }
    }

    private fun requireHostAllowed(url: String) {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull().orEmpty()
        val ok = allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
        require(ok) { "Host not allowed for this plugin: $host" }
    }

    private fun envelope(
        texts: List<String>,
        binaries: List<ByteArray>,
        edgeAudio: Boolean,
    ): String {
        val o = JSONObject()
        o.put("texts", JSONArray(texts))
        if (edgeAudio) {
            val mp3 = EdgeAudioFrames.assembleMp3(binaries)
            val dir = audioDir?.apply { mkdirs() }
            if (dir != null && mp3.isNotEmpty()) {
                val file = File(dir, "clip-${System.nanoTime()}.mp3")
                file.writeBytes(mp3)
                o.put("audioPath", file.absolutePath)
            } else {
                o.put("audioPath", "")
            }
            o.put("binariesBase64", JSONArray())
        } else {
            val bins = JSONArray()
            for (b in binaries) {
                bins.put(Base64.encodeToString(b, Base64.NO_WRAP))
            }
            o.put("binariesBase64", bins)
        }
        return o.toString()
    }

    private class Frame(val opcode: Int, val payload: ByteArray)

    private class FrameReader(private val input: InputStream) {
        private var buffer = ByteArray(0)
        private var fragments: ByteArrayOutputStream? = null
        private var fragmentOpcode = 0

        fun next(): Frame? {
            while (true) {
                val frame = readOne() ?: return null
                when (frame.opcode) {
                    OP_CONTINUATION -> {
                        (fragments ?: ByteArrayOutputStream().also { fragments = it })
                            .write(frame.payload)
                        if (frame.fin) {
                            val payload = fragments!!.toByteArray()
                            fragments = null
                            return Frame(fragmentOpcode, payload)
                        }
                    }
                    OP_TEXT, OP_BINARY -> {
                        if (!frame.fin) {
                            fragmentOpcode = frame.opcode
                            fragments = ByteArrayOutputStream().also { it.write(frame.payload) }
                            continue
                        }
                        return Frame(frame.opcode, frame.payload)
                    }
                    else -> return Frame(frame.opcode, frame.payload)
                }
            }
        }

        private data class Raw(val fin: Boolean, val opcode: Int, val payload: ByteArray)

        private fun readOne(): Raw? {
            while (buffer.size < 2) {
                if (!pull()) return null
            }
            val b0 = buffer[0].toInt() and 0xff
            val b1 = buffer[1].toInt() and 0xff
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0f
            var length = b1 and 0x7f
            var offset = 2
            when (length) {
                126 -> {
                    while (buffer.size < 4) if (!pull()) return null
                    length = ((buffer[2].toInt() and 0xff) shl 8) or (buffer[3].toInt() and 0xff)
                    offset = 4
                }
                127 -> {
                    while (buffer.size < 10) if (!pull()) return null
                    length = ByteBuffer.wrap(buffer, 2, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
                    offset = 10
                }
            }
            val masked = b1 and 0x80 != 0
            val maskLen = if (masked) 4 else 0
            while (buffer.size < offset + maskLen + length) {
                if (!pull()) return null
            }
            var payloadStart = offset
            val mask = if (masked) {
                buffer.copyOfRange(offset, offset + 4).also { payloadStart += 4 }
            } else {
                null
            }
            val raw = buffer.copyOfRange(payloadStart, payloadStart + length)
            buffer = buffer.copyOfRange(payloadStart + length, buffer.size)
            if (mask != null) {
                for (i in raw.indices) raw[i] = (raw[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            return Raw(fin, opcode, raw)
        }

        private fun pull(): Boolean {
            val chunk = ByteArray(8192)
            val n = input.read(chunk)
            if (n < 0) return false
            val merged = ByteArray(buffer.size + n)
            System.arraycopy(buffer, 0, merged, 0, buffer.size)
            System.arraycopy(chunk, 0, merged, buffer.size, n)
            buffer = merged
            return true
        }
    }

    companion object {
        private const val OP_CONTINUATION = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xa

        private val RESERVED_HEADERS = setOf(
            "host", "upgrade", "connection", "sec-websocket-key", "sec-websocket-version",
        )

        private fun writeFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
            val length = payload.size
            val extended = when {
                length < 126 -> 0
                length < 65536 -> 2
                else -> 8
            }
            val frame = ByteArray(2 + extended + 4 + length)
            frame[0] = (0x80 or opcode).toByte()
            when (extended) {
                0 -> frame[1] = (0x80 or length).toByte()
                2 -> {
                    frame[1] = (0x80 or 126).toByte()
                    frame[2] = ((length ushr 8) and 0xff).toByte()
                    frame[3] = (length and 0xff).toByte()
                }
                else -> {
                    frame[1] = (0x80 or 127).toByte()
                    val view = ByteBuffer.wrap(frame, 2, 8).order(ByteOrder.BIG_ENDIAN)
                    view.putLong(length.toLong())
                }
            }
            val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
            System.arraycopy(mask, 0, frame, 2 + extended, 4)
            val payloadAt = 2 + extended + 4
            for (i in payload.indices) {
                frame[payloadAt + i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            output.write(frame)
            output.flush()
        }

        private fun readUntilHeaders(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val tmp = ByteArray(1)
            var last4 = 0
            while (true) {
                val n = input.read(tmp)
                if (n < 0) error("WebSocket upgrade failed: connection closed")
                out.write(tmp[0].toInt() and 0xff)
                last4 = ((last4 shl 8) or (tmp[0].toInt() and 0xff)) and 0xffffffff.toInt()
                if (last4 == 0x0d0a0d0a) return out.toByteArray()
                if (out.size() > 64_000) error("WebSocket upgrade headers too large")
            }
        }

        private const val BINARY_FORMAT_EDGE_AUDIO = "edge-audio"

        private fun headerPath(message: String): String {
            for (line in message.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) break
                val idx = trimmed.indexOf(':')
                if (idx <= 0) continue
                if (trimmed.substring(0, idx).trim().equals("Path", ignoreCase = true)) {
                    return trimmed.substring(idx + 1).trim()
                }
            }
            return ""
        }
    }
}
