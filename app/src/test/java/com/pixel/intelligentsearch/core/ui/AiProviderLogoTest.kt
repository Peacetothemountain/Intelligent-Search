package com.pixel.intelligentsearch.core.ui

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.ai.AiProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every AI provider has its own official logo, which loads; only OpenAI's one-colour mark takes the text colour. */
@RunWith(RobolectricTestRunner::class)
class AiProviderLogoTest {

    @Test
    fun everyProviderHasItsOwnLogoThatLoads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logos = AiProvider.entries.map(::aiProviderLogo)
        assertEquals("one logo per provider", logos.size, logos.toSet().size)
        for (provider in AiProvider.entries) {
            val drawable = ContextCompat.getDrawable(context, aiProviderLogo(provider))
            assertTrue("$provider's logo is a vector", drawable is VectorDrawable)
        }
    }

    @Test
    fun onlyOpenAisMarkIsTintedToTheText() {
        assertEquals(listOf(AiProvider.OPENAI), AiProvider.entries.filter(::aiProviderLogoIsMonochrome))
    }

    @Test
    fun theRevealTracesEachLogosOwnOutline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (provider in AiProvider.entries) {
            val outline = logoOutline(context.resources, aiProviderLogo(provider))
            assertTrue("$provider's outline is read from its drawable", outline != null && !outline.isEmpty)
        }
    }

    @Test
    fun theSparksAndThePillAreInTheBrandsColours() {
        assertEquals(listOf(Color(0xFFD97757), Color(0xFFF0A48A)), aiProviderBrandColors(AiProvider.ANTHROPIC))
        // Gemini's sparkle: blue with its green, red and yellow.
        assertEquals(4, aiProviderBrandColors(AiProvider.GEMINI).size)
        assertTrue(aiProviderBrandColors(AiProvider.OPENAI).isEmpty())
        val base = Color(0xFF334455)
        assertEquals(lerp(base, Color(0xFFD97757), BRAND_TINT), aiProviderPillColor(AiProvider.ANTHROPIC, base))
        assertEquals("OpenAI keeps the theme's own", base, aiProviderPillColor(AiProvider.OPENAI, base))
    }

    @Test
    fun theSparksFlyOutEvenlyAndSlowAsTheyGo() {
        val centre = Offset(50f, 50f)
        for (k in 0 until LOGO_SPARKS) {
            val start = sparkPosition(0f, k, LOGO_SPARKS, centre, 10f)
            val mid = sparkPosition(0.5f, k, LOGO_SPARKS, centre, 10f)
            val end = sparkPosition(1f, k, LOGO_SPARKS, centre, 10f)
            val d0 = (start - centre).getDistance()
            val d1 = (mid - centre).getDistance()
            val d2 = (end - centre).getDistance()
            assertTrue("spark $k leaves the logo's edge and flies out", d0 < d1 && d1 < d2 && d0 >= 6.9f)
            assertTrue("and slows as it goes", d1 - d0 > d2 - d1)
        }
        assertTrue(LOGO_TRACE_MILLIS + LOGO_BURST_DELAY_MILLIS < 1000)
    }
}
