package com.pixel.intelligentsearch.core.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.search.SystemActionRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * The Wi-Fi toggle's status line. Android hides the network's name from apps without location access, so the line
 * says whether Wi-Fi is connected, never which network.
 */
@RunWith(RobolectricTestRunner::class)
class WifiStatusTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun eachStateHasItsLine() {
        val connected = SystemToggleManager.wifiStatusText(enabled = true, connected = true)
        assertTrue(connected, connected.contains("Connected") && !connected.contains("Not connected"))
        assertEquals("Wi-Fi is on • Not connected", SystemToggleManager.wifiStatusText(enabled = true, connected = false))
        assertEquals("Wi-Fi is turned off", SystemToggleManager.wifiStatusText(enabled = false, connected = false))
    }

    @Suppress("DEPRECATION") // NetworkInfo and setWifiEnabled are how Robolectric sets up the network
    @Test
    fun aWifiConnectionWhoseNameIsHiddenStillReadsConnected() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).setActiveNetworkInfo(
            ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED)
        )
        val wifiCapabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(wifiCapabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, wifiCapabilities)
        // Wi-Fi is on, and the connection's name reads "<unknown ssid>", as it does for this app on a phone.
        context.getSystemService(WifiManager::class.java).isWifiEnabled = true

        val toggle = SystemActionRouter(context, SystemToggleManager(context)).matchAction("wifi") as SystemActionRouter.ActionResult.Toggle
        assertEquals("Wi-Fi is on • Connected", toggle.toggleUiState.subtitle)
    }
}
