package com.personal.flowreader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.personal.flowreader.data.TtsPrefs
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Quiet broadband noise on a dedicated [AudioTrack] so Bluetooth/car head units
 * do not treat inter-clip silence as "no playback".
 */
class AudioKeepAlive {
    private val running = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var levelPercent = TtsPrefs.DEFAULT_MIN_SIGNAL

    /** Tonal underlay loudness, 0 (off) to 10 (full). Applies immediately if playing. */
    fun setLevel(percent: Float) {
        levelPercent = TtsPrefs.coerceMinSignal(percent)
        track?.setVolume(levelPercent / TtsPrefs.MAX_SIGNAL)
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
        t.setVolume(levelPercent / TtsPrefs.MAX_SIGNAL)
        t.play()
        worker = thread(name = "tts-keep-alive", isDaemon = true) {
            val buf = ShortArray(minBuf / 2)
            val rnd = Random(System.nanoTime())
            while (running.get()) {
                // Full-scale noise; [setLevel] attenuates it to the chosen percent.
                for (i in buf.indices) {
                    buf[i] = rnd.nextInt(-32767, 32768).toShort()
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
