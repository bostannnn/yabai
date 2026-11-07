package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState

/** The account a sign-in or sync request is for: the key of the server's cached library list. */
interface SourceSyncRequest {
    val baseUrl: String
    val username: String
}

/** A step of a sync as the progress bar shows it; a null [fraction] is a step whose length is unknown. */
internal data class SourceSyncProgress(
    val message: String,
    val fraction: Float?,
)

/** What a server's sign-in or sync will do, worked out from the form before anything is sent. */
internal sealed interface SourceSyncPreparation<out Request, out Session : StoredSourceSession> {
    data class Invalid(
        val connection: RemoteSourceConnectionUiState,
    ) : SourceSyncPreparation<Nothing, Nothing>

    data object MissingSession : SourceSyncPreparation<Nothing, Nothing>

    data class Ready<Request, Session : StoredSourceSession>(
        val request: Request,
        val activeConnection: RemoteSourceConnectionUiState,
        val fallbackSession: Session?,
        val successConnectionMessage: String,
        val failurePrefix: String,
    ) : SourceSyncPreparation<Request, Session>
}

internal sealed interface SourceSyncExecutionResult {
    data class Success(
        val libraryCount: Int,
        val itemCount: Int,
        val supportedFormatCounts: Map<FormatType, Int>,
        /** Empty for a server that does not list its libraries (Grimmory). */
        val availableLibraries: List<Library> = emptyList(),
        /** False when a fast sync found nothing new, so cached pages can stay. */
        val hasCatalogChanges: Boolean = true,
        val connection: RemoteSourceConnectionUiState,
    ) : SourceSyncExecutionResult

    data class Failure(
        val message: String,
        val connection: RemoteSourceConnectionUiState,
    ) : SourceSyncExecutionResult
}

internal sealed interface SourceConnectExecutionResult {
    data class Success(
        val libraryCount: Int,
        val availableLibraries: List<Library> = emptyList(),
        val connection: RemoteSourceConnectionUiState,
    ) : SourceConnectExecutionResult

    data class Failure(
        val message: String,
        val connection: RemoteSourceConnectionUiState,
    ) : SourceConnectExecutionResult
}
