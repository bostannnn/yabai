package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAdaptiveLayoutTest {
    @Test
    fun `medium widths allow two filter source columns`() {
        val layout = resolveSettingsAdaptiveLayout(widthDp = 759)

        assertEquals(2, layout.filterSourceColumns)
        assertTrue(layout.useTwoColumnServerForm)
        assertTrue(layout.useInlineChoiceControls)
    }

    @Test
    fun `overview content width is reduced by padding and bounded by max width`() {
        assertEquals(760, resolveSettingsOverviewContentWidth(containerWidthDp = 800))
        assertEquals(1080, resolveSettingsOverviewContentWidth(containerWidthDp = 1400))
    }

    @Test
    fun `narrow cards keep single column form and stacked choices`() {
        val layout = resolveSettingsAdaptiveLayout(widthDp = 419)

        assertEquals(1, layout.filterSourceColumns)
        assertFalse(layout.useTwoColumnServerForm)
        assertFalse(layout.useInlineChoiceControls)
    }

    @Test
    fun `overview needs enough width for two full cards plus gutter`() {
        val singleColumnWidth = (SettingsOverviewMinColumnWidthDp * 2) + SettingsOverviewColumnSpacingDp - 1
        val twoColumnWidth = (SettingsOverviewMinColumnWidthDp * 2) + SettingsOverviewColumnSpacingDp

        assertEquals(855, singleColumnWidth)
        assertEquals(856, twoColumnWidth)
    }
}
