package com.personal.flowreader.plugin.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCardTest {
    private fun action(id: String, placement: PluginActionPlacement = PluginActionPlacement.Rail) =
        PluginCardAction(id = id, label = id, placement = placement)

    @Test
    fun card_parsesAllSlots() {
        val card = PluginJson.card(
            JSONObject(
                """
                {
                  "stats": [{"icon":"star","value":"4.6","label":"Rating"}, {"icon":"eye","value":""}],
                  "badges": ["Ongoing", " "],
                  "links": [{"label":"Author","url":"https://example.com/a"}, {"label":"Bad","url":"javascript:x"}],
                  "actions": [{"id":"rate","label":"Rate","icon":"star","placement":"footer","toggle":true,"on":true}]
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals(listOf(PluginStat("star", "4.6", "Rating")), card.stats)
        assertEquals(listOf("Ongoing"), card.badges)
        assertEquals(listOf(PluginLink("Author", "https://example.com/a")), card.links)
        val rate = card.actions.single()
        assertEquals(PluginActionPlacement.Footer, rate.placement)
        assertTrue(rate.toggle)
        assertTrue(rate.on)
    }

    @Test
    fun card_absentOrEmpty_isNull() {
        assertNull(PluginJson.card(null))
        assertNull(PluginJson.card(JSONObject("{}")))
        assertNull(PluginJson.card(JSONObject("""{"stats":[{"icon":"star","value":""}]}""")))
    }

    @Test
    fun capped_enforcesLimits() {
        val card = PluginCard(
            stats = (1..10).map { PluginStat("star", "$it") },
            badges = (1..10).map { "b$it" },
            links = (1..10).map { PluginLink("l$it", "https://x/$it") },
            actions = (1..5).map { action("r$it") } + (1..5).map { action("f$it", PluginActionPlacement.Footer) },
        ).capped()
        assertEquals(PluginCardLimits.MAX_STATS, card.stats.size)
        assertEquals(PluginCardLimits.MAX_BADGES, card.badges.size)
        assertEquals(PluginCardLimits.MAX_LINKS, card.links.size)
        assertEquals(listOf("r1", "r2", "f1", "f2"), card.actions.map { it.id })
    }

    @Test
    fun capped_dropsReservedInvalidAndDuplicateIds() {
        val card = PluginCard(
            actions = listOf(
                action("read"),
                action("share"),
                action("list:follow"),
                action("Bad Id"),
                action("rate"),
                action("rate"),
            ),
        ).capped()
        assertEquals(listOf("rate"), card.actions.map { it.id })
    }

    @Test
    fun apply_replacesOnlyPatchedSlots() {
        val card = PluginCard(
            stats = listOf(PluginStat("star", "4.0")),
            badges = listOf("Ongoing"),
            actions = listOf(PluginCardAction("rate", "Rate", toggle = true, on = false)),
        )
        val patched = card.apply(PluginCardPatch(actions = listOf(PluginCardAction("rate", "Rate", toggle = true, on = true))))
        assertEquals(card.stats, patched.stats)
        assertEquals(card.badges, patched.badges)
        assertTrue(patched.actions.single().on)
    }

    @Test
    fun cardPatch_distinguishesMissingFromEmpty() {
        val patch = PluginJson.cardPatch(JSONObject("""{"badges":[]}"""))!!
        assertEquals(emptyList<String>(), patch.badges)
        assertNull(patch.stats)
        assertNull(patch.links)
        assertNull(patch.actions)
    }

    @Test
    fun cardActionResult_parsesToastReloadAndPatch() {
        val result = PluginJson.cardActionResult(
            JSONObject("""{"toast":"Saved","reload":true,"card":{"badges":["Done"]}}"""),
        )
        assertEquals("Saved", result.toast)
        assertTrue(result.reload)
        assertEquals(listOf("Done"), result.patch?.badges)

        val empty = PluginJson.cardActionResult(null)
        assertEquals("", empty.toast)
        assertFalse(empty.reload)
        assertNull(empty.patch)
    }

    @Test
    fun cardJson_roundTrips() {
        val card = PluginCard(
            stats = listOf(PluginStat("star", "4.6", "Rating")),
            badges = listOf("Ongoing"),
            links = listOf(PluginLink("Author", "https://example.com/a")),
            actions = listOf(PluginCardAction("rate", "Rate", "star", PluginActionPlacement.Footer, toggle = true, on = true)),
        )
        assertEquals(card, PluginJson.card(PluginJson.cardToJson(card)))
    }

    @Test
    fun detail_withoutCardHasNone_withCardCarriesIt() {
        val bare = PluginJson.detail(JSONObject("""{"id":"1","title":"T"}"""))
        assertNull(bare.card)
        val carded = PluginJson.detail(JSONObject("""{"id":"1","title":"T","card":{"badges":["Ongoing"]}}"""))
        assertEquals(listOf("Ongoing"), carded.card?.badges)
    }

    @Test
    fun work_parsesDisplayCardSlots() {
        val work = PluginJson.work(
            JSONObject("""{"id":"1","title":"T","badges":["A","B","C","D","E"],"stats":[{"icon":"star","value":"4"}]}"""),
        )!!
        assertEquals(PluginCardLimits.MAX_BADGES, work.badges.size)
        assertEquals("4", work.stats.single().value)
    }
}
