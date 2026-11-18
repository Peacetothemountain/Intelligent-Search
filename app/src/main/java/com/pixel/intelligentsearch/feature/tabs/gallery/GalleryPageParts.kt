package com.pixel.intelligentsearch.feature.tabs.gallery

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.feature.tabs.pageGlowTones
import com.pixel.intelligentsearch.feature.tabs.ui.MatteGrain
import com.pixel.intelligentsearch.feature.tabs.ui.PreviewIconButton
import com.pixel.intelligentsearch.feature.tabs.ui.Springs
import com.pixel.intelligentsearch.feature.tabs.ui.Symbols
import com.pixel.intelligentsearch.feature.tabs.ui.bouncyClickable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/* The Gallery page's parts as the approved mockup (Direction B, refined) draws them: the big title, the look of the
 * week, Yours, the season's looks, and All Looks with its chips over the swatch fan. */

/** The page's side gutter (the mockup's 16). */
internal val GalleryGutter = 16.dp

/** The top bar: back, the small title that rises in once the big one has scrolled away, and the page's menu. */
@Composable
internal fun GalleryTopBar(showTitle: Boolean, onBack: () -> Unit, onTitle: () -> Unit, more: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val s = remember { Animatable(if (showTitle) 1f else 0f) }
    // The small title grows with the text setting only up to 1.3 times, as Material's top app bars do: past that it
    // would no longer fit the bar.
    val fontScale = LocalDensity.current.fontScale
    val titleSize = (20f * minOf(fontScale, 1.3f) / fontScale).sp
    LaunchedEffect(showTitle) { s.animateTo(if (showTitle) 1f else 0f, Springs.spatial()) }
    Row(modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PreviewIconButton(Symbols.ArrowBack, "Back", tint = scheme.onSurface, onClick = onBack)
        // As tall as its line at the largest text, so the small title is never cut off.
        Box(Modifier.weight(1f).heightIn(min = 30.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) {
            Text(
                "Gallery", color = scheme.onSurface, maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = titleSize, lineHeight = titleSize * 1.3f, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .graphicsLayer { translationY = (1f - s.value) * maxOf(size.height, 40.dp.toPx()) }
                    .then(if (showTitle) Modifier.bouncyClickable(RoundedCornerShape(12.dp), onClickLabel = "Scroll to the top", onClick = onTitle) else Modifier.clearAndSetSemantics { })
            )
        }
        more()
    }
}

/** The page's big title. */
@Composable
internal fun DisplayTitle() {
    Text(
        "Gallery", color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 45.sp, lineHeight = 47.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp),
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 16.dp).semantics { heading() }
    )
}

