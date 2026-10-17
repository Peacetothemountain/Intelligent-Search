package com.ngdesigns.intelligentsearch.launch

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The flashlight, switched directly (no permission needed) and observed so the switch shows its real state. */
class Torch(context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    private val id: String? by lazy {
        if (!available) null else runCatching {
            camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }

    val exists: Boolean get() = id != null

    fun set(on: Boolean): Boolean = id?.let { runCatching { camera.setTorchMode(it, on) }.isSuccess } ?: false

    val state: Flow<Boolean> = callbackFlow {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) trySend(enabled) }
        }
        camera.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { camera.unregisterTorchCallback(callback) }
    }
}
