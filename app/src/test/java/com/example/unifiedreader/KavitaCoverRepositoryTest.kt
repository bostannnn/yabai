package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.LibraryCoverVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.thumbnailDirectoryName
import com.example.unifiedreader.feature.library.covers.thumbnailFileName
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
class KavitaCoverRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PersistentRemoteCoverStore(context)

    @After
    fun tearDown() {
        File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME).deleteRecursively()
        File(
            context.filesDir,
            thumbnailDirectoryName(KAVITA_COVER_DIRECTORY_NAME, LibraryCoverVariant.Grid),
        ).deleteRecursively()
        File(context.cacheDir, KAVITA_LEGACY_COVER_DIRECTORY_NAME).deleteRecursively()
    }

    @Test
    fun `cachedCover marks original as fallback and getCoverUri backfills grid thumbnail`() = runTest {
        val repository = KavitaCoverRepository(
            kavitaApiFactory = KavitaApiFactory { error("Cached Kavita cover should not hit the API.") },
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            coverStore = store,
            thumbnailGenerator = LibraryCoverThumbnailGenerator(),
        )
        val item = sampleItem(
            id = "kavita:item:issue-cached",
            title = "Cached Issue",
            format = com.example.unifiedreader.core.model.FormatType.CBZ,
        ).copy(
            sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "issue-cached",
            remoteFileType = "akira",
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=test",
        )
        val cachedOriginalFile = File(
            File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME),
            item.kavitaCacheFileName("cover"),
        ).apply {
            parentFile?.mkdirs()
            writeBytes(validCoverBytes())
        }
        val expectedThumbnailFile = File(
            File(
                context.filesDir,
                thumbnailDirectoryName(KAVITA_COVER_DIRECTORY_NAME, LibraryCoverVariant.Grid),
            ),
            thumbnailFileName(item.kavitaCacheFileName("cover"), LibraryCoverVariant.Grid),
        )

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
        eraseColor(Color.BLUE)
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
