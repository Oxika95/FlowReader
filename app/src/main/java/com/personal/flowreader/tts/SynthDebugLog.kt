package com.personal.flowreader.tts

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded in-memory ring of synthesizer events for post-stutter dumps.
 * Append is cheap and lock-guarded; only active while [enabled] is true.
 */
object SynthDebugLog {
    private const val MAX_LINES = 800
    private val enabled = AtomicBoolean(false)
    private val lines = ArrayDeque<String>(MAX_LINES)
    private val lock = Any()
    private val _revision = MutableStateFlow(0L)

    /** Bumps on each append/clear so UI can refresh a live snapshot. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        if (!value) {
            synchronized(lock) { lines.clear() }
            bump()
        }
    }

    fun isEnabled(): Boolean = enabled.get()

    fun append(message: String) {
        if (!enabled.get()) return
        val ts = System.currentTimeMillis()
        val line = "$ts $message"
        synchronized(lock) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
        bump()
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun clear() {
        synchronized(lock) { lines.clear() }
        bump()
    }

    private fun bump() {
        _revision.value = _revision.value + 1L
    }
}
