package com.pixel.intelligentsearch.feature.settings

import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pixel.intelligentsearch.R

/**
 * The bugdroid clips. They were rendered as 16:9 frames on black and are stored cropped to the area the
 * character ever occupies, with a margin; the crop rectangle lets [BugdroidPlayer] place them exactly where the
 * full frame would have put them. The character rectangle is the furthest the character (balloon, waving arm,
 * fan) reaches in any frame, so the player can keep all of it inside the view. Both are in 1920x1080 pixels.
 */
enum class BugdroidClip(
    @param:RawRes val rawRes: Int,
    val cropLeft: Int, val cropTop: Int, val cropWidth: Int, val cropHeight: Int,
    val characterLeft: Int, val characterTop: Int, val characterRight: Int, val characterBottom: Int
) {
    SETTINGS(R.raw.bugdroid_video, 340, 0, 1216, 1080, 350, 3, 1543, 1073),
    // Rendered at 1280x720; its rectangles are scaled to the shared 1920x1080 frame.
    WELCOME(R.raw.bugdroid_welcome_video, 348, 6, 1335, 1071, 367, 25, 1664, 1059),
    BACKUP(R.raw.bugdroid_backup_video, 586, 14, 1036, 986, 603, 30, 1604, 982);

    companion object {
        const val SOURCE_WIDTH = 1920
        const val SOURCE_HEIGHT = 1080
    }
}

/** The black background is keyed out with a runtime shader, which needs Android 13; without it the clips show as boxes. */
val isBugdroidVideoSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// Rec. 709 luma key: dark pixels become transparent with a smooth edge; output is premultiplied so no dark fringe.
private const val LUMA_KEY_SHADER = """
    uniform shader content;
    half4 main(float2 coords) {
        half4 color = content.eval(coords);
        float luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
        float alpha = smoothstep(0.14, 0.28, luma);
        return half4(color.rgb * alpha, alpha);
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
 * Draws [player]'s video with the black background keyed out. The frame is scaled to fill the view like the
 * original 16:9 render (times [scaleFactor]), with the cropped clip offset back to its place in that frame.
 */
@Composable
fun BugdroidPlayer(
    player: ExoPlayer?,
    clip: BugdroidClip,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f
) {
    val keyEffect = lumaKeyEffect
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
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
        modifier = modifier.graphicsLayer { renderEffect = keyEffect }
    )
}

/**
 * TextureView stretches the (cropped) video over the whole view. This maps it to where that crop sat in the
 * full 1920x1080 frame, then applies the frame's fill-and-scale about the view centre.
 */
internal fun applyClipTransform(view: TextureView, clip: BugdroidClip, scaleFactor: Float) {
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    if (vw <= 0f || vh <= 0f) return
    val sourceW = BugdroidClip.SOURCE_WIDTH.toFloat()
    val sourceH = BugdroidClip.SOURCE_HEIGHT.toFloat()
    val frameAspect = sourceW / sourceH
    // Fill the view with the full frame (cropping the overflow), as the original player did.
    var fillX = 1f
    var fillY = 1f
    if (vw / vh > frameAspect) fillY = (vw / frameAspect) / vh else fillX = (vh * frameAspect) / vw
    val scale = clip.fittedScale(vw, vh, scaleFactor)
    val matrix = Matrix().apply {
        setScale(clip.cropWidth / sourceW, clip.cropHeight / sourceH)
        postTranslate(clip.cropLeft * vw / sourceW, clip.cropTop * vh / sourceH)
        postScale(fillX * scale, fillY * scale, vw / 2f, vh / 2f)
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

/** Empty space kept around a character that had to be shrunk to fit, as a share of the view's shorter side. */
private const val FIT_MARGIN = 0.03f

/**
 * The key effect, compiled once for the whole app. Building it in each player recompiled the shader every time a
 * page with a bugdroid appeared (returning to the main settings page included), costing frames as it opened.
 * A shader that fails to compile on some GPU driver leaves the video unkeyed rather than crashing the page.
 */
private val lumaKeyEffect: androidx.compose.ui.graphics.RenderEffect? by lazy {
    if (!isBugdroidVideoSupported) return@lazy null
    runCatching {
        RenderEffect.createRuntimeShaderEffect(RuntimeShader(LUMA_KEY_SHADER), "content").asComposeRenderEffect()
    }.getOrNull()
}
