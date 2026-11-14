package com.pixel.intelligentsearch.core.answers

import com.pixel.intelligentsearch.core.answers.PhoneAnswers.Kind
import com.pixel.intelligentsearch.core.answers.PhoneAnswers.NetworkReading
import com.pixel.intelligentsearch.core.answers.PhoneAnswers.StorageReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneAnswersTest {

    private fun answers(
        storage: StorageReading? = StorageReading(totalBytes = 256_000_000_000L, freeBytes = 118_400_000_000L),
        network: NetworkReading? = NetworkReading("192.168.1.23", wifi = true, mobile = false)
    ) = PhoneAnswers({ storage }, { network })

    @Test
    fun storageSaysHowMuchIsFreeOfHowMuch() {
        val answer = answers().evaluate("storage")!!
        assertEquals(Kind.STORAGE, answer.kind)
        assertEquals("118 GB free", answer.title)
        assertEquals("of 256 GB • 53% used", answer.subtitle)
        assertNull(answer.copyable)
    }

    @Test
    fun theWaysOfAskingAboutStorageAreAllAnswered() {
        for (query in listOf("Storage", "free space", "how much storage do I have?", "How much space is left", " disk  space ")) {
            assertEquals(query, Kind.STORAGE, answers().evaluate(query)?.kind)
        }
    }

    @Test
    fun smallSizesKeepADecimalOrDropToMegabytes() {
        assertEquals("9.4 GB", PhoneAnswers.formatSize(9_400_000_000L))
        assertEquals("512 MB", PhoneAnswers.formatSize(512_000_000L))
        assertEquals("64 GB", PhoneAnswers.formatSize(64_000_000_000L))
    }

    @Test
    fun aPhoneThatWontSayItsStorageGivesNoAnswer() {
        assertNull(answers(storage = null).evaluate("storage"))
        assertNull(answers(storage = StorageReading(0L, 0L)).evaluate("storage"))
    }

    @Test
    fun myIpIsThePhonesAddressAndATapCopiesIt() {
        val answer = answers().evaluate("what's my ip?")!!
        assertEquals(Kind.ADDRESS, answer.kind)
        assertEquals("192.168.1.23", answer.title)
        assertEquals("This phone's address on Wi-Fi • Tap to copy", answer.subtitle)
        assertEquals("192.168.1.23", answer.copyable)
    }

    @Test
    fun theAddressSaysWhichKindOfNetworkItIsOn() {
        assertEquals(
            "This phone's address on mobile data • Tap to copy",
            answers(network = NetworkReading("10.20.30.40", wifi = false, mobile = true)).evaluate("my ip")!!.subtitle
        )
        assertEquals(
            "This phone's address on this network • Tap to copy",
            answers(network = NetworkReading("10.8.0.2", wifi = false, mobile = false)).evaluate("ip address")!!.subtitle
        )
    }

    @Test
    fun withNoNetworkItSaysSoAndThereIsNothingToCopy() {
        val answer = answers(network = null).evaluate("my ip")!!
        assertEquals("Not connected", answer.title)
        assertNull(answer.copyable)
    }

    @Test
    fun searchesThatOnlyMentionTheWordsAreLeftAlone() {
        for (query in listOf("storage units near me", "cloud storage", "ip man", "ipad", "what is an ip address", "storage wars")) {
            assertNull(query, answers().evaluate(query))
        }
    }
}
