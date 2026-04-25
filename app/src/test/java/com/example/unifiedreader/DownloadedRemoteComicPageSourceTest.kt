package com.example.unifiedreader

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `observe pages recovers stale empty downloaded archive entry cache`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-cached-empty",
            pageCount = 5,
        )
        val archiveFile = File(context.cacheDir, "cached-empty-downloaded-page-source-test.cbz").apply {
            parentFile?.mkdirs()
            rewriteZip(
                "003.jpg" to byteArrayOf(3),
                "001.jpg" to byteArrayOf(1),
            )
        }
        val archiveEntryCache = DownloadedRemoteComicArchiveEntryCache().apply {
            put(item.id, emptyList())
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = archiveEntryCache,
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val emissions = pageSource.observePages(item).toList()

        assertEquals(2, emissions.size)
        assertEquals(5, emissions.first().size)
        assertEquals(listOf("001.jpg", "003.jpg"), emissions.last().map { page -> page.id })
        assertEquals(listOf("001.jpg", "003.jpg"), archiveEntryCache.get(item.id))
    }

    @Test
    fun `diagnostic reports missing downloaded archive details`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-1",
            pageCount = null,
        )
        val missingArchiveFile = File(context.cacheDir, "missing-downloaded-page-source-test.cbz")
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, missingArchiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val diagnostic = pageSource.pagesUnavailableDiagnostic(item)

        assertEquals("Downloaded comic file was not found", diagnostic.title)
        assertTrue(diagnostic.detail.contains("itemId=${item.id}"))
        assertTrue(diagnostic.detail.contains("pageCount=unknown"))
        assertTrue(diagnostic.detail.contains("cachedEntries=not loaded"))
    }

    @Test
    fun `diagnostic updates after inspecting empty downloaded archive`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-empty",
            pageCount = null,
        )
        val archiveFile = File(context.cacheDir, "empty-downloaded-page-source-test.cbz").apply {
            parentFile?.mkdirs()
            rewriteZip()
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val diagnostics = pageSource.observePagesUnavailableDiagnostic(item).toList()

        assertEquals("Downloaded comic pages are still being inspected", diagnostics.first().title)
        assertEquals("Downloaded comic archive has no entries", diagnostics.last().title)
        assertTrue(diagnostics.last().detail.contains("fileBytes="))
        assertTrue(diagnostics.last().detail.contains("firstBytes=50 4b"))
        assertTrue(diagnostics.last().detail.contains("archiveEntries=0"))
        assertTrue(diagnostics.last().detail.contains("imageEntries=0"))
        assertTrue(diagnostics.last().detail.contains("cachedEntries=0"))
    }

    @Test
    fun `page preview is stored in persistent preview cache`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-preview-cache",
            pageCount = 1,
        )
        val archiveFile = File(context.cacheDir, "preview-cache-downloaded-page-source-test.cbz").apply {
            parentFile?.mkdirs()
            rewriteZip(
                "001.png" to testPngBytes(),
            )
        }
        val previewDirectory = File(context.cacheDir, "downloaded-preview-cache-test").apply {
            deleteRecursively()
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
            previewStore = PersistentComicPagePreviewStore(previewDirectory),
        )

        val preview = pageSource.loadPagePreview(
            item = item,
            page = ComicPageSpec(
                id = "0",
                pageIndex = 0,
                contentDescription = "Downloaded Comic page 1",
            ),
        )

        assertTrue(preview is ComicPagePreviewResult.Success)
        assertTrue(previewDirectory.listFiles().orEmpty().any { file -> file.isFile && file.length() > 0L })
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

private fun testPngBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

private fun testDownloadedComic(
    id: String,
    pageCount: Int?,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library"),
    remoteId = id,
    title = "Downloaded Comic",
    format = FormatType.CBZ,
    pageCount = pageCount,
)
