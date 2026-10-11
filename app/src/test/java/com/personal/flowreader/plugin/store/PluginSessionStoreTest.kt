package com.personal.flowreader.plugin.store

import com.personal.flowreader.plugin.api.PluginChapterRef
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                    cover = "https://cdn.example/cover.jpg",
                ),
            )
            val loaded = PluginSessionStore.read(root, "1")!!
            assertEquals("demo", loaded.pluginId)
            assertEquals("X", loaded.title)
            assertEquals(2, loaded.toc.size)
            assertEquals("Ch 1", loaded.toc[0].title)
            assertFalse(File(dir, "c").exists())
            val splash = PluginSessionStore.readSplash(dir)!!
            assertEquals("Hello", splash.synopsis)
            assertEquals(listOf("Fantasy"), splash.tags)
            assertEquals("https://cdn.example/cover.jpg", splash.cover)
        }
    }

    @Test
    fun lockedChaptersRoundTripInToc() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "4")
            val toc = listOf(PluginChapterRef("A\tB", "a", locked = true), PluginChapterRef("C", "c"))
            PluginSessionStore.writeMeta(dir, session("4", tocSize = 1).copy(toc = toc))
            val loaded = PluginSessionStore.read(root, "4")!!.toc
            assertEquals(listOf("A\tB" to true, "C" to false), loaded.map { it.title to it.locked })
        }
    }

    @Test
    fun bodiesFollowTheirUrlAcrossTocChanges() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "r")
            val old = listOf(ref("a"), ref("b"), ref("c"))
            old.forEach { PluginSessionStore.writeChapter(dir, it.url, it.title, "body ${it.title}") }
            val fresh = listOf(ref("new"), ref("a"), ref("c"))
            assertEquals(setOf(1, 2), PluginSessionStore.cachedChapterIndices(dir, fresh))
            assertEquals("A" to "body A", PluginSessionStore.readChapterText(dir, fresh[1].url))
            assertEquals("body C", PluginSessionStore.readChapterText(dir, old[2].url)!!.second)
            assertNull(PluginSessionStore.readChapterText(dir, fresh[0].url))
        }
    }

    @Test
    fun deleteChaptersAlsoDropsBodiesThatLeftTheToc() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "5")
            val toc = (0..5).map { ref("u$it") }
            toc.forEach { PluginSessionStore.writeChapter(dir, it.url, it.title, "body") }
            PluginSessionStore.writeChapter(dir, "gone", "Gone", "body")
            assertEquals(3, PluginSessionStore.deleteChapters(dir, toc, listOf(0, 4)))
            assertEquals(setOf(1, 2, 3, 5), PluginSessionStore.cachedChapterIndices(dir, toc))
            assertFalse(PluginSessionStore.hasChapter(dir, "gone"))
        }
    }

    @Test
    fun fetchIsAheadOnlyFromLocus() {
        val fetch = PluginSessionStore.fetchIndices(locus = 99, tocSize = 500, policy = PluginCachePolicy(cacheLevel = 10))
        assertEquals((99..109).toSet(), fetch)
    }

    @Test
    fun keepAllFetchesEveryChapter() {
        val fetch = PluginSessionStore.fetchIndices(locus = 8, tocSize = 10, policy = PluginCachePolicy(cacheLevel = 1, keepAll = true))
        assertEquals((0..9).toSet(), fetch)
    }

    @Test
    fun pruneDoesNothingWithCleanupOff() {
        val prune = PluginSessionStore.pruneIndices((0..20).toList(), locus = 15, PluginCachePolicy(cacheLevel = 2))
        assertTrue(prune.isEmpty())
    }

    @Test
    fun pruneDeletesOlderThanLevel() {
        val policy = PluginCachePolicy(cacheLevel = 10, cleanup = true)
        assertEquals((0..104).toSet(), PluginSessionStore.pruneIndices((0..120).toList(), locus = 115, policy))
    }

    @Test
    fun keepAllBlocksCleanup() {
        val policy = PluginCachePolicy(cacheLevel = 1, cleanup = true, keepAll = true)
        assertTrue(PluginSessionStore.pruneIndices((0..20).toList(), locus = 15, policy).isEmpty())
    }

    @Test
    fun unsafeWorkIdsGetHashedFolders() {
        assertEquals("21220", PluginSessionStore.workDirName("21220"))
        val hashed = PluginSessionStore.workDirName("a/b:c")
        assertTrue(hashed.startsWith("w_"))
        assertEquals(hashed, PluginSessionStore.workDirName("a/b:c"))
    }

    @Test
    fun savedChapterPrefersHref() {
        val toc = listOf(ref("a"), ref("b"), ref("c"))
        assertEquals(2, PluginSessionStore.savedChapter(toc, "https://example.com/c", 0))
        assertEquals(1, PluginSessionStore.savedChapter(toc, "gone", 1))
        assertEquals(2, PluginSessionStore.savedChapter(toc, "", 9))
    }

    @Test
    fun displayTitlePrefersRename() {
        assertEquals("X", session("1", 1).displayTitle)
        assertEquals("Mine", session("1", 1).copy(titleOverride = "Mine").displayTitle)
    }

    @Test
    fun readRenamesIndexNamedBodiesToUrlKeys() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "L")
            val s = session("L", tocSize = 3)
            PluginSessionStore.writeMeta(dir, s)
            File(dir, "c").mkdirs()
            File(dir, "c/0.txt").writeText("Ch 1\n\nfirst")
            File(dir, "c/2.txt").writeText("Ch 3\n\nthird")
            File(dir, "c/9.txt").writeText("gone\n\nout of toc")
            val loaded = PluginSessionStore.read(root, "L")!!
            assertEquals(setOf(0, 2), PluginSessionStore.cachedChapterIndices(dir, loaded.toc))
            assertEquals("Ch 3" to "third", PluginSessionStore.readChapterText(dir, s.toc[2].url))
            assertFalse(File(dir, "c/0.txt").exists())
            assertEquals(1, PluginSessionStore.deleteChapters(dir, loaded.toc, emptySet()))
            assertEquals(setOf(0, 2), PluginSessionStore.cachedChapterIndices(dir, loaded.toc))
        }
    }

    @Test
    fun legacySettingsMapPinsToKeepAllOrNoCleanup() {
        assertNull(PluginSessionStore.legacySettings(mapOf("bookId" to "d:1"), 5))
        val plain = PluginSessionStore.legacySettings(
            mapOf("cacheLevel" to "3", "cleanup" to "true", "pinnedRanges" to "", "notify" to "false"),
            5,
        )!!
        assertEquals(LegacyStorySettings(3, cleanup = true, keepAll = false, notify = false), plain)
        val all = PluginSessionStore.legacySettings(mapOf("cacheLevel" to "1", "cleanup" to "true", "pinnedRanges" to "0-4"), 5)!!
        assertTrue(all.keepAll)
        assertFalse(all.cleanup)
        val some = PluginSessionStore.legacySettings(mapOf("cacheLevel" to "1", "cleanup" to "true", "pinnedRanges" to "1-2"), 5)!!
        assertFalse(some.keepAll)
        assertFalse(some.cleanup)
        assertNull(some.notify)
    }

    @Test
    fun currentMetaHasNoLegacySettings() {
        withRoot { root ->
            val dir = PluginSessionStore.dir(root, "n")
            PluginSessionStore.writeMeta(dir, session("n", tocSize = 2))
            assertNull(PluginSessionStore.readLegacySettings(dir))
        }
    }

    private fun ref(id: String) = PluginChapterRef(id.uppercase(), "https://example.com/$id")

    private fun session(workId: String, tocSize: Int) = PluginReadSession(
        bookId = "d:$workId",
        pluginId = "demo",
        workId = workId,
        workUrl = "https://example.com/work/$workId",
        title = "X",
        author = "A",
        toc = (1..tocSize).map { PluginChapterRef("Ch $it", "https://example.com/work/$workId/$it") },
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
