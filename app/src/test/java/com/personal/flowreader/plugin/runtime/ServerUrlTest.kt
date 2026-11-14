package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test
    fun hostExact() {
        assertEquals("192.168.1.20", ServerUrl.host("http://192.168.1.20:13378"))
        assertEquals("abs.example.com", ServerUrl.host("https://abs.example.com/audiobookshelf"))
        assertNull(ServerUrl.host(""))
        assertNull(ServerUrl.host("http://user:pass@host.example/"))
    }

    @Test
    fun allowsHttpOnlyForHttpUrl() {
        assertTrue(ServerUrl.allowsHttp("http://192.168.1.20:13378"))
        assertFalse(ServerUrl.allowsHttp("https://abs.example.com"))
        assertFalse(ServerUrl.allowsHttp(""))
    }
}
