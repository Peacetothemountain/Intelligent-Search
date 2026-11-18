package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.core.graphics.withMatrix
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.tabs.ui.MatteGrain
import com.pixel.intelligentsearch.feature.tabs.ui.rememberTick
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.foundation.layout.BoxWithConstraints
import com.pixel.intelligentsearch.feature.settings.oneLineAutoSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import com.pixel.intelligentsearch.core.ui.wheelSteps
import androidx.compose.ui.unit.isSpecified

/* The fan's drawing: the side text, the shadow on the page under the rivet, the forty strips (each a picture of its
 * own, moved on the page by the lens's matrix: nothing is laid out or repainted as the fan turns), and the rivet. */

/** The fan's colors: the page's Material You roles, as the mockup maps them onto paper. */
private class FanColors(
    val card: Color, val on: Color, val onVariant: Color, val outline: Color, val shade: Color, val cardShadow: Color,
    val edge: Color, val primary: Color, val dark: Boolean
)

@Composable
private fun rememberFanColors(): FanColors {
    val s = MaterialTheme.colorScheme
    val dark = s.dark
    return remember(s) {
        val shade = if (dark) Color(0xFF0D0809).let { lerp(s.surfaceContainerLowest, it, 0.5f) } else s.onSecondaryContainer
        FanColors(
            // In the dark the paper is the highest container, so it reads as paper on the page (the mockup's 1.3 to 1),
            // and its cut edge is a firmer hairline, so stacked strips still part.
            card = if (dark) s.surfaceContainerHighest else s.surfaceContainerLowest,
            on = s.onSurface,
            onVariant = s.onSurfaceVariant,
            outline = if (dark) s.outline.copy(alpha = 0.6f) else s.outlineVariant,
            shade = shade,
            cardShadow = if (dark) Color.Black.copy(alpha = 0.42f) else s.onSecondaryContainer.copy(alpha = 0.2f),
            edge = lerp(shade, s.outlineVariant, 0.62f),
            primary = s.primary,
            dark = dark
        )
    }
}

/** WCAG contrast between two colors (a translucent one laid on [b] first). */
private fun contrast(a: Color, b: Color): Float {
    val la = (if (a.alpha < 1f) a.compositeOver(b) else a).luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** A strip's outline (mockup px): the page's generous corners at the tip and a narrow foot under the rivet. */
private fun stripPath(u: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            0f, 0f, FanDims.SW * u, FanDims.SLEN * u,
            CornerRadius(20f * u), CornerRadius(20f * u), CornerRadius(14f * u), CornerRadius(14f * u)
        )
    )
}

/**
 * Where a strip's picture lies on the strip (mockup px): left, top, width and height. It is as wide as the strip, the
 * bar's place at the picture's middle where the picture allows; on a screen squarer than the picture area (a
 * foldable's inner screen, a tablet) it is scaled up to cover that area, centered, and the strip's corners cut it.
 */
internal fun stripPicture(wallRatio: Float, focus: Float): FloatArray {
    val w = max(FanDims.SW, FanDims.PIC_H / wallRatio)
    val h = max(w * wallRatio, FanDims.PIC_H)
    val top = (FanDims.PIC_H / 2f - focus * h).coerceIn(FanDims.PIC_H - h, 0f)
    return floatArrayOf((FanDims.SW - w) / 2f, top, w, h)
}

/** What the page does when the fan is tapped. */
internal class FanActions(
    val onOpen: (Look, Rect, Rect?) -> Unit,
    val isRaisedHidden: (Int) -> Boolean,
    /** Filled in by the fan: where strip i's picture and bar lie on the page. */
    val rects: Array<((Int) -> Pair<Rect, Rect?>?)?>
)

/**
 * All Looks: the swatch fan, as wide as the screen ([bleed] past the page's padding on each side), and never so tall that
 * the upright strip and the spread can't be seen at once (landscape, an unfolded foldable: there it is narrower than
 * the screen, centered). Swipe sideways to turn it, tap a strip peeking out to bring it upright, tap the upright strip
 * (or its name beside it) to open the full preview. On "Your Wallpaper" and "Colors Only" every strip shows the mesh
 * of the wallpaper's colors ([wallColors]) for its picture.
 */
