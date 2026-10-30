package com.pixel.intelligentsearch.core.system

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The Bluetooth toggle card says whether Bluetooth is on, with no Nearby Devices permission, which the app never asks for. */
@RunWith(RobolectricTestRunner::class)
class BluetoothStatusTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theCardSaysOnWhenBluetoothIsOnWithoutNearbyDevices() {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))

        val toggles = SystemToggleManager(context)
        // It read "Bluetooth is off" whenever the permission was missing, so always.
        assertTrue(toggles.isBluetoothEnabled.value)
        assertEquals("Bluetooth is on", toggles.getBluetoothStatusText(toggles.isBluetoothEnabled.value))

        shadowOf(adapter).setEnabled(false)
        assertFalse(SystemToggleManager(context).isBluetoothEnabled.value)
    }
}
