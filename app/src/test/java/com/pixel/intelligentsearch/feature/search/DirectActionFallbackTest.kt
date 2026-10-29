package com.pixel.intelligentsearch.feature.search

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser
import com.pixel.intelligentsearch.core.search.NaturalLanguageIntentParser.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Directions and WhatsApp messages name the app they open. Without Google Maps or WhatsApp the tap did nothing; it
 * now opens any maps app, or sends the message as a text.
 */
@RunWith(RobolectricTestRunner::class)
class DirectActionFallbackTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyInstalledAppsOpen() {
        // Neither Google Maps nor WhatsApp is installed: an intent no app takes fails to start, as on a phone.
        shadowOf(context).checkActivities(true)
    }

    private fun installHandlerFor(intent: Intent) {
        val handler = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "org.example.handler"; name = "org.example.handler.Main" }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, handler)
    }

    @Test
    fun directionsWithoutGoogleMapsOpenAnyMapsApp() {
        val navigation = NaturalLanguageIntentParser.parse("directions to Central Park") as ParsedIntent.Navigation
        val fallback = navigation.fallbackIntent!!
        assertNull("any maps app may answer", fallback.`package`)
        assertEquals("geo:0,0?q=Central%20Park", fallback.dataString)
        installHandlerFor(fallback)

        val action = DirectAction("Navigate to Central Park", "Central Park", "navigation", navigation.intent, navigation.fallbackIntent)
        assertTrue(launchDirectActionIntent(context, action))
        assertEquals("geo", shadowOf(context).nextStartedActivity.data?.scheme)
    }

    @Test
    fun aPlaceWrittenAsAnIntentStaysAPlaceToSearch() {
        val typed = "x#Intent;component=org.example.evil/.Steal;S.token=1;end"
        val fallback = (NaturalLanguageIntentParser.parse("directions to $typed") as ParsedIntent.Navigation).fallbackIntent!!
        assertEquals(Intent.ACTION_VIEW, fallback.action)
        assertNull(fallback.component)
        assertNull(fallback.extras)
        assertNull(fallback.selector)
        assertEquals("geo", fallback.data?.scheme)
        assertEquals("geo:0,0?q=" + android.net.Uri.encode(typed), fallback.dataString)
    }

    @Test
    fun aWhatsAppMessageWithoutWhatsAppIsSentAsAText() {
        val message = NaturalLanguageIntentParser.parse("message Dave on whatsapp saying hi") as ParsedIntent.DirectMessage
        assertEquals("com.whatsapp", message.intent.`package`)
        val fallback = message.fallbackIntent!!
        assertEquals(Intent.ACTION_SENDTO, fallback.action)
        assertEquals("hi", fallback.getStringExtra("sms_body"))
        installHandlerFor(fallback)

        val action = DirectAction("Message Dave (WhatsApp)", "hi", "message", message.intent, message.fallbackIntent)
        assertTrue(launchDirectActionIntent(context, action))
        assertEquals(Intent.ACTION_SENDTO, shadowOf(context).nextStartedActivity.action)
    }

    @Test
    fun aPlainTextHasNoFallback() {
        val message = NaturalLanguageIntentParser.parse("text Dave saying hi") as ParsedIntent.DirectMessage
        assertNull(message.fallbackIntent)
    }
}
