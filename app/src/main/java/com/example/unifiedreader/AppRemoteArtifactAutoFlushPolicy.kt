package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRemoteArtifactAutoFlushPolicy(
    private val currentTimeMillis: () -> Long,
) {
    @Inject constructor() : this(System::currentTimeMillis)

    private val attemptsBySource = linkedMapOf<SourceType, SourceAutoFlushAttempt>()

    @Synchronized
    fun eligibleItemsForAutomaticFlush(
        pendingKeys: List<AppRemoteArtifactAutoFlushKey>,
        remoteItems: List<ReadableItem>,
    ): List<ReadableItem> {
        if (pendingKeys.isEmpty() || remoteItems.isEmpty()) return emptyList()
        val now = currentTimeMillis()
        val fingerprintsBySource = fingerprintsBySource(
            pendingKeys = pendingKeys,
            remoteItems = remoteItems,
        )
        if (fingerprintsBySource.isEmpty()) return emptyList()
        val eligibleSources = fingerprintsBySource
            .filter { (sourceType, fingerprint) ->
                canAttemptSource(
                    sourceType = sourceType,
                    fingerprint = fingerprint,
                    nowEpochMillis = now,
                )
            }
            .keys
        if (eligibleSources.isEmpty()) return emptyList()
        return remoteItems.filter { item -> item.sourceType in eligibleSources }
    }

    @Synchronized
    fun recordAutomaticFlushAttempt(
        pendingKeys: List<AppRemoteArtifactAutoFlushKey>,
        attemptedItems: List<ReadableItem>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        recordAttempt(
            pendingKeys = pendingKeys,
            attemptedItems = attemptedItems,
            failures = failures,
        )
    }

    @Synchronized
    fun recordExternalFlushAttempt(
        pendingKeys: List<AppRemoteArtifactAutoFlushKey>,
        attemptedItems: List<ReadableItem>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        recordAttempt(
            pendingKeys = pendingKeys,
            attemptedItems = attemptedItems,
            failures = failures,
        )
    }

    private fun canAttemptSource(
        sourceType: SourceType,
        fingerprint: SourceAutoFlushFingerprint,
        nowEpochMillis: Long,
    ): Boolean {
        val attempt = attemptsBySource[sourceType] ?: return true
        if (nowEpochMillis < attempt.nextAllowedAtEpochMillis) return false
        if (!attempt.failed && attempt.fingerprint == fingerprint) return false
        return true
    }

    private fun recordAttempt(
        pendingKeys: List<AppRemoteArtifactAutoFlushKey>,
        attemptedItems: List<ReadableItem>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        if (pendingKeys.isEmpty() || attemptedItems.isEmpty()) return
        val now = currentTimeMillis()
        val failedSources = failures.mapTo(mutableSetOf(), SourceProgressSyncFailure::sourceType)
        fingerprintsBySource(
            pendingKeys = pendingKeys,
            remoteItems = attemptedItems,
        ).forEach { (sourceType, fingerprint) ->
            val failed = sourceType in failedSources
            attemptsBySource[sourceType] = SourceAutoFlushAttempt(
                fingerprint = fingerprint,
                nextAllowedAtEpochMillis = now + if (failed) {
                    AUTO_REMOTE_ARTIFACT_FLUSH_FAILURE_BACKOFF_MILLIS
                } else {
                    AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS
                },
                failed = failed,
            )
        }
    }

    private fun fingerprintsBySource(
        pendingKeys: List<AppRemoteArtifactAutoFlushKey>,
        remoteItems: List<ReadableItem>,
    ): Map<SourceType, SourceAutoFlushFingerprint> {
        val keysByItemId = pendingKeys.groupBy(AppRemoteArtifactAutoFlushKey::itemId)
        val fingerprintPartsBySource = linkedMapOf<SourceType, MutableSet<String>>()
        remoteItems.forEach { item ->
            keysByItemId[item.id].orEmpty().forEach { key ->
                fingerprintPartsBySource
                    .getOrPut(item.sourceType, ::linkedSetOf)
                    .add(key.fingerprintPart)
            }
        }
        return fingerprintPartsBySource.mapValues { (_, parts) ->
            SourceAutoFlushFingerprint(parts.toList().sorted())
        }.filterValues { fingerprint -> fingerprint.parts.isNotEmpty() }
    }
}

data class AppRemoteArtifactAutoFlushKey(
    val artifact: RemoteSyncArtifact,
    val itemId: String,
    val updatedAtEpochMillis: Long?,
) {
    val fingerprintPart: String = "${artifact.name}:$itemId:${updatedAtEpochMillis ?: 0L}"
}

fun pendingRemoteArtifactAutoFlushKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    journalSnapshot: JournalSnapshot,
): List<AppRemoteArtifactAutoFlushKey> = (
    pendingProgressAutoFlushKeys(progressByItemId) + pendingJournalAutoFlushKeys(journalSnapshot)
    ).distinct().sortedBy(AppRemoteArtifactAutoFlushKey::fingerprintPart)

private fun pendingProgressAutoFlushKeys(
    progressByItemId: Map<String, ReaderProgressSnapshot>,
): List<AppRemoteArtifactAutoFlushKey> = progressByItemId.values
    .filter(ReaderProgressSnapshot::isPendingRemoteSync)
    .map { progress ->
        AppRemoteArtifactAutoFlushKey(
            artifact = RemoteSyncArtifact.PROGRESS,
            itemId = progress.itemId,
            updatedAtEpochMillis = progress.updatedAtEpochMillis,
        )
    }

private fun pendingJournalAutoFlushKeys(
    snapshot: JournalSnapshot,
): List<AppRemoteArtifactAutoFlushKey> =
    snapshot.entries.mapNotNull(ItemJournalEntry::pendingAutoFlushKey) +
        snapshot.sessions.mapNotNull(JournalReadingSession::pendingAutoFlushKey)

private fun ItemJournalEntry.pendingAutoFlushKey(): AppRemoteArtifactAutoFlushKey? =
    syncState.takeIf { state -> state.toPendingRemoteSyncItemState() != null }?.let {
        AppRemoteArtifactAutoFlushKey(
            artifact = RemoteSyncArtifact.JOURNAL_ENTRY,
            itemId = itemId,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

private fun JournalReadingSession.pendingAutoFlushKey(): AppRemoteArtifactAutoFlushKey? =
    syncState.takeIf { state -> state.toPendingRemoteSyncItemState() != null }?.let {
        AppRemoteArtifactAutoFlushKey(
            artifact = RemoteSyncArtifact.READING_SESSION,
            itemId = itemId,
            updatedAtEpochMillis = endedAtEpochMillis,
        )
    }

private data class SourceAutoFlushAttempt(
    val fingerprint: SourceAutoFlushFingerprint,
    val nextAllowedAtEpochMillis: Long,
    val failed: Boolean,
)

private data class SourceAutoFlushFingerprint(
    val parts: List<String>,
)

internal const val AUTO_REMOTE_ARTIFACT_FLUSH_THROTTLE_MILLIS = 15_000L
internal const val AUTO_REMOTE_ARTIFACT_FLUSH_FAILURE_BACKOFF_MILLIS = 60_000L
