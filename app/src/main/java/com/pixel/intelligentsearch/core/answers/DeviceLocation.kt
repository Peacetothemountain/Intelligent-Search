package com.pixel.intelligentsearch.core.answers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The phone's approximate location, for weather answers about "here". Tests replace it. */
interface DeviceLocation {
    /** Whether approximate location may be read (the user allowed it). */
    fun permitted(): Boolean

    /** Latitude and longitude to about a kilometre, or null when the phone can't tell soon enough. */
    suspend fun approximate(): Pair<Double, Double>?
}

/**
 * [DeviceLocation] from Android's location service with approximate (coarse) location only, which is all a forecast
 * needs: the app never asks for precise location. A fresh fix is asked for briefly, else the last one is used.
 */
class AndroidDeviceLocation(context: Context) : DeviceLocation {
    private val appContext = context.applicationContext

    override fun permitted(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // permitted() is checked first; a permission revoked in between only makes the calls fail, which is caught.
    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun approximate(): Pair<Double, Double>? {
        if (!permitted()) return null
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) { currentLocation(manager, provider) }
        val location = fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: return null
        return location.latitude to location.longitude
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, signal, Dispatchers.IO.asExecutor()) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private companion object {
        const val FIX_TIMEOUT_MS = 4_000L
    }
}
