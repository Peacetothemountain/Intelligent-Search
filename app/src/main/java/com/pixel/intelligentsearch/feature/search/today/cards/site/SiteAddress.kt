package com.pixel.intelligentsearch.feature.search.today.cards.site

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

/*
 * The website reader's two gates: what a typed (or redirected-to) address may be, and which network addresses a site
 * may be read from. Both are pure, so the security rules are tested without a network or a phone.
 *
 * NG Designs
 */

/** A web address the reader may open: always `https://[host][pathAndQuery]`, the host lowercase ASCII (punycode). */
internal data class SiteAddress(val host: String, val pathAndQuery: String) {
    /** The whole address, as cards store it. */
    val url: String get() = "https://$host$pathAndQuery"
}

/** Why an address was turned away. The reader turns each into a plain message for the user. */
internal enum class SiteRefusal {
    /** Nothing typed, or nothing that reads as a website address. */
    NOT_AN_ADDRESS,

    /** http, or any scheme but https. */
    NOT_SECURE,

    /** A local, private or reserved name or network address. */
    NOT_PUBLIC
}

/** What [normalizeSiteAddress] made of the input: the address, or why it was refused. */
internal sealed interface SiteAddressResult {
    data class Ok(val address: SiteAddress) : SiteAddressResult
    data class Refused(val reason: SiteRefusal) : SiteAddressResult
}

private const val MAX_ADDRESS_LENGTH = 2048
private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
private val HOST_PORT = Regex("^[^/:?#]+:\\d+([/?#].*)?$")
private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
private val NUMERIC_TLD = Regex("^[0-9]+$")
private const val FORBIDDEN_CHARS = "<>\"'`\\^{}|"

/** Names that only ever mean this phone or a home or company network, never a public site. */
private val PRIVATE_SUFFIXES = listOf(".local", ".internal", ".localhost", ".home.arpa", ".lan", ".intranet", ".corp")

/**
 * Turns what the user typed ("weekend.recipes", "https://Example.com/page") into the one https address the reader
 * opens, or refuses it. The rules are strict on purpose, so nothing typed can point the reader somewhere it shouldn't:
 * - https only: no scheme gets https added; http and every other scheme (`javascript:`, `file:`, `intent:`) are refused,
 *   not rewritten.
 * - The host is lowercased and reduced to ASCII (international names become punycode), so look-alike letters can't
 *   hide the real site. It needs at least one dot, and names that only mean this phone or a local network
 *   ("localhost", ".local", ".internal", single words) are refused before any lookup.
 * - Network addresses typed as hosts (IPv4 or IPv6) are refused: a website card is for a named site.
 * - A user name or password in the address is refused, as is any port but 443.
 * - The fragment is dropped; any character a URL can't carry in the path or query is percent-encoded as UTF-8.
 *
 * The same check runs on every redirect target, given as a whole URL.
 */
internal fun normalizeSiteAddress(raw: String): SiteAddressResult {
    val input = raw.trim()
    if (input.isEmpty() || input.length > MAX_ADDRESS_LENGTH) return refused(SiteRefusal.NOT_AN_ADDRESS)
    if (input.any { it.isISOControl() || it.isWhitespace() || it in FORBIDDEN_CHARS }) return refused(SiteRefusal.NOT_AN_ADDRESS)

    val afterScheme = when {
        input.contains("://") -> {
            val scheme = input.substringBefore("://")
            if (!SCHEME.matches("$scheme:")) return refused(SiteRefusal.NOT_AN_ADDRESS)
            if (!scheme.equals("https", ignoreCase = true)) return refused(SiteRefusal.NOT_SECURE)
            input.substring(scheme.length + 3)
        }
        // "javascript:...", "mailto:..." and "http:example.com" have a scheme but no "//"; "example.com:443/x" is a port.
        SCHEME.containsMatchIn(input) && !HOST_PORT.matches(input) -> return refused(SiteRefusal.NOT_SECURE)
        input.startsWith("//") -> input.substring(2)
        else -> input
    }

    val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it == -1) afterScheme.length else it }
    val authority = afterScheme.substring(0, authorityEnd)
    val rest = afterScheme.substring(authorityEnd).substringBefore('#')
    if ('@' in authority || authority.startsWith("[")) return refused(SiteRefusal.NOT_AN_ADDRESS)

    val rawHost = if (':' in authority) {
        val port = authority.substringAfterLast(':')
        // Only the https port itself (or an empty one, "example.com:/") is allowed.
        if (port.isNotEmpty() && port.trimStart('0') != "443") return refused(SiteRefusal.NOT_AN_ADDRESS)
        authority.substringBeforeLast(':')
    } else {
        authority
    }
    val host = asciiHost(rawHost) ?: return refused(SiteRefusal.NOT_AN_ADDRESS)
    hostRefusal(host)?.let { return refused(it) }

    val encoded = encodePathAndQuery(rest)
    val pathAndQuery = if (encoded.isEmpty() || encoded.startsWith("?")) "/$encoded" else encoded
    // A last parse: the rebuilt address must be a valid URI with exactly this host.
    val parsed = runCatching { URI("https://$host$pathAndQuery") }.getOrNull() ?: return refused(SiteRefusal.NOT_AN_ADDRESS)
    if (parsed.host != host || parsed.rawUserInfo != null || parsed.port != -1) return refused(SiteRefusal.NOT_AN_ADDRESS)
    return SiteAddressResult.Ok(SiteAddress(host, pathAndQuery))
}

