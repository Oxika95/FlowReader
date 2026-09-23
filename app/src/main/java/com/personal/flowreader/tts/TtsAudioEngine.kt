package com.personal.flowreader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Post-synthesis Edge playback: decode sentence MP3s to PCM, write a single continuous
 * [AudioTrack], equal-power crossfade (or silence gap / butt-join), and expose a heard clock
 * for word-cue sync. Does not synthesize or touch the network.
 */
class TtsAudioEngine {
    private val cancelled = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var trackSampleRate = 0
    private var trackChannels = 0
    private var framesWritten: Long = 0L
    private var pendingRemainder: PcmClip? = null
    private var pendingMediaSeedSec: Double = 0.0

    /** Decode-ahead cache keyed by absolute path. */
    private val decodeCache = object : LinkedHashMap<String, PcmClip>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PcmClip>?): Boolean =
            size > DECODE_CACHE_MAX
    }
    private val decodeCacheLock = Any()

    fun playbackHeadFrames(): Long {
        val t = track ?: return 0L
        return t.playbackHeadPosition.toLong() and 0xffff_ffffL
    }

    fun writtenFrames(): Long = framesWritten

    fun sampleRateHz(): Int = trackSampleRate

    fun peekPendingMediaSeedSec(): Double =
        if (pendingRemainder != null) pendingMediaSeedSec else 0.0

    fun cancel() {
        cancelled.set(true)
        pendingRemainder = null
        pendingMediaSeedSec = 0.0
    }

    fun release() {
        cancelled.set(true)
        pendingRemainder = null
        pendingMediaSeedSec = 0.0
        framesWritten = 0L
        synchronized(decodeCacheLock) { decodeCache.clear() }
        val t = track
        track = null
        trackSampleRate = 0
        trackChannels = 0
        if (t != null) {
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            }
            runCatching { t.flush() }
            runCatching { t.release() }
        }
        cancelled.set(false)
    }

    /** Warm the decode cache for [file] without playing. */
    suspend fun prefetchDecode(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext
        decodeCached(file)
    }

    /**
     * Play [file] as PCM. When [overlapMs] &gt; 0 and [nextFile] is ready, equal-power
     * crossfades a clamped overlap; otherwise writes the clip fully.
     *
     * [onHearableStart] — just before writing this clip (seed already into the clip).
     * [onOverlapNextStart] — when the incoming clip becomes hearable at the mix.
     */
    suspend fun play(
        file: File,
        generationActive: () -> Boolean,
        nextFile: File? = null,
        overlapMs: Int = 0,
        onHearableStart: ((mediaSeedSec: Double) -> Unit)? = null,
        onOverlapNextStart: (() -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        cancelled.set(false)
        if (!generationActive() || cancelled.get()) return@withContext

        val fromPending = pendingRemainder != null
        val seed = if (fromPending) pendingMediaSeedSec else 0.0
        val current = pendingRemainder?.also {
            pendingRemainder = null
            pendingMediaSeedSec = 0.0
        } ?: (decodeCached(file) ?: return@withContext)
        ensureTrack(current.sampleRate, current.channels)
        onHearableStart?.invoke(seed)
        playClipCrossfading(current, nextFile, overlapMs, generationActive, onOverlapNextStart)
    }

    private suspend fun playClipCrossfading(
        current: PcmClip,
        nextFile: File?,
        overlapMs: Int,
        generationActive: () -> Boolean,
        onOverlapNextStart: (() -> Unit)?,
    ) {
        val nextReady = nextFile != null && nextFile.exists() && nextFile.length() > 0L
        if (overlapMs <= 0 || !nextReady) {
            writeSamples(current.samples, generationActive)
            return
        }

        // Requested overlap samples (upper bound); clamp after next is decoded.
        val requestedFrames = (current.sampleRate * overlapMs / 1000).coerceAtLeast(1)
        val requestedSamples =
            (requestedFrames * current.channels).coerceAtMost(current.samples.size)
        if (requestedSamples <= 0) {
            writeSamples(current.samples, generationActive)
            return
        }
        val bodyLen = current.samples.size - requestedSamples

        val next = if (bodyLen > 0) {
            coroutineScope {
                val nextDeferred = async(Dispatchers.IO) { decodeCached(nextFile!!) }
                writeSamples(current.samples, generationActive, from = 0, length = bodyLen)
                nextDeferred.await()
            }
        } else {
            decodeCached(nextFile!!)
        }

        if (!generationActive() || cancelled.get()) return

        if (next == null ||
            next.sampleRate != current.sampleRate ||
            next.channels != current.channels
        ) {
            writeSamples(
                current.samples,
                generationActive,
                from = bodyLen,
                length = current.samples.size - bodyLen,
            )
            return
        }

        val effectiveMs = Crossfade.clampOverlapMs(
            requestedMs = overlapMs,
            currentSampleCount = current.samples.size,
            nextSampleCount = next.samples.size,
            sampleRate = current.sampleRate,
            channels = current.channels,
        )
        val overlapFrames = if (effectiveMs > 0) {
            (current.sampleRate * effectiveMs / 1000).coerceAtLeast(1)
        } else {
            0
        }
        val overlapSamplesDesired =
            (overlapFrames * current.channels).coerceAtMost(current.samples.size)

        // If clamp shortened the fade, write the extra current samples before the mix.
        val extraBeforeMix = requestedSamples - overlapSamplesDesired
        if (extraBeforeMix > 0) {
            writeSamples(
                current.samples,
                generationActive,
                from = bodyLen,
                length = extraBeforeMix,
            )
        }

        if (overlapSamplesDesired <= 0) {
            writeSamples(next.samples, generationActive)
            return
        }

        val mixFrom = current.samples.size - overlapSamplesDesired
        val overlapSamples = overlapSamplesDesired.coerceAtMost(next.samples.size)
        if (overlapSamples < overlapSamplesDesired) {
            val unmatched = overlapSamplesDesired - overlapSamples
            writeSamples(current.samples, generationActive, from = mixFrom, length = unmatched)
        }

        if (!generationActive() || cancelled.get()) return
        if (overlapSamples <= 0) return

        onOverlapNextStart?.invoke()
        val mixStart = mixFrom + (overlapSamplesDesired - overlapSamples)
        val ch = current.channels.coerceAtLeast(1)
        val frames = overlapSamples / ch
        val mixed = ShortArray(overlapSamples)
        for (frame in 0 until frames) {
            val progress = if (frames <= 1) 1f else frame.toFloat() / (frames - 1).toFloat()
            for (c in 0 until ch) {
                val idx = frame * ch + c
                mixed[idx] = Crossfade.mixSample(
                    current.samples[mixStart + idx],
                    next.samples[idx],
                    progress,
                )
            }
        }
        writeSamples(mixed, generationActive)

        if (overlapSamples < next.samples.size) {
            pendingRemainder = PcmClip(
                samples = next.samples.copyOfRange(overlapSamples, next.samples.size),
                sampleRate = next.sampleRate,
                channels = next.channels,
            )
            pendingMediaSeedSec = effectiveMs / 1000.0
        }
    }

    suspend fun writeSilence(
        gapMs: Int,
        generationActive: () -> Boolean,
    ) = withContext(Dispatchers.IO) {
        if (gapMs <= 0 || !generationActive() || cancelled.get()) return@withContext
        val t = track ?: return@withContext
        val rate = trackSampleRate
        val ch = trackChannels
        if (rate <= 0 || ch <= 0) return@withContext
        val frames = (rate * gapMs / 1000L).toInt().coerceAtLeast(1)
        val samples = frames * ch
        val zeros = ShortArray(minOf(samples, rate / 10 * ch).coerceAtLeast(ch))
        var remaining = samples
        while (remaining > 0 && generationActive() && !cancelled.get()) {
            val n = minOf(remaining, zeros.size)
            val written = t.write(zeros, 0, n)
            if (written < 0) break
            if (written > 0) {
                framesWritten += written / ch.toLong()
                remaining -= written
            } else {
                yield()
                delay(2)
            }
        }
    }

    private fun decodeCached(file: File): PcmClip? {
        val key = file.absolutePath
        synchronized(decodeCacheLock) {
            decodeCache[key]?.let { return it }
        }
        val decoded = decodeFile(file) ?: return null
        synchronized(decodeCacheLock) {
            decodeCache[key] = decoded
        }
        return decoded
    }

    private fun decodeFile(file: File): PcmClip? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                .coerceAtLeast(1)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var pcm = ShortArray(sampleRate * channelCount)
            var pcmSize = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: return null
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    outIndex >= 0 -> {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)
                            if (outBuf != null) {
                                val needed = pcmSize + (info.size / 2)
                                if (needed > pcm.size) {
                                    var cap = pcm.size
                                    while (cap < needed) cap *= 2
                                    pcm = pcm.copyOf(cap)
                                }
                                pcmSize = appendPcm16(pcm, pcmSize, outBuf, info)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            if (pcmSize <= 0) null
            else PcmClip(
                samples = pcm.copyOf(pcmSize),
                sampleRate = sampleRate,
                channels = if (channelCount >= 2) 2 else 1,
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun appendPcm16(
        dest: ShortArray,
        destOffset: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ): Int {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val asShort = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val count = asShort.remaining()
        asShort.get(dest, destOffset, count)
        return destOffset + count
    }

    private suspend fun writeSamples(
        samples: ShortArray,
        generationActive: () -> Boolean,
        from: Int = 0,
        length: Int = samples.size - from,
    ) {
        val t = track ?: return
        val ch = trackChannels.coerceAtLeast(1)
        var offset = from
        val end = from + length
        while (offset < end && generationActive() && !cancelled.get()) {
            val n = minOf(end - offset, 2048)
            val written = t.write(samples, offset, n)
            if (written < 0) break
            if (written > 0) {
                framesWritten += written / ch.toLong()
                offset += written
            } else {
                yield()
                delay(2)
            }
        }
    }

    private fun ensureTrack(sampleRate: Int, channelCount: Int) {
        if (track != null && trackSampleRate == sampleRate && trackChannels == channelCount) {
            val t = track!!
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                t.play()
            }
            return
        }
        releaseTrackOnly()
        val channelMask = if (channelCount >= 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuf <= 0) throw IllegalStateException("Unsupported PCM format")
        // ~1s PCM: decode-ahead covers inter-clip decode; lower write-ahead latency.
        val bytesPerSec = sampleRate * (if (channelCount >= 2) 2 else 1) * 2
        val bufBytes = max(minBuf * 4, bytesPerSec)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(encoding)
            .setChannelMask(channelMask)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        trackSampleRate = sampleRate
        trackChannels = if (channelCount >= 2) 2 else 1
        framesWritten = 0L
        t.play()
    }

    private fun releaseTrackOnly() {
        val t = track
        track = null
        trackSampleRate = 0
        trackChannels = 0
        framesWritten = 0L
        if (t != null) {
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            }
            runCatching { t.flush() }
            runCatching { t.release() }
        }
    }

    private data class PcmClip(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
    )

    companion object {
        private const val DECODE_CACHE_MAX = 4
    }
}
