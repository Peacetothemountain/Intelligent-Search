package com.pixel.intelligentsearch.feature.tabs

import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.theme.LARGE_TEXT_SCALE
import com.pixel.intelligentsearch.core.ui.pageGlow
import com.pixel.intelligentsearch.feature.tabs.gallery.GalleryPage
import com.pixel.intelligentsearch.feature.tabs.ui.GlassBackdrop
import com.pixel.intelligentsearch.feature.tabs.ui.Springs
import com.pixel.intelligentsearch.feature.tabs.wellbeing.WellbeingPage
import kotlinx.coroutines.launch
import kotlin.math.sign

/** Settings' three top pages, in the pill's order. */
enum class SettingsTab { Settings, Gallery, Wellbeing }

private val TABS = listOf(
    PillTab("Settings", PillSymbols.Settings, PillSymbols.SettingsFilled),
    PillTab("Gallery", PillSymbols.Style, PillSymbols.StyleFilled),
    PillTab("Wellbeing", PillSymbols.Spa, PillSymbols.SpaFilled),
)

/**
 * Settings' main page with its floating pill: Settings (the page as it always was, [settingsPage]), Gallery and
 * Wellbeing. [tab] is the tab on screen, held by the settings hub so its page glow can step aside for Gallery; it is
 * saved with the screen, so a recreated screen opens on the tab it was on; Settings opens on Settings unless the search
 * asks for Wellbeing (its screen time).
 *
 * - Tabs switch by sliding the pages sideways on the spatial spring (no fade). Back from Gallery or Wellbeing (the
 *   gesture, predictive, or a page's back arrow) slides back to Settings; back on Settings does what it always did.
 * - The pill slides down out of sight as a page scrolls down and back as it scrolls up, and stays away while a page
 *   is covered (Gallery's full preview, a sheet) or [hidePill] (the tutorial).
 * - While Gallery is in sight the window may show Android's wallpaper ([WallpaperBehindWindow]): every frame then
 *   starts from a clear window, and the other page sliding with it brings its own background and glow.
 */