/**
 * [reference] (a redirect's Location, a page's image) resolved against the page at [base], then put through every rule
 * of [normalizeSiteAddress]: a link to http, another scheme or a local name is refused just as if it had been typed.
 */
internal fun resolveSiteAddress(base: SiteAddress, reference: String): SiteAddressResult {
    val ref = reference.trim()
    if (ref.isEmpty() || ref.length > MAX_ADDRESS_LENGTH || ref.any { it.isISOControl() }) return refused(SiteRefusal.NOT_AN_ADDRESS)
    return when {
        ref.startsWith("//") -> normalizeSiteAddress("https:$ref")
        SCHEME.containsMatchIn(ref) -> normalizeSiteAddress(ref)
        else -> {
            val resolved = runCatching { URI(base.url).resolve(encodePathAndQuery(ref.substringBefore('#'))).toString() }.getOrNull()
            if (resolved == null) refused(SiteRefusal.NOT_AN_ADDRESS) else normalizeSiteAddress(resolved)
        }
    }
}

private fun refused(reason: SiteRefusal) = SiteAddressResult.Refused(reason)

/** [host] in lowercase ASCII, without a trailing dot, or null when it isn't a valid name. */
private fun asciiHost(host: String): String? {
    val trimmed = host.removeSuffix(".")
    if (trimmed.isEmpty()) return null
    val ascii = runCatching { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES) }.getOrNull() ?: return null
    return ascii.lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && it.length <= 253 }
}

/**
 * Why the name [host] (lowercase ASCII) may not be read, or null when it may: it must be a dotted DNS name of valid
 * labels, not an address written as numbers, and not a name that only means this phone or a private network. This is
 * checked before the lookup; the addresses it resolves to are checked by [isPublicAddress] after.
 */
internal fun hostRefusal(host: String): SiteRefusal? {
    if (host == "localhost" || PRIVATE_SUFFIXES.any { host.endsWith(it) }) return SiteRefusal.NOT_PUBLIC
    val labels = host.split('.')
    if (labels.any { !LABEL.matches(it) }) return SiteRefusal.NOT_AN_ADDRESS
    // A single word only ever names something on the local network.
    if (labels.size < 2) return SiteRefusal.NOT_PUBLIC
    // "10.0.0.1", "1.2.3", "0x7f.1": a top-level name of digits (or hex) is an address written as numbers, not a site.
    val top = labels.last()
    if (NUMERIC_TLD.matches(top) || top.startsWith("0x")) return SiteRefusal.NOT_AN_ADDRESS
    return null
}

/** [rest] (path and query) with every character a URL can't carry percent-encoded as UTF-8; existing escapes kept. */
private fun encodePathAndQuery(rest: String): String {
    val out = StringBuilder(rest.length)
    var i = 0
    while (i < rest.length) {
        val c = rest[i]
        when {
            c == '%' && i + 2 < rest.length && rest[i + 1].isHex() && rest[i + 2].isHex() -> out.append(c)
            c == '%' -> out.append("%25")
            c.code in 0x21..0x7E -> out.append(c)
            else -> {
                val end = if (Character.isHighSurrogate(c) && i + 1 < rest.length) i + 2 else i + 1
                for (b in rest.substring(i, end).toByteArray(Charsets.UTF_8)) out.append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
                i = end
                continue
            }
        }
        i++
    }
    return out.toString()
}

