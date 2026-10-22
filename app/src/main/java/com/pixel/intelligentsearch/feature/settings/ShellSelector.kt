package com.pixel.intelligentsearch.feature.settings

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.expressive.MatteGrain
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language

/**
 * The one selector every settings page uses for tabs and one-of-a-few choices, after the floating pill bars of
 * Google's own apps: equal options in a matte shell ([MatteShell]), the chosen one on a pill-shaped indicator.
 *
 * When the choice changes, the old indicator dissolves away and a new one grows in under the new option on the
 * Material 3 Expressive fast spatial spring, so it overshoots a touch and settles (the squish), its fill appearing
 * grain by grain and shimmering briefly before it settles flat. Only the indicator is redrawn while that plays;
 * nothing recomposes per frame, and the settled indicator is a plain fill. With animations off it simply moves.
 *
 * [itemContent] draws an option; [LocalContentColor] is the option's colour (the indicator's content colour when
 * chosen, muted otherwise), and the chosen option should show its filled icon and bold label. Options tap through
 * [bouncyClickable], so a tap bounces and gives the same one haptic as every other control, and each announces
 * whether it is selected. [indicatorColor] can give an option its own indicator colour (a custom colour swatch).
 */
@Composable
fun <T> ShellSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp,
    role: Role = Role.RadioButton,
    indicatorColor: (T) -> Color = ShellSelectorDefaults.NoOverride,
    itemContent: @Composable BoxScope.(option: T, isSelected: Boolean) -> Unit
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val defaultIndicator = MaterialTheme.colorScheme.secondaryContainer
    val selectedContent = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant
    val currentIndicatorColor by rememberUpdatedState(indicatorColor)
    val currentOptions by rememberUpdatedState(options)
    val animationsOn by rememberAnimationsEnabled()
    val indicator = remember { ShellIndicatorState(selectedIndex) }
    val dissolve = remember { GrainDissolve.createOrNull() }

    LaunchedEffect(selectedIndex) { indicator.moveTo(selectedIndex, animate = animationsOn) }

    MatteShell(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveCornerTokens.ShapeFull
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .drawBehind {
                    val count = currentOptions.size
                    if (count == 0) return@drawBehind
                    fun colorAt(index: Int): Color {
                        val option = currentOptions.getOrNull(index) ?: return defaultIndicator
                        return currentIndicatorColor(option).takeIf { it != Color.Unspecified } ?: defaultIndicator
                    }
                    val previous = indicator.previous
                    val fade = indicator.fadeOut.value
                    if (previous in 0 until count && previous != indicator.current && fade > 0f) {
                        drawIndicator(previous, count, 0.85f + 0.15f * fade, fade, colorAt(previous), dissolve)
                    }
                    val current = indicator.current
                    if (current in 0 until count) {
                        drawIndicator(current, count, indicator.grow.value, indicator.reveal.value, colorAt(current), dissolve)
                    }
                }
                .padding(ShellSelectorDefaults.Padding)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ShellSelectorDefaults.Gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) selectedContent else unselectedContent,
                    animationSpec = ExpressiveMotionTokens.Effects.ExpressiveDefault(),
                    label = "shell_selector_content"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        // At least [itemHeight], taller where large text needs it; all options as tall as the row.
                        .heightIn(min = itemHeight)
                        .fillMaxHeight()
                        .semantics { this.selected = isSelected }
                        .bouncyClickable(shape = ShellSelectorDefaults.ItemShape, role = role) { onSelect(option) },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        itemContent(option, isSelected)
                    }
                }
            }
        }
    }
}

object ShellSelectorDefaults {
    /** Space between the shell's edge and the options, and between options. */
    val Padding: Dp = 4.dp
    val Gap: Dp = 4.dp

    /** The shape options clip their ripple and focus ring to, the indicator's own. */
    val ItemShape = RoundedCornerShape(percent = 50)

    /** [ShellSelector]'s default [indicatorColor]: no option has its own, all use the secondary container. */
    val NoOverride: (Any?) -> Color = { Color.Unspecified }
}

/**
 * Draws the indicator under option [index] of [count]: [grow] sizes it (0 is 60% wide and 80% tall, 1 is the full
 * option, a spring's overshoot a little over), and [reveal] is how far its grain-by-grain fill has come in (1 is
 * a plain fill). Laid out as [ShellSelector]'s row lays out its options, mirrored right to left.
 */
