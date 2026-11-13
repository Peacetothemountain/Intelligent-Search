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
        assertEquals("call Sam", SpeechCleanup.clean(listOf("call Ben", "I mean Sam")))
    }

    @Test fun keepsOrdinaryUsesOfCorrectionWords() {
        assertEquals("coffee with no sugar", clean("coffee with no sugar"))
        assertEquals("is it actually good", clean("is it actually good"))
        assertEquals("no man's sky", clean("no man's sky"))
        assertEquals("best games like No Man's Sky", SpeechCleanup.clean(listOf("best games like", "No Man's Sky")))
        assertEquals("timer for 10 minutes", SpeechCleanup.clean(listOf("timer for 5 minutes", "No, 10 minutes")))
    }

    @Test fun restartsDiscardEarlierWords() {
        assertEquals("best tacos nearby", clean("send a text to mom scratch that best tacos nearby"))
        assertEquals("", clean("um never mind"))
        assertEquals("", clean("set a timer for ten minutes never mind"))
        assertEquals("", clean("send a text to mom scratch that"))
        assertEquals("what time is it in tokyo", clean("weather in boston never mind what time is it in tokyo"))
    }

    @Test fun keepsSearchesThatOnlySoundLikeCorrections() {
        assertEquals("how to start over after a divorce", clean("how to start over after a divorce"))
        assertEquals("how to delete that app", clean("how to delete that app"))
        assertEquals("Never Mind the Bollocks", clean("Never Mind the Bollocks"))
        assertEquals("best albums like Never Mind the Bollocks", SpeechCleanup.clean(listOf("best albums like", "Never Mind the Bollocks")))
        assertEquals("nirvana nevermind", clean("nirvana nevermind"))
        assertEquals("Bora Bora hotels", clean("Bora Bora hotels"))
        assertEquals("mahi mahi recipe", clean("mahi mahi recipe"))
        assertEquals("how to start over", clean("how to start over"))
        assertEquals("best albums like never mind the bollocks", clean("best albums like never mind the bollocks"))
        assertEquals("lyrics i mean it", clean("lyrics i mean it"))
        assertEquals("destinys child no no no", clean("destinys child no no no"))
        assertEquals("new york new york hotel", clean("new york new york hotel"))
        assertEquals("nirvana never mind", clean("nirvana never mind"))
        assertEquals("the song start over", clean("the song start over"))
        assertEquals("how do you say never mind in spanish", clean("how do you say never mind in spanish"))
        assertEquals("translate never mind to french", clean("translate never mind to french"))
    }

    @Test fun restartsSaidAfterAPause() {
        assertEquals("best tacos nearby", SpeechCleanup.clean(listOf("send a text to mom", "start over", "best tacos nearby")))
        assertEquals("best tacos nearby", clean("send a text to mom, never mind, best tacos nearby"))
        assertEquals("", SpeechCleanup.clean(listOf("send a text to mom", "never mind")))
    }

    @Test fun joinsSegmentsAndTrimsTrailingPunctuation() {
        assertEquals("flights to Denver next Friday", SpeechCleanup.clean(listOf("flights to Denver", "next Friday.")))
    }
}
