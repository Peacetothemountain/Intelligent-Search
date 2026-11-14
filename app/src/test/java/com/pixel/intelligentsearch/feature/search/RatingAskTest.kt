package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RatingAskTest {

    private val day = 24L * 60L * 60L * 1000L
    private val installed = 1_000_000_000_000L

    private fun due(afterDays: Int = 8, opens: Int = 30, done: Boolean = false, tutorialDone: Boolean = true, firstInstall: Long = installed) =
        RatingAsk.due(firstInstall, installed + afterDays * day, opens, done, tutorialDone)

    @Test
    fun itAsksOnceTheAppHasBeenInstalledAWeekAndWellUsed() {
        assertTrue(due())
        assertTrue(due(afterDays = 7))
        assertFalse("too soon", due(afterDays = 6))
        assertFalse("not used enough", due(opens = 29))
    }

    @Test
    fun itNeverAsksAgainOnceAnswered() {
        assertFalse(due(done = true, afterDays = 400, opens = 1000))
    }

    @Test
    fun itWaitsForTheTutorialAndForAPhoneThatSaysWhenItWasInstalled() {
        assertFalse(due(tutorialDone = false))
        assertFalse(due(firstInstall = 0L))
    }

    @Test
    fun feedbackIsAnEmailToTheDeveloperNamingTheVersion() {
        val intent = RatingAsk.feedbackIntent("9.6.1")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:support.ngdesigns@gmail.com", intent.data.toString())
        assertEquals("Intelligent Search feedback (9.6.1)", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Intelligent Search feedback", RatingAsk.feedbackIntent("").getStringExtra(Intent.EXTRA_SUBJECT))
    }
}