@Composable
internal fun SwatchFan(
    engine: FanEngine, phoneDark: Boolean, bleed: Dp, wallColors: List<Color>, actions: FanActions, rootCoords: () -> LayoutCoordinates?,
    room: Dp = Dp.Unspecified
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val tick = rememberTick()
    val hint = rememberFanHint(engine)
    SideEffect {
        engine.onTick = { CardHaptics.pass(view) }
        engine.onLand = { byHand -> CardHaptics.land(view); if (byHand) hint.turned() }
        engine.onGrab = { CardHaptics.start(view) }
    }
    val c = rememberFanColors()
    val scheme = MaterialTheme.colorScheme
    val hintColors = remember(scheme) {
        HintColors(
            arrow = scheme.primary.copy(alpha = 0.78f).compositeOver(scheme.background),
            glow = if (scheme.dark) scheme.tertiary else lerp(scheme.tertiary, Color(0xFFFF9A3D), 0.45f),
            core = if (scheme.dark) lerp(scheme.tertiary, Color.White, 0.55f) else scheme.tertiaryContainer
        )
    }
    val hintPaints = remember { HintPaints() }
    val window = LocalWindowInfo.current.containerSize
    val screenW = with(density) { (if (view.width > 0) view.width else window.width).toFloat().toDp() }
    val screenH = with(density) { (if (window.height > 0) window.height else view.height).toFloat().toDp() }
    // Three quarters of the window's height at most, and never taller than the list shows under its top bar ([room]),
    // so in a short window (a split screen) the whole fan is in view at once.
    val kWindow = if (screenH.value > 0f) screenH.value * 0.75f / FanDims.FAN_H else Float.MAX_VALUE
    val kH = if (room.isSpecified && room.value > 0f) min(kWindow, room.value / FanDims.FAN_H) else kWindow
    val kW = screenW.value / 360f
    val k = min(kW, kH)
    val unit = k * density.density
    SideEffect { engine.scale = k }
    // The side text grows with the fan, more gently (the square root of its scale); larger text settings grow it already.
    val large = density.fontScale >= 1.3f
    val textK = if (large) 1f else sqrt(max(1f, k))
    // At the largest text the side text has no room beside the strips: it goes under the fan, full width.
    // So does a fan the window's height has made small, so the text keeps its size and the hint never runs over it.
    val under = density.fontScale >= 1.6f || (kH < kW && k < UNDER_BELOW)
    val fanCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val captionBox = remember { arrayOfNulls<Rect>(1) }
    val opener = remember { arrayOfNulls<(Int) -> Unit>(1) }
    val store = LocalWallpaperStore.current
    val wallRatio = store?.size?.let { it.height.toFloat() / it.width } ?: (997f / 448f)

    fun rectsOf(i: Int): Pair<Rect, Rect?>? {
        val root = rootCoords() ?: return null
        val fc = fanCoords[0] ?: return null
        if (!fc.isAttached) return null
        val origin = root.localPositionOf(fc, Offset.Zero)
        val pb = engine.pictureBox(i)
        val pic = Rect(origin.x + pb.left * unit, origin.y + pb.top * unit, origin.x + pb.right * unit, origin.y + pb.bottom * unit)
        val look = fanLook(stripLookId(i))
        val wp = wallpaperOf(look, look.appDark(phoneDark))
        val focus = wp?.focus ?: 0.5f
        val place = stripPicture(wallRatio, focus)
        val barY = place[1] + focus * place[3]
        val bw = 312f * 0.338f
        val bh = barHeight(look).value * 0.338f
        val m = engine.out[i].m
        val pts = listOf(FanDims.SW / 2f - bw / 2f to barY - bh / 2f, FanDims.SW / 2f + bw / 2f to barY + bh / 2f).map { M3.ap(m, it.first, it.second) }
        val bar = Rect(origin.x + pts[0].first * unit, origin.y + pts[0].second * unit, origin.x + pts[1].first * unit, origin.y + pts[1].second * unit)
        return pic to bar
    }
    fun openStrip(i: Int) {
        val r = rectsOf(i) ?: return
        actions.onOpen(fanLook(stripLookId(i)), r.first, r.second)
    }
    opener[0] = ::openStrip
    actions.rects[0] = ::rectsOf

    Column(Modifier.fillMaxWidth()) {
    Box(
        Modifier
            .layout { m, cs ->
                val full = cs.maxWidth + bleed.roundToPx() * 2
                val w = min(full, (360f * k).dp.roundToPx())
                val p = m.measure(cs.copy(minWidth = w, maxWidth = w))
                layout(cs.maxWidth, p.height) { p.place(-bleed.roundToPx() + (full - w) / 2, 0) }
            }
            .fillMaxWidth()
            .height((FanDims.FAN_H * k).dp)
            .onGloballyPositioned { fanCoords[0] = it }
            // One node for TalkBack: the look upright, and actions to turn the fan and open the look (the strips' own
            // printed names stay out of it).
            .clearAndSetSemantics {
                val id = stripLookId(engine.top)
                contentDescription = "All looks, a fan of swatch cards"
                stateDescription = "${fanLook(id).name}, ${lookOfStrip(engine.top) + 1} of ${engine.n}, ${fanGroupOf(id).name}"
                onClick(label = "Open preview") { opener[0]?.invoke(engine.top); true }
                // Only the turns there are: none past either end. The next look is the strip under the upright one.
                customActions = listOfNotNull(
                    if (engine.dest <= 0) null else CustomAccessibilityAction("Next look") {
                        if (engine.dest <= 0) false else { engine.turnTo(engine.dest - 1); true }
                    },
                    if (engine.dest >= engine.n - 1) null else CustomAccessibilityAction("Previous look") {
                        if (engine.dest >= engine.n - 1) false else { engine.turnTo(engine.dest + 1); true }
                    }
                )
            }
            // A mouse wheel or trackpad turns it a look at a time, down for the next; past either end the page scrolls.
            .wheelSteps(engine, canStep = { engine.canWheel(it) }, step = { engine.wheel(it) })
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val d = density.density
                    engine.down(down.position.x / d, down.position.y / d, down.uptimeMillis)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            // The release speed comes from the touch input itself (the engine keeps its last tenth of a
                            // second of travel around the rivet).
                            val wasDrag = engine.dragging
                            if (wasDrag) ch.consume()
                            val tap = engine.up(ch.uptimeMillis)
                            if (tap && !ch.isConsumed) {
                                val i = engine.stripAt(ch.position.x / unit, ch.position.y / unit)
                                if (i >= 0) {
                                    tick()
                                    engine.tap(i) { opener[0]?.invoke(it) }
                                } else if (captionBox[0]?.contains(ch.position) == true) {
                                    // The look's name beside the fan opens it too.
                                    tick()
                                    engine.tap(engine.top) { opener[0]?.invoke(it) }
                                }
                            }
                            break
                        }
                        if (ch.positionChange() != Offset.Zero) {
                            ch.historical.forEach { h -> engine.move(h.position.x / d, h.position.y / d, h.uptimeMillis) }
                            if (engine.move(ch.position.x / d, ch.position.y / d, ch.uptimeMillis)) ch.consume()
                        } else if (engine.dragging) ch.consume()
                    }
                }
            }
    ) {
        // Where the fan is narrower than the screen (landscape), the side text may use the room to its right too.
        val sideRoom = ((screenW.value - 360f * k) / 2f).coerceAtLeast(0f).dp
        if (!under) FanCaption(
            engine, textK, large,
            Modifier
                .padding(start = (184f * k).dp, top = (24f * k).dp)
                .layout { m, cs ->
                    val wpx = (cs.maxWidth - 16.dp.roundToPx() + sideRoom.roundToPx()).coerceAtLeast(0)
                    val p = m.measure(cs.copy(minWidth = wpx, maxWidth = wpx))
                    layout(cs.maxWidth, p.height) { p.place(0, 0) }
                }
                .onGloballyPositioned { cc -> captionBox[0] = fanCoords[0]?.takeIf { it.isAttached }?.localBoundingBoxOf(cc, clipBounds = false) }
        )
        // The deck's own shadow on the page, under the rivet.
        Box(
            Modifier
                .fillMaxSize()
                .bakedBlur(c.cardShadow, 15.dp, 4f, bounds = { Rect(Offset.Zero, size) }) {
                    val u = unit
                    Path().apply { addOval(Rect((FanDims.RX - 62f) * u, (FanDims.RY - 50f) * u, (FanDims.RX + 74f) * u, (FanDims.RY + 46f) * u)) }
                }
        )
        for (i in 0 until engine.n) key(i) {
            StripSlot(engine, i, unit, c, hidden = { actions.isRaisedHidden(i) }) { rec ->
                // The shadows its edges throw: turned out around the rivet as far as the gap on that side allows.
                EdgeShadow(left = true, k = k, color = c.cardShadow) { engine.out[i].leftShadow }
                EdgeShadow(left = false, k = k, color = c.cardShadow) { engine.out[i].rightShadow }
                StripFace(engine, i, fanLook(stripLookId(i)), phoneDark, k, c, wallRatio, wallColors, rec, hidden = { actions.isRaisedHidden(i) })
            }
        }
        // The gesture hint, over the strips (one swinging through the gap under a finger never hides it) and under the
        // rivet. A layer of its own: its light moving redraws nothing else.
        Box(Modifier.fillMaxSize().drawBehind { drawFanHint(engine, hint, unit, hintColors, hintPaints) })
        // The one rivet everything turns on: a small eyelet at the foot, metal in the theme's primary.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    engine.frame.intValue
                    val u = unit
                    val m = engine.out[engine.n - 1].m
                    val (rx, ry) = M3.ap(m, FanDims.OX, FanDims.OY)
                    val center = Offset(rx * u, ry * u)
                    val r = 7.5f * u
                    val p = c.primary
                    drawCircle(c.cardShadow, r + 1.5f * u, center + Offset(0f, 1f * u))
                    drawCircle(c.outline, r + 0.8f * u, center)
                    drawCircle(
                        Brush.radialGradient(
                            0f to lerp(c.shade, Color.Black, 0.2f), 0.32f to lerp(c.shade, Color.Black, 0.2f),
                            0.41f to lerp(p, Color.Black, 0.55f), 0.61f to p, 0.8f to lerp(p, Color.White, 0.45f), 1f to lerp(p, Color.Black, 0.3f),
                            center = center, radius = r
                        ),
                        r, center
                    )
                }
        )
    }
    if (under) FanCaption(
        engine, textK, large,
        Modifier
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp)
            // A tap opens the look, as the fan's own "Open preview" does for TalkBack: no node of its own here.
            .pointerInput(engine) { detectTapGestures { tick(); engine.tap(engine.top) { opener[0]?.invoke(it) } } }
    )
    }
}

