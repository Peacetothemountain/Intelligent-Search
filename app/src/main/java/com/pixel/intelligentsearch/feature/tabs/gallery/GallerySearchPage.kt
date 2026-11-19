package com.pixel.intelligentsearch.feature.tabs.gallery

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import com.pixel.intelligentsearch.core.ui.icons.filled.WbCloudy
import com.pixel.intelligentsearch.core.ui.symbols.Symbols as AppSymbols
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import com.pixel.intelligentsearch.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ── OverlayBackdrop.kt ── */

/** The most the overlay dims the wallpaper behind it: at Background Transparency 0. */
internal const val MAX_OVERLAY_SCRIM = 0.7f

/** How much of the extra dim stands in for blur where the phone has blur off. */
internal const val BLUR_FALLBACK_DIM = 0.35f

internal fun overlayScrimAlpha(transparency: Int, blur: Int, blurAvailable: Boolean): Float {
    val base = (1f - transparency.coerceIn(0, 100) / 100f) * MAX_OVERLAY_SCRIM
    if (blurAvailable) return base
    return base + (1f - base) * BLUR_FALLBACK_DIM * (blur.coerceIn(0, 100) / 100f)
}

/** Dark: the scheme's scrim, which dims. Light: surfaceContainer, which washes the wallpaper out toward the panels. */
internal fun overlayScrimColor(scheme: ColorScheme): Color = if (scheme.dark) scheme.scrim else scheme.surfaceContainer

/* ── The Gemini input plate (core/ui/DynamicMeshShader.kt materialYouGeminiPlate), copied unchanged ── */

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

/** materialYouGeminiPlate at [alpha] (Search Pill Opacity). The idle flow of its bottom light is not ported. */
@Composable
private fun Modifier.geminiPlate(cornerRadius: Dp, alpha: Float): Modifier {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.dark
    val center = if (isDark) lerp(Color(0xFF060305), scheme.primaryContainer, 0.025f) else scheme.surfaceContainerLowest
    val outer = if (isDark) lerp(Color(0xFF090608), scheme.primaryContainer, 0.24f) else scheme.surfaceContainerHigh
    val bottomLift = if (isDark) lerp(Color(0xFF160E14), scheme.primaryContainer, 0.46f) else scheme.surfaceContainerHigh
    val topRim = if (isDark) lerp(Color(0xFF0B070A), scheme.primaryContainer, 0.32f) else outer
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(AGSL_GEMINI_PLATE) }.getOrNull() else null
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shader == null) {
        // Below Android 13 (or where the shader can't compile) the app draws an edge-glow mesh; a plain fill stands in.
        return this.clip(RoundedCornerShape(cornerRadius)).background(outer.copy(alpha = alpha))
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    return this.drawWithCache {
        shader.setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setFloatUniform("uRadius", cornerRadius.toPx())
        shader.setFloatUniform("uDp", density)
        shader.setColorUniform("uCenterColor", center.toArgb())
        shader.setColorUniform("uOuterColor", outer.toArgb())
        shader.setColorUniform("uBottomLiftColor", bottomLift.toArgb())
        shader.setColorUniform("uTopRimColor", topRim.toArgb())
        onDrawBehind { drawRect(brush = brush, alpha = alpha.coerceIn(0f, 1f)) }
    }
}

/* ── The search bar (OverlaySearchBar), idle: lens, "Search…", the sparkle mic and the settings bars ── */

