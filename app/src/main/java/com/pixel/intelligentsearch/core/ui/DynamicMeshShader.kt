package com.pixel.intelligentsearch.core.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.performance.ADPFThermalManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.intellij.lang.annotations.Language

/**
 * Slowly flowing mesh-gradient background between five colours (four corners and the centre), animated to
 * each new colour over 600 ms. Draws a static gradient under severe thermal throttling.
 */
@Composable
fun Modifier.meshGradientBackground(
    colorTL: Color,
    colorTR: Color,
    colorBL: Color,
    colorBR: Color,
    colorCenter: Color,
    alpha: Float = 0.18f,
    isPaused: Boolean = false,
    isInteracting: Boolean = false
): Modifier {
    val animSpec = tween<Color>(600, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
    val animTL = animateColorAsState(targetValue = colorTL, animationSpec = animSpec, label = "mesh_tl")
    val animTR = animateColorAsState(targetValue = colorTR, animationSpec = animSpec, label = "mesh_tr")
    val animBL = animateColorAsState(targetValue = colorBL, animationSpec = animSpec, label = "mesh_bl")
    val animBR = animateColorAsState(targetValue = colorBR, animationSpec = animSpec, label = "mesh_br")
    val animCenter = animateColorAsState(targetValue = colorCenter, animationSpec = animSpec, label = "mesh_center")

    return this.meshGradientBackground(
        colorTLProducer = { animTL.value },
        colorTRProducer = { animTR.value },
        colorBLProducer = { animBL.value },
        colorBRProducer = { animBR.value },
        colorCenterProducer = { animCenter.value },
        alpha = alpha,
        isPaused = isPaused,
        isInteracting = isInteracting
    )
}

/**
 * Convenience modifier automatically deriving an Android 18 mesh gradient from Material You colorScheme.
 * Features symmetrical horizontal lighting to eliminate harsh left-to-right color splits,
 * smoothly transitioning from an ambient top dynamic tint to the deep background surface.
 */
@Composable
fun Modifier.materialYouMeshGradientBackground(
    alpha: Float = 0.12f
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val topTint = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primaryContainer, 0.20f)
    val bottomTint = scheme.background
    val centerTint = androidx.compose.ui.graphics.lerp(scheme.surface, scheme.secondaryContainer, 0.10f)
    return this.meshGradientBackground(
        colorTL = topTint,
        colorTR = topTint,
        colorBL = bottomTint,
        colorBR = bottomTint,
        colorCenter = centerTint,
        alpha = alpha
    )
}

/**
 * Android 18 mesh gradient vertex layout (3x3 vertices / 2x2 patches) for [MeshGradientPainter].
 *
 * Corners carry the four Material You accent roles, edge midpoints blend their neighbours, and the
 * centre vertex carries the surface role. Edge midpoints slide along their edge and the centre
 * orbits slowly, so the colour field flows organically while still covering the full bounds.
 */
private fun androidx.compose.ui.graphics.MeshGradientScope.configureMaterialMesh(
    t: Float,
    tl: Color,
    tr: Color,
    bl: Color,
    br: Color,
    center: Color
) {
    val topMidX = 0.5f + 0.16f * kotlin.math.sin(t * 0.13f)
    val bottomMidX = 0.5f + 0.16f * kotlin.math.cos(t * 0.11f)
    val leftMidY = 0.5f + 0.13f * kotlin.math.sin(t * 0.15f)
    val rightMidY = 0.5f + 0.13f * kotlin.math.cos(t * 0.19f)
    val centerX = 0.5f + 0.12f * kotlin.math.sin(t * 0.21f)
    val centerY = 0.5f + 0.10f * kotlin.math.cos(t * 0.17f)

    setVertex(0, 0, androidx.compose.ui.geometry.Offset(0f, 0f), tl)
    setVertex(0, 1, androidx.compose.ui.geometry.Offset(topMidX, 0f), androidx.compose.ui.graphics.lerp(tl, tr, 0.5f))
    setVertex(0, 2, androidx.compose.ui.geometry.Offset(1f, 0f), tr)

    setVertex(1, 0, androidx.compose.ui.geometry.Offset(0f, leftMidY), androidx.compose.ui.graphics.lerp(tl, bl, 0.5f))
    setVertex(1, 1, androidx.compose.ui.geometry.Offset(centerX, centerY), center)
    setVertex(1, 2, androidx.compose.ui.geometry.Offset(1f, rightMidY), androidx.compose.ui.graphics.lerp(tr, br, 0.5f))

    setVertex(2, 0, androidx.compose.ui.geometry.Offset(0f, 1f), bl)
    setVertex(2, 1, androidx.compose.ui.geometry.Offset(bottomMidX, 1f), androidx.compose.ui.graphics.lerp(bl, br, 0.5f))
    setVertex(2, 2, androidx.compose.ui.geometry.Offset(1f, 1f), br)
}

