package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemVibrator
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * Every tap gives exactly one haptic, whichever way a control was written: a reset button that adds its own
 * click haptic must not also get the wrapper's, and Back arrows get the wrapper's predictive-back haptic.
 * The test vibrator supports every primitive, so each haptic ends in one vibration or one view haptic; both are
 * counted.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(
    application = HiltTestApplication::class,
    qualifiers = "w412dp-h2600dp-xhdpi",
    shadows = [SettingsHapticsTest.CountingVibrator::class, SettingsHapticsTest.CountingViewRoot::class]
)
class SettingsHapticsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.VIBRATION, true).commit()
        shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator).setSupportedPrimitives((1..8).toList())
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
        haptics = 0
    }

    @Test
    fun openingADropdownGivesOneHaptic() {
        show { AppearanceScreen(prefs, {}, {}) }
        compose.onNodeWithText("App Theme").performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
    }

    @Test
    fun aPriorityWeightResetGivesOneHaptic() {
        show { ContactSearchScreen(prefs) {} }
        // The priority weight block is the last one on the page, so its reset button is the last reset.
        compose.onAllNodesWithContentDescription("Reset", substring = true).onLast().performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
    }

    @Test
    fun aSliderResetGivesOneHaptic() {
        show { AppearanceScreen(prefs, {}, {}) }
        compose.onAllNodesWithContentDescription("Reset", substring = true).onLast().performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
    }

    /** The widget editor, whose animations never end, so its clock is driven by hand instead of waiting for idle. */
    private fun showWidgetEditor() {
        disableHandRolledAnimations()
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { WidgetSettingsScreen(prefs) {} } }
        compose.settle()
        haptics = 0
    }

    /**
     * Taps the first control showing [label] and returns the haptics played as the finger lifts, which is when the
     * click happens. Touching down gives its own light tick (outside a scrolling page, where a press shows at once),
     * so that is left out.
     */
    private fun clickHapticsOf(label: String): Int {
        val control = compose.onAllNodesWithText(label, useUnmergedTree = true)[0]
        control.performTouchInput { down(center) }
        compose.settle(steps = 1)
        haptics = 0
        control.performTouchInput { up() }
        compose.settle(steps = 5)
        return haptics
    }

    @Test
    fun aWidgetEditorTabGivesOneHaptic() {
        // The Custom subtheme adds the Color Studio tab.
        prefs.edit().putBoolean("widget_system_design_saved", true).putString("widget_system_subtheme", "Custom").commit()
        showWidgetEditor()
        assertEquals(1, clickHapticsOf("Color Studio"))
    }

    @Test
    fun theWidgetDesignSwitcherGivesOneHaptic() {
        showWidgetEditor()
        assertEquals(1, clickHapticsOf("Material Design"))
    }

    @Test
    fun widgetEditorOptionsGiveOneHaptic() {
        showWidgetEditor()
        // A G icon style.
        assertEquals(1, clickHapticsOf("Accented"))
        // An action icon, which only the Material design has.
        clickHapticsOf("Material Design")
        assertEquals(1, clickHapticsOf("Playing"))
    }

    @Test
    fun theWidgetElementPickerGivesOneHaptic() {
        // Custom icon colours, set one element at a time.
        prefs.edit()
            .putBoolean("widget_system_design_saved", true)
            .putString("widget_system_material_g_icon", "Custom G Icon")
            .putBoolean("widget_system_custom_sync_colors", false)
            .commit()
        showWidgetEditor()
        assertEquals(1, clickHapticsOf("G Icon"))
    }

    /** A settings row, which taps through [bouncyClickable] and so also shows a ripple. */
    private fun showRow(onLongClick: (() -> Unit)? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) {
                SettingsRow(title = "Row", subtitle = "Subtitle", icon = null, onClick = {}, onLongClick = onLongClick, showDivider = false)
            }
        }
        compose.settle(steps = 5)
    }

    @Test
    fun aRowTapWithItsRippleGivesOneHaptic() {
        showRow()
        assertEquals(1, clickHapticsOf("Row"))
    }

    @Test
    fun aRowLongPressGivesOneHaptic() {
        var longPressed = false
        showRow(onLongClick = { longPressed = true })
        val row = compose.onAllNodesWithText("Row", useUnmergedTree = true)[0]
        row.performTouchInput { down(center) }
        compose.settle(steps = 1)
        // The touch-down tick is the press's own; only the long press's haptics are counted.
        haptics = 0
        compose.settle(steps = 10)
        row.performTouchInput { up() }
        compose.settle(steps = 5)
        assertEquals(true, longPressed)
        // The system's own long-press haptic used to play as well as the app's.
        assertEquals(1, haptics)
    }

    @Test
    fun backGivesThePredictiveBackHaptic() {
        show { AppSearchScreen(prefs, {}, {}) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        assertEquals(1, haptics)
    }

    /** Counts every vibration the app asks the vibrator for. */
    @Implements(className = "android.os.SystemVibrator", isInAndroidSdk = false)
    class CountingVibrator : ShadowSystemVibrator() {
        @Implementation(minSdk = 31)
        protected fun vibrate(uid: Int, opPkg: String?, effect: VibrationEffect, reason: String?, attributes: VibrationAttributes) {
            haptics++
            super.vibrate(uid, opPkg, effect as Any, reason, attributes as Any)
        }
    }

    /** Counts haptic feedback performed through a view, which the window plays instead of the app's vibrator. */
    @Implements(className = "android.view.ViewRootImpl", isInAndroidSdk = false)
    class CountingViewRoot : ShadowViewRootImpl() {
        @Implementation
        protected fun performHapticFeedback(effectId: Int, always: Boolean): Boolean {
            haptics++
            return true
        }
    }

    companion object {
        @JvmStatic var haptics = 0
    }
}
