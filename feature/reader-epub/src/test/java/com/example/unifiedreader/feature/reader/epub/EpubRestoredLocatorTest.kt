package com.example.unifiedreader.feature.reader.epub

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EpubRestoredLocatorTest {
    private val publication = publication(
        "OEBPS/text/foreword.xhtml",
        "OEBPS/text/part03.xhtml",
        "OEBPS/text/part06.xhtml",
    )

    @Test
    fun grimmoryHrefWithAFragmentResolvesToItsResourceAndKeepsTheAnchor() {
        // What GrimmoryProgressParsing builds from Grimmory's web reader progress.
        val saved = locator(
            """{"href":"OEBPS/text/part03.xhtml#ch3","type":"application/xhtml+xml",""" +
                """"locations":{"fragments":["epubcfi(/6/12!/4/2[ch3]/120/1:0)"],"totalProgression":0.39}}""",
        )

        val resolved = checkNotNull(publication.resolveEpubRestoredLocator(saved))

        assertEquals("OEBPS/text/part03.xhtml", resolved.href.toString())
        assertNull(resolved.href.fragment)
        assertEquals(listOf("ch3", "epubcfi(/6/12!/4/2[ch3]/120/1:0)"), resolved.locations.fragments)
        assertEquals(0.39, checkNotNull(resolved.locations.totalProgression), 0.0)
    }

    @Test
    fun hrefRelativeToThePackageDocumentResolvesByItsPath() {
        val resolved = publication.resolveEpubRestoredLocator(hrefLocator("text/part06.xhtml"))

        assertEquals("OEBPS/text/part06.xhtml", resolved?.href?.toString())
    }

    @Test
    fun encodedNonAsciiHrefResolvesAndKeepsItsChapterProgress() {
        val cyrillic = publication("OEBPS/Text/%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%203.xhtml")
        // What the Grimmory sync builds from a Kobo or app position: the chapter and how far into it.
        val saved = locator(
            """{"href":"OEBPS/Text/%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%203.xhtml","type":"application/xhtml+xml",""" +
                """"locations":{"progression":0.4,"totalProgression":0.47}}""",
        )

        val resolved = checkNotNull(cyrillic.resolveEpubRestoredLocator(saved))

        assertEquals(cyrillic.readingOrder.single().url(), resolved.href)
        assertEquals(0.4, checkNotNull(resolved.locations.progression), 0.0)
    }

    @Test
    fun readiumsOwnLocatorIsKeptAsItIs() {
        val saved = locator(
            """{"href":"OEBPS/text/part06.xhtml","type":"application/xhtml+xml",""" +
                """"locations":{"progression":0.25,"position":98,"totalProgression":0.46}}""",
        )

        assertEquals(saved, publication.resolveEpubRestoredLocator(saved))
    }

    @Test
    fun hrefOutsideTheReadingOrderOrMatchingSeveralResourcesResolvesToNothing() {
        val ambiguous = publication(
            "OEBPS/one/chapter.xhtml",
            "OEBPS/two/chapter.xhtml",
        )

        assertNull(publication.resolveEpubRestoredLocator(hrefLocator("OEBPS/text/missing.xhtml")))
        assertNull(ambiguous.resolveEpubRestoredLocator(hrefLocator("chapter.xhtml")))
    }

    private fun publication(
        vararg hrefs: String,
    ): Publication {
        val readingOrder = JSONArray()
        hrefs.forEach { href ->
            readingOrder.put(JSONObject().put("href", href).put("type", "application/xhtml+xml"))
        }
        val manifest = Manifest.fromJSON(
            JSONObject()
                .put("metadata", JSONObject().put("title", "Test"))
                .put("readingOrder", readingOrder),
        )
        return Publication(manifest = checkNotNull(manifest))
    }

    private fun locator(
        json: String,
    ): Locator = checkNotNull(Locator.fromJSON(JSONObject(json)))

    private fun hrefLocator(
        href: String,
    ): Locator = locator("""{"href":"$href","type":"application/xhtml+xml","locations":{}}""")
}