/**
 * Zero-recomposition overload of [meshGradientBackground] deferring state reads to the draw phase.
 *
 * Renders with Jetpack Compose's official [androidx.compose.ui.graphics.MeshGradientPainter]
 * (hardware-accelerated `Canvas.drawVertices`), the mesh gradient API surfaced for Android 18.
 */
@Composable
fun Modifier.meshGradientBackground(
    colorTLProducer: () -> Color,
    colorTRProducer: () -> Color,
    colorBLProducer: () -> Color,
    colorBRProducer: () -> Color,
    colorCenterProducer: () -> Color,
    alpha: Float = 0.18f,
    isPaused: Boolean = false,
    isInteracting: Boolean = false
): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevelState = adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
    // Stands still while paused, interacting, throttled, in the background or with animations removed.
    val timeState = rememberMeshClock(isPaused, extraPause = isInteracting)

    val currentTL by androidx.compose.runtime.rememberUpdatedState(colorTLProducer)
    val currentTR by androidx.compose.runtime.rememberUpdatedState(colorTRProducer)
    val currentBL by androidx.compose.runtime.rememberUpdatedState(colorBLProducer)
    val currentBR by androidx.compose.runtime.rememberUpdatedState(colorBRProducer)
    val currentCenter by androidx.compose.runtime.rememberUpdatedState(colorCenterProducer)

    // The configuration block runs in the draw phase, so time/colour reads only invalidate drawing.
    val meshPainter = remember {
        androidx.compose.ui.graphics.MeshGradientPainter(rows = 2, columns = 2, hasBicubicColor = true) {
            configureMaterialMesh(
                t = timeState.value,
                tl = currentTL(),
                tr = currentTR(),
                bl = currentBL(),
                br = currentBR(),
                center = currentCenter()
            )
        }
    }

    return this.drawBehind {
        if (throttleLevelState.value >= ADPFThermalManager.ThermalThrottleLevel.SEVERE) {
            // ADPF severe thermal fallback: cheap static linear gradient
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        currentTL().copy(alpha = alpha),
                        currentCenter().copy(alpha = alpha * 0.7f),
                        currentTR().copy(alpha = alpha * 0.4f)
                    )
                )
            )
        } else {
            with(meshPainter) { draw(size, alpha = alpha.coerceIn(0f, 1f)) }
        }
    }
}

/**
 * Seconds the mesh backgrounds have drifted. Their drift is far too slow to need every display frame, so the clock
 * waits [MESH_FRAME_GAP_MS] after each tick and then takes the next frame: about 30 redraws a second at 60, 90 or
 * 120 Hz. It stands still while [isPaused] or [extraPause], in the background, under severe thermal throttling and,
 * when [stopsWithAnimationsRemoved], while the user has removed animations. Time is taken from frame timestamps, so
 * a late frame doesn't slow it.
 */
@Composable
internal fun rememberMeshClock(
    isPaused: Boolean,
    extraPause: Boolean = false,
    stopsWithAnimationsRemoved: Boolean = true
): State<Float> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevel by adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
    val isThermallyThrottled = throttleLevel >= ADPFThermalManager.ThermalThrottleLevel.SEVERE
    val animationsEnabled by rememberAnimationsEnabled()
    val stoppedForAccessibility = stopsWithAnimationsRemoved && !animationsEnabled
    val lifecycleOwner = LocalLifecycleOwner.current
    val timeState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lifecycleOwner, isPaused, extraPause, isThermallyThrottled, stoppedForAccessibility) {
        if (!isPaused && !extraPause && !isThermallyThrottled && !stoppedForAccessibility) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = 0L
                while (isActive) {
                    withFrameNanos { frameTime ->
                        if (lastFrame == 0L) lastFrame = frameTime
                        val dt = ((frameTime - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                        timeState.floatValue = (timeState.floatValue + dt) % MESH_CLOCK_PERIOD
                        lastFrame = frameTime
                    }
                    delay(MESH_FRAME_GAP_MS)
                }
            }
        }
    }
    return timeState
}

