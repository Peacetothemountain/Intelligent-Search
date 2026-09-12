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
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.pixel.intelligentsearch.core.data.AppAction
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ProfileType
import com.pixel.intelligentsearch.core.data.SystemDataProvider
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
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "MultiProfileManager"
        private const val USER_TYPE_PRIVATE = "android.os.usertype.profile.PRIVATE"
        private const val USER_TYPE_MANAGED = "android.os.usertype.profile.MANAGED"
        private const val USER_TYPE_CLONE = "android.os.usertype.profile.CLONE"
    }

    private val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private val _profilesState = MutableStateFlow<List<ProfileDescriptor>>(emptyList())
    val profilesState: StateFlow<List<ProfileDescriptor>> = _profilesState.asStateFlow()

    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            refreshProfiles()
            SystemDataProvider.invalidateAppsCache()
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

    init {
        try {
            launcherApps?.registerCallback(launcherCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register LauncherApps callback", e)
        }
        refreshProfiles()
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

        val um = userManager ?: return ProfileType.PERSONAL

        // 1. Android 15/16/17 Private Space Direct API
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val isPrivateMethod = UserManager::class.java.getMethod("isPrivateProfile")
                val isPrivate = isPrivateMethod.invoke(um) as? Boolean
                if (isPrivate == true) return ProfileType.PRIVATE
            } catch (_: Throwable) {}
        }

        // 2. LauncherUserInfo userType check
        if (Build.VERSION.SDK_INT >= 35 && launcherApps != null) {
            try {
                val getInfoMethod = launcherApps.javaClass.getMethod("getLauncherUserInfo", UserHandle::class.java)
                val info = getInfoMethod.invoke(launcherApps, user)
                if (info != null) {
                    val getUserTypeMethod = info.javaClass.getMethod("getUserType")
                    val userType = getUserTypeMethod.invoke(info) as? String
                    when (userType) {
                        USER_TYPE_PRIVATE -> return ProfileType.PRIVATE
                        USER_TYPE_MANAGED -> return ProfileType.WORK
                        USER_TYPE_CLONE -> return ProfileType.CLONE
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. UserProperties reflection for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val getUserProps = um.javaClass.getMethod("getUserProperties", UserHandle::class.java)
                val props = getUserProps.invoke(um, user)
                if (props != null) {
                    val getProfileType = props.javaClass.getMethod("getProfileType")
                    val profileTypeStr = getProfileType.invoke(props) as? String
                    when (profileTypeStr) {
                        USER_TYPE_PRIVATE -> return ProfileType.PRIVATE
                        USER_TYPE_MANAGED -> return ProfileType.WORK
                        USER_TYPE_CLONE -> return ProfileType.CLONE
                    }
                }
            } catch (_: Throwable) {}
        }

        // 4. Managed profile fallback check
        try {
            if (um.isManagedProfile) {
                return ProfileType.WORK
            }
        } catch (_: Throwable) {}

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

                    // Obtain platform-badged icon for this specific profile
                    val originalIcon: Drawable = activity.getBadgedIcon(0)
                    val badgedIcon = try {
                        context.packageManager.getUserBadgedIcon(originalIcon, desc.userHandle)
                    } catch (_: Throwable) {
                        originalIcon
                    }

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
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load apps for profile ${desc.userHandle}", e)
            }
        }

        appList.sortedWith(compareBy({ it.profileType.ordinal }, { it.name.lowercase() }))
    }

    fun isPrivateSpaceLocked(): Boolean {
        val privateProfile = _profilesState.value.firstOrNull { it.profileType == ProfileType.PRIVATE } ?: return false
        return privateProfile.isLocked
    }

    fun hasPrivateSpace(): Boolean {
        return _profilesState.value.any { it.profileType == ProfileType.PRIVATE }
    }

    fun requestUnlockProfile(userHandle: UserHandle, activity: Activity) {
        val um = userManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                um.requestQuietModeEnabled(false, userHandle)
            } else {
                @Suppress("DEPRECATION")
                um.requestQuietModeEnabled(false, userHandle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request quiet mode disabled", e)
            // Fallback: Launch a component from that profile to prompt system keyguard/biometric gate
            try {
                val activities = launcherApps?.getActivityList(null, userHandle)
                if (!activities.isNullOrEmpty()) {
                    launcherApps?.startMainActivity(activities.first().componentName, userHandle, null, null)
                }
            } catch (_: Exception) {}
        }
    }

    fun launchApp(
        packageName: String,
        userHandle: UserHandle? = null,
        activityName: String? = null,
        sourceBounds: Rect? = null,
        opts: Bundle? = null
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
            }
        }

        // Fallback to standard package manager
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            sourceBounds?.let { bounds -> this.sourceBounds = bounds }
        }
        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent, opts)
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
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    sourceBounds?.let { bounds -> this.sourceBounds = bounds }
                }
                context.startActivity(explicitIntent, opts)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Explicit component launch fallback failed for $packageName", e)
        }

        return false
    }
}
