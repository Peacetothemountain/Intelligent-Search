package com.pixel.intelligentsearch.feature.search

import android.Manifest
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled
import com.pixel.intelligentsearch.feature.settings.declinedForGood
import com.pixel.intelligentsearch.feature.settings.forgetDecline
import com.pixel.intelligentsearch.feature.settings.noteDecline
import com.pixel.intelligentsearch.feature.settings.rememberSettingsGrant
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import com.pixel.intelligentsearch.feature.voice.Dictation

/**
 * Voice search on the search screen, Rambler style: the search bar listens until the user taps the check button, then
 * fills in what they meant (filler sounds, repeats and spoken corrections tidied). Speech goes through the phone's
 * speech recognizer (on a Pixel, Google's on-device engine); Android gives apps no way to use the keyboard's own
 * microphone, so the search bar's mic button and the assistant gesture (with Start Assistant with Voice on) start this.
 */
@Stable
internal class OverlayVoice(
    private val dictation: Dictation,
    private val askPermission: () -> Unit,
    private val begin: () -> Unit,
) {
    /** True from the moment listening starts until the words are delivered or it is cancelled. */
    val active: Boolean get() = dictation.phase.value != Dictation.Phase.Idle

    /** The current voice level, 0 to 1, read in the draw phase so the animation follows speech without recomposing. */
    val level: Float get() = dictation.amplitude.floatValue

    internal var permissionGranted: () -> Boolean = { false }

    /** Starts listening, asking for the microphone first if it hasn't been allowed yet. */
    fun start() = if (permissionGranted()) begin() else askPermission()

    /** Done speaking (the check button): stop and fill in the words. */
    fun finish() = dictation.finish()

    /** Cancel (the close button): stop and keep nothing. */
    fun stop() = dictation.cancel()
}

/** [onHeard] gets the finished, tidied words ("" when nothing was said); [onProblem] a short message when voice failed. */
@Composable
internal fun rememberOverlayVoice(onHeard: (text: String) -> Unit, onProblem: (String) -> Unit): OverlayVoice {
    val context = LocalContext.current
    val heard by rememberUpdatedState(onHeard)
    val problem by rememberUpdatedState(onProblem)
    val dictation = remember { Dictation(context) }
    val begin = remember { { dictation.start(onFinished = { heard(it) }, onProblem = { problem(it) }) } }
    val asking = remember { MicRequest() }
    // Allowed on App info after a decline for good: back on the search, it listens, as the tap that asked meant to.
    val settingsGrant = rememberSettingsGrant(Manifest.permission.RECORD_AUDIO) { begin() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        when {
            granted -> {
                forgetDecline(context, Manifest.permission.RECORD_AUDIO)
                begin()
            }
            // Denied for good (Android no longer shows its dialog): the app's settings page is the only way back.
            activity != null && declinedForGood(activity, Manifest.permission.RECORD_AUDIO, asking.rationaleBefore) -> {
                problem("Turn on the microphone for Intelligent Search in Settings to search by voice.")
                settingsGrant.open()
            }
            else -> problem("Allow the microphone to search by voice.")
        }
    }
    val voice = remember {
        OverlayVoice(dictation, askPermission = {
            asking.rationaleBefore = context.micRationale()
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }, begin = begin)
    }
    voice.permissionGranted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { moveOldMicRecord(context, voice.permissionGranted()) }
    DisposableEffect(Unit) { onDispose { dictation.cancel() } }
    // Leaving the screen (home, another app, the screen turning off) lets go of the microphone; cancelling also drops
    // the callbacks, so nothing heard is searched later. Not on pause: the permission dialog pauses the screen too.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dictation.cancel() }
    return voice
}

/** What was known just before the microphone was last asked for, read when the answer comes back. */
private class MicRequest(var rationaleBefore: Boolean = false)

/** Whether Android wants the app to explain why it needs the microphone: true after one denial, until a second. */
private fun Context.micRationale(): Boolean =
    findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true

/**
 * The microphone's own "asked before" record, kept until the microphone shared the other permissions' decline record
 * ([declinedForGood]). It was never cleared, so a lapsed "Only this time" grant then a closed prompt read as declined
 * for good. Moved over once, as a decline only while the microphone is off, then deleted.
 */
private const val OLD_VOICE_PERMISSION_PREFS = "voice_permission_state"
private const val OLD_KEY_MIC_ASKED = "mic_asked"

private fun moveOldMicRecord(context: Context, micAllowed: Boolean) {
    runCatching {
        val old = context.getSharedPreferences(OLD_VOICE_PERMISSION_PREFS, Context.MODE_PRIVATE)
        if (!old.contains(OLD_KEY_MIC_ASKED)) return
        if (old.getBoolean(OLD_KEY_MIC_ASKED, false) && !micAllowed) noteDecline(context, Manifest.permission.RECORD_AUDIO)
        old.edit { clear() }
    }
}

