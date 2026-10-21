package com.pixel.intelligentsearch.core.data

import android.app.Activity
import android.content.Context
import com.pixel.intelligentsearch.core.profile.MultiProfileManager

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateSpaceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val multiProfileManager: MultiProfileManager
) {

    fun hasPrivateSpace(): Boolean = multiProfileManager.hasPrivateSpace()

    fun isPrivateSpaceLocked(): Boolean = multiProfileManager.isPrivateSpaceLocked()

    fun requestUnlockPrivateSpace(activity: Activity) {
        val privateProfile = multiProfileManager.profilesState.value.firstOrNull { it.profileType == ProfileType.PRIVATE }
        if (privateProfile != null) {
            multiProfileManager.requestUnlockProfile(privateProfile.userHandle)
        }
    }

    suspend fun getAllProfileApps(forceRefresh: Boolean = false): List<AppItem> {
        return multiProfileManager.getAllProfileApps(forceRefresh)
    }
}
