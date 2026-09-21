package com.personal.flowreader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Decodes sentence MP3 files to PCM and writes them to a single stream-mode [AudioTrack],
 * keeping one continuous output across clips.
 */
class PcmSentencePlayer {
    private val cancelled = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var trackSampleRate = 0
    private var trackChannels = 0

    fun cancel() {
        cancelled.set(true)
    }

    fun release() {
        cancelled.set(true)
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
     * Play [file] as PCM. Returns when the clip has been fully written (or cancelled).
     * [generationActive] should return false when the controller has moved on.
     */
    suspend fun play(
        file: File,
        generationActive: () -> Boolean,
    ) = withContext(Dispatchers.IO) {
        cancelled.set(false)
        if (!generationActive() || cancelled.get()) return@withContext
        decodeAndWrite(file, generationActive)
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
            remaining -= written
            yield()
        }
    }

    private fun decodeAndWrite(file: File, generationActive: () -> Boolean) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw IllegalStateException("No audio track in clip")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Missing audio mime")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            ensureTrack(sampleRate, channelCount)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone && generationActive() && !cancelled.get()) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                            ?: throw IllegalStateException("No input buffer")
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
                                writePcm(outBuf, info)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun writePcm(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val t = track ?: return
        val bytes = ByteArray(info.size)
        buffer.position(info.offset)
        buffer.get(bytes)
        var offset = 0
        while (offset < bytes.size && !cancelled.get()) {
            val written = t.write(bytes, offset, bytes.size - offset)
            if (written < 0) break
            offset += written
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
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        trackSampleRate = sampleRate
        trackChannels = if (channelCount >= 2) 2 else 1
        t.play()
    }

    private fun releaseTrackOnly() {
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
    }
}
