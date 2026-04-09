package com.example.unifiedreader.feature.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpubTableOfContentsModelTest {
    @Test
    fun activeEntryPrefersCurrentHrefWhenPresent() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Chapter 1",
                href = "https://example.com/chapter-1.xhtml",
                progression = 0.1,
            ),
            tocEntry(
                id = "two",
                title = "Chapter 2",
                href = "https://example.com/chapter-2.xhtml",
                progression = 0.6,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.15,
            currentHref = "https://example.com/chapter-2.xhtml",
        )

        assertEquals("two", activeEntry?.id)
    }

    @Test
    fun activeEntryFallsBackToClosestProgressionWhenHrefMissing() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Chapter 1",
                href = "https://example.com/chapter-1.xhtml",
                progression = 0.1,
            ),
            tocEntry(
                id = "two",
                title = "Chapter 2",
                href = "https://example.com/chapter-2.xhtml",
                progression = 0.55,
            ),
            tocEntry(
                id = "three",
                title = "Chapter 3",
                href = "https://example.com/chapter-3.xhtml",
                progression = 0.82,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.6,
            currentHref = null,
        )

        assertEquals("two", activeEntry?.id)
    }

    @Test
    fun activeEntryReturnsNullWithoutHrefOrProgressionMatch() {
        val entries = listOf(
            tocEntry(
                id = "one",
                title = "Front Matter",
                href = "https://example.com/front.xhtml",
                progression = null,
            ),
        )

        val activeEntry = entries.activeEpubTocEntry(
            currentProgress = 0.0,
            currentHref = null,
        )

        assertNull(activeEntry)
    }

    private fun tocEntry(
        id: String,
        title: String,
        href: String,
        progression: Double?,
        position: Int? = null,
    ): EpubTocEntry = EpubTocEntry(
        id = id,
        title = title,
        depth = 0,
        href = href,
        progression = progression,
        position = position,
        link = Link(
            href = checkNotNull(Href(href)),
            title = title,
        ),
    )
}
