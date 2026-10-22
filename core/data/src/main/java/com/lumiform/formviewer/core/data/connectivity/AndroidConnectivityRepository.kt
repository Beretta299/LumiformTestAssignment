package com.lumiform.formviewer.core.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lumiform.formviewer.core.domain.repository.ConnectivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Follows the default network. Online means validated, as JobScheduler requires for WorkManager's CONNECTED: a network
 * that only claims internet (e.g. behind a captive portal) would keep the launch sync waiting while the screen thinks
 * it is online.
 */
internal class AndroidConnectivityRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ConnectivityRepository {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        override val isOnline: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.isValidatedInternet())
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                // Without a default network no callback fires, so the current state is sent first.
                val current = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
                trySend(current.isValidatedInternet())
                connectivityManager.registerDefaultNetworkCallback(callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged().conflate()
    }

private fun NetworkCapabilities?.isValidatedInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
