package com.example.unifiedreader

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmoryEpubProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmorySession
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteDocumentReadCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `online grimmory epub read writes reader cache without persistent download`() = runTest {
        File(context.cacheDir, "grimmory-reader-documents").deleteRecursively()
        val api = RecordingGrimmoryReadApi()
        val credentialStore = FakeGrimmoryCredentialStore(
            initialSession = grimmoryStoredSession(),
        )
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.GRIMMORY)
        }
        val cache = RemoteDocumentReadCache(
            context = context,
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = credentialStore,
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val item = grimmoryDocumentItem()

        val uri = cache.documentUriForReading(item)

        assertNotNull(uri)
        val cachedFile = File(requireNotNull(uri).path!!)
        assertTrue(cachedFile.isFile)
        assertTrue(cachedFile.path.contains("${File.separator}cache${File.separator}"))
        assertEquals("reader-cache", cachedFile.readText())
        assertEquals(listOf("book-1"), api.downloadedBookIds)
    }

    @Test
    fun `successful grimmory reader cache fetch marks source reachable`() = runTest {
        File(context.cacheDir, "grimmory-reader-documents").deleteRecursively()
        val api = RecordingGrimmoryReadApi()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markUnavailable(SourceType.GRIMMORY)
        }
        val cache = RemoteDocumentReadCache(
            context = context,
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(
                initialSession = grimmoryStoredSession(),
            ),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )

        val uri = cache.documentUriForReading(grimmoryDocumentItem())

        assertNotNull(uri)
        assertEquals(listOf("book-1"), api.downloadedBookIds)
        assertEquals(RemoteSourceReachability.Reachable, connectivityPolicy.reachabilityFor(SourceType.GRIMMORY))
    }

    @Test
    fun `cached grimmory reader cache reuse does not mark source reachable`() = runTest {
        File(context.cacheDir, "grimmory-reader-documents").deleteRecursively()
        val api = RecordingGrimmoryReadApi()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.GRIMMORY)
        }
        val cache = RemoteDocumentReadCache(
            context = context,
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(
                initialSession = grimmoryStoredSession(),
            ),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        val item = grimmoryDocumentItem()
        assertNotNull(cache.documentUriForReading(item))
        connectivityPolicy.markUnavailable(SourceType.GRIMMORY)

        assertNotNull(cache.documentUriForReading(item))

        assertEquals(listOf("book-1"), api.downloadedBookIds)
        assertEquals(RemoteSourceReachability.Unavailable, connectivityPolicy.reachabilityFor(SourceType.GRIMMORY))
    }

    @Test
    fun `cached grimmory reader cache opens while source probe is checking`() = runTest {
        File(context.cacheDir, "grimmory-reader-documents").deleteRecursively()
        val api = RecordingGrimmoryReadApi()
        val item = grimmoryDocumentItem()
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.GRIMMORY)
        }
        val cache = RemoteDocumentReadCache(
            context = context,
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(
                initialSession = grimmoryStoredSession(),
            ),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )
        assertNotNull(cache.documentUriForReading(item))
        connectivityPolicy.markChecking(SourceType.GRIMMORY)
        val checkingCache = RemoteDocumentReadCache(
            context = context,
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            remoteSourceConnectivityPolicy = connectivityPolicy,
        )

        val uri = checkingCache.documentUriForReading(item)

        assertNotNull(uri)
        assertTrue(checkingCache.hasCachedDocument(item))
        assertEquals(listOf("book-1"), api.downloadedBookIds)
        assertEquals(SourceConnectivityState.Checking, connectivityPolicy.reachabilityFor(SourceType.GRIMMORY))
    }
}

private fun grimmoryDocumentItem(): ReadableItem = ReadableItem(
    id = "grimmory:book:1",
    sourceType = SourceType.GRIMMORY,
    libraryId = SourceType.GRIMMORY.remoteLibraryId("library-1"),
    remoteId = "book-1",
    title = "Book",
    format = FormatType.EPUB,
    remoteFileType = "epub",
)

private fun grimmoryStoredSession(): GrimmoryStoredSession = GrimmoryStoredSession(
    displayName = "Grimmory",
    baseUrl = "https://grimmory.example",
    username = "reader",
    accessToken = "access",
    refreshToken = "refresh",
)

private class RecordingGrimmoryReadApi : GrimmoryApi {
    val downloadedBookIds = mutableListOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession = error("Not used")

    override suspend fun refreshSession(
        refreshToken: String,
    ): GrimmorySession = error("Not used")

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = error("Not used")

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> = error("Not used")

    override suspend fun getBookEntries(
        accessToken: String,
        libraryId: String,
    ): List<GrimmoryBookCatalogEntry> = error("Not used")

    override suspend fun getAllBookEntries(
        accessToken: String,
    ): List<GrimmoryBookCatalogEntry> = error("Not used")

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? = error("Not used")

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = error("Not used")

    override fun bookCoverUrl(
        bookId: String,
    ): String = error("Not used")

    override suspend fun updateEpubProgress(
        accessToken: String,
        progress: GrimmoryEpubProgressUpdate,
    ) = error("Not used")

    override suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ) = error("Not used")

    override suspend fun downloadBookContent(
        accessToken: String,
        bookId: String,
        destination: File,
        bookType: String?,
    ) {
        downloadedBookIds += bookId
        destination.writeText("reader-cache")
    }

    override suspend fun downloadBookCover(
        accessToken: String,
        bookId: String,
        coverUrl: String?,
    ): ByteArray = error("Not used")
}
