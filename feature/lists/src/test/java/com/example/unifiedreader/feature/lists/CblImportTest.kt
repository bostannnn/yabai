package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CblImportTest {
    private val cbl = """
        <?xml version="1.0"?>
        <ReadingList xmlns:xsd="http://www.w3.org/2001/XMLSchema">
          <Name>Eclipse War &amp; After</Name>
          <Books>
            <Book Series="The Long Eclipse" Number="1" Volume="2016" Year="2016"><Id>a</Id></Book>
            <Book Number="007" Series="Neon Ronin" Volume="2020" Year="2020" />
            <Book Series="Harbor Lights" Number="3" />
            <Book Series="No number" />
          </Books>
        </ReadingList>
    """.trimIndent()

    @Test
    fun `a cbl gives its name and its issues in order`() {
        val list = parseCbl(cbl)

        assertEquals("Eclipse War & After", list?.name)
        assertEquals(listOf("The Long Eclipse" to "1", "Neon Ronin" to "007", "Harbor Lights" to "3"), list?.books?.map { it.series to it.number })
        assertNull(parseCbl("<html>not a reading list</html>"))
    }

    @Test
    fun `issues are matched by series and number from any source, the rest kept in their place`() {
        val library = listOf(
            comic("k1", "Long Eclipse (2016)", "1", SourceType.KAVITA, year = 2016),
            comic("k1-old", "Long Eclipse", "1", SourceType.KAVITA, year = 1990),
            comic("s7", "Neon Ronin", "7", SourceType.STUMP),
        )

        val imported = matchCbl(parseCbl(cbl)!!, library, fallbackName = "file")

        assertEquals(listOf("k1", "s7", "cbl:harborlights#3"), imported.items.map(ReadableItem::id))
        assertEquals("Imported 2 of 3 issues; 1 isn't in your library", imported.summary)
        assertEquals("Harbor Lights #3", imported.items.last().title)
    }

    @Test
    fun `issue numbers match whatever their padding`() {
        assertEquals("7", numberKey("007"))
        assertEquals("7", numberKey("7.0"))
        assertEquals("7.5", numberKey("07.50"))
        assertEquals("0", numberKey("000"))
    }

    private fun comic(id: String, series: String, number: String, source: SourceType, year: Int? = null) = ReadableItem(
        id = id,
        sourceType = source,
        libraryId = "lib",
        title = "$series $number",
        format = FormatType.CBZ,
        kind = ReadableItemKind.COMIC,
        publishedYear = year,
        comicMetadata = ComicMetadata(series = series, issueNumber = number),
    )
}
