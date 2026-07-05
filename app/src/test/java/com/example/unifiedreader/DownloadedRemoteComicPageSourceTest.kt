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
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
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
    fun `downloaded remote comic source resolves both zip and rar archive codecs`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val formats = downloadedRemoteComicArchiveCodecFormats(context)

        assertEquals(listOf(FormatType.CBZ, FormatType.CBR), formats)
    }

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
    fun `downloaded archive reading tolerates cbr catalog metadata for zip bytes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-wrong-format",
            pageCount = 1,
            format = FormatType.CBR,
        )
        val archiveFile = File(context.cacheDir, "wrong-format-downloaded-page-source-test.cbr").apply {
            parentFile?.mkdirs()
            rewriteZip(
                "001.png" to testPngBytes(),
            )
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val pages = pageSource.observePages(item).toList().last()
        val result = pageSource.loadPageImage(item, pages.single(), ComicPageLoadPriority.Visible)

        assertEquals(listOf("001.png"), pages.map { page -> page.id })
        assertTrue(result is ComicPageLoadResult.Success)
    }

    @Test
    fun `downloaded archive reading tolerates cbz catalog metadata for cbr extension`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-wrong-extension",
            pageCount = 1,
            format = FormatType.CBZ,
        )
        val archiveFile = File(context.cacheDir, "wrong-extension-downloaded-page-source-test.cbr").apply {
            parentFile?.mkdirs()
            rewriteZip(
                "001.png" to testPngBytes(),
            )
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val pages = pageSource.observePages(item).toList().last()
        val result = pageSource.loadPageImage(item, pages.single(), ComicPageLoadPriority.Visible)

        assertEquals(listOf("001.png"), pages.map { page -> page.id })
        assertTrue(result is ComicPageLoadResult.Success)
    }

    @Test
    fun `downloaded archive reading loads real cbr archive pages`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-real-cbr",
            pageCount = 1,
            format = FormatType.CBR,
        )
        val archiveFile = File(context.cacheDir, "real-cbr-downloaded-page-source-test.cbr").apply {
            parentFile?.mkdirs()
            rewriteStoredRar(
                "001.png" to testPngBytes(),
            )
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        val pages = pageSource.observePages(item).toList().last()
        val result = pageSource.loadPageImage(item, pages.single(), ComicPageLoadPriority.Visible)

        assertEquals(listOf("001.png"), pages.map { page -> page.id })
        assertTrue(result is ComicPageLoadResult.Success)
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

    @Test
    fun `downloaded comic invalidation clears copied rar archive cache`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = testDownloadedComic(
            id = "chapter-rar-cache",
            pageCount = 1,
            format = FormatType.CBR,
        )
        val dispatcher = DownloadedRemoteComicCacheInvalidationDispatcher()
        val archiveFile = File(context.cacheDir, "rar-cache-invalidation-source.cbr").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val rarCacheFile = File(context.cacheDir, "comic-archives/copied.cbr").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = listOf(TestStoredRemoteItemDownloadDataSource(item.id, archiveFile)),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = dispatcher,
        )

        dispatcher.invalidate(item.id)

        assertTrue(!rarCacheFile.exists())
    }

    @Test
    fun `clear all trim clears copied zip archive cache`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zipCacheFile = File(context.cacheDir, "comic-zip-archives/downloaded-clear-all.cbz").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = emptyList(),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )

        pageSource.trimMemoryCaches(ReaderMemoryTrimRequest.ClearAll)

        assertTrue(!zipCacheFile.exists())
    }

    @Test
    fun `non clear trim keeps copied zip archive cache`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ZipComicArchiveCodec.clearLocalArchiveCache(context)
        val zipCacheFile = File(context.cacheDir, "comic-zip-archives/downloaded-drop-previews.cbz").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val pageSource = DownloadedRemoteComicPageSource(
            context = context,
            remoteDownloadDataSources = emptyList(),
            archiveEntryCache = DownloadedRemoteComicArchiveEntryCache(),
            cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher(),
        )
        try {
            pageSource.trimMemoryCaches(ReaderMemoryTrimRequest.dropPreviewsAndDistantPages(retention = null))

            assertTrue(zipCacheFile.exists())
        } finally {
            ZipComicArchiveCodec.clearLocalArchiveCache(context)
        }
    }
}

private class TestStoredRemoteItemDownloadDataSource(
    private val storedItemId: String,
    private val file: File,
) : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun supports(
        item: ReadableItem,
    ): Boolean = item.sourceType == sourceType && item.format in setOf(FormatType.CBZ, FormatType.CBR)

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

private fun File.rewriteStoredRar(
    vararg entries: Pair<String, ByteArray>,
) {
    writeBytes(storedRarBytes(*entries))
}

private fun storedRarBytes(
    vararg entries: Pair<String, ByteArray>,
): ByteArray = ByteArrayOutputStream().use { output ->
    output.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
    output.write(rarHeader(type = 0x73, flags = 0x0000, body = ByteArray(6)))
    entries.forEach { (name, bytes) ->
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val body = ByteArrayOutputStream().use { header ->
            header.writeIntLe(bytes.size)
            header.writeIntLe(bytes.size)
            header.write(0x03)
            header.writeIntLe(bytes.rarCrc32())
            header.writeIntLe(0)
            header.write(20)
            header.write(0x30)
            header.writeShortLe(nameBytes.size)
            header.writeIntLe(0x20)
            header.write(nameBytes)
            header.toByteArray()
        }
        output.write(rarHeader(type = 0x74, flags = 0x8000, body = body))
        output.write(bytes)
    }
    output.write(byteArrayOf(0xC4.toByte(), 0x3D, 0x7B, 0x00, 0x40, 0x07, 0x00))
    output.toByteArray()
}

private fun rarHeader(
    type: Int,
    flags: Int,
    body: ByteArray,
): ByteArray {
    val headerSize = 7 + body.size
    val bytesForCrc = ByteArrayOutputStream().use { output ->
        output.write(type)
        output.writeShortLe(flags)
        output.writeShortLe(headerSize)
        output.write(body)
        output.toByteArray()
    }
    return ByteArrayOutputStream().use { output ->
        output.writeShortLe(bytesForCrc.rarCrc32() and 0xFFFF)
        output.write(bytesForCrc)
        output.toByteArray()
    }
}

private fun ByteArrayOutputStream.writeShortLe(
    value: Int,
) {
    write(value and 0xFF)
    write(value ushr 8 and 0xFF)
}

private fun ByteArrayOutputStream.writeIntLe(
    value: Int,
) {
    write(value and 0xFF)
    write(value ushr 8 and 0xFF)
    write(value ushr 16 and 0xFF)
    write(value ushr 24 and 0xFF)
}

private fun ByteArray.rarCrc32(): Int {
    val crc = CRC32()
    crc.update(this)
    return crc.value.toInt()
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
    format: FormatType = FormatType.CBZ,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("library"),
    remoteId = id,
    title = "Downloaded Comic",
    format = format,
    pageCount = pageCount,
)
