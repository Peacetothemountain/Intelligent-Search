package com.pixel.intelligentsearch.core.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

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

    fun isSearchRoleAvailable(context: Context): Boolean {
        return getAvailableSearchRole(context) != null
    }

    fun isSearchRoleHeld(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        val role = getAvailableSearchRole(context) ?: return false
        return roleManager.isRoleHeld(role)
    }

    fun createRequestSearchRoleIntent(context: Context): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        val role = getAvailableSearchRole(context) ?: return null
        return try {
            roleManager.createRequestRoleIntent(role)
        } catch (_: Exception) {
            null
        }
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

    fun openSearchRoleOrSettings(context: Context) {
        ensureUserSetupSearchEngineAllowlist(context)
        val requestIntent = createRequestSearchRoleIntent(context)
        if (requestIntent != null) {
            try {
                if (context !is android.app.Activity) {
                    requestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(requestIntent)
                return
            } catch (_: Exception) {}
        }
        openDefaultSearchEngineSettings(context)
    }

    const val SECURE_SELECTED_SEARCH_ENGINE = "selected_search_engine"
    const val SECURE_USER_SETUP_SEARCH_ENGINE = "user_setup_search_engine"
    const val ACTION_UPDATE_DSE = "com.google.android.finsky.intent.action.UPDATE_DSE"

    /**
     * Checks whether Settings.Secure.user_setup_search_engine is active and set to this app.
     * This bypasses the QPR2 permission controller package allowlist restriction.
     */
    fun isUserSetupSearchEngineActive(context: Context): Boolean {
        return try {
            val current = Settings.Secure.getString(context.contentResolver, SECURE_USER_SETUP_SEARCH_ENGINE)
            current == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensures Settings.Secure.user_setup_search_engine is set to this package.
     * On Android 17 QPR2, SearchEngineRoleBehavior.getQualifyingPackagesInternal() checks
     * SearchManager.getUserSetupSearchEngineRoleHolder() which reads "user_setup_search_engine".
     * Setting this key bypasses the hardcoded allow list of search engine packages,
     * ensuring Intelligent Search is always marked as a qualifying package.
     */
    fun ensureUserSetupSearchEngineAllowlist(context: Context): Boolean {
        // Runs on every overlay resume; without the adb-granted permission the write can only throw.
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

    fun isPixelLauncherSynced(context: Context): Boolean {
        return try {
            val selected = Settings.Secure.getString(context.contentResolver, SECURE_SELECTED_SEARCH_ENGINE)
            selected == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Synchronizes both user_setup_search_engine (for QPR2 allowlist bypass)
     * and selected_search_engine (for Pixel Launcher widget sync), then broadcasts UPDATE_DSE.
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
            val intent = Intent(ACTION_UPDATE_DSE)
            context.sendBroadcast(intent)
            success = success && putSuccess
        } catch (_: Exception) {
            success = false
        }
        return success
    }

    fun syncPixelLauncherSearchEngine(context: Context): Boolean {
        return syncAllSearchEngineSettings(context)
    }
}
