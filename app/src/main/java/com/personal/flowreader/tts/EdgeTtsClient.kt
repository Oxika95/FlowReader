package com.personal.flowreader.tts

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Edge `audio.metadata` WordBoundary (offsets in 100-ns ticks). */
data class EdgeWordBoundary(
    val offset: Long,
    val duration: Long,
    val text: String,
)

data class EdgeAudio(
    val mp3: ByteArray,
    val boundaries: List<EdgeWordBoundary> = emptyList(),
)

/** Edge refused this text (empty, too long, or no audio for it); retrying won't help. */
class EdgeContentException(message: String) : IllegalArgumentException(message)

/** Every race attempt failed or hung on the network; retry once a network is usable. */
class EdgeNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One race attempt: sent [startMs] after the race begins, over [http] (bound to [lane]). */
class RaceLane(val startMs: Long, val lane: NetLane, val http: OkHttpClient)

class EdgeTtsClient(
    val http: OkHttpClient = defaultHttp(),
) {
    /** Staggered attempts on the default network. */
    fun defaultLanes(): List<RaceLane> =
        RaceSchedule.staggered(RACE_ATTEMPTS, HEDGE_DELAY_MS).map { RaceLane(it, NetLane.DEFAULT, http) }

    /**
     * Synthesize [text] with a staggered race over [lanes] (see [RaceSchedule]). The first
     * successful [EdgeAudio] wins; siblings are cancelled. A hard [RACE_TIMEOUT_MS] wall aborts
     * the whole race. [onReport] receives the outcome (also on failure). [tag] labels this race's
     * log lines (concurrent races interleave).
     */
    suspend fun synthesize(
        text: String,
        voice: String = "en-US-JennyNeural",
        lang: String = "en-US",
        ratePercent: Int = 0,
        pitchPercent: Int = 0,
        lanes: List<RaceLane> = defaultLanes(),
        onReport: (RaceReport) -> Unit = {},
        tag: String = "",
    ): EdgeAudio {
        val trimmed = SpeechText.xmlSafe(text).trim()
        if (trimmed.isEmpty()) {
            throw EdgeContentException("Nothing to synthesize.")
        }
        if (trimmed.length > MAX_UTTERANCE_CHARS) {
            throw EdgeContentException(
                "Utterance too long (${trimmed.length} chars; max $MAX_UTTERANCE_CHARS).",
            )
        }
        require(lanes.isNotEmpty()) { "No race lanes." }
        val race = RaceState(lanes, tag)
        SynthDebugLog.append(
            "${race.label()} start n=${lanes.size} chars=${trimmed.length} voice=$voice " +
                "plan=${lanes.joinToString(",") { "${it.lane.label()}@${it.startMs}" }}",
        )
        return try {
            withTimeout(RACE_TIMEOUT_MS) {
                raceSynthesize(race, trimmed, voice, lang, ratePercent, pitchPercent, onReport)
            }
        } catch (e: TimeoutCancellationException) {
            SynthDebugLog.append("${race.label()} timeout ms=$RACE_TIMEOUT_MS started=${race.started}")
            onReport(race.report(winner = -1))
            throw EdgeNetworkException("Edge did not answer within $RACE_TIMEOUT_MS ms.", e)
        }
    }

    private sealed interface RaceEvent {
        class FirstAudio(val idx: Int, val atMs: Long) : RaceEvent
        class Done(val idx: Int, val result: Result<EdgeAudio>) : RaceEvent
    }

    /** Mutated only by the race loop coroutine. */
    private class RaceState(val lanes: List<RaceLane>, private val tag: String) {
        private val t0 = System.nanoTime()

        /** `edge race[tag]` for the race, `edge race[tag #i]` for attempt i. */
        fun label(idx: Int? = null): String {
            val parts = listOfNotNull(tag.ifEmpty { null }, idx?.let { "#$it" })
            return if (parts.isEmpty()) "edge race" else "edge race[${parts.joinToString(" ")}]"
        }
        val startMs = lanes.map { it.startMs }
        var started = 0
        var inFlight = 0
        val audioAt = LongArray(lanes.size) { -1L }
        val finished = BooleanArray(lanes.size)
        val failed = ArrayList<NetLane>()
        val errors = ArrayList<Throwable>()

        fun elapsedMs(): Long = (System.nanoTime() - t0) / 1_000_000L

        fun audioInFlight(): Boolean = (0 until started).any { !finished[it] && audioAt[it] >= 0L }

        fun report(winner: Int, contentError: Boolean = false): RaceReport {
            val first = audioAt.getOrNull(winner)?.takeIf { it >= 0L }
            val start = lanes.getOrNull(winner)?.startMs
            return RaceReport(
                winner = lanes.getOrNull(winner)?.lane,
                firstAudioMs = first,
                winnerStartMs = start,
                winnerLatencyMs = if (first != null && start != null) (first - start).coerceAtLeast(0L) else null,
                totalMs = elapsedMs(),
                started = started,
                failed = failed.toList(),
                contentError = contentError,
            )
        }
    }

    private suspend fun raceSynthesize(
        race: RaceState,
        text: String,
        voice: String,
        lang: String,
        ratePercent: Int,
        pitchPercent: Int,
        onReport: (RaceReport) -> Unit,
    ): EdgeAudio = coroutineScope {
        val events = Channel<RaceEvent>(Channel.UNLIMITED)
        val jobs = ArrayList<Job>(race.lanes.size)

        fun startDue() {
            val due = RaceSchedule.due(
                startMs = race.startMs,
                elapsedMs = race.elapsedMs(),
                started = race.started,
                inFlight = race.inFlight,
                audioInFlight = race.audioInFlight(),
            )
            for (idx in due) {
                val lane = race.lanes[idx]
                race.started++
                race.inFlight++
                SynthDebugLog.append("${race.label(idx)} start lane=${lane.lane.label()} t=${race.elapsedMs()}")
                jobs += launch {
                    val result = try {
                        Result.success(
                            synthesizeOnce(lane.http, text, voice, lang, ratePercent, pitchPercent) {
                                events.trySend(RaceEvent.FirstAudio(idx, race.elapsedMs()))
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Result.failure(t)
                    }
                    events.trySend(RaceEvent.Done(idx, result))
                }
            }
        }

        fun cancelAll() = jobs.forEach { it.cancel() }

        startDue()
        while (race.inFlight > 0 || race.started < race.lanes.size) {
            val next = RaceSchedule.nextStartMs(race.startMs, race.started)
            val elapsed = race.elapsedMs()
            val event = if (next != null && next > elapsed) {
                withTimeoutOrNull(next - elapsed) { events.receive() }
            } else {
                events.receive()
            }
            when (event) {
                is RaceEvent.FirstAudio -> {
                    race.audioAt[event.idx] = event.atMs
                    SynthDebugLog.append("${race.label(event.idx)} first-audio ms=${event.atMs}")
                }
                is RaceEvent.Done -> {
                    race.inFlight--
                    race.finished[event.idx] = true
                    val lane = race.lanes[event.idx].lane
                    val audio = event.result.getOrNull()
                    if (audio != null) {
                        SynthDebugLog.append(
                            "${race.label(event.idx)} win lane=${lane.label()} bytes=${audio.mp3.size} " +
                                "firstAudioMs=${race.audioAt[event.idx]} totalMs=${race.elapsedMs()} " +
                                "started=${race.started}",
                        )
                        cancelAll()
                        onReport(race.report(winner = event.idx))
                        return@coroutineScope audio
                    }
                    val t = event.result.exceptionOrNull() ?: IllegalStateException("Edge attempt failed.")
                    race.failed += lane
                    race.errors += t
                    SynthDebugLog.append(
                        "${race.label(event.idx)} fail lane=${lane.label()} t=${race.elapsedMs()}: " +
                            (t.message ?: t.javaClass.simpleName),
                    )
                    if (t is EdgeContentException) {
                        cancelAll()
                        onReport(race.report(winner = -1, contentError = true))
                        throw t
                    }
                }
                null -> Unit
            }
            startDue()
        }
        SynthDebugLog.append("${race.label()} all failed n=${race.errors.size} totalMs=${race.elapsedMs()}")
        onReport(race.report(winner = -1))
        race.errors.firstOrNull { it !is IOException }?.let { throw it }
        val last = race.errors.lastOrNull()
        throw EdgeNetworkException(last?.message ?: "All Edge race attempts failed.", last)
    }

    private suspend fun synthesizeOnce(
        http: OkHttpClient,
        text: String,
        voice: String,
        lang: String,
        ratePercent: Int,
        pitchPercent: Int,
        onFirstAudio: () -> Unit,
    ): EdgeAudio = suspendCancellableCoroutine { cont ->
        val id = UUID.randomUUID().toString().replace("-", "")
        val url = EdgeHandshake.url(id, System.currentTimeMillis() / 1000)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", EdgeHandshake.USER_AGENT)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .build()

        val audio = ByteArrayOutputStream(16 * 1024)
        val boundaries = ArrayList<EdgeWordBoundary>()
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
                            """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":false,"wordBoundaryEnabled":true},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""",
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
                    val path = headerPath(text)
                    when {
                        path.equals("audio.metadata", ignoreCase = true) -> {
                            boundaries += parseAudioMetadataBody(messageBody(text))
                        }
                        path.equals("turn.end", ignoreCase = true) ||
                            text.contains("Path:turn.end") ||
                            text.contains("Path: turn.end") -> {
                            webSocket.close(1000, null)
                            if (audio.size() == 0) {
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        EdgeContentException("No audio data received."),
                                    )
                                }
                            } else if (cont.isActive) {
                                cont.resume(EdgeAudio(audio.toByteArray(), boundaries.toList()))
                            }
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
                        if (audio.size() == 0) onFirstAudio()
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
        /** WebSockets per utterance at most; first success wins. */
        const val RACE_ATTEMPTS = 3
        /**
         * Backup attempt n starts n × this after the race begins, only if no audio has arrived.
         * Used without lane history; [NetworkLanePolicy] adapts it to measured latency.
         */
        const val HEDGE_DELAY_MS = 2_000L
        /** Wall clock for the whole race (all attempts hang → fail). */
        const val RACE_TIMEOUT_MS = 6_000L

        private fun NetLane.label(): String = name.lowercase()

        fun parseAudioMetadataBody(body: String): List<EdgeWordBoundary> {
            if (body.isBlank()) return emptyList()
            return try {
                val root = JSONObject(body)
                val meta = root.optJSONArray("Metadata") ?: return emptyList()
                buildList {
                    for (i in 0 until meta.length()) {
                        val entry = meta.optJSONObject(i) ?: continue
                        if (entry.optString("Type") != "WordBoundary") continue
                        val data = entry.optJSONObject("Data") ?: continue
                        val offset = data.optLong("Offset", -1L)
                        if (offset < 0L) continue
                        val textObj = data.optJSONObject("text")
                        val word = textObj?.optString("Text").orEmpty()
                        if (word.isEmpty()) continue
                        add(
                            EdgeWordBoundary(
                                offset = offset,
                                duration = data.optLong("Duration", 0L),
                                text = word,
                            ),
                        )
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        private fun headerPath(message: String): String {
            for (line in message.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) break
                val idx = trimmed.indexOf(':')
                if (idx <= 0) continue
                val key = trimmed.substring(0, idx).trim()
                if (key.equals("Path", ignoreCase = true)) {
                    return trimmed.substring(idx + 1).trim()
                }
            }
            return ""
        }

        private fun messageBody(message: String): String {
            val sep = message.indexOf("\r\n\r\n")
            if (sep >= 0) return message.substring(sep + 4)
            val sep2 = message.indexOf("\n\n")
            if (sep2 >= 0) return message.substring(sep2 + 2)
            return ""
        }

        private fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
