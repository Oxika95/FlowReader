package com.personal.flowreader.plugin.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginClipOwnershipTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun newRequestId_isThirtyTwoLowerHex() {
        val id = PluginClipOwnership.newRequestId()
        assertTrue(PluginClipOwnership.isValidRequestId(id))
        assertEquals(32, id.length)
        assertFalse(PluginClipOwnership.isValidRequestId("req_not_hex"))
        assertFalse(PluginClipOwnership.isValidRequestId("A".repeat(32)))
    }

    @Test
    fun acceptAudioPath_requiresMatchingNameUnderAudioDir() {
        val dir = tmp.newFolder("ws-audio")
        val id = PluginClipOwnership.newRequestId()
        val ok = PluginClipOwnership.expectedAudioFile(dir, id)
        ok.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(ok.canonicalFile, PluginClipOwnership.acceptAudioPath(ok.absolutePath, id, dir))

        val foreign = File(dir, "clip-123.mp3").also { it.writeBytes(byteArrayOf(9)) }
        try {
            PluginClipOwnership.acceptAudioPath(foreign.absolutePath, id, dir)
            fail("expected reject")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun acceptAudioPath_rejectsEmptyFile() {
        val dir = tmp.newFolder("ws-audio")
        val id = PluginClipOwnership.newRequestId()
        val empty = PluginClipOwnership.expectedAudioFile(dir, id).also { it.writeBytes(byteArrayOf()) }
        try {
            PluginClipOwnership.acceptAudioPath(empty.absolutePath, id, dir)
            fail("expected reject")
        } catch (_: IllegalArgumentException) {
        }
    }
}
