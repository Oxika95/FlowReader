package com.personal.flowreader.ui.library

import com.personal.flowreader.share.ParseRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrawlCountTest {
    @Test
    fun acceptsOneToMax() {
        assertEquals(1, CrawlCount.parse("1"))
        assertEquals(10, CrawlCount.parse(" 10 "))
        assertEquals(ParseRule.MAX_CRAWL_LIMIT, CrawlCount.parse(ParseRule.MAX_CRAWL_LIMIT.toString()))
    }

    @Test
    fun rejectsEmptyZeroAndOverMax() {
        assertNull(CrawlCount.parse(""))
        assertNull(CrawlCount.parse("0"))
        assertNull(CrawlCount.parse((ParseRule.MAX_CRAWL_LIMIT + 1).toString()))
    }
}
