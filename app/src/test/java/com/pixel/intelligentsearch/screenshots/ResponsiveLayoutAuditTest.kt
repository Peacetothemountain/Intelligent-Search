package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
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
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every settings page and the main overlay states on phones from 320 dp to 527 dp at 85% to 200% text,
 * a phone in landscape, an unfolded foldable, tablets both ways and an Android XR sized window, and records
 * what [LayoutAudit] finds. Run with `./gradlew testDebugUnitTest --tests "*ResponsiveLayoutAuditTest*"
 * -PlayoutAudit`; the report is build/layout-audit/report.tsv, with a capture of every screen beside it.
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

    private val prefs: SharedPreferences
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun audit() {
        assumeTrue("run with -PlayoutAudit", System.getProperty("layoutAudit") == "true")
        val isOverlay = screen.startsWith("overlay_")
        // Settings pages scroll, so they get a tall window to lay out every row; the overlay fills the real screen.
        val heightDp = if (isOverlay) device.heightDp else maxOf(device.heightDp, 3000)
        RuntimeEnvironment.setQualifiers("w${device.widthDp}dp-h${heightDp}dp-xhdpi")
        RuntimeEnvironment.setFontScale(device.fontScale)
        hilt.inject()
        disableHandRolledAnimations()
        if (screen != "overlay_tutorial") TutorialManager.completeTutorial(prefs)
        prefs.edit().putBoolean(SettingsManager.SEARCH_WEB_SUGGESTIONS.name, false).commit()

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
            val issues = LayoutAudit.inspect(root, density = 2f)
            report(issues)
            capture()
        } finally {
            scenario.close()
        }
    }

    @Composable
    private fun Screen(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val back = {}
        when (name) {
            "main" -> MainSettingsScreen(prefs, {}, back, context)
            "appearance" -> AppearanceScreen(prefs, {}, back)
            "custom_icons" -> CustomIconsScreen(prefs, back)
            "search_sources" -> SearchSourcesScreen(prefs, {}, back)
            "search_behavior" -> SearchBehaviorScreen(prefs, back)
            "launch_portal" -> LaunchPortalScreen(prefs, back)
            "app_search" -> AppSearchScreen(prefs, {}, back)
            "search_pills" -> SearchPillsScreen(prefs, back, {})
            "web_search" -> WebSearchScreen(prefs, back)
            "contact_search" -> ContactSearchScreen(prefs, back)
            "file_search" -> FileSearchScreen(prefs, back)
            "widget" -> WidgetSettingsScreen(prefs, back)
            "hidden_apps" -> ManageHiddenAppsScreen(prefs, back)
            "backup" -> BackupRestoreScreen(prefs, back)
            "setup" -> SetupChecklistScreen(prefs, {}, back)
            else -> {
                val query = OVERLAY_QUERIES.getValue(name)
                val viewModel: SearchViewModel = hiltViewModel()
                if (query.isNotEmpty()) LaunchedEffect(Unit) { viewModel.onQueryChanged(query) }
                SearchOverlayScreen(onOpenSettings = {}, onLaunchApp = { _, _, _ -> }, viewModel = viewModel, isKeyboardDisabled = true)
            }
        }
    }

    private val outDir: File get() = File(System.getProperty("layoutAuditDir") ?: "build/layout-audit")

    private fun report(issues: List<LayoutAudit.Issue>) {
        val file = File(outDir, "report.tsv").apply { parentFile?.mkdirs() }
        if (!file.exists()) file.writeText("screen\tdevice\tkind\tserious\tlabel\tbounds_px\n")
        val lines = issues.joinToString("") { i ->
            val b = i.bounds
            "$screen\t${device.name}\t${i.kind}\t${i.kind.serious}\t${i.label.replace('\t', ' ').replace('\n', ' ').take(120)}\t" +
                "${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}\n"
        }
        if (lines.isEmpty()) file.appendText("$screen\t${device.name}\tNONE\tfalse\t\t\n") else file.appendText(lines)
    }

    private fun capture() {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(outDir, "png/${screen}__${device.name}.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private val PAGES = SettingsGoldenTest.PAGES

        private val OVERLAY_QUERIES = mapOf(
            "overlay_tutorial" to "",
            "overlay_empty" to "",
            "overlay_math" to "12*4+7",
            "overlay_timer" to "timer 5m"
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
        fun parameters(): List<Array<Any>> = (PAGES + OVERLAY_QUERIES.keys).flatMap { s -> DEVICES.map { arrayOf<Any>(s, it) } }
    }
}
