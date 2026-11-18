package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import androidx.compose.runtime.mutableFloatStateOf
import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.pixel.intelligentsearch.feature.tabs.wellbeing.model.Range
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The day mark's three Material shapes (MaterialShapes Sunny, Cookie7Sided and Cookie12Sided, as Intelligent Search's
 * MaterialMorphShapes builds them), one per range, morphing when the range changes.
 */
internal object RangeShapes {
    private val round15 = CornerRounding(0.15f)
    private val round50 = CornerRounding(0.5f)
    val sunny = RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = round15)
    val cookie7 = RoundedPolygon.star(numVerticesPerRadius = 7, innerRadius = 0.75f, rounding = round50)
    val cookie12 = RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.8f, rounding = round50)
    fun of(r: Range) = when (r) { Range.Day -> sunny; Range.Week -> cookie7; Range.Month -> cookie12 }
}

/** Fills [polygon] (or [morph] at [progress]) centered in the draw area, turned by [degrees]. */
internal fun DrawScope.drawShape(color: Color, polygon: RoundedPolygon? = null, morph: Morph? = null, progress: Float = 0f, degrees: Float = 0f) {
    val p = android.graphics.Path()
    if (morph != null) morph.toPath(progress, p) else polygon?.toPath(p)
    val r = minOf(size.width, size.height) / 2f
    val m = Matrix()
    m.postRotate(degrees - 90f)
    m.postScale(r, r)
    m.postTranslate(size.width / 2f, size.height / 2f)
    p.transform(m)
    drawPath(p.asComposePath(), color)
}

/** A Material shape of [size] in [color] with [icon] in [iconColor] inside: the day mark, the empty-state mark. */
@Composable
internal fun ShapeMark(range: Range, size: Dp, color: Color, icon: ImageVector?, iconColor: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    val animate by rememberAnimationsEnabled()
    var from by remember { mutableStateOf(range) }
    var to by remember { mutableStateOf(range) }
    val progress = remember { Animatable(1f) }
    var turn by remember { mutableFloatStateOf(0f) }
    var shownIcon by remember { mutableStateOf(icon) }
    LaunchedEffect(range) {
        if (range == to) return@LaunchedEffect
        from = to
        to = range
        if (!animate) { progress.snapTo(1f); shownIcon = icon; return@LaunchedEffect }
        progress.snapTo(0f)
        turn += 45f
        progress.animateTo(1f, Springs.morph()) { if (value >= 0.5f) shownIcon = icon }
    }
    LaunchedEffect(icon) { if (progress.value >= 0.5f || !animate) shownIcon = icon }
    val morph = remember(from, to) { Morph(RangeShapes.of(from), RangeShapes.of(to)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            drawShape(color, morph = morph, progress = p.coerceIn(0f, 1f), degrees = turn - 45f * (1f - p))
        }
        shownIcon?.let { Icon(it, null, Modifier.size(iconSize), tint = iconColor) }
    }
}

/** A still Material shape. */
@Composable
internal fun StillShape(range: Range, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawShape(color, RangeShapes.of(range)) }
}

// ---------------------------------------------------------------------- app icons
internal object AppIcons {
    private val cache = HashMap<String, ImageBitmap?>()
    var enabled = true

    fun get(context: android.content.Context, key: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(key) {
            if (!enabled) null else runCatching {
                context.packageManager.getApplicationIcon(key).toBitmap(144, 144).asImageBitmap()
            }.getOrNull()
        }
    }
}

/** An app's own icon from the phone, at [size]; while it loads, or for an app the phone no longer has, a tinted circle. */
@Composable
internal fun AppIcon(key: String, size: Dp, modifier: Modifier = Modifier, fallback: ImageVector? = null) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, key) { value = withContext(Dispatchers.IO) { AppIcons.get(context, key) } }
    val b = bitmap
    if (b != null) {
        Image(b, null, modifier.size(size))
    } else {
        Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            if (fallback != null) Icon(fallback, null, Modifier.size(size * 0.5f), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