/** A section's title in primary, with an optional count or button at its end. */
@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier, count: Int? = null, trailing: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = scheme.primary, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp), modifier = Modifier.semantics { heading() })
        if (count != null) Box(
            Modifier.padding(start = 8.dp).heightIn(min = 26.dp).widthIn(min = maxOf(32.dp, 18.dp + 8.dp * count.toString().length)).background(scheme.primaryContainer, RoundedCornerShape(13.dp)).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text("$count", color = scheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A tonal button with its icon (Paste a Code). */
@Composable
internal fun TonalIconButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MorphButton(
        GroupButton(text, icon, scheme.secondaryContainer, scheme.onSecondaryContainer, onClick = onClick),
        40.dp, listOf(20.dp, 20.dp, 20.dp, 20.dp), fontSize = 14f, pressed = 10.dp
    )
}

/** The week the look of the week is for, "Nov 16 to 22" (or "Nov 30 to Dec 6"). */
internal fun weekLabel(today: LocalDate): String {
    val mon = today.with(DayOfWeek.MONDAY)
    val sun = mon.plusDays(6)
    fun m(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.US)
    return if (mon.month == sun.month) "${m(mon)} ${mon.dayOfMonth} to ${sun.dayOfMonth}" else "${m(mon)} ${mon.dayOfMonth} to ${m(sun)} ${sun.dayOfMonth}"
}

/** The matte film grain over a container. */
private fun Modifier.grain(dark: Boolean): Modifier = drawBehind {
    if (MatteGrain.drawn) drawRect(MatteGrain.brush(1), alpha = if (dark) 0.022f else 0.014f)
}

/** Look of the week: the primary container with the page glow's tones in it, its window, name, chips and buttons. */
@Composable
internal fun HeroB(
    look: Look, state: GalleryState, phoneDark: Boolean, today: LocalDate,
    registerScene: (LayoutCoordinates) -> Unit, registerBar: (LayoutCoordinates) -> Unit,
    onOpen: () -> Unit, onApply: () -> Unit, onCustomize: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.dark
    val on = look.id == state.current.id
    val fg = scheme.onPrimaryContainer
    val tones = remember(scheme.primary) { pageGlowTones(scheme.primary) }
    val strength = if (dark) 0.8f else 0.35f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.primaryContainer)
            .drawBehind {
                // The mesh: the glow's tones pooled at the bottom corners and the tertiary at the top.
                fun pool(cx: Float, cy: Float, rx: Float, c: Color) = drawRect(
                    Brush.radialGradient(0f to c, 1f to c.copy(alpha = 0f), center = Offset(cx, cy), radius = rx)
                )
                pool(0f, size.height, size.width * 0.75f, tones[0].copy(alpha = 0.7f * strength))
                pool(size.width, size.height * 0.8f, size.width * 0.65f, tones[2].copy(alpha = 0.6f * strength))
                pool(size.width * 0.7f, 0f, size.width * 0.6f, scheme.tertiary.copy(alpha = 0.22f * strength))
            }
            .grain(dark)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 10.dp).semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(PolarShape.Cookie12, 40.dp, scheme.tertiary) { Icon(Symbols.AwardStarFilled, null, tint = scheme.onTertiary, modifier = Modifier.size(22.dp)) }
            Column(Modifier.padding(start = 10.dp)) {
                Text("Look of the Week", color = fg, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Text(weekLabel(today), color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            LookWindowBox(
                look, phoneDark, maxWidth, maxWidth * (132f / 312f), RoundedCornerShape(22.dp),
                register = registerScene, registerBar = registerBar, badge = on, onClick = onOpen
            )
        }
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Look of the week: " + look.spoken(on, phoneDark, today) }) {
            Text(
                look.name, color = fg, maxLines = 2,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 2.dp)
            )
            if (on) Text("On your phone now", color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                look.desc, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
            )
        }
        LookChips(look, phoneDark, today, fg.copy(alpha = 0.12f).compositeOn(scheme.primaryContainer), fg, fg, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp))
        ConnectedButtons(
            Modifier.padding(horizontal = 6.dp), height = 52.dp, outer = 26.dp, fontSize = 15.5f,
            left = GroupButton("Try It", Symbols.Fullscreen, fg.copy(alpha = 0.14f).compositeOn(scheme.primaryContainer), fg, "Try ${look.name}", onOpen),
            right = if (on) GroupButton("Customize", Symbols.Tune, scheme.primary, scheme.onPrimary, "Customize ${look.name}", onCustomize)
            else GroupButton("Apply", Symbols.Check, scheme.primary, scheme.onPrimary, "Apply ${look.name}", onApply)
        )
    }
}

private fun Color.compositeOn(bg: Color): Color = this.compositeOver(bg)

