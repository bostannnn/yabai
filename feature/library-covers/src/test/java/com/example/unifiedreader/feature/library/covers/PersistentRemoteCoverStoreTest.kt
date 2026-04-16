package com.example.unifiedreader.feature.library.covers

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
        File(context.filesDir, THUMBNAIL_DIRECTORY).deleteRecursively()
    }

    @Test
    fun `cached cover lookup can discover unwarmed legacy file and move still migrates it`() = runTest {
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
    fun `cached thumbnail lookup can discover unwarmed file immediately`() = runTest {
        val thumbnailFile = File(File(context.filesDir, THUMBNAIL_DIRECTORY), FILE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(7, 7, 7))
        }

        assertTrue(
            store.cachedThumbnailFile(
                thumbnailDirectoryName = THUMBNAIL_DIRECTORY,
                fileName = FILE_NAME,
            ) == thumbnailFile,
        )

        store.warmStorageDirectoryIndex(storageDirectoryName = THUMBNAIL_DIRECTORY)

        assertTrue(
            store.cachedThumbnailFile(
                thumbnailDirectoryName = THUMBNAIL_DIRECTORY,
                fileName = FILE_NAME,
            ) == thumbnailFile,
        )
    }

    @Test
    fun `warmStorageDirectoryIndex indexes existing persistent and legacy covers`() = runTest {
        val persistentFile = File(File(context.filesDir, STORAGE_DIRECTORY), FILE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 8, 7))
        }
        val legacyOnlyFile = File(File(context.cacheDir, LEGACY_DIRECTORY), LEGACY_ONLY_FILE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }

        store.warmStorageDirectoryIndex(
            storageDirectoryName = STORAGE_DIRECTORY,
            legacyDirectoryName = LEGACY_DIRECTORY,
        )

        val persistentCover = store.cachedCoverFile(
            storageDirectoryName = STORAGE_DIRECTORY,
            fileName = FILE_NAME,
            legacyDirectoryName = LEGACY_DIRECTORY,
        )
        val legacyCover = store.cachedCoverFile(
            storageDirectoryName = STORAGE_DIRECTORY,
            fileName = LEGACY_ONLY_FILE_NAME,
            legacyDirectoryName = LEGACY_DIRECTORY,
        )

        assertNotNull(persistentCover)
        assertTrue(requireNotNull(persistentCover).isPersistent)
        assertTrue(persistentCover.file == persistentFile)

        assertNotNull(legacyCover)
        assertFalse(requireNotNull(legacyCover).isPersistent)
        assertTrue(legacyCover.file == legacyOnlyFile)
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
private const val THUMBNAIL_DIRECTORY = "remote-cover-store-test-thumbnails"
private const val FILE_NAME = "issue-1.cover"
private const val LEGACY_ONLY_FILE_NAME = "issue-2.cover"
