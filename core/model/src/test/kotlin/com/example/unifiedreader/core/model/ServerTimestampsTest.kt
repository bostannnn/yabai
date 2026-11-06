package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerTimestampsTest {
    private val expected = 1_793_875_320_000L // 2026-11-05T10:42:00Z

    @Test
    fun readsAnInstant() {
        assertEquals(expected, "2026-11-05T10:42:00Z".toServerEpochMillisOrNull())
    }

    @Test
    fun readsFractionalSecondsUpToSevenDigits() {
        assertEquals(expected + 123, "2026-11-05T10:42:00.1234567Z".toServerEpochMillisOrNull())
    }

    @Test
    fun readsAnOffset() {
        assertEquals(expected, "2026-11-05T13:42:00+03:00".toServerEpochMillisOrNull())
    }

    @Test
    fun readsALocalTimeAsUtc() {
        assertEquals(expected, "2026-11-05T10:42:00".toServerEpochMillisOrNull())
        assertEquals(expected + 500, "2026-11-05T10:42:00.5".toServerEpochMillisOrNull())
    }

    @Test
    fun trimsSurroundingSpace() {
        assertEquals(expected, " 2026-11-05T10:42:00Z\n".toServerEpochMillisOrNull())
    }

    @Test
    fun returnsNullForNothingOrNonsense() {
        assertNull(null.toServerEpochMillisOrNull())
        assertNull("".toServerEpochMillisOrNull())
        assertNull("   ".toServerEpochMillisOrNull())
        assertNull("yesterday".toServerEpochMillisOrNull())
        assertNull("2026-11-05".toServerEpochMillisOrNull())
    }
}
