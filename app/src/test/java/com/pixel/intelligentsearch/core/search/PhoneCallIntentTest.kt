package com.pixel.intelligentsearch.core.search

import android.content.Intent
import com.pixel.intelligentsearch.core.data.ContactItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "call <name>" dials the contact's number exactly as saved, service codes included. */
@RunWith(RobolectricTestRunner::class)
class PhoneCallIntentTest {

    private fun call(query: String, contact: ContactItem) =
        NaturalLanguageIntentParser.parse(query, contactLookup = { contact }) as NaturalLanguageIntentParser.ParsedIntent.PhoneCall

    @Test
    fun aContactsServiceCodeIsDialledWhole() {
        val call = call("call voicemail", ContactItem("Voicemail", "*86#", ""))

        assertEquals(Intent.ACTION_DIAL, call.intent.action)
        // Filtered to digits, "*86#" became "86"; parsed as a "tel:" link, everything after '#' was lost.
        assertEquals("*86#", call.intent.data!!.schemeSpecificPart)
        assertEquals("Voicemail", call.contactNameOrNumber)
        assertEquals("*86#", call.resolvedPhoneNumber)
    }

    @Test
    fun aContactsFormattedNumberReachesTheDiallerAsSaved() {
        val call = call("dial carrier", ContactItem("Carrier", "#225#", ""))

        assertEquals("#225#", call.intent.data!!.schemeSpecificPart)
    }

    @Test
    fun aTypedNumberIsStillDialledAsDigits() {
        val call = NaturalLanguageIntentParser.parse("call (415) 555-0100") as NaturalLanguageIntentParser.ParsedIntent.PhoneCall

        assertEquals("4155550100", call.intent.data!!.schemeSpecificPart)
    }
}
