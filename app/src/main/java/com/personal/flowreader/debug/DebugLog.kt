package com.personal.flowreader.debug

import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded in-memory ring for debug channels (TTS synth, eReader jumps). Append is cheap;
 * only writes while master debug is on and the channel pref is on (except [appendError]).
 */
object DebugLog {
    const val CHANNEL_TTS = "tts"
    const val CHANNEL_READER = "reader"

    /** About 20+ minutes of Edge playback, enough for a walk test. */
    private const val MAX_LINES = 5_000
    private val master = AtomicBoolean(false)
    private val ttsChannel = AtomicBoolean(true)
    private val readerChannel = AtomicBoolean(true)
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()
    private val _revision = MutableStateFlow(0L)

    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _openRequested = MutableStateFlow(false)
    val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

    fun requestOpen() {
        _openRequested.value = true
    }

    fun consumeOpenRequest() {
        _openRequested.value = false
    }

    fun setMasterEnabled(value: Boolean) {
        master.set(value)
        if (!value) {
            synchronized(lock) { lines.clear() }
            bump()
        }
    }

    fun setTtsLogEnabled(value: Boolean) {
        ttsChannel.set(value)
    }

    fun setReaderLogEnabled(value: Boolean) {
        readerChannel.set(value)
    }

    fun isMasterEnabled(): Boolean = master.get()

    fun isTtsLogEnabled(): Boolean = ttsChannel.get()

    fun isReaderLogEnabled(): Boolean = readerChannel.get()

    fun append(channel: String, message: String) {
        if (!master.get()) return
        when (channel) {
            CHANNEL_TTS -> if (!ttsChannel.get()) return
            CHANNEL_READER -> if (!readerChannel.get()) return
            else -> return
        }
        write("$channel $message")
        logcat(channel, message, error = false)
    }

    fun tts(message: String) = append(CHANNEL_TTS, message)

    fun reader(message: String) = append(CHANNEL_READER, message)

    /** Force-enable master + TTS channel and record an error line. */
    fun appendError(message: String) {
        master.set(true)
        ttsChannel.set(true)
        write("$CHANNEL_TTS ERROR $message")
        logcat(CHANNEL_TTS, message, error = true)
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() {
        synchronized(lock) { lines.clear() }
        bump()
    }

    private fun write(message: String) {
        val line = "${System.currentTimeMillis()} $message"
        synchronized(lock) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1L
    }

    private fun logTag(channel: String): String = when (channel) {
        CHANNEL_TTS -> "FlowReader.Tts"
        CHANNEL_READER -> "FlowReader.Reader"
        else -> "FlowReader.Debug"
    }

    /** Logcat is absent in JVM unit tests; never let that drop a ring line. */
    private fun logcat(channel: String, message: String, error: Boolean) {
        runCatching {
            val tag = logTag(channel)
            if (error) Log.e(tag, message) else Log.d(tag, message)
        }
    }
}
