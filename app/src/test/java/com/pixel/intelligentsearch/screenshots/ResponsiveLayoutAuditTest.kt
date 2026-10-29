package com.pixel.intelligentsearch.screenshots

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.HistoryDao
import com.pixel.intelligentsearch.core.data.HistoryEntity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.ContactActionTargets
import com.pixel.intelligentsearch.feature.search.FakeContactBook
import com.pixel.intelligentsearch.feature.search.ResultThumbnails
import com.pixel.intelligentsearch.feature.search.SearchOverlayScreen
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import com.pixel.intelligentsearch.feature.settings.AppSearchScreen
import com.pixel.intelligentsearch.feature.settings.AppearanceScreen
import com.pixel.intelligentsearch.feature.settings.BackupRestoreScreen
import com.pixel.intelligentsearch.feature.settings.ContactSearchScreen
import com.pixel.intelligentsearch.feature.settings.CustomIconsScreen
import com.pixel.intelligentsearch.feature.settings.FileSearchScreen
import com.pixel.intelligentsearch.feature.settings.LaunchPortalScreen
import com.pixel.intelligentsearch.feature.settings.MainSettingsScreen
import com.pixel.intelligentsearch.feature.settings.ManageHiddenAppsScreen
import com.pixel.intelligentsearch.feature.settings.SearchBehaviorScreen
import com.pixel.intelligentsearch.feature.settings.SearchPillsScreen
import com.pixel.intelligentsearch.feature.settings.SearchSourcesScreen
import com.pixel.intelligentsearch.feature.settings.SetupChecklistScreen
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.WebSearchScreen
import com.pixel.intelligentsearch.feature.settings.WidgetSettingsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

