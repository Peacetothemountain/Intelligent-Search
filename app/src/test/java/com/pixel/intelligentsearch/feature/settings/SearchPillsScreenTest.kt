package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Application Search pills page: system back in its app picker, and TalkBack's way to reorder pills. */
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

    /** Apps are listed by name as soon as the names are read; their icons follow row by row. */
    @Test
    fun thePickerListsAppsByName() {
        installLauncherApp("Alpha Notes", "com.example.alpha")
        show()
        openPicker()
        compose.onNodeWithText("Alpha Notes").assertExists()
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
}