@Composable
fun SettingsTabs(
    tab: MutableState<SettingsTab>,
    hidePill: Boolean,
    glowClock: State<Float>?,
    openWidgetSettings: () -> Unit,
    settingsPage: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var current by tab
    val swap = remember { PageSwap(current) }
    val covered = remember { mutableStateMapOf<SettingsTab, Boolean>() }
    // Which pages want Android's wallpaper behind the window (PageChrome.showWallpaper): Gallery, unless it says not.
    val wantsWallpaper = remember { mutableStateMapOf(SettingsTab.Gallery to true) }
    var scrolledAway by remember { mutableStateOf(false) }
    val largeText = density.fontScale >= LARGE_TEXT_SCALE

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun backToSettings() {
        if (current == SettingsTab.Settings) return
        current = SettingsTab.Settings
        scrolledAway = false
        scope.launch { swap.go(SettingsTab.Settings, direction = -1) }
    }

    fun select(to: SettingsTab) {
        if (to == current) return
        // The Settings tab from Gallery or Wellbeing is the same as back.
        if (to == SettingsTab.Settings) return backToSettings()
        val direction = sign((to.ordinal - current.ordinal).toFloat()).toInt()
        current = to
        scrolledAway = false
        scope.launch { swap.go(to, direction) }
    }

    PredictiveBackSwap(swap, to = { SettingsTab.Settings }, enabled = current != SettingsTab.Settings, onCommitted = {
        current = SettingsTab.Settings
        scrolledAway = false
    })

    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val currentDispatcher by rememberUpdatedState(dispatcher)
    val currentOpenWidgetSettings by rememberUpdatedState(openWidgetSettings)
    val chromes = remember(statusTop, navigationBottom) {
        val padding = PaddingValues(top = statusTop, bottom = navigationBottom + PILL_BOTTOM_GAP + PILL_HEIGHT)
        SettingsTab.entries.associateWith { page ->
            PageChrome(
                contentPadding = padding,
                onBack = { if (page == SettingsTab.Settings) currentDispatcher?.onBackPressed() else backToSettings() },
                setCovered = { on ->
                    covered[page] = on
                    if (!on) scrolledAway = false
                },
                showWallpaper = { on -> if (page == SettingsTab.Gallery) wantsWallpaper[page] = on },
                revealPill = { if (current == page) scrolledAway = false },
                openWidgetSettings = { currentOpenWidgetSettings() }
            )
        }
    }

    // Down scrolls (content moving up) send the pill away, up scrolls bring it back.
    val hideOnScroll = remember(density) {
        val threshold = with(density) { 6.dp.toPx() }
        object : NestedScrollConnection {
            var run = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = consumed.y
                if (dy != 0f) {
                    if (sign(dy) != sign(run)) run = 0f
                    run += dy
                    if (run < -threshold) scrolledAway = true else if (run > threshold) scrolledAway = false
                }
                // Pulling down at the top brings it back too.
                if (available.y > 0f) scrolledAway = false
                return Offset.Zero
            }
        }
    }

    val hidden = hidePill || covered[current] == true || scrolledAway
    val hide = remember { Animatable(0f) }
    LaunchedEffect(hidden) { hide.animateTo(if (hidden) 1f else 0f, Springs.spatial()) }
    // Far enough that the pill and its shadow are out of sight.
    val hideDistance = with(density) { (PILL_HEIGHT + PILL_BOTTOM_GAP + 32.dp).toPx() + navigationBottom.toPx() }

    val galleryInSight = swap.front == SettingsTab.Gallery || swap.behind == SettingsTab.Gallery
    // Only while a page that looks through to it is in sight: Settings keeps Android's back-to-home preview.
    WallpaperBehindWindow(listOfNotNull(swap.behind, swap.front).any { wantsWallpaper[it] == true })
    DisposableEffect(Unit) { onDispose { covered.clear() } }

    Box(
        Modifier
            .fillMaxSize()
            // While Gallery is in sight the window may show the real wallpaper: every frame starts from a clear window,
            // so nothing of an earlier frame (or the settings page's own background under this) shows through Gallery.
            .drawBehind { if (galleryInSight) drawRect(Color.Black, blendMode = BlendMode.Clear) }
    ) {
        if (galleryInSight) {
            // The settings page's background and glow were cleared with the rest: the page sliding with Gallery brings
            // its own, and the glow slides up out of the page towards Gallery.
            for (page in listOfNotNull(swap.behind, swap.front)) {
                if (page != SettingsTab.Gallery) key(page) {
                    Box(Modifier.fillMaxSize().swapOffset(swap, page).background(scheme.background))
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val lit = listOfNotNull(swap.behind, swap.front).filter { it != SettingsTab.Gallery }
                            .sumOf { swap.shareOf(it).toDouble() }.toFloat()
                        translationY = -(1f - lit) * size.height * 0.45f
                    }
                    .pageGlow(clock = glowClock)
            )
        }
        PageSwapHost(swap, Modifier.fillMaxSize().nestedScroll(hideOnScroll)) { page ->
            val chrome = chromes.getValue(page)
            DisposableEffect(page) { onDispose { covered[page] = false } }
            when (page) {
                SettingsTab.Settings -> settingsPage()
                SettingsTab.Gallery -> GalleryPage(chrome)
                SettingsTab.Wellbeing -> WellbeingPage(chrome)
            }
        }

        FloatingPill(
            tabs = TABS,
            selected = current.ordinal,
            indicator = {
                val behind = swap.behind
                val front = swap.front.ordinal.toFloat()
                if (behind == null) front else behind.ordinal + (front - behind.ordinal) * swap.progress.value
            },
            onSelect = { select(SettingsTab.entries[it]) },
            frosted = false,
            backdrop = NO_BACKDROP,
            positionInBackdrop = { Offset.Zero },
            largeText = largeText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (largeText) PILL_SIDE_LARGE_TEXT else PILL_SIDE)
                .padding(bottom = navigationBottom + PILL_BOTTOM_GAP)
                // On a wide screen (landscape, a tablet) it keeps a phone's width, centered.
                .widthIn(max = if (largeText) PILL_MAX_WIDTH_LARGE_TEXT else PILL_MAX_WIDTH)
                .fillMaxWidth()
                .height(PILL_HEIGHT)
                .graphicsLayer { translationY = hide.value * hideDistance }
                // Out of TalkBack's reach while it is away, and out of a keyboard's: Tab skips its tabs then (it was
                // moved off screen only, so Tab stopped on tabs no one could see, and Enter switched tab unseen).
                .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
                .focusProperties { onEnter = { if (hidden) cancelFocusChange() } }
                .focusGroup()
        )
    }
}

/** The pill is solid, so it never blurs what is under it. */
private val NO_BACKDROP = GlassBackdrop(size = { Size.Zero }, draw = {})
