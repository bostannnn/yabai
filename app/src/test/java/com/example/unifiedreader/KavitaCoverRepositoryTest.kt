package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaChapterInfoDto
import com.example.unifiedreader.data.kavita.KavitaProgressDto
import com.example.unifiedreader.data.kavita.KavitaProgressUpdate
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaSession
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.LibraryCoverVariant
import com.example.unifiedreader.feature.library.covers.LibraryCoverThumbnailGenerator
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
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
    private val coverCacheResolver = RemoteCoverCacheResolver(
        coverStore = store,
        thumbnailGenerator = LibraryCoverThumbnailGenerator(),
    )

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
            coverCacheResolver = coverCacheResolver,
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

        store.warmStorageDirectoryIndex(storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME)

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

    @Test
    fun `getCoverUri rewrites synced primary cover URL to active backup server`() = runTest {
        val api = RecordingCoverKavitaApi()
        val repository = KavitaCoverRepository(
            kavitaApiFactory = KavitaApiFactory { baseUrl ->
                assertEquals("http://backup.example:5000", baseUrl)
                api
            },
            kavitaCredentialStore = FakeKavitaCredentialStore(
                KavitaStoredSession(
                    displayName = "Kavita",
                    baseUrl = "http://primary.example:5000",
                    backupBaseUrl = "http://backup.example:5000",
                    activeBaseUrl = "http://backup.example:5000",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    apiKey = "api-key",
                ),
            ),
            coverCacheResolver = coverCacheResolver,
        )
        val item = sampleItem(
            id = "kavita:item:issue-backup",
            title = "Backup Issue",
            format = com.example.unifiedreader.core.model.FormatType.CBZ,
        ).copy(
            sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "issue-backup",
            remoteFileType = "12",
            coverUrl = "http://primary.example:5000/api/Image/chapter-cover?chapterId=1&apiKey=old",
        )

        val coverUri = repository.getCoverUri(item, LibraryCoverImageVariant.Grid)

        assertTrue(File(requireNotNull(requireNotNull(coverUri).path)).isFile)
        assertEquals(
            "http://backup.example:5000/api/Image/chapter-cover?chapterId=1&apiKey=old",
            api.requestedCoverUrls.single(),
        )
        assertEquals("access-token", api.requestedAccessTokens.single())
        assertEquals("api-key", api.requestedApiKeys.single())
    }

    @Test
    fun `cachedCover reuses primary address cache while connected through backup address`() = runTest {
        val repository = KavitaCoverRepository(
            kavitaApiFactory = KavitaApiFactory { error("Backup-address cache reuse should not hit the API.") },
            kavitaCredentialStore = FakeKavitaCredentialStore(
                KavitaStoredSession(
                    displayName = "Kavita",
                    baseUrl = "http://primary.example:5000",
                    backupBaseUrl = "http://backup.example:5000",
                    activeBaseUrl = "http://backup.example:5000",
                    username = "reader",
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    apiKey = "api-key",
                ),
            ),
            coverCacheResolver = coverCacheResolver,
        )
        val backupItem = sampleItem(
            id = "kavita:item:issue-address-reuse",
            title = "Address Reuse Issue",
            format = com.example.unifiedreader.core.model.FormatType.CBZ,
        ).copy(
            sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
            libraryId = "kavita:library:k-comics",
            remoteId = "issue-address-reuse",
            remoteFileType = "12",
            coverUrl = "http://backup.example:5000/api/Image/chapter-cover?chapterId=1&apiKey=new",
        )
        val primaryItem = backupItem.copy(
            coverUrl = "http://primary.example:5000/api/Image/chapter-cover?chapterId=1&apiKey=old",
        )
        val cachedPrimaryFile = File(
            File(context.filesDir, KAVITA_COVER_DIRECTORY_NAME),
            primaryItem.kavitaCacheFileName("cover"),
        ).apply {
            parentFile?.mkdirs()
            writeBytes(validCoverBytes())
        }

        store.warmStorageDirectoryIndex(storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME)

        val cachedFallback = repository.cachedCover(backupItem, LibraryCoverImageVariant.Grid)

        assertNotNull(cachedFallback)
        assertEquals(Uri.fromFile(cachedPrimaryFile), cachedFallback?.uri)
        assertTrue(requireNotNull(cachedFallback).isPersistent)
        assertFalse(cachedFallback.matchesRequestedVariant)
    }
}

private class RecordingCoverKavitaApi : KavitaApi {
    val requestedCoverUrls = mutableListOf<String>()
    val requestedAccessTokens = mutableListOf<String>()
    val requestedApiKeys = mutableListOf<String?>()

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession = error("Not needed in test.")

    override suspend fun getLibraries(
        accessToken: String,
    ) = emptyList<com.example.unifiedreader.core.model.Library>()

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> = emptyList()

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = emptyList()

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto = error("Not needed in test.")

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = error("Not needed in test.")

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) = Unit

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed in test.")

    override suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray {
        requestedAccessTokens += accessToken
        requestedCoverUrls += coverUrl
        requestedApiKeys += apiKey
        return validCoverBytes()
    }

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    ) = error("Not needed in test.")
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