/** Pause after each mesh tick; with the wait for the next frame, ticks land about 33 ms apart. */
private const val MESH_FRAME_GAP_MS = 30L

/** Every mesh drift frequency completes whole cycles in this time, so the clock wraps without a jump. */
private const val MESH_CLOCK_PERIOD = (2000.0 * Math.PI).toFloat()

/**
 * Material You under-lit mesh (Gemini overlay search bar style).
 *
 * A 4x4-vertex [androidx.compose.ui.graphics.MeshGradientPainter] lit like a soft light shining up
 * against the bottom of the bar:
 * - The whole rim is a deep Material colour ([androidx.compose.material3.ColorScheme.primaryContainer]).
 * - The interior, which the light can't reach, settles into a darker tone of the same hue
 *   (primaryContainer toward surfaceContainerLowest), never plain black.
 * - The bottom edge carries the lightest tone (primaryContainer toward primary, with a gentle
 *   tertiary sway), which wraps up and over the rounded ends and fades softly across the bar.
 *
 * All colours are [MaterialTheme.colorScheme] roles, so it follows the user's dynamic (wallpaper)
 * palette and light/dark theme automatically.
 */
@Composable
fun Modifier.materialYouEdgeGlowMesh(
    alpha: Float = 1f,
    glowStrength: Float = 1f,
    isPaused: Boolean = false
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val deepState = androidx.compose.runtime.rememberUpdatedState(scheme.primaryContainer)
    val interiorState = androidx.compose.runtime.rememberUpdatedState(
        androidx.compose.ui.graphics.lerp(scheme.surfaceContainerLowest, scheme.primaryContainer, 0.45f)
    )
    val lightPrimaryState = androidx.compose.runtime.rememberUpdatedState(scheme.primary)
    val lightTertiaryState = androidx.compose.runtime.rememberUpdatedState(scheme.tertiary)
    val strengthState = androidx.compose.runtime.rememberUpdatedState(glowStrength.coerceIn(0f, 1.2f))

    val context = androidx.compose.ui.platform.LocalContext.current
    val adpfThermalManager = remember(context) { ADPFThermalManager.getInstance(context) }
    val throttleLevelState = adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
    // Unlike the other mesh backgrounds, this one keeps drifting with animations removed; the overlay screenshots
    // are recorded with it drifting.
    val timeState = rememberMeshClock(isPaused, stopsWithAnimationsRemoved = false)

    val meshPainter = remember {
        androidx.compose.ui.graphics.MeshGradientPainter(rows = 3, columns = 3, hasBicubicColor = true) {
            configureUnderLitMesh(
                t = timeState.value,
                deep = deepState.value,
                interior = interiorState.value,
                lightPrimary = lightPrimaryState.value,
                lightTertiary = lightTertiaryState.value,
                strength = strengthState.value
            )
        }
    }

    return this.drawBehind {
        if (throttleLevelState.value >= ADPFThermalManager.ThermalThrottleLevel.SEVERE) {
            // ADPF severe thermal fallback: static deep tonal bar lit from the bottom
            val deep = deepState.value
            drawRect(
                brush = Brush.verticalGradient(
                    0f to deep,
                    0.45f to interiorState.value,
                    1f to androidx.compose.ui.graphics.lerp(deep, lightPrimaryState.value, 0.6f * strengthState.value)
                ),
                alpha = alpha.coerceIn(0f, 1f)
            )
        } else {
            with(meshPainter) { draw(size, alpha = alpha.coerceIn(0f, 1f)) }
        }
    }
}

