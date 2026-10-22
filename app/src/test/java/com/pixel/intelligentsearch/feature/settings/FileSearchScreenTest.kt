package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
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
import org.robolectric.annotation.Config

/** Clearing the chosen photos and videos drops access to all of them, so it asks first, like removing a folder. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class FileSearchScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear()
            .putStringSet(UserGrantedFiles.KEY_MEDIA_URIS, setOf("content://media/picker/0/1", "content://media/picker/0/2"))
            .commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { FileSearchScreen(prefs) {} } }
        compose.waitForIdle()
    }

    @Test
    fun clearingChosenPhotosAsksFirst() {
        compose.onNodeWithText("Clear Chosen Photos & Videos").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Stop searching chosen photos and videos?").assertExists()
        assertEquals(2, UserGrantedFiles.mediaUris(prefs).size)

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(2, UserGrantedFiles.mediaUris(prefs).size)
    }

    @Test
    fun confirmingClearsThem() {
        compose.onNodeWithText("Clear Chosen Photos & Videos").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Clear").performClick()
        compose.settle(steps = 5)

        assertEquals(0, UserGrantedFiles.mediaUris(prefs).size)
        compose.onNodeWithText("Clear Chosen Photos & Videos").assertDoesNotExist()
    }
}
