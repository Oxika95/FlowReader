package com.personal.flowreader.plugin.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiVersionTest {
    @Test
    fun hostAcceptsOnlyTheCurrentVersion() {
        assertEquals(4, PLUGIN_HOST_API_VERSION)
        assertEquals(PLUGIN_HOST_API_VERSION, PLUGIN_MIN_API_VERSION)
        assertTrue(3 !in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
        assertTrue(5 !in PLUGIN_MIN_API_VERSION..PLUGIN_HOST_API_VERSION)
    }

    @Test
    fun manifest_readsUpdatesCapability() {
        val m = PluginManifest.parse("""{"id":"demo","apiVersion":4,"capabilities":["updates","search"]}""")
        assertTrue(m.has(PluginCapability.Updates))
    }

    @Test
    fun updateInfo_newerWhenCountGrowsOrLatestMoves() {
        val q = PluginUpdateQuery(id = "1", url = "u", chapters = 10, lastChapterUrl = "c10")
        assertTrue(PluginUpdateInfo("1", chapters = 11).isNewer(q))
        assertTrue(PluginUpdateInfo("1", latestUrl = "c11").isNewer(q))
        assertTrue(!PluginUpdateInfo("1", chapters = 10).isNewer(q))
        assertTrue(!PluginUpdateInfo("1", chapters = 9, latestUrl = "c10").isNewer(q))
        assertTrue(!PluginUpdateInfo("1").isNewer(q))
    }

    @Test
    fun readPositions_dropsRowsWithoutIdOrChapter() {
        val arr = JSONArray(
            """[{"id":"1","chapterUrl":" https://s/c/9 "},{"id":"2"},{"chapterUrl":"x"},{"id":" ","chapterUrl":"y"}]""",
        )
        assertEquals(listOf(PluginReadPosition("1", "https://s/c/9")), PluginJson.readPositions(arr))
        assertEquals(emptyList<PluginReadPosition>(), PluginJson.readPositions(null))
    }

    @Test
    fun manifest_defaultsToV1() {
        val m = PluginManifest.parse("""{"id":"demo","name":"Demo"}""")
        assertEquals(1, m.apiVersion)
    }

    @Test
    fun manifest_readsDeclaredVersion() {
        val m = PluginManifest.parse("""{"id":"demo","apiVersion":4}""")
        assertEquals(4, m.apiVersion)
    }

    @Test
    fun manifest_readsWebLoginAndNotifyDefault() {
        val m = PluginManifest.parse(
            """{"id":"demo","apiVersion":4,
               "auth":{"web":{"url":"https://example.com/login","doneCookie":"session_id"}},
               "lists":[{"id":"follow","notifyDefault":true},{"id":"later"}]}""",
        )
        assertEquals(PluginWebLogin("https://example.com/login", "session_id"), m.auth?.web)
        assertTrue(m.auth!!.fields.isEmpty())
        assertTrue(m.list("follow")!!.notifyDefault)
        assertFalse(m.list("later")!!.notifyDefault)
    }

    @Test
    fun manifest_readsBrowseListsAndCapability() {
        val m = PluginManifest.parse(
            """{"id":"demo","apiVersion":4,"capabilities":["browse"],
               "lists":[{"id":"creators","kind":"browse","notifyDefault":true},{"id":"follow","kind":"odd"}]}""",
        )
        assertTrue(m.has(PluginCapability.Browse))
        val creators = m.list("creators")!!
        assertTrue(creators.isBrowse)
        assertFalse(creators.membershipToggle)
        assertFalse(creators.notifyDefault)
        assertEquals(PluginListKind.Stories, m.list("follow")!!.kind)
    }

    @Test
    fun browsePage_keepsKnownSortOrFallsBackToFirst() {
        val page = PluginJson.browsePage(
            JSONObject(
                """{"items":[{"id":"1","title":"A"}],"hasMore":true,
                   "sorts":[{"id":"latest","label":"Latest"},{"id":"title"},{"label":"no id"}],"sort":"title"}""",
            ),
        )
        assertEquals(listOf("1"), page.items.map { it.id })
        assertTrue(page.hasMore)
        assertEquals(listOf(PluginSort("latest", "Latest"), PluginSort("title", "title")), page.sorts)
        assertEquals("title", page.sort)
        assertEquals("latest", PluginJson.browsePage(JSONObject("""{"sorts":[{"id":"latest"}],"sort":"x"}""")).sort)
        assertEquals("", PluginJson.browsePage(JSONArray("""[{"id":"1","title":"A"}]""")).sort)
    }

    @Test
    fun browsePage_readsTabsAndTabContent() {
        val about = PluginJson.browsePage(
            JSONObject(
                """{"tabs":[{"id":"about","label":"About"},{"id":"posts","label":"Posts"},{"label":"x"}],"tab":"nope",
                   "text":"Hi","cover":"https://c/a.jpg","badges":["Active"],
                   "links":[{"label":"Site","url":"https://e.com"},{"url":"ftp://bad"}]}""",
            ),
        )
        assertEquals(listOf(PluginBrowseTab("about", "About"), PluginBrowseTab("posts", "Posts")), about.tabs)
        assertEquals("about", about.tab)
        assertEquals("Hi", about.text)
        assertEquals(listOf("Active"), about.badges)
        assertEquals(listOf("https://e.com"), about.links.map { it.url })
        val posts = PluginJson.browsePage(JSONObject("""{"tabs":[{"id":"posts"}],"tab":"posts","storyId":"42"}"""))
        assertEquals("42", posts.storyId)
        val ref = PluginJson.detail(
            JSONObject("""{"id":"w","chapters":[{"title":"A","url":"u","locked":true},{"title":"B","url":"v"}]}"""),
        ).chapters
        assertEquals(listOf(true, false), ref.map { it.locked })
    }

    @Test
    fun browsePage_readsTonedBadgesGroupsAndSections() {
        val page = PluginJson.browsePage(
            JSONObject(
                """{"items":[{"id":"1","title":"A","group":"Paid","badges":["2 new",{"label":"Paid","tone":"positive"},{"label":"X","tone":"odd"}]}],
                   "badges":[{"label":"Free","tone":"negative"}],
                   "sections":[{"heading":"Your membership","title":"Fan","text":"Perks","links":[{"label":"Manage","url":"https://e.com"}]},{}]}""",
            ),
        )
        val work = page.items.single()
        assertEquals("Paid", work.group)
        assertEquals(listOf("2 new", "Paid", "X"), work.badges)
        assertEquals(mapOf("Paid" to PluginTone.Positive), work.badgeTones)
        assertEquals(mapOf("Free" to PluginTone.Negative), page.badgeTones)
        assertEquals(
            listOf(PluginBrowseSection("Your membership", "Fan", "Perks", listOf(PluginLink("Manage", "https://e.com")))),
            page.sections,
        )
        assertTrue(PluginJson.browsePage(JSONObject("""{"sections":[{"text":"t"}]}""")).isText)
        val folded = PluginJson.browsePage(
            JSONObject("""{"sections":[{"heading":"More","title":"A","collapsed":true},{"title":"B","collapsed":true}]}"""),
        ).sections
        assertEquals(listOf(true, false), folded.map { it.collapsed })
        val grouped = PluginJson.browsePage(
            JSONObject("""{"items":[],"groups":[{"title":"Collections","empty":"None."},{"title":" "},{"title":"Collections"},{"title":"Tags"}]}"""),
        )
        assertEquals(listOf(PluginBrowseGroup("Collections", "None."), PluginBrowseGroup("Tags")), grouped.groups)
        assertFalse(grouped.isText)
    }

    @Test
    fun manifest_dropsInsecureOrIncompleteWebLogin() {
        val http = PluginManifest.parse("""{"id":"demo","auth":{"web":{"url":"http://e.com","doneCookie":"s"}}}""")
        assertNull(http.auth?.web)
        val noCookie = PluginManifest.parse("""{"id":"demo","auth":{"web":{"url":"https://e.com"}}}""")
        assertNull(noCookie.auth?.web)
    }
}
