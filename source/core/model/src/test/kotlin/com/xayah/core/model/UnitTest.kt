package com.xayah.core.model

import com.xayah.core.model.util.formatSize
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitTest {
    @Test
    fun testFormatSize() {
        assertEquals("999.00 Bytes", 999.0.formatSize())
        assertEquals("1.00 KB", 1_000.0.formatSize())
        assertEquals("1.00 MB", 1_000_000.0.formatSize())
        assertEquals("1.00 GB", 1_000_000_000.0.formatSize())
    }
}