/**
 * The upright look, named on the page beside the fan (the strips swing over this as they turn), or under it: its kind
 * as a small tonal tag, its name as the hero line, one line about it, and a thin line that fills as the fan moves
 * through the deck. TalkBack hears the fan's own node instead ("n of 40").
 */
@Composable
private fun FanCaption(engine: FanEngine, ts: Float, large: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val id = stripLookId(engine.top)
    val look = fanLook(id)
    val g = fanGroupOf(id)
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.secondaryContainer.copy(alpha = 0.72f))
                .padding(start = 7.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(g.icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size((15f * ts).dp))
            Text(
                g.name, color = scheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = max(11.5f, 11.5f * ts).sp, fontWeight = FontWeight.SemiBold)
            )
        }
        // The name keeps to one line (a long one shrinks a little), and the line about it keeps the room the longest one
        // needs here, so the progress line under them holds still as the fan turns.
        val nameStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = (27f * ts).sp, lineHeight = (29f * ts).sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.35).sp)
        Text(
            look.name, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            // A fixed line height, so a long name that shrinks to fit doesn't lift everything under it.
            modifier = Modifier.padding(top = (10f * ts).dp, bottom = 6.dp).height(with(LocalDensity.current) { nameStyle.lineHeight.toDp() }),
            style = nameStyle, autoSize = oneLineAutoSize(nameStyle, shrinkBy = 0.2f)
        )
        val descStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = max(13.5f, 12.5f * ts).sp, lineHeight = (max(13.5f, 12.5f * ts) * 1.38f).sp, lineBreak = LineBreak.Paragraph
        )
        val measurer = rememberTextMeasurer(cacheSize = 0)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wpx = constraints.maxWidth
            val lines = remember(wpx, descStyle) {
                if (wpx <= 0 || wpx == Constraints.Infinity) 2
                else FAN_ORDER.maxOf { measurer.measure(fanLook(it).desc, descStyle, constraints = Constraints(maxWidth = wpx)).lineCount }.coerceIn(1, 3)
            }
            Text(look.desc, color = scheme.onSurfaceVariant, minLines = lines, maxLines = 3, overflow = TextOverflow.Ellipsis, style = descStyle)
        }
        // Where you are in the deck: it fills as the fan moves, strip by strip.
        val track = scheme.outlineVariant
        val fill = scheme.primary
        Box(
            Modifier
                .padding(top = 12.dp)
                .width(if (large) 96.dp else (112f * ts).dp)
                .height(4.dp)
                .drawBehind {
                    engine.frame.intValue
                    val f = ((engine.n - engine.p.coerceIn(0f, engine.n - 1f)) / engine.n).coerceIn(0f, 1f)
                    val cr = CornerRadius(size.height / 2f)
                    drawRoundRect(track, cornerRadius = cr)
                    drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = cr)
                }
        )
    }
}

