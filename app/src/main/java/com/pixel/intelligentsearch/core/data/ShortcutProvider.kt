package com.pixel.intelligentsearch.core.data
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi

data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String,
    val shortcutInfo: ShortcutInfo
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
                
                val userHandle = Process.myUserHandle()
                val shortcuts = launcherApps.getShortcuts(queryObj, userHandle)
                
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
                                shortcutInfo = info
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
}
