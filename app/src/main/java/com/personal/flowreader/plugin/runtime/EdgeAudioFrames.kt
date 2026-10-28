package com.personal.flowreader.plugin.runtime

/**
 * Microsoft Edge Read Aloud binary WebSocket frames: 2-byte big-endian header length,
 * then that many header bytes, then MP3 payload. Pure so JVM tests can cover it.
 */
object EdgeAudioFrames {
    /** Concatenate MP3 payloads from Edge binary frames; skip frames that are header-only. */
    fun assembleMp3(frames: List<ByteArray>): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        var total = 0
        val payloads = ArrayList<ByteArray>(frames.size)
        for (frame in frames) {
            val payload = payloadOf(frame) ?: continue
            payloads += payload
            total += payload.size
        }
        if (total == 0) return ByteArray(0)
        val out = ByteArray(total)
        var o = 0
        for (p in payloads) {
            System.arraycopy(p, 0, out, o, p.size)
            o += p.size
        }
        return out
    }

    /** MP3 bytes after the Edge header, or null when the frame has no audio. */
    fun payloadOf(frame: ByteArray): ByteArray? {
        if (frame.size < 2) return null
        val headerLen = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
        val start = 2 + headerLen
        if (start >= frame.size) return null
        return frame.copyOfRange(start, frame.size)
    }
}
