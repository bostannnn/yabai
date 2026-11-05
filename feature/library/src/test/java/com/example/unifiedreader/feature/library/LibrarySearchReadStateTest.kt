package com.example.unifiedreader.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySearchReadStateTest {

    @Test
    fun `a search result says where its reading stands`() {
        assertNull(librarySearchReadState(null))
        assertNull(librarySearchReadState(ItemProgressSummary(label = "", fraction = 0.002f)))
        assertEquals("12%", librarySearchReadState(ItemProgressSummary(label = "", fraction = 0.12f)))
        assertEquals("Finished", librarySearchReadState(ItemProgressSummary(label = "", fraction = 1f, isFinished = true)))
    }
}
