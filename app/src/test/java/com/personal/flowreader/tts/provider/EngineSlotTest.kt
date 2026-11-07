package com.personal.flowreader.tts.provider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EngineSlotTest {
    private class Fake(val pkg: String?) {
        var released = false
    }

    private val created = mutableListOf<Fake>()

    private fun slot(failFor: String? = null) = EngineSlot<Fake>(
        bind = { pkg ->
            if (pkg != null && pkg == failFor) error("init failed")
            Fake(pkg).also { created += it }
        },
        release = { it.released = true },
    )

    @Test fun reusesEngineForSamePackage() = runBlocking {
        val slot = slot()
        val a = slot.use(null) { it }
        val b = slot.use(null) { it }
        assertSame(a, b)
        assertEquals(1, created.size)
    }

    @Test fun otherPackageReleasesOldEngine() = runBlocking {
        val slot = slot()
        val a = slot.use(null) { it }
        val b = slot.use("pkg") { it }
        assertTrue(a.released)
        assertFalse(b.released)
        assertSame(b, slot.current())
    }

    @Test fun rebindCreatesNewEngineForSamePackage() = runBlocking {
        val slot = slot()
        val a = slot.use("pkg") { it }
        val b = slot.use("pkg", rebind = true) { it }
        assertTrue(a.released)
        assertFalse(b === a)
    }

    @Test fun peekMatchesOnlyTheBoundPackage() = runBlocking {
        val slot = slot()
        assertNull(slot.peek(null))
        val a = slot.use("pkg") { it }
        assertSame(a, slot.peek("pkg"))
        assertNull(slot.peek(null))
        assertSame(a, slot.current())
    }

    @Test fun usersNeverOverlap() = runBlocking {
        val slot = slot()
        var running = 0
        var peak = 0
        val jobs = List(4) {
            async {
                slot.use(if (it % 2 == 0) null else "pkg") {
                    running++
                    peak = maxOf(peak, running)
                    delay(10)
                    running--
                }
            }
        }
        jobs.forEach { it.await() }
        assertEquals(1, peak)
    }

    @Test fun shutdownWhileIdleReleasesNow() = runBlocking {
        val slot = slot()
        val a = slot.use(null) { it }
        slot.shutdown()
        assertTrue(a.released)
        assertNull(slot.current())
    }

    @Test fun shutdownWhileInUseWaitsForTheUser() = runBlocking {
        val slot = slot()
        val started = CompletableDeferred<Fake>()
        val finish = CompletableDeferred<Unit>()
        val user = async { slot.use(null) { started.complete(it); finish.await() } }
        val engine = started.await()
        slot.shutdown()
        assertFalse("engine released while in use", engine.released)
        finish.complete(Unit)
        user.await()
        assertTrue(engine.released)
        assertNull(slot.current())
    }

    @Test fun failedBindLeavesSlotUsable() = runBlocking {
        val slot = slot(failFor = "bad")
        try {
            slot.use("bad") { }
            fail("bind should have failed")
        } catch (_: IllegalStateException) {
        }
        assertNull(slot.current())
        val ok = slot.use(null) { it }
        assertSame(ok, slot.current())
    }
}
