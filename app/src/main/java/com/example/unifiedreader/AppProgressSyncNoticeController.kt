package com.example.unifiedreader

import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import javax.inject.Inject

data class AppProgressSyncNotice(
    val id: Long,
    val message: String,
    val isError: Boolean = true,
    /** Why it failed, for the toast's second line. */
    val reason: String? = null,
    /** False for a source that can't be reached at all, where trying again now won't help. */
    val canRetry: Boolean = true,
)

class AppProgressSyncNoticeController @Inject constructor() {
    private var nextNoticeId = 1L

    fun reportFailures(
        failures: List<SourceProgressSyncFailure>,
        updateState: AppUiStateUpdater,
    ) {
        val reportableFailures = failures.filterNot(SourceProgressSyncFailure::isQueuedOfflineFailure)
        if (reportableFailures.isEmpty()) return
        val notice = reportableFailures.toProgressSyncNotice(id = nextNoticeId++)
        updateState { state ->
            state.copy(progressSyncNotice = notice)
        }
    }

    fun dismissNotice(
        noticeId: Long,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        if (stateProvider().progressSyncNotice?.id != noticeId) return
        updateState { state ->
            state.copy(progressSyncNotice = null)
        }
    }
}

private fun SourceProgressSyncFailure.isQueuedOfflineFailure(): Boolean =
    artifact != null && throwable is RemoteSourceUnavailableException

private fun List<SourceProgressSyncFailure>.toProgressSyncNotice(id: Long): AppProgressSyncNotice {
    val groupedFailures = groupBy(SourceProgressSyncFailure::sourceType)
    if (groupedFailures.size == 1) {
        val (sourceType, failures) = groupedFailures.entries.single()
        failures.first().throwable.takeIf { throwable -> throwable is RemoteSourceUnavailableException }?.let { throwable ->
            return AppProgressSyncNotice(id = id, message = throwable.userFacingMessage(), canRetry = false)
        }
        val artifactLabel = failures.first().artifact.syncNoticeLabel()
        return AppProgressSyncNotice(
            id = id,
            message = "Couldn't send your $artifactLabel to ${sourceType.displayName()}",
            reason = failures.first().throwable.userFacingMessage(),
        )
    }
    val names = groupedFailures.keys.map { sourceType -> sourceType.displayName() }
    return AppProgressSyncNotice(
        id = id,
        message = "Couldn't send your progress to ${names.dropLast(1).joinToString()} and ${names.last()}",
    )
}

private fun RemoteSyncArtifact?.syncNoticeLabel(): String = when (this) {
    RemoteSyncArtifact.JOURNAL_ENTRY -> "journal"
    RemoteSyncArtifact.READING_SESSION -> "reading sessions"
    RemoteSyncArtifact.PROGRESS,
    null -> "progress"
}

internal fun progressPushWarningMessage(
    sourceLabel: String,
    failures: List<Throwable>,
): String? {
    if (failures.isEmpty()) return null
    return "Progress push failed for $sourceLabel: ${failures.first().userFacingMessage()}"
}