/**
 * One strip's place: it draws the strip (a picture of its own) through the lens's matrix for this frame, or nothing
 * while the strip is out of sight. Only this small drawing is redone each frame.
 */
@Composable
private fun StripSlot(engine: FanEngine, i: Int, unit: Float, c: FanColors, hidden: () -> Boolean, content: @Composable (GraphicsLayer) -> Unit) {
    val o = engine.out[i]
    // The picture's outline on the strip, for the shade of the spread turned away, which falls on the picture only.
    val picture = remember(unit) {
        Path().apply {
            addRoundRect(
                RoundRect(
                    0f, 0f, FanDims.SW * unit, FanDims.PIC_H * unit,
                    CornerRadius(20f * unit), CornerRadius(20f * unit), CornerRadius.Zero, CornerRadius.Zero
                )
            )
        }
    }
    val matrix = remember { android.graphics.Matrix() }
    val values = remember { FloatArray(9) }
    // The strip, drawn once into a picture of its own: through the lens it is then one textured card, and the shade of
    // the strips over it is a tint on that picture, so turning the fan repaints nothing.
    val rec = rememberGraphicsLayer()
    val shadeAt = remember { floatArrayOf(-1f) }
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                engine.frame.intValue
                if (!o.seen) return@drawWithContent
                // In px: the matrix works in the mockup's px.
                val m = o.m
                values[0] = m[0]; values[1] = m[1]; values[2] = m[2] * unit
                values[3] = m[3]; values[4] = m[4]; values[5] = m[5] * unit
                values[6] = m[6] / unit; values[7] = m[7] / unit; values[8] = m[8]
                matrix.setValues(values)
                val shade = (o.shade * 100f).roundToInt() / 100f
                if (shade != shadeAt[0]) {
                    shadeAt[0] = shade
                    rec.colorFilter = if (shade <= 0f) null else ColorFilter.tint(c.shade.copy(alpha = shade), BlendMode.SrcAtop)
                }
                val nc = drawContext.canvas.nativeCanvas
                nc.withMatrix(matrix) {
                    drawContent()
                    drawLayer(rec)
                    if (o.away > 0.005f && !hidden()) drawPath(picture, c.shade.copy(alpha = o.away))
                }
            }
    ) { content(rec) }
}

