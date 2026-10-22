package com.pixel.intelligentsearch.feature.search

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PersistableBundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Search with Lens for an image on the clipboard: telling an image clip from its description alone, building the
 * share to Lens (or the Google app), and offering it among the Smart Clipboard Suggestions only when they are on.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ClipboardLensTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
    private val image = Uri.parse("content://com.example.screenshots/image/42")
    private val googleLens = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensShareEntryPointActivity")

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    private fun description(vararg types: String, sensitive: Boolean = false) = ClipDescription("copied", types).apply {
        if (sensitive) extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }

    @Test
    fun anImageIsToldFromItsDescriptionAlone() {
        assertTrue(ClipboardLens.isImage(description("image/png")))
        assertTrue(ClipboardLens.isImage(description("text/plain", "image/jpeg")))
        assertFalse(ClipboardLens.isImage(description("text/plain")))
        assertFalse(ClipboardLens.isImage(null))
        // Marked sensitive, it is never offered.
        assertFalse(ClipboardLens.isImage(description("image/png", sensitive = true)))
        assertEquals("image/jpeg", ClipboardLens.imageType(description("text/plain", "image/jpeg")))
        assertEquals("image/*", ClipboardLens.imageType(description("image/*")))
    }

    @Test
    fun theLensAppIsPreferredThenTheGoogleAppsLensShareThenTheGoogleApp() {
        fun pick(vararg installed: String) = ClipboardLens.intentFor(image, "image/png") { intent ->
            (intent.component?.className ?: intent.`package`) in installed
        }
        assertEquals("com.google.ar.lens", pick("com.google.ar.lens", googleLens.className)?.`package`)
        assertEquals(googleLens, pick(googleLens.className, "com.google.android.googlequicksearchbox")?.component)
        assertEquals("com.google.android.googlequicksearchbox", pick("com.google.android.googlequicksearchbox")?.`package`)
        assertNull(pick())
    }

    @Test
    fun theShareCarriesTheImageWithReadAccessToItAlone() {
        val share = ClipboardLens.intentFor(image, "image/png") { true }!!
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("image/png", share.type)
        @Suppress("DEPRECATION")
        assertEquals(image, share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(image, share.clipData?.getItemAt(0)?.uri)
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(share.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    private fun installGoogleLens() {
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(googleLens)
        pm.addIntentFilterForActivity(googleLens, IntentFilter(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType("image/*")
        })
    }

    /** A browser, so copied text would be offered as a web search. */
    private fun installBrowser() {
        val component = ComponentName("com.example.browser", "com.example.browser.Browser")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("https")
        })
    }

    private fun copyImage() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData("Screenshot", arrayOf("image/png"), ClipData.Item(image)))
    }

    private fun viewModel(): SearchViewModel {
        lateinit var viewModel: SearchViewModel
        ActivityScenario.launch(HiltTestActivity::class.java).onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
        return viewModel
    }

    @Test
    fun aCopiedImageIsOfferedToLensNotAsCopiedText() {
        prefs.edit().put(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, true).commit()
        installGoogleLens()
        installBrowser()
        copyImage()
        val viewModel = viewModel()
        runBlocking { viewModel.refreshClipboardActions() }

        val actions = viewModel.uiState.value.directActions
        assertEquals(listOf(ClipboardLens.TITLE), actions.map { it.title })
        // Offered from the description: nothing of the clip, not even its address, is in the suggestion.
        assertNull(actions.single().intent)
        assertFalse(actions.single().subtitle.contains("content://"))
    }

    @Test
    fun tappingItSendsTheImageToLens() {
        installGoogleLens()
        copyImage()
        assertTrue(ClipboardLens.searchClipboardImage(context))
        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, started?.action)
        assertEquals(googleLens, started?.component)
        assertEquals(image, started?.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun nothingIsOfferedWithSmartClipboardOffUnderTorOrWithoutLens() {
        installGoogleLens()
        copyImage()
        val viewModel = viewModel()
        runBlocking { viewModel.refreshClipboardActions() }
        assertEquals(emptyList<DirectAction>(), viewModel.uiState.value.directActions)

        prefs.edit().put(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS, true).put(SettingsManager.SEARCH_ENGINE, "Tor Project").commit()
        runBlocking { viewModel.refreshClipboardActions() }
        assertEquals(emptyList<DirectAction>(), viewModel.uiState.value.directActions)
    }

    @Test
    fun withoutLensOrTheGoogleAppThereIsNothingToOffer() {
        assertNull(ClipboardLens.suggestionFor(context, description("image/png")))
        installGoogleLens()
        assertEquals(ClipboardLens.ICON_TYPE, ClipboardLens.suggestionFor(context, description("image/png"))?.iconType)
    }
}
