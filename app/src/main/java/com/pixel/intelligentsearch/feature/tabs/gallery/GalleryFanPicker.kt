package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.tabs.ui.ButtonKind
import com.pixel.intelligentsearch.feature.tabs.ui.PreviewButton
import com.pixel.intelligentsearch.feature.tabs.ui.Springs
import com.pixel.intelligentsearch.feature.tabs.ui.Symbols
import com.pixel.intelligentsearch.feature.tabs.ui.bouncyClickable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.ui.wheelSteps

/*
 * The counter beside All Looks and the number picker it opens (the approved mockup, round b4). The counter says where
 * the fan is in the deck, "4/40", rolling like an odometer as the fan turns. A tap on it grows a small card out of it:
 * a wheel of every look's number on the left, the chosen one in a primary container pill with its neighbors smaller and
 * fainter, and on the right the look that number is (its bar on its backdrop, its kind, its name), changing as the wheel
 * turns. Go to Look, the preview or a tap on a number turns the fan there.
 */

/**
 * Where the fan is in the deck, rolling with it: only its own small drawing is redone as the fan turns. 26 dp tall in a
 * 48 dp touch target that takes no more room in its row than the pill does.
 */
@Composable
internal fun FanCounter(engine: FanEngine, rootCoords: () -> LayoutCoordinates?, onOpen: (Rect) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val digits = remember(style, density) { with(density) { measurer.measure("${engine.n}", style).size.width.toDp() } }
    val on = scheme.onPrimaryContainer
    val pill = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val at = lookOfStrip(engine.top) + 1
    Box(
        Modifier
            .padding(start = 2.dp)
            // The touch target is 48 dp tall; the row is laid out as if it were the pill's 26.
            .layout { m, cs ->
                val p = m.measure(cs)
                val h = min(p.height, 26.dp.roundToPx())
                layout(p.width, h) { p.place(0, (h - p.height) / 2) }
            }
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Look $at of ${engine.n}" }
            .bouncyClickable(RoundedCornerShape(16.dp), onClickLabel = "Jump to a look", ripple = false) {
                val root = rootCoords()
                val c = pill[0]
                if (root != null && c != null && c.isAttached) onOpen(root.localBoundingBoxOf(c, clipBounds = false))
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .onGloballyPositioned { pill[0] = it }
                .clearAndSetSemantics { }
                .height(26.dp)
                .background(scheme.primaryContainer, RoundedCornerShape(13.dp))
                .padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(digits, 26.dp)
                    .clipToBounds()
                    .drawBehind {
                        engine.frame.intValue
                        // A number a strip: the one leaving rolls up and out as the next rolls in under it.
                        val pos = (engine.n - 1 - engine.p).coerceIn(-0.3f, engine.n - 0.7f)
                        val first = floor(pos).toInt()
                        for (k in first..first + 1) {
                            if (k !in 0 until engine.n) continue
                            val l = measurer.measure("${k + 1}", style)
                            drawText(l, on, Offset(size.width - l.size.width, (size.height - l.size.height) / 2f + (k - pos) * size.height))
                        }
                    }
            )
            Text("/${engine.n}", color = on.copy(alpha = 0.7f), style = style.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 1.5.dp))
            Icon(Symbols.UnfoldMore, null, tint = on.copy(alpha = 0.78f), modifier = Modifier.padding(start = 1.dp).size(16.dp))
        }
    }
}

private val WheelSpring = spring<Float>(dampingRatio = 1f, stiffness = 260f)

/** Past either end the wheel gives a little and comes back. */
private fun wheelBand(w: Float, last: Float): Float = when {
    w < 0f -> -(1f - 1f / (1f - w * 1.2f)) * 0.5f
    w > last -> last + (1f - 1f / (1f + (w - last) * 1.2f)) * 0.5f
    else -> w
}

