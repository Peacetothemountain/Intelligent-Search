package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.findActivity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Activity
import android.content.Context
import android.os.Build
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.ui.pageGlow
import com.pixel.intelligentsearch.core.ui.backFrame
import com.pixel.intelligentsearch.core.ui.backScrim
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import com.pixel.intelligentsearch.core.data.SettingsManager

val LocalSettingsViewModel = staticCompositionLocalOf<SettingsViewModel?> {
    null
}

val LocalSettingsState = compositionLocalOf<com.pixel.intelligentsearch.core.data.IntelligentSearchSettings?> {
    null
}

/**
 * The tutorial's last two steps, shown over Settings. The page is hidden behind the tutorial's backdrop while they
 * show, so they say what Settings holds and what Got it opens rather than point at rows the user can't see.
 */
internal val SETTINGS_TUTORIAL_STEPS = mapOf(
    3 to TutorialStepInfo(
        "Settings & Features",
        "Settings is where you make Intelligent Search your own: its look, your widget and how search works.",
        Alignment.Center,
        showArrow = false
    ),
    4 to TutorialStepInfo(
        "Make It Yours",
        "Tap Got it to see every category and fine-tune Intelligent Search to the way you search. You're all set!",
        Alignment.Center,
        showArrow = false
    )
)

/**
 * A page ([closing]) leaving at the end of a back swipe, taken off the back stack by [pop] once its animation ([job])
 * has played, or at once by [finishNow] when the next swipe starts first.
 */
/** A page as last seen before another opened over it, with the glow's moment in it. */
private class PagePicture(val image: androidx.compose.ui.graphics.ImageBitmap, val glowTime: Float)

private class LeavingPage(val closing: androidx.navigation.NavBackStackEntry?, private val pop: () -> Unit) {
    var job: kotlinx.coroutines.Job? = null
    private var left = false

    /** Takes the page off the back stack, however often it is asked. */
    fun leave() {
        if (left) return
        left = true
        pop()
    }

    /** The page leaves now, as at the end of its animation, which stops. */
    fun finishNow() {
        leave()
        job?.cancel()
    }
}