/** Yours: the look that is on, the one before (the lasting undo), the saved ones, and Save Current. */
@Composable
internal fun YoursRowB(
    state: GalleryState, phoneDark: Boolean,
    register: (String, LayoutCoordinates) -> Unit, registerBar: (String, LayoutCoordinates) -> Unit,
    onOpen: (Look) -> Unit, onMenu: (Rect, String, Look) -> Unit, onSaveCurrent: () -> Unit,
    rootCoords: () -> LayoutCoordinates?
) {
    val scheme = MaterialTheme.colorScheme
    val tiles = buildList {
        add("now" to state.current)
        state.before?.let { add("before" to it) }
        state.saved.filter { it.id != state.current.id && it.id != state.before?.id }.forEach { add("saved" to it) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bleed(GalleryGutter)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = GalleryGutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for ((kind, look) in tiles) {
            val name = if (kind == "before") "Before" else look.name
            // TalkBack says one name for the tile and its menu: "Before: Ink", as the title alone doesn't say which look.
            val spoken = if (kind == "before") "Before: ${look.name}" else look.name
            val sub = when (kind) {
                "now" -> "Wearing it now"
                "before" -> "Your look before"
                else -> "Saved"
            }
            var more by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Column(Modifier.width(150.dp).semantics(mergeDescendants = false) { }) {
                LookWindowBox(
                    look, phoneDark, 150.dp, 84.dp, RoundedCornerShape(20.dp),
                    register = { register(look.id, it) }, registerBar = { registerBar(look.id, it) },
                    badge = kind == "now", onClick = { onOpen(look) }, label = spoken
                ) {
                    Box(Modifier.fillMaxWidth().height(84.dp)) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp)
                                .onGloballyPositioned { more = it }
                                .background(Color(0x6B1E181A), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .bouncyClickable(CircleShape) {
                                    val root = rootCoords()
                                    val c = more
                                    if (root != null && c != null && c.isAttached) onMenu(root.localBoundingBoxOf(c, clipBounds = false), kind, look)
                                }
                                .semantics { contentDescription = "More for $spoken" },
                            contentAlignment = Alignment.Center
                        ) { Icon(Symbols.MoreVert, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                    }
                }
                Text(name, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp))
                Text(sub, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
        if (state.currentUnsaved) {
            val full = state.savedFull
            Column(Modifier.width(104.dp).bouncyClickable(RoundedCornerShape(20.dp), onClick = onSaveCurrent)) {
                Box(Modifier.fillMaxWidth().height(84.dp).background(if (scheme.dark) scheme.surfaceContainer else scheme.surfaceContainerLowest, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    ShapeBadge(PolarShape.Sunny, 46.dp, scheme.primary) { Icon(if (full) Symbols.Bookmarks else Symbols.BookmarkAddFilled, null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp)) }
                }
                Text(if (full) "Saved Looks Are Full" else "Save This One", color = scheme.onSurface, maxLines = 2, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp))
                if (!full) Text("Keep it for later", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

/** Widens the element by [bleed] on each side, past its parent's padding. */
private fun Modifier.bleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx() * 2
    val p = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, p.height) { p.place(-bleed.roundToPx(), 0) }
}

/**
 * The season's looks: the tertiary container, matte, with a card for each look in season. In the dark theme it is a
 * dark surface toned with the tertiary (as the approved mockup's dark box is): a bright tertiary container that size
 * would glare and outrank the look of the week above it.
 */
@Composable
internal fun SeasonSection(
    season: Season, looks: List<Look>, state: GalleryState, phoneDark: Boolean,
    register: (String, LayoutCoordinates) -> Unit, registerBar: (String, LayoutCoordinates) -> Unit,
    onOpen: (Look) -> Unit, onApply: (Look) -> Unit, onCustomize: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.dark
    val container = if (dark) androidx.compose.ui.graphics.lerp(scheme.surfaceContainerLow, scheme.tertiary, 0.16f) else scheme.tertiaryContainer
    val fg = if (dark) scheme.onSurface else scheme.onTertiaryContainer
    val fgSub = if (dark) scheme.onSurfaceVariant else fg.copy(alpha = 0.85f)
    Column(
        Modifier
            .padding(top = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .grain(scheme.dark)
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(PolarShape.Burst, 44.dp, scheme.tertiary) { Icon(Symbols.EcoFilled, null, tint = scheme.onTertiary, modifier = Modifier.size(22.dp)) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("${season.title} Looks", color = fg, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium), modifier = Modifier.semantics { heading() })
                Text(if (season == Season.FALL) "Made for sweater weather" else "In season now", color = fgSub, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        for (look in looks) {
            val key = "season:${look.id}"
            val on = look.id == state.current.id
            Column(Modifier.fillMaxWidth().background(if (scheme.dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest, RoundedCornerShape(26.dp)).padding(6.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    LookWindowBox(
                        look, phoneDark, maxWidth, maxWidth * (96f / 304f), RoundedCornerShape(20.dp),
                        register = { register(key, it) }, registerBar = { registerBar(key, it) }, badge = on, onClick = { onOpen(look) }
                    ) {
                        Box(Modifier.fillMaxWidth().height(maxWidth * (96f / 304f))) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .height(32.dp)
                                    .background(Color(0x6B1E181A), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .bouncyClickable(RoundedCornerShape(16.dp), onClickLabel = "try ${look.name}") { onOpen(look) }
                                    .padding(start = 9.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Symbols.Fullscreen, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Text("Try It", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
                Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                        Text(look.name, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium))
                        Text(look.desc, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp))
                    }
                    Spacer(Modifier.width(10.dp))
                    if (on) MorphButton(GroupButton("Customize", Symbols.Tune, scheme.primaryContainer, scheme.onPrimaryContainer, onClick = onCustomize), 48.dp, listOf(24.dp, 24.dp, 24.dp, 24.dp), fontSize = 15f, pressed = 12.dp)
                    else MorphButton(GroupButton("Apply", Symbols.Check, scheme.primaryContainer, scheme.onPrimaryContainer, "Apply ${look.name}") { onApply(look) }, 48.dp, listOf(24.dp, 24.dp, 24.dp, 24.dp), fontSize = 15f, pressed = 12.dp)
                }
            }
        }
    }
}

/**
 * The chips over the fan: each turns it to its kind's first look. The selected one is the kind of the look the fan
 * stands on, so the row is always a "you are here": it morphs pill to squircle, its icon to a check, and the row scrolls
 * it into view.
 */
@Composable
internal fun FanChips(selected: String, onPick: (String) -> Unit) {
    val chips = FAN_GROUPS.map { Triple(it.f, it.name, it.icon) }
    val scroll = rememberScrollState()
    // Each chip's span in the row (px), for bringing the selected one into view.
    val spans = remember { HashMap<String, Pair<Int, Int>>() }
    var rowWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(selected, rowWidth) {
        val (start, end) = spans[selected] ?: return@LaunchedEffect
        if (rowWidth <= 0) return@LaunchedEffect
        val margin = with(density) { (GalleryGutter + 8.dp).roundToPx() }
        val to = when {
            start - margin < scroll.value -> start - margin
            end + margin > scroll.value + rowWidth -> end + margin - rowWidth
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(to.coerceIn(0, scroll.maxValue), Springs.spatial())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bleed(GalleryGutter)
            .semantics { contentDescription = "Jump to" }
            .onSizeChanged { rowWidth = it.width }
            .horizontalScroll(scroll)
            .padding(start = GalleryGutter, end = GalleryGutter, top = 2.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((f, label, icon) in chips) BoxChip(
            label, icon, f == selected,
            Modifier.onPlaced { c -> val x = c.positionInParent().x.roundToInt(); spans[f] = x to x + c.size.width }
        ) { onPick(f) }
    }
}

@Composable
private fun BoxChip(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val u = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) { u.animateTo(if (selected) 1f else 0f, Springs.fast()) }
    Row(
        modifier
            .heightIn(min = 36.dp)
            .graphicsLayer {
                shape = RoundedCornerShape((18f + (10f - 18f) * u.value).coerceAtLeast(0f).dp)
                clip = true
            }
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .drawBehind {
                if (!selected) drawRoundRect(
                    scheme.outlineVariant, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
            }
            .bouncyClickable(null, role = Role.RadioButton, ripple = true, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(18.dp)) {
            Icon(
                icon, null, tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).graphicsLayer { val k = (1f - u.value).coerceIn(0f, 1f); scaleX = k; scaleY = k }
            )
            Icon(
                Symbols.Check, null, tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp).graphicsLayer { val k = u.value.coerceIn(0f, 1.2f); scaleX = k; scaleY = k; rotationZ = (1f - u.value) * -90f }
            )
        }
        Text(
            label, color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
