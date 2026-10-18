package com.example.unifiedreader

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The default network: whether it reaches the internet, and which network it is. */
data class AppNetworkState(
    val isAvailable: Boolean,
    val defaultNetworkId: Long? = null,
)

/**
 * Whether [this] is a new way onto the network after [previous]: back from no network, or a switch to
 * another default network while connected, which a Wi-Fi to cellular handoff is (no loss is reported
 * in between). The first state seen is not one.
 */
internal fun AppNetworkState.isNewConnectionAfter(previous: AppNetworkState?): Boolean {
    if (!isAvailable || previous == null) return false
    return !previous.isAvailable || previous.defaultNetworkId != defaultNetworkId
}

interface AppNetworkConnectivityMonitor {
    val networkStateFlow: Flow<AppNetworkState>
}

@Singleton
class AndroidAppNetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : AppNetworkConnectivityMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val networkStateFlow: Flow<AppNetworkState> = callbackFlow {
        fun currentState(): AppNetworkState {
            val activeNetwork = connectivityManager.activeNetwork ?: return AppNetworkState(isAvailable = false)
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            return AppNetworkState(
                isAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                defaultNetworkId = activeNetwork.networkHandle,
            )
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(AppNetworkState(isAvailable = true, defaultNetworkId = network.networkHandle))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(
                    AppNetworkState(
                        isAvailable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        defaultNetworkId = network.networkHandle,
                    ),
                )
            }

            override fun onLost(network: Network) {
                trySend(currentState())
            }
        }

        trySend(currentState())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
