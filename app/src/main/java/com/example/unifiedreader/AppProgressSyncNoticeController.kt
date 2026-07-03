package com.example.unifiedreader

import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import javax.inject.Inject

data class AppProgressSyncNotice(
    val id: Long,
    val message: String,
    val isError: Boolean = true,
)

class AppProgressSyncNoticeController @Inject constructor() {
    private var nextNoticeId = 1L

    fun reportFailures(
        failures: List<SourceProgressSyncFailure>,
        updateState: AppUiStateUpdater,
    ) {
        val reportableFailures = failures.filterNot(SourceProgressSyncFailure::isQueuedOfflineFailure)
        if (reportableFailures.isEmpty()) return
        val message = reportableFailures.toProgressSyncNoticeMessage()
        updateState { state ->
            state.copy(
                progressSyncNotice = AppProgressSyncNotice(
                    id = nextNoticeId++,
                    message = message,
                    isError = true,
                ),
            )
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

private fun List<SourceProgressSyncFailure>.toProgressSyncNoticeMessage(): String {
    val groupedFailures = groupBy(SourceProgressSyncFailure::sourceType)
    if (groupedFailures.size == 1) {
        val (sourceType, failures) = groupedFailures.entries.single()
        failures.first().throwable.takeIf { throwable -> throwable is RemoteSourceUnavailableException }?.let { throwable ->
            return throwable.userFacingMessage()
        }
        val artifactLabel = failures.first().artifact.syncNoticeLabel()
        return "Unable to sync ${sourceType.displayName()} $artifactLabel: ${
            failures.first().throwable.userFacingMessage()
        }"
    }
    return "Unable to sync progress for ${
        groupedFailures.keys.joinToString { sourceType -> sourceType.displayName() }
    }."
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
