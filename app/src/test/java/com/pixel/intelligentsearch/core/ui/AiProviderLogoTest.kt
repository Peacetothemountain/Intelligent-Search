package com.pixel.intelligentsearch.core.ui

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.ai.AiProvider
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
    fun eachLogoHasItsOwnFlourishWhenPicked() {
        val flourishes = AiProvider.entries.map(::logoFlourish)
        assertEquals("each provider moves in its own way", flourishes.size, flourishes.toSet().size)
        assertEquals(LogoFlourish.Spin, logoFlourish(AiProvider.ANTHROPIC))
        assertEquals(LogoFlourish.Turn, logoFlourish(AiProvider.OPENAI))
        assertEquals(LogoFlourish.Twinkle, logoFlourish(AiProvider.GEMINI))
        // Short enough to finish before the next tap, and every one ends upright at its own size.
        assertTrue(LOGO_FLOURISH_MILLIS in 300..800)
        for (f in LogoFlourish.entries) assertTrue(f.turnDegrees > 0f && f.fromScale in 0.1f..1f)
    }
}
