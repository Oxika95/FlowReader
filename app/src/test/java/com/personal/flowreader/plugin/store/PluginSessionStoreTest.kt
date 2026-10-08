package com.personal.flowreader.plugin.store

import com.personal.flowreader.plugin.api.PluginChapterRef
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginSessionStoreTest {
    @Test
    fun metaAndSplashRoundTripWithoutBodies() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "1")
            PluginSessionStore.writeMeta(dir, session("1", tocSize = 2))
            PluginSessionStore.writeSplash(
                dir,
                PluginSplashMeta(
                    synopsis = "Hello",
                    tags = listOf("Fantasy"),
                    views = 10,
                    rating = "4 / 5",
                    status = "Ongoing",
                    cover = "https://cdn.example/cover.jpg",
                ),
            )
            val loaded = PluginSessionStore.read(root, "1", "demo", "d:1")!!
            assertEquals("demo", loaded.pluginId)
            assertEquals(2, loaded.toc.size)
            assertEquals("Ch 1", loaded.toc[0].title)
            assertEquals(-1, loaded.loadedThrough)
            assertEquals(PluginCachePolicy.DEFAULT_LEVEL, loaded.cacheLevel)
            assertFalse(loaded.cleanup)
            assertTrue(loaded.pinnedRanges.isEmpty())
            assertFalse(File(dir, "c/0.txt").exists())
            val splash = PluginSessionStore.readSplash(dir)!!
            assertEquals("Hello", splash.synopsis)
            assertEquals(listOf("Fantasy"), splash.tags)
            assertEquals(10L, splash.views)
            assertEquals("4 / 5", splash.rating)
        }
    }

    @Test
    fun readsLegacyBuiltInRoyalRoadFiles() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "21220").apply { mkdirs() }
            File(dir, "meta.txt").writeText(
                """
                v1
                bookId=rr:21220
                fictionId=21220
                fictionUrl=https://www.royalroad.com/fiction/21220/mol
                title=Mother of Learning
                author=nobody103
                startIndex=3
                loadedThrough=4
                prefetchAhead=2
                keepBehind=1
                pinnedRanges=0-1
                """.trimIndent(),
            )
            File(dir, "toc.txt").writeText("One\thttps://x/1\nTwo\thttps://x/2")
            File(dir, "splash.txt").writeText("v1\nratingLabel=4.5 / 5\ncoverUrl=https://cdn/c.jpg\n")
            val loaded = PluginSessionStore.read(root, "21220", "royalroad", "rr:21220")!!
            assertEquals("rr:21220", loaded.bookId)
            assertEquals("royalroad", loaded.pluginId)
            assertEquals("21220", loaded.workId)
            assertEquals("https://www.royalroad.com/fiction/21220/mol", loaded.workUrl)
            assertEquals(3, loaded.startIndex)
            assertEquals(2, loaded.cacheLevel)
            assertFalse(loaded.cleanup)
            assertEquals(listOf(0..1), loaded.pinnedRanges)
            val splash = PluginSessionStore.readSplash(dir)!!
            assertEquals("4.5 / 5", splash.rating)
            assertEquals("https://cdn/c.jpg", splash.cover)
        }
    }

    @Test
    fun tocRefreshPreservesCachedBodies() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "9")
            val first = session("9", tocSize = 1).copy(loadedThrough = 0)
            PluginSessionStore.writeMeta(dir, first)
            PluginSessionStore.writeChapter(dir, 0, "Ch 1", "body text")
            PluginSessionStore.writeMeta(dir, session("9", tocSize = 2).copy(loadedThrough = 0))
            assertEquals("body text", PluginSessionStore.readChapterText(dir, 0)!!.second)
            assertEquals(2, PluginSessionStore.read(root, "9", "demo", "d:9")!!.toc.size)
            assertEquals(1, PluginSessionStore.cachedChapterCount(dir, 2))
        }
    }

    @Test
    fun cachePolicyRoundTripsInMeta() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "3")
            PluginSessionStore.writeMeta(
                dir,
                session("3", tocSize = 10).copy(cacheLevel = 4, cleanup = true, pinnedRanges = listOf(0..2, 8..9)),
            )
            val loaded = PluginSessionStore.read(root, "3", "demo", "d:3")!!
            assertEquals(4, loaded.cacheLevel)
            assertTrue(loaded.cleanup)
            assertEquals(listOf(0..2, 8..9), loaded.pinnedRanges)
        }
    }

    @Test
    fun legacyPartialPinsAreDropped() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "7").apply { mkdirs() }
            File(dir, "meta.txt").writeText("v2\nprefetchAhead=10\nkeepBehind=10\npinnedRanges=99-108\n")
            File(dir, "toc.txt").writeText((1..200).joinToString("\n") { "C$it\thttps://x/$it" })
            val loaded = PluginSessionStore.read(root, "7", "demo", "d:7")!!
            assertEquals(10, loaded.cacheLevel)
            assertTrue(loaded.pinnedRanges.isEmpty())
        }
    }

    @Test
    fun fetchIsAheadOnlyFromLocus() {
        val fetch = PluginSessionStore.fetchIndices(
            locus = 99,
            tocSize = 500,
            policy = PluginCachePolicy(cacheLevel = 10),
        )
        assertEquals((99..109).toSet(), fetch)
    }

    @Test
    fun fetchAddsPinsAndClipsToToc() {
        val fetch = PluginSessionStore.fetchIndices(
            locus = 8,
            tocSize = 10,
            policy = PluginCachePolicy(cacheLevel = 5, pinnedRanges = listOf(0..1)),
        )
        assertEquals(setOf(8, 9, 0, 1), fetch)
    }

    @Test
    fun pruneDoesNothingWithCleanupOff() {
        val prune = PluginSessionStore.pruneIndices((0..20).toList(), locus = 15, tocSize = 21, PluginCachePolicy(cacheLevel = 2))
        assertTrue(prune.isEmpty())
    }

    @Test
    fun pruneDeletesOlderThanLevelExceptPins() {
        val policy = PluginCachePolicy(cacheLevel = 10, cleanup = true, pinnedRanges = listOf(0..2))
        val prune = PluginSessionStore.pruneIndices((0..120).toList(), locus = 115, tocSize = 200, policy)
        assertEquals((3..104).toSet(), prune)
    }

    @Test
    fun pruneAlwaysDropsChaptersPastToc() {
        val prune = PluginSessionStore.pruneIndices(listOf(1, 5, 6), locus = 1, tocSize = 5, PluginCachePolicy())
        assertEquals(setOf(5, 6), prune)
    }

    @Test
    fun deleteChaptersRemovesFiles() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "5")
            PluginSessionStore.writeMeta(dir, session("5", tocSize = 6))
            for (i in 0..5) PluginSessionStore.writeChapter(dir, i, "C$i", "body $i")
            assertEquals(2, PluginSessionStore.deleteChapters(dir, listOf(0, 4)))
            assertEquals(setOf(1, 2, 3, 5), PluginSessionStore.cachedChapterIndices(dir, 6))
            assertEquals(setOf(1, 2, 3, 5), PluginSessionStore.chapterFileIndices(dir))
        }
    }

    @Test
    fun pinnedRangesMergeAndClip() {
        val merged = PluginSessionStore.mergePinnedRanges(listOf(0..2, 2..4, 10..12, 7..7))
        assertEquals(listOf(0..4, 7..7, 10..12), merged)
        assertEquals(listOf(0..4, 7..7), PluginSessionStore.clipPinnedRanges(merged, tocSize = 8))
    }

    @Test
    fun unsafeWorkIdsGetHashedFolders() {
        assertEquals("21220", PluginSessionStore.workDirName("21220"))
        val hashed = PluginSessionStore.workDirName("a/b:c")
        assertTrue(hashed.startsWith("w_"))
        assertEquals(hashed, PluginSessionStore.workDirName("a/b:c"))
    }

    private fun session(workId: String, tocSize: Int) = PluginReadSession(
        bookId = "d:$workId",
        pluginId = "demo",
        workId = workId,
        workUrl = "https://example.com/work/$workId",
        title = "X",
        author = "A",
        toc = (1..tocSize).map { PluginChapterRef("Ch $it", "https://example.com/work/$workId/$it") },
        startIndex = 0,
        loadedThrough = -1,
        chapters = emptyList(),
    )

    private fun withRoot(block: (File) -> Unit) {
        val root = File.createTempFile("plugin-session", "").apply {
            delete()
            mkdirs()
        }
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