/**
 * Renders every settings page, the main overlay states and the overlay's network-fed result cards on phones from
 * 320 dp to 527 dp at 85% to 200% text, a phone in landscape, an unfolded foldable, tablets both ways and an Android
 * XR sized window, and records what [LayoutAudit] finds. A screen fails on any serious finding
 * ([LayoutAudit.isSerious]) not in [ACCEPTED]. Run with `./gradlew testDebugUnitTest --tests
 * "*ResponsiveLayoutAuditTest*" -PlayoutAudit`; the report of every finding is build/layout-audit/report.tsv, with a
 * capture of every screen beside it.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class ResponsiveLayoutAuditTest(private val screen: String, private val device: Device) {

    data class Device(val name: String, val widthDp: Int, val heightDp: Int, val fontScale: Float) {
        override fun toString() = name
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule(confinedEffects())

    @Inject lateinit var historyDao: HistoryDao

    private val prefs: SharedPreferences
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun audit() {
        assumeTrue("run with -PlayoutAudit", System.getProperty("layoutAudit") == "true")
        // The overlay and the voice sheet fill the real screen; settings pages scroll.
        val isOverlay = screen.startsWith("overlay_") || screen.startsWith("voice_")
        // Settings pages scroll, so they get a tall window to lay out every row; the overlay fills the real screen.
        val heightDp = if (isOverlay) device.heightDp else maxOf(device.heightDp, 3000)
        RuntimeEnvironment.setQualifiers("w${device.widthDp}dp-h${heightDp}dp-xhdpi")
        RuntimeEnvironment.setFontScale(device.fontScale)
        hilt.inject()
        disableHandRolledAnimations()
        if (screen != "overlay_tutorial") TutorialManager.completeTutorial(prefs)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()
        // Buttons found by an earlier test must not stand in for this one's.
        ContactActionTargets.cached = null
        when (screen) {
            "overlay_recent" -> runBlocking {
                // Newest first, as the overlay lists them; eight, so the Delete All chip shows too.
                RECENT_SEARCHES.forEachIndexed { i, query -> historyDao.insertSearch(HistoryEntity(query, 1_000_000L - i)) }
            }
            "overlay_contact" -> {
                val app = ApplicationProvider.getApplicationContext<Application>()
                FakeContactBook.install(app)
                FakeContactBook.installContactApps(app)
            }
            "card_web_suggestions" -> WebSuggestionSamples.installPicture()
            // Encrypted Backup and Manage Hidden Apps ask to unlock first (Backup draws nothing behind the prompt); a
            // phone with no screen lock or biometrics opens them straight away, so the page itself is audited.
            "backup", "hidden_apps" -> {
                val context = ApplicationProvider.getApplicationContext<Context>()
                shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
                Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
            }
        }

        val scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        try {
            compose.mainClock.autoAdvance = false
            scenario.onActivity { activity ->
                activity.setContent {
                    IntelligentSearchTheme(darkTheme = true) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) { Screen(screen) }
                    }
                }
            }
            compose.settle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            // A screen that draws nothing has nothing to find; it must not pass as clean.
            assertTrue("$screen drew nothing at ${device.name}", root.size.width > 0 && root.size.height > 0)
            val issues = LayoutAudit.inspect(root, density = 2f)
            report(issues)
            capture()
            val failures = issues.filter { LayoutAudit.isSerious(it, device.widthDp) && acceptance(it) == null }
            if (failures.isNotEmpty()) {
                fail(
                    "${failures.size} serious layout issue(s) on $screen at ${device.name} (all findings in ${File(outDir, "report.tsv")}):\n" +
                        failures.joinToString("\n") { "  ${it.kind}: ${it.label.oneLine()} @ ${it.bounds.px()}" }
                )
            }
        } finally {
            scenario.close()
            ResultThumbnails.clear()
        }
    }

    /** The [ACCEPTED] entry that excuses [issue] on this screen and device, if any. */
    private fun acceptance(issue: LayoutAudit.Issue): Accepted? = ACCEPTED.firstOrNull { it.covers(screen, device, issue) }

    @Composable
    private fun Screen(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val back = {}
        when (name) {
            "main" -> MainSettingsScreen(prefs, {}, back, context)
            "appearance" -> AppearanceScreen(prefs, {}, back)
            "custom_icons" -> CustomIconsScreen(prefs, back)
            "search_sources" -> SearchSourcesScreen({}, back)
            "search_behavior" -> SearchBehaviorScreen(prefs, back)
            "launch_portal" -> LaunchPortalScreen(prefs, back)
            "app_search" -> AppSearchScreen(prefs, {}, back)
            "search_pills" -> SearchPillsScreen(prefs, back)
            "web_search" -> WebSearchScreen(prefs, back)
            "contact_search" -> ContactSearchScreen(prefs, back)
            "file_search" -> FileSearchScreen(prefs, back)
            "widget" -> WidgetSettingsScreen(prefs, back)
            "hidden_apps" -> ManageHiddenAppsScreen(prefs, back)
            "backup" -> BackupRestoreScreen(prefs, back)
            "setup" -> SetupChecklistScreen(prefs, {}, back)
            // Cards fed with the golden tests' stand-in data (nothing is fetched), as the overlay's result list shows them.
            "card_web_suggestions", "card_weather", "card_definition", "card_ai_answer" ->
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                    when (name) {
                        "card_web_suggestions" -> WebSuggestionSamples.Rows()
                        "card_weather" -> InstantAnswerSamples.Cards("weather")
                        "card_definition" -> InstantAnswerSamples.Cards("definition")
                        else -> AiAnswerSamples.Cards()
                    }
                }
            else -> {
                val query = OVERLAY_QUERIES.getValue(name)
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
    }

    private val outDir: File get() = File(System.getProperty("layoutAuditDir") ?: "build/layout-audit")

    /** Appends every finding to the report; the verdict column says which fail the audit and which are accepted. */
    private fun report(issues: List<LayoutAudit.Issue>) {
        val file = File(outDir, "report.tsv").apply { parentFile?.mkdirs() }
        if (!file.exists()) file.writeText("screen\tdevice\tkind\tserious\tverdict\tlabel\tbounds_px\n")
        val lines = issues.joinToString("") { i ->
            val serious = LayoutAudit.isSerious(i, device.widthDp)
            val verdict = when {
                !serious -> "report"
                else -> acceptance(i)?.let { "accepted: ${it.reason}" } ?: "FAIL"
            }
            "$screen\t${device.name}\t${i.kind}\t$serious\t$verdict\t${i.label.oneLine().take(120)}\t${i.bounds.px()}\n"
        }
        if (lines.isEmpty()) file.appendText("$screen\t${device.name}\tNONE\tfalse\t\t\t\n") else file.appendText(lines)
    }

    private fun String.oneLine() = replace('\t', ' ').replace('\n', ' ')

    private fun Rect.px() = "${left.toInt()},${top.toInt()},${right.toInt()},${bottom.toInt()}"

    private fun capture() {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outDir, "png/${screen}__${device.name}.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private val PAGES = SettingsGoldenTest.PAGES

        /** Full overlay states and the query typed into each. */
        private val OVERLAY_QUERIES = mapOf(
            "overlay_tutorial" to "",
            "overlay_empty" to "",
            // Recent searches (seeded before launch) under an empty search bar.
            "overlay_recent" to "",
            "overlay_math" to "12*4+7",
            "overlay_timer" to "timer 5m",
            // OverlayGoldenTest's two made-up Robins: the Best Match with its Call, Message, Email and WhatsApp chips.
            "overlay_contact" to FakeContactBook.QUERY
        )

        /** The overlay's result cards that need data from the network, rendered from the golden tests' fixtures. */
        private val CARDS = listOf("card_web_suggestions", "card_weather", "card_definition", "card_ai_answer")

        private val RECENT_SEARCHES = listOf(
            "weather tomorrow",
            "how tall is everest",
            "translate good morning to spanish",
            "pizza near me",
            "timer 5m",
            "days between 2026-03-01 and 2026-06-30",
            "obama presidential center",
            "battery"
        )

        /** Every audited screen is xhdpi. */
        private const val DENSITY = 2f

        /**
         * A known finding that is accepted for now. [label] must match the finding's label, [screens] and [devices]
         * its screen and device names, and [sizeDp], when given, its width and height; every entry says why.
         */
        data class Accepted(
            val kind: LayoutAudit.Kind,
            val label: Regex,
            val reason: String,
            val screens: Regex = Regex(".*"),
            val devices: Regex = Regex(".*"),
            val sizeDp: Pair<Int, Int>? = null
        ) {
            init { require(reason.isNotBlank()) { "an accepted finding needs a reason" } }

            fun covers(screen: String, device: Device, issue: LayoutAudit.Issue) =
                issue.kind == kind && label.matches(issue.label) && screens.matches(screen) && devices.matches(device.name) &&
                    (sizeDp == null || sizeDp == Pair(Math.round(issue.bounds.width / DENSITY), Math.round(issue.bounds.height / DENSITY)))
        }

        private val ACCEPTED: List<Accepted> = listOf(
            Accepted(
                LayoutAudit.Kind.SMALL_TOUCH_TARGET, Regex("\\[.+]"), sizeDp = 52 to 32,
                reason = "GoogleMessagesSwitch is the Messages 52x32dp track with no M3 48dp reservation; Compose's input " +
                    "layer still widens its touch area to 48dp and its row's 14dp padding keeps that clear. Reserving 48dp " +
                    "makes every toggle row 2dp taller and changes the approved goldens, so it needs the owner's decision"
            ),
            Accepted(
                LayoutAudit.Kind.SMALL_TOUCH_TARGET, Regex("field \".*\""), screens = Regex("overlay_.*"),
                reason = "The search field is one 20sp line tall, but the whole search-bar pill around it (at least 48dp " +
                    "tall) is a clickable that focuses the field, so a tap anywhere on the bar reaches it"
            )
        )

        val DEVICES = listOf(
            Device("owner-527dp-85pct", 527, 1173, 0.85f),
            Device("small-320dp-100pct", 320, 640, 1.0f),
            Device("small-320dp-200pct", 320, 640, 2.0f),
            Device("phone-360dp-100pct", 360, 800, 1.0f),
            Device("phone-360dp-150pct", 360, 800, 1.5f),
            Device("phone-360dp-200pct", 360, 800, 2.0f),
            Device("phone-411dp-100pct", 411, 914, 1.0f),
            Device("phone-411dp-130pct", 411, 914, 1.3f),
            Device("phone-411dp-200pct", 411, 914, 2.0f),
            Device("phone-landscape-800x360", 800, 360, 1.0f),
            Device("fold-open-673x841", 673, 841, 1.0f),
            Device("fold-open-673x841-150pct", 673, 841, 1.5f),
            Device("tablet-800x1280", 800, 1280, 1.0f),
            Device("tablet-landscape-1280x800", 1280, 800, 1.0f),
            Device("tablet-landscape-1280x800-200pct", 1280, 800, 2.0f),
            Device("xr-window-1024x720", 1024, 720, 1.0f)
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> = (PAGES + OVERLAY_QUERIES.keys + CARDS).flatMap { s -> DEVICES.map { arrayOf<Any>(s, it) } }

    }
}
