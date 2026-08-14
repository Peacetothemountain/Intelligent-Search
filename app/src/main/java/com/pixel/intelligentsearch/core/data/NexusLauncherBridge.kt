package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

data class NexusPredictionApp(
    val packageName: String,
    val className: String,
    val displayName: String
)

class NexusLauncherBridge(private val context: Context) {

    companion object {
        private const val TAG = "NexusLauncherBridge"
        private const val LAUNCHER_PKG = "com.google.android.apps.nexuslauncher"
        private val PREDICTION_URI: Uri = Uri.parse("content://com.google.android.apps.nexuslauncher.prediction/predictions")
    }

    fun isNexusLauncherInstalled(): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(LAUNCHER_PKG, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(LAUNCHER_PKG, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getPredictedApps(): List<NexusPredictionApp> {
        if (!isNexusLauncherInstalled()) return emptyList()

        val list = mutableListOf<NexusPredictionApp>()
        try {
            val cursor = context.contentResolver.query(PREDICTION_URI, null, null, null, null)
            cursor?.use { c ->
                val pkgIndex = c.getColumnIndex("package")
                val classIndex = c.getColumnIndex("class")
                val titleIndex = c.getColumnIndex("title")

                while (c.moveToNext()) {
                    val pkg = if (pkgIndex >= 0) c.getString(pkgIndex) else continue
                    val cls = if (classIndex >= 0) c.getString(classIndex) else ""
                    val title = if (titleIndex >= 0) c.getString(titleIndex) else pkg

                    list.add(NexusPredictionApp(packageName = pkg, className = cls, displayName = title))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nexus launcher prediction query fallback: ${e.message}")
        }
        return list
    }
}
