package com.pixel.intelligentsearch.core.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import org.intellij.lang.annotations.Language

/**
 * Whether the search bar's bottom light flows: only while the bar sits idle, with nothing typed and not listening.
 */
internal fun plateFlowsWhileIdle(query: String, listening: Boolean): Boolean = query.isEmpty() && !listening

/**
 * Lets the light along the bottom of [materialYouGeminiPlate] flow while the search bar sits [idle], with the motion
 * of the widget customization preview's light (its rippling bottom glow) but far subtler and slower. Nothing new is
 * lit: the plate's own bottom colour ([geminiPlateBottomLiftColor]) is drawn where that light already fades out, at
 * low strength, so the fading edge ripples sideways and gently breathes; at the very bottom, already that colour,
 * nothing changes. The plate itself is untouched. Draw it straight after the plate, inside the bar's clip.
 *
 * Fades in when the bar goes idle and out when typing or listening starts. Draws nothing with animations removed,
 * and stands still under severe thermal throttling and while the screen isn't resumed ([rememberMeshClock]). Time is
 * read only while drawing, so it never recomposes, and nothing is allocated per frame. Needs AGSL (API 33); below
 * that the plate is the drifting mesh already and this adds nothing. [alpha] is the plate's own alpha.
 */
@Composable
fun Modifier.geminiPlateIdleFlow(idle: Boolean, alpha: Float = 1f): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { runCatching { RuntimeShader(AGSL_PLATE_IDLE_FLOW) }.getOrNull() } ?: return this
    val brush = remember(shader) { ShaderBrush(shader) }
    val flow = rememberPlateIdleFlow(idle)
    val color = geminiPlateBottomLiftColor(MaterialTheme.colorScheme).toArgb()
    val plateAlpha = alpha.coerceIn(0f, 1f)

    return this.drawWithCache {
        shader.setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setColorUniform("uColor", color)
        onDrawBehind {
            val s = flow.strength
            if (s > 0f) {
                shader.setFloatUniform("uTime", flow.time)
                drawRect(brush = brush, alpha = s * plateAlpha)
            }
        }
    }
}

/** The idle flow's state, read while drawing: how strongly it shows (0 to 1) and its clock in seconds. */
internal class PlateIdleFlow(
    private val strengthState: State<Float>,
    private val timeState: State<Float>,
    private val animationsEnabled: State<Boolean>
) {
    /** 0 with animations removed, whatever the fade's state. */
    val strength: Float get() = if (animationsEnabled.value) strengthState.value else 0f
    val time: Float get() = timeState.value
}

/**
 * [geminiPlateIdleFlow]'s strength and clock: fading in over [IDLE_FLOW_FADE_IN_MS] while [idle] with animations on,
 * out over [IDLE_FLOW_FADE_OUT_MS] otherwise, and the clock running only while the flow shows (through the fade out,
 * so the light doesn't freeze as it goes).
 */
@Composable
internal fun rememberPlateIdleFlow(idle: Boolean): PlateIdleFlow {
    val animationsEnabled = rememberAnimationsEnabled()
    val flowing = idle && animationsEnabled.value
    val strength = animateFloatAsState(
        targetValue = if (flowing) 1f else 0f,
        animationSpec = tween(if (flowing) IDLE_FLOW_FADE_IN_MS else IDLE_FLOW_FADE_OUT_MS),
        label = "plate_idle_flow"
    )
    val flowingState = rememberUpdatedState(flowing)
    val clockPaused by remember { derivedStateOf { !flowingState.value && strength.value == 0f } }
    val time = rememberMeshClock(isPaused = clockPaused)
    return remember(strength, time, animationsEnabled) { PlateIdleFlow(strength, time, animationsEnabled) }
}

/** How long the flow takes to appear once the bar is idle, and to go once typing or listening starts. */
private const val IDLE_FLOW_FADE_IN_MS = 1200
private const val IDLE_FLOW_FADE_OUT_MS = 350

/**
 * The widget preview's rippling bottom glow, reshaped for the search bar: two counter-moving ripples at about a third
 * of the preview's speed and height, raising and lowering the fading top edge of the plate's bottom light, and a slow
 * breath in place of its pulse. At most [uColor] at 0.30 opacity, so the bar's light moves but never brightens much.
 * Every frequency times 1000 is whole, so the mesh clock wraps without a jump.
 */
@Language("AGSL")
private const val AGSL_PLATE_IDLE_FLOW = """
    uniform float2 uSize;
    uniform float uTime;
    layout(color) uniform half4 uColor;

    half4 main(float2 p) {
        float u = p.x / uSize.x;
        float y = 1.0 - p.y / uSize.y; // 0.0 at the bottom edge, 1.0 at the top

        float wave = sin(u * 6.28 + uTime * 0.7) * 0.06 + cos(u * 9.42 - uTime * 0.55) * 0.035;
        // The plate's bottom light fades out over the lower ~60% of the bar.
        float band = 1.0 - smoothstep(0.0, 0.55, y - wave);
        float breath = 0.85 + 0.15 * sin(uTime * 0.8);

        half a = half(clamp(band * breath, 0.0, 1.0) * 0.30);
        return half4(uColor.rgb * a, a);
    }
"""
