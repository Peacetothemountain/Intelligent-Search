package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.SearchPillsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Application Search's app picker: its apps in the same container and rows as Manage Hidden Apps, without the switch.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class AppPickerGoldenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs: SharedPreferences get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
        prefs.edit().clear().commit()
        listOf("Alpha Notes" to "com.example.alpha", "Bravo Maps" to "com.example.bravo", "Charlie Camera" to "com.example.charlie")
            .forEach { (label, packageName) -> installLauncherApp(label, packageName) }
    }

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
    fun picker() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { SearchPillsScreen(prefs, onBack = {}) }
            }
        }
        compose.settle(steps = 5)
        compose.onNodeWithContentDescription("Add Pill").performClick()
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/settings/search_pills_picker_dark.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }
}
