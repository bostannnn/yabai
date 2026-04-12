package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        File(context.cacheDir, "stump-covers").deleteRecursively()
    }

    @Test
    fun `getCoverUri migrates legacy stump cover before attempting a download`() = runTest {
        val repository = StumpCoverRepository(
            stumpApiFactory = StumpApiFactory { error("Legacy migration should not hit the Stump API.") },
            stumpCredentialStore = FakeStumpCredentialStore(),
            coverStore = store,
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
            writeBytes(byteArrayOf(9, 8, 7))
        }
        val expectedPersistentFile = File(File(context.filesDir, "stump-covers"), item.stumpCacheFileName("cover"))

        val cachedUri = repository.getCoverUri(item)

        assertEquals(Uri.fromFile(expectedPersistentFile), cachedUri)
        assertTrue(expectedPersistentFile.isFile)
        assertFalse(legacyFile.exists())
    }
}
