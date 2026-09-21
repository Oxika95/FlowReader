package com.personal.flowreader.library.plugin.royalroad

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyalRoadMembershipStoreTest {
    @Test
    fun roundTripsFollowsMembership() {
        val root = File.createTempFile("rr-follows", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            val works = listOf(
                FictionListItem(
                    title = "Mother of Learning",
                    url = "https://www.royalroad.com/fiction/21220/mother-of-learning",
                    author = "nobody103",
                    latestChapter = "109 Chapters",
                    coverUrl = "https://cdn.example/cover.jpg",
                ),
            )
            RoyalRoadMembershipStore.write(root, works, RoyalRoadListKind.Follow)
            val loaded = RoyalRoadMembershipStore.read(root, RoyalRoadListKind.Follow)
            assertEquals(1, loaded.size)
            assertEquals("Mother of Learning", loaded[0].title)
            assertEquals(works[0].url, loaded[0].url)
            assertEquals("nobody103", loaded[0].author)
            assertEquals("https://cdn.example/cover.jpg", loaded[0].coverUrl)
            assertTrue(RoyalRoadMembershipStore.file(root, RoyalRoadListKind.Follow).exists())

            RoyalRoadMembershipStore.upsert(
                root,
                FictionListItem(
                    title = "Mother of Learning",
                    url = "https://www.royalroad.com/fiction/21220/mother-of-learning",
                    author = "nobody103",
                    latestChapter = "110 Chapters",
                    coverUrl = "https://cdn.example/cover2.jpg",
                ),
                RoyalRoadListKind.Follow,
            )
            RoyalRoadMembershipStore.upsert(
                root,
                FictionListItem(
                    title = "Other",
                    url = "https://www.royalroad.com/fiction/99/other",
                    author = "a",
                    latestChapter = "",
                    coverUrl = "",
                ),
                RoyalRoadListKind.Favorite,
            )
            assertEquals(1, RoyalRoadMembershipStore.read(root, RoyalRoadListKind.Follow).size)
            assertEquals("110 Chapters", RoyalRoadMembershipStore.read(root, RoyalRoadListKind.Follow)[0].latestChapter)
            assertEquals(setOf("rr:99"), RoyalRoadMembershipStore.bookIds(root, RoyalRoadListKind.Favorite))
            assertEquals(setOf("rr:21220"), RoyalRoadMembershipStore.bookIds(root, RoyalRoadListKind.Follow))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun migrateUnlistedToFollowWhenMissingOrEmptyFile() {
        val root = File.createTempFile("rr-migrate", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            RoyalRoadMembershipStore.migrateUnlistedToFollow(root, listOf("rr:1", "rr:2"))
            assertEquals(setOf("rr:1", "rr:2"), RoyalRoadMembershipStore.bookIds(root, RoyalRoadListKind.Follow))
            // Existing non-empty file: do not re-seed new catalog ids automatically.
            RoyalRoadMembershipStore.migrateUnlistedToFollow(root, listOf("rr:1", "rr:2", "rr:3"))
            assertEquals(setOf("rr:1", "rr:2"), RoyalRoadMembershipStore.bookIds(root, RoyalRoadListKind.Follow))

            // Empty file should re-seed.
            RoyalRoadMembershipStore.file(root, RoyalRoadListKind.Follow).writeText("")
            RoyalRoadMembershipStore.migrateUnlistedToFollow(root, listOf("rr:9"))
            assertEquals(setOf("rr:9"), RoyalRoadMembershipStore.bookIds(root, RoyalRoadListKind.Follow))
        } finally {
            root.deleteRecursively()
        }
    }
}
