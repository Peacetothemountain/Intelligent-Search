package com.pixel.intelligentsearch.feature.settings

import android.graphics.BlendMode
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.isDark
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * The bugdroid clips. They were rendered as 16:9 frames on black and are stored cropped to the area the
 * character ever occupies, with a margin; the crop rectangle lets [BugdroidPlayer] place them exactly where the
 * full frame would have put them. The character rectangle is the furthest the character (balloon, waving arm,
 * fan) reaches in any frame, so the player can keep all of it inside the view. Both, and [groundY], where the
 * feet meet the floor, are in 1920x1080 pixels. [posterRes] is the clip's first frame, shown until the video draws.
 *
 * The clips' backgrounds were cleaned to exact black offline (the renders' grain and floor shading removed) and
 * each was encoded at the largest size it is shown, every frame and its loop kept as rendered.
 */
enum class BugdroidClip(
    @param:RawRes val rawRes: Int,
    @param:DrawableRes val posterRes: Int,
    val cropLeft: Int, val cropTop: Int, val cropWidth: Int, val cropHeight: Int,
    val characterLeft: Int, val characterTop: Int, val characterRight: Int, val characterBottom: Int,
    val groundY: Int
) {
    SETTINGS(R.raw.bugdroid_video, R.drawable.bugdroid_settings_poster, 340, 0, 1216, 1080, 350, 3, 1543, 1073, 1058),
    // Rendered at 1280x720; its rectangles are scaled to the shared 1920x1080 frame.
    WELCOME(R.raw.bugdroid_welcome_video, R.drawable.bugdroid_welcome_poster, 348, 6, 1335, 1071, 367, 25, 1664, 1059, 1036),
    BACKUP(R.raw.bugdroid_backup_video, R.drawable.bugdroid_backup_poster, 586, 14, 1036, 986, 603, 30, 1604, 982, 976);

    companion object {
        const val SOURCE_WIDTH = 1920
        const val SOURCE_HEIGHT = 1080
    }
}

/** The black background is keyed out with a runtime shader, which needs Android 13; without it the clips show as boxes. */
val isBugdroidVideoSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// Rec. 709 luma key. The render on black is already premultiplied, so its colour is kept as it is: multiplying it by
// the key again darkened the glass and soft edges. The alpha is at least the brightest channel, so the faint glass
// still adds its light over the page as it did over black.
internal const val LUMA_KEY_SHADER = """
    uniform shader content;
    half4 main(float2 coords) {
        half4 color = content.eval(coords);
        float luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
        float brightest = max(color.r, max(color.g, color.b));
        float alpha = max(smoothstep(0.14, 0.28, luma), brightest);
        return half4(color.rgb, alpha * color.a);
    }
"""

// One part of the shadow, cast from each frame's silhouette onto the floor at [ground] by a light from the front,
// above and to one side. A point h above the floor casts its shadow h * light.x across and h * light.y up the
// screen (back into the floor). Part 0 is the shadow near the feet, 1 the part far from them (blurred more, as a
// shadow softens with distance), 2 the contact shadow right under the body.
internal const val SHADOW_SHADER = """
    uniform shader content;
    uniform float2 view;
    uniform float k;
    uniform float ground;
    uniform float2 light;
    uniform float reach;
    uniform float part;
    uniform float strength;

    float silhouette(float2 f) {
        half4 c = content.eval(view * 0.5 + (f - float2(960.0, 540.0)) * k);
        float luma = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
        return c.a * smoothstep(0.05, 0.14, luma);
    }

    half4 main(float2 coords) {
        float2 f = float2(960.0, 540.0) + (coords - view * 0.5) / k;
        float s = 0.0;
        if (part < 1.5) {
            float h = (ground - f.y) / light.y;
            if (h >= 0.0) {
                float m = silhouette(float2(f.x - h * light.x, ground - h));
                float t = clamp(h / reach, 0.0, 1.0);
                s = part < 0.5 ? m * (1.0 - t) : m * t * (1.0 - 0.6 * t);
            }
        } else {
            float h = abs(f.y - ground) / 0.10;
            s = silhouette(float2(f.x, ground - h)) * exp(-h / 70.0);
        }
        return half4(0.0, 0.0, 0.0, s * strength);
    }
"""

// Fades the shadows out towards the view's edges, so one that reaches an edge isn't cut off in a hard line.
internal const val EDGE_FADE_SHADER = """
    uniform shader content;
    uniform float2 view;
    uniform float fade;
    half4 main(float2 coords) {
        float edge = min(min(coords.x, view.x - coords.x), min(coords.y, view.y - coords.y));
        return content.eval(coords) * smoothstep(0.0, fade, edge);
    }
"""

