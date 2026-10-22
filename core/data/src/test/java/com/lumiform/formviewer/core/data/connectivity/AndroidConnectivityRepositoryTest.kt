package com.lumiform.formviewer.core.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(AndroidJUnit4::class)
class AndroidConnectivityRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val repository = AndroidConnectivityRepository(context)

    private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { networkCapabilities ->
            capabilities.forEach { shadowOf(networkCapabilities).addCapability(it) }
        }

    private val validatedInternet =
        capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    @Test
    fun isOnline_startsFromTheCurrentNetwork_thenFollowsTheDefaultNetworkCallback() =
        runTest {
            val network = connectivityManager.activeNetwork!!
            shadowOf(connectivityManager).setNetworkCapabilities(network, validatedInternet)

            repository.isOnline.test {
                assertThat(awaitItem()).isTrue()
                val callbacks = shadowOf(connectivityManager).networkCallbacks

                callbacks.forEach { it.onLost(network) }
                assertThat(awaitItem()).isFalse()

                callbacks.forEach { it.onCapabilitiesChanged(network, validatedInternet) }
                assertThat(awaitItem()).isTrue()
            }
            assertThat(shadowOf(connectivityManager).networkCallbacks).isEmpty()
        }

    @Test
    fun isOnline_isFalse_untilTheNetworkIsValidated() =
        runTest {
            val unvalidated = capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.activeNetwork, unvalidated)

            repository.isOnline.test {
                assertThat(awaitItem()).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
