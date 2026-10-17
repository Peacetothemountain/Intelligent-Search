package com.ngdesigns.intelligentsearch.engine.web

import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import com.ngdesigns.intelligentsearch.engine.toggles.ToggleRequest
import com.ngdesigns.intelligentsearch.engine.toggles.Toggles
import com.ngdesigns.intelligentsearch.engine.toggles.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTest {
    private fun valid(s: String) = SafeUrl.template(s)!!

    @Test fun safeTemplatesAreNormalized() {
        assertEquals("https://duckduckgo.com/?q=%s", valid("https://duckduckgo.com/?q=%s").template)
        assertEquals("https://search.example.org/search?q=%s", valid("search.example.org/search?q=%s").template)
        assertEquals("https://www.startpage.com/do/search?query=%s", valid("HTTPS://www.Startpage.com/do/search?query=%s").template)
        assertEquals("https://searx.example.net:8443/?q=%s", valid("searx.example.net:8443/?q=%s").template)
        assertTrue(valid("https://аpple.com/?q=%s").host.startsWith("xn--"))
    }

    @Test fun unsafeTemplatesAreRefused() {
        for (s in listOf(
            "javascript:alert(document.cookie)//%s", "JAVASCRIPT:alert(1)", "intent://scan/#Intent;scheme=zxing;end",
            "data:text/html,<script>alert(1)</script>", "file:///sdcard/evil.html", "content://com.example.provider/x",
            "http://example.com/?q=%s", "mailto:someone@example.com", "https://www.google.com@evil.example/?q=%s",
            "https://evil.example/?q=%s\"onload=\"x", "https://evil.example/<script>", "https://evil.example/?q=%s\nInjected: header",
            "https://exa mple.com/?q=%s", "https://%s.evil.example/", "https://localhost/?q=%s", "https://192.168.1.1/?q=%s",
            "https://example.com/no-placeholder", "https://" + "a".repeat(SafeUrl.MAX_LENGTH),
        )) assertNull(s, SafeUrl.template(s))
    }

    @Test fun plainAddressesFollowTheSameRules() {
        assertEquals("https://example.com/news", SafeUrl.address("example.com/news"))
        assertNull(SafeUrl.address("javascript:alert(1)"))
        assertNull(SafeUrl.address("https://example.com/?q=%s"))
        assertNull(SafeUrl.address("http://example.com"))
    }

    @Test fun buildingEncodesTheQueryAndKeepsTheHost() {
        val uri = SafeUrl.build("https://search.example.org/?q=%s", "a&b=c/../#frag")!!
        assertEquals("search.example.org", uri.host)
        assertEquals("q=a&b=c/../#frag", uri.query)
        assertNull(SafeUrl.build("javascript:alert(1)", "x"))
        assertEquals("Startpage", SafeUrl.displayName("https://www.startpage.com/do/search?query=%s"))
    }

    @Test fun shortcutsWorkAtEitherEnd() {
        val all = WebShortcuts.builtIn
        val prefix = WebShortcuts.parse("!g maps", all)!!
        assertEquals("Google" to "maps", prefix.shortcut.name to prefix.terms)
        val suffix = WebShortcuts.parse("cute cats !yt", all)!!
        assertEquals("YouTube" to "cute cats", suffix.shortcut.name to suffix.terms)
        assertNull(WebShortcuts.parse("no shortcut here", all))
        assertNull(WebShortcuts.parse("wow! amazing", all))
        assertEquals("Reddit", WebShortcuts.parse("@r kotlin", all, symbol = "@")!!.shortcut.name)
        assertTrue(WebShortcuts.parse("!g a&b c", all)!!.let { it.shortcut.url(it.terms) }.contains("a%26b%20c"))
        assertEquals(listOf("yt"), WebShortcuts.completions("!y", all).map { it.key })
    }

    @Test fun customShortcutsMustBeSafe() {
        assertNull(WebShortcuts.custom("ev", "Evil", "javascript:alert(%s)"))
        assertNull(WebShortcuts.custom("bad key", "X", "https://example.com/?q=%s"))
        assertEquals("https://example.com/?q=%s", WebShortcuts.custom("!mine", "Mine", "example.com/?q=%s")!!.urlTemplate)
    }

    @Test fun enginesBuildSearchesAndRespectPrivateHistory() {
        assertEquals("https://www.google.com/search?q=new%20york", SearchEngine.Google.searchUrl("new york"))
        assertNull(SearchEngine.DuckDuckGo.historyUrl)
        assertTrue(SearchEngine.Tor.searchUrl("x").contains(".onion/"))
        assertEquals(SearchEngine.Google, SearchEngine.named("Custom", "javascript:x"))
        assertEquals("Startpage", SearchEngine.named("Custom", "startpage.com/?q=%s").name)
    }

    @Test fun togglesAndVolumes() {
        assertEquals(listOf(ToggleRequest(Toggle.Wifi, null)), Toggles.find("wifi"))
        assertEquals(ToggleRequest(Toggle.Flashlight, true), Toggles.find("turn on flashlight").first())
        assertEquals(ToggleRequest(Toggle.Bluetooth, false), Toggles.find("bluetooth off").first())
        assertEquals(Toggle.Flashlight, Toggles.find("flas").first().toggle)
        assertEquals(Toggle.DoNotDisturb, Toggles.find("dnd").first().toggle)
        assertTrue(Toggles.find("spotify").isEmpty())
        assertEquals(listOf(VolumeStream.Alarm), Toggles.volumes("alarm volume"))
    }
}
