package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.model.SyncState

enum class StumpProgressSyncOperation(
    val wireName: String,
) {
    UpdatePagedProgress("updateMediaProgress"),
    MarkMediaComplete("markMediaAsComplete"),
    MarkMediaIncomplete("markMediaAsComplete"),
    LocalUnreadReset("localUnreadReset"),
}

data class StumpProgressSyncDiagnosticEvent(
    val itemId: String,
    val mediaId: String,
    val operation: StumpProgressSyncOperation,
    val pageIndex: Int?,
    val remotePageNumber: Int?,
    val pageCount: Int?,
    val isFinished: Boolean,
    val isExplicitUnreadReset: Boolean,
    val syncState: SyncState,
    val updatedAtEpochMillis: Long,
)

interface StumpProgressSyncDiagnostics {
    fun onAttempt(
        event: StumpProgressSyncDiagnosticEvent,
    )

    fun onSuccess(
        event: StumpProgressSyncDiagnosticEvent,
    )

    fun onFailure(
        event: StumpProgressSyncDiagnosticEvent,
        throwable: Throwable,
    )
}

object NoOpStumpProgressSyncDiagnostics : StumpProgressSyncDiagnostics {
    override fun onAttempt(
        event: StumpProgressSyncDiagnosticEvent,
    ) = Unit

    override fun onSuccess(
        event: StumpProgressSyncDiagnosticEvent,
    ) = Unit

    override fun onFailure(
        event: StumpProgressSyncDiagnosticEvent,
        throwable: Throwable,
    ) = Unit
}

object LogcatStumpProgressSyncDiagnostics : StumpProgressSyncDiagnostics {
    override fun onAttempt(
        event: StumpProgressSyncDiagnosticEvent,
    ) {
        Log.i(LOG_TAG, "Stump progress push attempt: ${event.summary()}")
    }

    override fun onSuccess(
        event: StumpProgressSyncDiagnosticEvent,
    ) {
        Log.i(LOG_TAG, "Stump progress push succeeded: ${event.summary()}")
    }

    override fun onFailure(
        event: StumpProgressSyncDiagnosticEvent,
        throwable: Throwable,
    ) {
        Log.w(
            LOG_TAG,
            "Stump progress push failed: ${event.summary()} error=${throwable.diagnosticMessage()}",
            throwable,
        )
    }
}

private fun StumpProgressSyncDiagnosticEvent.summary(): String = listOf(
    "itemId=$itemId",
    "mediaId=$mediaId",
    "operation=${operation.wireName}",
    "intent=${operation.name}",
    "pageIndex=$pageIndex",
    "remotePageNumber=$remotePageNumber",
    "pageCount=$pageCount",
    "isFinished=$isFinished",
    "isExplicitUnreadReset=$isExplicitUnreadReset",
    "syncState=$syncState",
    "updatedAtEpochMillis=$updatedAtEpochMillis",
).joinToString(" ")

private fun Throwable.diagnosticMessage(): String =
    "${this::class.java.simpleName}: ${message.orEmpty()}"

private const val LOG_TAG = "StumpProgressSync"