private fun androidx.compose.ui.graphics.MeshGradientScope.configureUnderLitMesh(
    t: Float,
    deep: Color,
    interior: Color,
    lightPrimary: Color,
    lightTertiary: Color,
    strength: Float
) {
    // Soft light source below the bar: gently breathes and drifts sideways.
    val intensity = (0.92f + 0.08f * kotlin.math.sin(t * 0.6f)) * strength
    val slide = 0.04f * kotlin.math.sin(t * 0.2f)
    val sway = 0.5f + 0.5f * kotlin.math.sin(t * 0.25f)

    // Lightest tone: deep container lifted toward primary, with a subtle tertiary sway.
    val lightHue = androidx.compose.ui.graphics.lerp(lightPrimary, lightTertiary, 0.25f * sway)
    val light = androidx.compose.ui.graphics.lerp(deep, lightHue, 0.6f)

    fun lit(base: Color, amount: Float): Color =
        androidx.compose.ui.graphics.lerp(base, light, (amount * intensity).coerceIn(0f, 1f))

    val xs = floatArrayOf(0f, 0.16f + slide, 0.84f + slide, 1f)
    val ys = floatArrayOf(0f, 0.38f, 0.72f, 1f)

    // Row 0: top rim, deep material colour (no light reaches the top)
    // Row 1: ends stay near the deep rim, interior sinks to the darker tone
    // Row 2: light starts wrapping up the rounded ends, interior barely touched
    // Row 3: bottom rim, brightest at the centre, rounding over the bottom corners
    val grid = arrayOf(
        arrayOf(deep, deep, deep, deep),
        arrayOf(
            androidx.compose.ui.graphics.lerp(interior, deep, 0.85f), interior,
            interior, androidx.compose.ui.graphics.lerp(interior, deep, 0.85f)
        ),
        arrayOf(lit(deep, 0.40f), lit(interior, 0.16f), lit(interior, 0.16f), lit(deep, 0.40f)),
        arrayOf(lit(deep, 0.72f), lit(deep, 1.0f), lit(deep, 1.0f), lit(deep, 0.72f))
    )

    for (r in 0..3) {
        for (c in 0..3) {
            setVertex(r, c, androidx.compose.ui.geometry.Offset(xs[c], ys[r]), grid[r][c])
        }
    }
}

/**
 * Gemini-style input plate (Android 17 QPR3 / Google app "Robin" plate design), reimplemented.
 *
 * Design structure (matches the Google app's plate composition):
 * 1. Elliptical radial background whose centre sits near the TOP of the pill (0.5, 0.1437) with
 *    radii (0.7031, 0.8563). The top stays the deep centre tone; the colour grows toward the
 *    sides and peaks along the bottom edge, which reads as a soft light shining up at the bar.
 * 2. Ambient inset edge glow (18.6dp falloff, quadratic) in the deep container colour all the way
 *    around the rim.
 * 3. Bottom rim bevel: the pill SDF shifted up by 1.5dp so only the bottom edge catches the
 *    primary colour (3dp soft edge).
 * 4. Mid-stop of the radial ramp: 0.3846 in dark theme, 0.5547 in light theme.
 *
 * Colours are Material You roles (dynamic colour). Unlike the Google app, whose dark-theme centre
 * is pure black, the centre here is a deep primaryContainer tone so the bar stays on-palette.
 * Requires API 33 (AGSL); falls back to [materialYouEdgeGlowMesh] below that.
 */
@Composable
fun Modifier.materialYouGeminiPlate(
    cornerRadius: androidx.compose.ui.unit.Dp,
    alpha: Float = 1f
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.materialYouEdgeGlowMesh(alpha = alpha)
    }
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    // 1. Center: "The dark purple is suppose to be way way darker. It's as close to purple black as you can get it"
    val centerColor = if (isDark) {
        androidx.compose.ui.graphics.lerp(
            Color(0xFF060305),
            scheme.primaryContainer,
            0.025f
        )
    } else {
        scheme.surfaceContainerLowest
    }

    // 2. Outer: soft, dark muted material color (#160E15 to #1E141D)
    val outerColor = if (isDark) {
        androidx.compose.ui.graphics.lerp(
            Color(0xFF090608),
            scheme.primaryContainer,
            0.24f
        )
    } else {
        scheme.surfaceContainerHigh
    }

    // 3. Bottom lift: defined upward light along bottom edge matching #1E1319 reference
    val bottomLiftColor = geminiPlateBottomLiftColor(scheme)

    // 4. Top rim: defined subtle highlight along top edge (#181016)
    val topRimColor = if (isDark) {
        androidx.compose.ui.graphics.lerp(
            Color(0xFF0B070A),
            scheme.primaryContainer,
            0.32f
        )
    } else {
        outerColor
    }

    val shader = remember { runCatching { RuntimeShader(AGSL_GEMINI_PLATE) }.getOrNull() }
        ?: return this.materialYouEdgeGlowMesh(alpha = alpha)
    val brush = remember(shader) { ShaderBrush(shader) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dpPx = density.density
    val radiusPx = with(density) { cornerRadius.toPx() }

    return this.drawWithCache {
        shader.setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setFloatUniform("uRadius", radiusPx)
        shader.setFloatUniform("uDp", dpPx)
        shader.setColorUniform("uCenterColor", centerColor.toArgb())
        shader.setColorUniform("uOuterColor", outerColor.toArgb())
        shader.setColorUniform("uBottomLiftColor", bottomLiftColor.toArgb())
        shader.setColorUniform("uTopRimColor", topRimColor.toArgb())
        onDrawBehind { drawRect(brush = brush, alpha = alpha.coerceIn(0f, 1f)) }
    }
}

