package com.pixel.intelligentsearch.feature.tabs.gallery

import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.tabs.ui.pageContainer
import com.pixel.intelligentsearch.feature.tabs.ui.rememberTick
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Whether a scheme is the dark one. */
internal val androidx.compose.material3.ColorScheme.dark: Boolean get() = background.luminance() < 0.5f

/** The check badge on the look that is on. */
@Composable
internal fun CheckBadge(size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(size).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Symbols.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.68f))
    }
}

/**
 * The search page as a drawn summary (gallery_design.md 5.2): the wallpaper's main color under the look's dimming, or
 * the plain surface; the field in its opacity; rows in the phone's colors. Opaque, so it never fights the backdrop.
 */
@Composable
internal fun SearchTile(look: Look, phoneDark: Boolean, wallMain: Color, big: Boolean, modifier: Modifier = Modifier) {
    val schemes = rememberLookSchemes(look)
    val dark = look.appDark(phoneDark)
    val s = schemes.of(dark)
    val q = look.search
    val scrimColor = if (dark) Color.Black else s.surfaceContainer
    val scrimAlpha = (1f - q.transparency / 100f) * MAX_OVERLAY_SCRIM
    // With the look's own wallpaper behind it, search dims that wallpaper's main color.
    val store = LocalWallpaperStore.current
    val own = if (store != null) wallpaperOf(look, dark) else null
    val main = if (store != null && own != null) Color(store.palettes[own.id]?.main ?: own.base) else wallMain
    val fill = if (q.wallpaper) scrimColor.copy(alpha = scrimAlpha).compositeOver(main) else s.surface
    val field = s.surfaceContainerHigh.copy(alpha = q.pillOpacity / 100f).compositeOver(fill)
    val accent = s.primary
    val line = s.onSurface.copy(alpha = 0.4f).compositeOver(fill)
    val outline = s.outlineVariant.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(if (big) 18.dp else 14.dp)
    Canvas(
        modifier
            .size(if (big) 88.dp else 48.dp, if (big) 156.dp else 48.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, outline, shape)
    ) {
        val u = if (big) 1f else 0.5f
        val padX = (if (big) 10.dp else 6.dp).toPx()
        var y = (if (big) 12.dp else 7.dp).toPx()
        val fh = (if (big) 18.dp else 9.dp).toPx()
        drawRoundRect(field, Offset(padX, y), Size(size.width - 2 * padX, fh), CornerRadius(fh / 2))
        y += fh + (if (big) 14.dp else 5.dp).toPx()
        val rh = (if (big) 14.dp else 7.dp).toPx()
        repeat(if (big) 4 else 2) {
            drawCircle(accent, rh / 2, Offset(padX + rh / 2, y + rh / 2))
            val lh = (if (big) 4.dp else 3.dp).toPx()
            drawRoundRect(line, Offset(padX + rh + (if (big) 7.dp else 4.dp).toPx(), y + rh / 2 - lh / 2), Size(size.width - 2 * padX - rh - 7.dp.toPx() * u, lh), CornerRadius(lh / 2))
            y += rh + (if (big) 11.dp else 5.dp).toPx()
        }
    }
}

/** What to know before applying: short lines, each with its icon. */
@Composable
internal fun FactsRow(facts: List<Fact>, modifier: Modifier = Modifier) {
    if (facts.isEmpty()) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium
    // As tall as a line of the text beside it, so it grows with the text size and sits centered on its first line.
    val line = with(androidx.compose.ui.platform.LocalDensity.current) { style.lineHeight.toDp() }
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (f in facts) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(line * 0.32f)) {
                val mod = Modifier.size(line * 0.94f)
                if (f.icon == FactIcon.G) Icon(painterResource(R.drawable.gallery_ic_g_logo), null, tint = color, modifier = mod)
                else Icon(f.icon.vector(), null, tint = color, modifier = mod)
                Text(f.text, style = style, color = color)
            }
        }
    }
}

private fun FactIcon.vector(): ImageVector = when (this) {
    FactIcon.Dark -> Symbols.DarkMode
    FactIcon.Light -> Symbols.LightMode
    FactIcon.Info -> Symbols.Info
    FactIcon.Contrast -> Symbols.Contrast
    FactIcon.Calendar -> Symbols.CalendarMonth
    FactIcon.Palette -> Symbols.Palette
    FactIcon.Paint -> Symbols.FormatPaint
    FactIcon.G -> Symbols.Info
}

@Composable
internal fun ProvideContent(color: Color, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color, content = content)

@Composable
internal fun RowScope.Gap(w: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.size(w, 0.dp))
