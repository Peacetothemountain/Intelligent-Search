package com.pixel.intelligentsearch.feature.voice

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Keeps Intelligent Search working as the digital assistant after an app update. Updating kills the app, and Android
 * tries to bind the assistant service again while the new version is still installing; that attempt fails and Android
 * doesn't retry, so the corner swipe and power button long press stop doing anything until the assistant is picked
 * again or the phone restarts. Android does rebind the assistant when its service component changes, so once the update
 * has finished this flips the service between its two enabled states (default and explicitly enabled). The service
 * stays enabled throughout and the assistant role is untouched.
 */
class AssistantRebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) rebindAssistant(context)
    }

    companion object {
        private const val TAG = "AssistantRebind"

        fun rebindAssistant(context: Context) {
            val roles = context.getSystemService(RoleManager::class.java) ?: return
            if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) || !roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return
            val pm = context.packageManager
            val service = ComponentName(context, IntelligentVoiceInteractionService::class.java)
            val next = if (pm.getComponentEnabledSetting(service) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            try {
                pm.setComponentEnabledSetting(service, next, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                Log.w(TAG, "Could not ask Android to rebind the assistant", e)
            }
        }
    }
}
