package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test
    fun `side pane needs the hub column, the gap and a readable page`() {
        val threshold = (SettingsScreenHorizontalPaddingDp * 2) +
            SettingsSidePaneWidthDp +
            SettingsSidePaneSpacingDp +
            SettingsSidePaneMinPageWidthDp

        assertEquals(860, threshold)
        assertFalse(settingsUsesSidePane(windowWidthDp = threshold - 1))
        assertTrue(settingsUsesSidePane(windowWidthDp = threshold))
    }

    @Test
    fun `tablet portrait stacks and landscape keeps the side pane`() {
        assertFalse(settingsUsesSidePane(windowWidthDp = 800))
        assertTrue(settingsUsesSidePane(windowWidthDp = 1280))
    }

    @Test
    fun `wide forms use two columns and inline choice controls`() {
        val layout = resolveSettingsAdaptiveLayout(widthDp = 760)

        assertTrue(layout.useTwoColumnServerForm)
        assertTrue(layout.useInlineChoiceControls)
    }

    @Test
    fun `narrow forms stack their fields and choices`() {
        val layout = resolveSettingsAdaptiveLayout(widthDp = 419)

        assertFalse(layout.useTwoColumnServerForm)
        assertFalse(layout.useInlineChoiceControls)
    }
}
