package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRatingBadgeStateTest {
    @Test
    fun `rating badge state formats half star ratings`() {
        val state = ratingBadgeState(7)

        assertEquals("3.5", state.label)
        assertEquals("Rated 3.5", state.contentDescription)
    }

    @Test
    fun `rating badge state clamps unsupported values`() {
        assertEquals("0.5", ratingBadgeState(0).label)
        assertEquals("5.0", ratingBadgeState(12).label)
    }
}
