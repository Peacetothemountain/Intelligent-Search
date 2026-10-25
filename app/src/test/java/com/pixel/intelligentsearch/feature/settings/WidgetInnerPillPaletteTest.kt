package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.DEFAULT_LOCKED_PILL_COLOR
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Color Studio's Inner Pill & Circle palette: shown only for the Material design, under the search bar's own palette.
 * Its switch forces the pill and circle to one color, the default #121212 until another is picked or typed.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class WidgetInnerPillPaletteTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    @Before fun inject() = hilt.inject()

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun openEditor() {
        // The screen has never-ending animations, so the clock is driven by hand instead of waiting for idle.
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()
    }

    /** The Material design on the widget with its Custom subtheme, which has the Color Studio tab. */
    private fun materialCustom() = prefs.edit()
        .putString(SettingsManager.WIDGET_THEME_STYLE.name, "Material You (Minimal)")
        .putBoolean("widget_material_design_saved", true)
        .putString("widget_material_subtheme", "Custom")

    /**
     * Moves the page on while a dialog may open or change (see WidgetEditorDialogsTest): the Compose clock first, the
     * looper after, then every window laid out as a frame would.
     */
    private fun advance(steps: Int = 5) = repeat(steps) {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        composeWindows().forEach { it.measureAndLayoutForTest() }
    }

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

    private fun typeHex(hex: String) {
        compose.onNodeWithContentDescription("Edit Inner Pill & Circle Hex").performClick()
        advance()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(hex)
        advance()
        compose.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
        advance()
    }

    @Test
    fun thePaletteIsOnlyForTheMaterialDesign() {
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_subtheme", "Custom")
            .putBoolean("widget_material_design_saved", true)
            .putString("widget_material_subtheme", "Custom")
            .commit()
        openEditor()
        tap("Color Studio")
        compose.onNodeWithText("Search Bar Color Palette").assertExists()
        compose.onNodeWithText("Inner Pill & Circle Color Palette").assertDoesNotExist()
        compose.onNodeWithText("Inner Pill & Circle State").assertDoesNotExist()

        tap("Material Design")
        tap("Color Studio")
        compose.onNodeWithText("Search Bar Color Palette").assertExists()
        compose.onNodeWithText("Inner Pill & Circle Color Palette").assertExists()
        compose.onNodeWithText("Inner Pill & Circle State").assertExists()
    }

    @Test
    fun theDefaultIsChosenAtFirst() {
        materialCustom().commit()
        openEditor()
        tap("Color Studio")

        compose.onNodeWithText("Default").assertIsSelected()
        // The hex row shows the color, and the default is #121212.
        assertEquals(2, compose.onAllNodesWithText("#121212").fetchSemanticsNodes().size)
    }

    @Test
    fun aTypedHexIsTheColorSavedAndDefaultPutsItBack() {
        materialCustom().commit()
        openEditor()
        tap("Color Studio")

        typeHex("#F5E6C8")
        compose.onNodeWithText("Default").assertIsNotSelected()
        compose.onNodeWithText("#F5E6C8").assertExists()
        compose.onNodeWithText("Save").performClick()
        advance()
        assertEquals(0xFFF5E6C8.toInt(), prefs.getInt("widget_material_lock_pill_color", 0))

        tap("Default")
        compose.onNodeWithText("Default").assertIsSelected()
        compose.onNodeWithText("Save").performClick()
        advance()
        assertEquals(DEFAULT_LOCKED_PILL_COLOR, prefs.getInt("widget_material_lock_pill_color", 0))
    }

    /** With the switch off, the pill and circle follow the theme, so there is no color to pick. */
    @Test
    fun theColorsShowOnlyWhileTheSwitchIsOn() {
        materialCustom().commit()
        openEditor()
        tap("Color Studio")
        compose.onNodeWithText("Default").assertExists()

        tap("Inner Pill & Circle State")
        compose.onNodeWithText("Default").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        advance()
        assertFalse(prefs.getBoolean("widget_material_lock_black", true))
    }
}
