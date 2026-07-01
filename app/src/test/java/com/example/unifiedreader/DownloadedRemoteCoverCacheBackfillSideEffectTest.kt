package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadedRemoteCoverCacheBackfillSideEffectTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME).deleteRecursively()
        File(context.cacheDir, "downloaded-cover-backfill.cbz").delete()
        File(context.cacheDir, "downloaded-cover-backfill.pdf").delete()
    }

    @Test
    fun `download completion backfills kavita cover cache from downloaded comic archive`() = runTest {
        val item = ReadableItem(
            id = SourceType.KAVITA.remoteItemId("chapter-1"),
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("library-1"),
            remoteId = "chapter-1",
            remoteFileType = "series-1",
            title = "Downloaded Comic",
            format = FormatType.CBZ,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=secret",
        )
        val archiveFile = File(context.cacheDir, "downloaded-cover-backfill.cbz").apply {
            writeCbzCoverArchive(coverBytes = downloadedCoverBytes())
        }
        val backfiller = DownloadedRemoteCoverCacheBackfiller(
            context = context,
            remoteDownloadDataSources = listOf(
                StoredDocumentDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    storedUrisByItemId = mapOf(item.id to Uri.fromFile(archiveFile)),
                ),
            ),
            coverStore = PersistentRemoteCoverStore(context),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            pdfCoverRenderer = FakeDownloadedPdfCoverRenderer(),
        )
        val effect = DownloadedRemoteCoverCacheBackfillSideEffect(backfiller)

        effect.onDownloadCompleted(item)

        val cachedCover = File(
            File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME),
            item.kavitaCacheFileName("cover"),
        )
        assertTrue(cachedCover.isFile)
        assertTrue(cachedCover.length() > 0L)
    }

    @Test
    fun `download completion backfills kavita cover cache from downloaded pdf first page`() = runTest {
        val item = ReadableItem(
            id = SourceType.KAVITA.remoteItemId("chapter-pdf"),
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("library-1"),
            remoteId = "chapter-pdf",
            remoteFileType = "series-1",
            title = "Downloaded PDF",
            format = FormatType.PDF,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=pdf&apiKey=secret",
        )
        val pdfFile = File(context.cacheDir, "downloaded-cover-backfill.pdf").apply {
            writeText("placeholder pdf")
        }
        val pdfRenderer = FakeDownloadedPdfCoverRenderer(coverBytes = downloadedCoverBytes())
        val backfiller = DownloadedRemoteCoverCacheBackfiller(
            context = context,
            remoteDownloadDataSources = listOf(
                StoredDocumentDownloadDataSource(
                    sourceType = SourceType.KAVITA,
                    storedUrisByItemId = mapOf(item.id to Uri.fromFile(pdfFile)),
                ),
            ),
            coverStore = PersistentRemoteCoverStore(context),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            pdfCoverRenderer = pdfRenderer,
        )
        val effect = DownloadedRemoteCoverCacheBackfillSideEffect(backfiller)

        assertTrue(effect.supports(item))

        effect.onDownloadCompleted(item)

        val cachedCover = File(
            File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME),
            item.kavitaCacheFileName("cover"),
        )
        assertTrue(cachedCover.isFile)
        assertTrue(cachedCover.length() > 0L)
        assertTrue(Uri.fromFile(pdfFile) in pdfRenderer.requestedUris)
    }
}

private class StoredDocumentDownloadDataSource(
    override val sourceType: SourceType,
    private val storedUrisByItemId: Map<String, Uri>,
) : RemoteItemDownloadDataSource {
    override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? = storedUrisByItemId[item.id]

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = storedDocumentUri(item)

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = items
        .filter { item -> item.id in storedUrisByItemId }
        .mapTo(mutableSetOf(), ReadableItem::id)

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private class FakeDownloadedPdfCoverRenderer(
    private val coverBytes: ByteArray? = null,
) : DownloadedPdfCoverRenderer {
    val requestedUris = mutableListOf<Uri>()

    override suspend fun coverBytes(
        uri: Uri,
    ): ByteArray? {
        requestedUris += uri
        return coverBytes
    }
}

private fun File.writeCbzCoverArchive(
    coverBytes: ByteArray,
) {
    parentFile?.mkdirs()
    ZipOutputStream(outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("001.jpg"))
        zip.write(coverBytes)
        zip.closeEntry()
    }
}

private fun downloadedCoverBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(6, 9, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.GREEN)
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
