package com.pixel.intelligentsearch.core.system

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.search.SystemActionRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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

    @Test
    fun theAppDeclaresNoNearbyDevicesPermission() {
        // Declared but never asked for, it showed in App info as "Nearby devices: not allowed", for nothing.
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(declared.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    @Config(sdk = [32])
    fun theCardsSwitchOpensBluetoothSettingsOnAndroid12() {
        // Even with Nearby Devices allowed by hand in App info, where the app used to switch Bluetooth itself.
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val settings = ComponentName("com.android.settings", "com.android.settings.Bluetooth")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(settings)
            addIntentFilterForActivity(settings, IntentFilter(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        val toggles = SystemToggleManager(context)
        val card = SystemActionRouter(context, toggles).matchAction("bluetooth") as SystemActionRouter.ActionResult.Toggle
        card.toggleUiState.onToggle(true)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, shadowOf(context as android.app.Application).nextStartedActivity?.action)
    }
}
