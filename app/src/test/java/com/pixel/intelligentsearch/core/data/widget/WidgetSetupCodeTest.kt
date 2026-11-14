package com.pixel.intelligentsearch.core.data.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A widget's look goes into a code and comes back out the same, and a code that isn't one the app made is refused. */
@RunWith(RobolectricTestRunner::class)
class WidgetSetupCodeTest {

    private lateinit var prefs: SharedPreferences
    private val defaults = WidgetDefaults(primaryColor = 0xFF6750A4.toInt(), isDynamicColorActive = true)

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("widget_setup_code_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun stock(design: WidgetDesign) = WidgetSettingsStore(prefs).load(design, defaults)

    /** A design far from the defaults in every field. */
    private fun custom(design: WidgetDesign): WidgetDesignSettings = stock(design).copy(
        subtheme = "Custom",
        gIconTheme = "Custom G Icon",
        elementColors = WidgetElementColors(0xFF112233.toInt(), 0xFF223344.toInt(), 0xFF334455.toInt(), 0xFF445566.toInt(), 0xFF556677.toInt(), 0xFF667788.toInt()),
        syncColors = true,
        keepSystemG = true,
        hue = 123f,
        saturation = 45f,
        lightness = 67f,
        colorOpacity = 89f,
        transparency = 12f,
        showGIcon = false,
        showVoice = false,
        shortcut1 = "Weather",
        shortcut2 = "Calculator",
        shortcut3 = "News",
        slotOrder = "mic,shortcut3,shortcut2,shortcut1",
        lockBlack = design != WidgetDesign.MATERIAL,
        actionIcon = if (design == WidgetDesign.MATERIAL) "Now Playing" else "Search",
        showDoodles = false,
        outlineThickness = 4,
        cornerRadius = 12
    )

    private fun mine() = WidgetProfileDesign(WidgetDesign.SYSTEM, stock(WidgetDesign.SYSTEM), stock(WidgetDesign.MATERIAL))

    /** A code holding [json], packed and checked as the app's own are. */
    private fun codeFor(json: String): String {
        val deflater = Deflater().apply { setInput(json.toByteArray()); finish() }
        val buffer = ByteArray(8192)
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.copyOf(deflater.deflate(buffer)))
        val check = CRC32().apply { update(body.toByteArray()) }.value.toString(36).padStart(7, '0')
        return "IS1.$body.$check"
    }

    @Test
    fun aLookComesBackOutOfItsCodeTheSame() {
        for (design in WidgetDesign.entries) {
            val shared = custom(design)
            val theirs = WidgetProfileDesign(design, custom(WidgetDesign.SYSTEM), custom(WidgetDesign.MATERIAL))
            val got = WidgetSetupCode.decode(WidgetSetupCode.encode(theirs), mine(), defaults)!!
            assertEquals(design, got.design)
            assertEquals(shared, got.settings(design))
        }
    }

    @Test
    fun theOtherDesignStaysTheReceiversOwn() {
        val theirs = WidgetProfileDesign(WidgetDesign.MATERIAL, custom(WidgetDesign.SYSTEM), custom(WidgetDesign.MATERIAL))
        val got = WidgetSetupCode.decode(WidgetSetupCode.encode(theirs), mine(), defaults)!!
        assertEquals(mine().system, got.system)
    }

    @Test
    fun aCodeIsFoundInsideAMessageAndIsShortEnoughToSend() {
        val code = WidgetSetupCode.encode(WidgetProfileDesign(WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM), stock(WidgetDesign.MATERIAL)))
        assertTrue("was ${code.length} long", code.length < 700)
        assertNotNull(WidgetSetupCode.decode("Try my widget!\n$code\nsent from my phone", mine(), defaults))
    }

    @Test
    fun aButtonForTheSharersOwnAppIsNotShared() {
        val withOwnApp = custom(WidgetDesign.SYSTEM).copy(shortcut2 = "Custom App")
        val code = WidgetSetupCode.encode(WidgetProfileDesign(WidgetDesign.SYSTEM, withOwnApp, stock(WidgetDesign.MATERIAL)))
        val got = WidgetSetupCode.decode(code, mine(), defaults)!!
        assertEquals("None", got.system.shortcut2)
        assertEquals("Weather", got.system.shortcut1)
    }

    @Test
    fun aDamagedCodeIsRefused() {
        val code = WidgetSetupCode.encode(WidgetProfileDesign(WidgetDesign.SYSTEM, custom(WidgetDesign.SYSTEM), stock(WidgetDesign.MATERIAL)))
        val middle = code.length / 2
        val swapped = code.substring(0, middle) + (if (code[middle] == 'A') 'B' else 'A') + code.substring(middle + 1)
        assertNull(WidgetSetupCode.decode(swapped, mine(), defaults))
        assertNull(WidgetSetupCode.decode(code.dropLast(4), mine(), defaults))
        assertNull(WidgetSetupCode.decode("", mine(), defaults))
        assertNull(WidgetSetupCode.decode("just some words", mine(), defaults))
        assertNull(WidgetSetupCode.decode("IS1.AAAAAAAAAAAA.0000000", mine(), defaults))
    }

    @Test
    fun valuesTheEditorCouldNotHaveSavedRefuseTheCode() {
        fun refused(values: String) = WidgetSetupCode.decode(codeFor("""{"design":"SYSTEM","values":{$values}}"""), mine(), defaults) == null
        // The plain case is accepted, so each refusal below is for its one bad value.
        assertFalse(refused(""""corner_radius":12,"shortcut_1":"Weather""""))
        assertTrue(refused(""""corner_radius":900"""))
        assertTrue(refused(""""outline_thickness":-3"""))
        assertTrue(refused(""""custom_hue":720"""))
        assertTrue(refused(""""custom_color_opacity":"lots""""))
        assertTrue(refused(""""show_voice":"yes""""))
        assertTrue(refused(""""shortcut_1":"Custom App""""))
        assertTrue(refused(""""shortcut_order":"mic,mic,mic,mic""""))
        assertTrue(refused(""""subtheme":"<script>""""))
        assertTrue(refused(""""subtheme":"${"x".repeat(200)}""""))
        assertTrue(refused(""""custom_hue":{"a":1}"""))
    }

    @Test
    fun anUnknownDesignOrShapeIsRefusedAndUnknownNamesAreIgnored() {
        assertNull(WidgetSetupCode.decode(codeFor("""{"design":"NEON","values":{}}"""), mine(), defaults))
        assertNull(WidgetSetupCode.decode(codeFor("""{"design":"SYSTEM"}"""), mine(), defaults))
        assertNull(WidgetSetupCode.decode(codeFor("""["SYSTEM"]"""), mine(), defaults))
        val got = WidgetSetupCode.decode(codeFor("""{"design":"SYSTEM","values":{"corner_radius":9,"search_contacts":true}}"""), mine(), defaults)!!
        assertEquals(9, got.system.cornerRadius)
    }

    @Test
    fun aPasteTooLongToBeACodeIsNotRead() {
        val code = WidgetSetupCode.encode(mine())
        assertNull(WidgetSetupCode.decode("x".repeat(WidgetSetupCode.MAX_TEXT_LENGTH) + code, mine(), defaults))
    }
}
