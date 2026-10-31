package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class WantToReadMirrorTest {
    private val seriesOf = mapOf("a1" to "7", "a2" to "7", "b1" to "9")

    @Test
    fun `adding books puts each of their series on the server once`() {
        val change = wantToReadSeriesChange(listOf(issue("a1"), issue("a2"), local("x")), added = true, remaining = emptyList(), seriesIdOf = ::series)

        assertEquals(setOf("7"), change)
    }

    @Test
    fun `a series comes off only once none of its books is left`() {
        assertEquals(
            emptySet<String>(),
            wantToReadSeriesChange(listOf(issue("a1")), added = false, remaining = listOf(issue("a2"), issue("b1")), seriesIdOf = ::series),
        )
        assertEquals(
            setOf("7"),
            wantToReadSeriesChange(listOf(issue("a1"), issue("a2")), added = false, remaining = listOf(issue("b1")), seriesIdOf = ::series),
        )
    }

    private fun series(item: ReadableItem): String? = seriesOf[item.id]

    private fun issue(id: String) = ReadableItem(id = id, sourceType = SourceType.KAVITA, libraryId = "k", title = id, format = FormatType.CBZ)

    private fun local(id: String) = ReadableItem(id = id, sourceType = SourceType.LOCAL, libraryId = "l", title = id, format = FormatType.EPUB)
}
