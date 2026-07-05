package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComicArchiveSupportTest {
    @Test
    fun `natural page order sorts numbered filenames as expected`() {
        val sorted = listOf(
            "page10.jpg",
            "page2.jpg",
            "page1.jpg",
            "page11.jpg",
        ).sortedWith(NaturalPageOrderComparator)

        assertEquals(
            listOf("page1.jpg", "page2.jpg", "page10.jpg", "page11.jpg"),
            sorted,
        )
    }

    @Test
    fun `comic archive title strips cbz and cbr suffixes case insensitively`() {
        assertEquals("Akira v01", "Akira v01.cbz".comicArchiveTitle())
        assertEquals("Blame! v02", "Blame! v02.CBR".comicArchiveTitle())
        assertEquals("Planetes", "Planetes".comicArchiveTitle())
    }

    @Test
    fun `zip codec reads deep entries from local archive file`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val archiveFile = File(context.cacheDir, "zip-codec-deep-entry.cbz").apply {
            delete()
        }
        try {
            archiveFile.writeBytes(createZipArchiveBytes(pageCount = 150))

            val uri = Uri.fromFile(archiveFile)
            val entries = ZipComicArchiveCodec.listEntries(context, uri)
            val pageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = uri,
                entryName = "page-149.jpg",
            )

            assertEquals(150, entries.size)
            assertEquals("page-000.jpg", entries.first())
            assertEquals("page-149.jpg", entries.last())
            assertEquals("image-149", pageBytes?.toString(Charsets.UTF_8))
        } finally {
            archiveFile.delete()
        }
    }

    @Test
    fun `zip codec caches content uri archive before reading deep entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val archiveBytes = createZipArchiveBytes(pageCount = 150)
        val uri = Uri.parse("content://imports/zip-codec-deep-entry.cbz")
        var openCount = 0
        ZipComicArchiveCodec.clearLocalArchiveCache(context)
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            openCount += 1
            ByteArrayInputStream(archiveBytes)
        }
        try {
            val entries = ZipComicArchiveCodec.listEntries(context, uri)
            val pageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = uri,
                entryName = "page-149.jpg",
            )

            assertEquals(150, entries.size)
            assertEquals("image-149", pageBytes?.toString(Charsets.UTF_8))
            assertEquals(1, openCount)
        } finally {
            ZipComicArchiveCodec.clearLocalArchiveCache(context)
        }
    }

    @Test
    fun `zip codec keeps colliding uri hash archives isolated`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstUri = Uri.parse("content://imports/Aa.cbz")
        val secondUri = Uri.parse("content://imports/BB.cbz")
        assertEquals(firstUri.toString().hashCode(), secondUri.toString().hashCode())
        ZipComicArchiveCodec.clearLocalArchiveCache(context)
        shadowOf(context.contentResolver).registerInputStreamSupplier(firstUri) {
            ByteArrayInputStream(createZipArchiveBytes(pageCount = 1, imagePrefix = "first"))
        }
        shadowOf(context.contentResolver).registerInputStreamSupplier(secondUri) {
            ByteArrayInputStream(createZipArchiveBytes(pageCount = 1, imagePrefix = "second"))
        }
        try {
            val firstPageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = firstUri,
                entryName = "page-000.jpg",
            )
            val secondPageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = secondUri,
                entryName = "page-000.jpg",
            )
            val firstPageBytesAgain = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = firstUri,
                entryName = "page-000.jpg",
            )

            assertEquals("first-0", firstPageBytes?.toString(Charsets.UTF_8))
            assertEquals("second-0", secondPageBytes?.toString(Charsets.UTF_8))
            assertEquals("first-0", firstPageBytesAgain?.toString(Charsets.UTF_8))
        } finally {
            ZipComicArchiveCodec.clearLocalArchiveCache(context)
        }
    }

    @Test
    fun `zip codec keeps previous cached archive when refresh fails`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val archiveBytes = createZipArchiveBytes(pageCount = 150)
        val uri = Uri.parse("content://imports/zip-codec-refresh-failure.cbz")
        var openCount = 0
        ZipComicArchiveCodec.clearLocalArchiveCache(context)
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            openCount += 1
            ByteArrayInputStream(archiveBytes)
        }
        try {
            val cachedPageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = uri,
                entryName = "page-149.jpg",
            )
            ageZipArchiveCache(context)
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
                openCount += 1
                error("Archive stream is unavailable")
            }

            val fallbackPageBytes = ZipComicArchiveCodec.readEntryBytes(
                context = context,
                uri = uri,
                entryName = "page-149.jpg",
            )

            assertEquals("image-149", cachedPageBytes?.toString(Charsets.UTF_8))
            assertEquals("image-149", fallbackPageBytes?.toString(Charsets.UTF_8))
            assertEquals(2, openCount)
        } finally {
            ZipComicArchiveCodec.clearLocalArchiveCache(context)
        }
    }

    @Test
    fun `cache refresh is skipped for unknown signature while ttl is still fresh`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "unknown",
            cachedSignature = "unknown",
            localFileLastModified = 10_000L,
            nowEpochMillis = 65_000L,
        )

        assertFalse(shouldRefresh)
    }

    @Test
    fun `cache refresh happens for unknown signature after ttl expires`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "unknown",
            cachedSignature = "unknown",
            localFileLastModified = 10_000L,
            nowEpochMillis = 71_000L,
        )

        assertTrue(shouldRefresh)
    }

    @Test
    fun `cache refresh happens when signature changes`() {
        val shouldRefresh = ComicArchiveCodecsTestAccess.shouldRefreshArchiveCache(
            localFileExists = true,
            localFileLength = 128L,
            currentSignature = "200:1000",
            cachedSignature = "100:1000",
            localFileLastModified = 10_000L,
            nowEpochMillis = 20_000L,
        )

        assertTrue(shouldRefresh)
    }

    @Test
    fun `build item progress summary clamps page index and returns null for empty page count`() {
        assertNull(AppViewModelTestAccess.buildItemProgressSummary(pageIndex = 3, pageCount = 0))

        val summary = AppViewModelTestAccess.buildItemProgressSummary(pageIndex = 99, pageCount = 12)

        assertEquals("Resume on page 12 of 12", summary?.label)
        assertEquals(1f, summary?.fraction)
    }

    private fun createZipArchiveBytes(
        pageCount: Int,
        imagePrefix: String = "image",
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(pageCount) { index ->
                val entryName = "page-${index.toString().padStart(3, '0')}.jpg"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write("$imagePrefix-$index".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ageZipArchiveCache(
        context: Context,
    ) {
        val staleLastModified = System.currentTimeMillis() - 120_000L
        ComicArchiveCodecsTestAccess.zipArchiveCacheDirectory(context)
            .listFiles()
            .orEmpty()
            .forEach { file -> file.setLastModified(staleLastModified) }
    }
}
