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
import com.pixel.intelligentsearch.core.navigation.Route
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
    fun everydayPhrasingsOfASettingFindItsPage() {
        val search = everyPageResolves()
        val expected = mapOf(
            "blue light filter" to "Night Light", "turn on bluetooth" to "Bluetooth", "screen timeout" to "Display",
            "dark mode" to "Display", "power saving mode" to "Battery Saver", "wifi password" to "Wi-Fi",
            "app permissions" to "Privacy", "turn off wifi" to "Wi-Fi", "enable dark mode" to "Display",
            "bluetooth on" to "Bluetooth", "night light off" to "Night Light", "switch on airplane mode" to "Airplane Mode"
        )
        for ((query, title) in expected) {
            assertEquals("first result for \"$query\"", title, titles(search, query).firstOrNull())
        }
        // Naming the setting outright, they are strong matches, lifted like "bluetooth" is.
        assertTrue(search.search("turn on bluetooth").first().isStrongMatch)
        // The switch words alone name no page.
        assertEquals(emptyList<String>(), titles(search, "turn on"))
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
        // "clock" is the Clock app's: no longer a Date & Time keyword, and "lock screen" is not a clock.
        assertEquals(emptyList<String>(), titles(search, "clock"))
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
        // An app named like a settings page keeps first place: settings pages sit in their own section after the
        // apps, straight after them when the query names a page outright.
        val order = resultSectionOrder(appWeight = 0, webWeight = 100, contactWeight = 50, fileWeight = 100, settingsStrongMatch = true)
        assertTrue(order.indexOf("android_settings") == order.indexOf("apps") + 1)
        for (weights in listOf(listOf(0, 100, 50, 100), listOf(50, 50, 50, 50), listOf(100, 0, 0, 0), listOf(0, 0, 0, 0))) {
            val strong = resultSectionOrder(weights[0], weights[1], weights[2], weights[3], settingsStrongMatch = true)
            assertEquals(strong.indexOf("apps") + 1, strong.indexOf("android_settings"))
            val weak = resultSectionOrder(weights[0], weights[1], weights[2], weights[3])
            assertTrue("$weak", weak.indexOf("android_settings") > weak.indexOf("apps"))
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

    @Test
    fun theSettingsSectionSitsAfterWebAndContactsUnlessAPageIsNamedOutright() {
        // Default weights: a weak match waits below the web and the contacts; a strong one comes straight after the apps.
        assertEquals(listOf("apps", "web", "contacts", "android_settings", "files"), resultSectionOrder(50, 50, 50, 50))
        assertEquals(listOf("apps", "android_settings", "web", "contacts", "files"), resultSectionOrder(50, 50, 50, 50, settingsStrongMatch = true))
        // Whatever the weights, the other sections keep the order they had without settings.
        for (weights in listOf(listOf(20, 80, 60, 40), listOf(80, 10, 60, 40), listOf(70, 60, 10, 90), listOf(0, 0, 0, 100))) {
            val before = listOf("apps" to weights[0], "web" to weights[1], "contacts" to weights[2], "files" to weights[3])
                .sortedByDescending { it.second }.map { it.first }
            for (strong in listOf(false, true)) {
                val order = resultSectionOrder(weights[0], weights[1], weights[2], weights[3], strong)
                assertEquals("$weights strong=$strong", before, order - "android_settings")
                val settings = order.indexOf("android_settings")
                if (strong) {
                    assertEquals(order.indexOf("apps") + 1, settings)
                } else {
                    assertEquals(maxOf(order.indexOf("apps"), order.indexOf("web"), order.indexOf("contacts")) + 1, settings)
                }
            }
        }
    }

    @Test
    fun aQueryNamingAPageIsAStrongMatchAndAPartOfOneIsNot() {
        val search = everyPageResolves()
        for (query in listOf("battery", "Battery Settings", "settings wifi", "wi-fi", "dnd", "screen timeout", "date & time")) {
            assertTrue("\"$query\" names its page", search.search(query).first().isStrongMatch)
        }
        for (query in listOf("batt", "batery", "phone", "model")) {
            assertTrue("\"$query\" only partly names a page", search.search(query).none { it.isStrongMatch })
        }
    }

    /** A Pixel's usual apps, and an index lookup that finds them by the start of their name. */
    private val pixelApps = listOf("Chrome", "Camera", "Phone", "Google", "Files", "Clock", "Instagram", "Wallet", "Home", "Patreon")
        .map { app(it, "com.example.${it.lowercase()}") }
    private val findPixelApps: (String) -> List<AppItem> = { query ->
        pixelApps.filter { QueryNormalizer.normalize(it.name).startsWith(query) }.take(3)
    }
    private val people = listOf("Carrie Fisher", "Tim Cook", "Pat Smith")

    @Test
    fun everydayWordsAppNamesAndPeopleDoNotBecomeSettingsPages() {
        val search = everyPageResolves()
        fun results(query: String) = search.search(query, people, findPixelApps).map { it.title }
        // A query that is an app's name is that app's, which the apps already show: no App Info row (only asked
        // for, as in "chrome settings"), and no page that merely shares a word with it.
        assertEquals(listOf("Chrome App Info"), results("chrome settings"))
        for (query in listOf("chrome", "camera", "phone", "google", "files", "clock", "wallet", "home", "patreon")) {
            assertEquals("results for \"$query\"", emptyList<String>(), results(query))
        }
        // The start of an app's or a person's name, and short words, are theirs.
        for (query in listOf("chr", "cam", "file", "insta", "pay", "tim", "pat", "sid", "carrie")) {
            assertEquals("results for \"$query\"", emptyList<String>(), results(query))
        }

        // Without such apps or people installed, the dropped keywords and the stricter matching still keep them out.
        for (query in listOf("chrome", "chr", "chrome settings", "camera", "cam", "google", "clock", "pay", "tim", "pat", "sid", "patreon")) {
            assertEquals("results for \"$query\" alone", emptyList<String>(), titles(search, query))
        }
        // "model" is About Phone's (it shows the model), as a weak match, and no longer Do Not Disturb's "modes".
        assertEquals(listOf("About Phone"), titles(search, "model"))
        assertTrue(search.search("model").none { it.isStrongMatch })
        // "phone" is no longer a Default Apps keyword.
        assertEquals(listOf("About Phone"), titles(search, "phone"))
    }

    @Test
    fun multiWordQueriesMatchWordForWord() {
        val search = everyPageResolves()
        assertEquals(listOf("System Update"), titles(search, "system update"))
        assertEquals(emptyList<String>(), titles(search, "screen recorder"))
        assertEquals(listOf("Network & Internet"), titles(search, "mobile hotspot"))
        assertEquals(listOf("Network & Internet"), titles(search, "internet"))
        // Digital Wellbeing has no public Settings action, and Display's "screen timeout" is not it.
        assertEquals(emptyList<String>(), titles(search, "screen time"))
        assertEquals("Display", titles(search, "screen tim").first())
        assertEquals("Display", titles(search, "text size").first())
        // A slip inside one word still counts.
        assertEquals("Developer Options", titles(search, "devloper opt").first())
    }

    @Test
    fun commonSettingsStillFindTheirPageFirst() {
        val search = everyPageResolves()
        val expected = mapOf(
            "wi-fi" to "Wi-Fi", "wifi" to "Wi-Fi", "dnd" to "Do Not Disturb", "screen timeout" to "Display",
            "font size" to "Display", "ringtone" to "Sound & Vibration", "data saver" to "Network & Internet", "vpn" to "VPN",
            "bluetooth" to "Bluetooth", "airplane" to "Airplane Mode", "lock screen" to "Security",
            "hotspot" to "Network & Internet", "dark mode" to "Display", "battery" to "Battery", "storage" to "Storage",
            "display" to "Display", "sound" to "Sound & Vibration", "wallpaper" to "Wallpaper & Style"
        )
        for ((query, title) in expected) {
            val first = search.search(query, people, findPixelApps).firstOrNull()
            assertEquals("first result for \"$query\"", title, first?.title)
            assertTrue("\"$query\" names its page", first!!.isStrongMatch)
        }
    }

    @Test
    fun textToSpeechIsItsOwnPageNotVoiceInput() {
        val search = everyPageResolves()
        for (query in listOf("text to speech", "text-to-speech", "tts")) {
            val results = search.search(query)
            assertEquals("results for \"$query\"", listOf("Text-to-Speech Output"), results.map { it.title })
            assertEquals("com.android.settings.TTS_SETTINGS", results.first().intent.action)
        }
        assertEquals("Voice Input", titles(search, "voice input").first())
    }

    @Test
    fun textToSpeechIsHiddenWhereItsPageDoesNotOpen() {
        resolves(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        val search = AndroidSettingsSearch(context)
        assertEquals(emptyList<String>(), titles(search, "text to speech"))
        assertEquals(emptyList<String>(), titles(search, "tts"))
    }

    @Test
    fun newPagesOpenThroughTheirPublicActions() {
        val search = everyPageResolves()
        assertEquals("android.settings.SYSTEM_UPDATE_SETTINGS", search.search("software update").first().intent.action)
        assertEquals(Intent.ACTION_SET_WALLPAPER, search.search("wallpaper").first().intent.action)
        assertEquals("Device & App Notifications", titles(search, "notification access").first())
    }

    @Test
    fun resultsAreEqualByWhatTheyShowAndOpen() {
        val search = everyPageResolves()
        val first = search.search("battery").first()
        val again = search.search("battery").first()
        assertTrue(first.intent !== again.intent)
        assertEquals(first, again)
        assertEquals(first.hashCode(), again.hashCode())
        // A weak "batt" Battery is not the strong "battery" one.
        assertTrue(first != search.search("batt").first())
        assertEquals(search.search("app info chrome", findPixelApps), search.search("app info chrome", findPixelApps))
    }

    private val everydayApps = listOf("Maps", "YouTube", "Gmail", "Chrome", "Pinterest").map { app(it, "com.example.${it.lowercase()}") }
    private val findEverydayApps: (String) -> List<AppItem> = { query ->
        everydayApps.filter { QueryNormalizer.normalize(it.name).startsWith(query) }
    }

    @Test
    fun appInfoIsOfferedOnlyWhenAskedFor() {
        val search = everyPageResolves()
        fun results(query: String) = search.search(query, emptyList(), findEverydayApps)
        // Typing an app's name shows the app; App Info is not added to it, nor is a page sharing a word ("chrome" is not Cast).
        for (query in listOf("maps", "youtube", "gmail", "chrome", "Chrome", "pinterest")) {
            assertEquals("results for \"$query\"", emptyList<AndroidSettingResult>(), results(query))
        }
        // Asked for in so many words, it is the first result and names its page outright.
        for (query in listOf("app info maps", "maps app info", "maps settings", "settings maps", "app settings maps",
            "app details maps", "application info maps", "maps app details", "app info for maps")) {
            val first = results(query).firstOrNull()
            assertEquals("first result for \"$query\"", "Maps App Info", first?.title)
            assertTrue("\"$query\" asks for App Info", first!!.isStrongMatch)
        }
        assertEquals("YouTube App Info", results("youtube settings").first().title)
        assertEquals("Gmail App Info", results("gmail app info").first().title)
    }

    @Test
    fun everydayWordsAreOnlyWeakMatches() {
        val search = everyPageResolves()
        val everyday = listOf(
            "dictionary", "time", "date", "space", "memory", "disk", "background", "power", "pin", "pattern", "password",
            "sync", "silence", "print", "sim", "carrier", "region", "launcher"
        )
        for (query in everyday) {
            val results = search.search(query)
            // Still offered, below the web and the contacts.
            assertTrue("\"$query\" still finds a page", results.isNotEmpty())
            assertTrue("\"$query\" is an everyday word: $results", results.none { it.isStrongMatch })
        }
        // A weak match gives way to an app the query starts: "pin" is Pinterest's.
        assertEquals(emptyList<AndroidSettingResult>(), search.search("pin", emptyList(), findEverydayApps))
        assertEquals(emptyList<AndroidSettingResult>(), search.search("pin", listOf("Pinky Tuscadero")) { emptyList() })
    }

    @Test
    fun settingsWordsAndTitlesStayStrongMatches() {
        val search = everyPageResolves()
        val expected = mapOf(
            "battery" to "Battery", "wifi" to "Wi-Fi", "bluetooth" to "Bluetooth", "hotspot" to "Network & Internet",
            "dnd" to "Do Not Disturb", "vpn" to "VPN", "storage" to "Storage", "display" to "Display",
            "sound" to "Sound & Vibration", "wallpaper" to "Wallpaper & Style", "dark mode" to "Display",
            "brightness" to "Display", "ringtone" to "Sound & Vibration", "nfc" to "NFC", "airplane" to "Airplane Mode",
            "location" to "Location", "printing" to "Printing", "date and time" to "Date & Time"
        )
        for ((query, title) in expected) {
            val first = search.search(query, emptyList(), findEverydayApps).firstOrNull()
            assertEquals("first result for \"$query\"", title, first?.title)
            assertTrue("\"$query\" names its page", first!!.isStrongMatch)
        }
        // Every word on the list is a keyword some page has.
        val keywords = AndroidSettingsCatalog.pages.flatMap { it.keywords }.map(QueryNormalizer::normalize).toSet()
        assertEquals(emptySet<String>(), AndroidSettingsSearch.UNAMBIGUOUS_KEYWORDS - keywords)
    }

    @Test
    fun wordsInManyPagesAloneMatchNothing() {
        val search = everyPageResolves()
        assertEquals(emptyList<String>(), titles(search, "mode"))
        assertEquals(emptyList<String>(), titles(search, "data"))
        // With more to go on they find their page.
        assertEquals("Airplane Mode", titles(search, "airplane mode").first())
        assertEquals("Data Usage", titles(search, "data usage").first())
        assertEquals("Do Not Disturb", titles(search, "modes").first())
    }

    @Test
    fun restoredKeywordsAndSpellingVariantsFindTheirPage() {
        val search = everyPageResolves()
        val strong = mapOf(
            "tap to pay" to "Contactless Payments", "phone number" to "About Phone", "ring tone" to "Sound & Vibration",
            "hot spot" to "Network & Internet", "air plane mode" to "Airplane Mode", "screen brightness" to "Display",
            "silent mode" to "Do Not Disturb", "do not disturb mode" to "Do Not Disturb"
        )
        for ((query, title) in strong) {
            val first = search.search(query).firstOrNull()
            assertEquals("first result for \"$query\"", title, first?.title)
            assertTrue("\"$query\" names its page", first!!.isStrongMatch)
        }
        // "silent" is an everyday word, and "wifii" a slip: they find the page, as weak matches.
        for ((query, title) in mapOf("silent" to "Do Not Disturb", "wifii" to "Wi-Fi")) {
            val first = search.search(query).firstOrNull()
            assertEquals("first result for \"$query\"", title, first?.title)
            assertTrue("\"$query\" is a weak match", !first!!.isStrongMatch)
        }
        // "pay" and "phone" alone still don't reach for them.
        assertEquals(emptyList<String>(), titles(search, "pay"))
        assertTrue("Contactless Payments" !in titles(search, "phone"))
    }

    @Test
    fun theAppsOwnSettingsPagesAreFoundAndOpenInTheApp() {
        val search = everyPageResolves()
        for ((query, title) in mapOf(
            "hidden apps" to "Manage Hidden Apps", "icon pack" to "Custom Icons", "api key" to "AI Answers",
            "appearance" to "Appearance", "widget customization" to "Widget Customization", "backup" to "Encrypted Backup"
        )) {
            val result = search.search(query).firstOrNull { it.title == title }
            requireNotNull(result) { "\"$query\" didn't offer $title" }
            assertEquals("Intelligent Search Settings", result.subtitle)
            assertTrue("$title opens in the app", result.appRoute != null)
            assertEquals(title, Route.fromKey(result.appRoute).let { titleOf(it) })
        }
    }

    @Test
    fun anOwnPageNamedOutrightIsAStrongMatch() {
        val search = everyPageResolves()
        assertTrue(search.search("manage hidden apps").first { it.appRoute != null }.isStrongMatch)
    }

    @Test
    fun aPinnedOwnPageComesBackById() {
        val search = everyPageResolves()
        val page = search.page(AppSettingsCatalog.ID_PREFIX + "appearance")
        assertEquals("Appearance", page?.title)
        assertEquals("appearance", page?.appRoute)
    }

    /** The page title each route's screen shows. */
    private fun titleOf(route: Route): String = when (route) {
        Route.ManageHiddenApps -> "Manage Hidden Apps"
        Route.CustomIcons -> "Custom Icons"
        Route.AiAnswers -> "AI Answers"
        Route.Appearance -> "Appearance"
        Route.WidgetCustomization -> "Widget Customization"
        Route.BackupRestore -> "Encrypted Backup"
        else -> route.toString()
    }
}
