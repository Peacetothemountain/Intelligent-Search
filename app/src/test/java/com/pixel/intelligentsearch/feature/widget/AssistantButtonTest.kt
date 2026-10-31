package com.pixel.intelligentsearch.feature.widget

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.system.GoogleApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The Material bar's round assistant button opens the user's assistant. With Intelligent Search as the phone's
 * assistant, the Google app's voice command only forwards to Android's "Default digital assistant app" page (Google
 * isn't the assistant), so the button opens Gemini itself, else the Google app's voice search, else this app's own.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantButtonTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun install(component: ComponentName, filter: IntentFilter) {
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(component, filter)
        }
    }

    private fun gemini(): ComponentName = ComponentName(GoogleApps.GEMINI_APP, "${GoogleApps.GEMINI_APP}.Main").also {
        install(it, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    /** The Google app: its voice command (which forwards to the assistant picker here) and its voice search. */
    private fun googleApp(): ComponentName {
        install(
            ComponentName(GoogleApps.GOOGLE_APP, "com.google.android.apps.gsa.HandsFreeActivity"),
            IntentFilter(Intent.ACTION_VOICE_COMMAND).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        )
        return ComponentName(GoogleApps.GOOGLE_APP, GoogleApps.VOICE_SEARCH_ACTIVITY).also {
            install(it, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
    }

    private fun thisAppIsTheAssistant() =
        shadowOf(context.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_ASSISTANT)

    private fun tapped(): Intent {
        assertTrue(launchGeminiCornerOverlay(context))
        return shadowOf(app).nextStartedActivity
    }

    @Test
    fun asTheAssistantItOpensGemini() {
        thisAppIsTheAssistant()
        googleApp()
        val gemini = gemini()
        val opened = tapped()
        assertEquals(gemini, context.packageManager.resolveActivity(opened, 0)?.activityInfo?.let { ComponentName(it.packageName, it.name) })
    }

    @Test
    fun asTheAssistantWithoutGeminiItOpensTheGoogleAppsVoiceSearch() {
        thisAppIsTheAssistant()
        val voiceSearch = googleApp()
        assertEquals(voiceSearch, tapped().component)
    }

    @Test
    fun asTheAssistantWithNeitherItOpensItsOwnVoiceSearch() {
        thisAppIsTheAssistant()
        // Another app answering voice web searches is not the assistant either.
        install(ComponentName("org.example.voice", "org.example.voice.Main"), IntentFilter(RecognizerIntent.ACTION_WEB_SEARCH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        install(ComponentName("org.example.speech", "org.example.speech.Main"), IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        val opened = tapped()
        assertEquals(ComponentName(context, WidgetActivity::class.java), opened.component)
        assertTrue(opened.getBooleanExtra(EXTRA_RECOGNIZE_SPEECH, false))
    }

    @Test
    fun withAnotherAssistantTheFallbacksStartWithTheGoogleAppsVoiceCommand() {
        googleApp()
        gemini()
        val fallbacks = assistantFallbacks(context, isTheAssistant = false)
        assertEquals(Intent.ACTION_VOICE_COMMAND, fallbacks.first().action)
        assertEquals(GoogleApps.GOOGLE_APP, fallbacks.first().`package`)
        assertEquals(GoogleApps.GEMINI_APP, fallbacks[1].`package` ?: fallbacks[1].component?.packageName)
    }
}
