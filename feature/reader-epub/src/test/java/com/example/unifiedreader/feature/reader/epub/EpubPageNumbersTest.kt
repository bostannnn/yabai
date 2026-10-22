package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.feature.reader.core.ReaderPageNumber
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpubPageNumbersTest {
    // A chapter holding positions 101..110.
    private val chapter = EpubResourcePositions(firstPosition = 101, count = 10)

    @Test
    fun secondPageIsNumberedTheWayReadiumNumbersAColumn() {
        // Three spreads, six columns. Readium numbers a column at progression p as
        // firstPosition + ceil(p × (count − 1)); the second column of spread 0 starts at 1/6.
        assertEquals(
            103,
            epubSecondSpreadPage(
                locator = locator(progression = 0.0, position = 101),
                resourcePage = EpubResourcePage(pageIndex = 0, totalPages = 3),
                resource = chapter,
            ),
        )
        // Spread 1 starts at 1/3 (position 104); its second column at 1/2.
        assertEquals(
            106,
            epubSecondSpreadPage(
                locator = locator(progression = 1.0 / 3.0, position = 104),
                resourcePage = EpubResourcePage(pageIndex = 1, totalPages = 3),
                resource = chapter,
            ),
        )
    }

    @Test
    fun secondPageStaysInsideItsChapter() {
        assertEquals(
            110,
            epubSecondSpreadPage(
                locator = locator(progression = 0.8, position = 109),
                resourcePage = EpubResourcePage(pageIndex = 1, totalPages = 2),
                resource = chapter,
            ),
        )
    }

    @Test
    fun blankSecondColumnHasNoNumber() {
        assertNull(
            epubSecondSpreadPage(
                locator = locator(progression = 2.0 / 3.0, position = 107),
                resourcePage = EpubResourcePage(pageIndex = 2, totalPages = 3, isSecondColumnBlank = true),
                resource = chapter,
            ),
        )
    }

    @Test
    fun secondPageNeedsReadiumsOwnPositionAndThePageCount() {
        assertNull(
            epubSecondSpreadPage(
                locator = locator(progression = 0.0, position = null),
                resourcePage = EpubResourcePage(pageIndex = 0, totalPages = 3),
                resource = chapter,
            ),
        )
        assertNull(
            epubSecondSpreadPage(
                locator = locator(progression = 0.0, position = 101),
                resourcePage = EpubResourcePage(pageIndex = 0, totalPages = null),
                resource = chapter,
            ),
        )
        assertNull(
            epubSecondSpreadPage(
                locator = locator(progression = 0.0, position = 101),
                resourcePage = EpubResourcePage(pageIndex = 0, totalPages = 3),
                resource = null,
            ),
        )
    }

    @Test
    fun singlePageShowsOneNumberInTheMiddle() {
        assertEquals(
            listOf(ReaderPageNumber("37 / 412", 0.5f)),
            buildEpubPageNumbers(
                currentPage = 37,
                pageCount = 412,
                isSpread = false,
                isRightToLeft = false,
                secondSpreadPage = 38,
            ),
        )
    }

    @Test
    fun spreadShowsANumberUnderEachPage() {
        assertEquals(
            listOf(ReaderPageNumber("37 / 412", 0.25f), ReaderPageNumber("38 / 412", 0.75f)),
            buildEpubPageNumbers(
                currentPage = 37,
                pageCount = 412,
                isSpread = true,
                isRightToLeft = false,
                secondSpreadPage = 38,
            ),
        )
    }

    @Test
    fun rightToLeftSpreadStartsOnTheRightPage() {
        assertEquals(
            listOf(ReaderPageNumber("37 / 412", 0.75f), ReaderPageNumber("38 / 412", 0.25f)),
            buildEpubPageNumbers(
                currentPage = 37,
                pageCount = 412,
                isSpread = true,
                isRightToLeft = true,
                secondSpreadPage = 38,
            ),
        )
    }

    @Test
    fun spreadEndingOnABlankPageNumbersOnlyThePageWithText() {
        assertEquals(
            listOf(ReaderPageNumber("40 / 412", 0.25f)),
            buildEpubPageNumbers(
                currentPage = 40,
                pageCount = 412,
                isSpread = true,
                isRightToLeft = false,
                secondSpreadPage = null,
            ),
        )
    }

    @Test
    fun noNumbersUntilThePageCountIsKnown() {
        assertTrue(
            buildEpubPageNumbers(
                currentPage = 37,
                pageCount = 0,
                isSpread = false,
                isRightToLeft = false,
                secondSpreadPage = null,
            ).isEmpty(),
        )
        assertTrue(
            buildEpubPageNumbers(
                currentPage = 0,
                pageCount = 412,
                isSpread = false,
                isRightToLeft = false,
                secondSpreadPage = null,
            ).isEmpty(),
        )
    }

    @Test
    fun fixedLayoutSpreadShowsOneNumberInTheMiddle() {
        val fixedLayout = publication(metadata = JSONObject().put("presentation", JSONObject().put("layout", "fixed")))

        assertEquals(
            listOf(ReaderPageNumber("5 / 40", 0.5f)),
            fixedLayout.buildEpubPageNumbers(
                currentPage = 5,
                pageCount = 40,
                layoutMode = EpubReaderLayoutMode.DoublePage,
                secondSpreadPage = 6,
            ),
        )
    }

    @Test
    fun bookReadingRightToLeftPutsItsFirstPageOnTheRight() {
        val rightToLeft = publication(metadata = JSONObject().put("readingProgression", "rtl"))

        assertEquals(
            listOf(ReaderPageNumber("5 / 40", 0.75f), ReaderPageNumber("6 / 40", 0.25f)),
            rightToLeft.buildEpubPageNumbers(
                currentPage = 5,
                pageCount = 40,
                layoutMode = EpubReaderLayoutMode.DoublePage,
                secondSpreadPage = 6,
            ),
        )
    }

    @Test
    fun resourcePositionsKeepEachChaptersFirstPositionAndCount() {
        val positions = listOf(
            listOf(locator(progression = 0.0, position = 1), locator(progression = 0.5, position = 2)),
            listOf(locator(progression = 0.0, position = 3)),
            emptyList(),
        )

        assertEquals(
            listOf(
                EpubResourcePositions(firstPosition = 1, count = 2),
                EpubResourcePositions(firstPosition = 3, count = 1),
                EpubResourcePositions(firstPosition = 0, count = 0),
            ),
            positions.toEpubResourcePositions(),
        )
    }

    private fun locator(
        progression: Double,
        position: Int?,
    ): Locator {
        val locations = JSONObject().put("progression", progression)
        if (position != null) locations.put("position", position)
        return checkNotNull(
            Locator.fromJSON(
                JSONObject()
                    .put("href", "OEBPS/text/chapter.xhtml")
                    .put("type", "application/xhtml+xml")
                    .put("locations", locations),
            ),
        )
    }

    private fun publication(
        metadata: JSONObject,
    ): Publication {
        val manifest = Manifest.fromJSON(
            JSONObject()
                .put("metadata", metadata.put("title", "Test"))
                .put(
                    "readingOrder",
                    JSONArray().put(
                        JSONObject().put("href", "OEBPS/text/chapter.xhtml").put("type", "application/xhtml+xml"),
                    ),
                ),
        )
        return Publication(manifest = checkNotNull(manifest))
    }
}
