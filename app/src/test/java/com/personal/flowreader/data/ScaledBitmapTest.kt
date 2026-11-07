package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaledBitmapTest {
    @Test fun smallImageIsNotSubsampled() = assertEquals(1, ScaledBitmap.sampleSize(300, 400, 512))

    @Test fun exactFitIsNotSubsampled() = assertEquals(1, ScaledBitmap.sampleSize(512, 200, 512))

    @Test fun longestEdgeDecidesTheSample() {
        assertEquals(2, ScaledBitmap.sampleSize(600, 1000, 512))
        assertEquals(2, ScaledBitmap.sampleSize(1000, 600, 512))
    }

    @Test fun largeImageUsesPowerOfTwo() = assertEquals(8, ScaledBitmap.sampleSize(4000, 3000, 512))
}
