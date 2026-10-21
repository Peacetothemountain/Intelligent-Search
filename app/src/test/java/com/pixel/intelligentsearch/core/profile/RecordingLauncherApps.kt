package com.pixel.intelligentsearch.core.profile

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLauncherApps

/**
 * LauncherApps that records the apps and shortcuts it is asked to start (Robolectric's own refuses to start a
 * shortcut and ignores startMainActivity), with the source bounds and ActivityOptions each start was given.
 */
@Implements(LauncherApps::class)
class RecordingLauncherApps : ShadowLauncherApps() {

    /** One start: what was started (a component, or "package/shortcut id"), for whom, and from where. */
    data class Start(val target: String, val user: UserHandle?, val sourceBounds: Rect?, val options: Bundle?)

    @Implementation
    fun startMainActivity(component: ComponentName, user: UserHandle, sourceBounds: Rect?, opts: Bundle?) {
        starts += Start(component.flattenToString(), user, sourceBounds, opts)
    }

    @Implementation
    public override fun startShortcut(packageName: String, shortcutId: String, sourceBounds: Rect?, startActivityOptions: Bundle?, user: UserHandle) {
        starts += Start("$packageName/$shortcutId", user, sourceBounds, startActivityOptions)
    }

    companion object {
        val starts = mutableListOf<Start>()
    }
}
