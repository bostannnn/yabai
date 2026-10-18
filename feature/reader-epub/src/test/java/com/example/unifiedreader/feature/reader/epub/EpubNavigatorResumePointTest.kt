package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubNavigatorResumePointTest {
    private val openedAt = locator(href = "/chapter-1.xhtml", progression = 0.1)

    @Test
    fun `the first navigator opens at the restored position`() {
        assertEquals(openedAt, EpubNavigatorResumePoint().startLocator(openedAt))
    }

    @Test
    fun `a navigator rebuilt for a layout change opens where the old one stopped`() {
        val resumePoint = EpubNavigatorResumePoint()
        resumePoint.startLocator(openedAt)
        val readTo = locator(href = "/chapter-4.xhtml", progression = 0.6)

        resumePoint.onNavigatorRemoved(readTo)

        assertEquals(readTo, resumePoint.startLocator(openedAt))
    }

    private fun locator(href: String, progression: Double) = Locator(
        href = requireNotNull(Url.fromDecodedPath(href)),
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression),
    )
}
