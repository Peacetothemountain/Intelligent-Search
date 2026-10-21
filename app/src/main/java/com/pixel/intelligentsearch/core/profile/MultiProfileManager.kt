package com.pixel.intelligentsearch.core.profile

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ProfileType
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.ui.evictThemedAppIcon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileDescriptor(
    val userHandle: UserHandle,
    val profileType: ProfileType,
    val label: String,
    val isLocked: Boolean,
    val isQuietMode: Boolean
)

@Singleton
class MultiProfileManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "MultiProfileManager"
        private const val USER_TYPE_PRIVATE = "android.os.usertype.profile.PRIVATE"
        private const val USER_TYPE_MANAGED = "android.os.usertype.profile.MANAGED"
        private const val USER_TYPE_CLONE = "android.os.usertype.profile.CLONE"
    }

    private val appContext = context.applicationContext ?: context
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private val _profilesState = MutableStateFlow<List<ProfileDescriptor>>(emptyList())
    val profilesState: StateFlow<List<ProfileDescriptor>> = _profilesState.asStateFlow()

    // An install, update or removal can change an app's icon, and nothing else drops a cached icon while the
    // process lives, so those events forget the package's icon along with the app list.
    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
            evictThemedAppIcon(packageName)
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
            evictThemedAppIcon(packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
            evictThemedAppIcon(packageName)
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
            packageNames.forEach(::evictThemedAppIcon)
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
            packageNames.forEach(::evictThemedAppIcon)
        }

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }

        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }
    }

    private var isCallbackRegistered = false

    init {
        registerCallbackSafely()
        refreshProfiles()
    }

    private fun registerCallbackSafely() {
        if (!isCallbackRegistered) {
            try {
                launcherApps?.registerCallback(launcherCallback, Handler(Looper.getMainLooper()))
                isCallbackRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register LauncherApps callback", e)
            }
        }
    }

    fun refreshProfiles(): List<ProfileDescriptor> {
        val um = userManager ?: return emptyList()
        val la = launcherApps ?: return emptyList()

        val profiles = try {
            la.profiles
        } catch (e: Exception) {
            try {
                um.userProfiles
            } catch (_: Exception) {
                listOf(Process.myUserHandle())
            }
        }

        val descriptors = mutableListOf<ProfileDescriptor>()
        val currentUser = Process.myUserHandle()

        for (user in profiles) {
            val type = resolveProfileType(user, currentUser)
            val isQuiet = try { um.isQuietModeEnabled(user) } catch (_: Exception) { false }
            val label = resolveProfileLabel(user, type)

            descriptors.add(
                ProfileDescriptor(
                    userHandle = user,
                    profileType = type,
                    label = label,
                    isLocked = isQuiet && (type == ProfileType.PRIVATE || type == ProfileType.WORK),
                    isQuietMode = isQuiet
                )
            )
        }

        _profilesState.value = descriptors
        return descriptors
    }

    private fun resolveProfileType(user: UserHandle, currentUser: UserHandle): ProfileType {
        if (user == currentUser) return ProfileType.PERSONAL

        // Android 15+ exposes the profile's user type through LauncherUserInfo. UserManager's
        // isPrivateProfile()/isManagedProfile() describe the *calling* user rather than [user], so they
        // cannot classify other profiles.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && launcherApps != null) {
            try {
                when (launcherApps.getLauncherUserInfo(user)?.userType) {
                    USER_TYPE_PRIVATE -> return ProfileType.PRIVATE
                    USER_TYPE_MANAGED -> return ProfileType.WORK
                    USER_TYPE_CLONE -> return ProfileType.CLONE
                }
            } catch (_: Throwable) {}
        }

        // Before Android 15 the only secondary profile LauncherApps exposes is effectively a work profile.
        return ProfileType.WORK
    }

    private fun resolveProfileLabel(user: UserHandle, type: ProfileType): String {
        return when (type) {
            ProfileType.PERSONAL -> "Personal"
            ProfileType.WORK -> "Work"
            ProfileType.PRIVATE -> "Private Space"
            ProfileType.CLONE -> "Dual App"
        }
    }

    suspend fun getAllProfileApps(forceRefresh: Boolean = false): List<AppItem> = withContext(Dispatchers.IO) {
        val la = launcherApps ?: return@withContext emptyList()
        val descriptors = if (forceRefresh || _profilesState.value.isEmpty()) refreshProfiles() else _profilesState.value
        val appList = mutableListOf<AppItem>()

        for (desc in descriptors) {
            // Cryptographic Biometric Isolation: If Private Space is locked, NEVER return apps into the active search index!
            if (desc.profileType == ProfileType.PRIVATE && desc.isLocked) {
                continue
            }

            try {
                val activities: List<LauncherActivityInfo> = la.getActivityList(null, desc.userHandle)
                for (activity in activities) {
                    val label = activity.label.toString()
                    val packageName = activity.applicationInfo.packageName
                    val activityName = activity.componentName.className

                    // getBadgedIcon already applies the profile badge; badging it again would draw two badges.
                    val badgedIcon: Drawable = activity.getBadgedIcon(0)

                    appList.add(
                        AppItem(
                            name = label,
                            packageName = packageName,
                            icon = badgedIcon,
                            actions = SystemDataProvider.getAppActions(packageName),
                            userHandle = desc.userHandle,
                            profileType = desc.profileType,
                            isPrivateProfile = desc.profileType == ProfileType.PRIVATE,
                            isQuietMode = desc.isQuietMode,
                            activityName = activityName
                        )
                    )
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to load apps for profile ${desc.userHandle}", e)
            }
        }

        appList.sortedWith(compareBy({ it.profileType.ordinal }, { it.name.lowercase() }))
    }

    /**
     * Asked of the system each time rather than read from the snapshot: if Private Space locked after the last
     * refresh, a stale "unlocked" would let its apps appear in search results.
     */
    fun isPrivateSpaceLocked(): Boolean {
        val privateProfile = _profilesState.value.firstOrNull { it.profileType == ProfileType.PRIVATE } ?: return false
        return try {
            userManager?.isQuietModeEnabled(privateProfile.userHandle) ?: privateProfile.isLocked
        } catch (_: Exception) {
            privateProfile.isLocked
        }
    }

    fun hasPrivateSpace(): Boolean {
        return _profilesState.value.any { it.profileType == ProfileType.PRIVATE }
    }

    fun requestUnlockProfile(userHandle: UserHandle) {
        val um = userManager ?: return
        try {
            um.requestQuietModeEnabled(false, userHandle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request quiet mode disabled", e)
            // Fallback: Launch a component from that profile to prompt system keyguard/biometric gate
            try {
                val apps = launcherApps
                if (apps != null) {
                    val activities = apps.getActivityList(null, userHandle)
                    if (!activities.isNullOrEmpty()) {
                        apps.startMainActivity(activities.first().componentName, userHandle, null, null)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun launchApp(
        packageName: String,
        userHandle: UserHandle? = null,
        activityName: String? = null,
        sourceBounds: Rect? = null,
        opts: Bundle? = null,
        activity: Activity? = null
    ): Boolean {
        val targetUser = userHandle ?: Process.myUserHandle()
        val isCurrentUser = targetUser == Process.myUserHandle()

        if (!isCurrentUser && launcherApps != null) {
            try {
                val component = if (activityName != null) {
                    ComponentName(packageName, activityName)
                } else {
                    val acts = launcherApps.getActivityList(packageName, targetUser)
                    acts.firstOrNull()?.componentName ?: ComponentName(packageName, "")
                }
                launcherApps.startMainActivity(component, targetUser, sourceBounds, opts)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "LauncherApps failed to start activity for $packageName on $targetUser", e)
                return false
            }
        }

        if (!isCurrentUser) {
            // Never leak cross-profile launches to the personal profile
            return false
        }

        // Personal profile: the indexed launcher activity when known (packages can have several), else the default.
        val pm = appContext.packageManager
        val launchIntent = (activityName?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(packageName, it) }
            ?: pm.getLaunchIntentForPackage(packageName))?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            sourceBounds?.let { bounds -> this.sourceBounds = bounds }
        }
        if (launchIntent != null) {
            try {
                if (activity != null) {
                    activity.startActivity(launchIntent, opts)
                } else {
                    appContext.startActivity(launchIntent, opts)
                }
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Standard launch intent failed for $packageName", e)
            }
        }

        // Explicit component resolution fallback for OEM apps (e.g. Samsung Calendar/Notes/Gallery, Xiaomi, Motorola)
        try {
            val queryIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
            val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(queryIntent, 0)
            }
            val targetClass = resolves.firstOrNull()?.activityInfo?.name ?: activityName
            if (targetClass != null) {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(packageName, targetClass)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    sourceBounds?.let { bounds -> this.sourceBounds = bounds }
                }
                if (activity != null) {
                    activity.startActivity(explicitIntent, opts)
                } else {
                    appContext.startActivity(explicitIntent, opts)
                }
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Explicit component launch fallback failed for $packageName", e)
        }

        return false
    }
}
