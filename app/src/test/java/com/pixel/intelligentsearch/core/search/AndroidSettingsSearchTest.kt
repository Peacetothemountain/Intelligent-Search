package com.pixel.intelligentsearch.core.search

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import com.pixel.intelligentsearch.feature.search.resultSectionOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Matching queries to Android Settings pages, hiding pages that don't open here, and App Info for installed apps. */
@RunWith(RobolectricTestRunner::class)
class AndroidSettingsSearchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Makes [intent] open an activity, as the Settings app would on a phone. */
    private fun resolves(intent: Intent) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.SubSettings"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }

    /** A phone whose Settings app opens every catalogue page and App Info. */
    private fun everyPageResolves(): AndroidSettingsSearch {
        AndroidSettingsCatalog.pages.forEach { resolves(Intent(it.action)) }
        resolves(AndroidSettingsSearch.appDetailsIntent(context.packageName))
        return AndroidSettingsSearch(context)
    }

    private fun titles(search: AndroidSettingsSearch, query: String) = search.search(query).map { it.title }

    private fun app(name: String, pkg: String) = AppItem(name = name, packageName = pkg, icon = ColorDrawable())

    @Test
    fun everyCatalogueQueryFindsItsPageFirst() {
        val search = everyPageResolves()
        val expected = mapOf(
            "battery" to "Battery", "hotspot" to "Network & Internet", "dark mode" to "Display", "wifi" to "Wi-Fi",
            "wi-fi" to "Wi-Fi", "bluetooth" to "Bluetooth", "display" to "Display", "brightness" to "Display",
            "storage" to "Storage", "notifications" to "App Notifications", "location" to "Location",
            "privacy" to "Privacy", "accessibility" to "Accessibility", "sound" to "Sound & Vibration",
            "vibration" to "Sound & Vibration", "date and time" to "Date & Time", "language" to "Languages",
            "keyboard" to "On-Screen Keyboard", "developer options" to "Developer Options", "about phone" to "About Phone",
            "default apps" to "Default Apps", "data usage" to "Data Usage", "nfc" to "NFC", "cast" to "Cast",
            "airplane mode" to "Airplane Mode", "do not disturb" to "Do Not Disturb", "dnd" to "Do Not Disturb",
            "bt" to "Bluetooth", "vpn" to "VPN"
        )
        for ((query, title) in expected) {
            assertEquals("first result for \"$query\"", title, titles(search, query).firstOrNull())
        }
    }

    @Test
    fun synonymsAndPartialWordsFindThePage() {
        val search = everyPageResolves()
        assertEquals("Network & Internet", titles(search, "tethering").first())
        assertEquals("Security", titles(search, "fingerprint").first())
        assertEquals("About Phone", titles(search, "build number").first())
        assertEquals("Battery", titles(search, "batt").first())
        assertTrue("Night Light" in titles(search, "blue light"))
    }

    @Test
    fun typosStillFindThePage() {
        val search = everyPageResolves()
        assertEquals("Battery", titles(search, "batery").first())
        assertEquals("Bluetooth", titles(search, "blutooth").first())
        assertEquals("Accessibility", titles(search, "acessibility").first())
        assertEquals("Network & Internet", titles(search, "hotpsot").first())
        assertEquals("Developer Options", titles(search, "devloper options").first())
    }

    @Test
    fun theWordSettingsIsIgnoredButAloneMatchesNothing() {
        val search = everyPageResolves()
        assertEquals("Battery", titles(search, "battery settings").first())
        assertEquals("Wi-Fi", titles(search, "settings wifi").first())
        // "settings" is the Settings app's: no page competes with it.
        assertEquals(emptyList<String>(), titles(search, "settings"))
    }

    @Test
    fun unrelatedAndTooShortQueriesMatchNothing() {
        val search = everyPageResolves()
        assertEquals(emptyList<String>(), titles(search, "b"))
        // App names that only sound like or are one slip from a settings word: "other", "adb", "new", "contactless".
        for (query in listOf("weather", "spotify", "youtube", "news", "contacts")) {
            assertEquals("results for \"$query\"", emptyList<String>(), titles(search, query))
        }
        // "clock" is a Date & Time keyword, but "lock screen" is not a clock.
        assertEquals(listOf("Date & Time"), titles(search, "clock"))
        // A match in the middle of a word is not a match: "ion" is not Location.
        assertTrue("Location" !in titles(search, "ion"))
    }

    @Test
    fun resultsAreCappedAndCarryTheirIntentAndSpokenLabel() {
        val search = everyPageResolves()
        val results = search.search("battery")
        assertTrue(results.size <= 4)
        val battery = results.first()
        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, battery.intent.action)
        assertEquals("Battery settings", battery.spokenLabel)
    }

    @Test
    fun pagesThatDoNotOpenOnThisPhoneAreHidden() {
        // Only Wi-Fi resolves; Bluetooth's page doesn't exist here.
        resolves(Intent(Settings.ACTION_WIFI_SETTINGS))
        val search = AndroidSettingsSearch(context)
        assertEquals(emptyList<String>(), titles(search, "bluetooth"))
        assertEquals(listOf("Wi-Fi"), titles(search, "wifi"))
    }

    @Test
    fun resolutionIsCachedPerPage() {
        val search = AndroidSettingsSearch(context)
        assertEquals(emptyList<String>(), titles(search, "bluetooth"))
        // Installing the page afterwards doesn't change the cached answer for this process.
        resolves(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        assertEquals(emptyList<String>(), titles(search, "bluetooth"))
        assertEquals(listOf("Bluetooth"), titles(AndroidSettingsSearch(context), "bluetooth"))
    }

    @Test
    fun appInfoQueriesOpenTheAppsDetailsPage() {
        val search = everyPageResolves()
        val chrome = app("Chrome", "com.android.chrome")
        val findApps: (String) -> List<AppItem> = { name -> listOf(chrome).filter { it.name.startsWith(name, ignoreCase = true) } }

        for (query in listOf("app info chrome", "App Info for Chrome", "chrome app info", "app settings chr")) {
            val result = search.search(query, findApps).firstOrNull()
            assertEquals("result for \"$query\"", "Chrome App Info", result?.title)
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, result!!.intent.action)
            assertEquals("package:com.android.chrome", result.intent.dataString)
            assertEquals("App info for Chrome", result.spokenLabel)
        }
        // No such app: no App Info result (the general Apps page may still be offered).
        assertTrue(search.search("app info spotify", findApps).none { it.app != null })
    }

    @Test
    fun appInfoTargetParsesOnlyAppInfoQueries() {
        assertEquals("chrome", AndroidSettingsSearch.appInfoTarget("app info chrome"))
        assertEquals("google maps", AndroidSettingsSearch.appInfoTarget("google maps app info"))
        assertNull(AndroidSettingsSearch.appInfoTarget("app info"))
        assertNull(AndroidSettingsSearch.appInfoTarget("battery"))
    }

    @Test
    fun appInfoIsHiddenWhenAppDetailsDoesNotOpen() {
        val search = AndroidSettingsSearch(context)
        val chrome = app("Chrome", "com.android.chrome")
        assertTrue(search.search("app info chrome") { listOf(chrome) }.none { it.app != null })
    }

    @Test
    fun appInfoFindsAppsThroughTheAppIndex() {
        val search = everyPageResolves()
        val coordinator = UnifiedSearchCoordinator(
            context,
            SystemActionRouter(context, SystemToggleManager(context)),
            MultiProfileManager(context),
            SettingsManager(context)
        )
        coordinator.bulkIndexApps(listOf(app("Chrome", "com.android.chrome"), app("Google Maps", "com.google.android.apps.maps")))
        val results = search.search("app info maps") { coordinator.searchApps(it, limit = 3) }
        assertEquals(listOf("Google Maps App Info"), results.filter { it.app != null }.map { it.title })
        assertEquals("Google Maps App Info", results.first().title)
    }

    @Test
    fun aSettingsPageNeverRanksAboveAnExactAppMatch() {
        // An app named like a settings page keeps first place: settings pages sit in their own section after the apps.
        val order = resultSectionOrder(appWeight = 0, webWeight = 100, contactWeight = 50, fileWeight = 100)
        assertTrue(order.indexOf("android_settings") == order.indexOf("apps") + 1)
        for (weights in listOf(listOf(50, 50, 50, 50), listOf(100, 0, 0, 0), listOf(0, 0, 0, 0))) {
            val o = resultSectionOrder(weights[0], weights[1], weights[2], weights[3])
            assertEquals(o.indexOf("apps") + 1, o.indexOf("android_settings"))
        }

        // And "settings" still finds the Settings app first, with no page competing.
        val coordinator = UnifiedSearchCoordinator(
            context,
            SystemActionRouter(context, SystemToggleManager(context)),
            MultiProfileManager(context),
            SettingsManager(context)
        )
        coordinator.bulkIndexApps(listOf(app("Settings", "com.android.settings"), app("Settings Shortcut Maker", "com.example.shortcuts")))
        assertEquals("Settings", coordinator.searchApps("settings").first().name)
        assertEquals(emptyList<String>(), titles(everyPageResolves(), "settings"))
    }
}
