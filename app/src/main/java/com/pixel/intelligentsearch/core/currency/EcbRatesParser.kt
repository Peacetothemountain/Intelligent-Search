package com.pixel.intelligentsearch.core.currency

import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/**
 * Reads the European Central Bank's daily reference rates (eurofxref-daily.xml): per-euro rates for about thirty
 * currencies and the day they were set. The file arrives over the network, so it is handled as hostile input: it
 * must be small, it may not contain a DTD (so there are no entities to expand or fetch), and only three-letter
 * upper-case codes with plausible rates are kept. Anything else makes the whole file, or that entry, unused.
 */
object EcbRatesParser {

    /** The daily file is under 2 KB; anything past this is not it. */
    const val MAX_BYTES = 256 * 1024

    /** How far after today a table may be dated (time zones and a clock a little behind); later is not a real one. */
    const val MAX_DAYS_AHEAD = 2L

    /** The ECB publishes about thirty; more than this is not its file either. */
    private const val MAX_RATES = 200

    /** Per-euro rates run from about 0.3 to about 20,000; outside these no currency is worth converting with. */
    private const val MIN_RATE = 1e-6
    private const val MAX_RATE = 1e7

    /** U+FEFF, which some servers put before the XML declaration. */
    private val BYTE_ORDER_MARK = 0xFEFF.toChar()

    private val CODE = Regex("^[A-Z]{3}$")
    private val RATE = Regex("^\\d{1,12}(?:\\.\\d{1,12})?$")

    /** Parser features that turn off DTDs and external entities where the platform's parser knows them. */
    private val HARDENING = listOf(
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
    )

    /**
     * The rates in [body], or null when it isn't a well-formed, plausible daily file. A table dated more than
     * [MAX_DAYS_AHEAD] days after [today] is refused: kept, it would hold off every real update until then.
     */
    fun parse(body: ByteArray, today: LocalDate = LocalDate.now()): CurrencyRates? = parse(body, today, HARDENING)

    /** [parse] with the given parser [features]; tests turn them off to show [textToParse] stands on its own. */
    internal fun parse(body: ByteArray, today: LocalDate, features: List<Pair<String, Boolean>>): CurrencyRates? {
        val text = textToParse(body) ?: return null

        val handler = CubeHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
            for ((feature, value) in features) runCatching { factory.setFeature(feature, value) }
            val reader = factory.newSAXParser().xmlReader
            // Nothing outside the document is ever read, whatever it refers to.
            reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(StringReader(text)))
        } catch (_: Exception) {
            return null
        }

        val date = handler.date ?: return null
        if (date > today.plusDays(MAX_DAYS_AHEAD)) return null
        if (handler.rates.isEmpty()) return null
        return CurrencyRates(base = "eur", perBase = handler.rates + ("eur" to 1.0), date = date)
    }

    /**
     * [body] as the text a parser may be given, or null when it must not be parsed at all. This is the defence
     * against DTDs and entities: Android's parser ignores the [HARDENING] features, so a declaration is refused here,
     * before any parser sees the text. The body must be UTF-8 (a UTF-16 or UTF-32 file could spell a DTD this check
     * can't read) without NUL characters, and may hold no `<!` other than a comment or CDATA section, which refuses
     * `<!DOCTYPE` and `<!ENTITY` however they are spelled or spaced. The parser gets these characters, never the bytes,
     * so an encoding the file declares can't change what it reads.
     */
    internal fun textToParse(body: ByteArray): String? {
        if (body.isEmpty() || body.size > MAX_BYTES) return null
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        val text = decoded.removePrefix(BYTE_ORDER_MARK.toString())
        if ('\u0000' in text) return null
        var at = text.indexOf("<!")
        while (at >= 0) {
            if (!text.startsWith("<!--", at) && !text.startsWith("<![CDATA[", at)) return null
            at = text.indexOf("<!", at + 2)
        }
        return text
    }

    /** [text] as a rate when it is plain digits ("1.0812", no sign or exponent) within a plausible range. */
    internal fun rateOrNull(text: String): Double? {
        if (!RATE.matches(text)) return null
        val rate = text.toDouble()
        return rate.takeIf { it.isFinite() && it in MIN_RATE..MAX_RATE }
    }

    /** Collects `<Cube time="…">` and `<Cube currency="…" rate="…"/>`, the only elements that carry data. */
    private class CubeHandler : DefaultHandler() {
        var date: LocalDate? = null
        val rates = LinkedHashMap<String, Double>()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = localName?.ifEmpty { null } ?: qName
            if (name != "Cube") return
            attributes.getValue("time")?.let { time ->
                if (date == null) date = runCatching { LocalDate.parse(time.trim()) }.getOrNull() ?: throw SAXException("Unreadable date")
            }
            val code = attributes.getValue("currency")?.trim() ?: return
            val rateText = attributes.getValue("rate")?.trim() ?: return
            if (!CODE.matches(code) || code == "EUR") return
            val rate = rateOrNull(rateText) ?: return
            if (rates.size >= MAX_RATES) throw SAXException("Too many rates")
            rates.putIfAbsent(code.lowercase(Locale.ROOT), rate)
        }

        // Warnings are ignored; errors end the parse rather than being skipped over.
        override fun error(e: org.xml.sax.SAXParseException) = throw e
        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
    }
}
