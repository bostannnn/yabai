package com.example.unifiedreader

import com.example.unifiedreader.core.model.displayName
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
        if (failures.isEmpty()) return
        val message = failures.toProgressSyncNoticeMessage()
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

private fun List<SourceProgressSyncFailure>.toProgressSyncNoticeMessage(): String {
    val groupedFailures = groupBy(SourceProgressSyncFailure::sourceType)
    if (groupedFailures.size == 1) {
        val (sourceType, failures) = groupedFailures.entries.single()
        return "Unable to sync ${sourceType.displayName()} progress: ${failures.first().throwable.userFacingMessage()}"
    }
    return "Unable to sync progress for ${
        groupedFailures.keys.joinToString { sourceType -> sourceType.displayName() }
    }."
}

internal fun progressPushWarningMessage(
    sourceLabel: String,
    failures: List<Throwable>,
): String? {
    if (failures.isEmpty()) return null
    return "Progress push failed for $sourceLabel: ${failures.first().userFacingMessage()}"
}
