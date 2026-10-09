package com.personal.flowreader.plugin.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressReconcileTest {
    private val toc = (1..20).associate { "https://site/c/$it/slug" to it - 1 }
    private fun ch(n: Int) = "https://site/c/$n/slug"
    private fun decide(local: String?, remote: String?, baseline: String?) =
        ProgressReconcile.decide(local, remote, baseline) { url ->
            toc[url] ?: Regex("/c/(\\d+)/").find(url)?.groupValues?.get(1)?.toInt()?.takeIf { it <= 20 }?.minus(1)
        }

    @Test
    fun unknownRemoteChangesNothing() {
        assertEquals(ProgressDecision(ProgressAction.NoOp, ch(3)), decide(ch(5), null, ch(3)))
    }

    @Test
    fun remoteChapterOutsideTheTocWaits() {
        assertEquals(ProgressDecision(ProgressAction.NoOp, ch(3)), decide(ch(3), ch(25), ch(3)))
    }

    @Test
    fun noLocalPositionTakesTheSite() {
        assertEquals(ProgressDecision(ProgressAction.ApplyRemote(ch(7)), ch(7)), decide(null, ch(7), ch(2)))
    }

    @Test
    fun sameChapterIsInSyncEvenWithAnotherSlug() {
        assertEquals(
            ProgressDecision(ProgressAction.NoOp, ch(4)),
            decide(ch(4), "https://site/c/4/renamed", ch(2)),
        )
    }

    @Test
    fun onlyTheSiteMovedApplies() {
        assertEquals(ProgressDecision(ProgressAction.ApplyRemote(ch(9)), ch(9)), decide(ch(4), ch(9), ch(4)))
    }

    @Test
    fun onlyTheAppMovedPushes() {
        assertEquals(ProgressDecision(ProgressAction.PushLocal(ch(9)), ch(9)), decide(ch(9), ch(4), ch(4)))
    }

    @Test
    fun movingBackwardOnOneSideStillApplies() {
        assertEquals(ProgressDecision(ProgressAction.ApplyRemote(ch(2)), ch(2)), decide(ch(6), ch(2), ch(6)))
    }

    @Test
    fun bothMovedIsAConflictAndKeepsTheBaseline() {
        assertEquals(
            ProgressDecision(ProgressAction.Conflict(ch(12), ch(15)), ch(10)),
            decide(ch(12), ch(15), ch(10)),
        )
    }

    @Test
    fun tocIndexToleratesHostCaseTrailingSlashAndSlugChanges() {
        val urls = listOf(
            "https://www.site.com/fiction/1/demo/chapter/100/first",
            "https://www.site.com/fiction/1/demo/chapter/200/second",
        )
        assertEquals(1, ProgressReconcile.tocIndex(urls, urls[1]))
        assertEquals(1, ProgressReconcile.tocIndex(urls, "http://Site.com/fiction/1/demo/chapter/200/second/?x=1"))
        assertEquals(0, ProgressReconcile.tocIndex(urls, "https://www.site.com/fiction/1/demo/chapter/100/renamed"))
        assertEquals(null, ProgressReconcile.tocIndex(urls, "https://www.site.com/fiction/1/demo/chapter/300/third"))
        assertEquals(null, ProgressReconcile.tocIndex(urls, "not a url"))
    }

    @Test
    fun firstSyncTakesTheFurtherChapterWithoutPrompting() {
        assertEquals(ProgressDecision(ProgressAction.ApplyRemote(ch(8)), ch(8)), decide(ch(3), ch(8), null))
        assertEquals(ProgressDecision(ProgressAction.PushLocal(ch(8)), ch(8)), decide(ch(8), ch(3), null))
    }
}
