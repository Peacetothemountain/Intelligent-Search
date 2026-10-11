package com.pixel.intelligentsearch.core.data
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo

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

    /**
     * Returns every labelled dynamic, pinned and manifest shortcut across all profiles. Shortcut access
     * is only granted to the default launcher, so this is empty unless the user made this app the home app.
     */
    fun getAllShortcuts(context: Context): List<AppShortcutItem> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val results = mutableListOf<AppShortcutItem>()
        try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val queryObj = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            }
            for (userHandle in launcherApps.profiles) {
                val shortcuts = try {
                    launcherApps.getShortcuts(queryObj, userHandle)
                } catch (_: Exception) {
                    null
                }
                shortcuts?.forEach { info ->
                    val shortLabel = info.shortLabel?.toString().orEmpty()
                    val longLabel = info.longLabel?.toString().orEmpty()
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
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        return results
    }
}
