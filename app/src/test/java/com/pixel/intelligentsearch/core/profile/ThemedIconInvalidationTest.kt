package com.pixel.intelligentsearch.core.profile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.ui.clearThemedIconCache
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLauncherApps

/**
 * An app that is updated, reinstalled or comes back shows its current icon in results, Best Match and the quick-app
 * pills: the cached icon is dropped when the launcher reports the package changed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ThemedIconInvalidationTest.CallbackRecordingLauncherApps::class])
class ThemedIconInvalidationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        clearThemedIconCache()
        CallbackRecordingLauncherApps.callbacks.clear()
        shadowOf(context.packageManager).apply {
            installPackage(
                PackageInfo().apply {
                    packageName = APP
                    applicationInfo = ApplicationInfo().apply { packageName = APP }
                }
            )
            setApplicationIcon(APP, ColorDrawable(Color.BLUE))
        }
    }

    /** The callback a fresh [MultiProfileManager] registers for package events. */
    private fun launcherCallback(): LauncherApps.Callback {
        MultiProfileManager(context)
        return CallbackRecordingLauncherApps.callbacks.single()
    }

    @Test
    fun anUpdatedAppsCachedIconIsDropped() {
        val callback = launcherCallback()
        getThemedAppIcon(context, APP)
        assertNotNull(peekThemedAppIcon(APP))

        callback.onPackageChanged(APP, Process.myUserHandle())

        assertNull(peekThemedAppIcon(APP))
    }

    @Test
    fun aReinstalledAppsCachedIconIsDropped() {
        val callback = launcherCallback()
        getThemedAppIcon(context, APP)

        callback.onPackageRemoved(APP, Process.myUserHandle())
        assertNull(peekThemedAppIcon(APP))

        getThemedAppIcon(context, APP)
        callback.onPackageAdded(APP, Process.myUserHandle())
        assertNull(peekThemedAppIcon(APP))
    }

    @Test
    fun appsThatComeBackDropTheirCachedIcons() {
        // Apps on an SD card, or a work profile turned back on, arrive together.
        val callback = launcherCallback()
        getThemedAppIcon(context, APP)
        assertNotNull(peekThemedAppIcon(APP))

        callback.onPackagesAvailable(arrayOf("com.example.other", APP), Process.myUserHandle(), true)

        assertNull(peekThemedAppIcon(APP))
    }

    @Test
    fun otherAppsKeepTheirCachedIcons() {
        val callback = launcherCallback()
        getThemedAppIcon(context, APP)

        callback.onPackageChanged("com.example.other", Process.myUserHandle())
        // Only the package's own name counts: "app" is not "example.app".
        callback.onPackageChanged("example.app", Process.myUserHandle())

        assertNotNull(peekThemedAppIcon(APP))
    }

    @Test
    fun anUpdatedIconPackRedrawsTheIconsItDrew() {
        val callback = launcherCallback()
        getThemedAppIcon(context, APP, activePackOverride = ICON_PACK)
        assertNotNull(peekThemedAppIcon(APP, activePackOverride = ICON_PACK))

        callback.onPackageChanged(ICON_PACK, Process.myUserHandle())

        assertNull(peekThemedAppIcon(APP, activePackOverride = ICON_PACK))
    }

    /** A LauncherApps that keeps the callbacks registered with it, so a test can deliver package events. */
    @Implements(LauncherApps::class)
    class CallbackRecordingLauncherApps : ShadowLauncherApps() {
        @Implementation
        override fun registerCallback(callback: LauncherApps.Callback, handler: Handler?) {
            callbacks += callback
            super.registerCallback(callback, handler)
        }

        companion object {
            val callbacks = mutableListOf<LauncherApps.Callback>()
        }
    }

    private companion object {
        const val APP = "com.example.app"
        const val ICON_PACK = "com.example.iconpack"
    }
}
