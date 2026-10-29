package com.pixel.intelligentsearch.feature.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** The widget editor's hex and custom app dialogs accept only input they can act on, and store what they show. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetEditorDialogsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** Bumping this disposes the editor and opens it again, loading what was saved. */
    private var generation by mutableIntStateOf(0)

    private fun openEditor() {
        // The screen has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { key(generation) { WidgetSettingsScreen(prefs) {} } }
        }
        compose.settle()
    }

    /**
     * Moves the page on while a dialog, sheet or menu may open or change. Its window attaches on its first frame,
     * which Robolectric runs only once the looper's clock moves; [settle] checks for idle before moving it, and
     * would wait forever on a window composed in its last step. So here the Compose clock moves first and the
     * looper follows. Robolectric never draws a dialog's window either, and drawing is where Compose finishes a
     * layout its window's own pass left pending, so each step ends by laying out every window as a frame would.
     */
    private fun advance(steps: Int = 5) = repeat(steps) {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        composeWindows().forEach { it.measureAndLayoutForTest() }
    }

    /** The Compose content of every window on screen: the page and any dialog, sheet or menu over it. */
    private fun composeWindows(): List<RootForTest> {
        val windows = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = windows.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(windows) as List<*>
        return buildList {
            fun visit(view: View) {
                if (view is RootForTest) add(view)
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            views.filterIsInstance<View>().forEach(::visit)
        }
    }

    /** Taps the first control showing [label], the one higher up the page. */
    private fun tap(label: String) {
        compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
        advance()
    }

    private fun dialogButton(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    private fun typeInDialog(text: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(text)
        advance()
    }

    /** The hex code shown on the page (the Color Studio's colour bar). */
    private fun shownHex(): String {
        val hex = Regex("^#[0-9A-F]{6}$")
        val texts = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().let { root ->
            buildList {
                fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                    node.children.forEach(::visit)
                }
                visit(root)
            }
        }
        return texts.single { hex.matches(it) }
    }

    @Test
    fun colorStudioHexMustBeSixHexDigits() {
        openEditor()
        tap("Custom")
        tap("Color Studio")
        compose.onNodeWithContentDescription("Edit Hex").performClick()
        advance()

        typeInDialog("#12345")
        dialogButton("Save").assertIsNotEnabled()
        typeInDialog("12345G")
        dialogButton("Save").assertIsNotEnabled()
        typeInDialog(" 4285f4 ")
        dialogButton("Save").assertIsEnabled()
    }

    @Test
    fun customIconHexMustBeSixHexDigits() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .commit()
        openEditor()
        compose.onNodeWithContentDescription("Edit Hex").performClick()
        advance()

        typeInDialog("#12345")
        dialogButton("Save").assertIsNotEnabled()
        typeInDialog("#4285F4")
        dialogButton("Save").assertIsEnabled()
    }

    @Test
    fun hexEnteredInColorStudioIsTheColourThatIsSaved() {
        openEditor()
        tap("Custom")
        tap("Color Studio")
        compose.onNodeWithContentDescription("Edit Hex").performClick()
        advance()
        typeInDialog("#4285F4")
        dialogButton("Save").performClick()
        advance()
        val shownBeforeClosing = shownHex()

        compose.onNodeWithText("Save").performClick()
        advance()
        generation++
        advance()
        tap("Color Studio")

        assertEquals(shownBeforeClosing, shownHex())
    }

    @Test
    fun customAppMustBeAnInstalledApp() {
        val installed = ComponentName("com.example.installed", "com.example.installed.Main")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(installed)
            addIntentFilterForActivity(installed, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        openEditor()
        tap("Shortcuts")
        tap("Slot 1 · Google Lens")
        tap("Weather")
        tap("Custom App (APK)")

        typeInDialog("com.not.installed")
        dialogButton("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("No installed app with that package name").assertExists()

        typeInDialog("com.example.installed")
        dialogButton("Confirm").assertIsEnabled()
    }
}
