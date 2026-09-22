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
 * Decodes sentence MP3 files to PCM and writes them to a single stream-mode [AudioTrack],
 * keeping one continuous output across clips. Supports overlapping the tail of one clip
 * with the head of the next when [overlapMs] &gt; 0.
 */
class PcmSentencePlayer {
    private val cancelled = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var trackSampleRate = 0
    private var trackChannels = 0
    /** Total frames written to the current track (matches hearable timeline). */
    private var framesWritten: Long = 0L
    /** Remainder of a clip already started via overlap; flushed on the next [play]. */
    private var pendingRemainder: PcmClip? = null
    /** Media-time seed (seconds) for [pendingRemainder] after an overlap into that clip. */
    private var pendingMediaSeedSec: Double = 0.0

    /** Unsigned playback-head frames for word-highlight sync (Readest-style heard time). */
    fun playbackHeadFrames(): Long {
        val t = track ?: return 0L
        return t.playbackHeadPosition.toLong() and 0xffff_ffffL
    }

    /** Frames queued to the track so far; equals the hearable frame index of the next write. */
    fun writtenFrames(): Long = framesWritten

    fun sampleRateHz(): Int = trackSampleRate

    /** Seconds already played into the pending remainder clip (0 if none). */
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

    /**
     * Play [file] as PCM. When [overlapMs] &gt; 0 and [nextFile] is present, mixes the last
     * [overlapMs] of this clip with the start of the next and holds the next clip's
     * remainder for the following [play] call.
     *
     * Does **not** report word-highlight time — callers must poll [playbackHeadFrames]
     * against marks taken from [writtenFrames] (write-ahead is ahead of what is heard).
     *
     * [onHearableStart] fires just before writing this clip (seed already into the clip).
     * [onOverlapNextStart] fires just before the mixed next-clip head (seed 0 of next).
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
        } ?: (decodeFile(file) ?: return@withContext)
        ensureTrack(current.sampleRate, current.channels)
        onHearableStart?.invoke(seed)
        playClipOverlapping(current, nextFile, overlapMs, generationActive, onOverlapNextStart)
    }

    private suspend fun playClipOverlapping(
        current: PcmClip,
        nextFile: File?,
        overlapMs: Int,
        generationActive: () -> Boolean,
        onOverlapNextStart: (() -> Unit)?,
    ) {
        if (overlapMs <= 0 || nextFile == null || !nextFile.exists() || nextFile.length() == 0L) {
            writeSamples(current.samples, generationActive)
            return
        }

        val overlapFrames = (current.sampleRate * overlapMs / 1000).coerceAtLeast(1)
        val overlapSamplesDesired = (overlapFrames * current.channels)
            .coerceAtMost(current.samples.size)
        if (overlapSamplesDesired <= 0) {
            writeSamples(current.samples, generationActive)
            return
        }

        val cut = current.samples.size - overlapSamplesDesired

        // Decode the next clip while we keep the track fed with this clip's body.
        // Previously we decoded first — underruns while MediaCodec ran with only ~0.3s buffer.
        val next = if (cut > 0) {
            coroutineScope {
                val nextDeferred = async(Dispatchers.IO) { decodeFile(nextFile) }
                writeSamples(current.samples, generationActive, from = 0, length = cut)
                nextDeferred.await()
            }
        } else {
            decodeFile(nextFile)
        }

        if (!generationActive() || cancelled.get()) return

        if (next == null ||
            next.sampleRate != current.sampleRate ||
            next.channels != current.channels
        ) {
            if (cut < current.samples.size) {
                writeSamples(
                    current.samples,
                    generationActive,
                    from = cut,
                    length = current.samples.size - cut,
                )
            }
            return
        }

        val overlapSamples = overlapSamplesDesired.coerceAtMost(next.samples.size)
        if (overlapSamples <= 0) {
            if (cut < current.samples.size) {
                writeSamples(
                    current.samples,
                    generationActive,
                    from = cut,
                    length = current.samples.size - cut,
                )
            }
            return
        }

        // Current tail past what next can cover — write unmatched current first.
        if (overlapSamples < overlapSamplesDesired) {
            val unmatched = overlapSamplesDesired - overlapSamples
            writeSamples(current.samples, generationActive, from = cut, length = unmatched)
        }

        if (!generationActive() || cancelled.get()) return

        // Next sentence becomes hearable at the mix; mark before writing it.
        onOverlapNextStart?.invoke()
        val mixFrom = cut + (overlapSamplesDesired - overlapSamples)
        val mixed = ShortArray(overlapSamples)
        for (i in 0 until overlapSamples) {
            val sum = current.samples[mixFrom + i].toInt() + next.samples[i].toInt()
            mixed[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeSamples(mixed, generationActive)
        if (overlapSamples < next.samples.size) {
            pendingRemainder = PcmClip(
                samples = next.samples.copyOfRange(overlapSamples, next.samples.size),
                sampleRate = next.sampleRate,
                channels = next.channels,
            )
            pendingMediaSeedSec = overlapMs / 1000.0
        }
    }

    /** Write [gapMs] of silence into the open track (keeps the stream continuous). */
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

            // Growable ShortArray — avoid ArrayList<Short> boxing (was multi-100ms on decode).
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
                // Buffer full / transient 0 — retry; never drop the rest of the clip.
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
        // ~2s of PCM so decode/synth jitter between clips doesn't underrun.
        val bytesPerSec = sampleRate * (if (channelCount >= 2) 2 else 1) * 2
        val bufBytes = max(minBuf * 4, bytesPerSec * 2)
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
}
