package com.pixel.intelligentsearch.core.currency

import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.LocalDate
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/**
 * Reads the European Central Bank's daily reference rates (eurofxref-daily.xml): per-euro rates for about thirty
 * currencies and the day they were set. The file arrives over the network, so it is handled as hostile input: it
 * must be small, it may not contain a DTD (so there are no entities to expand or fetch), and only three-letter
 * upper-case codes with positive, finite rates are kept. Anything else makes the whole file, or that entry, unused.
 */
object EcbRatesParser {

    /** The daily file is under 2 KB; anything past this is not it. */
    const val MAX_BYTES = 256 * 1024

    /** The ECB publishes about thirty; more than this is not its file either. */
    private const val MAX_RATES = 200

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

    /** The rates in [body], or null when it isn't a well-formed, plausible daily file. */
    fun parse(body: ByteArray): CurrencyRates? {
        if (body.isEmpty() || body.size > MAX_BYTES) return null
        val text = String(body, Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK.toString())
        // Not every Android parser honours the features below, so a DTD is refused before any parser sees the text.
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) return null

        val handler = CubeHandler()
        try {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
            for ((feature, value) in HARDENING) runCatching { factory.setFeature(feature, value) }
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
        if (handler.rates.isEmpty()) return null
        return CurrencyRates(base = "eur", perBase = handler.rates + ("eur" to 1.0), date = date)
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
            if (!CODE.matches(code) || code == "EUR" || !RATE.matches(rateText)) return
            val rate = rateText.toDouble()
            if (!rate.isFinite() || rate <= 0.0) return
            if (rates.size >= MAX_RATES) throw SAXException("Too many rates")
            rates.putIfAbsent(code.lowercase(Locale.ROOT), rate)
        }

        // Warnings are ignored; errors end the parse rather than being skipped over.
        override fun error(e: org.xml.sax.SAXParseException) = throw e
        override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
    }
}
