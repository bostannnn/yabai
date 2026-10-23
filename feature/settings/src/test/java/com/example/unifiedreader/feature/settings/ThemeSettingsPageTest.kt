package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsPageTest {
    @Test
    fun `System Dynamic gets its own group, not Light`() {
        val groups = themeTileGroups(ThemeMode.settingsEntries).toMap()

        assertEquals(listOf("Device", "Light", "Dark"), themeTileGroups(ThemeMode.settingsEntries).map { it.first })
        assertEquals(listOf(ThemeMode.SystemDynamic), groups.getValue("Device"))
        assertEquals(false, ThemeMode.SystemDynamic in groups.getValue("Light"))
        assertEquals(ThemeMode.settingsEntries.size, groups.values.sumOf { it.size })
    }
}
