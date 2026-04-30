package com.example.unifiedreader.feature.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalRatingEditorTest {
    @Test
    fun `rating labels use five star half steps`() {
        assertEquals("No rating", journalRatingLabel(null))
        assertEquals("Rated 0.5", journalRatingLabel(1))
        assertEquals("Rated 1.0", journalRatingLabel(2))
        assertEquals("Rated 3.5", journalRatingLabel(7))
        assertEquals("Rated 5.0", journalRatingLabel(10))
    }

    @Test
    fun `rating labels clamp to supported range`() {
        assertEquals("Rated 0.5", journalRatingLabel(-1))
        assertEquals("Rated 5.0", journalRatingLabel(12))
    }

    @Test
    fun `tap offset maps across ten rating steps`() {
        assertEquals(1, ratingFromTapOffset(offsetX = 0f, width = 300))
        assertEquals(1, ratingFromTapOffset(offsetX = 1f, width = 300))
        assertEquals(5, ratingFromTapOffset(offsetX = 150f, width = 300))
        assertEquals(10, ratingFromTapOffset(offsetX = 300f, width = 300))
    }

    @Test
    fun `tap offset clamps outside rating row`() {
        assertEquals(1, ratingFromTapOffset(offsetX = -20f, width = 300))
        assertEquals(10, ratingFromTapOffset(offsetX = 360f, width = 300))
        assertEquals(null, ratingFromTapOffset(offsetX = 10f, width = 0))
    }
}
