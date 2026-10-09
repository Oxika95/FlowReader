package com.personal.flowreader.plugin.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginVersionUpdatesTest {
    private fun entry(id: String, version: String, apiVersion: Int = 4) = RepoPlugin(
        id = id, name = id.replaceFirstChar { it.uppercase() }, description = "", version = version,
        apiVersion = apiVersion, lang = "en", iconUrl = "", manifestUrl = "", pluginUrl = "",
        manifestSha256 = "", sha256 = "", repoUrl = "",
    )

    private fun repo(vararg plugins: RepoPlugin) = PluginRepo("https://r", RepoIndex("https://r", "R", 4, plugins.toList()))

    @Test
    fun latestById_keepsNewestAcrossRepos() {
        val latest = PluginVersionUpdates.latestById(
            listOf(repo(entry("a", "1.2.0")), repo(entry("a", "1.10.0"), entry("b", "1.0.0")), PluginRepo("https://x", error = "down")),
        )
        assertEquals(listOf("a" to "1.10.0", "b" to "1.0.0"), latest.map { it.id to it.version }.sortedBy { it.first })
    }

    @Test
    fun available_onlyNewerCompatibleInstalledPlugins() {
        val updates = PluginVersionUpdates.available(
            installed = mapOf("a" to "1.0.0", "b" to "2.0.0", "c" to "1.0.0"),
            catalog = listOf(entry("a", "1.1.0"), entry("b", "2.0.0"), entry("c", "1.5.0", apiVersion = 9), entry("d", "1.0.0")),
            compatible = { it.apiVersion == 4 },
        )
        assertEquals(listOf(PluginVersionUpdate("a", "A", "1.0.0", "1.1.0")), updates)
        assertEquals("a@1.1.0", updates.single().key)
    }

    @Test
    fun stillPending_forgetsInstalledAndRemovedVersions() {
        val pending = PluginVersionUpdates.stillPending(
            notified = setOf("a@1.1.0", "b@2.0.0", "gone@1.0.0"),
            installed = mapOf("a" to "1.0.0", "b" to "2.0.0"),
        )
        assertEquals(setOf("a@1.1.0"), pending)
    }
}
