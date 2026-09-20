package com.personal.flowreader.library.plugin.royalroad

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.Chapter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoyalRoadSessionStoreTest {
    @Test
    fun roundTripsMetaAndChapterCache() {
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
                author = "Ann",
                toc = listOf(
                    ChapterLink("Start", "https://www.royalroad.com/fiction/1/demo/chapter/9/start"),
                    ChapterLink("Next", "https://www.royalroad.com/fiction/1/demo/chapter/10/next"),
                ),
                startIndex = 1,
                loadedThrough = 1,
                chapters = listOf(
                    Chapter("Next", listOf(Block("b0", BlockKind.Paragraph, "Body text."))),
                ),
            )
            val dir = RoyalRoadSessionStore.dir(root, "1")
            RoyalRoadSessionStore.writeMeta(dir, session)
            RoyalRoadSessionStore.writeChapter(dir, 1, "Next", "Body text.")
            val loaded = RoyalRoadSessionStore.read(root, "1")
            assertNotNull(loaded)
            assertEquals("rr:1", loaded!!.bookId)
            assertEquals(1, loaded.startIndex)
            assertEquals(1, loaded.loadedThrough)
            assertEquals(2, loaded.toc.size)
            assertEquals("Next", loaded.toc[1].title)
            val cached = RoyalRoadSessionStore.readChapterText(dir, 1)
            assertEquals("Next", cached!!.first)
            assertEquals("Body text.", cached.second)
        } finally {
            root.deleteRecursively()
        }
    }
}
