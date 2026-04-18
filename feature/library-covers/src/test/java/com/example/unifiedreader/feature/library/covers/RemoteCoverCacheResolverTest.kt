package com.example.unifiedreader.feature.library.covers

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteCoverCacheResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PersistentRemoteCoverStore(context)
    private val resolver = RemoteCoverCacheResolver(
        coverStore = store,
        thumbnailGenerator = LibraryCoverThumbnailGenerator(),
    )

    @After
    fun tearDown() {
        File(context.filesDir, STORAGE.storageDirectoryName).deleteRecursively()
        File(context.cacheDir, requireNotNull(STORAGE.legacyDirectoryName)).deleteRecursively()
        File(
            context.filesDir,
            thumbnailDirectoryName(STORAGE.storageDirectoryName, LibraryCoverVariant.Grid),
        ).deleteRecursively()
    }

    @Test
    fun `resolveCoverFile backfills thumbnail from cached original`() = runTest {
        val baseFileName = "resolver-test.cover"
        val originalFile = File(
            File(context.filesDir, STORAGE.storageDirectoryName),
            baseFileName,
        ).apply {
            parentFile?.mkdirs()
            writeBytes(validCoverBytes())
        }

        val cachedFallback = resolver.cachedCover(
            storage = STORAGE,
            baseFileName = baseFileName,
            variant = LibraryCoverVariant.Grid,
        )

        assertNotNull(cachedFallback)
        assertEquals(originalFile, cachedFallback?.file)
        assertTrue(requireNotNull(cachedFallback).isPersistent)
        assertFalse(cachedFallback.matchesRequestedVariant)

        val resolvedFile = resolver.resolveCoverFile(
            storage = STORAGE,
            baseFileName = baseFileName,
            variant = LibraryCoverVariant.Grid,
            downloadOriginal = { error("Existing original should not trigger a download.") },
        )
        val cachedThumbnail = resolver.cachedCover(
            storage = STORAGE,
            baseFileName = baseFileName,
            variant = LibraryCoverVariant.Grid,
        )

        assertTrue(resolvedFile.isFile)
        assertNotNull(cachedThumbnail)
        assertEquals(resolvedFile, cachedThumbnail?.file)
        assertTrue(requireNotNull(cachedThumbnail).matchesRequestedVariant)
    }
}

private val STORAGE = RemoteCoverStorageDirectory(
    storageDirectoryName = "resolver-cover-store-test",
    legacyDirectoryName = "resolver-cover-store-test-legacy",
)

private fun validCoverBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(6, 9, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLUE)
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
