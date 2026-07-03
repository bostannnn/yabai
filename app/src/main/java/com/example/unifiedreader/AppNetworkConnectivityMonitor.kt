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

interface AppNetworkConnectivityMonitor {
    val networkAvailableFlow: Flow<Boolean>
}

@Singleton
class AndroidAppNetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : AppNetworkConnectivityMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val networkAvailableFlow: Flow<Boolean> = callbackFlow {
        fun isNetworkAvailable(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable())
            }
        }

        trySend(isNetworkAvailable())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
