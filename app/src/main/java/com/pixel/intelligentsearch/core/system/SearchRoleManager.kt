package com.pixel.intelligentsearch.core.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

object SearchRoleManager {
    const val ROLE_SEARCH_ENGINE = "android.app.role.SEARCH_ENGINE"
    const val ROLE_SEARCH = "android.app.role.SEARCH"

    fun getAvailableSearchRole(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        val role = getAvailableSearchRole(context) ?: return false
        return roleManager.isRoleHeld(role)
    }

    fun createRequestSearchRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        val role = getAvailableSearchRole(context) ?: return null
        return roleManager.createRequestRoleIntent(role)
    }

    fun openSearchRoleOrSettings(context: Context) {
        val roleIntent = createRequestSearchRoleIntent(context)
        if (roleIntent != null) {
            roleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(roleIntent)
                return
            } catch (_: Exception) {}
        }

        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