/**
 * The colour of the light along the bottom edge of [materialYouGeminiPlate] for [scheme]: in dark theme a lift of
 * the deep container tone, in light theme the plate's outer tone. [geminiPlateIdleFlow] flows this same colour.
 */
internal fun geminiPlateBottomLiftColor(scheme: androidx.compose.material3.ColorScheme): Color =
    if (scheme.surface.luminance() < 0.5f) {
        androidx.compose.ui.graphics.lerp(
            Color(0xFF160E14),
            scheme.primaryContainer,
            0.46f
        )
    } else {
        // The light theme's outer colour.
        scheme.surfaceContainerHigh
    }

@Language("AGSL")
private const val AGSL_GEMINI_PLATE = """
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uDp;
    layout(color) uniform half4 uCenterColor;
    layout(color) uniform half4 uOuterColor;
    layout(color) uniform half4 uBottomLiftColor;
    layout(color) uniform half4 uTopRimColor;

    float sdCapsuleRaw(float2 p, float2 halfDims, float r) {
        float2 q = abs(p) - halfDims + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 p) {
        float2 hd = uSize * 0.5;
        float2 pos = p - hd;
        float r = min(uRadius, min(hd.x, hd.y));
        float dist = sdCapsuleRaw(pos, hd, r);

        // Anti-aliased outer edge of the stadium pill
        half shapeAlpha = half(clamp(1.0 - dist / (1.0 * uDp), 0.0, 1.0));
        if (shapeAlpha <= 0.0) {
            return half4(0.0);
        }

        // Relative coordinates (0.0 to 1.0)
        float u = clamp(p.x / uSize.x, 0.0, 1.0);
        float v = clamp(p.y / uSize.y, 0.0, 1.0);

        // 1. Natural end-cap ambient glow (diffuses smoothly into the outer ~20% of each end)
        float dxCenter = abs(u - 0.50);
        float capDist = max(0.0, (dxCenter - 0.30) / 0.20);
        float capGlow = pow(capDist, 1.8);

        // 2. Bottom upward glow (starts at v=0.40, smoothly rises toward the bottom edge)
        float tBot = max(0.0, (v - 0.40) / 0.60);
        float botGlow = pow(tBot, 1.7) * 0.70;

        // Subtle glowing lip along the bottom edge (last 14%)
        float tLip = max(0.0, (v - 0.86) / 0.14);
        float botLip = pow(tLip, 1.4) * 0.55;
        float botTotal = min(1.0, botGlow + botLip);

        // 3. Delicate top rim highlight (very top 8%)
        float tTop = max(0.0, (0.08 - v) / 0.08);
        float topGlow = pow(tTop, 1.5) * 0.95;

        // 4. Subtle, smooth organic gradient within the dark purple itself:
        // Deepest purple-black at the core (v ~ 0.35, u ~ 0.50), gently shading into soft dark purple
        float vDark = clamp((0.35 - v) / 0.35, 0.0, 1.0);
        float hDark = clamp(dxCenter / 0.50, 0.0, 1.0);
        float darkGrad = pow(vDark, 1.5) * 0.30 + pow(hDark, 1.8) * 0.20;

        // Base color with internal gradient within the dark purple
        half3 col = mix(uCenterColor.rgb, uOuterColor.rgb, half(darkGrad));

        // Smoothly illuminate with ambient end caps, bottom glow, and top rim
        if (capGlow > 0.0) {
            col = mix(col, uOuterColor.rgb, half(capGlow));
        }
        if (botTotal > 0.0) {
            col = mix(col, uBottomLiftColor.rgb, half(botTotal));
        }
        if (topGlow > 0.0) {
            col = mix(col, uTopRimColor.rgb, half(topGlow));
        }

        return half4(col * shapeAlpha, shapeAlpha);
    }
"""
