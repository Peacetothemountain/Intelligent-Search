package com.pixel.intelligentsearch.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.core.data.ResultShortcutStore
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.ResultActionMenuItems
import com.pixel.intelligentsearch.feature.search.ResultCommand
import com.pixel.intelligentsearch.feature.search.ResultMenuStyle
import com.pixel.intelligentsearch.feature.settings.NicknamesTriggersScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden images of an app result's long-press menu (every command an app can offer) and of the Nicknames & Triggers
 * page with two results named, light and dark. See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ResultShortcutsGoldenTest(private val dark: Boolean) {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val suffix get() = if (dark) "dark" else "light"

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun appMenu() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                    Surface(
                        shape = ResultMenuStyle.shape,
                        color = ResultMenuStyle.containerColor(),
                        border = ResultMenuStyle.border(),
                        shadowElevation = 3.dp
                    ) {
                        Box(Modifier.padding(vertical = 8.dp)) {
                            ResultActionMenuItems(
                                title = "Spotify",
                                groups = listOf(
                                    listOf(ResultCommand.UNPIN, ResultCommand.ADD_NICKNAME, ResultCommand.CHANGE_TRIGGER),
                                    listOf(ResultCommand.SPLIT_SCREEN),
                                    listOf(ResultCommand.APP_INFO, ResultCommand.HIDE, ResultCommand.UNINSTALL)
                                ),
                                onCommand = {}
                            )
                        }
                    }
                }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/result_menu_$suffix.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    @Test
    fun nicknamesAndTriggersPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ResultShortcutStore(SettingsManager(context)).update {
            it.withNickname("contact:john", "John Smith", "Dad")
                .withNickname("contact:john", "John Smith", "Father")
                .withTrigger("app:com.spotify.music/com.spotify.music.Main@0", "Spotify", "sp")
                .withNickname("app:com.spotify.music/com.spotify.music.Main@0", "Spotify", "music")
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) { NicknamesTriggersScreen(onBack = {}) }
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/settings/nicknames_triggers_$suffix.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
