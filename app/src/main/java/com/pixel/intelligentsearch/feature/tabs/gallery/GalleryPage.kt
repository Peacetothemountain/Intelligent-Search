package com.pixel.intelligentsearch.feature.tabs.gallery

import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.feature.tabs.PageChrome
import com.pixel.intelligentsearch.feature.tabs.pageGlow
import com.pixel.intelligentsearch.feature.tabs.PAGE_GLOW_FRAME_GAP_MS
import com.pixel.intelligentsearch.feature.tabs.rememberMeshClock
import androidx.compose.runtime.snapshotFlow
import com.pixel.intelligentsearch.feature.tabs.rememberTabsSession
import com.pixel.intelligentsearch.feature.tabs.ui.pageContainer
import com.pixel.intelligentsearch.feature.tabs.ui.rememberTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.Dp

/** Screenshot tests fix the date (the look of the week and the season follow it). */
internal val LocalGalleryToday = androidx.compose.runtime.staticCompositionLocalOf<LocalDate?> { null }

/** Screenshot tests open the full preview on a look straight away (look id, and Search instead of Home screen). */
internal val LocalGalleryOpenPreview = androidx.compose.runtime.staticCompositionLocalOf<Pair<String, Boolean>?> { null }

/** Card shapes: a scene's window has 28 dp top corners and 12 dp bottom ones (gallery_design.md 4.3). */
private val SceneShape = RoundedCornerShape(28.dp, 28.dp, 12.dp, 12.dp)
private val CardShape = RoundedCornerShape(28.dp)

/**
 * Gallery: complete ready-made looks for the search bar widget and the search page, each previewed on a wallpaper made
 * for it (or on the person's own wallpaper, or its colors), one tap to try full screen, one tap to apply, with an undo
 * that lasts. Apply changes the real widget and search page through the app's own settings (GalleryLive); it doesn't
 * change. The one thing that does change the phone is Set wallpaper in the full preview, and only once its sheet is
 * confirmed.
 *
 * The page is a stencil: its opaque parts are painted here, and a preview is either a window with the look's own
 * wallpaper drawn in it, or a window cleared through the page to what is behind the app's window (the real wallpaper,
 * drawn by Android) or to a mesh of the wallpaper's colors.
 */