/**
 * A strip printed like a paint swatch: the look itself, wallpaper and bar, edge to edge from the tip well past half way
 * down, cut by the strip's own corners; under it, its name in small print up the side and its three colors; the shadows
 * its edges throw; the shade of the strips lying close over it.
 */
@Composable
private fun StripFace(
    engine: FanEngine, i: Int, look: Look, phoneDark: Boolean, k: Float, c: FanColors, wallRatio: Float, wallColors: List<Color>,
    rec: GraphicsLayer, hidden: () -> Boolean
) {
    val density = LocalDensity.current
    val u = k * density.density
    val w = (FanDims.SW * k).dp
    val h = (FanDims.SLEN * k).dp
    val store = LocalWallpaperStore.current
    val wp = if (store != null) wallpaperOf(look, look.appDark(phoneDark)) else null
    // Its full picture is kept while the strip may be in sight, and always for the last strip, which always is.
    val keep by remember(engine, i) { derivedStateOf { i in engine.near || i == engine.n - 1 } }
    if (store != null && wp != null && keep) KeepWallpaper(store, wp, "strip")
    // Read through a derived state: a picture arriving for another strip then doesn't redraw this one (each redraw
    // renders the strip's picture again, and all of them at once was the hitch in a long riffle).
    val img = remember(store, wp) {
        if (store == null || wp == null) null
        else derivedStateOf(referentialEqualityPolicy()) { store.strips[wp.id] ?: store.thumbs[wp.id] }
    }
    val focus = wp?.focus ?: 0.5f
    val pic = stripPicture(wallRatio, focus)
    val barY = pic[1] + focus * pic[3]
    val sw = rememberSwatch(look, phoneDark)
    val schemes = rememberLookSchemes(look)
    val accent = schemes.of(look.appDark(phoneDark)).primary
    // Its three colors, all different: a Material look's rim, pill and icon; another look's pill, icon and its
    // wallpaper's own accent.
    val dots = if (look.hero == Design.MATERIAL) sw else listOf(sw[0], sw[1], accent)
    // A bar that all but vanishes into its wallpaper (Midnight, Aurora, Fog) gets a hairline on the strip only. The bar
    // is judged as it is drawn: faded by its transparency (Fog's light pill is three quarters see-through).
    val faint = remember(sw, wp, look) {
        val shown = WidgetRules.backdropAlphas(look.heroStyle, look.hero == Design.MATERIAL).backdrop
        wp != null && contrast(sw[0].copy(alpha = sw[0].alpha * shown), Color(wp.base)) < 1.2f
    }
    val barCorner = remember(look) { BarShape.of(look.heroStyle, look.hero == Design.MATERIAL).outerRadius.toFloat() }
    val nameShown = engine.nameAlpha[i].floatValue
    val family = MaterialTheme.typography.bodyMedium.fontFamily
    val measurer = rememberTextMeasurer(cacheSize = 0)
    fun sp(v: Float) = with(density) { (v * k).dp.toSp() }
    // Its name, shrunk a little to fit the paper below the picture when it is long (never under 7.2).
    val nameSize = remember(look.name, k, family) {
        val wide = measurer.measure(
            look.name.uppercase(), TextStyle(fontSize = sp(8.5f), fontWeight = FontWeight.SemiBold, letterSpacing = sp(0.8f), fontFamily = family),
            softWrap = false, maxLines = 1
        ).size.width
        8.5f * ((92f * u) / wide.coerceAtLeast(1)).coerceIn(7.2f / 8.5f, 1f)
    }
    Box(
        Modifier
            .requiredSize(w, h)
            .graphicsLayer { }
            // Recorded into the strip's picture, drawn by its slot through the lens; nothing is drawn here.
            .drawWithContent {
                rec.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                rec.record { this@drawWithContent.drawContent() }
            }
    ) {
        // The paper: its cut edge's thickness showing on the lower right, its face, and the hairline where it was cut.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val path = stripPath(u)
                    val edge = stripPath(u).apply { translate(Offset(1f * u, 1.2f * u)) }
                    onDrawBehind {
                        drawPath(edge, c.edge)
                        drawPath(path, c.card)
                        drawPath(path, c.outline, style = Stroke(0.8f * u))
                    }
                }
        )
        // The picture: the look's wallpaper as wide as the strip, the bar where it sits on it. On "Your Wallpaper" and
        // "Colors Only" there is no picture of the look's own: the wallpaper's colors stand in for it.
        Box(
            Modifier
                .size(w, (FanDims.PIC_H * k).dp)
                .graphicsLayer {
                    alpha = if (hidden()) 0f else 1f
                    clip = true
                    shape = RoundedCornerShape(topStart = (20f * k).dp, topEnd = (20f * k).dp)
                }
                .drawBehind {
                    val left = pic[0] * u
                    val top = pic[1] * u
                    val pw = pic[2] * u
                    val ph = pic[3] * u
                    if (store == null || wp == null) {
                        translate(left, top) { drawWallMesh(wallColors, Size(pw, ph)) }
                        return@drawBehind
                    }
                    val image = img?.value
                    if (image != null) drawImage(
                        image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(pw.roundToInt(), ph.roundToInt()),
                        filterQuality = FilterQuality.Medium
                    ) else drawRect(Color(wp.base))
                }
        ) {
            val scale = 0.338f * k
            val bw = 312f * scale
            val bh = barHeight(look).value * scale
            Box(
                Modifier
                    .offset((FanDims.SW * k / 2f - bw / 2f).dp, (barY * k - bh / 2f).dp)
                    .then(
                        if (!faint) Modifier
                        else Modifier.drawWithContent {
                            drawContent()
                            val r = min(barCorner * scale * density.density, size.height / 2f)
                            drawRoundRect(sw[if (look.hero == Design.MATERIAL) 2 else 1].copy(alpha = 0.35f), cornerRadius = CornerRadius(r), style = Stroke(0.7f * u))
                        }
                    )
            ) {
                GalleryBar(look, phoneDark, scale = scale)
            }
        }
        // Small print up the left side: its name, ending at the picture, and its three colors beside it. On the spread
        // the strip over it hides all but a few letters, so the name isn't printed there.
        if (nameShown > 0f) Box(Modifier.offset((7f * k).dp, (165f * k).dp).size((13f * k).dp, (96f * k).dp), contentAlignment = Alignment.Center) {
            Text(
                look.name.uppercase(), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.End,
                color = c.on.copy(alpha = c.on.alpha * nameShown),
                style = TextStyle(fontSize = sp(nameSize), fontWeight = FontWeight.SemiBold, letterSpacing = sp(0.8f * nameSize / 8.5f), lineHeight = sp(13f), fontFamily = family),
                modifier = Modifier.requiredWidth((96f * k).dp).rotate(-90f)
            )
        }
        Column(Modifier.offset((24f * k).dp, (167f * k).dp), verticalArrangement = Arrangement.spacedBy((3f * k).dp)) {
            for (col in dots) {
                // A color close to the paper's gets a ring, so it still shows.
                val ring = if (contrast(col, c.card) < 1.5f) c.on.copy(alpha = 0.3f) else c.outline
                Box(
                    Modifier
                        .size((9f * k).dp)
                        .clip(RoundedCornerShape((3f * k).dp))
                        .background(col)
                        .drawBehind { drawRoundRect(ring, cornerRadius = CornerRadius(3f * u), style = Stroke(0.6f * u)) }
                )
            }
        }
        // A printed finish: the page's matte grain, and the light falling off toward the rivet under the picture.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val path = stripPath(u)
                    val grain = if (MatteGrain.drawn) MatteGrain.brush(1) else null
                    val tone = Brush.verticalGradient(0.6f to Color.Transparent, 1f to c.shade.copy(alpha = 0.24f))
                    onDrawBehind {
                        clipPath(path) {
                            drawRect(tone)
                            if (grain != null) drawRect(grain, alpha = if (c.dark) 0.022f else 0.014f)
                        }
                    }
                }
        )
    }
}

