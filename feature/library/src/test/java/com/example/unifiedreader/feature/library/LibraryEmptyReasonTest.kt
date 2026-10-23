package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryEmptyReasonTest {
    @Test
    fun `a source that was never connected says so before anything else about the source`() {
        assertEquals(
            LibraryEmptyReason.SourceNotConnected,
            libraryEmptyReason(searchActive = false, filtersActive = false, sourceUnreachable = true, sourceNotConnected = true),
        )
        assertEquals(
            LibraryEmptyReason.SourceUnreachable,
            libraryEmptyReason(searchActive = false, filtersActive = false, sourceUnreachable = true),
        )
        assertEquals(
            LibraryEmptyReason.Empty,
            libraryEmptyReason(searchActive = false, filtersActive = false, sourceUnreachable = false),
        )
    }

    @Test
    fun `a search or filters that match nothing win over the source state`() {
        assertEquals(
            LibraryEmptyReason.NoSearchMatches,
            libraryEmptyReason(searchActive = true, filtersActive = true, sourceUnreachable = false, sourceNotConnected = true),
        )
        assertEquals(
            LibraryEmptyReason.NoFilterMatches,
            libraryEmptyReason(searchActive = false, filtersActive = true, sourceUnreachable = false, sourceNotConnected = true),
        )
    }
}
