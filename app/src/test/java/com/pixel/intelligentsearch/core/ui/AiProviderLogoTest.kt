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
}
