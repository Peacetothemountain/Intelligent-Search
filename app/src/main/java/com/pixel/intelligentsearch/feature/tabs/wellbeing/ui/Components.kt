package com.pixel.intelligentsearch.feature.tabs.wellbeing.ui

import com.pixel.intelligentsearch.feature.tabs.ui.*
import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.tabs.ui.pageContainer
import com.pixel.intelligentsearch.feature.tabs.ui.matteCard
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// The page uses the preview's shared kit (com.pixel.intelligentsearch.feature.tabs.ui): its springs, its one button behavior, its buttons,
// its matte card and its glass. What is here is Wellbeing's own type scale and layout helpers.

// ---------------------------------------------------------------------- type
internal object WbType {
    private fun s(size: TextUnit, line: TextUnit, weight: Int, tracking: TextUnit = 0.sp) = TextStyle(
        fontFamily = GoogleSansFlex, fontSize = size, lineHeight = line, fontWeight = FontWeight(weight), letterSpacing = tracking,
        fontFeatureSettings = "tnum"
    )
    val label12 = s(12.sp, 16.sp, 500, 0.3.sp)
    val body12 = s(12.sp, 16.sp, 400, 0.2.sp)
    val body14 = s(14.sp, 20.sp, 400, 0.2.sp)
    val label14 = s(14.sp, 20.sp, 500, 0.1.sp)
    val value14 = s(14.sp, 20.sp, 700)
    val title16 = s(16.sp, 24.sp, 700)
    val name16 = s(16.sp, 21.sp, 500)
    val body16 = s(16.sp, 24.sp, 400)
    val title20 = s(20.sp, 27.sp, 500)
    val title22 = s(22.sp, 28.sp, 500)
    val headline28 = s(28.sp, 36.sp, 500)
    val title30 = s(30.sp, 35.sp, 500, (-0.2).sp)
    val display36 = s(36.sp, 44.sp, 500)
    val display45 = s(45.sp, 52.sp, 500)
    val display57 = s(57.sp, 64.sp, 500, (-0.25).sp)
}

/** Large text: Android's text size at or above 1.3. */
@Composable
internal fun largeText(): Boolean = LocalDensity.current.fontScale >= 1.3f

// ---------------------------------------------------------------------- cards
/**
 * A card holding a column, in [color]'s content color: the preview's one card ([matteCard], Intelligent Search's lit
 * matte card) with Wellbeing's inner padding.
 */
@Composable
internal fun MatteCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.pageContainer,
    shape: Shape = RoundedCornerShape(28.dp),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.semantics { isTraversalGroup = true }.matteCard(shape, container = color).padding(padding)) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** A segment of a connected list: 16 outer corners, 4 inner ones. */
internal fun segmentShape(index: Int, count: Int, outer: Dp = 16.dp, inner: Dp = 4.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(top, top, bottom, bottom)
}

/**
 * A card's own title, in the style of Intelligent Search's card titles and section headers: bold, Title Case, in the
 * primary color (or the card's own accent on a colored card), and a heading for TalkBack.
 */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) =
    Text(text, style = WbType.title16, color = color, modifier = modifier.semantics { heading() })

@Composable internal fun Gap(h: Dp) = Spacer(Modifier.heightIn(min = h, max = h))
@Composable internal fun HGap(w: Dp) = Spacer(Modifier.width(w))
