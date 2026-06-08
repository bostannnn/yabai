package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.KAVITA_REFRESH_TOKEN_MISSING_MESSAGE
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.stump.StumpUnauthorizedException
import javax.inject.Inject
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

    suspend fun check(): RemoteSourceHealthCheckResult
}

sealed interface RemoteSourceHealthCheckResult {
    data object NotConfigured : RemoteSourceHealthCheckResult
    data object Online : RemoteSourceHealthCheckResult
}

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
        healthChecks.map { healthCheck ->
            async {
                checkSource(healthCheck)
            }
        }.awaitAll()
    }

    suspend fun retrySource(
        sourceType: SourceType,
    ) {
        val healthCheck = healthChecks.firstOrNull { check -> check.sourceType == sourceType } ?: return
        checkSource(healthCheck)
    }

    private suspend fun checkSource(
        healthCheck: RemoteSourceHealthCheck,
    ) {
        remoteSourceConnectivityPolicy.markChecking(healthCheck.sourceType)
        sourceErrors.update { current -> current - healthCheck.sourceType }
        runCatching {
            healthCheck.check()
        }.fold(
            onSuccess = { checked ->
                when (checked) {
                    RemoteSourceHealthCheckResult.NotConfigured -> remoteSourceConnectivityPolicy.clear(healthCheck.sourceType)
                    RemoteSourceHealthCheckResult.Online -> remoteSourceConnectivityPolicy.markReachable(healthCheck.sourceType)
                }
            },
            onFailure = { throwable ->
                if (throwable.isRemoteServerAddressRetryableFailure()) {
                    remoteSourceConnectivityPolicy.markUnavailable(healthCheck.sourceType)
                } else {
                    remoteSourceConnectivityPolicy.clear(healthCheck.sourceType)
                    sourceErrors.update { current -> current + (healthCheck.sourceType to throwable) }
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
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            try {
                api.getLibraries(activeSession.accessToken)
            } catch (_: KavitaUnauthorizedException) {
                val refreshToken = activeSession.refreshToken?.takeIf(String::isNotBlank)
                    ?: throw KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_MISSING_MESSAGE)
                val refreshedSession = api.refreshSession(
                    refreshToken = refreshToken,
                    accessToken = activeSession.accessToken,
                    apiKey = activeSession.apiKey,
                )
                kavitaCredentialStore.save(
                    activeSession.copy(
                        accessToken = refreshedSession.accessToken,
                        refreshToken = refreshedSession.refreshToken,
                        apiKey = refreshedSession.apiKey ?: activeSession.apiKey,
                    ),
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
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
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
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
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
