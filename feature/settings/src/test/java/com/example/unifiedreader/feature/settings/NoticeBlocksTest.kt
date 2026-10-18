package com.example.unifiedreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeBlocksTest {
    @Test
    fun `headings rules and reflowed paragraphs come out in order`() {
        val blocks = parseNoticeBlocks(
            """
            # Third-party notices

            ## Manrope (UI typeface)

            Bundled as `manrope.ttf`.
            Licensed under the OFL.

            ---

            ---

            PREAMBLE
            The goals.
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                NoticeBlock.Heading("Manrope (UI typeface)"),
                NoticeBlock.Paragraph("Bundled as 'manrope.ttf'. Licensed under the OFL."),
                NoticeBlock.Rule,
                NoticeBlock.Paragraph("PREAMBLE The goals."),
            ),
            blocks,
        )
    }
}
