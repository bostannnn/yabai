package com.example.unifiedreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchSplashHoldTest {

    private var now = 1_000L
    private val released = mutableListOf<Pair<Long, Boolean>>()
    private val hold = AppLaunchSplashHold(
        maxHoldMillis = 800L,
        clock = { now },
        onReleased = { heldMillis, timedOut -> released += heldMillis to timedOut },
    )

    @Test
    fun `a first screen without placeholders lets the splash go at the first frame`() {
        assertFalse(hold.shouldKeep())
        assertTrue(hold.isReleased)
        assertEquals(listOf(0L to false), released)
    }

    @Test
    fun `the splash stays while placeholders are on screen and goes when the last one leaves`() {
        assertTrue(hold.hold())
        assertTrue(hold.hold())
        assertTrue(hold.shouldKeep())
        now += 120
        hold.release()
        assertTrue(hold.shouldKeep())
        now += 80
        hold.release()
        assertFalse(hold.shouldKeep())
        assertEquals(listOf(200L to false), released)
    }

    @Test
    fun `the splash stops waiting after the longest hold, counted from the first frame`() {
        hold.hold()
        now += 5_000 // however long the first frame took
        assertTrue(hold.shouldKeep())
        now += 799
        assertTrue(hold.shouldKeep())
        now += 1
        assertFalse(hold.shouldKeep())
        assertEquals(listOf(800L to true), released)
    }

    @Test
    fun `once gone the splash stays gone and later placeholders are not counted`() {
        assertFalse(hold.shouldKeep())
        assertFalse(hold.hold())
        assertFalse(hold.shouldKeep())
        assertEquals(1, released.size)
    }

    @Test
    fun `releasing more than was held never goes below none`() {
        hold.release()
        hold.hold()
        assertTrue(hold.shouldKeep())
        hold.release()
        hold.release()
        assertFalse(hold.shouldKeep())
    }
}
