package com.amaxonia.erp.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

open class NetworkMonitor(
    context: Context,
) {
    private val connectivityManager: ConnectivityManager? = runCatching {
        context.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    val isOnlineFlow: Flow<Boolean> =
        callbackFlow {
            val cm = connectivityManager
            if (cm == null) {
                trySend(false)
                awaitClose { }
                return@callbackFlow
            }
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onLost(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(isOnline())
                    }
                }
            trySend(isOnline())
            cm.registerNetworkCallback(
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
            awaitClose { cm.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    open fun isOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
