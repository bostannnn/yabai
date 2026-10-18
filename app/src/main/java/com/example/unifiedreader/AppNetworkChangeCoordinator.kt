package com.example.unifiedreader

import javax.inject.Inject

/**
 * The remote sources' preferred server addresses. A failover keeps using the address it settled on
 * (`activeBaseUrl`), so after a fallback to the backup address the app never went back to the primary,
 * such as the LAN address once home again.
 */
interface RemoteServerAddressPreference {
    /** Has each source try its primary address first again on its next request. */
    suspend fun preferPrimaryAddresses()
}

class StoredSessionServerAddressPreference @Inject constructor(
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : RemoteServerAddressPreference {
    override suspend fun preferPrimaryAddresses() {
        kavitaCredentialStore.clearActiveBaseUrl { session -> session.copy(activeBaseUrl = null) }
        stumpCredentialStore.clearActiveBaseUrl { session -> session.copy(activeBaseUrl = null) }
        grimmoryCredentialStore.clearActiveBaseUrl { session -> session.copy(activeBaseUrl = null) }
    }
}

private suspend fun <Session : StoredSourceSession> SourceSessionStore<Session>.clearActiveBaseUrl(
    clear: (Session) -> Session,
) {
    val session = sessionFlow.value ?: return
    if (session.activeBaseUrl == null) return
    save(clear(session))
}

/** Acts on the device getting onto a network anew (see [isNewConnectionAfter]). */
class AppNetworkChangeCoordinator(
    private val networkConnectivityMonitor: AppNetworkConnectivityMonitor,
    private val serverAddressPreference: RemoteServerAddressPreference,
) {
    // Takes the stored-session preference directly, so no module binding is needed.
    @Inject constructor(
        networkConnectivityMonitor: AppNetworkConnectivityMonitor,
        serverAddressPreference: StoredSessionServerAddressPreference,
    ) : this(networkConnectivityMonitor, serverAddressPreference as RemoteServerAddressPreference)

    /**
     * Runs [onNewConnection] for each new connection, after pointing every source back at its primary
     * address: the new network decides which address answers, and the failover falls back again when
     * the primary doesn't.
     */
    suspend fun observeNewConnections(onNewConnection: suspend () -> Unit) {
        var previous: AppNetworkState? = null
        networkConnectivityMonitor.networkStateFlow.collect { state ->
            val isNewConnection = state.isNewConnectionAfter(previous)
            previous = state
            if (!isNewConnection) return@collect
            serverAddressPreference.preferPrimaryAddresses()
            onNewConnection()
        }
    }
}
