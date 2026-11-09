package com.personal.flowreader.plugin.runtime

import java.io.File
import java.util.UUID

/**
 * App-owned synth identity: each clip attempt gets a [requestId] that names the host temp
 * file and must be echoed before bytes are accepted into the clip cache.
 *
 * Shape is 32 lowercase hex so Edge TTS can use the same value as ConnectionId / X-RequestId.
 */
object PluginClipOwnership {
    private val REQUEST_ID = Regex("^[a-f0-9]{32}$")

    fun newRequestId(): String =
        UUID.randomUUID().toString().replace("-", "").lowercase()

    fun isValidRequestId(id: String): Boolean = REQUEST_ID.matches(id)

    fun expectedAudioFile(audioDir: File, requestId: String): File {
        require(isValidRequestId(requestId)) { "invalid requestId" }
        return File(audioDir, "$requestId.mp3")
    }

    /**
     * Accept only a host-written edge-audio file named for [requestId] under [audioDir].
     * Rejects foreign paths, time-named temps, and empty files.
     */
    fun acceptAudioPath(path: String, requestId: String, audioDir: File): File {
        require(isValidRequestId(requestId)) { "invalid requestId" }
        val expected = expectedAudioFile(audioDir, requestId).canonicalFile
        val actual = File(path).canonicalFile
        require(actual == expected) {
            "audioPath does not match requestId (got ${actual.name}, want ${expected.name})"
        }
        require(actual.isFile && actual.length() > 0L) { "empty audio" }
        return actual
    }
}
