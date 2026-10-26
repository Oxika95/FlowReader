package com.personal.flowreader.tts

import com.personal.flowreader.debug.DebugLog
import kotlinx.coroutines.flow.StateFlow

/**
 * TTS Synthesis channel facade over [DebugLog]. Call sites keep using this name;
 * gating is master debug + About → Debug → TTS Synthesis.
 */
object SynthDebugLog {
    val revision: StateFlow<Long> get() = DebugLog.revision
    val openRequested: StateFlow<Boolean> get() = DebugLog.openRequested

    fun requestOpen() = DebugLog.requestOpen()

    fun consumeOpenRequest() = DebugLog.consumeOpenRequest()

    /** Master debug on/off (clears the shared ring when off). */
    fun setEnabled(value: Boolean) = DebugLog.setMasterEnabled(value)

    fun isEnabled(): Boolean = DebugLog.isMasterEnabled()

    fun append(message: String) = DebugLog.tts(message)

    fun appendError(message: String) = DebugLog.appendError(message)

    fun snapshot(): List<String> = DebugLog.snapshot()

    fun clear() = DebugLog.clear()
}
