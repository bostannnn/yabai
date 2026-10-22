package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.core.ReaderPageNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicPageNumbersTest {
    private val pages = (0 until 5).map { index ->
        ComicPageSpec(id = "page-$index", pageIndex = index, contentDescription = "Page ${index + 1}")
    }
    private val portraitPageMetadata = pages.associate { page -> page.id to ComicPageMetadata(width = 600, height = 900) }

    // A landscape tablet: the 4:3 pair is fitted to the height, 1600 px wide, centred.
    private val landscape = IntSize(width = 1920, height = 1200)

    @Test
    fun pairedSpreadNumbersEachPageUnderItsCentre() {
        val spread = spreads(ComicReadingDirection.LTR)[1]

        val numbers = spread.pageNumbers(pageCount = pages.size, containerSize = landscape, pageFitMode = ComicPageFitMode.FitPage)

        assertNumbers(
            expected = listOf(ReaderPageNumber("2 / 5", 560f / 1920f), ReaderPageNumber("3 / 5", 1360f / 1920f)),
            actual = numbers,
        )
    }

    @Test
    fun rightToLeftSpreadPutsTheFirstPageOnTheRight() {
        val spread = spreads(ComicReadingDirection.RTL)[1]

        val numbers = spread.pageNumbers(pageCount = pages.size, containerSize = landscape, pageFitMode = ComicPageFitMode.FitPage)

        assertNumbers(
            expected = listOf(ReaderPageNumber("3 / 5", 560f / 1920f), ReaderPageNumber("2 / 5", 1360f / 1920f)),
            actual = numbers,
        )
    }

    @Test
    fun soloPageShowsOneNumberInTheMiddle() {
        val cover = spreads(ComicReadingDirection.LTR).first()

        assertEquals(
            listOf(ReaderPageNumber("1 / 5")),
            cover.pageNumbers(pageCount = pages.size, containerSize = landscape, pageFitMode = ComicPageFitMode.FitPage),
        )
    }

    private fun spreads(direction: ComicReadingDirection): List<ComicPageSpread> = buildComicPageSpreads(
        pages = pages,
        readingDirection = direction,
        useDoublePageSpreads = true,
        pageMetadataById = portraitPageMetadata,
    )

    private fun assertNumbers(
        expected: List<ReaderPageNumber>,
        actual: List<ReaderPageNumber>,
    ) {
        assertEquals(expected.map(ReaderPageNumber::label), actual.map(ReaderPageNumber::label))
        expected.zip(actual).forEach { (expectedNumber, actualNumber) ->
            assertEquals(expectedNumber.centerFraction, actualNumber.centerFraction, 0.002f)
        }
    }
}
