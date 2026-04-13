package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterDefinitionsTest {
    @Test
    fun `grimmory defaults include series and metadata facets`() {
        val panels = defaultVisibleLibraryFilterPanels(SourceType.GRIMMORY)

        assertTrue(LibraryFilterPanel.Author in panels)
        assertTrue(LibraryFilterPanel.Series in panels)
        assertTrue(LibraryFilterPanel.Publisher in panels)
        assertTrue(LibraryFilterPanel.Language in panels)
    }

    @Test
    fun `kavita defaults include metadata facets`() {
        val panels = defaultVisibleLibraryFilterPanels(SourceType.KAVITA)

        assertTrue(LibraryFilterPanel.Series in panels)
        assertTrue(LibraryFilterPanel.Publisher in panels)
        assertTrue(LibraryFilterPanel.Genre in panels)
        assertTrue(LibraryFilterPanel.Tag in panels)
        assertTrue(LibraryFilterPanel.Language in panels)
    }
}
