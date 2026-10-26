package com.pixel.intelligentsearch.core.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Android 17 QPR2's search engine role ("Search engine app" under Default apps) decides which app powers the
 * home screen search bar. The system picker lists the apps on the device's search engine list plus the app
 * named in Settings.Secure.user_setup_search_engine, and each must have a home-screen widget that can be added
 * without a required setup step (ours are configuration-optional). The system removes a holder that stops
 * qualifying, so the setting is what keeps this app listed; it can only be written with WRITE_SECURE_SETTINGS,
 * which users grant once over ADB.
 */
object SearchRoleManager {
    const val ROLE_SEARCH_ENGINE = "android.app.role.SEARCH_ENGINE"
    const val ROLE_SEARCH = "android.app.role.SEARCH"

    fun getAvailableSearchRole(context: Context): String? {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        return when {
            roleManager.isRoleAvailable(ROLE_SEARCH_ENGINE) -> ROLE_SEARCH_ENGINE
            roleManager.isRoleAvailable(ROLE_SEARCH) -> ROLE_SEARCH
            else -> null
        }
    }

    fun isSearchRoleHeld(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        val role = getAvailableSearchRole(context) ?: return false
        return roleManager.isRoleHeld(role)
    }

    fun openDefaultSearchEngineSettings(context: Context) {
        ensureUserSetupSearchEngineAllowlist(context)
        // Direct intent to Android system Default Search Engine selection page
        try {
            val role = getAvailableSearchRole(context) ?: ROLE_SEARCH_ENGINE
            val intent = Intent("android.intent.action.MANAGE_DEFAULT_APP").apply {
                putExtra("android.intent.extra.ROLE_NAME", role)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // Direct intent to Android 17 QPR2 native Default Search Engine settings page
        try {
            val dseIntent = Intent("android.settings.DEFAULT_SEARCH_ENGINE_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (dseIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dseIntent)
                return
            }
        } catch (_: Exception) {}

        // Fallback to Android system Default Apps Settings page
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // Fallback to System Settings
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    const val SECURE_SELECTED_SEARCH_ENGINE = "selected_search_engine"
    const val SECURE_USER_SETUP_SEARCH_ENGINE = "user_setup_search_engine"
    const val ACTION_UPDATE_DSE = "com.google.android.finsky.intent.action.UPDATE_DSE"

    /**
     * The one-time ADB step, one command per line: switch the search engine role on (Android leaves it off unless
     * search_engine_role_available is set), put the app on the search engine list and select it, let it keep those
     * settings itself, and make it the role holder. All are needed, so every place that offers the step shows and
     * copies them all.
     */
    const val ADB_ACTIVATION_COMMANDS =
        "adb shell settings put secure search_engine_role_available 1\n" +
            "adb shell settings put secure $SECURE_USER_SETUP_SEARCH_ENGINE com.pixel.intelligentsearch\n" +
            "adb shell settings put secure $SECURE_SELECTED_SEARCH_ENGINE com.pixel.intelligentsearch\n" +
            "adb shell pm grant com.pixel.intelligentsearch android.permission.WRITE_SECURE_SETTINGS\n" +
            "adb shell cmd role add-role-holder android.app.role.SEARCH_ENGINE com.pixel.intelligentsearch"

    /**
     * Whether this app is on the device's search engine list as far as the picker is concerned: the
     * user_setup_search_engine setting names it, or it already holds the role. Only then will the system's
     * "Search engine app" picker show it.
     */
    fun isListedAsSearchEngine(context: Context): Boolean {
        if (isSearchRoleHeld(context)) return true
        return try {
            Settings.Secure.getString(context.contentResolver, SECURE_USER_SETUP_SEARCH_ENGINE) == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Keeps Settings.Secure.user_setup_search_engine pointed at this package. The role treats the package
     * named there as qualifying alongside the device's search engine list, and revokes a holder that no longer
     * qualifies, so a holder must keep the setting in place. Writing it needs WRITE_SECURE_SETTINGS.
     */
    fun ensureUserSetupSearchEngineAllowlist(context: Context): Boolean {
        // Without the adb-granted permission the write can only throw.
        if (!canWriteSecureSettings(context)) return false
        return try {
            val current = Settings.Secure.getString(context.contentResolver, SECURE_USER_SETUP_SEARCH_ENGINE)
            if (current != context.packageName) {
                Settings.Secure.putString(
                    context.contentResolver,
                    SECURE_USER_SETUP_SEARCH_ENGINE,
                    context.packageName
                )
            } else {
                true
            }
        } catch (e: Exception) {
            android.util.Log.d("SearchRoleManager", "Could not set $SECURE_USER_SETUP_SEARCH_ENGINE: ${e.message}")
            false
        }
    }

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Writes both user_setup_search_engine (keeps this app on the search engine list) and
     * selected_search_engine (the setting Pixel Launcher reads directly), then broadcasts UPDATE_DSE.
     * Both writes need WRITE_SECURE_SETTINGS; without it this does nothing.
     */
    fun syncAllSearchEngineSettings(context: Context): Boolean {
        var success = true
        try {
            ensureUserSetupSearchEngineAllowlist(context)
        } catch (_: Exception) {
            success = false
        }
        try {
            val putSuccess = Settings.Secure.putString(
                context.contentResolver,
                SECURE_SELECTED_SEARCH_ENGINE,
                context.packageName
            )
            val intent = Intent(ACTION_UPDATE_DSE).setPackage("com.google.android.apps.nexuslauncher")
            context.sendBroadcast(intent)
            success = success && putSuccess
        } catch (_: Exception) {
            success = false
        }
        return success
    }

}
