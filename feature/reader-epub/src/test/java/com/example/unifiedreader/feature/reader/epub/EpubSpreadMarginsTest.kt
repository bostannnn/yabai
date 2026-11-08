package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.css.Length
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubSpreadMarginsTest {
    @Test
    fun `a spread on a tablet gets book margins, a narrow window or a single page does not`() {
        assertTrue(usesEpubSpreadMargins(isReflowableSpread = true, windowWidthDp = 1292))
        assertTrue(usesEpubSpreadMargins(isReflowableSpread = true, windowWidthDp = 1280))
        // Under 960 dp left for Readium, it would fall back to one column.
        assertFalse(usesEpubSpreadMargins(isReflowableSpread = true, windowWidthDp = 1000))
        assertFalse(usesEpubSpreadMargins(isReflowableSpread = false, windowWidthDp = 1292))
    }

    @Test
    fun `each outer strip is as wide as a page's padding, so the middle matches an outer edge`() {
        // Normal: 32 dp strip + 32 px padding = 64 at each edge; 32 + 32 = 64 in the middle.
        assertEquals(32.dp, epubSpreadSideStrip(usesSpreadMargins = true, pageMargins = 1.0))
        assertEquals(16.dp, epubSpreadSideStrip(usesSpreadMargins = true, pageMargins = 0.5))
        assertEquals(64.dp, epubSpreadSideStrip(usesSpreadMargins = true, pageMargins = 2.0))
        // The book's own styling: Readium's margins stay at 1.0.
        assertEquals(32.dp, epubSpreadSideStrip(usesSpreadMargins = true, pageMargins = null))
        assertEquals(0.dp, epubSpreadSideStrip(usesSpreadMargins = false, pageMargins = 1.0))
    }

    @Test
    fun `the spread's gutter goes to Readium only when asked`() {
        assertEquals(
            Length.Px(EpubSpreadPageGutterPx),
            epubNavigatorFragmentConfiguration(pageGutterPx = EpubSpreadPageGutterPx).readiumCssRsProperties.pageGutter,
        )
        assertNull(epubNavigatorFragmentConfiguration().readiumCssRsProperties.pageGutter)
    }
}
