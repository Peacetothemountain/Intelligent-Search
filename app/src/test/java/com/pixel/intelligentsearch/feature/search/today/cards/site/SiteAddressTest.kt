package com.pixel.intelligentsearch.feature.search.today.cards.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * The website reader's two security gates, without a network: which typed addresses become an https address (and
 * which are refused, and why), and which network addresses a site may be read from.
 */
class SiteAddressTest {

    private fun url(input: String): String? = (normalizeSiteAddress(input) as? SiteAddressResult.Ok)?.address?.url

    private fun refusal(input: String): SiteRefusal? = (normalizeSiteAddress(input) as? SiteAddressResult.Refused)?.reason

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal) // a literal: never looked up

    @Test
    fun aBareSiteBecomesItsHttpsAddress() {
        assertEquals("https://weekend.recipes/", url("weekend.recipes"))
        assertEquals("https://weekend.recipes/", url("  weekend.recipes  "))
        assertEquals("https://example.com/page?a=1", url("https://Example.COM/page?a=1#top"))
        assertEquals("https://example.com/", url("HTTPS://example.com"))
        assertEquals("https://example.com/", url("example.com:443"))
        assertEquals("https://example.com/", url("example.com."))
        assertEquals("https://example.com/?q=1", url("example.com?q=1"))
    }

    @Test
    fun internationalNamesBecomePunycode() {
        assertEquals("https://xn--bcher-kva.de/", url("bücher.de"))
        assertEquals("https://xn--bcher-kva.de/", url("https://BÜCHER.de"))
    }

    @Test
    fun pathsAreEncodedButEscapesKept() {
        assertEquals("https://example.com/caf%C3%A9", url("example.com/café"))
        assertEquals("https://example.com/a%20b", url("example.com/a%20b"))
        assertEquals("https://example.com/100%25", url("example.com/100%"))
    }

    @Test
    fun onlyHttpsIsAllowed() {
        assertEquals(SiteRefusal.NOT_SECURE, refusal("http://example.com"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("ftp://example.com"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("javascript:alert(1)"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("file:///etc/hosts"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("intent:#Intent;end"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("data:text/html,hi"))
        assertEquals(SiteRefusal.NOT_SECURE, refusal("http:example.com"))
    }

    @Test
    fun nonsenseIsNotAnAddress() {
        for (input in listOf("", "   ", "https://", "two words.com", "a<b>.com", "user@example.com", "https://user:pw@example.com",
            "example.com:8080", "-bad-.com", "exa_mple.com", "[::1]", "https://[2606:4700::1]/")) {
            assertEquals(input, SiteRefusal.NOT_AN_ADDRESS, refusal(input))
        }
    }

    @Test
    fun addressesWrittenAsNumbersAreRefused() {
        for (input in listOf("127.0.0.1", "8.8.8.8", "https://10.0.0.1/", "0x7f.1", "1.2.3")) {
            assertEquals(input, SiteRefusal.NOT_AN_ADDRESS, refusal(input))
        }
    }

    @Test
    fun localNamesAreRefusedBeforeAnyLookup() {
        for (input in listOf("localhost", "LOCALHOST", "https://localhost/", "printer.local", "db.internal", "app.localhost",
            "router.home.arpa", "intranet", "https://nas/")) {
            assertEquals(input, SiteRefusal.NOT_PUBLIC, refusal(input))
        }
    }

    @Test
    fun redirectsAndImagesAreResolvedUnderTheSameRules() {
        val base = (normalizeSiteAddress("example.com/blog/post") as SiteAddressResult.Ok).address
        fun resolved(ref: String) = (resolveSiteAddress(base, ref) as? SiteAddressResult.Ok)?.address?.url
        fun refused(ref: String) = (resolveSiteAddress(base, ref) as? SiteAddressResult.Refused)?.reason

        assertEquals("https://example.com/img/a.png", resolved("/img/a.png"))
        assertEquals("https://example.com/blog/a%20b.png", resolved("a b.png"))
        assertEquals("https://cdn.example.net/x.jpg", resolved("//cdn.example.net/x.jpg"))
        assertEquals("https://other.org/", resolved("https://other.org"))
        assertEquals(SiteRefusal.NOT_SECURE, refused("http://example.com/"))
        assertEquals(SiteRefusal.NOT_SECURE, refused("data:image/png;base64,AAAA"))
        assertEquals(SiteRefusal.NOT_PUBLIC, refused("https://localhost/admin"))
        assertEquals(SiteRefusal.NOT_AN_ADDRESS, refused("https://169.254.169.254/latest/meta-data"))
    }

    @Test
    fun publicAddressesMayBeRead() {
        for (literal in listOf("93.184.216.34", "8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "172.15.0.1", "172.32.0.1",
            "2606:4700:4700::1111", "2001:4860:4860::8888", "64:ff9b::808:808", "2002:808:808::1")) {
            assertTrue(literal, isPublicAddress(ip(literal)))
        }
    }

    @Test
    fun privateAndReservedAddressesAreRefused() {
        for (literal in listOf(
            // This phone and "any".
            "127.0.0.1", "127.1.2.3", "0.0.0.0", "0.1.2.3", "::1", "::",
            // Private networks and carrier-grade NAT.
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.1.1", "100.64.0.1", "100.127.255.255",
            // Link-local, the cloud metadata address among them.
            "169.254.169.254", "fe80::1", "febf::1",
            // Unique local and old site-local IPv6.
            "fc00::1", "fd12:3456::1", "fec0::1",
            // Multicast, broadcast, reserved and documentation ranges.
            "224.0.0.1", "239.255.255.250", "255.255.255.255", "240.0.0.1", "ff02::1",
            "192.0.2.1", "198.51.100.1", "203.0.113.1", "198.18.0.1", "192.0.0.1", "2001:db8::1",
            // IPv6 carrying a private IPv4 address: mapped, compatible, NAT64, 6to4; and Teredo.
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::10.0.0.1", "64:ff9b::a00:1", "64:ff9b::7f00:1", "2002:a00:1::1",
            "2002:7f00:1::1", "2001:0:4136:e378::1"
        )) {
            assertFalse(literal, isPublicAddress(ip(literal)))
        }
    }
}
