package com.example.unifiedreader

import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.kavita.renewSession
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface RemoteSourceHealthCheck {
    val sourceType: SourceType

    fun consumeStartupProbeBudget(): Boolean = false

    suspend fun check(): RemoteSourceHealthCheckResult
}

sealed interface RemoteSourceHealthCheckResult {
    data object NotConfigured : RemoteSourceHealthCheckResult
    data object Online : RemoteSourceHealthCheckResult
}

sealed interface RemoteSourceConnectivityProbeResult {
    data object NoHealthCheck : RemoteSourceConnectivityProbeResult
    data object NotConfigured : RemoteSourceConnectivityProbeResult
    data object Online : RemoteSourceConnectivityProbeResult
    data class Offline(
        val throwable: Throwable,
    ) : RemoteSourceConnectivityProbeResult
    data class Failed(
        val throwable: Throwable,
    ) : RemoteSourceConnectivityProbeResult
}

// One for the app: the ViewModel and the download controller share its source errors.
@Singleton
class RemoteSourceConnectivityStartupCoordinator @Inject constructor(
    private val healthChecks: List<@JvmSuppressWildcards RemoteSourceHealthCheck>,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    private val sourceErrors = MutableStateFlow<Map<SourceType, Throwable>>(emptyMap())
    val sourceErrorsFlow: StateFlow<Map<SourceType, Throwable>> = sourceErrors.asStateFlow()
    private var checkedConfiguredSources = false

    suspend fun checkConfiguredSources() = coroutineScope {
        if (checkedConfiguredSources) return@coroutineScope
        checkedConfiguredSources = true
        healthChecks.mapNotNull { healthCheck ->
            if (!healthCheck.consumeStartupProbeBudget()) return@mapNotNull null
            async {
                checkSource(healthCheck)
            }
        }.awaitAll()
    }

    suspend fun retrySource(
        sourceType: SourceType,
    ): RemoteSourceConnectivityProbeResult =
        healthChecks.firstOrNull { check -> check.sourceType == sourceType }
            ?.let { healthCheck -> checkSource(healthCheck) }
            ?: RemoteSourceConnectivityProbeResult.NoHealthCheck

    private suspend fun checkSource(
        healthCheck: RemoteSourceHealthCheck,
    ): RemoteSourceConnectivityProbeResult {
        remoteSourceConnectivityPolicy.markChecking(healthCheck.sourceType)
        sourceErrors.update { current -> current - healthCheck.sourceType }
        return runSuspendCatching {
            healthCheck.check()
        }.fold(
            onSuccess = { checked ->
                when (checked) {
                    RemoteSourceHealthCheckResult.NotConfigured -> {
                        remoteSourceConnectivityPolicy.clear(healthCheck.sourceType)
                        RemoteSourceConnectivityProbeResult.NotConfigured
                    }
                    RemoteSourceHealthCheckResult.Online -> {
                        remoteSourceConnectivityPolicy.markReachable(healthCheck.sourceType)
                        RemoteSourceConnectivityProbeResult.Online
                    }
                }
            },
            onFailure = { throwable ->
                if (throwable.isRemoteServerAddressRetryableFailure()) {
                    remoteSourceConnectivityPolicy.markUnavailable(healthCheck.sourceType)
                    RemoteSourceConnectivityProbeResult.Offline(throwable)
                } else {
                    remoteSourceConnectivityPolicy.clear(healthCheck.sourceType)
                    sourceErrors.update { current -> current + (healthCheck.sourceType to throwable) }
                    RemoteSourceConnectivityProbeResult.Failed(throwable)
                }
            },
        )
    }

    fun currentStates(): Map<SourceType, SourceConnectivityState> =
        remoteSourceConnectivityPolicy.reachabilityFlow.value
}

class KavitaRemoteSourceHealthCheck @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : RemoteSourceHealthCheck {
    override val sourceType: SourceType = SourceType.KAVITA

    override suspend fun check(): RemoteSourceHealthCheckResult {
        val session = kavitaCredentialStore.sessionFlow.value ?: return RemoteSourceHealthCheckResult.NotConfigured
        runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
        ) { api, activeSession ->
            try {
                api.getLibraries(activeSession.accessToken)
            } catch (_: KavitaUnauthorizedException) {
                kavitaCredentialStore.saveRenewedTokens(
                    renewed = api.renewSession(
                        accessToken = activeSession.accessToken,
                        refreshToken = activeSession.refreshToken,
                        apiKey = activeSession.apiKey,
                    ),
                    runSession = activeSession,
                )
            }
        }
        return RemoteSourceHealthCheckResult.Online
    }
}

class StumpRemoteSourceHealthCheck @Inject constructor(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
) : RemoteSourceHealthCheck {
    override val sourceType: SourceType = SourceType.STUMP

    override suspend fun check(): RemoteSourceHealthCheckResult {
        val session = stumpCredentialStore.sessionFlow.value ?: return RemoteSourceHealthCheckResult.NotConfigured
        runWithStoredSessionAddressFailover(
            sessionStore = stumpCredentialStore,
            session = session,
            createApi = stumpApiFactory::create,
        ) { api, activeSession ->
            val refreshToken = activeSession.refreshToken?.takeIf(String::isNotBlank)
                ?: throw StumpUnauthorizedException()
            val refreshedSession = api.refreshSession(refreshToken)
            stumpCredentialStore.save(
                activeSession.copy(
                    accessToken = refreshedSession.accessToken,
                    refreshToken = refreshedSession.refreshToken,
                ),
            )
        }
        return RemoteSourceHealthCheckResult.Online
    }
}

class GrimmoryRemoteSourceHealthCheck @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : RemoteSourceHealthCheck {
    override val sourceType: SourceType = SourceType.GRIMMORY

    override suspend fun check(): RemoteSourceHealthCheckResult {
        val session = grimmoryCredentialStore.sessionFlow.value ?: return RemoteSourceHealthCheckResult.NotConfigured
        runWithStoredSessionAddressFailover(
            sessionStore = grimmoryCredentialStore,
            session = session,
            createApi = grimmoryApiFactory::create,
        ) { api, activeSession ->
            val refreshedSession = api.refreshSession(activeSession.refreshToken)
            grimmoryCredentialStore.save(
                activeSession.copy(
                    accessToken = refreshedSession.accessToken,
                    refreshToken = refreshedSession.refreshToken,
                ),
            )
        }
        return RemoteSourceHealthCheckResult.Online
    }
}
