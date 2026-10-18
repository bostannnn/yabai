package com.example.unifiedreader.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPageTest {
    private val saverScope = SaverScope { true }

    private fun save(page: SettingsPage?): String? = with(SettingsPageSaver) { saverScope.save(page) }

    @Test
    fun `every page survives a save and restore through its key`() {
        val pages = listOf(
            SettingsPage.Theme,
            SettingsPage.FilterChips,
            SettingsPage.DrawerOrder,
            SettingsPage.SyncStatus,
            SettingsPage.Licenses,
        ) + SourceType.entries.map(SettingsPage::Source)

        pages.forEach { page ->
            assertEquals(page, SettingsPage.fromKey(page.key))
            assertEquals(page, SettingsPageSaver.restore(requireNotNull(save(page))))
        }
    }

    @Test
    fun `the hub saves as an empty key and unknown keys restore to the hub`() {
        assertEquals("", save(null))
        assertNull(SettingsPageSaver.restore(""))
        assertNull(SettingsPage.fromKey(null))
        assertNull(SettingsPage.fromKey("source:NOPE"))
        assertNull(SettingsPage.fromKey("garbage"))
    }
}
