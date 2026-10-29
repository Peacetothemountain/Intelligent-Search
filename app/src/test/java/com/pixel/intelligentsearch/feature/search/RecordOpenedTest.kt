package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.ProfileType
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.ResultKeys
import com.pixel.intelligentsearch.core.search.SelectionMemory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Opening a result from search is remembered for ranking, with what was typed only while searches are remembered,
 * and clearing the search history forgets what was typed.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RecordOpenedTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var memory: SelectionMemory

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val calculator = AppItem(name = "Calculator", packageName = "com.google.android.calculator", icon = ColorDrawable(), activityName = "Main")
    private val mom = ContactItem("Mom", "555 0100", "content://com.android.contacts/contacts/lookup/mom/1")

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun viewModel(): SearchViewModel {
        lateinit var viewModel: SearchViewModel
        ActivityScenario.launch(HiltTestActivity::class.java).onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
        return viewModel
    }

    /** Waits for the background write [recordOpened] starts. */
    private fun eventually(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
        assertTrue("timed out", check())
    }

    @Test
    fun anOpenedResultIsRememberedWithWhatWasTyped() {
        val viewModel = viewModel()
        viewModel.onQueryChanged("ca")
        viewModel.recordOpened(calculator)
        eventually { memory.learnedWeight(ResultKeys.app(calculator), "ca") > 0f }
        assertEquals(1f, memory.learnedWeight(ResultKeys.app(calculator), "ca"), 0.01f)
    }

    @Test
    fun withPreviousSearchesOffOnlyTheOpenIsRemembered() {
        prefs.edit().put(SettingsManager.SEARCH_PREVIOUS_SEARCHES, false).commit()
        val viewModel = viewModel()
        viewModel.onQueryChanged("mo")
        viewModel.recordOpened(mom)
        val key = ResultKeys.contact(mom)!!
        eventually { memory.usage(key) != null }
        assertEquals(0f, memory.learnedWeight(key, "mo"), 0.001f)
    }

    @Test
    fun privateSpaceAppsLeaveNoTrace() {
        val hidden = calculator.copy(profileType = ProfileType.PRIVATE, isPrivateProfile = true)
        val viewModel = viewModel()
        viewModel.onQueryChanged("ca")
        viewModel.recordOpened(hidden)
        // Opened after it, so once this one is written the private one would have been too.
        viewModel.recordOpened(mom)
        eventually { memory.usage(ResultKeys.contact(mom)) != null }
        assertNull(memory.usage(ResultKeys.app(hidden)))
    }

    @Test
    fun clearingTheHistoryForgetsWhatWasTyped() {
        val viewModel = viewModel()
        viewModel.onQueryChanged("ca")
        viewModel.recordOpened(calculator)
        val key = ResultKeys.app(calculator)
        eventually { memory.learnedWeight(key, "ca") > 0f }
        viewModel.clearSearchHistory()
        eventually { memory.learnedWeight(key, "ca") == 0f }
        assertTrue(memory.usage(key) != null)
    }
}
