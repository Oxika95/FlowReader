package com.personal.flowreader.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressWriterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dao = FakeProgressDao()

    @After
    fun tearDown() = scope.cancel()

    private fun writer() = ProgressWriter(dao, scope, debounceMs = 60_000L)

    private fun update(chapter: Int, at: Long, fraction: Float? = null) =
        ProgressUpdate("b", chapter, blockIndex = 1, charOffset = 2, fraction = fraction, at = at)

    @Test
    fun writesLatestPendingUpdate() = runBlocking {
        dao.rows["b"] = row(chapter = 0, at = 1L, progress = 0.3f)
        val w = writer()
        w.submit(update(4, at = 10L))
        w.submit(update(5, at = 11L))
        w.drain()
        val stored = dao.rows.getValue("b")
        assertEquals(5, stored.chapterIndex)
        assertEquals(11L, stored.updatedAt)
        assertEquals(0.3f, stored.readingProgress, 0f)
    }

    @Test
    fun olderUpdateNeverRewindsANewerWrite() = runBlocking {
        dao.rows["b"] = row(chapter = 0, at = 1L)
        val w = writer()
        w.submit(update(9, at = 20L, fraction = 0.9f))
        w.drain()
        w.submit(update(2, at = 15L, fraction = 0.2f))
        w.drain()
        assertEquals(9, dao.rows.getValue("b").chapterIndex)
        assertEquals(0.9f, dao.rows.getValue("b").readingProgress, 0f)
    }

    @Test
    fun missingRowIsIgnored() = runBlocking {
        val w = writer()
        w.submit(update(3, at = 5L))
        w.drain()
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun locatorMapsWindowLocus() {
        val w = writer()
        w.setLocator("b") { locus, at ->
            ProgressUpdate("b", locus.chapterIndex + 3, locus.blockIndex, locus.charOffset, 0.5f, at, "x", "h")
        }
        assertEquals(7, w.locate("b", Locus(4, 0, 0), 1L).chapterIndex)
        w.setLocator("b", null)
        val plain = w.locate("b", Locus(4, 1, 2), 1L)
        assertEquals(4, plain.chapterIndex)
        assertEquals(null, plain.fraction)
    }

    @Test
    fun anchorAndHrefStoredAndLegacyFlagCleared() = runBlocking {
        dao.rows["b"] = row(chapter = 0, at = 1L).copy(locusVersion = 0, anchorText = "old", chapterHref = "a.xhtml")
        val w = writer()
        w.submit(update(2, at = 5L))
        w.drain()
        assertEquals("old", dao.rows.getValue("b").anchorText)
        assertEquals(ProgressEntity.LOCUS_CURRENT, dao.rows.getValue("b").locusVersion)
        w.submit(update(3, at = 6L).copy(anchorText = "new", chapterHref = "b.xhtml"))
        w.drain()
        assertEquals("new", dao.rows.getValue("b").anchorText)
        assertEquals("b.xhtml", dao.rows.getValue("b").chapterHref)
    }

    private fun row(chapter: Int, at: Long, progress: Float = 0f) = ProgressEntity(
        bookId = "b",
        title = "t",
        storedPath = "",
        chapterIndex = chapter,
        blockIndex = 0,
        charOffset = 0,
        updatedAt = at,
        readingProgress = progress,
    )
}

private class FakeProgressDao : ProgressDao {
    val rows = LinkedHashMap<String, ProgressEntity>()
    override suspend fun get(id: String) = rows[id]
    override suspend fun latest() = rows.values.maxByOrNull { it.updatedAt }
    override suspend fun library() = rows.values.toList()
    override suspend fun libraryTab(tabId: String) = rows.values.filter { it.libraryTabId == tabId }
    override suspend fun clearLibraryTab(tabId: String) = Unit
    override suspend fun pluginLibrary(sourceKind: String) = rows.values.filter { it.sourceKind == sourceKind }
    override suspend fun getMany(ids: List<String>) = ids.mapNotNull { rows[it] }
    override suspend fun upsert(row: ProgressEntity) {
        rows[row.bookId] = row
    }
    override suspend fun delete(id: String) {
        rows.remove(id)
    }
}
