package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryProgressTest {
    @Test
    fun `percentages round as the readers do`() {
        // Page 78 of 202 is 38.6%: the comic reader says 39%, so the card does too.
        assertEquals("39%", ItemProgressSummary(label = "", fraction = 78f / 202f).percentLabel)
        assertEquals("0%", ItemProgressSummary(label = "", fraction = 0f).percentLabel)
        assertEquals("100%", ItemProgressSummary(label = "", fraction = 1f).percentLabel)
    }
}
