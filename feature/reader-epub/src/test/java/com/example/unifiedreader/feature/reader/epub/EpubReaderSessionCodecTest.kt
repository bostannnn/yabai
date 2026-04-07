package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubReaderSessionCodecTest {
    @Test
    fun `readium locator round trips through reader snapshot`() {
        val locator = Locator(
            href = requireNotNull(Url.fromDecodedPath("/chapter-1.xhtml")),
            mediaType = MediaType.XHTML,
            title = "Opening",
            locations = Locator.Locations(
                fragments = listOf("epubcfi(/6/2[chapter-1]!/4/2/14)"),
                progression = 0.2,
                position = 1,
                totalProgression = 0.35,
            ),
        )

        val restored = buildReaderLocator(locator)
            .let { readerLocator ->
                ReaderProgressSnapshot(
                    itemId = "epub-1",
                    locatorType = LocatorType.CFI,
                    cfi = readerLocator.cfi,
                    percent = readerLocator.percent,
                )
            }
            .toReadiumLocator()

        assertNotNull(restored)
        assertEquals(locator.href, restored?.href)
        assertEquals(locator.mediaType, restored?.mediaType)
        assertEquals(locator.locations.fragments, restored?.locations?.fragments)
        assertEquals(locator.locations.totalProgression, restored?.locations?.totalProgression)
    }

    @Test
    fun `restore epub preferences clamps values`() {
        val restored = restoreEpubPreferences(
            ReaderSessionSnapshot(
                itemId = "epub-1",
                preferences = mapOf(
                    EpubFontSizeKey to "8.0",
                    EpubLineHeightKey to "0.2",
                ),
            ),
        )

        assertEquals(2.4, restored.fontSize, 0.0001)
        assertEquals(1.1, restored.lineHeight, 0.0001)
    }

    @Test
    fun `viewport padding script applies vertical body padding inside navigator`() {
        val script = buildEpubViewportPaddingScript(32)

        assertTrue(script.contains("body.style.paddingTop = padding;"))
        assertTrue(script.contains("body.style.paddingBottom = padding;"))
        assertTrue(script.contains("var padding = '32px';"))
        assertFalse(script.contains("paddingLeft"))
        assertFalse(script.contains("paddingRight"))
    }

    @Test
    fun `viewport padding script clamps negative padding to zero`() {
        val script = buildEpubViewportPaddingScript(-8)

        assertTrue(script.contains("var padding = '0px';"))
    }

    @Test
    fun `tap action uses edge zones for ltr reading`() {
        assertEquals(
            EpubTapAction.Previous,
            resolveEpubTapAction(
                tapX = 10f,
                containerWidth = 100f,
                readingProgression = ReadingProgression.LTR,
            ),
        )
        assertEquals(
            EpubTapAction.ToggleOverlay,
            resolveEpubTapAction(
                tapX = 50f,
                containerWidth = 100f,
                readingProgression = ReadingProgression.LTR,
            ),
        )
        assertEquals(
            EpubTapAction.Next,
            resolveEpubTapAction(
                tapX = 95f,
                containerWidth = 100f,
                readingProgression = ReadingProgression.LTR,
            ),
        )
    }

    @Test
    fun `tap action mirrors edge zones for rtl reading`() {
        assertEquals(
            EpubTapAction.Next,
            resolveEpubTapAction(
                tapX = 10f,
                containerWidth = 100f,
                readingProgression = ReadingProgression.RTL,
            ),
        )
        assertEquals(
            EpubTapAction.Previous,
            resolveEpubTapAction(
                tapX = 95f,
                containerWidth = 100f,
                readingProgression = ReadingProgression.RTL,
            ),
        )
    }
}
