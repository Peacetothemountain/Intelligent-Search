package com.pixel.intelligentsearch.screenshots

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.FileTypeSources
import com.pixel.intelligentsearch.feature.search.SearchOverlayScreen
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Golden images of the search overlay for "pdf invoice": the file results limited to PDFs, with the removable
 * "PDFs Only" chip above them. The files are stand-ins ([FileTypeSources]). See [SettingsGoldenTest] for recording.
 */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class FileTypeFilterGoldenTest(private val dark: Boolean) {

    @Module
    @InstallIn(SingletonComponent::class)
    object FileSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FileTypeSources()
    }

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>(confinedEffects())

    @Inject lateinit var coordinator: UnifiedSearchCoordinator

    @Before fun inject() {
        hilt.inject()
        disableHandRolledAnimations()
    }

    @Test
    fun filteredFiles() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        com.pixel.intelligentsearch.feature.settings.TutorialManager.completeTutorial(prefs)
        // Live web suggestions come from the network at random times; goldens must not depend on them.
        prefs.edit().put(SettingsManager.SEARCH_WEB_SUGGESTIONS, false).put(SettingsManager.SEARCH_FILES, true).commit()
        coordinator.replaceFiles(FileTypeSources.FILES)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IntelligentSearchTheme(darkTheme = dark) {
                val viewModel: SearchViewModel = hiltViewModel()
                LaunchedEffect(Unit) { viewModel.onQueryChanged("pdf invoice") }
                SearchOverlayScreen(
                    onOpenSettings = {},
                    onLaunchApp = { _, _, _, _ -> },
                    viewModel = viewModel,
                    isKeyboardDisabled = true
                )
            }
        }
        compose.settle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/overlay/file_type_filter_${if (dark) "dark" else "light"}.png",
            roborazziOptions = SettingsGoldenTest.GOLDEN_OPTIONS
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun parameters(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
