package com.pixel.intelligentsearch.core.currency

import com.pixel.intelligentsearch.core.currency.EcbRatesParserTest.Companion.ecbFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.LocalDate
import javax.xml.parsers.SAXParserFactory

/**
 * The text check that runs before any XML parser. On Android it is the defence against DTDs and entities, because
 * Android's parser ignores the hardening features; the JVM's parser honours them, so these tests call the check
 * directly and parse with the features off, the way Android would.
 */
class EcbRatesPreCheckTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun check(text: String) = EcbRatesParser.textToParse(text.toByteArray(Charsets.UTF_8))

    /** The daily file with [declaration] after its XML declaration. */
    private fun withDeclaration(declaration: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$declaration\n" + ecbFile(prolog = false)

    @Test
    fun theDailyFilePasses() {
        assertEquals(ecbFile(), check(ecbFile()))
    }

    @Test
    fun aDoctypeOrEntityIsRefusedHoweverItIsWritten() {
        for (declaration in listOf(
            "<!DOCTYPE gesmes:Envelope>",
            "<!DOCTYPE gesmes:Envelope SYSTEM \"https://attacker.example/evil.dtd\">",
            "<!DOCTYPE x [<!ENTITY rate \"1.5\">]>",
            "<!doctype x>",
            "<!DocType x>",
            "<!ENTITY rate \"1.5\">",
            "<!entity rate \"1.5\">",
            "<!Entity % p SYSTEM \"https://attacker.example/p.dtd\">",
            "<! DOCTYPE x>",
            "<!\nDOCTYPE x>",
            "<!\tENTITY rate \"1.5\">",
            "<!\r\n  doctype x>",
            "<!ELEMENT x ANY>",
            "<!ATTLIST x y CDATA #IMPLIED>",
            "<!NOTATION n SYSTEM \"n\">",
            "<!"
        )) {
            assertNull(declaration, check(withDeclaration(declaration)))
        }
        // Anywhere in the file, not only at the top.
        assertNull(check(ecbFile().replace("<Cube>", "<Cube><!DOCTYPE x>")))
    }

    @Test
    fun commentsAndCdataAreNotDeclarations() {
        assertNotNull(check(withDeclaration("<!-- published by the ECB -->")))
        assertNotNull(check(ecbFile().replace("<gesmes:subject>Reference rates", "<gesmes:subject><![CDATA[Reference rates]]>")))
        // But a declaration hidden after one still counts.
        assertNull(check(withDeclaration("<!-- note --><!DOCTYPE x>")))
    }

    @Test
    fun aByteOrderMarkIsDroppedAndHidesNothing() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(ecbFile(), EcbRatesParser.textToParse(bom + ecbFile().toByteArray()))
        assertNull(EcbRatesParser.textToParse(bom + withDeclaration("<!DOCTYPE x [<!ENTITY e \"1\">]>").toByteArray()))
        assertNull(EcbRatesParser.textToParse(bom + "<!DOCTYPE x>".toByteArray() + ecbFile(prolog = false).toByteArray()))
    }

    @Test
    fun otherEncodingsAreRefusedSoTheyCanNotHideADeclaration() {
        val hostile = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><!DOCTYPE x [<!ENTITY e \"1\">]>" + ecbFile(prolog = false)
        for (charset in listOf(Charsets.UTF_16, Charsets.UTF_16BE, Charsets.UTF_16LE, Charsets.UTF_32, Charsets.UTF_32BE, Charsets.UTF_32LE)) {
            assertNull(charset.name(), EcbRatesParser.textToParse(hostile.toByteArray(charset)))
            assertNull(charset.name(), EcbRatesParser.textToParse(ecbFile().toByteArray(charset)))
        }
        // Bytes that aren't UTF-8 at all, and a NUL character.
        assertNull(EcbRatesParser.textToParse(byteArrayOf(0xC3.toByte(), 0x28) + ecbFile().toByteArray()))
        assertNull(check(ecbFile().replace("<Cube>", "<Cube>\u0000")))
    }

    @Test
    fun emptyAndOversizedBodiesAreRefused() {
        assertNull(EcbRatesParser.textToParse(ByteArray(0)))
        assertNull(EcbRatesParser.textToParse(ByteArray(EcbRatesParser.MAX_BYTES + 1) { ' '.code.toByte() }))
    }

    @Test
    fun withTheParserHardeningOffADeclarationIsStillNeverParsed() {
        val entity = withDeclaration("<!DOCTYPE gesmes:Envelope [<!ENTITY rate \"1.5\">]>")
            .replace("<Cube currency='USD' rate='1.0812'/>", "<Cube currency='USD' rate='&rate;'/>")
        // A parser without the features expands the entity, as Android's does...
        assertEquals("1.5", usdRateFromAPlainParser(entity))
        // ...so the check alone keeps the file out.
        assertNull(EcbRatesParser.parse(entity.toByteArray(), today, features = emptyList()))
        assertNull(EcbRatesParser.parse(entity.toByteArray(), today))
        // While the daily file still parses without them.
        assertEquals(1.0812, EcbRatesParser.parse(ecbFile().toByteArray(), today, features = emptyList())!!.perBase.getValue("usd"), 0.0)
    }

    /** The USD rate attribute as a parser with no hardening reads it. */
    private fun usdRateFromAPlainParser(xml: String): String? {
        var rate: String? = null
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        factory.newSAXParser().parse(InputSource(StringReader(xml)), object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                if (attributes.getValue("currency") == "USD") rate = attributes.getValue("rate")
            }
        })
        return rate
    }
}
