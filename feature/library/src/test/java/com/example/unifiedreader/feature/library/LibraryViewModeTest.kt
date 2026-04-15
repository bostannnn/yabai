package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModeTest {
    @Test
    fun `grid minimum cell size limits visible card count on wide screens`() {
        assertEquals(180.dp, LibraryGridMinimumCellSize)
    }
}
