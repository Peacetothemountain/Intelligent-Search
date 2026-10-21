package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** Each icon pack's switch is named after the pack, and packs can be reordered without dragging. */
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

    @Test
    fun eachSwitchIsNamedAfterItsPack() {
        val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        assertEquals(3, switches.size)
        val names = switches.map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
        assertEquals(listOf("System Default", "Pack A", "Pack B"), names)
    }

}
