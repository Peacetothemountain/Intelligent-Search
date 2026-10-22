package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.answers.TranslationStatus
import com.pixel.intelligentsearch.core.data.SettingsManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The overlay's view model asks the instant-answer engine about each query and shows its answer for that query only.
 * Every switch is at its default here, so nothing reaches the network: definitions and forecasts keep their
 * tap-through cards, and translations offer Google Translate.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LiveAnswerViewModelTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<HiltTestActivity>
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        hilt.inject()
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
    }

    @After
    fun tearDown() = scenario.close()

    private fun await(what: String, condition: (SearchUiState) -> Boolean): SearchUiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            val state = viewModel.uiState.value
            if (condition(state)) return state
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for $what; state=${viewModel.uiState.value}")
    }

    @Test
    fun aTranslationQueryGetsItsCardAndTheNextKeystrokeClearsIt() {
        viewModel.onQueryChanged("translate hello to spanish")
        val answer = await("translation") { it.liveAnswer != null }.liveAnswer as LiveAnswer.Translation
        assertEquals("translate hello to spanish", answer.query)
        assertEquals(TranslationStatus.Off, answer.status)

        viewModel.onQueryChanged("translate hello to spanis")
        assertTrue(viewModel.uiState.value.liveAnswer?.query != "translate hello to spanish")
        viewModel.onQueryChanged("")
        assertNull(await("cleared") { it.query.isEmpty() }.liveAnswer)
    }

    @Test
    fun screenTimeIsAnsweredFromThePhone() {
        viewModel.onQueryChanged("screen time today")
        val answer = await("screen time") { it.liveAnswer != null }.liveAnswer
        assertTrue("was $answer", answer is LiveAnswer.ScreenTime && answer.query == "screen time today")
    }

    @Test
    fun withDictionaryAnswersOffADefinitionKeepsItsTapThroughCard() {
        viewModel.onQueryChanged("define serendipity")
        val state = await("dictionary card") { it.instantAnswer != null }
        assertEquals("dictionary", state.instantAnswer?.iconType)
        assertEquals("Serendipity", state.instantAnswer?.title)
        assertNull(state.liveAnswer)
    }

    @Test
    fun withWeatherAnswersOffTheForecastKeepsItsTapThroughCard() {
        viewModel.onQueryChanged("weather tomorrow in johnson city")
        val state = await("weather card") { it.instantAnswer != null }
        assertEquals("weather", state.instantAnswer?.iconType)
        assertEquals("Weather in Johnson City", state.instantAnswer?.title)
        assertEquals("Tap for tomorrow's forecast", state.instantAnswer?.subtitle)
        assertNull(state.liveAnswer)
    }

    @Test
    fun aBareWordQuestionHasNoTapThroughCard() {
        viewModel.onQueryChanged("serendipity?")
        var done = false
        viewModel.viewModelScope.launch { viewModel.awaitSearch(); done = true }
        val state = await("local results") { done }
        assertNull(state.instantAnswer)
        assertNull(state.liveAnswer)
    }
}