/**
 * The search bar's listening animation, matched frame by frame to Gboard's Rambler strip. A field of light in the
 * colour scheme's primary tone sits on the bottom edge: full strength there, fading to nothing on a soft S-curve over a
 * height that swells while speaking (about half the bar when quiet, nearly all of it when loud). Two slow, long waves
 * roll across it so dark lobes drift along the top. A faint diamond halftone shows inside the light only, never past
 * its edge. The field grows out from the middle when listening starts and shrinks back when it stops. Its colour is
 * Rambler's own reading of the user's Material You palette, see [ramblerGlowColors]. Drawn per pixel by an AGSL
 * shader; Android 12 draws it in thin slices.
 */
internal fun Modifier.listeningGlow(active: Boolean, level: () -> Float, cornerRadius: Dp): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val colors = remember(scheme.primary, scheme.tertiary) { ramblerGlowColors(scheme.primary, scheme.tertiary) }
    // How far the field has spread from the middle: 0 is nothing, 1 is the whole bar.
    val spreadState = animateFloatAsState(if (active) 1f else 0f, tween(if (active) 420 else 320), label = "listening")
    // Only the away/showing boundary recomposes; the spread itself is read in the draw phase.
    val showing by remember { derivedStateOf { spreadState.value > 0f } }
    val smoothed = remember { floatArrayOf(0f) }
    val field = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { ListeningField() }.getOrNull() else null
    }
    // Nothing runs while the field is away: no clock, no drawing.
    if (!showing) return@composed this
    // The clock behind the waves. Their speeds are whole multiples of this loop, so it repeats without a jump. With
    // animations off it stays at the loop's start: the field rests still, rising only with the voice.
    val loopsOn by rememberAnimationsEnabled()
    val clock = if (loopsOn) {
        rememberInfiniteTransition(label = "listening-field").animateFloat(
            initialValue = 0f, targetValue = LOOP_SECONDS,
            animationSpec = infiniteRepeatable(tween((LOOP_SECONDS * 1000).toInt(), easing = LinearEasing)), label = "time",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    drawWithCache {
        val w = size.width
        val h = size.height
        val radius = cornerRadius.toPx().coerceAtMost(h / 2)
        val slices = ((w / (2f * density)).toInt() + 1).coerceAtLeast(1)
        val column = if (field == null) glowColumn(colors) else null
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius))) }
        onDrawWithContent {
            val t = clock.value
            val spread = spreadState.value
            val target = level().coerceIn(0f, 1f)
            smoothed[0] += (target - smoothed[0]) * (if (target > smoothed[0]) 0.4f else 0.1f)
            val loud = smoothed[0]
            if (android.os.Build.VERSION.SDK_INT >= 33 && field != null) {
                field.set(w, h, density, t, loud, spread, colors)
                drawRoundRect(field.brush, cornerRadius = CornerRadius(radius))
            } else if (column != null) {
                // Android 12: the same field, one two-dp slice at a time, each a stretched copy of the fade.
                clipPath(clip) {
                    for (i in 0 until slices) {
                        val x = (i + 0.5f) / slices
                        val reach = fieldReach(x, t, loud, spread)
                        if (reach <= 0.01f) continue
                        val top = h * (1f - reach)
                        // Whole-pixel edges that meet exactly, so no seams show between slices.
                        val left = (w * i / slices).toInt()
                        val right = (w * (i + 1) / slices).toInt().coerceAtLeast(left + 1)
                        drawImage(
                            column, dstOffset = IntOffset(left, top.toInt()),
                            dstSize = IntSize(right - left, (h - top).toInt().coerceAtLeast(1)),
                            alpha = fieldStrength(x, spread),
                        )
                    }
                }
            }
            drawContent()
        }
    }
}

private const val LOOP_SECONDS = 12f

/** How far from the primary hue towards the tertiary the glow sits: measured from Gboard, pink, blue and orange seeds. */
private const val GLOW_HUE_TURN = 0.38

/** The AGSL field and its brush, Android 13 and up. */
@androidx.annotation.RequiresApi(33)
private class ListeningField {
    private val shader = android.graphics.RuntimeShader(AGSL_LISTENING_FIELD)
    val brush = ShaderBrush(shader)

    fun set(w: Float, h: Float, dp: Float, time: Float, loud: Float, spread: Float, colors: RamblerGlowColors) {
        shader.setFloatUniform("uSize", w, h)
        shader.setFloatUniform("uDp", dp)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uLoud", loud)
        shader.setFloatUniform("uSpread", spread)
        shader.setColorUniform("uColor", colors.body.toArgb())
        shader.setColorUniform("uLine", colors.line.toArgb())
    }
}

/** The field's two colours: the body of the light, and the lighter line it rests on along the bottom edge. */
internal class RamblerGlowColors(val body: Color, val line: Color)

/**
 * Gboard's Rambler colours for a Material You palette, measured from Gboard on a Pixel with pink, blue and orange
 * seeds, light and dark. The light is not the primary colour: its hue sits [GLOW_HUE_TURN] of the way from the primary
 * towards the tertiary (twenty-odd degrees on), with more chroma than any tonal palette colour and the same tone in both
 * themes; the line along the bottom is the same hue, lighter. The check button is the secondary colour.
 */
