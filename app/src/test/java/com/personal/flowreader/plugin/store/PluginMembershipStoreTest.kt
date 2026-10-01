package com.personal.flowreader.plugin.store

import com.personal.flowreader.plugin.api.PluginWork
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMembershipStoreTest {
    @Test
    fun roundTripsAndUpsertsPerList() {
        val root = File.createTempFile("plugin-lists", "").apply {
            delete()
            mkdirs()
        }
        try {
            val mol = PluginWork(
                id = "21220",
                title = "Mother of Learning",
                url = "https://www.royalroad.com/fiction/21220/mother-of-learning",
                author = "nobody103",
                cover = "https://cdn.example/cover.jpg",
                subtitle = "109 Chapters",
            )
            assertFalse(PluginMembershipStore.anyListed(root, listOf("follow")))
            PluginMembershipStore.write(root, "follow", listOf(mol))
            assertEquals(listOf(mol), PluginMembershipStore.read(root, "follow"))
            assertTrue(PluginMembershipStore.anyListed(root, listOf("follow")))

            PluginMembershipStore.upsert(root, "follow", mol.copy(subtitle = "110 Chapters"))
            PluginMembershipStore.upsert(root, "favorite", PluginWork(id = "99", title = "Other\twith tab"))
            assertEquals(1, PluginMembershipStore.read(root, "follow").size)
            assertEquals("110 Chapters", PluginMembershipStore.read(root, "follow")[0].subtitle)
            assertEquals("Other\twith tab", PluginMembershipStore.read(root, "favorite")[0].title)
            assertEquals(setOf("follow"), PluginMembershipStore.listsContaining(root, listOf("follow", "favorite"), "21220"))

            PluginMembershipStore.removeFromAll(root, listOf("follow", "favorite"), "99")
            assertEquals(emptySet<String>(), PluginMembershipStore.workIds(root, "favorite"))
            assertEquals(setOf("21220"), PluginMembershipStore.workIds(root, "follow"))
        } finally {
            root.deleteRecursively()
        }
    }
}
