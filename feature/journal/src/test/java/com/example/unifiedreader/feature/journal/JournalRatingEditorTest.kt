package com.example.unifiedreader.feature.journal

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `star row offset resolves gutters to nearest star slot`() {
        assertEquals(2, mappedRating(offsetX = 41f))
        assertEquals(2, mappedRating(offsetX = 43.9f))
        assertEquals(3, mappedRating(offsetX = 44f))
        assertEquals(3, mappedRating(offsetX = 47f))
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
    fun `rating star slots use full size when row has enough width`() {
        assertEquals(46.dp, ratingStarSlotSizeFor(maxWidth = 320.dp))
        assertEquals(46.dp, ratingStarSlotSizeFor(maxWidth = 258.dp))
    }

    @Test
    fun `rating star slots shrink when the row is narrow`() {
        assertEquals(
            44f,
            ratingStarSlotSizeFor(maxWidth = 244.dp).value,
            0.001f,
        )

        assertEquals(
            25.2f,
            ratingStarSlotSizeFor(maxWidth = 150.dp).value,
            0.001f,
        )
    }

    @Test
    fun `rating release clears only when a tap repeats the selected value`() {
        assertNull(
            ratingSelectionForRelease(
                selectedRating = 9,
                resolvedRating = 9,
                movedPastTouchSlop = false,
            ),
        )
        assertEquals(
            9,
            ratingSelectionForRelease(
                selectedRating = 9,
                resolvedRating = 9,
                movedPastTouchSlop = true,
            ),
        )
        assertEquals(
            8,
            ratingSelectionForRelease(
                selectedRating = 9,
                resolvedRating = 8,
                movedPastTouchSlop = false,
            ),
        )
    }

    @Test
    fun `the Finished screen's hint says what a tap does, then the pick, then the rating`() {
        assertEquals("Tap to rate", journalLeadingRatingHint(selectedRating = null, previewRating = null))
        assertEquals("3.5", journalLeadingRatingHint(selectedRating = null, previewRating = 7))
        assertEquals("4.0", journalLeadingRatingHint(selectedRating = 7, previewRating = 8))
        assertEquals("Rated 3.5", journalLeadingRatingHint(selectedRating = 7, previewRating = null))
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
