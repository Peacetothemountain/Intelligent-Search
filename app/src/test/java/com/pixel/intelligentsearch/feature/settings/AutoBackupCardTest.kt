package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Automatic Backup card shows what is set up now, including changes made while the page was away. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class AutoBackupCardTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private class PageLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    // AutoBackup's own state file and keys; enabling for real needs the Keystore, which tests don't have.
    private val state get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("auto_backup_state", Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        state.edit().clear().commit()
    }

    @Test
    fun backupsTurnedOnWhileThePageWasAwayShowWhenItComesBack() {
        val page = PageLifecycle().apply { registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides page) {
                IntelligentSearchTheme(darkTheme = false) { AutoBackupCard(obtainPassphrase = {}) }
            }
        }
        compose.settle(steps = 3)
        compose.onNodeWithText("Choose Folder and Turn On").assertExists()

        // Turned on by work that finished after the user left the page (the card's own run, or a relock mid-run).
        page.registry.currentState = Lifecycle.State.STARTED
        state.edit()
            .putBoolean("enabled", true)
            .putString("folder_uri", "content://com.android.externalstorage.documents/tree/primary%3ABackups")
            .putString("wrapped_key", "AAAA")
            .commit()
        page.registry.currentState = Lifecycle.State.RESUMED
        compose.settle(steps = 3)

        compose.onNodeWithText("On · Saving to", substring = true).assertExists()
    }
}
