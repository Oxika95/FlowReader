package com.personal.flowreader.ui.design.card.media

import com.personal.flowreader.plugin.api.PluginActionPlacement
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginCardAction
import com.personal.flowreader.plugin.api.PluginLink
import com.personal.flowreader.plugin.api.PluginList
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCardAdaptersTest {
    private val manifest = PluginManifest(
        id = "royalroad",
        name = "Royal Road",
        version = "1",
        apiVersion = 2,
        lists = listOf(
            PluginList("follow", "Follow", icon = "add"),
            PluginList("favorite", "Favorite", icon = "favorite"),
            PluginList("history", "History", membershipToggle = false),
        ),
    )

    private fun info(card: PluginCard? = null, listedIn: Set<String> = emptySet(), chapters: Int = 10) = PluginMediaInfo(
        title = "Story",
        author = "Author",
        workUrl = "https://example.com/story",
        synopsis = "Synopsis",
        tags = listOf("Fantasy"),
        status = "ONGOING",
        rating = "4.61",
        views = 1_234_567,
        chapterCount = chapters,
        downloadedCount = 3,
        cachedIndices = setOf(0, 1, 2),
        locus = 1,
        cacheLevel = 5,
        listedIn = listedIn,
        card = card,
    )

    private fun model(info: PluginMediaInfo, busy: Boolean = false) =
        PluginMediaCardAdapter.model(manifest, info, busy = busy, downloadProgress = null, error = null)

    @Test
    fun v1_mapsRatingViewsStatus() {
        val m = model(info())
        assertEquals(listOf("star", "eye", "pages"), m.stats.map { it.icon })
        assertEquals("4.61", m.stats[0].value)
        assertEquals("10", m.stats.last().value)
        assertEquals(listOf("ONGOING"), m.badges)
        assertTrue(m.links.isEmpty())
    }

    @Test
    fun v2_cardSlotsReplaceV1Mapping_chaptersAlwaysAppended() {
        val card = PluginCard(
            stats = listOf(PluginStat("followers", "12k", "Followers")),
            badges = listOf("Completed"),
            links = listOf(PluginLink("Author", "https://example.com/a")),
        )
        val m = model(info(card = card))
        assertEquals(listOf("followers", "pages"), m.stats.map { it.icon })
        assertEquals(listOf("Completed"), m.badges)
        assertEquals(listOf(MediaLink("Author", "https://example.com/a")), m.links)
    }

    @Test
    fun rail_hostListTogglesThenShareThenPluginActions() {
        val card = PluginCard(
            actions = listOf(
                PluginCardAction("rate", "Rate", "star", PluginActionPlacement.Rail, toggle = true),
                PluginCardAction("later", "Later", "clock", PluginActionPlacement.Rail),
                PluginCardAction("third", "Third", placement = PluginActionPlacement.Rail),
            ),
        )
        val m = model(info(card = card, listedIn = setOf("favorite")))
        assertEquals(listOf("list:follow", "list:favorite", "share", "rate", "later"), m.rail.map { it.id })
        assertFalse(m.rail[0].on)
        assertTrue(m.rail[1].on)
        assertEquals(MediaActionOwner.Host, m.rail[2].owner)
        assertEquals(MediaActionOwner.Plugin, m.rail[3].owner)
        assertEquals(MediaActionKind.Toggle, m.rail[3].kind)
        assertEquals(MediaActionKind.Icon, m.rail[4].kind)
    }

    @Test
    fun footer_hostActionsWrapPluginActions_readLast() {
        val card = PluginCard(
            actions = listOf(
                PluginCardAction("comments", "Comments", placement = PluginActionPlacement.Footer),
                PluginCardAction("read", "Hijack", placement = PluginActionPlacement.Footer),
            ),
        )
        val m = model(info(card = card))
        assertEquals(listOf("download", "refresh", "delete", "comments", "read"), m.footer.map { it.id })
        val read = m.footer.last()
        assertEquals(MediaActionOwner.Host, read.owner)
        assertEquals(MediaActionKind.Primary, read.kind)
        assertEquals(MediaActionKind.Destructive, m.footer[2].kind)
    }

    @Test
    fun busyOrNoChapters_disablesActions() {
        val busy = model(info(card = PluginCard(actions = listOf(PluginCardAction("rate", "Rate")))), busy = true)
        assertTrue(busy.footer.none { it.enabled })
        assertFalse(busy.rail.first { it.id == "rate" }.enabled)

        val empty = model(info(chapters = 0))
        assertFalse(empty.footer.first { it.id == MediaActionIds.READ }.enabled)
        assertFalse(empty.footer.first { it.id == MediaActionIds.DOWNLOAD }.enabled)
        assertTrue(empty.footer.first { it.id == MediaActionIds.REFRESH }.enabled)
    }

    @Test
    fun status_reflectsDownloadProgress() {
        val idle = model(info())
        assertEquals("Cached 3 / 10 chapters · cache level 5", idle.status)
        val downloading = PluginMediaCardAdapter.model(manifest, info(), busy = true, downloadProgress = 4 to 10, error = null)
        assertEquals("Downloading 4 / 10", downloading.status)
    }

    @Test
    fun listIds_roundTrip() {
        assertEquals("follow", MediaActionIds.listIdOf(MediaActionIds.list("follow")))
        assertEquals(null, MediaActionIds.listIdOf("share"))
    }

    @Test
    fun file_statsBadgesFooter() {
        val m = FileMediaCardAdapter.model(
            FileMediaInfo(
                title = "Book",
                sourceLabel = "Linked",
                linked = true,
                lastRead = "2 days ago",
                progress = 0.426f,
                format = "epub",
                sizeBytes = 2_621_440,
            ),
            busy = false,
        )
        assertEquals(listOf("42%", "EPUB", "2.5 MB"), m.stats.map { it.value })
        assertEquals(listOf("Linked"), m.badges)
        assertEquals("Read 2 days ago", m.subtitle)
        assertEquals(listOf(MediaActionIds.SHARE, MediaActionIds.REMOVE, MediaActionIds.OPEN), m.footer.map { it.id })
    }

    @Test
    fun withoutHostActions_dropsHostIdsOnly() {
        val card = PluginCard(
            actions = listOf(
                PluginCardAction("comments", "Comments", placement = PluginActionPlacement.Footer),
                PluginCardAction("later", "Later", placement = PluginActionPlacement.Rail),
            ),
        )
        val m = model(info(card = card)).withoutHostActions(
            setOf(MediaActionIds.READ, MediaActionIds.DELETE, "later"),
        )
        assertEquals(listOf("download", "refresh", "comments"), m.footer.map { it.id })
        assertEquals(listOf("list:follow", "list:favorite", "share", "later"), m.rail.map { it.id })
    }

    @Test
    fun formatBytes_units() {
        assertEquals("512 B", FileMediaCardAdapter.formatBytes(512))
        assertEquals("2 KB", FileMediaCardAdapter.formatBytes(2_048))
        assertEquals("1.5 MB", FileMediaCardAdapter.formatBytes(1_572_864))
    }
}
