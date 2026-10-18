package com.pixel.intelligentsearch.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechCleanupTest {
    private fun clean(s: String) = SpeechCleanup.clean(s)

    @Test fun dropsFillerSounds() = assertEquals("weather tomorrow in Chicago", clean("um weather uh tomorrow in, uhm, Chicago"))

    @Test fun collapsesStuttersAndRepeats() {
        assertEquals("open the camera", clean("open the the camera"))
        assertEquals("Let's do that. Okay", clean("Let's do that. Okay. Okay. Okay. Okay."))
        assertEquals("pizza near me", clean("pizza near me pizza near me"))
    }

    @Test fun appliesSpokenCorrections() {
        assertEquals("timer for 10 minutes", clean("timer for 5 minutes, no, 10 minutes"))
        assertEquals("timer for 10 minutes", clean("timer for 5 minutes, no, 10 minutes."))
        assertEquals("5 mm socket near the ER", clean("5 mm socket near the ER"))
        assertEquals("weather in Chicago", clean("weather in Boston, no wait Chicago"))
        assertEquals("call Sam", clean("call Ben, I mean Sam"))
        assertEquals("directions to the airport", clean("directions to the station, actually the airport"))
    }

    @Test fun keepsOrdinaryUsesOfCorrectionWords() {
        assertEquals("coffee with no sugar", clean("coffee with no sugar"))
        assertEquals("is it actually good", clean("is it actually good"))
        assertEquals("no man's sky", clean("no man's sky"))
    }

    @Test fun restartsDiscardEarlierWords() {
        assertEquals("best tacos nearby", clean("send a text to mom scratch that best tacos nearby"))
        assertEquals("", clean("um never mind"))
    }

    @Test fun joinsSegmentsAndTrimsTrailingPunctuation() {
        assertEquals("flights to Denver next Friday", SpeechCleanup.clean(listOf("flights to Denver", "next Friday.")))
    }
}
