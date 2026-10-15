package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import android.graphics.RuntimeShader
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*

@Composable
fun BugdroidPlayer(
    player: androidx.media3.exoplayer.ExoPlayer?,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f,
    videoAspect: Float = 16f / 9f
) {
    val shaderSrc = """
        uniform shader content;
        half4 main(float2 coords) {
            half4 color = content.eval(coords);
            // Perceptually weighted Rec. 709 luminance (Green dominance: 0.7152)
            float luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
            // Smooth cubic Hermite keying with edge antialiasing
            float alpha = smoothstep(0.14, 0.28, luma);
            // Premultiplied alpha output preventing dark fringe halos
            return half4(color.rgb * alpha, alpha);
        }
    """.trimIndent()

    val cachedVideoRenderEffect = remember(shaderSrc) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val shader = android.graphics.RuntimeShader(shaderSrc)
            val frameworkEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
            frameworkEffect.asComposeRenderEffect()
        } else null
    }

    val surfaceHolder = remember {
        object {
            var currentSurface: android.view.Surface? = null
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                isOpaque = false
                val adjustAspectRatio: (android.view.TextureView) -> Unit = { tv ->
                    val vw = tv.width
                    val vh = tv.height
                    if (vw > 0 && vh > 0) {
                        val matrix = android.graphics.Matrix()
                        val viewAspect = vw.toFloat() / vh.toFloat()
                        var scaleX = 1f
                        var scaleY = 1f
                        if (viewAspect > videoAspect) {
                            scaleY = (vw.toFloat() / videoAspect) / vh.toFloat()
                        } else {
                            scaleX = (vh.toFloat() * videoAspect) / vw.toFloat()
                        }
                        scaleX *= scaleFactor
                        scaleY *= scaleFactor
                        matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
                        tv.setTransform(matrix)
                    }
                }

                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surfaceHolder.currentSurface?.release()
                        val surface = android.view.Surface(surfaceTexture)
                        surfaceHolder.currentSurface = surface
                        player?.setVideoSurface(surface)
                        adjustAspectRatio(this@apply)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        adjustAspectRatio(this@apply)
                    }

                    override fun onSurfaceTextureDestroyed(
                        surfaceTexture: android.graphics.SurfaceTexture
                    ): Boolean {
                        surfaceHolder.currentSurface?.let {
                            player?.clearVideoSurface(it)
                            it.release()
                        }
                        surfaceHolder.currentSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(
                        surfaceTexture: android.graphics.SurfaceTexture
                    ) {}
                }

                if (isAvailable && surfaceTexture != null) {
                    val surface = surfaceHolder.currentSurface ?: android.view.Surface(surfaceTexture).also {
                        surfaceHolder.currentSurface = it
                    }
                    player?.setVideoSurface(surface)
                    adjustAspectRatio(this@apply)
                }
            }
        },
        update = { tv ->
            val existingSurface = surfaceHolder.currentSurface
            if (existingSurface != null) {
                player?.setVideoSurface(existingSurface)
            } else if (tv.isAvailable && tv.surfaceTexture != null) {
                val surface = android.view.Surface(tv.surfaceTexture)
                surfaceHolder.currentSurface = surface
                player?.setVideoSurface(surface)
            }
        },
        modifier = modifier.graphicsLayer {
            renderEffect = cachedVideoRenderEffect
        }
    )
}
