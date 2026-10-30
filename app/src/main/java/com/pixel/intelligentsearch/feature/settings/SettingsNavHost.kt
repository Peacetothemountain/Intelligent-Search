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
        val pageSnapshots = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }
        val livePage = androidx.compose.ui.graphics.rememberGraphicsLayer()
        val navScope = rememberCoroutineScope()
        // Set while a page leaves at the end of the back animation, which has already moved it: no transition then.
        var instantPop by remember { mutableStateOf(false) }

        val onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = { route ->
            navScope.launch {
                val leaving = navController.currentBackStackEntry
                val snapshot = runCatching { livePage.toImageBitmap() }.getOrNull()
                if (leaving != null && snapshot != null) pageSnapshots[leaving.id] = snapshot
                navController.navigate(route) {
                    launchSingleTop = true
                }
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

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        // A page back on top is shown live again: its picture is taken anew when another page next opens over it.
        LaunchedEffect(currentBackStackEntry) {
            currentBackStackEntry?.id?.let { pageSnapshots.remove(it) }
        }
        // Reading the current entry makes the shell recompose on every navigation, so the exit gesture below is
        // switched on and off as the user moves between Main and the sub-pages.
        val hasSubScreensInNavHost = currentBackStackEntry.let { navController.previousBackStackEntry != null }
        val onWidgetPage = currentBackStackEntry?.destination?.hasRoute<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization>() == true
        val pageGlowStrength = androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (onWidgetPage) 0f else 1f,
            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "page_glow"
        )

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onSizeChanged {
                    nativeBack.size = it
                    nativeBack.marginPx = with(density) { com.pixel.intelligentsearch.core.ui.NativeBackMotion.MARGIN_DP.dp.toPx() }
                    nativeBack.offsetPx = with(density) { com.pixel.intelligentsearch.core.ui.NativeBackMotion.ENTERING_OFFSET_DP.dp.toPx() }
                }
        ) {
        if (nativeBack.active) {
            // The page underneath, as it was last seen, under the scrim.
            Box(
                Modifier
                    .fillMaxSize()
                    .backFrame(frame = { nativeBack.enteringFrame() }, cornerPx = { screenCorner })
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
                    frame = { if (nativeBack.active) nativeBack.closingFrame() else null },
                    cornerPx = { screenCorner },
                    alpha = { nativeBack.closingAlpha() }
                )
                // The page as drawn, kept so it can be shown underneath once another page opens over it.
                .drawWithContent {
                    livePage.record { this@drawWithContent.drawContent() }
                    drawLayer(livePage)
                }
                .background(MaterialTheme.colorScheme.background)
                // Every page but Widget Customization (whose previews carry their own colours) has the glow behind
                // its title; it fades out as that page comes in and back as it leaves.
                .pageGlow { pageGlowStrength.value }
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

        // Back between pages, with Android's own animation. Registered after the NavHost's own handler, so it takes
        // the gesture first; with animations removed the NavHost's handler plays its plain transition instead.
        androidx.activity.compose.PredictiveBackHandler(enabled = hasSubScreensInNavHost && animationsOn) { events ->
            val previous = navController.previousBackStackEntry ?: return@PredictiveBackHandler
            val act = context.findActivity()
            com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
            try {
                var started = false
                events.collect { event ->
                    if (!started) {
                        screenCorner = com.pixel.intelligentsearch.core.ui.screenCornerRadius(view)
                        nativeBack.begin(event, pageSnapshots[previous.id])
                        started = true
                    } else {
                        nativeBack.track(event)
                    }
                }
                if (started) nativeBack.commit()
                instantPop = started
                navController.popBackStack()
                if (started) {
                    // Keep the picture until the real page has taken its place, then hand over.
                    snapshotFlow { navController.currentBackStackEntry?.id }.first { it == previous.id }
                    withFrameNanos { }
                    withFrameNanos { }
                    nativeBack.end()
                }
                instantPop = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    if (nativeBack.active) nativeBack.cancel()
                    instantPop = false
                }
                throw e
            } finally {
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.restoreDefaultRefreshRate(act)
            }
        }
        }
    }
}
