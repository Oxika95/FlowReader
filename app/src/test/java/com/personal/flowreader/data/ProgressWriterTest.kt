package com.personal.flowreader.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressWriterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = FakePositionStore()
    private val logged = ArrayList<Pair<ProgressUpdate, Boolean>>()
    private val written = ArrayList<ProgressUpdate>()

    @After
    fun tearDown() = scope.cancel()

    private fun writer() = ProgressWriter(
        store,
        scope,
        debounceMs = 60_000L,
        log = { u, ok -> logged += u to ok },
        onWritten = { written += it },
    )

    private val library = ReadingSessionId(PositionDomain.Library, "b", openedAt = 100L)
    private val queue = ReadingSessionId(PositionDomain.Queue, "", openedAt = 200L)

    private fun update(
        session: ReadingSessionId,
        rowKey: String,
        chapter: Int,
        at: Long,
        source: PositionSource = PositionSource.Reader,
    ) = ProgressUpdate(session, rowKey, chapter, blockIndex = 1, charOffset = 2, fraction = chapter / 10f, at = at, source = source)

    @Test
    fun latestPendingUpdatePerRowWins() = runBlocking {
        store.rows[PositionDomain.Library to "b"] = ReadingPosition()
        val w = writer()
        w.submit(update(library, "b", 4, at = 10L))
        w.submit(update(library, "b", 5, at = 11L))
        w.submit(update(library, "b", 3, at = 9L))
        w.drain()
        assertEquals(5, store.rows.getValue(PositionDomain.Library to "b").chapterIndex)
        assertEquals(1, logged.size)
    }

    @Test
    fun sameKeyInDifferentDomainsStaysSeparate() = runBlocking {
        store.rows[PositionDomain.Library to "b"] = ReadingPosition()
        store.rows[PositionDomain.Queue to "b"] = ReadingPosition()
        val w = writer()
        w.submit(update(library, "b", 7, at = 10L))
        w.submit(update(queue, "b", 9, at = 11L))
        w.drain()
        assertEquals(7, store.rows.getValue(PositionDomain.Library to "b").chapterIndex)
        assertEquals(9, store.rows.getValue(PositionDomain.Queue to "b").chapterIndex)
    }

    @Test
    fun queueUpdatesForDifferentItemsBothWrite() = runBlocking {
        store.rows[PositionDomain.Queue to "q1"] = ReadingPosition()
        store.rows[PositionDomain.Queue to "q2"] = ReadingPosition()
        val w = writer()
        w.submit(update(queue, "q1", 2, at = 10L))
        w.submit(update(queue, "q2", 6, at = 11L))
        w.drain()
        assertEquals(2, store.rows.getValue(PositionDomain.Queue to "q1").chapterIndex)
        assertEquals(6, store.rows.getValue(PositionDomain.Queue to "q2").chapterIndex)
    }

    @Test
    fun provenanceIsStored() = runBlocking {
        store.rows[PositionDomain.Library to "b"] = ReadingPosition()
        val w = writer()
        w.submit(update(library, "b", 3, at = 50L, source = PositionSource.Tts).copy(anchorText = "a", chapterHref = "c.xhtml"))
        w.drain()
        val p = store.rows.getValue(PositionDomain.Library to "b")
        assertEquals(50L, p.positionAt)
        assertEquals(100L, p.positionSessionAt)
        assertEquals("Tts", p.positionSource)
        assertEquals("a", p.anchorText)
        assertEquals("c.xhtml", p.chapterHref)
        assertEquals(0.3f, p.fraction, 0.0001f)
    }

    @Test
    fun missingRowIsLoggedNotWritten() = runBlocking {
        val w = writer()
        w.submit(update(library, "gone", 3, at = 5L))
        w.drain()
        assertTrue(store.rows.isEmpty())
        assertEquals(1, logged.size)
        assertFalse(logged.single().second)
        assertTrue(written.isEmpty())
    }

    @Test
    fun blankRowKeyIsDropped() = runBlocking {
        val w = writer()
        w.submit(update(queue, "", 3, at = 5L))
        w.drain()
        assertTrue(logged.isEmpty())
    }

    @Test
    fun storeFailureIsReportedAndOtherUpdatesStillWrite() = runBlocking {
        store.rows[PositionDomain.Library to "ok"] = ReadingPosition()
        store.failFor = "bad"
        val failed = ArrayList<Pair<ProgressUpdate, Throwable>>()
        val w = ProgressWriter(
            store,
            scope,
            debounceMs = 60_000L,
            log = { u, ok -> logged += u to ok },
            onFailed = { u, e -> failed += u to e },
            onWritten = { written += it },
        )
        w.submit(update(library, "bad", 2, at = 10L))
        w.submit(update(library, "ok", 3, at = 11L))
        w.drain()
        assertEquals(listOf("bad"), failed.map { it.first.rowKey })
        assertEquals(3, store.rows.getValue(PositionDomain.Library to "ok").chapterIndex)
        assertEquals(listOf("ok"), written.map { it.rowKey })
        assertEquals(listOf("ok"), logged.map { it.first.rowKey })
    }

    @Test
    fun writerKeepsWritingAfterAFailure() = runBlocking {
        store.rows[PositionDomain.Library to "b"] = ReadingPosition()
        store.failFor = "b"
        val w = writer()
        w.submit(update(library, "b", 2, at = 10L))
        w.drain()
        store.failFor = null
        w.submit(update(library, "b", 4, at = 11L))
        w.drain()
        assertEquals(4, store.rows.getValue(PositionDomain.Library to "b").chapterIndex)
    }

    @Test
    fun failingOnFailedHookDoesNotEscape() = runBlocking {
        store.failFor = "b"
        val w = ProgressWriter(store, scope, debounceMs = 60_000L, onFailed = { _, _ -> error("hook") })
        w.submit(update(library, "b", 2, at = 10L))
        w.drain()
    }

    @Test
    fun submitAndDrainWritesBeforeReturning() = runBlocking {
        store.rows[PositionDomain.Library to "b"] = ReadingPosition()
        writer().submitAndDrain(update(library, "b", 6, at = 10L))
        assertEquals(6, store.rows.getValue(PositionDomain.Library to "b").chapterIndex)
    }

    @Test
    fun chapterSeekSetsFractionFromChapterCount() {
        val u = ProgressUpdate.chapterSeek(
            PositionDomain.Plugin, "rr:1", chapterIndex = 3, chapterCount = 12,
            source = PositionSource.PluginSeek, chapterHref = "u3", at = 7L,
        )
        assertEquals("rr:1", u.rowKey)
        assertEquals(PositionDomain.Plugin, u.domain)
        assertEquals("rr:1", u.session.key)
        assertEquals(0.25f, u.fraction, 0f)
        assertEquals(0, u.blockIndex)
        assertEquals(0, u.charOffset)
        assertEquals("", u.anchorText)
        assertEquals("u3", u.chapterHref)
        assertEquals(PositionSource.PluginSeek, u.source)
        assertEquals(7L, u.at)
    }

    @Test
    fun chapterSeekWithUnknownCountHasZeroFraction() {
        val u = ProgressUpdate.chapterSeek(PositionDomain.Plugin, "rr:1", 3, 0, PositionSource.Sync)
        assertEquals(0f, u.fraction, 0f)
    }

    @Test
    fun chapterSeekKeepsInChapterOffsets() {
        val u = ProgressUpdate.chapterSeek(
            PositionDomain.Plugin, "rr:1", 3, 12, PositionSource.Sync,
            blockIndex = 5, charOffset = 9, anchorText = "text",
        )
        assertEquals(5, u.blockIndex)
        assertEquals(9, u.charOffset)
        assertEquals("text", u.anchorText)
    }

    @Test
    fun fractionIsClamped() {
        val p = update(library, "b", 0, at = 1L).copy(fraction = 1.4f).toPosition()
        assertEquals(1f, p.fraction, 0f)
    }
}

private class FakePositionStore : PositionStore {
    val rows = LinkedHashMap<Pair<PositionDomain, String>, ReadingPosition>()

    var failFor: String? = null

    override suspend fun write(update: ProgressUpdate): Boolean {
        if (update.rowKey == failFor) error("disk full")
        val key = update.domain to update.rowKey
        if (key !in rows) return false
        rows[key] = update.toPosition()
        return true
    }
}
