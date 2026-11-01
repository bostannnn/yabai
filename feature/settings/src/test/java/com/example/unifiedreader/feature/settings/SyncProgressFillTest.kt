package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProgressFillTest {
    @Test
    fun `the bar moves until a step can count its work`() {
        assertNull(syncProgressFillAfter(current = null, reported = null))
        assertEquals(0.02f, syncProgressFillAfter(current = null, reported = 0.02f))
    }

    @Test
    fun `a retried step doesn't send the fill back`() {
        // History reached 95%, then the uploads moved to the backup address and started their steps again.
        assertEquals(0.95f, syncProgressFillAfter(current = 0.95f, reported = 0.08f))
        assertEquals(0.96f, syncProgressFillAfter(current = 0.95f, reported = 0.96f))
    }

    @Test
    fun `a step that can't count keeps the fill`() {
        assertEquals(0.4f, syncProgressFillAfter(current = 0.4f, reported = null))
    }

    @Test
    fun `the fill stays within the bar`() {
        assertEquals(1f, syncProgressFillAfter(current = null, reported = 1.4f))
    }
}
