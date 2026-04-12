package com.example.unifiedreader

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistentRemoteCoverStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PersistentRemoteCoverStore(context)

    @After
    fun tearDown() {
        File(context.filesDir, STORAGE_DIRECTORY).deleteRecursively()
        File(context.cacheDir, LEGACY_DIRECTORY).deleteRecursively()
    }

    @Test
    fun `cachedCoverFile marks legacy cache files as non persistent and move migrates them`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val legacyFile = File(File(context.cacheDir, LEGACY_DIRECTORY), FILE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

        val cachedCover = store.cachedCoverFile(
            storageDirectoryName = STORAGE_DIRECTORY,
            fileName = FILE_NAME,
            legacyDirectoryName = LEGACY_DIRECTORY,
        )

        assertNotNull(cachedCover)
        assertFalse(requireNotNull(cachedCover).isPersistent)
        assertTrue(cachedCover.file == legacyFile)

        val migratedFile = store.moveLegacyCoverIfPresent(
            storageDirectoryName = STORAGE_DIRECTORY,
            fileName = FILE_NAME,
            legacyDirectoryName = LEGACY_DIRECTORY,
        )

        assertNotNull(migratedFile)
        assertTrue(requireNotNull(migratedFile).isFile)
        assertTrue(migratedFile.parentFile == File(context.filesDir, STORAGE_DIRECTORY))
        assertFalse(legacyFile.exists())
        assertArrayEquals(bytes, migratedFile.readBytes())
    }

    @Test
    fun `clearStorageDirectory waits for in flight storage work`() = runBlocking {
        val enteredLock = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()
        val heldLock = launch(Dispatchers.Default) {
            store.withStorageLock(STORAGE_DIRECTORY) {
                enteredLock.complete(Unit)
                releaseLock.await()
            }
        }
        enteredLock.await()

        val clearJob = async(Dispatchers.Default) {
            store.clearStorageDirectory(STORAGE_DIRECTORY, LEGACY_DIRECTORY)
        }

        assertNull(withTimeoutOrNull(100) { clearJob.await() })

        releaseLock.complete(Unit)
        clearJob.await()
        heldLock.join()
    }
}

private const val STORAGE_DIRECTORY = "remote-cover-store-test"
private const val LEGACY_DIRECTORY = "remote-cover-store-test-legacy"
private const val FILE_NAME = "issue-1.cover"
