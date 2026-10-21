package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
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

/**
 * Icon packs are a single choice: each pack (System Default being none) is a radio option named after it, only one is
 * selected, and packs can be reordered without dragging.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class CustomIconsScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun installIconPack(packageName: String, label: String) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Theme"
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
            nonLocalizedLabel = label
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent("org.adw.launcher.THEMES"), resolveInfo)
    }

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
        disableHandRolledAnimations()
        installIconPack("com.example.packa", "Pack A")
        installIconPack("com.example.packb", "Pack B")
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { CustomIconsScreen(prefs) {} } }
        compose.settle()
    }

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /** The packs' radio options, top to bottom. */
    private fun radios() = compose.onAllNodes(isRadio).fetchSemanticsNodes()

    private fun selectedPacks() = radios()
        .filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        .map { it.config[SemanticsProperties.Text].first().text }

    @Test
    fun eachPackIsARadioOptionNamedAfterIt() {
        val radios = radios()
        assertEquals(3, radios.size)
        val names = radios.map { it.config[SemanticsProperties.Text].first().text }
        assertEquals(listOf("System Default", "Pack A", "Pack B"), names)
        // No switches are left: packs are a single choice.
        assertEquals(0, compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        // Every option says whether it is selected, and they form one group.
        radios.forEach { assertEquals(true, it.config.contains(SemanticsProperties.Selected)) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
    }

    @Test
    fun systemDefaultIsSelectedUntilAPackIsPicked() {
        assertEquals(listOf("System Default"), selectedPacks())
    }

    @Test
    fun pickingAPackSelectsOnlyThatPack() {
        compose.onNodeWithText("Pack A").performClick()
        compose.settle(steps = 5)
        assertEquals("com.example.packa", prefs[SettingsManager.ACTIVE_ICON_PACK])
        assertEquals(listOf("Pack A"), selectedPacks())

        // Changing from one pack to another asks first.
        compose.onNodeWithText("Pack B").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Save").performClick()
        compose.settle(steps = 5)
        assertEquals("com.example.packb", prefs[SettingsManager.ACTIVE_ICON_PACK])
        assertEquals(listOf("Pack B"), selectedPacks())
    }

    @Test
    fun draggingAHandleStillReordersWithoutPickingThePack() {
        // Pack A's handle is the second of three; dragging it a full row down swaps it with Pack B.
        val handle = compose.onAllNodes(hasContentDescription("Drag to reorder"), useUnmergedTree = true)[1]
        val rowHeight = compose.onNodeWithText("Pack A").fetchSemanticsNode().size.height.toFloat()
        handle.performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, rowHeight * 0.15f)) }
            up()
        }
        compose.settle(steps = 5)
        assertEquals("system_default,com.example.packb,com.example.packa", prefs[SettingsManager.CUSTOM_ICON_PILLS])
        // The drag didn't count as a tap on the row.
        assertEquals(listOf("System Default"), selectedPacks())
    }

    @Test
    fun systemDefaultTurnsThePackOff() {
        prefs.edit().putBoolean(SettingsManager.NEVER_SHOW_ICON_PACK_WARNING.name, true).commit()
        compose.onNodeWithText("Pack A").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("System Default").performClick()
        compose.settle(steps = 5)
        assertEquals("system_default", prefs[SettingsManager.ACTIVE_ICON_PACK])
        assertEquals(listOf("System Default"), selectedPacks())
    }

    @Test
    fun aPackCanBeMovedUpWithoutDragging() {
        compose.onNodeWithText("Pack A").performCustomAccessibilityActionWithLabel("Move up")
        compose.settle(steps = 5)
        assertEquals("com.example.packa,system_default,com.example.packb", prefs[SettingsManager.CUSTOM_ICON_PILLS])
    }

    @Test
    fun aPackCanBeMovedDownWithoutDragging() {
        compose.onNodeWithText("Pack A").performCustomAccessibilityActionWithLabel("Move down")
        compose.settle(steps = 5)
        assertEquals("system_default,com.example.packb,com.example.packa", prefs[SettingsManager.CUSTOM_ICON_PILLS])
    }
}
