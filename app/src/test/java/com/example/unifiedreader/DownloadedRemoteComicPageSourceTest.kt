package com.example.unifiedreader

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadedRemoteComicPageSourceTest {
    @Test
    fun `observe pages replaces catalog fallback with downloaded archive entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-1",
            pageCount = 5,
        )
        val archiveFile = File(context.cacheDir, "downloaded-page-source-test.cbz").apply {
            parentFile?.mkdirs()
            rewriteZip(
                "003.jpg" to byteArrayOf(3),
                "001.jpg" to byteArrayOf(1),
            )
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val emissions = pageSource.observePages(item).toList()

        assertEquals(2, emissions.size)
        assertEquals(5, emissions.first().size)
        assertEquals(listOf("001.jpg", "003.jpg"), emissions.last().map { page -> page.id })
    }
}

private class TestStoredRemoteItemDownloadDataSource(
    private val storedItemId: String,
    private val file: File,
) : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun supports(
        item: ReadableItem,
    ): Boolean = item.sourceType == sourceType && item.format == FormatType.CBZ

    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = Uri.fromFile(file).takeIf { item.id == storedItemId && file.isFile }

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = storedDocumentUri(item)

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = items.filterTo(mutableSetOf()) { item ->
        item.id == storedItemId && file.isFile
    }.mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = item.id == storedItemId && file.delete()
}

private fun File.rewriteZip(
    vararg entries: Pair<String, ByteArray>,
) {
    ZipOutputStream(FileOutputStream(this)).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

private fun testDownloadedComic(
    id: String,
    pageCount: Int,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library"),
    remoteId = id,
    title = "Downloaded Comic",
    format = FormatType.CBZ,
    pageCount = pageCount,
)
