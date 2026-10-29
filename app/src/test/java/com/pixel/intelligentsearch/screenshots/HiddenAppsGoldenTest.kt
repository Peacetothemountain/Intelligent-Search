package com.pixel.intelligentsearch.screenshots

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.ManageHiddenAppsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/** Manage Hidden Apps once unlocked, with a change not yet saved: the title beside Back with Enable All after it, Reset and Save at the top right. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class HiddenAppsGoldenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        disableHandRolledAnimations()
        prefs.edit().clear().commit()
        // No screen lock or biometrics on this device, so the page opens without a prompt.
        Shadow.extract<ShadowBiometricManager>(context.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
    }

    @Test fun unsavedChangeDark() = capture(dark = true)

    @Test fun unsavedChangeLight() = capture(dark = false)

    private fun capture(dark: Boolean) {
        compose.mainClock.autoAdvance = false
        val apps = listOf("Alpha Notes", "Bravo Maps", "Charlie Camera").mapIndexed { i, name ->
            AppItem(name, "com.example.${name.substringBefore(' ').lowercase()}", ColorDrawable(listOf(Color.RED, Color.GREEN, Color.BLUE)[i]))
        }
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    ManageHiddenAppsScreen(prefs, onBack = {}, loadApps = { apps })
                }
            }
        }
        compose.settle()
        compose.onNodeWithText("Bravo Maps").performClick()
        compose.settle(steps = 5)
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/settings/hidden_apps_unlocked_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }
}
