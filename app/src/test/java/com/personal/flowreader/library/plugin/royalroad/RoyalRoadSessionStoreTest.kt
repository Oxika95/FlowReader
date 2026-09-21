package com.personal.flowreader.library.plugin.royalroad

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyalRoadSessionStoreTest {
    @Test
    fun ensureTocWritesTitlesWithoutBodies() {
        val root = File.createTempFile("rr-session", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            val session = RoyalRoadReadSession(
                bookId = "rr:1",
                fictionId = "1",
                fictionUrl = "https://www.royalroad.com/fiction/1/demo",
                title = "Demo",
                author = "Author",
                toc = listOf(
                    ChapterLink("One", "https://www.royalroad.com/fiction/1/demo/chapter/1/a"),
                    ChapterLink("Two", "https://www.royalroad.com/fiction/1/demo/chapter/2/b"),
                ),
                startIndex = 0,
                loadedThrough = -1,
                chapters = emptyList(),
            )
            val dir = RoyalRoadSessionStore.dir(root, "1")
            RoyalRoadSessionStore.writeMeta(dir, session)
            RoyalRoadSessionStore.writeSplash(
                dir,
                RoyalRoadSplashMeta(
                    synopsis = "Hello",
                    tags = listOf("Fantasy"),
                    views = 10,
                    ratingLabel = "4 / 5",
                    status = "Ongoing",
                    coverUrl = "https://cdn.example/cover.jpg",
                ),
            )
            val loaded = RoyalRoadSessionStore.read(root, "1")!!
            assertEquals(2, loaded.toc.size)
            assertEquals("One", loaded.toc[0].title)
            assertEquals(-1, loaded.loadedThrough)
            assertEquals(RoyalRoadCachePolicy.DEFAULT_AHEAD, loaded.prefetchAhead)
            assertEquals(RoyalRoadCachePolicy.DEFAULT_BEHIND, loaded.keepBehind)
            assertTrue(loaded.pinnedRanges.isEmpty())
            assertFalse(File(dir, "c/0.txt").exists())
            val splash = RoyalRoadSessionStore.readSplash(dir)!!
            assertEquals("Hello", splash.synopsis)
            assertEquals(listOf("Fantasy"), splash.tags)
            assertEquals(10L, splash.views)
            val meta = RoyalRoadSessionStore.libraryMeta(root, "1")!!
            assertEquals("Author", meta.author)
            assertEquals(2, meta.chapterCount)
            assertEquals(0, meta.downloadedCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun tocRefreshPreservesCachedBodies() {
        val root = File.createTempFile("rr-session2", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            val dir = RoyalRoadSessionStore.dir(root, "9")
            val first = RoyalRoadReadSession(
                bookId = "rr:9",
                fictionId = "9",
                fictionUrl = "https://www.royalroad.com/fiction/9/x",
                title = "X",
                author = "A",
                toc = listOf(ChapterLink("Old", "https://www.royalroad.com/fiction/9/x/chapter/1/a")),
                startIndex = 0,
                loadedThrough = 0,
                chapters = emptyList(),
            )
            RoyalRoadSessionStore.writeMeta(dir, first)
            RoyalRoadSessionStore.writeChapter(dir, 0, "Old", "body text")
            val refreshed = first.copy(
                toc = listOf(
                    ChapterLink("Old", "https://www.royalroad.com/fiction/9/x/chapter/1/a"),
                    ChapterLink("New", "https://www.royalroad.com/fiction/9/x/chapter/2/b"),
                ),
                loadedThrough = 0,
            )
            RoyalRoadSessionStore.writeMeta(dir, refreshed)
            assertTrue(File(dir, "c/0.txt").exists())
            assertEquals("body text", RoyalRoadSessionStore.readChapterText(dir, 0)!!.second)
            assertEquals(2, RoyalRoadSessionStore.read(root, "9")!!.toc.size)
            assertEquals(1, RoyalRoadSessionStore.cachedChapterCount(dir, 2))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cachePolicyRoundTripsInMeta() {
        val root = tempRoot("rr-policy")
        try {
            val dir = RoyalRoadSessionStore.dir(root, "3")
            val session = baseSession(
                fictionId = "3",
                tocSize = 10,
                prefetchAhead = 4,
                keepBehind = 2,
                pinnedRanges = listOf(0..2, 8..9),
            )
            RoyalRoadSessionStore.writeMeta(dir, session)
            val loaded = RoyalRoadSessionStore.read(root, "3")!!
            assertEquals(4, loaded.prefetchAhead)
            assertEquals(2, loaded.keepBehind)
            assertEquals(listOf(0..2, 8..9), loaded.pinnedRanges)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun desiredSetUnionsWindowAndPins() {
        val policy = RoyalRoadCachePolicy(
            prefetchAhead = 1,
            keepBehind = 1,
            pinnedRanges = listOf(8..9),
        )
        val desired = RoyalRoadSessionStore.desiredChapterIndices(
            locus = 3,
            tocSize = 10,
            policy = policy,
        )
        assertEquals(setOf(2, 3, 4, 8, 9), desired)
    }

    @Test
    fun pruneKeepsPinsAndWindowDeletesRest() {
        val root = tempRoot("rr-prune")
        try {
            val dir = RoyalRoadSessionStore.dir(root, "5")
            val session = baseSession(fictionId = "5", tocSize = 6, pinnedRanges = listOf(5..5))
            RoyalRoadSessionStore.writeMeta(dir, session)
            for (i in 0..5) {
                RoyalRoadSessionStore.writeChapter(dir, i, "C$i", "body $i")
            }
            val desired = RoyalRoadSessionStore.desiredChapterIndices(
                locus = 2,
                tocSize = 6,
                policy = session.cachePolicy,
            )
            // window around 2 with 1/1 => 1,2,3 plus pin 5
            assertEquals(setOf(1, 2, 3, 5), desired)
            RoyalRoadSessionStore.pruneChaptersOutside(dir, 6, desired)
            assertFalse(RoyalRoadSessionStore.hasChapter(dir, 0))
            assertTrue(RoyalRoadSessionStore.hasChapter(dir, 1))
            assertTrue(RoyalRoadSessionStore.hasChapter(dir, 2))
            assertTrue(RoyalRoadSessionStore.hasChapter(dir, 3))
            assertFalse(RoyalRoadSessionStore.hasChapter(dir, 4))
            assertTrue(RoyalRoadSessionStore.hasChapter(dir, 5))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun partialRangePinMathMergesAndClips() {
        val merged = RoyalRoadSessionStore.mergePinnedRanges(
            listOf(0..2, 2..4, 10..12, 7..7),
        )
        assertEquals(listOf(0..4, 7..7, 10..12), merged)
        val clipped = RoyalRoadSessionStore.clipPinnedRanges(merged, tocSize = 8)
        assertEquals(listOf(0..4, 7..7), clipped)
    }

    private fun tempRoot(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }

    private fun baseSession(
        fictionId: String,
        tocSize: Int,
        prefetchAhead: Int = RoyalRoadCachePolicy.DEFAULT_AHEAD,
        keepBehind: Int = RoyalRoadCachePolicy.DEFAULT_BEHIND,
        pinnedRanges: List<IntRange> = emptyList(),
    ): RoyalRoadReadSession {
        val toc = (1..tocSize).map { i ->
            ChapterLink("Ch $i", "https://www.royalroad.com/fiction/$fictionId/x/chapter/$i/a")
        }
        return RoyalRoadReadSession(
            bookId = "rr:$fictionId",
            fictionId = fictionId,
            fictionUrl = "https://www.royalroad.com/fiction/$fictionId/x",
            title = "X",
            author = "A",
            toc = toc,
            startIndex = 0,
            loadedThrough = -1,
            chapters = emptyList(),
            prefetchAhead = prefetchAhead,
            keepBehind = keepBehind,
            pinnedRanges = pinnedRanges,
        )
    }
}
