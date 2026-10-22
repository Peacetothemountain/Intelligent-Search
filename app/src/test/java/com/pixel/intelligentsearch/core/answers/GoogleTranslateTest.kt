package com.pixel.intelligentsearch.core.answers

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Translations are handed to Google Translate: the app (the same package the widget's Translate shortcut opens) when
 * it takes them, else its web page. Building these reaches no network; the addresses are only strings.
 */
@RunWith(RobolectricTestRunner::class)
class GoogleTranslateTest {

    @Test
    fun theWebAddressCarriesTheLanguagesAndTheEncodedText() {
        assertEquals(
            "https://translate.google.com/?sl=auto&tl=es&text=good%20morning&op=translate",
            GoogleTranslate.webAddress("good morning", null, "es")
        )
        assertEquals(
            "https://translate.google.com/?sl=es&tl=en&text=gato&op=translate",
            GoogleTranslate.webAddress("gato", "es", "en")
        )
    }

    @Test
    fun textThatCouldBreakTheAddressIsEncoded() {
        val address = GoogleTranslate.webAddress("I'm late & sorry? 100% #1 = día+noche/日本", null, "fr")
        assertEquals(
            "https://translate.google.com/?sl=auto&tl=fr&text=" +
                "I%27m%20late%20%26%20sorry%3F%20100%25%20%231%20%3D%20d%C3%ADa%2Bnoche%2F%E6%97%A5%E6%9C%AC&op=translate",
            address
        )
        // The text stays one parameter: nothing in it starts another.
        assertEquals(4, address.substringAfter('?').split('&').size)
    }

    @Test
    fun googleSpellsHebrewAndChineseItsOwnWay() {
        assertEquals("https://translate.google.com/?sl=iw&tl=zh-CN&text=shalom&op=translate", GoogleTranslate.webAddress("shalom", "he", "zh"))
    }

    @Test
    fun theAppGetsItsTranslatePageFirstThenTheSharedText() {
        val (view, send) = GoogleTranslate.appIntents("hello", null, "es")
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(GoogleTranslate.PACKAGE, view.`package`)
        assertEquals(GoogleTranslate.webAddress("hello", null, "es"), view.dataString)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals(GoogleTranslate.PACKAGE, send.`package`)
        assertEquals("text/plain", send.type)
        assertEquals("hello", send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(listOf(view, send).all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test
    fun theFirstIntentTheAppTakesIsUsedElseTheWebPage() {
        assertEquals(Intent.ACTION_VIEW, GoogleTranslate.appIntent("hello", null, "es") { true }?.action)
        assertEquals(Intent.ACTION_SEND, GoogleTranslate.appIntent("hello", null, "es") { it.action == Intent.ACTION_SEND }?.action)
        // Not installed: the overlay opens the web address instead.
        assertNull(GoogleTranslate.appIntent("hello", null, "es") { false })
    }
}
