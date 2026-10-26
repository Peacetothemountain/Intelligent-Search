package com.pixel.intelligentsearch.feature.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A tap on the home screen's search bar opens the overlay without Pixel Launcher's widget launch animation (a copy of
 * the bar as a lavender bar at the top, a black fill, a dark scrim, a square box over the rounded bar). Tapped as an
 * activity the launcher animates it; tapped as a broadcast the launcher only hands its animation to this app's next
 * activity start (decompiled QuickstepInteractionHandler); a never-shown activity started right after takes it (Android
 * moves the launch cookie to the newest start of the same transition).
 */
@RunWith(RobolectricTestRunner::class)
class WidgetLaunchReceiverTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun overlay() = Intent(context, WidgetActivity::class.java).apply { identifier = "widget_1_slot_pill" }

    /** Makes [launcherPackage] the home screen. */
    private fun home(launcherPackage: String) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        shadowOf(context.packageManager).addResolveInfoForIntent(homeIntent, android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = launcherPackage
                name = "$launcherPackage.Home"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = launcherPackage }
            }
        })
    }

    @Test
    fun withPixelLauncherEveryBarTapIsABroadcast() {
        home("com.google.android.apps.nexuslauncher")
        assertTrue("the launcher animates activity taps", shadowOf(SearchWidgetProvider.overlayPendingIntent(context, 7, overlay())).isBroadcast)
        // Lens, voice and shortcuts too: the launcher would otherwise grow them out of the bar and back into it.
        val web = Intent(Intent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(shadowOf(SearchWidgetProvider.overlayPendingIntent(context, 8, web)).isBroadcast)
    }

    @Test
    fun withAnotherLauncherTapsStayActivitiesThatAlwaysOpen() {
        home("com.example.launcher")
        assertTrue(shadowOf(SearchWidgetProvider.overlayPendingIntent(context, 7, overlay())).isActivity)
    }

    @Test
    fun theReceiverOpensTheOverlayThenLetsTheSinkTakeTheLaunchAnimation() {
        home("com.google.android.apps.nexuslauncher")
        val pending = SearchWidgetProvider.overlayPendingIntent(context, 9, overlay())
        val broadcast = shadowOf(pending).savedIntent
        WidgetLaunchReceiver().onReceive(context, broadcast)

        // Robolectric hands back the started activities newest first.
        val app = shadowOf(context as Application)
        val second = app.nextStartedActivity
        val first = app.nextStartedActivity
        assertEquals("the overlay first", WidgetActivity::class.java.name, first.component?.className)
        assertTrue("without Android's own motion", first.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
        assertEquals("the tap's own identity", "widget_1_slot_pill", first.identifier)
        assertEquals("then the never-shown activity, which takes the launcher's animation", LaunchAnimationSink::class.java.name, second.component?.className)
    }

    @Test
    fun lensVoiceAndShortcutsKeepAndroidsUsualMotion() {
        home("com.google.android.apps.nexuslauncher")
        val lens = Intent(Intent.ACTION_VIEW).setClassName("com.google.ar.lens", "com.google.vr.apps.ornament.app.lens.LensLauncherActivity")
        WidgetLaunchReceiver().onReceive(context, shadowOf(SearchWidgetProvider.overlayPendingIntent(context, 10, lens)).savedIntent)

        val app = shadowOf(context as Application)
        app.nextStartedActivity
        val opened = app.nextStartedActivity
        assertEquals("com.google.ar.lens", opened.component?.packageName)
        assertTrue("its own task", opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("only the overlay, which brings its own entrance, opens without motion", 0, opened.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
