package com.pixel.intelligentsearch.core.ecosystem

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class DeviceDockState(
    val isDocked: Boolean,
    val isDeskDock: Boolean,
    val isCarDock: Boolean
)

class PixelEcosystemSync(private val context: Context) {

    fun getDeviceDockState(): DeviceDockState {
        val dockIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_DOCK_EVENT))
        val dockState = dockIntent?.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED)
            ?: Intent.EXTRA_DOCK_STATE_UNDOCKED

        val isDocked = dockState != Intent.EXTRA_DOCK_STATE_UNDOCKED
        val isDesk = dockState == Intent.EXTRA_DOCK_STATE_DESK || dockState == Intent.EXTRA_DOCK_STATE_LE_DESK || dockState == Intent.EXTRA_DOCK_STATE_HE_DESK
        val isCar = dockState == Intent.EXTRA_DOCK_STATE_CAR

        return DeviceDockState(
            isDocked = isDocked,
            isDeskDock = isDesk,
            isCarDock = isCar
        )
    }

    fun broadcastSearchStateToWearOS(query: String) {
        val intent = Intent("com.pixel.intelligentsearch.WEAR_SYNC_UPDATE").apply {
            putExtra("SEARCH_QUERY", query)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
