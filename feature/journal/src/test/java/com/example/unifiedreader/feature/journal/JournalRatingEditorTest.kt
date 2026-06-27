package com.example.unifiedreader.feature.journal

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `star row offset maps half steps inside star slots`() {
        assertEquals(1, mappedRating(offsetX = 0f))
        assertEquals(1, mappedRating(offsetX = 19.9f))
        assertEquals(2, mappedRating(offsetX = 20f))
        assertEquals(3, mappedRating(offsetX = 48f))
        assertEquals(9, mappedRating(offsetX = 193f))
        assertEquals(10, mappedRating(offsetX = 212f))
    }

    @Test
    fun `star row offset ignores gutters between star slots`() {
        assertNull(mappedRating(offsetX = 41f))
        assertNull(mappedRating(offsetX = 44f))
        assertNull(mappedRating(offsetX = 47f))
        assertEquals(3, mappedRating(offsetX = 48f))
    }

    @Test
    fun `star row offset clamps outside rating slots`() {
        assertEquals(1, mappedRating(offsetX = -20f))
        assertEquals(10, mappedRating(offsetX = 252f))
        assertNull(mappedRating(offsetX = 10f, starSlotWidth = 0f))
        assertNull(mappedRating(offsetX = 10f, starGapWidth = -1f))
        assertNull(mappedRating(offsetX = 10f, starCount = 0))
    }

    @Test
    fun `rating controls shrink inline stars to avoid dialog overflow`() {
        assertTrue(ratingControlsFitInline(320.dp))
        assertEquals(46.dp, ratingStarSlotSizeFor(maxWidth = 320.dp, includeClearButton = true))

        assertTrue(ratingControlsFitInline(258.dp))
        assertEquals(
            36.4f,
            ratingStarSlotSizeFor(maxWidth = 258.dp, includeClearButton = true).value,
            0.001f,
        )
    }

    @Test
    fun `rating controls stack and shrink stars when inline controls cannot fit`() {
        assertFalse(ratingControlsFitInline(244.dp))

        val stackedSlotSize = ratingStarSlotSizeFor(
            maxWidth = 150.dp,
            includeClearButton = false,
        )
        assertEquals(25.2f, stackedSlotSize.value, 0.001f)
    }

    private fun mappedRating(
        offsetX: Float,
        starSlotWidth: Float = 40f,
        starGapWidth: Float = 8f,
        starCount: Int = 5,
    ): Int? = com.example.unifiedreader.feature.journal.ratingFromStarRowOffset(
        offsetX = offsetX,
        starSlotWidth = starSlotWidth,
        starGapWidth = starGapWidth,
        starCount = starCount,
    )
}
