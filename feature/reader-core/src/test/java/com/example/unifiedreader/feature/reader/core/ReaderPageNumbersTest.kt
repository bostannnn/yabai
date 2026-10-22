package com.example.unifiedreader.feature.reader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageNumbersTest {
    @Test
    fun `a number is centred at its fraction of the width`() {
        assertEquals(450, readerPageNumberLeft(centerFraction = 0.5f, containerWidth = 1000, labelWidth = 100))
        assertEquals(200, readerPageNumberLeft(centerFraction = 0.25f, containerWidth = 1000, labelWidth = 100))
        assertEquals(700, readerPageNumberLeft(centerFraction = 0.75f, containerWidth = 1000, labelWidth = 100))
    }

    @Test
    fun `a number near an edge stays on screen`() {
        assertEquals(0, readerPageNumberLeft(centerFraction = 0.01f, containerWidth = 1000, labelWidth = 100))
        assertEquals(900, readerPageNumberLeft(centerFraction = 0.99f, containerWidth = 1000, labelWidth = 100))
    }

    @Test
    fun `a label wider than the container starts at its left edge`() {
        assertEquals(0, readerPageNumberLeft(centerFraction = 0.5f, containerWidth = 80, labelWidth = 100))
    }
}