private fun DrawScope.drawIndicator(index: Int, count: Int, grow: Float, reveal: Float, color: Color, dissolve: GrainDissolve?) {
    val pad = ShellSelectorDefaults.Padding.toPx()
    val gap = ShellSelectorDefaults.Gap.toPx()
    val itemW = (size.width - 2f * pad - (count - 1) * gap) / count
    val itemH = size.height - 2f * pad
    if (itemW <= 0f || itemH <= 0f) return
    val slot = if (layoutDirection == LayoutDirection.Ltr) index else count - 1 - index
    val centerX = pad + slot * (itemW + gap) + itemW / 2f
    val centerY = size.height / 2f
    val w = itemW * (0.6f + 0.4f * grow)
    val h = itemH * (0.8f + 0.2f * grow)
    if (w <= 0f || h <= 0f) return
    val topLeft = Offset(centerX - w / 2f, centerY - h / 2f)
    val rectSize = Size(w, h)
    val corner = CornerRadius(h / 2f)
    val settled = reveal >= 1f
    when {
        settled -> drawRoundRect(color, topLeft, rectSize, corner)
        dissolve != null -> {
            dissolve.set(reveal, color, MatteGrain.cellPxFor(density) * 2f)
            drawRoundRect(dissolve.brush, topLeft, rectSize, corner)
        }
        else -> {
            // Without runtime shaders: the fill fades in under a strong grain that settles as it does.
            val shown = reveal.coerceIn(0f, 1f)
            drawRoundRect(color, topLeft, rectSize, corner, alpha = shown)
            drawRoundRect(MatteGrain.brush(MatteGrain.cellPxFor(density)), topLeft, rectSize, corner, alpha = 0.6f * shown * (1f - shown))
        }
    }
}

/** Where [ShellSelector]'s indicator is, and how far its grow, reveal and fade-out have come. */
@Stable
private class ShellIndicatorState(initial: Int) {
    private val currentState = mutableIntStateOf(initial)
    private val previousState = mutableIntStateOf(-1)
    val current: Int get() = currentState.intValue
    val previous: Int get() = previousState.intValue
    val grow = Animatable(1f)
    val reveal = Animatable(1f)
    val fadeOut = Animatable(0f)

    suspend fun moveTo(index: Int, animate: Boolean) {
        if (index == current) return
        previousState.intValue = current
        currentState.intValue = index
        if (!animate) {
            grow.snapTo(1f)
            reveal.snapTo(1f)
            fadeOut.snapTo(0f)
            return
        }
        grow.snapTo(0f)
        reveal.snapTo(0f)
        fadeOut.snapTo(1f)
        coroutineScope {
            launch { grow.animateTo(1f, ExpressiveMotionTokens.Spatial.FastSpatial()) }
            launch { reveal.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing)) }
            launch { fadeOut.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing)) }
        }
    }
}

/**
 * The indicator's grain-by-grain fill (Android 13 and later): each grain cell appears once the reveal passes its
 * own random threshold, and cells flicker lighter and darker until the reveal completes. One shader per selector,
 * its uniforms updated in place while the reveal plays, so it allocates nothing per frame.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GrainDissolveShader(private val shader: RuntimeShader) : GrainDissolve {
    override val brush: ShaderBrush = ShaderBrush(shader)

    override fun set(progress: Float, color: Color, cellPx: Float) {
        shader.setFloatUniform("uProgress", progress.coerceIn(0f, 1f))
        shader.setFloatUniform("uCell", cellPx.coerceAtLeast(1f))
        shader.setColorUniform("uColor", color.toArgb())
    }
}

private interface GrainDissolve {
    val brush: ShaderBrush
    fun set(progress: Float, color: Color, cellPx: Float)

    companion object {
        @Language("AGSL")
        const val AGSL_GRAIN_DISSOLVE = """
            uniform float uProgress;
            uniform float uCell;
            layout(color) uniform half4 uColor;

            float hash(float2 p) {
                return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
            }

            half4 main(float2 fragCoord) {
                float2 cell = floor(fragCoord / uCell);
                float threshold = hash(cell);
                // Every cell is in by the time the reveal reaches 1, the last ones softly.
                float shown = smoothstep(threshold - 0.12, threshold + 0.12, uProgress * 1.24 - 0.12);
                float flicker = (hash(cell + float2(17.0, 31.0)) - 0.5) * 0.32 * (1.0 - uProgress);
                half3 rgb = clamp(uColor.rgb + half3(flicker), half3(0.0), half3(1.0));
                half a = uColor.a * half(shown);
                return half4(rgb * a, a);
            }
        """

        /** The shader where runtime shaders can be compiled; otherwise null, and the indicator fades in instead. */
        fun createOrNull(): GrainDissolve? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            return runCatching { GrainDissolveShader(RuntimeShader(AGSL_GRAIN_DISSOLVE)) }.getOrNull()
        }
    }
}
