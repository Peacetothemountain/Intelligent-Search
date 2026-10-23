package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.util.findActivity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Activity
import android.content.Context
import android.os.Build
import android.content.Intent
import android.content.SharedPreferences
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.animation.*
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.ui.materialYouMeshGradientBackground
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

        val onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = { route ->
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

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        // Reading the current entry makes the shell recompose on every navigation, so the exit gesture below is
        // switched on and off as the user moves between Main and the sub-pages.
        val hasSubScreensInNavHost = currentBackStackEntry.let { navController.previousBackStackEntry != null }

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
                    com.pixel.intelligentsearch.core.ui.WindowFramePacing.setNormalRefreshRateCategory(act)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .materialYouMeshGradientBackground(alpha = 0.08f)
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
                        slideInHorizontally(
                            initialOffsetX = { -(it * 0.10f).toInt() },
                            animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 240, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
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
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchSources> { SearchSourcesScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior> { SearchBehaviorScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.NicknamesTriggers> { NicknamesTriggersScreen(onBack) }
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
                    stepsInfo = mapOf(
                        3 to TutorialStepInfo("Settings & Features", "Here you can personalize your experience by customizing your widget and search controls.", Alignment.Center, requireButtonPress = true, showArrow = false),
                        4 to TutorialStepInfo("Make it Yours", "Explore all the categories below to fine-tune Intelligent Search perfectly to your workflow. You're all set!", Alignment.Center, requireButtonPress = true, showArrow = false)
                    ),
                    onComplete = { showTutorial = false }
                )
            }
        }
    }
}
