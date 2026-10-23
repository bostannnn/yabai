package com.example.unifiedreader.feature.settings

import com.example.unifiedreader.ui.components.AppWindowWidth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    @Test
    fun `only expanded windows keep the hub beside the page`() {
        assertFalse(settingsUsesSidePane(AppWindowWidth.Compact))
        assertFalse(settingsUsesSidePane(AppWindowWidth.Medium))
        assertTrue(settingsUsesSidePane(AppWindowWidth.Expanded))
    }

    @Test
    fun `tablet portrait on the emulator stacks and landscape keeps the side pane`() {
        assertFalse(settingsUsesSidePane(AppWindowWidth.of(800)))
        assertTrue(settingsUsesSidePane(AppWindowWidth.of(1280)))
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
