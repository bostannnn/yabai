package com.example.unifiedreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppWindowSizeTest {

    @Test
    fun `width classes start at 600 and 840 dp`() {
        assertEquals(AppWindowWidth.Compact, AppWindowWidth.of(411))
        assertEquals(AppWindowWidth.Compact, AppWindowWidth.of(599))
        assertEquals(AppWindowWidth.Medium, AppWindowWidth.of(600))
        assertEquals(AppWindowWidth.Medium, AppWindowWidth.of(800))
        assertEquals(AppWindowWidth.Medium, AppWindowWidth.of(839))
        assertEquals(AppWindowWidth.Expanded, AppWindowWidth.of(840))
        assertEquals(AppWindowWidth.Expanded, AppWindowWidth.of(1292))
    }

    @Test
    fun `height classes start at 480 and 900 dp`() {
        assertEquals(AppWindowHeight.Compact, AppWindowHeight.of(411))
        assertEquals(AppWindowHeight.Compact, AppWindowHeight.of(479))
        assertEquals(AppWindowHeight.Medium, AppWindowHeight.of(480))
        assertEquals(AppWindowHeight.Medium, AppWindowHeight.of(899))
        assertEquals(AppWindowHeight.Expanded, AppWindowHeight.of(900))
    }

    @Test
    fun `classes compare from narrow to wide`() {
        assertTrue(AppWindowWidth.Compact < AppWindowWidth.Medium)
        assertTrue(AppWindowWidth.Medium < AppWindowWidth.Expanded)
        assertTrue(AppWindowWidth.of(1280) >= AppWindowWidth.Medium)
    }
}
