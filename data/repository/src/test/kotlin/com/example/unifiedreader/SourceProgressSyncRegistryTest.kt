package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SourceProgressSyncRegistryTest {
    @Test
    fun `sync item rethrows cancellation`() = runTest {
        val item = remoteItem()
        val registry = SourceProgressSyncRegistry(
            listOf(
                RegistryRecordingSourceProgressSync(
                    sourceType = SourceType.GRIMMORY,
                    syncItemError = CancellationException("cancelled"),
                ),
            ),
        )

        try {
            registry.syncItem(item)
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }
    }

    @Test
    fun `push pending progress rethrows cancellation`() = runTest {
        val item = remoteItem()
        val registry = SourceProgressSyncRegistry(
            listOf(
                RegistryRecordingSourceProgressSync(
                    sourceType = SourceType.GRIMMORY,
                    pushError = CancellationException("cancelled"),
                ),
            ),
        )

        try {
            registry.pushPendingProgress(listOf(item))
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("cancelled", exception.message)
        }
    }
}

private fun remoteItem(): ReadableItem = ReadableItem(
    id = "grimmory:item:1",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    remoteId = "1",
    title = "Book 1",
    format = FormatType.EPUB,
)

private class RegistryRecordingSourceProgressSync(
    override val sourceType: SourceType,
    private val syncItemError: Throwable? = null,
    private val pushError: Throwable? = null,
) : SourceProgressSync {
    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        syncItemError?.let { throw it }
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        pushError?.let { throw it }
        return emptyList()
    }
}
