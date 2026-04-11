package com.example.unifiedreader

import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.core.model.remoteItemId
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteDocumentDownloadRepositoryTest {
    @Test
    fun `download invalidates stored document cache entry`() = runBlocking {
        val invalidatedItemIds = mutableListOf<String>()
        val cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher().also { dispatcher ->
            dispatcher.register(invalidatedItemIds::add)
        }
        val repository = TestRemoteDocumentDownloadRepository(
            cacheInvalidationDispatcher = cacheInvalidationDispatcher,
        )
        val item = testRemoteComic(id = "download-cache-entry")

        val uri = repository.downloadToLocalStorage(item)

        assertNotNull(uri)
        assertEquals(listOf(item.id), invalidatedItemIds)
    }

    @Test
    fun `delete invalidates stored document cache entry`() = runBlocking {
        val invalidatedItemIds = mutableListOf<String>()
        val cacheInvalidationDispatcher = DownloadedRemoteComicCacheInvalidationDispatcher().also { dispatcher ->
            dispatcher.register(invalidatedItemIds::add)
        }
        val repository = TestRemoteDocumentDownloadRepository(
            cacheInvalidationDispatcher = cacheInvalidationDispatcher,
        )
        val item = testRemoteComic(id = "delete-cache-entry")
        repository.downloadToLocalStorage(item)
        invalidatedItemIds.clear()

        val deleted = repository.deleteStoredDocument(item)

        assertEquals(true, deleted)
        assertEquals(listOf(item.id), invalidatedItemIds)
        assertNull(repository.storedDocumentUri(item))
    }
}

private class TestRemoteDocumentDownloadRepository(
    private val cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : RemoteDocumentDownloadRepository<TestDownloadStoredSession>(
    context = ApplicationProvider.getApplicationContext(),
    sourceType = SourceType.KAVITA,
    storageDirectoryName = "test-remote-document-downloads",
    supportedFormats = setOf(FormatType.CBZ),
    sessionStore = TestDownloadSourceSessionStore(
        TestDownloadStoredSession(
            sourceType = SourceType.KAVITA,
            displayName = "Test Kavita",
            baseUrl = "https://example.test",
            username = "reader",
        ),
    ),
    missingSessionMessage = "Missing test session.",
) {
    override suspend fun download(
        session: TestDownloadStoredSession,
        item: ReadableItem,
        remoteItemId: String,
        destination: File,
    ) {
        destination.parentFile?.mkdirs()
        destination.writeText("downloaded:$remoteItemId")
    }

    override fun onStoredDocumentChanged(
        item: ReadableItem,
    ) {
        cacheInvalidationDispatcher.invalidate(item.id)
    }
}

private data class TestDownloadStoredSession(
    override val sourceType: SourceType,
    override val displayName: String,
    override val baseUrl: String,
    override val backupBaseUrl: String? = null,
    override val activeBaseUrl: String? = null,
    override val username: String,
) : StoredSourceSession

private class TestDownloadSourceSessionStore(
    initialSession: TestDownloadStoredSession?,
) : SourceSessionStore<TestDownloadStoredSession> {
    private val state = MutableStateFlow(initialSession)

    override val sessionFlow: StateFlow<TestDownloadStoredSession?> = state

    override suspend fun save(
        session: TestDownloadStoredSession,
    ) {
        state.value = session
    }

    override suspend fun clear() {
        state.value = null
    }
}

private fun testRemoteComic(
    id: String,
): ReadableItem = ReadableItem(
    id = SourceType.KAVITA.remoteItemId(id),
    sourceType = SourceType.KAVITA,
    libraryId = SourceType.KAVITA.remoteLibraryId("test-library"),
    remoteId = id,
    title = "Test Comic",
    format = FormatType.CBZ,
)
