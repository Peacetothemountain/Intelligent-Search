package com.pixel.intelligentsearch.core.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

data class ProfileContainerState(
    val hasPrivateSpace: Boolean,
    val isPrivateSpaceLocked: Boolean,
    val activeProfilesCount: Int
)

class PrivateSpaceManager(private val context: Context) {

    private val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    fun getProfileContainerState(): ProfileContainerState {
        if (userManager == null) {
            return ProfileContainerState(
                hasPrivateSpace = false,
                isPrivateSpaceLocked = false,
                activeProfilesCount = 1
            )
        }

        var hasPrivateSpace = false
        var isPrivateSpaceLocked = false
        var activeProfiles = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val userProfiles = userManager.userProfiles
                activeProfiles = userProfiles.size

                for (profile in userProfiles) {
                    if (isPrivateProfileHandle(profile)) {
                        hasPrivateSpace = true
                        if (userManager.isQuietModeEnabled(profile)) {
                            isPrivateSpaceLocked = true
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback for standard profile checks
            }
        }

        return ProfileContainerState(
            hasPrivateSpace = hasPrivateSpace,
            isPrivateSpaceLocked = isPrivateSpaceLocked,
            activeProfilesCount = activeProfiles
        )
    }

    private fun isPrivateProfileHandle(userHandle: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT >= 35 && launcherApps != null) {
            try {
                val info = launcherApps.getLauncherUserInfo(userHandle)
                if (info != null && info.userType == "android.os.usertype.profile.PRIVATE") {
                    return true
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        if (userManager != null) {
            try {
                val getUserPropertiesMethod = userManager.javaClass.getMethod("getUserProperties", UserHandle::class.java)
                val userProperties = getUserPropertiesMethod.invoke(userManager, userHandle)
                if (userProperties != null) {
                    val getProfileTypeMethod = userProperties.javaClass.getMethod("getProfileType")
                    val profileType = getProfileTypeMethod.invoke(userProperties) as? String
                    if (profileType == "android.os.usertype.profile.PRIVATE") {
                        return true
                    }
                }
            } catch (e: Throwable) {
                // Ignore API reflection failures
            }
        }
        return false
    }
}
