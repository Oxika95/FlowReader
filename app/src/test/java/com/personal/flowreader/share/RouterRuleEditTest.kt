package com.personal.flowreader.share

import com.personal.flowreader.plugin.api.PluginManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterRuleEditTest {
    private val rr = PluginManifest(
        id = "royalroad",
        name = "Royal Road",
        version = "1.0.0",
        apiVersion = 4,
        shareHosts = listOf("royalroad.com"),
    )
    private val rrRuleId = RouterRules.pluginRuleId("royalroad", "royalroad.com")

    private fun userRule(id: String, order: Int = 0) = RouterRule(
        id = id,
        kind = RouterContentKind.Url,
        hostPattern = "$id.example",
        order = order,
    )

    private fun ids(rules: List<RouterRule>) = rules.map { it.id }

    /** The Import tab read the list before the plugin rule was seeded, then the user saves a rule. */
    private fun storedWithSeededPluginRule(): List<RouterRule> =
        RouterRules.withPluginHosts(RouterRules.seed(), listOf(rr), emptySet()).first

    @Test
    fun replaceKeepsRuleSeededAfterUiSnapshot() {
        val staleSnapshot = RouterRules.seed()
        val edited = staleSnapshot.first { it.id == RouterRules.URL_ANY_ID }.copy(parseUrl = false)

        val result = RouterRuleEdit.apply(storedWithSeededPluginRule(), RouterRuleEdit.Replace(edited))

        assertTrue(rrRuleId in ids(result))
        assertFalse(result.first { it.id == RouterRules.URL_ANY_ID }.parseUrl)
        assertEquals(result.indices.toList(), result.map { it.order })
    }

    @Test
    fun addKeepsRuleSeededAfterUiSnapshot() {
        val result = RouterRuleEdit.apply(
            storedWithSeededPluginRule(),
            RouterRuleEdit.Add(userRule("mine")),
        )
        assertTrue(rrRuleId in ids(result))
        assertEquals("mine", result.last().id)
        assertEquals(result.indices.toList(), result.map { it.order })
    }

    @Test
    fun addWithStoredIdIsIgnored() {
        val stored = RouterRules.seed()
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.Add(stored.first().copy(enabled = false)))
        assertEquals(stored, result)
    }

    @Test
    fun replaceDoesNotResurrectDeletedRule() {
        val stored = RouterRules.seed()
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.Replace(userRule("gone")))
        assertEquals(stored, result)
    }

    @Test
    fun setEnabledTouchesOnlyThatRule() {
        val stored = storedWithSeededPluginRule()
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.SetEnabled(rrRuleId, false))
        assertFalse(result.first { it.id == rrRuleId }.enabled)
        assertTrue(result.filter { it.id != rrRuleId }.all { it.enabled })
        assertEquals(ids(stored), ids(result))
    }

    @Test
    fun deleteKeepsRulesNotNamed() {
        val stored = storedWithSeededPluginRule()
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.Delete(setOf("seed-raw-text")))
        assertFalse("seed-raw-text" in ids(result))
        assertTrue(rrRuleId in ids(result))
        assertEquals(result.indices.toList(), result.map { it.order })
    }

    @Test
    fun reorderKeepsRuleMissingFromUiListBeforeCatchAll() {
        val staleOrder = RouterRules.seed().map { it.id }.reversed()
        val result = RouterRuleEdit.apply(storedWithSeededPluginRule(), RouterRuleEdit.Reorder(staleOrder))

        val out = ids(result)
        assertTrue(rrRuleId in out)
        // The UI put the catch-all first, so the unnamed plugin rule goes ahead of it.
        assertEquals(listOf(rrRuleId, "seed-url-any", "seed-raw-text", "seed-book-files"), out)
        assertEquals(out.indices.toList(), result.map { it.order })
    }

    @Test
    fun reorderDropsIdsDeletedMeanwhile() {
        val stored = RouterRules.seed()
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.Reorder(listOf("ghost") + ids(stored).reversed()))
        assertEquals(ids(stored).reversed(), ids(result))
    }

    @Test
    fun redirectTabSendsRulesToFiles() {
        val tab = RouterLanding.tabChoices(listOf("custom-1")).first { it.id == "custom-1" }
        val stored = RouterRules.seed() + userRule("tabbed", order = 3).copy(destination = tab)
        val result = RouterRuleEdit.apply(stored, RouterRuleEdit.RedirectTabToFiles("custom-1"))
        assertEquals(RouterLanding.Files, result.first { it.id == "tabbed" }.destination)
        assertEquals(ids(stored), ids(result))
    }

    @Test
    fun deletedPluginRuleStaysDeletedAfterEditAndReseed() {
        val (seededRules, seededIds) = RouterRules.withPluginHosts(RouterRules.seed(), listOf(rr), emptySet())
        val afterDelete = RouterRuleEdit.apply(seededRules, RouterRuleEdit.Delete(setOf(rrRuleId)))
        val (again, _) = RouterRules.withPluginHosts(afterDelete, listOf(rr), seededIds)
        assertFalse(rrRuleId in ids(again))
    }
}