@Composable
fun GalleryPage(chrome: PageChrome, modifier: Modifier = Modifier) {
    val today = LocalGalleryToday.current ?: remember { LocalDate.now() }
    // Kept above the tabs (TabsSession): a tab switch or a recreated screen finds Gallery as it was left.
    val session = rememberTabsSession().gallery(today)
    val state = session.state
    val pv = session.pv
    val appContext = LocalContext.current.applicationContext
    // The looks are kept on the phone: read once before the page first draws, written back as they change.
    remember(session) { state.attach(appContext, session.scope); true }
    // Back on the page: the look that is on may have been changed meanwhile in Widget Customization or Appearance.
    androidx.lifecycle.compose.LifecycleResumeEffect(state) {
        state.resync()
        // An open preview (Customize from Yours Now, then Save and Back) shows the looks as they are now, not the ones it
        // took when it opened: Yours Now is the settings as they are, and the look that is on may have changed.
        if (pv.open && !pv.closing) {
            val shown = pv.look
            val fresh = when {
                pv.context == "shared" || shown == null -> pv.list.map { state.find(it.id) ?: it }
                else -> state.previewList(pv.context, state.find(shown.id) ?: shown)
            }
            val at = fresh.indexOfFirst { it.id == shown?.id }
            pv.endStack()
            pv.list = fresh
            pv.index = if (at >= 0) at else pv.index.coerceIn(0, (fresh.size - 1).coerceAtLeast(0))
        }
        onPauseOrDispose { }
    }
    // The fan's place, a pasted look and the open preview are kept in the screen's saved state, so Android closing the app
    // in the background (or rebuilding the screen) brings Gallery back as it was left.
    rememberSaveable(saver = Saver<GallerySession, android.os.Bundle>(save = { it.saveUi() }, restore = { session.restoreUi(it); session })) { session }
    session.uiSettled = true
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val phoneDark = scheme.dark
    val fontScale = density.fontScale
    val lg = fontScale >= 1.3f
    val xl = fontScale >= 1.6f
    val override = LocalGalleryBackdropOverride.current
    var backdropName by rememberSaveable { mutableStateOf((override ?: Backdrop.LookWallpaper).name) }
    val backdrop = Backdrop.valueOf(backdropName)
    // Android's wallpaper stays behind the window unless only its colors are wanted: a look of the owner's own has no
    // wallpaper of its own and is tried on the phone's.
    val showsReal = backdrop != Backdrop.ColorsOnly
    val store = if (backdrop == Backdrop.LookWallpaper) rememberWallpaperStore() else null
    // Screenshot tests have no wallpaper behind the window; the mesh stands in for it.
    val standIn = LocalGalleryWallpaperColors.current != null
    val blurAvailable by rememberSystemBlurEnabled()
    val wallColors = rememberWallpaperColors()
    var pop by remember { mutableStateOf<Pop?>(null) }
    // The number picker stands by the counter where it was when it opened; once the window turns or changes size (a
    // rotation, a fold, a split's divider) the counter is somewhere else, so the picker closes rather than float apart.
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    LaunchedEffect(windowSize) { if (pop is Pop.Picker) pop = null }
    var snack by session::snack
    val listState = rememberLazyListState()
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scenes = remember { HashMap<String, LayoutCoordinates>() }
    val bars = remember { HashMap<String, LayoutCoordinates>() }

    // Android's wallpaper behind the window only while a window here can look through to it: always on Your
    // wallpaper; on the looks' own wallpapers only while one of the owner's own looks (which have none) is on the page
    // or in the preview; never on Colors only. Otherwise the shell keeps the window's back-to-home preview.
    val holes = showsReal && (backdrop == Backdrop.RealWallpaper || standIn ||
        (listOfNotNull(state.current, state.before, state.hero) + state.saved).any { wallpaperOf(it) == null } ||
        (pv.open && (-1..1).any { d -> pv.list.getOrNull(pv.index + d)?.let { wallpaperOf(it) == null } == true }))
    LaunchedEffect(holes) { chrome.showWallpaper(holes) }
    ShowWallpaperBehindWindow(holes)
    // The pill stays away while anything is over the page: the full preview, a menu, a dialog or a sheet.
    LaunchedEffect(pv.open, pop != null) { chrome.setCovered(pv.open || pop != null) }
    DisposableEffect(Unit) { onDispose { chrome.setCovered(false) } }
    // Back on a recreated screen: a preview that was opening is shown open, one that was closing is closed.
    LaunchedEffect(Unit) {
        if (pv.closing) {
            pv.closing = false
            pv.open = false
        } else if (pv.open) {
            pv.snapOpen()
        }
    }

    val statusTop = chrome.contentPadding.calculateTopPadding()
    val bottomPad = chrome.contentPadding.calculateBottomPadding()
    val appBarHeight = statusTop + 64.dp
    var listHeight by remember { mutableIntStateOf(0) }

    fun show(s: Snack) { snack = s }
    // The page is pictured as it leaves (the back gesture shows that picture), so the menu goes first and the page is
    // drawn without it before Widget Customization opens.
    fun customize() {
        pop = null
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            chrome.openWidgetSettings()
        }
    }

    fun boundsOf(map: Map<String, LayoutCoordinates>, key: String): Rect? {
        val root = rootCoords ?: return null
        val c = map[key]?.takeIf { it.isAttached } ?: return null
        return root.localBoundingBoxOf(c, clipBounds = false)
    }

    fun decorOf(key: String?, look: Look): Decor {
        val on = look.id == state.current.id
        return when {
            key == null || key == "paste" -> Decor.None
            key.startsWith("hero:") -> if (on) Decor.Badge else Decor.None
            key.startsWith("yours:") -> if (on) Decor.SmallBadge else Decor.None
            else -> if (on) Decor.Badge else Decor.None
        }
    }

    fun openPreview(look: Look, context: String, sceneKey: String?, fromRect: Rect? = null, fromBarRect: Rect? = null) {
        if (pv.open) return
        val list = state.previewList(context, look)
        state.opens++
        pv.list = list
        pv.index = list.indexOf(look).coerceAtLeast(0)
        pv.context = context
        pv.search = false
        pv.bare = false
        pv.applied = false
        pv.noHint = false
        val from = fromRect ?: sceneKey?.let { boundsOf(scenes, it) }
        val root = rootCoords
        pv.from = from ?: Rect(Offset((root?.size?.width ?: 0) / 2f, (root?.size?.height ?: 0) / 2f), 0f)
        pv.fromRadius = with(density) { 28.dp.toPx() }
        pv.fromBar = fromBarRect ?: sceneKey?.let { boundsOf(bars, it) }
        pv.decor = if (from != null) decorOf(sceneKey, look) else Decor.None
        pop = null
        snack = null
        scope.launch {
            pv.snapClosed()
            pv.open = true
            launch { pv.bounds.animateTo(1f, Springs.damped()) }
            launch { pv.barTravel.animateTo(1f, Springs.spatial()) }
            delay(60)
            pv.chrome.animateTo(1f, Springs.spatial())
        }
    }

    fun undo() {
        if (state.undo()) show(Snack("Back to your look."))
    }

    fun askKeep(onReplace: () -> Unit, onAnyway: () -> Unit) {
        pop = Pop.Dialog(
            "Keep Your Current Look?",
            "Your saved looks are full. Applying replaces your current search bar and search page. You can still undo right after.",
            listOf(
                DialogAction("Replace a Saved Look", ButtonKind.Filled) { onReplace(); false },
                DialogAction("Apply Anyway", ButtonKind.Tonal) { onAnyway(); true },
                DialogAction("Cancel")
            ),
            stacked = true
        )
    }

    fun pickSlot(look: Look, after: (Look) -> Unit) {
        pop = Pop.Dialog(
            "Saved Looks Are Full", "You can keep five looks. Replace one to save another.", listOf(DialogAction("Cancel")),
            picks = state.saved.toList(),
            onPick = { k ->
                val old = state.saved[k]
                pop = Pop.Dialog(
                    "Replace “${old.name}”?", null,
                    listOf(DialogAction("Cancel"), DialogAction("Replace", ButtonKind.Filled) { after(state.store(look, state.defaultName(look), k)); true })
                )
            }
        )
    }

    fun save(look: Look) {
        if (state.isSaved(look)) return show(Snack("Saved to Yours."))
        if (state.savedFull) return pickSlot(look) { show(Snack("Saved to Yours.")) }
        pop = Pop.Dialog(
            "Name This Look", null, listOf(DialogAction("Cancel"), DialogAction("Save", ButtonKind.Filled)),
            input = state.defaultName(look),
            onInput = { name ->
                when {
                    name.isBlank() -> "Give it a name."
                    state.nameTaken(name) -> "You already have a look called “${name.trim()}”. Give this one another name."
                    else -> { state.store(look, name.trim()); show(Snack("Saved to Yours.")); null }
                }
            }
        )
    }

    fun applyLook(look: Look, asBefore: Boolean = false, force: Boolean = false, onDone: (Commit) -> Unit = { r -> if (r is Commit.Done) show(r.snack) }) {
        val r = state.commit(look, force, asBefore) { undo() }
        if (r == Commit.AskKeep) {
            askKeep(
                onReplace = { pickSlot(state.current) { applyLook(look, asBefore, true, onDone) } },
                onAnyway = { applyLook(look, asBefore, true, onDone) }
            )
        } else onDone(r)
    }

    fun share(look: Look) { pop = Pop.Share(look) }

    // The phone's own wallpaper. It changes only from the sheet Set wallpaper opens, and can be put back when the
    // phone let the old one be copied first.
    var keptWallpaper by remember { mutableStateOf(PhoneWallpaper.kept(appContext)) }

    fun restoreWallpaper() {
        session.scope.launch {
            val back = withContext(Dispatchers.IO) { PhoneWallpaper.restore(appContext) }
            keptWallpaper = PhoneWallpaper.kept(appContext)
            show(Snack(if (back) "Your wallpaper from before is back." else "Your wallpaper from before couldn't be brought back."))
        }
    }

    fun askWallpaper(look: Look) {
        val wallpaper = wallpaperOf(look, look.appDark(phoneDark)) ?: return
        val from = store ?: return
        if (!PhoneWallpaper.allowed(appContext)) return show(Snack("The wallpaper can't be changed from here on this phone."))
        scope.launch {
            // Found out first, so the sheet can say whether there is an undo before anything is set.
            val canUndo = withContext(Dispatchers.IO) { PhoneWallpaper.canUndo(appContext) }
            var sheet: Pop.Wallpaper? = null
            sheet = Pop.Wallpaper(look, canUndo) { where ->
                // On the session's scope: Android may recreate the screen for the new wallpaper's colors while (or
                // just after) it is set, and the confirmation must still show.
                session.scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val picture = from.wallpaperFile(wallpaper)
                            // The left of the picture is the screen; the rest is for the home screen to slide to.
                            val screen = if (from.size.fullWidth > from.size.width) android.graphics.Rect(0, 0, from.size.width, from.size.height) else null
                            PhoneWallpaper.set(appContext, picture, screen, where, canUndo)
                        }
                    }
                    if (pop === sheet) pop = null
                    keptWallpaper = PhoneWallpaper.kept(appContext)
                    val undo = result.getOrNull() == true
                    show(
                        when {
                            result.isFailure -> Snack("The wallpaper couldn't be set.")
                            undo -> Snack("Wallpaper set on your ${where.words}.", "Undo") { restoreWallpaper() }
                            canUndo -> Snack("Wallpaper set on your ${where.words}. Your old one couldn't be kept, so there is no undo.")
                            else -> Snack("Wallpaper set on your ${where.words}.")
                        }
                    )
                }
            }
            pop = sheet
        }
    }

    fun paste() {
        pop = Pop.Dialog(
            "Paste a Code", "Got a look from a friend? Paste it here and it's yours.", listOf(DialogAction("Cancel"), DialogAction("Try It", ButtonKind.Filled)),
            input = "", inputHint = "IS2.…",
            onInput = { text ->
                val look = LookCode.decode(text)
                if (look == null) "That isn't a look code, or part of it is missing."
                else {
                    state.shared = look
                    scope.launch { delay(16); openPreview(look, "shared", "paste") }
                    null
                }
            }
        )
    }

    fun tileMenu(anchor: Rect, kind: String, look: Look) {
        val items = mutableListOf<MenuItem>()
        fun rename(l: Look) = MenuItem(Symbols.Edit, "Rename") {
            pop = Pop.Dialog("Rename", null, listOf(DialogAction("Cancel"), DialogAction("Save", ButtonKind.Filled)), input = l.name, onInput = { n ->
                if (n.isBlank()) "Give it a name."
                else if (state.nameTaken(n, except = l.id)) "You already have a look called “${n.trim()}”. Give this one another name."
                else {
                    val k = state.saved.indexOfFirst { it.id == l.id }
                    val renamed = l.copy(name = n.trim())
                    if (k >= 0) state.saved[k] = renamed
                    if (state.current.id == l.id) state.current = renamed
                    null
                }
            })
        }
        when (kind) {
            "now" -> {
                if (state.saved.any { it.id == state.current.id }) items += rename(state.current)
                else if (!state.isSaved(state.current)) items += MenuItem(Symbols.BookmarkAdd, "Save") { save(state.current) }
                items += MenuItem(Symbols.IosShare, "Share Look Code") { share(state.current) }
                items += MenuItem(Symbols.Tune, "Customize") { customize() }
            }
            "before" -> {
                items += MenuItem(Symbols.Undo, "Restore") { state.before?.let { applyLook(it, asBefore = true) } }
                state.before?.let { b -> if (!state.isSaved(b)) items += MenuItem(Symbols.BookmarkAdd, "Save") { save(b) } }
            }
            else -> {
                items += rename(look)
                if (!state.isSaved(state.current)) items += MenuItem(Symbols.SwapHoriz, "Replace with Current Look") {
                    val k = state.saved.indexOfFirst { it.id == look.id }
                    if (k >= 0) pop = Pop.Dialog("Replace “${look.name}”?", null, listOf(DialogAction("Cancel"), DialogAction("Replace", ButtonKind.Filled) {
                        state.store(state.current, look.name, k); show(Snack("Saved to Yours.")); true
                    }))
                }
                items += MenuItem(Symbols.IosShare, "Share Look Code") { share(look) }
                items += MenuItem(Symbols.Delete, "Delete") {
                    val k = state.saved.indexOfFirst { it.id == look.id }
                    if (k >= 0) {
                        state.saved.removeAt(k)
                        show(Snack("“${look.name}” deleted.", "Undo") { if (state.saved.size < 5) state.saved.add(k.coerceAtMost(state.saved.size), look) })
                    }
                }
            }
        }
        pop = Pop.Menu(anchor, items)
    }

    fun info() {
        pop = Pop.Info(
            backdrop,
            onBackdrop = { backdropName = it.name },
            putBack = state.before?.let { b -> { applyLook(b, asBefore = true) } },
            // The lasting undo for a wallpaper set from here, while the old one is kept.
            bringBack = if (keptWallpaper) ({ restoreWallpaper() }) else null
        )
    }

    // The page's item order, so the preview can scroll a card back into view before shrinking into it.
    val season = Season.of(today)
    val inSeason = remember(today) { rack("seasonal", today).filter { it.season == season } }
    val rackLooks = remember(today) { fullRack(today) }
    LaunchedEffect(store) { store?.start(rackLooks.filter { wallpaperOf(it) != null }.map { it.id }) }
    val keys = buildList {
        add("title"); add("hero"); add("yoursHead"); add("yours")
        if (inSeason.isNotEmpty()) add("season")
        add("allHead"); add("filters"); add("fan")
        add("foot")
    }

    // All Looks: the swatch fan and the clock its strips turn on. Its place is kept in the session, so a tab switch or a
    // recreated screen finds it where it was; the selected chip is the kind of the look it stands on (or turns to).
    val clock = remember { MotionClock() }
    val fan = remember { FanEngine(clock, stripOfLook(session.fanAt)) }
    LaunchedEffect(fan) { clock.run { fan.layout() } }
    LaunchedEffect(fan) { snapshotFlow { fan.dest }.collect { session.fanAt = lookOfStrip(it) } }
    val chip by remember(fan) { derivedStateOf { fanGroupOf(stripLookId(fan.dest)).f } }
    // Where a strip's picture and bar lie on the page (set by the fan).
    val fanRects = remember { arrayOfNulls<(Int) -> Pair<Rect, Rect?>?>(1) }
    // Once the preview has closed, the strip it came out of drops back into its slot.
    LaunchedEffect(pv.open) { if (!pv.open && fan.raised >= 0) fan.raise(false) }

    // Built each composition, so it always reads the current theme and backdrop.
    val host = run {
        object : PreviewHost {
            override val state = state
            override val phoneDark = phoneDark
            override val backdrop = backdrop
            override val popOpen = pop != null
            override fun setWallpaper(look: Look) = askWallpaper(look)
            override fun apply(look: Look, asBefore: Boolean, onDone: (Commit) -> Unit) = applyLook(look, asBefore, onDone = onDone)
            override fun save(look: Look) = save(look)
            override fun customize() = customize()
            override fun snack(s: Snack) = show(s)
            override suspend fun target(look: Look, context: String): PreviewTarget? {
                val key = when (context) {
                    "hero" -> if (look.id == state.hero.id) "hero:${look.id}" else "rack:${look.id}"
                    "yours" -> "yours:${look.id}"
                    "season" -> if (look in inSeason) "season:${look.id}" else "rack:${look.id}"
                    "shared" -> "paste"
                    else -> "rack:${look.id}"
                }
                // Closing on a look of the fan goes home to its own strip: the fan is turned to it first, and the strip is
                // held raised out of the deck until the preview has come down onto it.
                if (key.startsWith("rack:")) {
                    val i = FAN_ORDER.indexOf(look.id).let { if (it >= 0) stripOfLook(it) else -1 }
                    if (i >= 0) {
                        fan.toFront(i)
                        fan.raise(true, i, now = true)
                        val viewTop0 = with(density) { appBarHeight.toPx() }
                        val viewBottom0 = (rootCoords?.size?.height ?: 0) - with(density) { bottomPad.toPx() }
                        withFrameNanos { }
                        var rr = fanRects[0]?.invoke(i)
                        if (rr == null || rr.first.top < viewTop0 || rr.first.bottom > viewBottom0) {
                            listState.scrollToItem(keys.indexOf("allHead"))
                            withFrameNanos { }
                            withFrameNanos { }
                            rr = fanRects[0]?.invoke(i)
                        }
                        if (rr != null) return PreviewTarget(rr.first, rr.second, Decor.None)
                    }
                }
                val viewTop = with(density) { appBarHeight.toPx() }
                val viewBottom = (rootCoords?.size?.height ?: 0) - with(density) { bottomPad.toPx() }
                fun visible(r: Rect?) = r != null && r.top >= viewTop && r.bottom <= viewBottom
                withFrameNanos { }
                var r = boundsOf(scenes, key)
                if (!visible(r) && context != "shared") {
                    // Scroll the card under the preview into view first (the preview still covers the page).
                    val idx = keys.indexOf(
                        when {
                            context == "yours" -> "yours"
                            key.startsWith("season:") -> "season"
                            key.startsWith("hero:") -> "hero"
                            else -> "allHead"
                        }
                    ).takeIf { it >= 0 }
                    if (idx != null) {
                        listState.scrollToItem(idx, if (key.startsWith("rack:")) 0 else -with(density) { 200.dp.roundToPx() })
                        withFrameNanos { }
                        withFrameNanos { }
                        r = boundsOf(scenes, key)
                    }
                }
                if (r == null) return null
                return PreviewTarget(r, boundsOf(bars, key), decorOf(key, look))
            }
        }
    }

    val showTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 56.dp.toPx() } } }
    // At the very top of the page the pill always shows, however the page got there (the title's scroll to the top, a
    // chip): the shell only hears scrolls a finger makes.
    LaunchedEffect(listState) { snapshotFlow { listState.canScrollBackward }.collect { if (!it) chrome.revealPill() } }
    // The page glow drifts only while the top of the page is in sight; scrolled down, the page can rest.
    val glowAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    val glowClock = rememberMeshClock(isPaused = glowAway || pv.covering, frameGapMs = PAGE_GLOW_FRAME_GAP_MS)

    // Open the preview straight away (screenshot tests).
    val openOnStart = LocalGalleryOpenPreview.current
    LaunchedEffect(openOnStart) {
        val (id, search) = openOnStart ?: return@LaunchedEffect
        val look = state.find(id) ?: return@LaunchedEffect
        openPreview(look, "rack", null)
        delay(700)
        if (search) { pv.search = true; pv.searchReveal.snapTo(1f) }
    }

    CompositionLocalProvider(LocalWallpaperStore provides store, LocalGalleryBackdrop provides backdrop) {
        Box(modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }) {
            // Colors only: the mesh the windows look through to, drawn behind the page (the real wallpaper needs nothing:
            // Android draws it behind the window).
            if (backdrop == Backdrop.ColorsOnly || standIn) {
                val blurPx = pv.backdropBlur
                WallMesh(
                    wallColors,
                    Modifier.fillMaxSize().graphicsLayer {
                        if (blurPx > 0.5f) renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    }
                )
            }
            // One offscreen layer for the whole page: the windows clear their shapes in it (BlendMode.Clear).
            Box(Modifier.fillMaxSize().offscreenLayer()) {
                // The page (not drawn while the full preview covers it), with the page glow at its top.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (pv.covering) Modifier.clearAndSetSemantics { } else Modifier)
                        .drawWithContent {
                            if (pv.covering) return@drawWithContent
                            drawContent()
                        }
                        .background(scheme.background)
                        .pageGlow(clock = glowClock)
                ) {
                  Column(Modifier.fillMaxSize()) {
                    // The top bar: back, the small title once the big one has scrolled away, and How Looks Work.
                    GalleryTopBar(
                        showTitle,
                        onBack = chrome.onBack,
                        onTitle = { scope.launch { scrollToTop(listState) } },
                        more = { TopBarAction(Symbols.Info, "How Looks Work", { info() }) },
                        modifier = Modifier.padding(top = statusTop).semantics { isTraversalGroup = true; traversalIndex = -2f }
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { listHeight = it.height },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = GalleryGutter, end = GalleryGutter, top = 0.dp, bottom = bottomPad + 16.dp
                        )
                    ) {
                        items(keys, key = { it }, contentType = { it }) { key ->
                            when (key) {
                                "title" -> DisplayTitle()
                                "hero" -> HeroB(
                                    state.hero, state, phoneDark, today,
                                    registerScene = { scenes["hero:${state.hero.id}"] = it }, registerBar = { bars["hero:${state.hero.id}"] = it },
                                    onOpen = { openPreview(state.hero, "hero", "hero:${state.hero.id}") },
                                    onApply = { applyLook(state.hero) }, onCustomize = ::customize
                                )
                                "yoursHead" -> SectionTitle("Yours") {
                                    Box(Modifier.onGloballyPositioned { scenes["paste"] = it; bars.remove("paste") }) {
                                        TonalIconButton("Paste a Code", Symbols.ContentPaste, ::paste)
                                    }
                                }
                                "yours" -> YoursRowB(
                                    state, phoneDark,
                                    register = { id, c -> scenes["yours:$id"] = c }, registerBar = { id, c -> bars["yours:$id"] = c },
                                    onOpen = { look -> openPreview(look, "yours", "yours:${look.id}") },
                                    onMenu = { anchor, kind, look -> tileMenu(anchor, kind, look) },
                                    onSaveCurrent = { save(state.current) },
                                    rootCoords = { rootCoords }
                                )
                                "season" -> SeasonSection(
                                    season, inSeason, state, phoneDark,
                                    register = { k, c -> scenes[k] = c }, registerBar = { k, c -> bars[k] = c },
                                    onOpen = { look -> openPreview(look, "season", "season:${look.id}") },
                                    onApply = { look -> applyLook(look) }, onCustomize = ::customize
                                )
                                // The counter says where the fan is in the deck; a tap on it opens the number picker.
                                "allHead" -> SectionTitle("All Looks", badge = {
                                    FanCounter(fan, { rootCoords }) { anchor ->
                                        pop = Pop.Picker(anchor, lookOfStrip(fan.dest)) { k ->
                                            fan.show(stripOfLook(k))
                                            scope.launch { listState.animateScrollToItem(keys.indexOf("allHead")) }
                                        }
                                    }
                                })
                                "filters" -> FanChips(chip) { f ->
                                    fan.show(stripOfLook(FAN_ORDER.indexOf(FAN_GROUPS.first { it.f == f }.ids[0])))
                                    scope.launch { listState.animateScrollToItem(keys.indexOf("allHead")) }
                                }
                                "fan" -> SwatchFan(
                                    fan, phoneDark, GalleryGutter, wallColors,
                                    FanActions(
                                        onOpen = { look, pic, bar ->
                                            val i = FAN_ORDER.indexOf(look.id).let { if (it >= 0) stripOfLook(it) else -1 }
                                            if (!pv.open && i >= 0) {
                                                fan.raise(true, i)
                                                openPreview(look, "rack", "rack:${look.id}", fromRect = pic, fromBarRect = bar)
                                            }
                                        },
                                        isRaisedHidden = { i -> pv.open && pv.context == "rack" && fan.raised == i },
                                        rects = fanRects
                                    ),
                                    rootCoords = { rootCoords },
                                    // What the list shows under the top bar, above the navigation bar (the floating pill
                                    // moves out of the way as the page scrolls down to the fan).
                                    room = if (listHeight > 0) {
                                        with(density) { listHeight.toDp() } - (bottomPad - com.pixel.intelligentsearch.feature.tabs.PILL_HEIGHT - com.pixel.intelligentsearch.feature.tabs.PILL_BOTTOM_GAP) - 16.dp
                                    } else Dp.Unspecified
                                )
                                "foot" -> Footer()
                            }
                        }
                    }
                  }
                }

                // The full preview: a window grown from the card to the whole screen.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    GalleryPreview(pv, host, blurAvailable, w, h)
                }

                // Over the page, above the pill. Over the full preview, on whichever side of the bar has more room: just
                // under the top row, or (the bar moved up there) just over the notes and buttons at the bottom, so it never
                // covers the bar being previewed, the top row or the buttons.
                val snackFromTop = pv.open && pv.barTop - pv.topEnd >= pv.notesTop - pv.barBottom
                PreviewSnackbar(
                    snack,
                    bottom = if (pv.open) with(density) { pv.lowReach.toDp() } + 8.dp else bottomPad + 8.dp,
                    top = if (snackFromTop) with(density) { pv.topEnd.toDp() } + 8.dp else null,
                    onDismiss = { snack = null },
                    modifier = Modifier.align(if (snackFromTop) Alignment.TopCenter else Alignment.BottomCenter)
                )

                PopLayer(pop, phoneDark, onClose = { pop = null }, snack = ::show)
            }
        }
    }
}

/**
 * Draws the element and everything in it into one offscreen layer (an explicit saveLayer, which hardware and software
 * canvases both honor), so a window's BlendMode.Clear clears the page there and nothing behind the page.
 */
private fun Modifier.offscreenLayer(): Modifier = drawWithContent {
    val canvas = drawContext.canvas
    canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
    drawContent()
    canvas.restore()
}
