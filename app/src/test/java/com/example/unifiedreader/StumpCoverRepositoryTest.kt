package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.STUMP_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.thumbnailDirectoryName
import com.example.unifiedreader.feature.library.covers.thumbnailFileName
import java.io.File
import java.io.ByteArrayOutputStream
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
class StumpCoverRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PersistentRemoteCoverStore(context)

    @After
    fun tearDown() {
        File(context.filesDir, "stump-covers").deleteRecursively()
        File(
            context.filesDir,
            thumbnailDirectoryName(STUMP_COVER_DIRECTORY_NAME, LibraryCoverVariant.Grid),
        ).deleteRecursively()
        File(context.cacheDir, "stump-covers").deleteRecursively()
    }

    @Test
    fun `getCoverUri migrates legacy stump cover before attempting a download`() = runTest {
        val repository = StumpCoverRepository(
            stumpApiFactory = StumpApiFactory { error("Legacy migration should not hit the Stump API.") },
            stumpCredentialStore = FakeStumpCredentialStore(),
            coverStore = store,
            thumbnailGenerator = LibraryCoverThumbnailGenerator(),
        )
        val item = sampleItem(
            id = "stump:item:issue-legacy",
            title = "Legacy Issue",
            format = com.example.unifiedreader.core.model.FormatType.CBZ,
        ).copy(
            sourceType = com.example.unifiedreader.core.model.SourceType.STUMP,
            libraryId = "stump:library:stump-library",
            remoteId = "issue-legacy",
            remoteFileType = "series-legacy",
            coverUrl = "https://stump.example/api/v1/media/issue-legacy/thumbnail",
        )
        val legacyFile = File(File(context.cacheDir, "stump-covers"), item.stumpCacheFileName("cover")).apply {
            parentFile?.mkdirs()
            writeBytes(validCoverBytes())
        }
        val expectedPersistentFile = File(File(context.filesDir, "stump-covers"), item.stumpCacheFileName("cover"))
        val expectedThumbnailFile = File(
            File(
                context.filesDir,
                thumbnailDirectoryName(STUMP_COVER_DIRECTORY_NAME, LibraryCoverVariant.Grid),
            ),
            thumbnailFileName(item.stumpCacheFileName("cover"), LibraryCoverVariant.Grid),
        )

        val cachedUri = repository.getCoverUri(item, LibraryCoverImageVariant.Grid)

        assertEquals(Uri.fromFile(expectedThumbnailFile), cachedUri)
        assertTrue(expectedPersistentFile.isFile)
        assertTrue(expectedThumbnailFile.isFile)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun `cachedCover marks original as fallback and getCoverUri backfills grid thumbnail`() = runTest {
        val repository = StumpCoverRepository(
            stumpApiFactory = StumpApiFactory { error("Cached Stump cover should not hit the API.") },
            stumpCredentialStore = FakeStumpCredentialStore(),
            coverStore = store,
            thumbnailGenerator = LibraryCoverThumbnailGenerator(),
        )
        val item = sampleItem(
            id = "stump:item:issue-cached",
            title = "Cached Issue",
            format = com.example.unifiedreader.core.model.FormatType.CBZ,
        ).copy(
            sourceType = com.example.unifiedreader.core.model.SourceType.STUMP,
            libraryId = "stump:library:stump-library",
            remoteId = "issue-cached",
            remoteFileType = "series-cached",
            coverUrl = "https://stump.example/api/v1/media/issue-cached/thumbnail",
        )
        val cachedOriginalFile = File(
            File(context.filesDir, STUMP_COVER_DIRECTORY_NAME),
            item.stumpCacheFileName("cover"),
        ).apply {
            parentFile?.mkdirs()
            writeBytes(validCoverBytes())
        }
        val expectedThumbnailFile = File(
            File(
                context.filesDir,
                thumbnailDirectoryName(STUMP_COVER_DIRECTORY_NAME, LibraryCoverVariant.Grid),
            ),
            thumbnailFileName(item.stumpCacheFileName("cover"), LibraryCoverVariant.Grid),
        )

        store.warmStorageDirectoryIndex(storageDirectoryName = STUMP_COVER_DIRECTORY_NAME)

        val cachedFallback = repository.cachedCover(item, LibraryCoverImageVariant.Grid)

        assertNotNull(cachedFallback)
        assertEquals(Uri.fromFile(cachedOriginalFile), cachedFallback?.uri)
        assertTrue(requireNotNull(cachedFallback).isPersistent)
        assertFalse(cachedFallback.matchesRequestedVariant)

        val coverUri = repository.getCoverUri(item, LibraryCoverImageVariant.Grid)
        val cachedThumbnail = repository.cachedCover(item, LibraryCoverImageVariant.Grid)

        assertEquals(Uri.fromFile(expectedThumbnailFile), coverUri)
        assertTrue(expectedThumbnailFile.isFile)
        assertNotNull(cachedThumbnail)
        assertEquals(Uri.fromFile(expectedThumbnailFile), cachedThumbnail?.uri)
        assertTrue(requireNotNull(cachedThumbnail).matchesRequestedVariant)
    }
}

private fun validCoverBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(6, 9, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.RED)
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
