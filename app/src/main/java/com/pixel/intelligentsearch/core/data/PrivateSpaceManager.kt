package com.pixel.intelligentsearch.core.data

import android.app.Activity
import android.content.Context
import android.os.UserHandle
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.profile.ProfileDescriptor

data class ProfileContainerState(
    val hasPrivateSpace: Boolean,
    val isPrivateSpaceLocked: Boolean,
    val activeProfilesCount: Int
)

class PrivateSpaceManager(private val context: Context) {

    private val multiProfileManager = MultiProfileManager(context)

    fun getProfileContainerState(): ProfileContainerState {
        val profiles = multiProfileManager.refreshProfiles()
        val privateProfile = profiles.firstOrNull { it.profileType == ProfileType.PRIVATE }
        return ProfileContainerState(
            hasPrivateSpace = privateProfile != null,
            isPrivateSpaceLocked = privateProfile?.isLocked ?: false,
            activeProfilesCount = profiles.size
        )
    }

    fun hasPrivateSpace(): Boolean = multiProfileManager.hasPrivateSpace()

    fun isPrivateSpaceLocked(): Boolean = multiProfileManager.isPrivateSpaceLocked()

    fun getProfiles(): List<ProfileDescriptor> = multiProfileManager.refreshProfiles()

    fun requestUnlockPrivateSpace(activity: Activity) {
        val privateProfile = multiProfileManager.profilesState.value.firstOrNull { it.profileType == ProfileType.PRIVATE }
        if (privateProfile != null) {
            multiProfileManager.requestUnlockProfile(privateProfile.userHandle, activity)
        }
    }

    suspend fun getAllProfileApps(forceRefresh: Boolean = false): List<AppItem> {
        return multiProfileManager.getAllProfileApps(forceRefresh)
    }
}
