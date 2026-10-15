package com.pixel.intelligentsearch.core.security

import androidx.core.net.toUri
import android.content.Intent
import android.net.Uri
import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * The single gate for user-typed (or backup-restored) custom search addresses.
 *
 * A custom engine is a URL template such as `https://search.example/?q=%s`. Anything else that could be typed
 * here is refused rather than "fixed": other schemes (`javascript:`, `intent:`, `data:`, `file:`, `content:`,
 * `http:`), embedded credentials (`https://bank.com@evil.example`), control characters, quotes or angle brackets,
 * and a `%s` placed in the host. Hostnames are reduced to their ASCII (punycode) form so look-alike characters
 * can't hide the real site. Searches are re-validated every time a URL is built, so a value stored by an older
 * version or restored from a backup is never opened unchecked, and the result is launched only to browsable
 * activities (see [browsableViewIntent]).
 */
object CustomSearchUrl {

    const val QUERY_TOKEN = "%s"
    const val MAX_LENGTH = 2048

    sealed interface Validation {
        /** [template] is the canonical form to store: https, ASCII host. */
        data class Valid(val template: String, val host: String) : Validation
        data class Invalid(val reason: String) : Validation
    }

    private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HOST_PORT_PREFIX = Regex("^[^/:?#]+:\\d+([/?#].*)?$")
    private val LABEL = Regex("^(?!-)[a-z0-9-]{1,63}(?<!-)$")
    private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")
    private const val FORBIDDEN_CHARS = "<>\"'`\\^{}|"

    fun validate(raw: String): Validation {
        val input = raw.trim()
        if (input.isEmpty()) return Validation.Invalid("Enter a search address")
        if (input.length > MAX_LENGTH) return Validation.Invalid("Address is too long")
        if (input.any { it.isISOControl() || it.isWhitespace() || it in FORBIDDEN_CHARS }) {
            return Validation.Invalid("Address contains characters that aren't allowed")
        }

        val withScheme = when {
            input.contains("://") -> {
                if (!input.startsWith("https://", ignoreCase = true)) return Validation.Invalid("Only https:// addresses are allowed")
                "https://" + input.substring("https://".length)
            }
            // "javascript:...", "intent:...", "mailto:..." have a scheme but no "//". "example.com:8443/q" is a port.
            SCHEME_PREFIX.containsMatchIn(input) && !HOST_PORT_PREFIX.matches(input) ->
                return Validation.Invalid("Only https:// addresses are allowed")
            else -> "https://$input"
        }

        // Convert an international host to ASCII first: java.net.URI only parses ASCII hosts.
        val afterScheme = withScheme.substring("https://".length)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it == -1) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        if ('@' in authority) return Validation.Invalid("Addresses with a user name or password aren't allowed")
        val hostPart = authority.substringBeforeLast(':').takeIf { authority.substringAfterLast(':', "").all(Char::isDigit) && ':' in authority } ?: authority
        val asciiHostPart = try {
            IDN.toASCII(hostPart.replace(QUERY_TOKEN, "QUERYTOKEN"), IDN.USE_STD3_ASCII_RULES)
        } catch (_: Exception) {
            return Validation.Invalid("That website name isn't valid")
        }
        val asciiUrl = "https://" + asciiHostPart + authority.substring(hostPart.length) + afterScheme.substring(authorityEnd)

        // Parse with a stand-in for the query token; the token itself isn't legal URI syntax.
        val parsed = try {
            URI(asciiUrl.replace(QUERY_TOKEN, "QUERYTOKEN"))
        } catch (_: Exception) {
            return Validation.Invalid("That isn't a valid web address")
        }
        if (!"https".equals(parsed.scheme, ignoreCase = true)) return Validation.Invalid("Only https:// addresses are allowed")
        if (parsed.rawUserInfo != null) return Validation.Invalid("Addresses with a user name or password aren't allowed")
        val rawHost = parsed.host ?: return Validation.Invalid("The address needs a website name")
        if (rawHost.contains("QUERYTOKEN", ignoreCase = true)) return Validation.Invalid("Put %s after the website name, not in it")

        val host = try {
            IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: Exception) {
            return Validation.Invalid("That website name isn't valid")
        }
        val isIp = IPV4.matches(host)
        if (!isIp && (!host.contains('.') || host.split('.').any { !LABEL.matches(it) })) {
            return Validation.Invalid("That website name isn't valid")
        }

        // Rebuild from the parsed parts so the stored form is exactly what was checked.
        val port = if (parsed.port != -1) ":${parsed.port}" else ""
        val rest = withScheme.substringAfter("://").let { afterScheme ->
            val cut = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            if (cut == -1) "" else afterScheme.substring(cut)
        }
        return Validation.Valid(template = "https://$host$port$rest", host = host)
    }

    /**
     * The search URL for [query], or null if [template] fails validation. The query is percent-encoded, so it
     * can't add path segments, parameters or a fragment of its own.
     */
    fun build(template: String, query: String): Uri? {
        val valid = validate(template) as? Validation.Valid ?: return null
        val encoded = Uri.encode(query)
        val url = if (valid.template.contains(QUERY_TOKEN)) valid.template.replace(QUERY_TOKEN, encoded) else valid.template + encoded
        val uri = url.toUri()
        // Belt and braces: the built URL must still be https to the validated host.
        return uri.takeIf { it.scheme == "https" && it.host.equals(valid.host, ignoreCase = true) }
    }

    /** A short label for the engine from its host, e.g. "Startpage" for https://www.startpage.com/... */
    fun displayName(template: String): String? {
        val host = (validate(template) as? Validation.Valid)?.host ?: return null
        return host.removePrefix("www.").removePrefix("search.").substringBefore('.')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }.takeIf { it.isNotBlank() }
    }

    /**
     * An ACTION_VIEW intent restricted to browsable activities (what a browser link may open), so a crafted
     * address can't reach a non-browser activity that happens to accept the URL.
     */
    fun browsableViewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    /** Cleans a user-typed engine name for display: no control characters, at most 40 characters. */
    fun sanitizeEngineName(raw: String): String = raw.filterNot { it.isISOControl() }.take(40)
}