@Suppress("RestrictedApi")
internal fun ramblerGlowColors(primary: Color, tertiary: Color): RamblerGlowColors {
    val from = com.google.android.material.color.utilities.Hct.fromInt(primary.toArgb()).hue
    val to = com.google.android.material.color.utilities.Hct.fromInt(tertiary.toArgb()).hue
    // Shortest way round the hue circle.
    val turn = ((to - from + 540.0) % 360.0) - 180.0
    val hue = (from + turn * GLOW_HUE_TURN + 360.0) % 360.0
    return RamblerGlowColors(
        body = Color(com.google.android.material.color.utilities.Hct.from(hue, 45.0, 75.0).toInt()),
        line = Color(com.google.android.material.color.utilities.Hct.from(hue, 45.0, 82.0).toInt()),
    )
}

/** How high the light reaches at [x] (0..1 across the bar), as a fraction of the bar's height. Mirrors the shader. */
private fun fieldReach(x: Float, t: Float, loud: Float, spread: Float): Float {
    val tau = (2 * Math.PI).toFloat()
    val wave = 0.6f * kotlin.math.sin(tau * (1.3f * x + t / 4f)) + 0.4f * kotlin.math.sin(tau * (2.1f * x - t / 6f) + 2f)
    val base = 0.50f + 0.42f * loud
    val reach = base * (1f + 0.22f * (0.45f + 0.55f * loud) * wave)
    return reach * (0.4f + 0.6f * fieldStrength(x, spread))
}

/** How much of the field has reached [x]: it grows from the middle outwards with a soft front. */
private fun fieldStrength(x: Float, spread: Float): Float {
    val edge = kotlin.math.abs(x - 0.5f) * 2f
    val front = spread * 1.3f
    return ((front - edge) / 0.3f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}

/** One column of the fade, bottom full strength to nothing at the top, for the Android 12 slices. */
private fun glowColumn(colors: RamblerGlowColors): ImageBitmap {
    val rows = 128
    val bitmap = ImageBitmap(1, rows)
    val pixels = IntArray(rows) { row ->
        val d = 1f - (row + 0.5f) / rows
        val s = ((d + 0.1f) / 1.1f).coerceIn(0f, 1f)
        val color = if (row >= rows - 2) colors.line else colors.body
        color.copy(alpha = 1f - s * s * (3f - 2f * s)).toArgb()
    }
    bitmap.asAndroidBitmap().setPixels(pixels, 0, 1, 0, 0, 1, rows)
    return bitmap
}

@org.intellij.lang.annotations.Language("AGSL")
private const val AGSL_LISTENING_FIELD = """
    uniform float2 uSize;
    uniform float uDp;
    uniform float uTime;
    uniform float uLoud;
    uniform float uSpread;
    layout(color) uniform half4 uColor;
    layout(color) uniform half4 uLine;
    const float TAU = 6.28318530718;

    // The field grows from the middle outwards with a soft front.
    float strengthAt(float x) {
        float edge = abs(x - 0.5) * 2.0;
        float front = uSpread * 1.3;
        return smoothstep(0.0, 1.0, (front - edge) / 0.3);
    }

    // How high the light reaches at x, as a fraction of the bar: a base lifted by the voice, two slow waves on top.
    float reachAt(float x, float strength) {
        float wave = 0.6 * sin(TAU * (1.3 * x + uTime / 4.0)) + 0.4 * sin(TAU * (2.1 * x - uTime / 6.0) + 2.0);
        float base = 0.50 + 0.42 * uLoud;
        float reach = base * (1.0 + 0.22 * (0.45 + 0.55 * uLoud) * wave);
        return reach * (0.4 + 0.6 * strength);
    }

    half4 main(float2 p) {
        float x = p.x / uSize.x;
        float strength = strengthAt(x);
        if (strength <= 0.0) return half4(0.0);
        float reach = reachAt(x, strength) * uSize.y;
        float d = uSize.y - p.y;
        // Full strength on the bottom edge, fading on an S-curve: slow at first, fastest halfway, a soft tail at the top.
        float a = 1.0 - smoothstep(-0.1 * reach, reach, d);
        // A faint diamond halftone inside the light: dots on a grid turned 45 degrees, strongest where the fade is half.
        float cell = 2.4 * uDp;
        float2 g = float2(p.x + p.y, p.x - p.y) / (cell * 1.41421356);
        float2 f = fract(g) - 0.5;
        float dot = 1.0 - smoothstep(0.18, 0.34, length(f));
        float mid = 4.0 * a * (1.0 - a);
        a += dot * 0.06 * mid;
        // The lighter line the light rests on: solid along the bottom dp, melting into the body above.
        float line = 1.0 - smoothstep(1.0 * uDp, 3.5 * uDp, d);
        half3 rgb = mix(uColor.rgb, uLine.rgb, half(line));
        return half4(rgb, 1.0) * half(clamp(a, 0.0, 1.0) * strength);
    }
"""
