package com.personal.flowreader.share

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareInboxTest {
    @Test
    fun handlesInOrderOneAtATimeWithOneAutoPlayPerBurst() = runBlocking {
        val inbox = ShareInbox<Int>()
        val handled = ArrayList<Pair<Int, Boolean>>()
        val gate = CompletableDeferred<Unit>()
        var running = 0
        var maxRunning = 0
        val job = launch {
            inbox.process { item, claim ->
                running++
                maxRunning = maxOf(maxRunning, running)
                if (item == 1) gate.await()
                handled += item to claim()
                running--
            }
        }
        (1..5).forEach(inbox::submit)
        yield()
        gate.complete(Unit)
        withTimeout(5_000) { while (handled.size < 5) yield() }
        assertEquals(listOf(1 to true, 2 to false, 3 to false, 4 to false, 5 to false), handled)
        assertEquals(1, maxRunning)

        inbox.submit(6)
        withTimeout(5_000) { while (handled.size < 6) yield() }
        assertEquals(6 to true, handled.last())
        job.cancel()
    }
}
