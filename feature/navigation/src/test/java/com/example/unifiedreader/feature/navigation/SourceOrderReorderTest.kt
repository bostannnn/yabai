package com.example.unifiedreader.feature.navigation

import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceOrderReorderTest {
    @Test
    fun `a full order is normalized so every source keeps a slot`() {
        val normalized = normalizeSourceOrder(listOf(SourceType.LOCAL, SourceType.STUMP))

        assertEquals(SourceType.LOCAL, normalized[0])
        assertEquals(SourceType.STUMP, normalized[1])
        assertEquals(SourceType.entries.size, normalized.size)
        assertEquals(SourceType.entries.toSet(), normalized.toSet())
    }
}
