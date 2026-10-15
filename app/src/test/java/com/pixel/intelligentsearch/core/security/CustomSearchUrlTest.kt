package com.pixel.intelligentsearch.core.security

import com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomSearchUrlTest {

    private fun valid(raw: String) = CustomSearchUrl.validate(raw) as Validation.Valid
    private fun rejected(raw: String) = assertTrue(raw, CustomSearchUrl.validate(raw) is Validation.Invalid)

    @Test
    fun acceptsOrdinaryTemplatesAndAddsHttps() {
        assertEquals("https://duckduckgo.com/?q=%s", valid("https://duckduckgo.com/?q=%s").template)
        assertEquals("https://search.example.org/search?q=%s", valid("search.example.org/search?q=%s").template)
        assertEquals("https://www.startpage.com/do/search?query=%s", valid("HTTPS://www.Startpage.com/do/search?query=%s").template)
        assertEquals("https://searx.example.net:8443/?q=%s", valid("searx.example.net:8443/?q=%s").template)
    }

    @Test
    fun refusesOtherSchemesInsteadOfPrefixingThem() {
        rejected("javascript:alert(document.cookie)//%s")
        rejected("JAVASCRIPT:alert(1)")
        rejected("intent://scan/#Intent;scheme=zxing;package=com.evil;end")
        rejected("data:text/html,<script>alert(1)</script>")
        rejected("file:///sdcard/evil.html")
        rejected("content://com.example.provider/x")
        rejected("http://example.com/?q=%s")
        rejected("mailto:someone@example.com")
    }

    @Test
    fun refusesCredentialsLookalikesAndInjectedCharacters() {
        rejected("https://www.google.com@evil.example/?q=%s")
        rejected("https://evil.example/?q=%s\"onload=\"x")
        rejected("https://evil.example/<script>")
        rejected("https://evil.example/?q=%s\nInjected: header")
        rejected("https://exa mple.com/?q=%s")
        rejected("https://%s.evil.example/")
        rejected("https://localhost/?q=%s")
        rejected("")
        rejected("https://" + "a".repeat(CustomSearchUrl.MAX_LENGTH))
    }

    @Test
    fun hostsAreStoredInPunycodeSoLookalikesAreVisible() {
        // Cyrillic "а" in place of Latin "a": shows up as xn-- instead of passing for the real site.
        val host = valid("https://аpple.com/?q=%s").host
        assertTrue(host, host.startsWith("xn--"))
    }

    @Test
    fun builtUrlsEncodeTheQuerySoItStaysInItsSlot() {
        val uri = CustomSearchUrl.build("https://search.example.org/?q=%s", "a&b=c/../#frag")!!
        assertEquals("search.example.org", uri.host)
        assertEquals("a&b=c/../#frag", uri.getQueryParameter("q"))
        assertNull(uri.fragment)
        assertNull(CustomSearchUrl.build("javascript:alert(1)", "x"))
        assertEquals("Startpage", CustomSearchUrl.displayName("https://www.startpage.com/do/search?query=%s"))
    }
}
