package com.pixel.intelligentsearch.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ai.AiProvider

/** [provider]'s official mark: Claude's starburst, OpenAI's knot, Gemini's sparkle. */
@DrawableRes
internal fun aiProviderLogo(provider: AiProvider): Int = when (provider) {
    AiProvider.ANTHROPIC -> R.drawable.ic_logo_claude
    AiProvider.OPENAI -> R.drawable.ic_logo_openai
    AiProvider.GEMINI -> R.drawable.ic_logo_gemini
}

/** Whether [provider]'s mark is one colour, drawn in the text beside it (OpenAI's); the others keep their brand colours. */
internal fun aiProviderLogoIsMonochrome(provider: AiProvider): Boolean = provider == AiProvider.OPENAI

/**
 * [provider]'s official logo at [size]: in its own colours, or (OpenAI's one-colour mark) in the current content
 * colour, so it reads in the light and dark theme and on a selected pill alike. Decorative: the name beside it is
 * what TalkBack reads.
 */
@Composable
fun AiProviderLogo(provider: AiProvider, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val painter = painterResource(aiProviderLogo(provider))
    if (aiProviderLogoIsMonochrome(provider)) {
        Icon(painter, contentDescription = null, tint = LocalContentColor.current, modifier = modifier.size(size))
    } else {
        Image(painter, contentDescription = null, modifier = modifier.size(size))
    }
}