@Composable
fun SettingsScreensHub(
    initialRoute: com.pixel.intelligentsearch.core.navigation.Route = com.pixel.intelligentsearch.core.navigation.Route.Main,
    fromSearchOverlay: Boolean = false,
    prefs: SharedPreferences,
    onBackToLauncher: () -> Unit,
    context: Context
) {
    val viewModel: SettingsViewModel = LocalSettingsViewModel.current ?: androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val localSettings = LocalSettingsState.current
    val settingsState by if (localSettings != null) {
        rememberUpdatedState(localSettings)
    } else {
        viewModel.settingsState.collectAsStateWithLifecycle()
    }

    CompositionLocalProvider(
        LocalSettingsViewModel provides viewModel,
        LocalSettingsState provides settingsState
    ) {
        val navController = androidx.navigation.compose.rememberNavController()
        // Held by the hub so the clip stays loaded while moving between settings pages; MainSettingsScreen plays
        // it only while it is the resumed page.
        val exoPlayer = rememberBugdroidPlayer(BugdroidClip.SETTINGS, autoPlay = false)

        // Android's own back animation between pages: each page is remembered as it was last seen when another opens
        // over it, so a back swipe can show it underneath as the system shows the activity underneath.
        val nativeBack = remember { com.pixel.intelligentsearch.core.ui.NativeBackState() }
        val pageSnapshots = remember { mutableStateMapOf<String, PagePicture>() }
        val pagePicture = remember { com.pixel.intelligentsearch.core.ui.PageSnapshotter { context.findActivity()?.window } }
        DisposableEffect(pagePicture) { onDispose { pagePicture.release() } }
        // Where the pages are in the window, for their pictures.
        val pageBounds = remember { android.graphics.Rect() }
        val navScope = rememberCoroutineScope()
        // Set while a page leaves at the end of the back animation, which has already moved it: no transition then.
        var instantPop by remember { mutableStateOf(false) }
        // The page a let-go back swipe is sending away while its animation plays, until it has left.
        val leavingPage = remember { mutableStateOf<LeavingPage?>(null) }

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        // A page back on top is shown live again: its picture is taken anew when another page next opens over it.
        LaunchedEffect(currentBackStackEntry) {
            currentBackStackEntry?.id?.let { pageSnapshots.remove(it) }
        }
        // Reading the current entry makes the shell recompose on every navigation, so the exit gesture below is
        // switched on and off as the user moves between Main and the sub-pages.
        val hasSubScreensInNavHost = currentBackStackEntry.let { navController.previousBackStackEntry != null }
        val onWidgetPage = currentBackStackEntry?.destination?.hasRoute<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization>() == true

        // One drift for the glow behind the page and behind the picture of the page underneath. The picture has the
        // glow as it was when taken; as the real page takes the picture's place, the drift is set back to that moment,
        // so the light carries on from the picture and never jumps. It stands still on Widget Customization, which
        // has no glow, except while a back swipe shows the page underneath.
        val glowClock = com.pixel.intelligentsearch.core.ui.rememberMeshClock(isPaused = onWidgetPage && !nativeBack.active)
        var enteringGlow by remember { mutableFloatStateOf(1f) }
        // The glow's moment in the picture of the page underneath a back swipe, until the real page has taken over.
        var enteringGlowTime by remember { mutableStateOf<Float?>(null) }
        fun carryGlowOnFromPicture() {
            enteringGlowTime?.let { glowClock.floatValue = it }
            enteringGlowTime = null
        }

        val onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = { route ->
            val leaving = navController.currentBackStackEntry
            val glowTime = glowClock.floatValue
            // The picture is of the page as last drawn, so it is taken now, before the new page draws over it; it
            // arrives a few milliseconds later on its own and nothing waits for it.
            pagePicture.take(pageBounds) { picture ->
                if (leaving != null && picture != null) pageSnapshots[leaving.id] = PagePicture(picture, glowTime)
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        val handleExitBack: () -> Unit = {
            ClickDebouncer.recordClick()
            val act = context.findActivity() ?: (context as? Activity)
            val isBackToOverlay = prefs[SettingsManager.BACK_TO_SEARCH_OVERLAY]
            if (act != null) {
                if (isBackToOverlay) {
                    if (act.isTaskRoot || !fromSearchOverlay) {
                        val intent = Intent(context, com.pixel.intelligentsearch.feature.search.SearchActivity::class.java).apply {
                            putExtra("FROM_BACK_SWIPE", true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                    }
                    act.finish()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        act.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
                    } else {
                        @Suppress("DEPRECATION")
                        act.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                } else {
                    (act as? com.pixel.intelligentsearch.feature.settings.SettingsActivity)?.isExitingToHome = true
                    context.sendBroadcast(Intent("com.pixel.intelligentsearch.DISMISS_OVERLAY").setPackage(context.packageName))
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    act.finishAffinity()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        act.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        act.overridePendingTransition(0, 0)
                    }
                }
            } else {
                onBackToLauncher()
            }
        }

        // The glow fades out as Widget Customization comes in and back as it leaves; after a back swipe the picture of
        // the page underneath already showed it at its own strength, so it is set at once rather than faded in again.
        val pageGlowStrength = remember { Animatable(if (onWidgetPage) 0f else 1f) }
        LaunchedEffect(onWidgetPage) {
            val target = if (onWidgetPage) 0f else 1f
            if (instantPop) pageGlowStrength.snapTo(target)
            else pageGlowStrength.animateTo(target, tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }

        val onBack: () -> Unit = onBack@{
            // A quick double tap would pop the sub-page, then see the root and exit Settings entirely.
            if (!ClickDebouncer.canClick(cooldownMs = 400L, key = "settings_top_bar_back")) return@onBack
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                handleExitBack()
            }
        }

        val exitBackProgress = remember { Animatable(0f) }
        val exitSwipes = remember { java.util.concurrent.atomic.AtomicInteger() }
        var predictiveBackSwipeEdge by remember { mutableIntStateOf(androidx.activity.BackEventCompat.EDGE_LEFT) }

        androidx.activity.compose.PredictiveBackHandler(enabled = !hasSubScreensInNavHost) { progressFlow ->
            val act = context.findActivity()
            val swipe = exitSwipes.incrementAndGet()
            try {
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
                progressFlow.collect { backEvent ->
                    predictiveBackSwipeEdge = backEvent.swipeEdge
                    exitBackProgress.snapTo(backEvent.progress)
                }
                exitBackProgress.snapTo(0f)
                handleExitBack()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A cancelled swipe arrives as cancellation, so the page is sprung back outside it; otherwise the
                // animation would end at once and leave the page shrunk. A new swipe's snapTo still cuts it short.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    exitBackProgress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 300f))
                }
                throw e
            } finally {
                // A swipe started while this one sprang back is already running at the high rate; resetting here
                // would drop it to the normal rate (and switch touch boost off) for the rest of that swipe.
                if (exitSwipes.get() == swipe) {
                    com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(act)
                }
            }
        }

        val view = androidx.compose.ui.platform.LocalView.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val darkPage = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val animationsOn by com.pixel.intelligentsearch.core.ui.rememberAnimationsEnabled()
        var screenCorner by remember { mutableFloatStateOf(0f) }

        /**
         * Hands over from the back animation to the real page underneath once [closing] has left: the picture stays
         * until that page is on top and the page that left is gone. The page that left is still drawn, whole, until
         * its (empty) exit transition is done: handing over before that flashed it over the page underneath for a
         * frame.
         */
        suspend fun awaitLeft(closing: androidx.navigation.NavBackStackEntry?) {
            snapshotFlow { navController.currentBackStackEntry?.id }.first { it != closing?.id }
            kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                closing?.lifecycle?.currentStateFlow?.first { it == androidx.lifecycle.Lifecycle.State.DESTROYED }
            }
            withFrameNanos { }
            instantPop = false
        }

        suspend fun handOver(closing: androidx.navigation.NavBackStackEntry?) {
            awaitLeft(closing)
            carryGlowOnFromPicture()
            nativeBack.end()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onSizeChanged {
                    nativeBack.size = it
                    nativeBack.marginPx = with(density) { com.pixel.intelligentsearch.core.ui.NativeBackMotion.MARGIN_DP.dp.toPx() }
                    nativeBack.offsetPx = with(density) { com.pixel.intelligentsearch.core.ui.NativeBackMotion.ENTERING_OFFSET_DP.dp.toPx() }
                }
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    pageBounds.set(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt())
                }
        ) {
        if (nativeBack.active && !nativeBack.liveEntering) {
            // The page underneath, as it was last seen, under the scrim.
            Box(
                Modifier
                    .fillMaxSize()
                    .backFrame(frame = { nativeBack.enteringFrame() }, cornerPx = { screenCorner })
                    .background(MaterialTheme.colorScheme.background)
                    .pageGlow(strength = { enteringGlow }, clock = glowClock)
                    .drawBehind {
                        nativeBack.entering?.let {
                            drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                        }
                    }
            )
            Box(Modifier.fillMaxSize().drawBehind { drawRect(backScrim(darkPage, nativeBack.scrimFraction())) })
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backFrame(
                    frame = {
                        when {
                            !nativeBack.active -> null
                            // The real page underneath, settling the rest of the way in its picture's place.
                            nativeBack.liveEntering -> nativeBack.enteringFrame()
                            else -> nativeBack.closingFrame()
                        }
                    },
                    cornerPx = { screenCorner },
                    alpha = { if (nativeBack.liveEntering) 1f else nativeBack.closingAlpha() }
                )
                .background(MaterialTheme.colorScheme.background)
                // Every page but Widget Customization (whose previews carry their own colours) has the glow behind
                // its title; it fades out as that page comes in and back as it leaves.
                .pageGlow(strength = { pageGlowStrength.value }, clock = glowClock)
                .graphicsLayer {
                    val p = exitBackProgress.value
                    if (p > 0f) {
                        val predictiveScale = 1f - (p * 0.08f)
                        scaleX = predictiveScale
                        scaleY = predictiveScale
                        val maxShiftPx = 48.dp.toPx()
                        translationX = if (predictiveBackSwipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) {
                            p * maxShiftPx
                        } else {
                            -p * maxShiftPx
                        }
                        alpha = (1f - p * 0.25f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(
                            if (predictiveBackSwipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT) 0.05f else 0.95f,
                            0.5f
                        )
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        alpha = 1f
                    }
                }
        ) {
            var showTutorial by remember { mutableStateOf(TutorialManager.isTutorialActive(prefs)) }
            val tutorialCompleted by rememberSetting(SettingsManager.TUTORIAL_COMPLETED) {}
            
            // Reactively sync tutorial visibility with the completed pref so it stays dismissed
            // permanently after finishing, but re-activates if force-tutorial resets it.
            LaunchedEffect(tutorialCompleted) {
                if (tutorialCompleted) {
                    showTutorial = false
                } else if (TutorialManager.isTutorialActive(prefs)) {
                    showTutorial = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showTutorial) Modifier.blur(24.dp) else Modifier)
                    // Hidden from TalkBack too while the tutorial covers it, so it can't be reached or activated.
                    .then(if (showTutorial) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = initialRoute,
                    enterTransition = {
                        if (initialState.destination.route == null) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { (it * 0.25f).toInt() },
                                animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 240, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                            )
                        }
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -(it * 0.10f).toInt() },
                            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                        )
                    },
                    popEnterTransition = {
                        if (instantPop) androidx.compose.animation.EnterTransition.None else slideInHorizontally(
                            initialOffsetX = { -(it * 0.10f).toInt() },
                            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 240, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                        )
                    },
                    popExitTransition = {
                        if (instantPop) androidx.compose.animation.ExitTransition.None else slideOutHorizontally(
                            targetOffsetX = { (it * 0.25f).toInt() },
                            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                        )
                    }
                ) {
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Main> { MainSettingsScreen(prefs, onNavigate, onBack, context, exoPlayer, showTutorial) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Appearance> { AppearanceScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.CustomIcons> { CustomIconsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchSources> { SearchSourcesScreen(onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior> { SearchBehaviorScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal> { LaunchPortalScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.AppSearch> { AppSearchScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchPills> { SearchPillsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WebSearch> { WebSearchScreen(prefs, onBack, onNavigate) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.AiAnswers> { AiAnswersScreen(onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ContactSearch> { ContactSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.FileSearch> { FileSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization> { WidgetSettingsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps> { ManageHiddenAppsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.BackupRestore> { BackupRestoreScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Setup> { SetupChecklistScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Debug> {
                        if (prefs[SettingsManager.DEBUG_UNLOCKED]) {
                            DebugScreen(
                                prefs = prefs,
                                onBack = onBack,
                                onDisableDebug = {
                                    disableDebugMode(context, prefs)
                                    if (!navController.popBackStack()) {
                                        navController.navigate(com.pixel.intelligentsearch.core.navigation.Route.Main) {
                                            popUpTo(0)
                                        }
                                    }
                                }
                            )
                        } else {
                            MainSettingsScreen(prefs, onNavigate, onBack, context, exoPlayer, showTutorial)
                        }
                    }
                }
            }
            
            if (showTutorial) {
                // The app's theme, like the tutorial card above it, not the system's.
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val overlayColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayColor)
                ) {
                    AnimatedMatrixBackground()
                }
                TutorialSpotlightOverlay(
                    prefs = prefs,
                    stepsInfo = SETTINGS_TUTORIAL_STEPS,
                    onComplete = { showTutorial = false }
                )
            }
        }
        if (nativeBack.active && nativeBack.liveEntering) {
            // The scrim over the page underneath fades out above it now that it is the real page.
            Box(Modifier.fillMaxSize().drawBehind { drawRect(backScrim(darkPage, nativeBack.scrimFraction())) })
        }
        if (nativeBack.active && !nativeBack.liveEntering) {
            // While the pages move, the page underneath is only a picture, and the real page under it is the one
            // leaving, invisible but still there to be tapped: a tap meant for the page coming back flipped a switch
            // on the page that left. So no touch gets through until the real page takes the picture's place, about a
            // tenth of a second after letting go. The back arrow is held back with the rest: it belongs to the page
            // that is leaving.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
            )
        }

        // Back between pages, with Android's own animation. Registered after the NavHost's own handler, so it takes
        // the gesture first; with animations removed the NavHost's handler plays its plain transition instead.
        androidx.activity.compose.PredictiveBackHandler(enabled = hasSubScreensInNavHost && animationsOn) { events ->
            // A swipe started while the last one's page is still leaving (its animation plays on after the finger
            // lets go): that page leaves now, as at the end of its animation, so this swipe goes back from the page
            // under it and two quick swipes go back two pages.
            leavingPage.value?.let {
                leavingPage.value = null
                it.finishNow()
                // Handed over as at the end of its animation, hidden meanwhile, so the page that left doesn't flash
                // back as this swipe starts; this swipe's first moves wait those few frames. Seen through even if
                // this swipe is cancelled meanwhile, which would otherwise leave the picture up.
                nativeBack.hideClosing()
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { handOver(it.closing) }
            }
            val previous = navController.previousBackStackEntry
            if (previous == null) {
                // The page that just left was the last sub-page: this swipe leaves Settings, as a swipe on Main does.
                try {
                    events.collect { }
                } finally {
                    instantPop = false
                    // The page that left handed this swipe the high refresh rate; cancelled, nothing else gives it back.
                    com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(context.findActivity())
                }
                handleExitBack()
                return@PredictiveBackHandler
            }
            val act = context.findActivity()
            com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
            var started = false
            try {
                events.collect { event ->
                    if (!started) {
                        screenCorner = com.pixel.intelligentsearch.core.ui.screenCornerRadius(view)
                        val picture = pageSnapshots[previous.id]
                        // The picture has its page's glow in it; without one, the glow is drawn live in its place.
                        val widgetPage = previous.destination.hasRoute<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization>()
                        enteringGlow = if (picture != null || widgetPage) 0f else 1f
                        enteringGlowTime = picture?.glowTime
                        nativeBack.begin(event, picture?.image)
                        started = true
                    } else {
                        nativeBack.track(event)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    if (nativeBack.active) nativeBack.cancel()
                    enteringGlowTime = null
                    instantPop = false
                    com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(act)
                }
                throw e
            }
            if (!started) {
                instantPop = false
                navController.popBackStack()
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(act)
                return@PredictiveBackHandler
            }
            // Let go: the page leaves in a job of its own, as Android cancels this gesture's job when the next swipe
            // starts, and cancelled mid-animation the page stayed. The next swipe finishes it instead (above).
            val closing = navController.currentBackStackEntry
            val leaving = LeavingPage(closing) {
                // Unless the page has already gone another way meanwhile (the top bar's back arrow).
                if (navController.currentBackStackEntry == closing) {
                    instantPop = true
                    navController.popBackStack()
                }
            }
            leavingPage.value = leaving
            leaving.job = navScope.launch {
                try {
                    kotlinx.coroutines.coroutineScope {
                        val settling = launch { nativeBack.commit() }
                        // The page that leaves is out of sight within the first fifth of the animation. Then it leaves
                        // the back stack, and the real page underneath takes its picture's place and settles the rest
                        // of the way itself, taking taps at once instead of after the whole animation.
                        snapshotFlow { nativeBack.closingAlpha() <= 0f }.first { it }
                        leaving.leave()
                        awaitLeft(closing)
                        carryGlowOnFromPicture()
                        nativeBack.goLive()
                        settling.join()
                    }
                    nativeBack.end()
                } finally {
                    // Finished early by the next swipe, that swipe has the pages and the refresh rate now.
                    if (leavingPage.value === leaving) {
                        leavingPage.value = null
                        com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(act)
                    }
                }
            }
        }
        }
    }
}