@Composable
internal fun SearchBarPreview(look: Look, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isLight = !scheme.dark
    val schemes = rememberLookSchemes(look)
    val colors = remember(schemes, isLight) { IconColors.of(schemes.accents(isLight), isLight) }
    // The lens and mic follow the hero design's G icon style (rememberSearchIconPalette).
    val g = look.heroStyle.gIconTheme
    val accents = schemes.accents(isLight)
    val (rim, top, handle, sparkle) = when (g) {
        G_ACCENTED -> List(4) { Color(accents.primary) }
        G_SYSTEM -> listOf(Color(0xFF4285F4), Color(0xFFFBBC05), Color(0xFF34A853), Color(0xFFEA4335))
        else -> listOf(Color(colors.lensRim), Color(colors.lensTop), Color(colors.lensHandle), Color(colors.lensSparkle))
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(100.dp))
            .geminiPlate(100.dp, look.search.pillOpacity / 100f)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LensDrawing(rim, top, handle, sparkle, Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            // Cut short with an ellipsis, never mid-letter, where a narrow window leaves it little room.
            "Search…", color = scheme.onSurfaceVariant, fontSize = 20.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            SparkleMicDrawing(
                Color(colors.micCapsule), Color(colors.micStem), Color(colors.micLeftCradle), Color(colors.micRightCradle),
                Color(colors.micCapsule), Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(48.dp).padding(12.dp)) {
            androidx.compose.material3.Icon(painterResource(R.drawable.gallery_ic_three_bars), null, tint = scheme.onSurface)
        }
    }
}

/* ── Quick-app pills (SearchPill), with the phone's own apps where it has them ── */

private class SampleApp(val label: String, val packages: List<String>, val fallback: androidx.compose.ui.graphics.vector.ImageVector)

private val SAMPLE_APPS = listOf(
    SampleApp("Camera", listOf("com.google.android.GoogleCamera", "com.android.camera2"), SampleIcons.Camera),
    SampleApp("Messages", listOf("com.google.android.apps.messaging"), AppSymbols.Outlined.ChatBubbleOutline),
    SampleApp("Maps", listOf("com.google.android.apps.maps"), SampleIcons.Map),
    SampleApp("Photos", listOf("com.google.android.apps.photos"), AppSymbols.Outlined.Image),
)

