package com.personal.flowreader.plugin.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoIndexTest {
    private val sha = "a".repeat(64)

    @Test
    fun resolvesRelativeUrlsAndSkipsInvalidEntries() {
        val json = """
            {"name":"Official","apiVersion":1,"plugins":[
              {"id":"royalroad","name":"Royal Road","version":"1.2.0","apiVersion":1,
               "manifestUrl":"plugins/royalroad/plugin.json","pluginUrl":"plugins/royalroad/index.js",
               "sha256":"$sha","manifestSha256":"${"B".repeat(64)}"},
              {"id":"Bad Id","manifestUrl":"x","pluginUrl":"y","sha256":"$sha"},
              {"id":"nosha","manifestUrl":"x","pluginUrl":"y"}
            ]}
        """.trimIndent()
        val index = RepoIndex.parse(json, "https://example.github.io/repo/index.json")
        assertEquals("Official", index.name)
        assertEquals(1, index.plugins.size)
        val p = index.plugins.single()
        assertEquals("https://example.github.io/repo/plugins/royalroad/index.js", p.pluginUrl)
        assertEquals("https://example.github.io/repo/plugins/royalroad/plugin.json", p.manifestUrl)
        assertEquals("b".repeat(64), p.manifestSha256)
    }

    @Test
    fun normalizesRepoUrls() {
        assertEquals("https://a.io/index.json", RepoManager.normalize("a.io"))
        assertEquals("https://a.io/r/index.json", RepoManager.normalize("https://a.io/r/"))
        assertEquals("https://a.io/x.json", RepoManager.normalize("https://a.io/x.json"))
        assertTrue(runCatching { RepoManager.normalize("http://a.io/index.json") }.isFailure)
    }

    @Test
    fun sha256Hex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PluginInstaller.sha256Hex("abc".toByteArray()),
        )
    }
}
