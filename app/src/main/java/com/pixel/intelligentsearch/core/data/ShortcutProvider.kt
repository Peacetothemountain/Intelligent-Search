package com.pixel.intelligentsearch.core.data
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

@androidx.compose.runtime.Immutable
data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String,
    val shortcutInfo: ShortcutInfo,
    val userHandle: android.os.UserHandle = android.os.Process.myUserHandle()
)

object ShortcutProvider {
    
    fun getShortcuts(context: Context, query: String): List<AppShortcutItem> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
            
        val results = mutableListOf<AppShortcutItem>()
        
        try {
            if (launcherApps.hasShortcutHostPermission()) {
                val queryObj = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or 
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or 
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                }
                
                val profiles = launcherApps.profiles ?: listOf(Process.myUserHandle())
                for (userHandle in profiles) {
                    val shortcuts = try {
                        launcherApps.getShortcuts(queryObj, userHandle)
                    } catch (_: Exception) {
                        null
                    }
                    
                    shortcuts?.forEach { info ->
                        val shortLabel = info.shortLabel?.toString() ?: ""
                        val longLabel = info.longLabel?.toString() ?: ""
                        
                        if (shortLabel.contains(query, ignoreCase = true) || longLabel.contains(query, ignoreCase = true)) {
                            results.add(
                                AppShortcutItem(
                                    id = info.id,
                                    packageName = info.`package`,
                                    shortLabel = shortLabel,
                                    longLabel = longLabel,
                                    shortcutInfo = info,
                                    userHandle = userHandle
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun getAllShortcuts(context: Context): List<AppShortcutItem> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
        val results = mutableListOf<AppShortcutItem>()
        try {
            if (launcherApps.hasShortcutHostPermission()) {
                val queryObj = LauncherApps.ShortcutQuery().apply {
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or 
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or 
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                }
                val profiles = launcherApps.profiles ?: listOf(Process.myUserHandle())
                for (userHandle in profiles) {
                    val shortcuts = try {
                        launcherApps.getShortcuts(queryObj, userHandle)
                    } catch (_: Exception) {
                        null
                    }
                    shortcuts?.forEach { info ->
                        val shortLabel = info.shortLabel?.toString() ?: ""
                        val longLabel = info.longLabel?.toString() ?: ""
                        if (shortLabel.isNotBlank() || longLabel.isNotBlank()) {
                            results.add(
                                AppShortcutItem(
                                    id = info.id,
                                    packageName = info.`package`,
                                    shortLabel = shortLabel,
                                    longLabel = longLabel,
                                    shortcutInfo = info,
                                    userHandle = userHandle
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
