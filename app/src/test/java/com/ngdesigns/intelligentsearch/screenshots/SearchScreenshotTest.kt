package com.ngdesigns.intelligentsearch.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ngdesigns.intelligentsearch.Fixtures
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.settings.Look
import com.ngdesigns.intelligentsearch.data.settings.ThemeMode
import com.ngdesigns.intelligentsearch.engine.Ranked
import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import com.ngdesigns.intelligentsearch.engine.web.WebShortcuts
import com.ngdesigns.intelligentsearch.ui.search.AnswerActions
import com.ngdesigns.intelligentsearch.ui.search.ResultActions
import com.ngdesigns.intelligentsearch.ui.search.SearchScreen
import com.ngdesigns.intelligentsearch.ui.search.SearchUiState
import com.ngdesigns.intelligentsearch.ui.theme.SearchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The search screen in its main states, light and dark, rendered headlessly at a Pixel-sized window. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class SearchScreenshotTest(private val scene: String, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    private object NoResults : ResultActions {
        override fun open(item: Ranked) = Unit
        override fun insert(text: String) = Unit
        override fun openApp(app: AppEntry) = Unit
        override fun call(item: Ranked) = Unit
        override fun message(item: Ranked) = Unit
        override fun appInfo(item: Ranked) = Unit
        override fun hide(item: Ranked) = Unit
        override fun uninstall(item: Ranked) = Unit
        override fun forget(query: String) = Unit
        override fun searchWeb(text: String) = Unit
    }

    private object NoAnswers : AnswerActions {
        override fun copy(text: String) = Unit
        override fun openCalculator() = Unit
        override fun run(action: Action) = Unit
        override fun toggle(toggle: Toggle, on: Boolean) = Unit
        override fun shortcut(use: WebShortcuts.Use) = Unit
        override fun address(url: String) = Unit
    }

    private fun stateFor(scene: String): SearchUiState {
        val settings = Fixtures.settings.copy(look = Look(theme = if (dark) ThemeMode.Dark else ThemeMode.Light, showWallpaper = false))
        return when (scene) {
            "home" -> Fixtures.state("", settings)
            "typing" -> Fixtures.state("cal", settings, web = listOf("calendar 2027", "calories in an avocado"))
            "calculator" -> Fixtures.state("(1024 + 35%) - 98.65", settings)
            "timer" -> Fixtures.state("timer 12m30s", settings)
            "torch" -> Fixtures.state("torch", settings)
            "person" -> Fixtures.state("ada", settings)
            else -> error(scene)
        }
    }

    @Test fun capture() {
        val ui = stateFor(scene)
        compose.setContent {
            SearchTheme(ui.settings.look) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    SearchScreen(
                        ui = ui, field = rememberTextFieldState(ui.query), focus = remember { FocusRequester() }, torchOn = false,
                        snackbar = remember { SnackbarHostState() }, createOpen = false, onCreateOpen = {}, onStarter = {},
                        onScope = {}, onSubmit = {}, onVoice = {}, onLens = {}, results = NoResults, answers = NoAnswers,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/search/${scene}_${if (dark) "dark" else "light"}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun scenes() = listOf("home", "typing", "calculator", "timer", "torch", "person").flatMap { listOf(arrayOf<Any>(it, false), arrayOf<Any>(it, true)) }
    }
}
