package com.personal.flowreader.tts.provider

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds one bound engine and the lock that guards it. Everything that touches the engine runs
 * inside [use], so utterances and rebinds never overlap, and a [shutdown] requested while the
 * engine is in use waits until the user is done. Pure so the rules have JVM tests;
 * [SystemTtsEngine] plugs in the Android `TextToSpeech`.
 */
class EngineSlot<E : Any>(
    private val bind: suspend (enginePackage: String?) -> E,
    private val release: (E) -> Unit,
) {
    private val lock = Mutex()

    @Volatile private var engine: E? = null

    @Volatile private var boundPackage: String? = null

    @Volatile private var shutdownRequested = false

    /** The bound engine, whatever its package. Never blocks; used to stop an utterance. */
    fun current(): E? = engine

    /** The bound engine when it is for [enginePackage], else null. Never blocks. */
    fun peek(enginePackage: String?): E? = engine?.takeIf { boundPackage == enginePackage }

    /**
     * Runs [block] with the engine for [enginePackage], binding it first when none is bound, the
     * package differs, or [rebind] is set. The old engine is released only here, under the lock.
     */
    suspend fun <T> use(enginePackage: String?, rebind: Boolean = false, block: suspend (E) -> T): T =
        try {
            lock.withLock {
                var current = engine
                if (current == null || rebind || boundPackage != enginePackage) {
                    drop()
                    current = bind(enginePackage)
                    engine = current
                    boundPackage = enginePackage
                }
                block(current)
            }
        } finally {
            releaseIfRequested()
        }

    /** Releases the engine now, or after the current [use] ends when one is running. */
    fun shutdown() {
        shutdownRequested = true
        releaseIfRequested()
    }

    private fun releaseIfRequested() {
        if (!lock.tryLock()) return
        try {
            if (shutdownRequested) {
                shutdownRequested = false
                drop()
            }
        } finally {
            lock.unlock()
        }
    }

    private fun drop() {
        engine?.let(release)
        engine = null
        boundPackage = null
    }
}
