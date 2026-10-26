package com.pixel.intelligentsearch.core.backup

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The WebAuthn JSON for the backup passkey: made for the app's website, asking for the PRF on the given salt. */
@RunWith(RobolectricTestRunner::class)
class PasskeyRequestsTest {

    private val salt = ByteArray(32) { it.toByte() }

    @Test
    fun creationAsksForASyncedPasskeyWithPrf() {
        val json = JSONObject(PasskeyRequests.creation(ByteArray(16) { 1 }, salt))
        assertEquals("intelligentsearchapp.github.io", json.getJSONObject("rp").getString("id"))
        assertEquals(PasskeyRequests.USER_NAME, json.getJSONObject("user").getString("name"))
        val selection = json.getJSONObject("authenticatorSelection")
        assertEquals("required", selection.getString("residentKey"))
        assertEquals("required", selection.getString("userVerification"))
        assertEquals(
            PasskeyRequests.b64url(salt),
            json.getJSONObject("extensions").getJSONObject("prf").getJSONObject("eval").getString("first")
        )
        assertFalse("base64url without padding", json.getString("challenge").contains("="))
    }

    @Test
    fun assertionAsksForTheBackupsPasskeyOnly() {
        val json = JSONObject(PasskeyRequests.assertion(listOf("cred-1"), salt))
        assertEquals("intelligentsearchapp.github.io", json.getString("rpId"))
        val allowed = json.getJSONArray("allowCredentials")
        assertEquals(1, allowed.length())
        assertEquals("cred-1", allowed.getJSONObject(0).getString("id"))
        assertEquals(PasskeyRequests.b64url(salt), json.getJSONObject("extensions").getJSONObject("prf").getJSONObject("eval").getString("first"))
    }

    @Test
    fun registrationResultsSayWhetherThePasswordManagerSupportsPrf() {
        val output = ByteArray(32) { 9 }
        val withOutput = PasskeyRequests.parseRegistration(
            """{"id":"abc","clientExtensionResults":{"prf":{"enabled":true,"results":{"first":"${PasskeyRequests.b64url(output)}"}}}}"""
        )
        assertEquals("abc", withOutput.credentialId)
        assertTrue(withOutput.prfEnabled)
        assertArrayEquals(output, withOutput.prfOutput)

        val enabledOnly = PasskeyRequests.parseRegistration("""{"id":"abc","clientExtensionResults":{"prf":{"enabled":true}}}""")
        assertTrue(enabledOnly.prfEnabled)
        assertNull(enabledOnly.prfOutput)

        val unsupported = PasskeyRequests.parseRegistration("""{"id":"abc","clientExtensionResults":{}}""")
        assertFalse(unsupported.prfEnabled)
    }

    @Test
    fun anAssertionWithoutPrfOutputIsRefused() {
        val output = ByteArray(32) { 3 }
        val result = PasskeyRequests.parseAssertion(
            """{"id":"abc","clientExtensionResults":{"prf":{"results":{"first":"${PasskeyRequests.b64url(output)}"}}}}"""
        )
        assertEquals("abc", result?.credentialId)
        assertArrayEquals(output, result?.prfOutput)
        assertNull(PasskeyRequests.parseAssertion("""{"id":"abc","clientExtensionResults":{}}"""))
    }
}
