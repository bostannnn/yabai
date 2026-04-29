package com.example.unifiedreader.data.repository

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.FlushPendingSourceProgressUseCase
import com.example.unifiedreader.SourceProgressSyncFailure
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSuppressWildcards

enum class RemoteSyncArtifact {
    PROGRESS,
    JOURNAL_ENTRY,
    READING_SESSION,
}

data class RemoteSyncCapability(
    val sourceType: SourceType,
    val supportedArtifacts: Set<RemoteSyncArtifact>,
) {
    fun supports(
        artifact: RemoteSyncArtifact,
    ): Boolean = artifact in supportedArtifacts
}

data class RemoteArtifactFlushRequest(
    val items: List<ReadableItem>,
    val artifacts: Set<RemoteSyncArtifact> = RemoteSyncArtifact.entries.toSet(),
)

interface SourceRemoteArtifactSupport {
    val sourceType: SourceType
    val supportedArtifacts: Set<RemoteSyncArtifact>
}

class FlushPendingRemoteArtifactsUseCase @Inject constructor(
    private val flushPendingSourceProgressUseCase: FlushPendingSourceProgressUseCase,
    private val remoteJournalPusher: RemoteJournalPusher,
    private val sourceJournalPushers: List<@JvmSuppressWildcards SourceRemoteJournalPusher>,
) {
    suspend operator fun invoke(
        request: RemoteArtifactFlushRequest,
    ): List<SourceProgressSyncFailure> {
        val remoteItems = request.items.filter { item -> item.sourceType != SourceType.LOCAL }
        if (remoteItems.isEmpty() || request.artifacts.isEmpty()) return emptyList()

        val failures = mutableListOf<SourceProgressSyncFailure>()
        val itemsBySource = remoteItems.groupBy(ReadableItem::sourceType)
        val capabilitiesBySource = sourceCapabilitiesBySource()

        if (RemoteSyncArtifact.PROGRESS in request.artifacts) {
            failures += flushPendingSourceProgressUseCase(remoteItems)
                .map { failure -> failure.copy(artifact = failure.artifact ?: RemoteSyncArtifact.PROGRESS) }
        }

        val failedProgressSources = failures.asSequence()
            .filter { failure -> failure.artifact == RemoteSyncArtifact.PROGRESS }
            .filter(SourceProgressSyncFailure::blocksDependentArtifacts)
            .map(SourceProgressSyncFailure::sourceType)
            .toSet()
        val journalArtifacts = request.artifacts intersect setOf(
            RemoteSyncArtifact.JOURNAL_ENTRY,
            RemoteSyncArtifact.READING_SESSION,
        )
        if (journalArtifacts.isEmpty()) return failures

        itemsBySource
            .filterKeys { sourceType -> sourceType !in failedProgressSources }
            .forEach { (sourceType, sourceItems) ->
                val supportedJournalArtifacts = journalArtifacts.filter { artifact ->
                    capabilitiesBySource[sourceType]?.supports(artifact) == true
                }.toSet()
                if (supportedJournalArtifacts.isEmpty()) return@forEach
                val pusherFailures = try {
                    remoteJournalPusher.pushPendingJournalEntries(
                        sourceType = sourceType,
                        items = sourceItems,
                        artifacts = supportedJournalArtifacts,
                    )
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    listOf(error)
                }
                failures += pusherFailures.map { error ->
                    SourceProgressSyncFailure(
                        sourceType = sourceType,
                        throwable = error,
                        artifact = supportedJournalArtifacts.singleOrNull(),
                    )
                }
            }

        return failures
    }

    private fun sourceCapabilitiesBySource(): Map<SourceType, RemoteSyncCapability> {
        val capabilities = linkedMapOf<SourceType, MutableSet<RemoteSyncArtifact>>()
        sourceJournalPushers.forEach { support ->
            capabilities.getOrPut(support.sourceType, ::mutableSetOf) += support.supportedArtifacts
        }
        return capabilities.mapValues { (sourceType, artifacts) ->
            RemoteSyncCapability(
                sourceType = sourceType,
                supportedArtifacts = artifacts,
            )
        }
    }
}
