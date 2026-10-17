package com.ngdesigns.intelligentsearch.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Installed apps and their shortcuts across the user's profiles. */
interface AppSource {
    fun apps(): List<AppEntry>
    fun shortcuts(): List<ShortcutEntry>
    /** Emits whenever apps are installed, updated or removed, or a profile is locked, unlocked, added or removed. */
    fun changes(): Flow<Unit>
}

/**
 * Reads apps through LauncherApps, so work and Private Space apps appear alongside personal ones while their
 * profile is running and unlocked. Shortcuts need shortcut host access (default launcher or assistant); without it
 * the list is simply empty.
 */
class LauncherAppSource(private val context: Context) : AppSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)

    private fun activeProfiles(): List<Pair<UserHandle, Profile>> = launcherApps.profiles.mapNotNull { user ->
        val profile = profileOf(user)
        val quiet = user != Process.myUserHandle() && runCatching { users.isQuietModeEnabled(user) }.getOrDefault(false)
        if (quiet) null else user to profile
    }

    private fun profileOf(user: UserHandle): Profile {
        if (user == Process.myUserHandle()) return Profile.Personal
        if (Build.VERSION.SDK_INT >= 35) {
            val info: LauncherUserInfo? = runCatching { launcherApps.getLauncherUserInfo(user) }.getOrNull()
            if (info?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE) return Profile.Private
        }
        return Profile.Work
    }

    override fun apps(): List<AppEntry> = activeProfiles().flatMap { (user, profile) ->
        runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
            AppEntry(info.label?.toString().orEmpty().ifBlank { info.componentName.packageName }, Target.App(info.componentName, user, profile))
        }
    }.filter { it.target.packageName != context.packageName }

    override fun shortcuts(): List<ShortcutEntry> {
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        )
        val labels = HashMap<String, String>()
        return activeProfiles().flatMap { (user, _) ->
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty().mapNotNull { s: ShortcutInfo ->
                if (!s.isEnabled) return@mapNotNull null
                val short = s.shortLabel?.toString().orEmpty()
                val long = s.longLabel?.toString().orEmpty()
                if (short.isBlank() && long.isBlank()) return@mapNotNull null
                val app = labels.getOrPut(s.`package`) {
                    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(s.`package`, 0)).toString() }
                        .getOrDefault(s.`package`)
                }
                ShortcutEntry(short.ifBlank { long }, long.ifBlank { short }, Target.Shortcut(s.`package`, s.id, user), app)
            }
        }
    }

    override fun changes(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(Unit) }
            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }.conflate()
}
