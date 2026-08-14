package com.pixel.intelligentsearch.core.data

import android.content.Context
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
        if (userManager != null) {
            try {
                val method = UserManager::class.java.getMethod("isPrivateProfile")
                return method.invoke(userManager) as? Boolean ?: false
            } catch (e: Exception) {
                // Ignore API reflection failures
            }
        }
        return false
    }
}
