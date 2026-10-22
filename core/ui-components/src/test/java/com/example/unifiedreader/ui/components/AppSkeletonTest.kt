package com.example.unifiedreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSkeletonTest {

    private val period = AppSkeletonDefaults.periodMillis

    @Test
    fun `the pulse starts and ends each period fully opaque`() {
        assertEquals(1f, skeletonPulseAlpha(0L), 0.0001f)
        assertEquals(1f, skeletonPulseAlpha(period), 0.0001f)
        assertEquals(1f, skeletonPulseAlpha(5 * period), 0.0001f)
    }

    @Test
    fun `the pulse is faintest halfway through a period`() {
        assertEquals(AppSkeletonDefaults.minAlpha, skeletonPulseAlpha(period / 2), 0.0001f)
    }

    @Test
    fun `the pulse goes down and comes back the same way`() {
        val quarter = period / 4
        assertEquals(skeletonPulseAlpha(quarter), skeletonPulseAlpha(period - quarter), 0.0001f)
        assertTrue(skeletonPulseAlpha(quarter) < 1f)
        assertTrue(skeletonPulseAlpha(quarter) > AppSkeletonDefaults.minAlpha)
    }

    @Test
    fun `placeholders read the same clock, so any two at the same time agree`() {
        val now = 123_456_789L
        assertEquals(skeletonPulseAlpha(now), skeletonPulseAlpha(now + 7 * period), 0.0001f)
    }
}