@Composable
private fun QuickAppPills(scale: Float) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val icons = remember(context) {
        SAMPLE_APPS.map { app ->
            app.packages.firstNotNullOfOrNull { pkg ->
                runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }
        }
    }
    // Only the pills that fit whole across the page show; in a narrow window the last ones went off its edge.
    FitRow(spacing = 8.dp, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        SAMPLE_APPS.forEachIndexed { i, app ->
            val shape = RoundedCornerShape(24.dp)
            Row(
                Modifier
                    .clip(shape)
                    .background(scheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, scheme.outline.copy(alpha = 0.15f), shape)
                    .padding(horizontal = (12 * scale).dp, vertical = (8 * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bmp = icons[i]
                if (bmp != null) Image(BitmapPainter(bmp), null, Modifier.size((22 * scale).dp))
                else Icon(app.fallback, null, tint = scheme.primary, modifier = Modifier.size((22 * scale).dp))
                Spacer(Modifier.width((8 * scale).dp))
                Text(app.label, color = scheme.onSurfaceVariant, fontSize = (14 * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/* ── At a Glance (OverlayAtAGlance GlanceCard) with its clock (GlanceClockFace) ── */

private val round15 = CornerRounding(radius = 0.15f)
private val round30 = CornerRounding(radius = 0.3f)
private val round50 = CornerRounding(radius = 0.5f)

/** MaterialShapes' Cookie12Sided, Sunny and Square, normalized to the unit square (MaterialMorphShapes). */
private fun clockShape(style: String): RoundedPolygon = when (style) {
    "sunny" -> RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.8f, rounding = round15)
    "square", "digital" -> RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = round30)
    else -> RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.8f, rounding = round50).let { p ->
        val a = (-90f) / 180f * PI.toFloat()
        val c = cos(a)
        val s = sin(a)
        p.transformed { x, y -> androidx.collection.FloatFloatPair(x * c - y * s, x * s + y * c) }
    }
}.normalized()

private fun shapePath(shape: RoundedPolygon, w: Float, h: Float): Path {
    val path = Path()
    val cubics = shape.cubics
    if (cubics.isEmpty()) return path
    path.moveTo(cubics.first().anchor0X * w, cubics.first().anchor0Y * h)
    for (c in cubics) path.cubicTo(c.control0X * w, c.control0Y * h, c.control1X * w, c.control1Y * h, c.anchor1X * w, c.anchor1Y * h)
    path.close()
    return path
}

/** The clock face at 10:10 with the seconds dot at 12 (a still picture: the preview doesn't tick). */
@Composable
internal fun GlanceClock(style: String, size: Dp = 64.dp) {
    val scheme = MaterialTheme.colorScheme
    val face = scheme.surfaceContainerHighest
    val hourColor = scheme.primary
    val minuteColor = lerp(face, scheme.primary, 0.78f).compositeOver(face)
    val dot = scheme.tertiary
    val shape = remember(style) { clockShape(style) }
    if (style == "digital") {
        Box(
            Modifier.size(size).drawWithCache {
                val p = shapePath(shape, this.size.width, this.size.height)
                onDrawBehind { drawPath(p, face) }
            },
            contentAlignment = Alignment.Center
        ) {
            val fs = (size.value * 0.3f).sp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("10", color = hourColor, fontSize = fs, lineHeight = fs, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("10", color = minuteColor, fontSize = fs, lineHeight = fs, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        return
    }
    Spacer(
        Modifier.size(size).drawWithCache {
            val path = shapePath(shape, this.size.width, this.size.height)
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f
            val handWidth = radius * 0.2f
            onDrawBehind {
                drawPath(path, face)
                if (style == "square") for (k in 0 until 4) {
                    val a = k * PI.toFloat() / 2f
                    drawCircle(minuteColor, radius * 0.075f, Offset(centre.x + sin(a) * radius * 0.74f, centre.y - cos(a) * radius * 0.74f))
                }
                hand(centre, 60f, radius * 0.56f, handWidth, minuteColor)
                hand(centre, 305f, radius * 0.38f, handWidth, hourColor)
                if (style != "square") drawCircle(dot, radius * 0.11f, Offset(centre.x, centre.y - radius * 0.66f))
            }
        }
    )
}

private fun DrawScope.hand(centre: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    val a = degrees / 180f * PI.toFloat()
    drawLine(color, centre, Offset(centre.x + sin(a) * length, centre.y - cos(a) * length), strokeWidth = width, cap = StrokeCap.Round)
}

@Composable
private fun GlanceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(title, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The search page's empty state with fixed sample content: the search bar, quick apps, the At a Glance card and two
 * sample results (a setting and a calculation). Drawn in the look's light or dark, always in the phone's colors.
 * [reveal] (0 to 1) opens each part in height in turn; nothing fades. As on the person's real page, the bar is at the
 * bottom when theirs is ([layout] fills the height given then) and the clock shows only when theirs does.
 * [fieldShift] gets the bar's top in the page and gives how far it is moved from there.
 */
@Composable
internal fun SearchPageContent(
    look: Look, reveal: () -> Float, fieldShift: (Float) -> Float, layout: GalleryLive.PageLayout, modifier: Modifier = Modifier
) {
    val clock = look.search.clock ?: "cookie"
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    var barTop by remember { mutableFloatStateOf(0f) }
    val bar = @Composable {
        Box(
            Modifier
                .onPlaced { barTop = it.positionInParent().y }
                .graphicsLayer { translationY = fieldShift(barTop) }
        ) { SearchBarPreview(look) }
    }
    val apps = @Composable { Box(Modifier.revealed(reveal, 0.1f)) { QuickAppPills(if (fontScale >= 1.3f) 0.8f else 1f) } }
    // The bar always shows, measured first; the quick apps and then the cards take the room left and keep only what
    // fits whole (in a short window, large text or a third of a split screen they squeezed the bar to nothing).
    if (layout.barAtBottom) {
        Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            SampleCards(look, reveal, clock.takeIf { layout.showsClock }, nearEnd = true, apps, Modifier.weight(1f, fill = false))
            bar()
        }
    } else {
        Column(modifier) {
            bar()
            SampleCards(look, reveal, clock.takeIf { layout.showsClock }, nearEnd = false, apps, Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * The quick [apps] next to the bar, then the At a Glance card, with the person's clock when they show it ([clock]),
 * and two sample results. Where they don't all fit, those farthest from the bar go: with the bar under them
 * ([nearEnd]) the top ones, else the bottom ones.
 */
@Composable
private fun SampleCards(
    look: Look, reveal: () -> Float, clock: String?, nearEnd: Boolean, apps: @Composable () -> Unit, modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)) }
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    // The two cards are 8 dp apart; the apps sit right against them, as before.
    FitColumn(keepEnd = nearEnd, gapAfter = { i -> if (i == (if (nearEnd) 0 else 1)) 8.dp else 0.dp }, modifier = modifier) {
        if (!nearEnd) apps()
        Box(Modifier.revealed(reveal, 0.2f).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(
                Modifier
                    .widthIn(max = if (fontScale >= 1.3f) 1000.dp else 300.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .geminiPlate(28.dp, look.search.pillOpacity / 100f)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        "At a Glance", color = scheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (clock != null) GlanceClock(clock)
                }
                GlanceRow(Icons.Filled.WbCloudy, "Partly cloudy · 54°", today)
            }
        }
        Box(Modifier.revealed(reveal, 0.3f).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).geminiPlate(28.dp, look.search.pillOpacity / 100f).padding(6.dp)
            ) {
                GlanceRow(Icons.Rounded.Settings, "Display", "Settings")
                GlanceRow(AppSymbols.Outlined.Calculate, "9 × 9 = 81", "Calculator")
            }
        }
        if (nearEnd) apps()
    }
}

/**
 * A column of [content], [gapAfter] each child, that keeps only the children that fit whole in the height it is given:
 * from the top, or from the bottom with [keepEnd] (the ones at the top going first).
 */
@Composable
private fun FitColumn(keepEnd: Boolean, gapAfter: (Int) -> Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        fun gap(between: Int) = gapAfter(between).roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val order = if (keepEnd) placeables.indices.reversed() else placeables.indices
        val kept = ArrayList<Int>()
        var used = 0
        for (i in order) {
            val need = used + (if (kept.isEmpty()) 0 else gap(if (keepEnd) i else i - 1)) + placeables[i].height
            if (need > constraints.maxHeight) break
            used = need
            kept += i
        }
        kept.sort()
        val width = kept.maxOfOrNull { placeables[it].width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        layout(width, used.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            for (i in kept) {
                placeables[i].place(0, y)
                y += placeables[i].height + gap(i)
            }
        }
    }
}

/** A row of [content] [spacing] apart that keeps only the children, from the start, that fit whole in its width. */
@Composable
private fun FitRow(spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)) }
        var used = 0
        var count = 0
        for (p in placeables) {
            val need = used + (if (count == 0) 0 else gap) + p.width
            if (need > constraints.maxWidth) break
            used = need
            count++
        }
        val height = placeables.take(count).maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            for (p in placeables.take(count)) {
                p.place(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/** Opens in height from its top as [reveal] passes [start], rising a little: a clip, never a fade. */
private fun Modifier.revealed(reveal: () -> Float, start: Float): Modifier = graphicsLayer {
    val p = ((reveal() - start) / (1f - start)).coerceIn(0f, 1f)
    clip = true
    shape = GenericShape { size, _ -> addRect(Rect(0f, 0f, size.width, size.height * p)) }
    translationY = (1f - p) * 44.dp.toPx()
}

/* ── The drifting star matrix (AnimatedMatrixBackground), its GPU path ── */

private const val AGSL_MATRIX_SPARKLE_SHADER = """
    uniform float2 uResolution;
    uniform float uPhase;
    uniform float uSpacing;
    uniform float uSparkleSize;
    uniform float uBaseAlpha;
    uniform float uMinAlpha;
    uniform float uOffsetDivisor;
    layout(color) uniform half4 uBaseColor;

    half4 main(float2 fragCoord) {
        float row = floor(fragCoord.y / uSpacing);
        float rowOffset = mod(row, 2.0) * (uSpacing * 0.5);
        float col = floor((fragCoord.x - rowOffset) / uSpacing);
        float2 center = float2((col + 0.5) * uSpacing + rowOffset, (row + 0.5) * uSpacing);
        float2 d = abs(fragCoord - center);
        if (d.x > uSparkleSize * 1.08 || d.y > uSparkleSize * 1.08) {
            return half4(0.0);
        }
        float cellJitter = fract(sin(row * 12.9898 + col * 78.233) * 43758.5453) * 1.85;
        float offsetPhase = (center.x + center.y) / uOffsetDivisor + cellJitter;
        float wave = (sin(uPhase + offsetPhase) + 1.0) * 0.5;
        float effectiveSize = max(uSparkleSize * (0.85 + 0.18 * wave), 0.001);
        float dist = (sqrt(max(d.x, 0.0)) + sqrt(max(d.y, 0.0))) / sqrt(effectiveSize);
        float shape = 1.0 - smoothstep(0.76, 1.01, dist);
        if (shape <= 0.0) {
            return half4(0.0);
        }
        float rawAlpha = wave * uBaseAlpha + uMinAlpha;
        half finalAlpha = uBaseColor.a * half(clamp(rawAlpha, 0.0, 1.0) * shape);
        return half4(uBaseColor.rgb * finalAlpha, finalAlpha);
    }
"""

/** The Matrix Animation's stars, twinkling as the real search page's do; still with animations off. */
@Composable
internal fun StarMatrix(modifier: Modifier = Modifier, animate: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.dark
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val shader = remember { runCatching { RuntimeShader(AGSL_MATRIX_SPARKLE_SHADER) }.getOrNull() } ?: return
    val brush = remember(shader) { ShaderBrush(shader) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, isDark) {
        if (!animate || !StarMotion.enabled) return@LaunchedEffect
        val period = if (isDark) 8.2f else 7.2f
        var last = 0L
        while (true) {
            kotlinx.coroutines.delay(30)
            withFrameNanos { t ->
                if (last != 0L) phase.floatValue = (phase.floatValue + (t - last) / 1e9f / period * 2f * PI.toFloat()) % (2f * PI.toFloat())
                last = t
            }
        }
    }
    val base = if (isDark) Color(0xFFA6A3B6) else scheme.primary
    Spacer(modifier.fillMaxSize().drawWithCache {
        shader.setFloatUniform("uResolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        shader.setFloatUniform("uSpacing", (if (isDark) 25.dp else 24.dp).toPx())
        shader.setFloatUniform("uSparkleSize", (if (isDark) 3.4.dp else 3.7.dp).toPx())
        shader.setFloatUniform("uBaseAlpha", if (isDark) 0.42f else 0.32f)
        shader.setFloatUniform("uMinAlpha", if (isDark) 0.11f else 0.08f)
        shader.setFloatUniform("uOffsetDivisor", (if (isDark) 95.dp else 80.dp).toPx())
        onDrawBehind {
            shader.setColorUniform("uBaseColor", base.toArgb())
            shader.setFloatUniform("uPhase", phase.floatValue)
            drawRect(brush)
        }
    })
}

/** Screenshot tests hold the stars still, as with Remove animations. */
internal object StarMotion {
    @Volatile var enabled = true
}

/** The sample apps' stand-in icons where the phone hasn't the app (Material Icons' photo_camera and map). */
private object SampleIcons {
    val Camera by lazy {
        com.pixel.intelligentsearch.core.ui.icons.icon(
            "Gallery.Camera",
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0",
            "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
        )
    }
    val Map by lazy {
        com.pixel.intelligentsearch.core.ui.icons.icon(
            "Gallery.Map",
            "M20.5,3l-0.16,0.03L15,5.1 9,3 3.36,4.9c-0.21,0.07 -0.36,0.25 -0.36,0.48V20.5c0,0.28 0.22,0.5 0.5,0.5l0.16,-0.03L9,18.9l6,2.1 5.64,-1.9c0.21,-0.07 0.36,-0.25 0.36,-0.48V3.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM15,19l-6,-2.11V5l6,2.11V19z"
        )
    }
}