/**
 * A silent, looping player for [clip], or null when [enabled] is false or the device can't key the video.
 * It is released when it leaves composition. With [autoPlay] it plays only while the screen is resumed; pass
 * false when the screen showing it controls playback itself.
 */
@Composable
fun rememberBugdroidPlayer(clip: BugdroidClip, enabled: Boolean = true, autoPlay: Boolean = true): ExoPlayer? {
    val context = LocalContext.current
    val player = remember(clip, enabled) {
        if (!enabled || !isBugdroidVideoSupported) return@remember null
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${clip.rawRes}"))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
            }
        }.getOrNull()
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    if (autoPlay) {
        LifecycleResumeEffect(player) {
            player?.play()
            onPauseOrDispose { player?.pause() }
        }
    }
    return player
}

/**
 * Draws [player]'s video with the black background keyed out, standing on shadows cast from its own silhouette.
 * The frame is scaled to fill the view like the original 16:9 render (times [scaleFactor]), with the cropped clip
 * offset back to its place in that frame. Until the video's first frame is on screen the clip's poster stands in
 * for it, so the bugdroid is there as soon as the page is.
 */
@Composable
fun BugdroidPlayer(
    player: ExoPlayer?,
    clip: BugdroidClip,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f
) {
    val dark = MaterialTheme.colorScheme.isDark
    val edgeFade = with(LocalDensity.current) { SHADOW_EDGE_FADE.toPx() }
    // Built from the layer's own size as it is laid out, so a resize doesn't recompose the player.
    val effects = remember(clip, scaleFactor, dark, edgeFade) { BugdroidEffects(clip, scaleFactor, dark, edgeFade) }

    // The poster stays until the video has drawn a frame of its own: ExoPlayer reports the frame as it hands it to
    // the TextureView, which shows it on its next draw, so the poster goes the frame after that.
    var videoShowing by remember(player) { mutableStateOf(false) }
    val textureView = remember { arrayOfNulls<TextureView>(1) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                val view = textureView[0] ?: return run { videoShowing = true }
                view.postOnAnimation { view.postOnAnimation { videoShowing = true } }
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    Box(modifier = modifier.graphicsLayer { renderEffect = effects.forSize(size) }) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    textureView[0] = this
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        applyClipTransform(view as TextureView, clip, scaleFactor)
                    }
                }
            },
            update = { view ->
                // ExoPlayer manages the TextureView's surface itself; only attach when the player changes.
                if (view.tag !== player) {
                    (view.tag as? ExoPlayer)?.clearVideoTextureView(view)
                    player?.setVideoTextureView(view)
                    view.tag = player
                }
                applyClipTransform(view, clip, scaleFactor)
            },
            onRelease = { view ->
                (view.tag as? ExoPlayer)?.clearVideoTextureView(view)
                view.tag = null
            },
            modifier = Modifier.fillMaxSize()
        )
        // Drawn over the video, unkeyed like it, so the key turns both into the same bugdroid.
        if (player != null && bugdroidShaders != null && !videoShowing) {
            val poster = ImageBitmap.imageResource(clip.posterRes)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = clip.videoRectInView(size.width, size.height, scaleFactor)
                drawImage(
                    image = poster,
                    dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                    dstSize = IntSize(rect.width().roundToInt(), rect.height().roundToInt()),
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

/**
 * Where the (cropped) video sits in a [vw] x [vh] view: the crop's place in the full 1920x1080 frame stretched
 * over the view, then the frame's fill-and-scale about the view centre.
 */
internal fun BugdroidClip.videoRectInView(vw: Float, vh: Float, scaleFactor: Float): RectF {
    val sourceW = BugdroidClip.SOURCE_WIDTH.toFloat()
    val sourceH = BugdroidClip.SOURCE_HEIGHT.toFloat()
    val frameAspect = sourceW / sourceH
    // Fill the view with the full frame (cropping the overflow), as the original player did.
    var fillX = 1f
    var fillY = 1f
    if (vw / vh > frameAspect) fillY = (vw / frameAspect) / vh else fillX = (vh * frameAspect) / vw
    val scale = fittedScale(vw, vh, scaleFactor)
    val sx = fillX * scale
    val sy = fillY * scale
    val left = cropLeft * vw / sourceW
    val top = cropTop * vh / sourceH
    return RectF(
        vw / 2f + (left - vw / 2f) * sx,
        vh / 2f + (top - vh / 2f) * sy,
        vw / 2f + (left + cropWidth * vw / sourceW - vw / 2f) * sx,
        vh / 2f + (top + cropHeight * vh / sourceH - vh / 2f) * sy
    )
}

/** TextureView stretches the (cropped) video over the whole view; this moves it to [videoRectInView]. */
internal fun applyClipTransform(view: TextureView, clip: BugdroidClip, scaleFactor: Float) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    val matrix = Matrix().apply {
        setRectToRect(RectF(0f, 0f, vw, vh), clip.videoRectInView(vw, vh, scaleFactor), Matrix.ScaleToFit.FILL)
    }
    view.setTransform(matrix)
}

/**
 * [scaleFactor], lowered just enough that the whole character fits inside a [vw] x [vh] view with a small
 * margin. Filling a view whose shape differs from 16:9 crops the frame, which on narrower phones cut off the
 * backup bugdroid's fan; on views where everything fits it is unchanged. The frame scales about the view
 * centre, so each edge of the character limits the scale on its own.
 */
internal fun BugdroidClip.fittedScale(vw: Float, vh: Float, scaleFactor: Float): Float {
    if (vw <= 0f || vh <= 0f) return scaleFactor
    val sourceW = BugdroidClip.SOURCE_WIDTH.toFloat()
    val sourceH = BugdroidClip.SOURCE_HEIGHT.toFloat()
    // View pixels per source pixel at scale 1 (the fill scale).
    val fill = maxOf(vw / sourceW, vh / sourceH)
    val margin = FIT_MARGIN * minOf(vw, vh)
    var scale = scaleFactor
    fun limit(reach: Float, room: Float) {
        if (reach > 0f) scale = minOf(scale, (room - margin).coerceAtLeast(0f) / (reach * fill))
    }
    limit(sourceW / 2f - characterLeft, vw / 2f)
    limit(characterRight - sourceW / 2f, vw / 2f)
    limit(sourceH / 2f - characterTop, vh / 2f)
    limit(characterBottom - sourceH / 2f, vh / 2f)
    return scale
}

/**
 * View pixels per pixel of the 1920x1080 frame once [applyClipTransform] has placed the clip in a [vw] x [vh]
 * view, so the shadows cast around the character follow its size and place.
 */
internal fun BugdroidClip.viewPixelsPerFramePixel(vw: Float, vh: Float, scaleFactor: Float): Float =
    maxOf(vw / BugdroidClip.SOURCE_WIDTH, vh / BugdroidClip.SOURCE_HEIGHT) * fittedScale(vw, vh, scaleFactor)

/** Empty space kept around a character that had to be shrunk to fit, as a share of the view's shorter side. */
private const val FIT_MARGIN = 0.03f

/**
 * The light the shadows are cast by: 32 degrees to the left of the camera and 58 degrees above the floor, the
 * direction the renders' own highlights come from. Steep enough that the shadow falls short and mostly behind the
 * bugdroid, inside even the small settings view. The floor seen from the front is pressed flat, to 0.32 of its depth.
 */
private const val LIGHT_AZIMUTH_DEGREES = 32.0
private const val LIGHT_ELEVATION_DEGREES = 58.0
private const val FLOOR_FORESHORTENING = 0.32

/** How far up the body (frame pixels) the cast shadow goes from the near part's sharpness to the far part's. */
private const val SHADOW_REACH = 900f

/** Opacity of the cast shadow and the contact shadow on a light page; see [shadowStrengths]. */
private const val CAST_SHADOW_STRENGTH = 0.55f
private const val CONTACT_SHADOW_STRENGTH = 0.6f

private val SHADOW_EDGE_FADE = 8.dp

/**
 * The three shaders, compiled once for the whole app. Each effect built from them keeps the uniforms set at the time.
 * [source] lets tests compile them with an older shader compiler.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class BugdroidShaders(source: (String) -> String = { it }) {
    val key = RuntimeShader(source(LUMA_KEY_SHADER))
    val shadow = RuntimeShader(source(SHADOW_SHADER))
    val edgeFade = RuntimeShader(source(EDGE_FADE_SHADER))
}

/**
 * Compiled once for the whole app: compiling in each player cost frames every time a page with a bugdroid appeared
 * (returning to the main settings page included). A shader that fails to compile on some GPU driver leaves the
 * video unkeyed rather than crashing the page.
 */
private val bugdroidShaders: BugdroidShaders? by lazy {
    if (!isBugdroidVideoSupported) return@lazy null
    runCatching { BugdroidShaders() }.getOrNull()
}

/** One part of the shadow (see [SHADOW_SHADER]) at [strength], cast by [clip] placed in a [vw] x [vh] view. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setShadowUniforms(clip: BugdroidClip, vw: Float, vh: Float, scaleFactor: Float, part: Int, strength: Float) {
    val azimuth = Math.toRadians(LIGHT_AZIMUTH_DEGREES)
    val cot = 1.0 / tan(Math.toRadians(LIGHT_ELEVATION_DEGREES))
    setFloatUniform("view", vw, vh)
    setFloatUniform("k", clip.viewPixelsPerFramePixel(vw, vh, scaleFactor))
    setFloatUniform("ground", clip.groundY.toFloat())
    setFloatUniform("light", (cot * sin(azimuth)).toFloat(), (FLOOR_FORESHORTENING * cot * cos(azimuth)).toFloat())
    setFloatUniform("reach", SHADOW_REACH)
    setFloatUniform("part", part.toFloat())
    setFloatUniform("strength", strength)
}

/** [EDGE_FADE_SHADER]'s fade over the last [fade] pixels of a [vw] x [vh] view. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun RuntimeShader.setEdgeFadeUniforms(vw: Float, vh: Float, fade: Float) {
    setFloatUniform("view", vw, vh)
    setFloatUniform("fade", fade)
}

/** Opacity of each shadow part: near, far, contact. A dark page needs a deeper shadow to read; the contact one most. */
internal fun shadowStrengths(dark: Boolean): FloatArray = floatArrayOf(
    CAST_SHADOW_STRENGTH * if (dark) 1.25f else 1f,
    CAST_SHADOW_STRENGTH * 0.75f,
    CONTACT_SHADOW_STRENGTH * if (dark) 0.9f else 0.6f
)

/** Blur of each shadow part as a Gaussian sigma in frame pixels, softer the further it falls from the feet. */
private val SHADOW_SIGMAS = floatArrayOf(10f, 42f, 16f)

/**
 * The key, over the shadows the keyed bugdroid casts in a [vw] x [vh] view. Both are worked out from the same
 * layer every frame, so the shadows follow every move of the arms, key and balloon.
 */
private fun bugdroidEffect(clip: BugdroidClip, vw: Float, vh: Float, scaleFactor: Float, dark: Boolean, edgeFade: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shaders = bugdroidShaders ?: return null
    return runCatching {
        val key = RenderEffect.createRuntimeShaderEffect(shaders.key, "content")
        if (vw <= 0f || vh <= 0f) return@runCatching key
        val k = clip.viewPixelsPerFramePixel(vw, vh, scaleFactor)
        val strengths = shadowStrengths(dark)
        val parts = (0..2).map { part ->
            shaders.shadow.setShadowUniforms(clip, vw, vh, scaleFactor, part, strengths[part])
            val radius = blurRadiusForSigma(SHADOW_SIGMAS[part] * k)
            RenderEffect.createBlurEffect(
                radius, radius, RenderEffect.createRuntimeShaderEffect(shaders.shadow, "content"), Shader.TileMode.DECAL
            )
        }
        // Black over black: source-over adds the parts up as 1 - (1 - a)(1 - b), so they deepen where they overlap.
        val shadows = parts.reduce { below, above -> RenderEffect.createBlendModeEffect(below, above, BlendMode.SRC_OVER) }
        shaders.edgeFade.setEdgeFadeUniforms(vw, vh, edgeFade)
        val faded = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shaders.edgeFade, "content"), shadows)
        RenderEffect.createBlendModeEffect(faded, key, BlendMode.SRC_OVER)
    }.getOrNull()
}

/** The effect for the last size asked for, rebuilt only when the size changes. */
private class BugdroidEffects(
    private val clip: BugdroidClip,
    private val scaleFactor: Float,
    private val dark: Boolean,
    private val edgeFade: Float
) {
    private var size = Size.Unspecified
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    fun forSize(size: Size): androidx.compose.ui.graphics.RenderEffect? {
        if (size != this.size) {
            this.size = size
            effect = bugdroidEffect(clip, size.width, size.height, scaleFactor, dark, edgeFade)?.asComposeRenderEffect()
        }
        return effect
    }
}

/** RenderEffect blurs take a radius, which Android turns into a sigma of 0.57735 * radius + 0.5. */
internal fun blurRadiusForSigma(sigma: Float): Float = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