/**
 * The number picker: a card grown out of the counter ([Pop.Picker.anchor], page px), under it where there is room and
 * over it where there is not. A touch anywhere else, or back, puts it away without turning the fan.
 */
@Composable
internal fun NumberPicker(pick: Pop.Picker, phoneDark: Boolean, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val n = FAN_ORDER.size
    val wheel = remember { Animatable(pick.start.toFloat()) }
    var chosen by remember { mutableIntStateOf(pick.start) }
    // A tick as each number passes the middle.
    LaunchedEffect(wheel) {
        snapshotFlow { wheel.value.roundToInt().coerceIn(0, n - 1) }.drop(1).collect {
            chosen = it
            CardHaptics.pass(view)
        }
    }
    fun go(k: Int) {
        onClose()
        pick.onGo(k.coerceIn(0, n - 1))
    }
    var card by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gutter = with(density) { GalleryGutter.roundToPx() }
        val gap = with(density) { 8.dp.roundToPx() }
        val up = pick.anchor.bottom + gap + card.height > h - with(density) { 96.dp.roundToPx() } && pick.anchor.top - gap - card.height > 0
        val x = gutter.coerceAtMost((w - card.width - gutter).coerceAtLeast(0))
        val y = (if (up) pick.anchor.top - gap - card.height else pick.anchor.bottom + gap).roundToInt()
        val grow = remember { Animatable(0f) }
        LaunchedEffect(Unit) { grow.animateTo(1f, Springs.spatial()) }
        val cardWidth = min(304f, with(density) { w.toDp().value } - GalleryGutter.value * 2f).dp
        // Behind the card: any touch outside it puts it away, a swipe as much as a tap.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); onClose() } })
        Row(
            Modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { card = it }
                .graphicsLayer {
                    val s = grow.value.coerceAtLeast(0f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (size.width > 0f) ((pick.anchor.center.x - x) / size.width).coerceIn(0f, 1f) else 0.4f, if (up) 1f else 0f)
                }
                .width(cardWidth)
                // As tall as the wheel, or as its side needs at a large text size.
                .height(IntrinsicSize.Min)
                .shadow(12.dp, RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .semantics { paneTitle = "Jump to a look" }
                .padding(8.dp)
        ) {
            // The wheel: 40 dp a number. It follows the finger, coasts to where its speed would carry it and snaps to a
            // number; a touch that never moves is a tap on that number.
            val pitch = with(density) { 40.dp.toPx() }
            val measurer = rememberTextMeasurer(cacheSize = 96)
            val plain = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
            val bold = plain.copy(fontWeight = FontWeight.Bold)
            val look = fanLook(FAN_ORDER[chosen])
            Box(
                Modifier
                    .size(78.dp, 200.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surfaceContainerLow)
                    .semantics {
                        contentDescription = "Look number"
                        stateDescription = "${chosen + 1} of $n, ${look.name}"
                        onClick(label = "Go to look") { go(chosen); true }
                        customActions = listOfNotNull(
                            if (chosen >= n - 1) null else CustomAccessibilityAction("Next number") { scope.launch { wheel.animateTo((chosen + 1f).coerceAtMost(n - 1f), WheelSpring) }; true },
                            if (chosen <= 0) null else CustomAccessibilityAction("Previous number") { scope.launch { wheel.animateTo((chosen - 1f).coerceAtLeast(0f), WheelSpring) }; true }
                        )
                    }
                    // A mouse wheel or trackpad turns it a number at a time, down for the next.
                    .wheelSteps(
                        wheel, minGapMillis = 90L,
                        canStep = { d -> wheel.targetValue.roundToInt() + d in 0 until n },
                        step = { d -> scope.launch { wheel.animateTo((wheel.targetValue.roundToInt() + d).coerceIn(0, n - 1).toFloat(), WheelSpring) } }
                    )
                    .pointerInput(wheel) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            scope.launch { wheel.stop() }
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val w0 = wheel.value
                            var moved = false
                            var lastY = down.position.y
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) {
                                    ch.consume()
                                    if (!moved) go((wheel.value + (ch.position.y - size.height / 2f) / pitch).roundToInt())
                                    else {
                                        val v = (-tracker.calculateVelocity().y / pitch).coerceIn(-40f, 40f)
                                        val to = (wheel.value + v * 0.22f).roundToInt().coerceIn(0, n - 1)
                                        scope.launch { wheel.animateTo(to.toFloat(), WheelSpring, initialVelocity = v) }
                                    }
                                    break
                                }
                                if (ch.positionChange() != Offset.Zero) {
                                    tracker.addPosition(ch.uptimeMillis, ch.position)
                                    lastY = ch.position.y
                                    val dy = lastY - down.position.y
                                    if (!moved && abs(dy) < 6.dp.toPx()) continue
                                    moved = true
                                    ch.consume()
                                    val to = wheelBand(w0 - dy / pitch, n - 1f)
                                    scope.launch { wheel.snapTo(to) }
                                }
                            }
                        }
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val at = wheel.value
                        val mid = size.height / 2f
                        // The chosen number's pill, standing still in the middle.
                        val pw = size.width - 18.dp.toPx()
                        val ph = 44.dp.toPx()
                        drawRoundRect(scheme.primaryContainer, Offset((size.width - pw) / 2f, mid - ph / 2f), Size(pw, ph), CornerRadius(ph / 2f))
                        val c = at.roundToInt().coerceIn(0, n - 1)
                        for (i in max(0, floor(at).toInt() - 3)..min(n - 1, ceil(at).toInt() + 3)) {
                            val d = i - at
                            val a = abs(d)
                            // Its neighbors: smaller, fainter and a little closer together the further from the middle.
                            val alpha = (1f - 0.36f * a).coerceIn(0f, 1f)
                            if (alpha <= 0.01f) continue
                            val l = measurer.measure("${i + 1}", if (i == c) bold else plain)
                            val cy = mid + d * pitch * (1f - 0.045f * min(a, 3f))
                            scale(max(0.5f, 1f - 0.19f * a), Offset(size.width / 2f, cy)) {
                                drawText(l, (if (i == c) scheme.onPrimaryContainer else scheme.onSurface).copy(alpha = alpha), Offset((size.width - l.size.width) / 2f, cy - l.size.height / 2f))
                            }
                        }
                        // The numbers go out softly toward the wheel's top and bottom.
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.24f to Color.Black, 0.76f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    }
            )
            Column(Modifier.padding(start = 10.dp).weight(1f).heightIn(min = 200.dp)) {
                val shape = RoundedCornerShape(20.dp)
                val store = LocalWallpaperStore.current
                val own = store != null && wallpaperOf(look, look.appDark(phoneDark)) != null
                val wallColors = rememberWallpaperColors()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        // Its backdrop: the look's own wallpaper, or (a look on your wallpaper) the wallpaper's colors, as its strip has.
                        .then(if (own) Modifier.lookWindow(look, shape) else Modifier.clip(shape).drawBehind { drawWallMesh(wallColors, size) })
                        .bouncyClickable(shape, onClickLabel = "Go to look") { go(chosen) }
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center
                ) {
                    // The side is the card less its padding, the wheel and the gap between them.
                    GalleryBar(look, phoneDark, scale = ((cardWidth.value - 16f - 78f - 10f - 16f) / BAR_WIDTH.value).coerceIn(0.3f, 0.7f))
                }
                val g = fanGroupOf(look.id)
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.secondaryContainer.copy(alpha = 0.72f))
                        .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(g.icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                    Text(
                        g.name, color = scheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    look.name, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
                )
                Spacer(Modifier.weight(1f))
                PreviewButton("Go to Look", ButtonKind.Filled, Modifier.fillMaxWidth(), icon = Symbols.ArrowForward) { go(chosen) }
            }
        }
    }
}