/**
 * The shadow one edge of a strip throws: a band lying wholly behind the strip until it is turned out a little around
 * the rivet, nothing at the rivet, widest at the tip. Drawn once; only turned.
 */
@Composable
private fun EdgeShadow(left: Boolean, k: Float, color: Color, angle: () -> Float) {
    val bw = 28f
    val x = if (left) 0f else FanDims.SW - bw
    val originX = (FanDims.OX - x) / bw
    Box(
        Modifier
            .offset((x * k).dp, (20f * k).dp)
            .size((bw * k).dp, (232f * k).dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(originX, 1f)
                rotationZ = if (left) -angle() else angle()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val wpx = size.width
                val edge = 17f / bw
                val across = if (left) Brush.horizontalGradient(0f to Color.Transparent, edge to color, 1f to color, startX = 0f, endX = wpx)
                else Brush.horizontalGradient(0f to color, 1f - edge to color, 1f to Color.Transparent, startX = 0f, endX = wpx)
                val fade = Brush.verticalGradient(0f to Color.Transparent, (16f / 232f) to Color.Black, 1f to Color.Black)
                onDrawBehind {
                    drawRect(across)
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            }
    )
}

/** A fan the window's height has made smaller than this (of its width's size) has its text under it. */
private const val UNDER_BELOW = 0.8f
