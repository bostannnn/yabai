package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCoverCacheFileNameTest {
    @Test
    fun `remote cover cache file name changes when cover identity changes`() {
        val first = item(
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=token-1",
        )
        val second = item(
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=2&apiKey=token-1",
        )

        assertNotEquals(
            first.kavitaCacheFileName("cover"),
            second.kavitaCacheFileName("cover"),
        )
    }

    @Test
    fun `remote cover cache file name ignores api key changes`() {
        val first = item(
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=token-1",
        )
        val second = item(
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=token-2",
        )

        assertEquals(
            first.kavitaCacheFileName("cover"),
            second.kavitaCacheFileName("cover"),
        )
    }

    @Test
    fun `remote cover cache file name stores hashed cover identity`() {
        val sourceUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=secret-token"
        val fileName = item(coverUrl = sourceUrl).kavitaCacheFileName("cover")

        assertTrue(fileName.contains("-cover-"))
        assertFalse(fileName.contains("secret-token"))
        assertFalse(fileName.contains("chapter-cover"))
    }

    @Test
    fun `legacy fallback names are only used when cover identity is absent`() {
        val itemWithCoverIdentity = item(
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=token-1",
        )
        val itemWithoutCoverIdentity = item(coverUrl = null)

        assertTrue(itemWithCoverIdentity.kavitaLegacyCacheFileNames("cover").isEmpty())
        assertTrue(itemWithCoverIdentity.stumpLegacyCacheFileNames("cover").isEmpty())
        assertTrue(itemWithCoverIdentity.grimmoryLegacyCacheFileNames("cover").isEmpty())
        assertEquals(
            listOf("kavita_item_1.cover"),
            itemWithoutCoverIdentity.kavitaLegacyCacheFileNames("cover"),
        )
        assertEquals(
            listOf("kavita_item_1.cover"),
            itemWithoutCoverIdentity.stumpLegacyCacheFileNames("cover"),
        )
        assertEquals(
            listOf("kavita_item_1.cover"),
            itemWithoutCoverIdentity.grimmoryLegacyCacheFileNames("cover"),
        )
    }

    private fun item(
        coverUrl: String?,
    ): ReadableItem = ReadableItem(
        id = "kavita:item:1",
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        remoteId = "1",
        title = "Kavita Item",
        format = FormatType.CBZ,
        coverUrl = coverUrl,
    )
}
