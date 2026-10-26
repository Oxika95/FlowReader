package com.personal.flowreader.tts

import java.io.IOException
import okhttp3.OkHttpClient

/** Word boundary cue (offsets in 100-ns ticks) from a clip-audio provider. */
data class EdgeWordBoundary(
    val offset: Long,
    val duration: Long,
    val text: String,
)

data class EdgeAudio(
    val mp3: ByteArray,
    val boundaries: List<EdgeWordBoundary> = emptyList(),
)

/** Provider refused this text (empty, too long, or no audio); retrying won't help. */
class EdgeContentException(message: String) : IllegalArgumentException(message)

/** Provider refused the handshake ([status] 200/401/403): bad token or protocol change. */
class EdgeServiceException(message: String, val status: Int) : IllegalStateException(message)

/** Network failure synthesizing a clip; retry once a network is usable. */
class EdgeNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One race attempt: sent [startMs] after the race begins, over [http] (bound to [lane]). */
class RaceLane(val startMs: Long, val lane: NetLane, val http: OkHttpClient)
