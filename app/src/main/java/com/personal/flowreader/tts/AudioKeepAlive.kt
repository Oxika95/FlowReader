package com.personal.flowreader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.personal.flowreader.data.TtsPrefs
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Quiet broadband noise on a dedicated [AudioTrack] so Bluetooth/car head units
 * do not treat inter-clip silence as "no playback".
 *
 * Level is dB below full-scale PCM (`0` = off). Gain is applied to the written
 * samples so very low steps stay accurate.
 */
class AudioKeepAlive {
    private val running = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var linearGain = 0f

    /** Underlay level: `0` off, otherwise negative dB vs full scale. */
    fun setLevel(levelDb: Float) {
        linearGain = TtsPrefs.underlayLinearGain(levelDb)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val sampleRate = 24_000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuf <= 0) {
            running.set(false)
            return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(encoding)
            .setChannelMask(channelConfig)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.setVolume(1f)
        t.play()
        worker = thread(name = "tts-keep-alive", isDaemon = true) {
            val buf = ShortArray(minBuf / 2)
            val rnd = Random(System.nanoTime())
            while (running.get()) {
                val gain = linearGain
                if (gain <= 0f) {
                    buf.fill(0)
                } else {
                    for (i in buf.indices) {
                        val sample = rnd.nextInt(-32767, 32768) * gain
                        buf[i] = sample.roundToInt().coerceIn(-32767, 32767).toShort()
                    }
                }
                val written = t.write(buf, 0, buf.size)
                if (written < 0) break
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        worker?.join(500)
        worker = null
        val t = track
        track = null
        if (t != null) {
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            }
            runCatching { t.release() }
        }
    }
}
