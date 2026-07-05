package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRemoteArtifactAutoFlushPolicyTest {
    @Test
    fun `unchanged successful fingerprint is deduped after throttle window`() {
        val clock = TestAutoFlushClock()
        val policy = AppRemoteArtifactAutoFlushPolicy(clock::now)
        val item = testRemoteItem()
        val key = testFlushKey(item = item, updatedAtEpochMillis = 100L)

        assertEquals(listOf(item), policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)))
        policy.recordAutomaticFlushAttempt(
            pendingKeys = listOf(key),
            attemptedItems = listOf(item),
            failures = emptyList(),
        )
        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS)

        assertTrue(policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)).isEmpty())
    }

    @Test
    fun `changed fingerprint waits for duplicate throttle`() {
        val clock = TestAutoFlushClock()
        val policy = AppRemoteArtifactAutoFlushPolicy(clock::now)
        val item = testRemoteItem()
        val firstKey = testFlushKey(item = item, updatedAtEpochMillis = 100L)
        val changedKey = testFlushKey(item = item, updatedAtEpochMillis = 200L)

        policy.recordAutomaticFlushAttempt(
            pendingKeys = listOf(firstKey),
            attemptedItems = listOf(item),
            failures = emptyList(),
        )
        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS - 1L)

        assertTrue(policy.eligibleItemsForAutomaticFlush(listOf(changedKey), listOf(item)).isEmpty())

        clock.advance(1L)

        assertEquals(listOf(item), policy.eligibleItemsForAutomaticFlush(listOf(changedKey), listOf(item)))
    }

    @Test
    fun `failed fingerprint waits for failure backoff`() {
        val clock = TestAutoFlushClock()
        val policy = AppRemoteArtifactAutoFlushPolicy(clock::now)
        val item = testRemoteItem()
        val key = testFlushKey(item = item, updatedAtEpochMillis = 100L)

        policy.recordAutomaticFlushAttempt(
            pendingKeys = listOf(key),
            attemptedItems = listOf(item),
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = item.sourceType,
                    throwable = IllegalStateException("No route"),
                ),
            ),
        )
        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS)

        assertTrue(policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)).isEmpty())

        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_FAILURE_BACKOFF_MILLIS - AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS)

        assertEquals(listOf(item), policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)))
    }

    @Test
    fun `external failed attempt backs off later automatic flush`() {
        val clock = TestAutoFlushClock()
        val policy = AppRemoteArtifactAutoFlushPolicy(clock::now)
        val item = testRemoteItem()
        val key = testFlushKey(item = item, updatedAtEpochMillis = 100L)

        policy.recordExternalFlushAttempt(
            pendingKeys = listOf(key),
            attemptedItems = listOf(item),
            failures = listOf(
                SourceProgressSyncFailure(
                    sourceType = item.sourceType,
                    throwable = IllegalStateException("No route"),
                ),
            ),
        )
        clock.advance(AUTO_REMOTE_ARTIFACT_FLUSH_FAILURE_BACKOFF_MILLIS - 1L)

        assertTrue(policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)).isEmpty())

        clock.advance(1L)

        assertEquals(listOf(item), policy.eligibleItemsForAutomaticFlush(listOf(key), listOf(item)))
    }
}

private class TestAutoFlushClock(
    private var nowEpochMillis: Long = 1_000L,
) {
    fun now(): Long = nowEpochMillis

    fun advance(millis: Long) {
        nowEpochMillis += millis
    }
}

private fun testFlushKey(
    item: ReadableItem,
    updatedAtEpochMillis: Long,
): AppRemoteArtifactAutoFlushKey = AppRemoteArtifactAutoFlushKey(
    artifact = RemoteSyncArtifact.PROGRESS,
    itemId = item.id,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun testRemoteItem(
    id: String = "kavita:item:pending",
    sourceType: SourceType = SourceType.KAVITA,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:1",
    remoteId = "pending",
    title = "Pending Comic",
    format = FormatType.CBZ,
    pageCount = 12,
)
