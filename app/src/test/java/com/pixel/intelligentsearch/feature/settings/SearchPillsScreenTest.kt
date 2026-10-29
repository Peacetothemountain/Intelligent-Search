package com.pixel.intelligentsearch.feature.settings

import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.disableHandRolledAnimations
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Application Search pills page: system back in its app picker, TalkBack's way to reorder and remove pills, and
 * pills swiped away: one still flying off is saved however the list goes away, and Reset puts it back.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class SearchPillsScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private var backCalls = 0

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { SearchPillsScreen(prefs, onBack = { backCalls++ }) } }
        compose.settle(steps = 5)
    }

    private fun openPicker() {
        compose.onNodeWithContentDescription("Add Pill").performClick()
        compose.settle(steps = 5)
        compose.onNodeWithText("Add Applications").assertExists()
    }

    private fun systemBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle(steps = 5)
    }

    /** One launcher app for the picker to list. */
    private fun installLauncherApp(label: String, packageName: String) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowOf(context.packageManager).addResolveInfoForIntent(launcher, ResolveInfo().apply {
            nonLocalizedLabel = label
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.MainActivity"
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        })
    }

    @Test
    fun systemBackClosesTheAppPicker() {
        show()
        openPicker()
        systemBack()
        compose.onNodeWithText("Application Search").assertExists()
        assertEquals(0, backCalls)
    }

    @Test
    fun systemBackLeavesMultiSelectBeforeThePicker() {
        installLauncherApp("Alpha Notes", "com.example.alpha")
        show()
        openPicker()
        compose.onNodeWithText("Alpha Notes").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.settle(steps = 5)
        compose.onNodeWithText("1 Selected").assertExists()

        systemBack()
        compose.onNodeWithText("Add Applications").assertExists()
        assertEquals(0, backCalls)
    }

    /**
     * The picker's search waits behind the magnifier, as Manage Hidden Apps' does, and opens in a pill at the bottom:
     * typing narrows the list, and system back closes the search before it leaves the picker.
     */
    @Test
    fun thePickersSearchOpensAtTheBottomAndBackClosesItFirst() {
        installLauncherApp("Alpha Notes", "com.example.alpha")
        installLauncherApp("Bravo Maps", "com.example.bravo")
        show()
        openPicker()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.settle(steps = 5)
        val title = compose.onNodeWithText("Add Applications").getBoundsInRoot()
        val field = compose.onNode(hasSetTextAction()).assertIsFocused().getBoundsInRoot()
        assertTrue("the pill is at the bottom of the page, far below the title", field.top > title.bottom + 1000.dp)
        compose.onNode(hasSetTextAction()).performTextInput("brav")
        compose.settle(steps = 5)
        compose.onNodeWithText("Bravo Maps").assertExists()
        compose.onNodeWithText("Alpha Notes").assertDoesNotExist()

        systemBack()
        compose.onNodeWithText("Add Applications").assertExists()
        compose.onNodeWithText("Alpha Notes").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(0, backCalls)
    }

    /** Apps are listed by name as soon as the names are read; their icons follow row by row. */
    @Test
    fun thePickerListsAppsByName() {
        installLauncherApp("Alpha Notes", "com.example.alpha")
        show()
        openPicker()
        compose.onNodeWithText("Alpha Notes").assertExists()
    }

    @Test
    fun anUnlabeledPillShowsItsPackageNameOnce() {
        // Neither app is installed, so each pill is titled by its package name, with no copy of it underneath.
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        show()
        val texts = compose.onNode(hasText("a.​b")).fetchSemanticsNode().config[SemanticsProperties.Text]
        assertEquals(listOf("a.​b"), texts.map { it.text })
    }

    @Test
    fun talkBackCanMoveAPillUp() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        show()
        // Package names are shown breakable at their dots.
        compose.onNode(hasText("c.\u200Bd")).performCustomAccessibilityActionWithLabel("Move up")
        compose.settle(steps = 5)
        assertEquals(listOf("c.d", "a.b"), SearchPillProfiles(prefs).pills())
    }

    @Test
    fun talkBackOffersOnlyTheMovesThatFit() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        show()
        fun actions(text: String) = compose.onNode(hasText(text)).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Remove", "Move down"), actions("a.\u200Bb"))
        assertEquals(listOf("Remove", "Move up"), actions("c.\u200Bd"))
    }

    @Test
    fun talkBackCanRemoveAPill() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        show()
        compose.onNode(hasText("a.\u200Bb")).performCustomAccessibilityActionWithLabel("Remove")
        compose.settle(steps = 5)
        assertEquals(listOf("c.d"), SearchPillProfiles(prefs).pills())
    }

    // ---- With animations on, as on a phone: a removed pill flies off before it is saved ----

    private var shown by mutableStateOf(true)

    private fun showAnimated() {
        // The scale is static, and @Before (and screenshot tests run earlier in this JVM) turn animations off.
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = false) { if (shown) SearchPillsScreen(prefs, onBack = { backCalls++ }) }
        }
        compose.settle(steps = 5)
    }

    /** Publishes the test thread's writes (with auto-advance off nothing else would) and runs [n] frames. */
    private fun frames(n: Int) {
        Snapshot.sendApplyNotifications()
        repeat(n) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun pill(packageName: String) = compose.onNode(hasText(packageName.replace(".", ".\u200B")))

    private fun pillLeft(packageName: String) = pill(packageName).getUnclippedBoundsInRoot().left.value

    /** Asks TalkBack's Remove of [packageName] and lets a few frames of its flight run. */
    private fun removeMidFlight(packageName: String) {
        pill(packageName).performCustomAccessibilityActionWithLabel("Remove")
        frames(3)
        assertTrue("$packageName is still flying off, not yet saved", packageName in SearchPillProfiles(prefs).pills())
    }

    @Test
    fun talkBackRemoveFliesThePillOffThenRemovesIt() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        showAnimated()
        val restingLeft = pillLeft("a.b")
        removeMidFlight("a.b")
        assertTrue("on its way to the wall", pillLeft("a.b") < restingLeft)

        compose.settle(steps = 10)
        assertEquals(listOf("c.d"), SearchPillProfiles(prefs).pills())
        pill("a.b").assertDoesNotExist()
    }

    @Test
    fun aRemovalSurvivesClosingThePageMidFlight() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        showAnimated()
        removeMidFlight("a.b")

        shown = false
        frames(2)
        assertEquals(listOf("c.d"), SearchPillProfiles(prefs).pills())
    }

    @Test
    fun aRemovalSurvivesOpeningThePickerMidFlight() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        showAnimated()
        removeMidFlight("a.b")

        openPicker()
        assertEquals(listOf("c.d"), SearchPillProfiles(prefs).pills())
        systemBack()
        pill("a.b").assertDoesNotExist()
    }

    /** Switching profile mid-flight removes the pill from the profile it was swiped on, not the one switched to. */
    @Test
    fun aRemovalMidFlightStaysWithItsProfile() {
        prefs.edit()
            .put(SettingsManager.SEARCH_PILLS, "a.b,c.d")
            .putString("app_search_profile_2", "a.b,e.f")
            .commit()
        showAnimated()
        removeMidFlight("a.b")

        compose.onNodeWithText("Profile 2").performClick()
        compose.settle(steps = 10)
        assertEquals(listOf("a.b", "e.f"), SearchPillProfiles(prefs).pills())
        assertEquals("c.d", prefs.getString("app_search_profile_1", null))
    }

    /** Reset puts back the saved list, so a pill still flying off stays. */
    @Test
    fun resetDuringARemovalKeepsThePill() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b,c.d").commit()
        SearchPillProfiles(prefs).saveState(listOf("a.b", "c.d"))
        showAnimated()
        removeMidFlight("a.b")

        compose.onNodeWithContentDescription("Reset to Saved State").performClick()
        compose.settle(steps = 10)
        assertEquals(listOf("a.b", "c.d"), SearchPillProfiles(prefs).pills())
        pill("a.b").assertExists()
    }

    /** Reset slides back in only the pills that were missing; the ones still there stay put. */
    @Test
    fun resetSlidesOnlyTheMissingPillsInFromTheRight() {
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "c.d").commit()
        SearchPillProfiles(prefs).saveState(listOf("a.b", "c.d"))
        showAnimated()
        val restingLeft = pillLeft("c.d")

        compose.onNodeWithContentDescription("Reset to Saved State").performClick()
        frames(4)
        assertTrue("the swiped-away pill arrives from the right", pillLeft("a.b") > restingLeft)
        assertEquals("the pill that was there stays put", restingLeft, pillLeft("c.d"), 0.5f)

        compose.settle(steps = 10)
        assertEquals(restingLeft, pillLeft("a.b"), 0.5f)
        assertEquals(restingLeft, pillLeft("c.d"), 0.5f)
    }

    @Test
    fun anAddedAppSlidesIn() {
        installLauncherApp("Alpha Notes", "com.example.alpha")
        prefs.edit().put(SettingsManager.SEARCH_PILLS, "a.b").commit()
        showAnimated()
        openPicker()

        compose.onNodeWithText("Alpha Notes").performClick()
        frames(4)
        val restingLeft = pillLeft("a.b")
        assertTrue("arriving from the right", pillLeft("com.example.alpha") > restingLeft)

        compose.settle(steps = 10)
        assertEquals(listOf("a.b", "com.example.alpha"), SearchPillProfiles(prefs).pills())
        assertEquals(restingLeft, pillLeft("com.example.alpha"), 0.5f)
    }
}
