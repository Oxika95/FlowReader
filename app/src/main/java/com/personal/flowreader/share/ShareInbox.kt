package com.personal.flowreader.share

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel

/**
 * In-process queue of routed shares, handled one at a time in arrival order. Shares never ride
 * in Activity intents, which Android replays on recreate and Recents relaunch.
 *
 * Auto Play is claimed once per burst: the first item of a run of shares may start playback,
 * the rest only enqueue. The burst ends when the inbox is empty again.
 */
class ShareInbox<T> {
    private val channel = Channel<T>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    @Volatile
    private var claimed = false

    fun submit(item: T) {
        pending.incrementAndGet()
        channel.trySend(item)
    }

    /** Handle items until cancelled. [handle] gets a one-shot claim on Auto Play for this burst. */
    suspend fun process(handle: suspend (item: T, claimAutoPlay: () -> Boolean) -> Unit) {
        for (item in channel) {
            try {
                handle(item) {
                    synchronized(this) {
                        if (claimed) false else true.also { claimed = true }
                    }
                }
            } finally {
                if (pending.decrementAndGet() == 0) claimed = false
            }
        }
    }
}
