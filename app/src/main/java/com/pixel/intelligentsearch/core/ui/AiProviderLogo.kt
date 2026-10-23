package com.pixel.intelligentsearch.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.ai.AiProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** [provider]'s official mark: Claude's starburst, OpenAI's knot, Gemini's sparkle. */
@DrawableRes
internal fun aiProviderLogo(provider: AiProvider): Int = when (provider) {
    AiProvider.ANTHROPIC -> R.drawable.ic_logo_claude
    AiProvider.OPENAI -> R.drawable.ic_logo_openai
    AiProvider.GEMINI -> R.drawable.ic_logo_gemini
}

/** Whether [provider]'s mark is one colour, drawn in the text beside it (OpenAI's); the others keep their brand colours. */
internal fun aiProviderLogoIsMonochrome(provider: AiProvider): Boolean = provider == AiProvider.OPENAI

/** How each logo moves as it is picked, in its own character. */
internal enum class LogoFlourish(val turnDegrees: Float, val fromScale: Float) {
    /** Claude's starburst spins half a turn and springs into place. */
    Spin(turnDegrees = 180f, fromScale = 0.7f),

    /** OpenAI's knot makes one smooth full turn. */
    Turn(turnDegrees = 360f, fromScale = 0.85f),

    /** Gemini's sparkle twinkles: it pops in from small, past its size and back, with a quarter turn. */
    Twinkle(turnDegrees = 90f, fromScale = 0.3f)
}

internal fun logoFlourish(provider: AiProvider): LogoFlourish = when (provider) {
    AiProvider.ANTHROPIC -> LogoFlourish.Spin
    AiProvider.OPENAI -> LogoFlourish.Turn
    AiProvider.GEMINI -> LogoFlourish.Twinkle
}

/** How long a logo's flourish takes, at most. */
internal const val LOGO_FLOURISH_MILLIS = 650

/**
 * [provider]'s official logo at [size]: in its own colours, or (OpenAI's one-colour mark) in the current content
 * colour, so it reads in the light and dark theme and on a selected pill alike. Each time it becomes [selected] (not
 * when first shown) it plays its own [LogoFlourish], unless animations are off. Decorative: the name beside it is what
 * TalkBack reads.
 */
@Composable
fun AiProviderLogo(provider: AiProvider, modifier: Modifier = Modifier, size: Dp = 20.dp, selected: Boolean = false) {
    val painter = painterResource(aiProviderLogo(provider))
    val animate by rememberAnimationsEnabled()
    val flourish = logoFlourish(provider)
    val turn = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    // Only a change to selected plays it, not the first time the logo is drawn already selected.
    val shownSelected = remember { booleanArrayOf(selected) }
    LaunchedEffect(selected, animate) {
        val becameSelected = selected && !shownSelected[0]
        shownSelected[0] = selected
        if (!becameSelected || !animate) return@LaunchedEffect
        turn.snapTo(-flourish.turnDegrees)
        scale.snapTo(flourish.fromScale)
        coroutineScope {
            launch {
                when (flourish) {
                    LogoFlourish.Spin -> turn.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
                    else -> turn.animateTo(0f, tween(LOGO_FLOURISH_MILLIS, easing = FastOutSlowInEasing))
                }
            }
            launch {
                when (flourish) {
                    LogoFlourish.Twinkle -> scale.animateTo(1f, keyframes {
                        durationMillis = LOGO_FLOURISH_MILLIS
                        1.2f at (LOGO_FLOURISH_MILLIS * 0.55f).toInt()
                    })
                    else -> scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
                }
            }
        }
    }
    val moving = Modifier.graphicsLayer {
        rotationZ = turn.value
        scaleX = scale.value
        scaleY = scale.value
    }
    if (aiProviderLogoIsMonochrome(provider)) {
        Icon(painter, contentDescription = null, tint = LocalContentColor.current, modifier = modifier.size(size).then(moving))
    } else {
        Image(painter, contentDescription = null, modifier = modifier.size(size).then(moving))
    }
}