private const val HEX = "0123456789ABCDEF"
private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/**
 * Whether the reader may connect to [address]: only a public unicast address. Refused are this phone (loopback,
 * any-local, 0.0.0.0/8), the local network (10/8, 172.16/12, 192.168/16, link-local 169.254/16, carrier-grade NAT
 * 100.64/10, IPv6 unique-local fc00::/7, link-local fe80::/10 and the old site-local fec0::/10), multicast and broadcast,
 * and the ranges kept for documentation, benchmarks and future use. An IPv6 address that carries an IPv4 one (mapped,
 * compatible, NAT64 and 6to4) is judged by the IPv4 address inside it, and Teredo, whose inner address is hidden, is
 * refused. Pure: it looks only at the bytes, never at the network.
 */
internal fun isPublicAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress
    ) return false
    val bytes = address.address
    return when (address) {
        is Inet4Address -> isPublicIpv4(bytes)
        is Inet6Address -> isPublicIpv6(bytes)
        else -> false
    }
}

private fun isPublicIpv4(b: ByteArray): Boolean {
    if (b.size != 4) return false
    val a0 = b[0].toInt() and 0xFF
    val a1 = b[1].toInt() and 0xFF
    val a2 = b[2].toInt() and 0xFF
    return when {
        a0 == 0 -> false // "this network", 0.0.0.0
        a0 == 10 -> false
        a0 == 127 -> false
        a0 == 100 && a1 in 64..127 -> false // carrier-grade NAT
        a0 == 169 && a1 == 254 -> false
        a0 == 172 && a1 in 16..31 -> false
        a0 == 192 && a1 == 168 -> false
        a0 == 192 && a1 == 0 && a2 == 0 -> false // protocol assignments
        a0 == 192 && a1 == 0 && a2 == 2 -> false // documentation
        a0 == 198 && a1 in 18..19 -> false // benchmarking
        a0 == 198 && a1 == 51 && a2 == 100 -> false // documentation
        a0 == 203 && a1 == 0 && a2 == 113 -> false // documentation
        a0 >= 224 -> false // multicast, reserved, broadcast
        else -> true
    }
}

private fun isPublicIpv6(b: ByteArray): Boolean {
    if (b.size != 16) return false
    val first = b[0].toInt() and 0xFF
    val second = b[1].toInt() and 0xFF
    val word0 = (first shl 8) or second
    val word1 = ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
    fun zeros(from: Int, to: Int) = (from until to).all { b[it].toInt() == 0 }
    return when {
        first and 0xFE == 0xFC -> false // unique local fc00::/7
        first == 0xFE && second and 0xC0 == 0x80 -> false // link-local fe80::/10
        first == 0xFE && second and 0xC0 == 0xC0 -> false // old site-local fec0::/10
        first == 0xFF -> false // multicast
        // ::ffff:a.b.c.d (mapped) and ::a.b.c.d (compatible), when the platform hands them over as IPv6.
        zeros(0, 10) && (b[10].toInt() and 0xFF) == 0xFF && (b[11].toInt() and 0xFF) == 0xFF -> isPublicIpv4(b.copyOfRange(12, 16))
        zeros(0, 12) -> !zeros(12, 16) && isPublicIpv4(b.copyOfRange(12, 16))
        // NAT64 64:ff9b::/96 and 64:ff9b:1::/48: the IPv4 address in the last four bytes.
        word0 == 0x0064 && word1 == 0xFF9B -> isPublicIpv4(b.copyOfRange(12, 16))
        // 6to4 2002::/16: the IPv4 address in bytes 2 to 5.
        word0 == 0x2002 -> isPublicIpv4(b.copyOfRange(2, 6))
        word0 == 0x2001 && word1 == 0x0000 -> false // Teredo: the real address is hidden
        word0 == 0x2001 && word1 == 0x0DB8 -> false // documentation
        word0 == 0x0100 && zeros(2, 8) -> false // discard-only 100::/64
        first == 0x00 -> false // the rest of ::/8 is reserved
        else -> true
    }
}
