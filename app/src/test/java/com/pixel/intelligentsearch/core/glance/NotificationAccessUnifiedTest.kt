package com.pixel.intelligentsearch.core.glance

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.MediaSessionAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One notification listener for the whole app: Search Your Notifications, At a Glance's Now Playing and the widget's
 * Media Controls share its component, its grant and its settings page, so the app shows once in Android's
 * Notification Access list and one grant turns all three on.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationAccessUnifiedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun grant(component: ComponentName, granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(component, granted)
    }

    @Test
    fun everyFeatureUsesTheOneListener() {
        assertEquals(ComponentName(context, NotificationAccessListener::class.java), NotificationAccess.component(context))
        assertEquals(NotificationAccessListener.component(context), NotificationAccess.component(context))
    }

    @Test
    fun oneGrantIsTheOneCheck() {
        grant(NotificationAccess.component(context), false)
        assertFalse(NotificationAccess.isGranted(context))
        // Without it, the widget's Media Controls and Now Playing see no media sessions either.
        assertTrue(MediaSessionAccess.activeControllers(context).isEmpty())
        assertEquals(null, MediaSessionAccess.nowPlaying(context))

        grant(NotificationAccess.component(context), true)
        assertTrue(NotificationAccess.isGranted(context))
    }

    @Test
    fun aGrantToAnotherComponentDoesNotCount() {
        grant(NotificationAccess.component(context), false)
        grant(ComponentName(context, "com.pixel.intelligentsearch.core.system.MediaSessionListenerService"), true)
        assertFalse(NotificationAccess.isGranted(context))
    }

    @Test
    fun theGrantPageIsTheOneListenersOwn() {
        val intents = NotificationAccess.settingsIntents(context)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intents.first().action)
        assertEquals(
            NotificationAccess.component(context).flattenToString(),
            intents.first().getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)
        )
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intents.last().action)
    }

    @Test
    fun theListenerStaysConnectedOnlyWhileSomethingNeedsIt() {
        assertTrue(NotificationAccessListener.staysBound(searchNotifications = true, widgetMedia = false))
        assertTrue(NotificationAccessListener.staysBound(searchNotifications = false, widgetMedia = true))
        assertTrue(NotificationAccessListener.staysBound(searchNotifications = true, widgetMedia = true))
        // Now Playing needs only the grant, so with search and the widget's row off the listener lets go.
        assertFalse(NotificationAccessListener.staysBound(searchNotifications = false, widgetMedia = false))
    }

    @Test
    fun theManifestDeclaresExactlyOneNotificationListener() {
        // Unit tests run from the module directory; the repo root is tried too.
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("service")
        val listeners = (0 until nodes.length).map { nodes.item(it) as Element }.filter { service ->
            val actions = service.getElementsByTagName("action")
            (0 until actions.length).any { (actions.item(it) as Element).getAttributeNS(androidNs, "name") == NotificationListenerService.SERVICE_INTERFACE }
        }
        assertEquals(1, listeners.size)
        val listener = listeners.single()
        assertEquals(".core.glance.NotificationAccessListener", listener.getAttributeNS(androidNs, "name"))
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", listener.getAttributeNS(androidNs, "permission"))
    }

    @Test
    fun androidFindsOneListenerInTheApp() {
        val found = context.packageManager.queryIntentServices(
            Intent(NotificationListenerService.SERVICE_INTERFACE).setPackage(context.packageName), 0
        )
        assertEquals(listOf(NotificationAccessListener::class.java.name), found.map { it.serviceInfo.name })
    }
}
