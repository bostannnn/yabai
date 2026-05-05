package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.displayName
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class RemoteSourceConnectivityPolicy @Inject constructor() {
    private val reachabilityBySource = MutableStateFlow<Map<SourceType, SourceConnectivityState>>(emptyMap())
    val reachabilityFlow: StateFlow<Map<SourceType, SourceConnectivityState>> = reachabilityBySource.asStateFlow()

    fun reachabilityFor(
        sourceType: SourceType,
    ): SourceConnectivityState = reachabilityBySource.value[sourceType] ?: SourceConnectivityState.Unknown

    fun isReachable(
        sourceType: SourceType,
    ): Boolean = reachabilityFor(sourceType) == SourceConnectivityState.Online

    fun isUnavailable(
        sourceType: SourceType,
    ): Boolean = reachabilityFor(sourceType) == SourceConnectivityState.Offline

    fun canAttemptReaderContent(
        sourceType: SourceType,
    ): Boolean = sourceType != SourceType.LOCAL && reachabilityFor(sourceType) != SourceConnectivityState.Checking

    fun canOpenRemoteItem(
        sourceType: SourceType,
    ): Boolean = canAttemptReaderContent(sourceType)

    fun canAttemptRemoteOpen(
        sourceType: SourceType,
    ): Boolean = canAttemptReaderContent(sourceType)

    fun canFetchCover(
        sourceType: SourceType,
    ): Boolean = isReachable(sourceType)

    fun canScheduleDownload(
        sourceType: SourceType,
    ): Boolean = isReachable(sourceType)

    fun canRunExplicitProbe(
        sourceType: SourceType,
    ): Boolean = sourceType != SourceType.LOCAL

    fun canAttemptDownloadedProgressSync(
        sourceType: SourceType,
    ): Boolean = canAttemptProgressSync(sourceType)

    fun canFlushDownloadedStartupProgress(
        sourceType: SourceType,
    ): Boolean = canAttemptProgressSync(sourceType)

    fun canAttemptProgressSync(
        sourceType: SourceType,
    ): Boolean = sourceType != SourceType.LOCAL && !isUnavailable(sourceType)

    fun canFlushStartupProgress(
        sourceType: SourceType,
        hasStoredDocument: Boolean,
    ): Boolean = if (hasStoredDocument) {
        canFlushDownloadedStartupProgress(sourceType)
    } else {
        canAttemptProgressSync(sourceType)
    }

    fun markReachable(
        sourceType: SourceType,
    ) {
        reachabilityBySource.update { current ->
            if (current[sourceType] == SourceConnectivityState.Online) {
                current
            } else {
                current + (sourceType to SourceConnectivityState.Online)
            }
        }
    }

    fun markUnavailable(
        sourceType: SourceType,
    ) {
        reachabilityBySource.update { current ->
            if (current[sourceType] == SourceConnectivityState.Offline) {
                current
            } else {
                current + (sourceType to SourceConnectivityState.Offline)
            }
        }
    }

    fun markChecking(
        sourceType: SourceType,
    ) {
        reachabilityBySource.update { current ->
            if (current[sourceType] == SourceConnectivityState.Checking) {
                current
            } else {
                current + (sourceType to SourceConnectivityState.Checking)
            }
        }
    }

    fun clear(
        sourceType: SourceType,
    ) {
        reachabilityBySource.update { current ->
            current - sourceType
        }
    }

    fun markFromFailure(
        sourceType: SourceType,
        throwable: Throwable,
    ) {
        if (throwable.isRemoteServerAddressRetryableFailure()) {
            markUnavailable(sourceType)
        } else {
            markReachable(sourceType)
        }
    }

    fun recordReaderContentSuccess(
        sourceType: SourceType,
    ) {
        markReachable(sourceType)
    }

    fun recordReaderContentFailure(
        sourceType: SourceType,
        throwable: Throwable,
    ) {
        markFromFailure(sourceType, throwable)
    }

    fun recordProgressSyncSuccess(
        sourceType: SourceType,
    ) {
        markReachable(sourceType)
    }

    fun recordProgressSyncFailure(
        sourceType: SourceType,
        throwable: Throwable,
    ) {
        markFromFailure(sourceType, throwable)
    }

    fun markFromSyncFailures(
        attemptedSourceTypes: Set<SourceType>,
        failures: List<SourceProgressSyncFailure>,
    ) {
        val networkFailureSourceTypes = failures.asSequence()
            .filter { failure -> failure.throwable.isRemoteServerAddressRetryableFailure() }
            .map(SourceProgressSyncFailure::sourceType)
            .toSet()
        attemptedSourceTypes.forEach { sourceType ->
            if (sourceType in networkFailureSourceTypes) {
                markUnavailable(sourceType)
            } else {
                markReachable(sourceType)
            }
        }
    }
}

object RemoteSourceReachability {
    val Unknown: SourceConnectivityState = SourceConnectivityState.Unknown
    val Reachable: SourceConnectivityState = SourceConnectivityState.Online
    val Unavailable: SourceConnectivityState = SourceConnectivityState.Offline
}

class RemoteSourceUnavailableException(
    sourceType: SourceType,
) : RuntimeException(
    "${sourceType.displayName()} local network cannot be accessed. Only downloaded items can be opened.",
)
