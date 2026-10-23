package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyNoticesTest {
    @Test
    fun `components are the headings before the first rule, the license text follows`() {
        val notices = parseThirdPartyNotices(
            """
            # Third-party notices

            ## Manrope (UI typeface)

            Bundled as a variable font. Licensed under the SIL Open Font License,
            Version 1.1; the full text follows.

            ## Material Symbols Rounded (UI icons)

            Converted from the Material icons (Apache License 2.0).

            ---

            SIL OPEN FONT LICENSE Version 1.1

            PREAMBLE The goals of the Open Font License.
            """.trimIndent(),
        )

        assertEquals(listOf("Manrope", "Material Symbols Rounded"), notices.components.map { it.name })
        assertEquals(listOf("UI typeface", "UI icons"), notices.components.map { it.about })
        assertTrue(notices.components[0].includesFullText)
        assertFalse(notices.components[1].includesFullText)
        assertEquals(
            listOf("SIL OPEN FONT LICENSE Version 1.1", "PREAMBLE The goals of the Open Font License."),
            notices.fullText.filterIsInstance<NoticeBlock.Paragraph>().map { it.text },
        )
    }

    @Test
    fun `a license page goes back to the list`() {
        assertEquals(SettingsPage.Licenses, SettingsPage.License("Manrope").parent)
        assertEquals(null, SettingsPage.Theme.parent)
        assertEquals(SettingsPage.License("Manrope"), SettingsPage.fromKey(SettingsPage.License("Manrope").key))
    }
}
