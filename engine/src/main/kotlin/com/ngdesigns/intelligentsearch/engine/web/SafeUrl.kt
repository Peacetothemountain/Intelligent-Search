package com.ngdesigns.intelligentsearch.engine.web

import java.net.IDN
import java.net.URI
import java.net.URLEncoder

/**
 * The single gate for web addresses the user types or a backup restores: custom search engines and custom web
 * shortcuts. A template is an https address with `%s` where the query goes. Anything else is refused rather than
 * "fixed": other schemes (javascript:, intent:, file:, data:), plain http, user info that disguises the real host
 * (`google.com@evil.example`), quotes, angle brackets, spaces or line breaks, a `%s` inside the host, local hosts
 * and IP literals. International hosts are shown in punycode, so a look-alike letter cannot pass for a real site.
 */
object SafeUrl {
    const val MAX_LENGTH = 2048

    data class Template(val template: String, val host: String)

    fun template(input: String): Template? = check(input, placeholder = true)

    /** A plain https address to open (a widget or shortcut link), under the same rules as [template] minus the %s. */
    fun address(input: String): String? = check(input, placeholder = false)?.template

    private fun check(input: String, placeholder: Boolean): Template? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        if (trimmed.any { it.isWhitespace() || it.isISOControl() || it in "\"'<>`\\{}|^" }) return null
        val schemeEnd = trimmed.indexOf("://")
        val withScheme = when {
            // javascript:, mailto: (a colon before any slash that is not a port number)
            schemeEnd < 0 && Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:(?![0-9])").containsMatchIn(trimmed) -> return null
            schemeEnd < 0 -> "https://$trimmed"
            trimmed.substring(0, schemeEnd).equals("https", ignoreCase = true) -> "https://" + trimmed.substring(schemeEnd + 3)
            else -> return null
        }
        if (placeholder != withScheme.contains("%s")) return null
        val authority = withScheme.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty() || authority.contains('@') || authority.contains("%")) return null
        val hostPart = authority.substringBefore(':')
        val port = authority.substringAfter(':', "")
        if (port.isNotEmpty() && (port.toIntOrNull() == null || port.toInt() !in 1..65535)) return null
        val ascii = try { IDN.toASCII(hostPart, IDN.USE_STD3_ASCII_RULES).lowercase() } catch (_: IllegalArgumentException) { return null }
        if (!ascii.contains('.') || ascii == "localhost" || ascii.endsWith(".localhost") || ascii.endsWith(".local")) return null
        if (Regex("^[0-9.]+$").matches(ascii) || ascii.startsWith("[")) return null
        val rest = withScheme.removePrefix("https://").substring(authority.length)
        val normalized = "https://" + ascii + (if (port.isNotEmpty()) ":$port" else "") + rest
        // The address must parse (once filled in, for a template) and stay on the same host.
        val probe = build(normalized, "probe", checked = false) ?: return null
        if (probe.host != ascii) return null
        return Template(normalized, ascii)
    }

    /** The template filled with [query] (percent-encoded, spaces as %20), or null if the template is unsafe. */
    fun build(template: String, query: String): URI? = build(template, query, checked = true)

    private fun build(template: String, query: String, checked: Boolean): URI? {
        val t = if (checked) template(template)?.template ?: return null else template
        val encoded = URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20")
        return try { URI(t.replace("%s", encoded)) } catch (_: Exception) { null }
    }

    /** A short display name for a template's site: "Startpage" for www.startpage.com. */
    fun displayName(template: String): String {
        val host = template(template)?.host ?: return "Custom"
        val label = host.removePrefix("www.").removePrefix("search.").substringBefore('.')
        return label.replaceFirstChar { it.uppercase() }
    }
}
