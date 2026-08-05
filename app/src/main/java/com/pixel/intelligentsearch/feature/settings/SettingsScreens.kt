package com.pixel.intelligentsearch.feature.settings
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.CornerRounding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchTileService
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.search.getAppName
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Application
import android.content.Context

import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import android.content.Intent
import android.content.SharedPreferences
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.app.PendingIntent
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.net.Uri
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.draw.*
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.zIndex

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.pixel.intelligentsearch.core.data.SettingsManager

// Allows the animation state to persist seamlessly across all settings pages!
val LocalMorphEngine = staticCompositionLocalOf<MorphAnimationEngine> { error("No engine provided") }

val LocalSettingsViewModel = staticCompositionLocalOf<SettingsViewModel?> {
    null
}

val LocalSettingsState = staticCompositionLocalOf<com.pixel.intelligentsearch.core.data.IntelligentSearchSettings?> {
    null
}

val LocalAnimationTime = staticCompositionLocalOf<Long> { 0L }

// --- State Helpers ---
@SuppressLint("UnrememberedMutableState")
@Composable
fun rememberBooleanPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Boolean,
    onChanged: () -> Unit = {}
): MutableState<Boolean> {
    val viewModel = LocalSettingsViewModel.current
    val settingsState = LocalSettingsState.current
    
    val datastoreKey = when (key) {
        "search.apps" -> SettingsManager.SEARCH_APPS
        "search.contacts" -> SettingsManager.SEARCH_CONTACTS
        "search.files" -> SettingsManager.SEARCH_FILES
        "search.web" -> SettingsManager.SEARCH_WEB
        "search.calculator" -> SettingsManager.SEARCH_CALCULATOR
        "search.calendar" -> SettingsManager.SEARCH_CALENDAR
        "search.shortcuts" -> SettingsManager.SEARCH_SHORTCUTS
        "search.background.show.wall" -> SettingsManager.SHOW_WALLPAPER
        "app_animations" -> SettingsManager.APP_ANIMATIONS
        "settings.bottom.search" -> SettingsManager.BOTTOM_SEARCH
        "settings.bottom.search.result" -> SettingsManager.BOTTOM_SEARCH_RESULT
        "g_icon_enabled" -> SettingsManager.G_ICON_ENABLED
        "widget_show_voice" -> SettingsManager.WIDGET_SHOW_VOICE
        "widget_show_gemini" -> SettingsManager.WIDGET_SHOW_GEMINI
        "quick_search_youtube" -> SettingsManager.QUICK_SEARCH_YOUTUBE
        "quick_search_wikipedia" -> SettingsManager.QUICK_SEARCH_WIKIPEDIA
        "quick_search_play_store" -> SettingsManager.QUICK_SEARCH_PLAY_STORE
        "quick_search_maps" -> SettingsManager.QUICK_SEARCH_MAPS
        "app_quick_launch" -> SettingsManager.APP_QUICK_LAUNCH
        "contact_direct_call" -> SettingsManager.CONTACT_DIRECT_CALL
        "shortcut.inline" -> SettingsManager.SHORTCUT_INLINE
        "app.fuzzy.search" -> SettingsManager.APP_FUZZY_SEARCH
        "quick.search.horizontal" -> SettingsManager.QUICK_SEARCH_HORIZONTAL
        "search.files.hidden.files" -> SettingsManager.FILES_HIDDEN_FILES
        "search.files.thumbnails" -> SettingsManager.FILES_THUMBNAILS
        "tutorial_completed" -> SettingsManager.TUTORIAL_COMPLETED
        "force_tutorial" -> SettingsManager.FORCE_TUTORIAL
        "context_aware_quick_apps" -> SettingsManager.CONTEXT_AWARE_QUICK_APPS
        "smart_clipboard_suggestions" -> SettingsManager.SMART_CLIPBOARD_SUGGESTIONS
        else -> null
    }

    val currentValue = when (key) {
        "search.apps" -> settingsState?.searchApps ?: prefs.getBoolean(key, defaultValue)
        "search.contacts" -> settingsState?.searchContacts ?: prefs.getBoolean(key, defaultValue)
        "search.files" -> settingsState?.searchFiles ?: prefs.getBoolean(key, defaultValue)
        "search.web" -> settingsState?.searchWeb ?: prefs.getBoolean(key, defaultValue)
        "search.calculator" -> settingsState?.searchCalculator ?: prefs.getBoolean(key, defaultValue)
        "search.calendar" -> settingsState?.searchCalendar ?: prefs.getBoolean(key, defaultValue)
        "search.shortcuts" -> settingsState?.searchShortcuts ?: prefs.getBoolean(key, defaultValue)
        "search.background.show.wall" -> settingsState?.showWallpaper ?: prefs.getBoolean(key, defaultValue)
        "app_animations" -> settingsState?.appAnimations ?: prefs.getBoolean(key, defaultValue)
        "settings.bottom.search" -> settingsState?.bottomSearch ?: prefs.getBoolean(key, defaultValue)
        "settings.bottom.search.result" -> settingsState?.bottomSearchResult ?: prefs.getBoolean(key, defaultValue)
        "g_icon_enabled" -> settingsState?.gIconEnabled ?: prefs.getBoolean(key, defaultValue)
        "widget_show_voice" -> settingsState?.widgetShowVoice ?: prefs.getBoolean(key, defaultValue)
        "widget_show_gemini" -> settingsState?.widgetShowGemini ?: prefs.getBoolean(key, defaultValue)
        "quick_search_youtube" -> settingsState?.quickSearchYoutube ?: prefs.getBoolean(key, defaultValue)
        "quick_search_wikipedia" -> settingsState?.quickSearchWikipedia ?: prefs.getBoolean(key, defaultValue)
        "quick_search_play_store" -> settingsState?.quickSearchPlayStore ?: prefs.getBoolean(key, defaultValue)
        "quick_search_maps" -> settingsState?.quickSearchMaps ?: prefs.getBoolean(key, defaultValue)
        "app_quick_launch" -> settingsState?.appQuickLaunch ?: prefs.getBoolean(key, defaultValue)
        "contact_direct_call" -> settingsState?.contactDirectCall ?: prefs.getBoolean(key, defaultValue)
        "shortcut.inline" -> settingsState?.shortcutInline ?: prefs.getBoolean(key, defaultValue)
        "app.fuzzy.search" -> settingsState?.appFuzzySearch ?: prefs.getBoolean(key, defaultValue)
        "quick.search.horizontal" -> settingsState?.quickSearchHorizontal ?: prefs.getBoolean(key, defaultValue)
        "search.files.hidden.files" -> settingsState?.filesHiddenFiles ?: prefs.getBoolean(key, defaultValue)
        "search.files.thumbnails" -> settingsState?.filesThumbnails ?: prefs.getBoolean(key, defaultValue)
        "tutorial_completed" -> settingsState?.tutorialCompleted ?: prefs.getBoolean(key, defaultValue)
        "force_tutorial" -> settingsState?.forceTutorial ?: prefs.getBoolean(key, defaultValue)
        "context_aware_quick_apps" -> settingsState?.contextAwareQuickApps ?: prefs.getBoolean(key, defaultValue)
        "smart_clipboard_suggestions" -> settingsState?.smartClipboardSuggestions ?: prefs.getBoolean(key, defaultValue)
        else -> prefs.getBoolean(key, defaultValue)
    }

    val state = remember { mutableStateOf(currentValue) }
    LaunchedEffect(currentValue) {
        state.value = currentValue
    }

    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                state.value = sharedPreferences.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<Boolean> {
        override var value: Boolean
            get() = state.value
            set(v) {
                state.value = v
                if (datastoreKey != null && viewModel != null) {
                    viewModel.updateSetting(datastoreKey, v)
                }
                prefs.edit().putBoolean(key, v).apply()
                onChanged()
            }
        override operator fun component1() = value
        override operator fun component2(): (Boolean) -> Unit = { value = it }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun rememberIntPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Int,
    onChanged: () -> Unit = {}
): MutableState<Int> {
    val viewModel = LocalSettingsViewModel.current
    val settingsState = LocalSettingsState.current
    
    val datastoreKey = when (key) {
        "search.background.blur" -> SettingsManager.BACKGROUND_BLUR
        "search.background.transparency" -> SettingsManager.BACKGROUND_TRANSPARENCY
        "search.pill.opacity" -> SettingsManager.PILL_OPACITY
        "tutorial_step" -> SettingsManager.TUTORIAL_STEP
        "shortcut_results_count" -> SettingsManager.SHORTCUT_RESULTS_COUNT
        else -> null
    }

    val currentValue = when (key) {
        "search.background.blur" -> settingsState?.backgroundBlur ?: prefs.getInt(key, defaultValue)
        "search.background.transparency" -> settingsState?.backgroundTransparency ?: prefs.getInt(key, defaultValue)
        "search.pill.opacity" -> settingsState?.pillOpacity ?: prefs.getInt(key, defaultValue)
        "tutorial_step" -> settingsState?.tutorialStep ?: prefs.getInt(key, defaultValue)
        "shortcut_results_count" -> settingsState?.shortcutResultsCount ?: prefs.getInt(key, defaultValue)
        else -> prefs.getInt(key, defaultValue)
    }

    val state = remember { mutableIntStateOf(currentValue) }
    LaunchedEffect(currentValue) {
        state.value = currentValue
    }

    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                state.value = sharedPreferences.getInt(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<Int> {
        override var value: Int
            get() = state.value
            set(v) {
                state.value = v
                if (datastoreKey != null && viewModel != null) {
                    viewModel.updateSetting(datastoreKey, v)
                }
                prefs.edit().putInt(key, v).apply()
                onChanged()
            }
        override operator fun component1() = value
        override operator fun component2(): (Int) -> Unit = { value = it }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun rememberStringPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: String
): MutableState<String> {
    val viewModel = LocalSettingsViewModel.current
    val settingsState by (viewModel?.settingsState ?: kotlinx.coroutines.flow.MutableStateFlow(null))
        .collectAsStateWithLifecycle()
    
    val datastoreKey = when (key) {
        "night.mode" -> SettingsManager.THEME
        "search.engine" -> SettingsManager.SEARCH_ENGINE
        "custom_search_engine_url" -> SettingsManager.CUSTOM_SEARCH_ENGINE_URL
        "widget.theme.style" -> SettingsManager.WIDGET_THEME_STYLE
        "search.pills" -> SettingsManager.SEARCH_PILLS
        else -> null
    }

    val currentValue = when (key) {
        "night.mode" -> settingsState?.theme ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "search.engine" -> settingsState?.searchEngine ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "custom_search_engine_url" -> settingsState?.customSearchEngineUrl ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "widget.theme.style" -> settingsState?.widgetThemeStyle ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "search.pills" -> settingsState?.searchPills ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        else -> prefs.getString(key, defaultValue) ?: defaultValue
    }

    val state = remember { mutableStateOf(currentValue) }
    LaunchedEffect(currentValue) {
        state.value = currentValue
    }

    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                state.value = sharedPreferences.getString(key, defaultValue) ?: defaultValue
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<String> {
        override var value: String
            get() = state.value
            set(v) {
                state.value = v
                if (datastoreKey != null && viewModel != null) {
                    viewModel.updateSetting(datastoreKey, v)
                }
                prefs.edit().putString(key, v).apply()
            }
        override operator fun component1() = value
        override operator fun component2(): (String) -> Unit = { value = it }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreensHub(
    initialScreen: String,
    prefs: SharedPreferences,
    onBackToLauncher: () -> Unit,
    context: Context
) {
    val viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    
    val animationTime by androidx.compose.runtime.produceState(0L) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { value = it }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val morphEngine = remember { MorphAnimationEngine(coroutineScope) }
    
    CompositionLocalProvider(
        LocalMorphEngine provides morphEngine,
        LocalSettingsViewModel provides viewModel,
        LocalSettingsState provides settingsState,
        LocalAnimationTime provides animationTime
    ) {
        val navController = androidx.navigation.compose.rememberNavController()
        
        val exoPlayer = androidx.compose.runtime.remember {
            val uri = androidx.media3.datasource.RawResourceDataSource.buildRawResourceUri(com.pixel.intelligentsearch.R.raw.bugdroid_video)
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                setMediaSource(mediaSource)
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    exoPlayer.play()
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                    exoPlayer.pause()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                exoPlayer.release()
            }
        }

        val onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = { route ->
            navController.navigate(route)
        }
        val onBack: () -> Unit = {
            if (!navController.popBackStack()) {
                onBackToLauncher()
            }
        }

        val startRoute: com.pixel.intelligentsearch.core.navigation.Route = when (initialScreen) {
            "main" -> com.pixel.intelligentsearch.core.navigation.Route.Main
            "appearance" -> com.pixel.intelligentsearch.core.navigation.Route.Appearance
            "search_sources" -> com.pixel.intelligentsearch.core.navigation.Route.SearchSources
            "search_behavior" -> com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior
            "launch_portal" -> com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal
            "app_search" -> com.pixel.intelligentsearch.core.navigation.Route.AppSearch
            "search_pills" -> com.pixel.intelligentsearch.core.navigation.Route.SearchPills
            "web_search" -> com.pixel.intelligentsearch.core.navigation.Route.WebSearch
            "contact_search" -> com.pixel.intelligentsearch.core.navigation.Route.ContactSearch
            "file_search" -> com.pixel.intelligentsearch.core.navigation.Route.FileSearch
            "widget" -> com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization
            "manage_hidden_apps" -> com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps
            "debug" -> com.pixel.intelligentsearch.core.navigation.Route.Debug
            else -> com.pixel.intelligentsearch.core.navigation.Route.Main
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            var showTutorial by remember { mutableStateOf(TutorialManager.isTutorialActive(prefs)) }
            val tutorialCompleted by rememberBooleanPreference(prefs, "tutorial_completed", false) {}
            
            // Reactively sync tutorial visibility with the completed pref so it stays dismissed
            // permanently after finishing, but re-activates if force-tutorial resets it.
            LaunchedEffect(tutorialCompleted) {
                if (tutorialCompleted) {
                    showTutorial = false
                } else if (TutorialManager.isTutorialActive(prefs)) {
                    showTutorial = true
                }
            }

            val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showTutorial) Modifier.blur(24.dp) else Modifier)
            ) {
                
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }) },
                    exitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it / 3 }) },
                    popEnterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it / 3 }) },
                    popExitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }) }
                ) {
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Main> { MainSettingsScreen(prefs, onNavigate, onBack, context, exoPlayer, showTutorial) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Appearance> { AppearanceScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchSources> { SearchSourcesScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior> { SearchBehaviorScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal> { LaunchPortalScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.AppSearch> { AppSearchScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchPills> { SearchPillsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WebSearch> { WebSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ContactSearch> { ContactSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.FileSearch> { FileSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization> { WidgetSettingsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps> { ManageHiddenAppsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Debug> {
                        if (prefs.getBoolean("debug_unlocked", false)) {
                            DebugScreen(
                                prefs = prefs,
                                onBack = onBack,
                                onDisableDebug = {
                                    prefs.edit().putBoolean("debug_unlocked", false).apply()
                                    if (!navController.popBackStack()) {
                                        navController.navigate("main") {
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
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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
                        3 to TutorialStepInfo("Settings & Features", "Here you can personalize your experience. We recently cleaned up this menu to organize all your Search Sources into one convenient page.", Alignment.Center, requireButtonPress = true, showArrow = false),
                        4 to TutorialStepInfo("Make it Yours", "Explore all the categories below to fine-tune Intelligent Search perfectly to your workflow. You're all set!", Alignment.Center, requireButtonPress = true, showArrow = false)
                    ),
                    onComplete = { showTutorial = false }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// DEBUG SETTINGS
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(prefs: SharedPreferences, onBack: () -> Unit, onDisableDebug: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "SECRET DEBUG MENU",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )
            
            var forceTutorial by rememberBooleanPreference(prefs, "force_tutorial", false)
            SettingsRowToggle(
                title = "Force Tutorial Mode",
                subtitle = "When checked, the tutorial will play every time the app opens",
                icon = Icons.Default.Warning,
                isChecked = forceTutorial,
                onCheckedChange = { forceTutorial = it }
            )
            
            SettingsRow(
                title = "Reset Tutorial Progress",
                subtitle = "Mark tutorial as incomplete and restart step guide",
                icon = Icons.Default.Refresh,
                onClick = {
                    prefs.edit()
                        .putInt("tutorial_step", 0)
                        .putBoolean("tutorial_completed", false)
                        .apply()
                },
                showDivider = true
            )
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            
            SettingsRow(
                title = "Clear Search History Cache",
                subtitle = "Reset and clear all saved recent queries",
                icon = Icons.Default.Delete,
                onClick = {
                    scope.launch {
                        com.pixel.intelligentsearch.core.data.IntelligentSearchDatabase.getDatabase(context).historyDao().clearHistory()
                    }
                    android.widget.Toast.makeText(context, "Search history cleared", android.widget.Toast.LENGTH_SHORT).show()
                },
                showDivider = true
            )
            
            var simulateLatency by rememberBooleanPreference(prefs, "debug.simulate_latency", false)
            SettingsRowToggle(
                title = "Simulate Web Latency",
                subtitle = "Adds a 2-second artificial delay to search suggestions",
                icon = Icons.Default.HourglassEmpty,
                isChecked = simulateLatency,
                onCheckedChange = { simulateLatency = it },
                showDivider = true
            )
            
            var mockLargeDataset by rememberBooleanPreference(prefs, "debug.mock_large_dataset", false)
            SettingsRowToggle(
                title = "Mock Large Dataset",
                subtitle = "Injects 50 mock contacts and files into search lists",
                icon = Icons.Default.Layers,
                isChecked = mockLargeDataset,
                onCheckedChange = { mockLargeDataset = it },
                showDivider = true
            )
            
            var verboseLogging by rememberBooleanPreference(prefs, "debug.verbose_logging", false)
            SettingsRowToggle(
                title = "Enable Verbose Logging",
                subtitle = "Print query logs and load frame times in Logcat",
                icon = Icons.Default.Code,
                isChecked = verboseLogging,
                onCheckedChange = { verboseLogging = it },
                showDivider = true
            )
            

            var showPerfStats by rememberBooleanPreference(prefs, "debug.show_perf_stats", false)
            SettingsRowToggle(
                title = "Show Performance HUD",
                subtitle = "Render search latency and results count at the top of overlay",
                icon = Icons.Default.Speed,
                isChecked = showPerfStats,
                onCheckedChange = { showPerfStats = it },
                showDivider = true
            )
            
            var mockZeroState by rememberBooleanPreference(prefs, "debug.mock_zero_state", false)
            SettingsRowToggle(
                title = "Mock Trending Queries",
                subtitle = "Force mock trending query topics when the search bar is empty",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                isChecked = mockZeroState,
                onCheckedChange = { mockZeroState = it },
                showDivider = true
            )
            
            var forceSearchError by rememberBooleanPreference(prefs, "debug.force_search_error", false)
            SettingsRowToggle(
                title = "Force Search API Error",
                subtitle = "Simulate suggestion fetch failure and display error banner",
                icon = Icons.Default.BugReport,
                isChecked = forceSearchError,
                onCheckedChange = { forceSearchError = it },
                showDivider = true
            )
            
            SettingsRow(
                title = "Disable Debug Mode",
                subtitle = "Turn off developer settings and exit",
                icon = Icons.Default.Close,
                onClick = onDisableDebug,
                showDivider = false
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// MAIN SETTINGS
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    prefs: SharedPreferences,
    onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit,
    onBack: () -> Unit,
    context: Context,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer,
    showTutorial: Boolean = false
) {
    Scaffold(
        containerColor = if (showTutorial) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (showTutorial) Color.Transparent else MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        val isTutorialActive = TutorialManager.isTutorialActive(prefs)
        
                Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
                .graphicsLayer { alpha = if (showTutorial) 0f else 1f },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                SettingsRow(
                    title = "Appearance",
                    subtitle = "Theme, Wallpaper, Material Design.",
                    icon = Icons.Default.Palette,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Appearance) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Shortcuts",
                    subtitle = "Apps, Contacts, Files, Etc.",
                    icon = Icons.AutoMirrored.Filled.ManageSearch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchSources) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Behavior",
                    subtitle = "Customize Search Overlay Display.",
                    icon = Icons.Default.Settings,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Widget",
                    subtitle = "Customize widget shape and color",
                    icon = Icons.Default.Widgets,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Launch Portal",
                    subtitle = "Quick Search Tile and App Shortcuts",
                    icon = Icons.AutoMirrored.Filled.Launch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal) },
                    showDivider = false,
                )
            }

            SettingsCard {
                SettingsRow(
                    title = "Default Digital Assistant",
                    subtitle = "Manage Android assistant settings",
                    icon = Icons.Default.Assistant,
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Fallback if the specific intent is not available
                            val fallbackIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Google My Activity",
                    subtitle = "View and manage your Google activity",
                    icon = Icons.Default.History,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    showDivider = true,

                )
                val isDebugUnlocked by rememberBooleanPreference(prefs, "debug_unlocked", false)
                val searchEngine = prefs.getString("search.engine", "Google") ?: "Google"
                val browserHistorySubtitle = when (searchEngine) {
                    "Google" -> "View your Chrome/Google history"
                    "Bing" -> "View your Bing history"
                    "DuckDuckGo" -> "Open your DuckDuckGo App history"
                    else -> "View your $searchEngine history"
                }
                SettingsRow(
                    title = "Browser History",
                    subtitle = browserHistorySubtitle,
                    icon = Icons.Default.HistoryEdu,
                    onClick = {
                        val intent = when (searchEngine) {
                            "Google" -> {
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity?product=6")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            "Bing" -> {
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.bing.com/profile/history")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            "DuckDuckGo" -> {
                                val ddgIntent = context.packageManager.getLaunchIntentForPackage("com.duckduckgo.mobile.android")
                                ddgIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                            }
                            else -> {
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity?product=6")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    showDivider = isDebugUnlocked,

                )
                
                if (isDebugUnlocked) {
                    SettingsRow(
                        title = "Debug",
                        subtitle = "Developer tools and experiments",
                        icon = Icons.Default.BugReport,
                        onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Debug) },
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val shaderSrc = """
                    uniform shader content;
                    vec4 main(vec2 coords) {
                        vec4 color = content.eval(coords);
                        float maxVal = max(color.r, max(color.g, color.b));
                        if (maxVal < 0.16) {
                            return vec4(0.0, 0.0, 0.0, 0.0);
                        }
                        if (maxVal < 0.28) {
                            float t = (maxVal - 0.16) / 0.12;
                            return color * t;
                        }
                        return color;
                    }
                """.trimIndent()

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.view.TextureView(ctx).apply {
                            val adjustAspectRatio: (android.view.TextureView) -> Unit = { tv ->
                                val vw = tv.width
                                val vh = tv.height
                                if (vw > 0 && vh > 0) {
                                    val matrix = android.graphics.Matrix()
                                    val videoAspect = 1280f / 720f
                                    val viewAspect = vw.toFloat() / vh.toFloat()
                                    var scaleX = 1f
                                    var scaleY = 1f
                                    if (viewAspect > videoAspect) {
                                        scaleY = (vw.toFloat() / 1280f * 720f) / vh.toFloat()
                                    } else {
                                        scaleX = (vh.toFloat() / 720f * 1280f) / vw.toFloat()
                                    }
                                    
                                    // Scale down / Zoom out (0.70f scale factor) to make the bugdroid wider and show the entire body and hands
                                    scaleX *= 0.70f
                                    scaleY *= 0.70f
                                    
                                    matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
                                    tv.setTransform(matrix)
                                }
                            }

                            var currentSurface: android.view.Surface? = null

                            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    val surface = android.view.Surface(surfaceTexture)
                                    currentSurface = surface
                                    exoPlayer.setVideoSurface(surface)
                                    adjustAspectRatio(this@apply)
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    adjustAspectRatio(this@apply)
                                }

                                override fun onSurfaceTextureDestroyed(
                                    surfaceTexture: android.graphics.SurfaceTexture
                                ): Boolean {
                                    currentSurface?.let {
                                        exoPlayer.clearVideoSurface(it)
                                        it.release()
                                    }
                                    currentSurface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(
                                    surfaceTexture: android.graphics.SurfaceTexture
                                ) {}
                            }
                        }
                    },
                    modifier = Modifier
                        .height(220.dp)
                        .width(200.dp) // Wider view container to fit the waving arms
                        .graphicsLayer {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                val shader = android.graphics.RuntimeShader(shaderSrc)
                                val frameworkEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                renderEffect = frameworkEffect.asComposeRenderEffect()
                            }
                        }
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thank you for using Intelligent Search.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(
                        text = "If you need support please email me at:\nsupport.nbdesigns@gmail.com",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.bouncyClickable {
                            uriHandler.openUri("mailto:support.nbdesigns@gmail.com")
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -----------------------------------------------------------------------------------------
// APPEARANCE SCREEN
// -----------------------------------------------------------------------------------------
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var iconPacks by remember { mutableStateOf(listOf("System Default")) }
    
    LaunchedEffect(Unit) {
        // Icon packs loading removed
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsCard {
                var enableSearchOverlay by rememberBooleanPreference(prefs, "search_overlay_enabled", true) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Enable Search Overlay Page",
                    subtitle = "If off, widget opens native Google Search app directly.",
                    icon = Icons.Default.Layers,
                    isChecked = enableSearchOverlay,
                    onCheckedChange = { enableSearchOverlay = it },
                    showDivider = true
                )

                var morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}
                SettingsRowToggle(
                    title = "Morph Animation",
                    subtitle = "Dynamic background animation",
                    icon = Icons.Default.AutoAwesome,
                    isChecked = morphAnimationEnabled,
                    onCheckedChange = { morphAnimationEnabled = it },
                    showDivider = true
                )


                
                var showWall by rememberBooleanPreference(prefs, "search.background.show.wall", true) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Show Wallpaper",
                    subtitle = "Show device wallpaper in search background",
                    icon = Icons.Default.Wallpaper,
                    isChecked = showWall,
                    onCheckedChange = { showWall = it },
                    showDivider = true
                )
                
                var blur by rememberIntPreference(prefs, "search.background.blur", 30) { updateWidgets(context) }
                var transparency by rememberIntPreference(prefs, "search.background.transparency", 30) { updateWidgets(context) }
                var pillOpacity by rememberIntPreference(prefs, "search.pill.opacity", 40) { updateWidgets(context) }

                SettingsSliderRow(
                    title = "Background Blur",
                    value = blur.toFloat(),
                    onValueChange = { blur = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Default.BlurOn,
                    showDivider = true
                )
                
                SettingsSliderRow(
                    title = "Background Transparency",
                    value = transparency.toFloat(),
                    onValueChange = { transparency = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Default.Opacity,
                    showDivider = true
                )
                
                SettingsSliderRow(
                    title = "Search Pill Opacity",
                    value = pillOpacity.toFloat(),
                    onValueChange = { pillOpacity = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Default.Visibility,
                    showDivider = false
                )
            }
            
        
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) 
                    .clipToBounds() 
            ) {
                if (morphAnimationEnabled) {
                    MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
}

// -----------------------------------------------------------------------------------------
// SEARCH SOURCES SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourcesScreen(prefs: SharedPreferences, onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Search Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsCard {
                var searchApps by rememberBooleanPreference(prefs, "search.apps", false)
                SettingsRowToggle(
                    title = "Apps",
                    subtitle = "Search installed applications",
                    icon = Icons.Default.Apps,
                    isChecked = searchApps,
                    onCheckedChange = { searchApps = it },
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.AppSearch) },
                    showDivider = true,

                )
                var searchWeb by rememberBooleanPreference(prefs, "search.web", false)
                SettingsRowToggle(
                    title = "Web",
                    subtitle = "Web search suggestions",
                    icon = Icons.Default.Language,
                    isChecked = searchWeb,
                    onCheckedChange = { searchWeb = it },
                    showDivider = true
                )
                var searchContacts by rememberBooleanPreference(prefs, "search.contacts", false)
                val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    if (results.values.all { it }) {
                        searchContacts = true
                    } else {
                        Toast.makeText(context, "Permission denied. Please enable in Settings.", Toast.LENGTH_LONG).show()
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
                SettingsRowToggle(
                    title = "Contacts",
                    subtitle = "Search contacts",
                    icon = Icons.Default.Contacts,
                    isChecked = searchContacts,
                    onCheckedChange = { isChecked -> 
                        if (isChecked) {
                            if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                searchContacts = true
                            } else {
                                contactsPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
                            }
                        } else {
                            searchContacts = false
                        }
                    },
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.ContactSearch) },
                    showDivider = true
                )
                
                var searchFiles by rememberBooleanPreference(prefs, "search.files", false)
                val filesPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    if (results.values.any { it }) {
                        searchFiles = true
                    } else {
                        Toast.makeText(context, "Permission denied. Please enable in Settings.", Toast.LENGTH_LONG).show()
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
                SettingsRowToggle(
                    title = "Files",
                    subtitle = "Search local files",
                    icon = Icons.Default.Folder,
                    isChecked = searchFiles,
                    onCheckedChange = { isChecked -> 
                        if (isChecked) {
                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO, android.Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            if (perms.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                                searchFiles = true
                            } else {
                                filesPermissionLauncher.launch(perms)
                            }
                        } else {
                            searchFiles = false
                        }
                    },
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.FileSearch) },
                    showDivider = true
                )
                var searchCalc by rememberBooleanPreference(prefs, "search.calculator", false)
                SettingsRowToggle(
                    title = "Calculator",
                    subtitle = "Solve math expressions",
                    icon = Icons.Default.Calculate,
                    isChecked = searchCalc,
                    onCheckedChange = { searchCalc = it },
                    showDivider = true
                )
                var searchCalendar by rememberBooleanPreference(prefs, "search.calendar", false)
                val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    if (results.values.all { it }) {
                        searchCalendar = true
                    } else {
                        Toast.makeText(context, "Permission denied. Please enable in Settings.", Toast.LENGTH_LONG).show()
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
                SettingsRowToggle(
                    title = "Calendar",
                    subtitle = "Show upcoming events",
                    icon = Icons.Default.Event,
                    isChecked = searchCalendar,
                    onCheckedChange = { isChecked -> 
                        if (isChecked) {
                            if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                searchCalendar = true
                            } else {
                                calendarPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CALENDAR))
                            }
                        } else {
                            searchCalendar = false
                        }
                    },
                    showDivider = false
                )
            }
        
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) 
                    .clipToBounds() 
            ) {
                if (morphAnimationEnabled) {
                    MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
}

// -----------------------------------------------------------------------------------------
// MANAGE HIDDEN APPS SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHiddenAppsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    var hiddenApps by remember { mutableStateOf(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()) }
    var installedApps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        installedApps = pm.queryIntentActivities(intent, 0).sortedBy { it.loadLabel(pm).toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Hidden Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
            ) {
                items(installedApps.size) { index ->
                    val resolveInfo = installedApps[index]
                    val packageName = resolveInfo.activityInfo.packageName
                    val label = resolveInfo.loadLabel(context.packageManager).toString()
                    val isHidden = hiddenApps.contains(packageName)
                    
                    var appIcon by remember(packageName) { mutableStateOf<Any>(Icons.Default.Apps) }
                    LaunchedEffect(packageName) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val drawable = resolveInfo.loadIcon(context.packageManager)
                                appIcon = drawable
                            } catch (e: Exception) { }
                        }
                    }
                    
                    SettingsRowToggle(
                        title = label,
                        subtitle = packageName,
                        icon = appIcon,
                        isChecked = isHidden,
                        onCheckedChange = { hide ->
                            val newSet = hiddenApps.toMutableSet()
                            if (hide) newSet.add(packageName) else newSet.remove(packageName)
                            hiddenApps = newSet
                            prefs.edit().putStringSet("hidden_apps", newSet).apply()
                            viewModel?.updateSetting(SettingsManager.HIDDEN_APPS, newSet)
                        },
                        showDivider = index < installedApps.size - 1
                    )
                }
                if (installedApps.isEmpty()) {
                    item {
                        Text(
                            "Loading apps...",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// APP SEARCH SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchScreen(prefs: SharedPreferences, onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & Shortcut Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("App Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            SettingsCard {
                SettingsRow(
                    title = "Application Search",
                    subtitle = "Customize quick launch apps in the search bar",
                    icon = Icons.Default.ViewCarousel,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchPills) },
                    showDivider = true
                )
                SettingsRow(
                    title = "Manage Hidden Apps",
                    subtitle = "Select apps to hide from search",
                    icon = Icons.Default.VisibilityOff,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps) },
                    showDivider = true
                )
                var fuzzySearch by rememberBooleanPreference(prefs, "app.fuzzy.search", false)
                SettingsRowToggle(
                    title = "Fuzzy Search",
                    subtitle = "Allow typos when searching for apps",
                    icon = Icons.Default.Spellcheck,
                    isChecked = fuzzySearch,
                    onCheckedChange = { fuzzySearch = it },
                    showDivider = true
                )
                var appAnimation by rememberBooleanPreference(prefs, "app.animation", true)
                SettingsRowToggle(
                    title = "App Animations",
                    subtitle = "Use animations when launching apps",
                    icon = Icons.Default.Animation,
                    isChecked = appAnimation,
                    onCheckedChange = { appAnimation = it },
                    showDivider = false
                )
            }

            Text("Include in search result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            SettingsCard {
                var searchShortcuts by rememberBooleanPreference(prefs, "search.shortcuts", false)
                SettingsRowToggle(
                    title = "Shortcuts",
                    subtitle = "Manage shortcuts for 20 apps",
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    isChecked = searchShortcuts,
                    onCheckedChange = { searchShortcuts = it },
                    showDivider = true
                )
                
                var recentShortcuts by rememberBooleanPreference(prefs, "shortcut.recent", true)
                SettingsRowToggle(
                    title = "Include recent shortcuts",
                    subtitle = "Show recently used shortcuts as suggestions",
                    icon = null,
                    isChecked = recentShortcuts,
                    onCheckedChange = { recentShortcuts = it },
                    showDivider = false
                )
            }
            
            Text("Results preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            SettingsCard {
                var searchPills by rememberStringPreference(prefs, "search.pills", "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files")
                var shortcutResultsCount by rememberIntPreference(prefs, "shortcut_results_count", 6)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Max shortcuts suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("$shortcutResultsCount", modifier = Modifier.padding(end = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Android17Slider(
                            value = shortcutResultsCount.toFloat(),
                            onValueChange = { newValue -> 
                                val newCount = newValue.toInt()
                                if (newCount < shortcutResultsCount) {
                                    val currentPills = searchPills.split(",").filter { it.isNotBlank() }
                                    if (currentPills.size > newCount) {
                                        searchPills = currentPills.take(newCount).joinToString(",")
                                    }
                                }
                                shortcutResultsCount = newCount
                            },
                            valueRange = 1f..20f,
                            steps = 18,
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
// Removed ShortcutSearchScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                var searchEngine by rememberStringPreference(prefs, "search.engine", "Google")
                SettingsDropdownRow(
                    title = "Primary Search App",
                    subtitle = searchEngine,
                    icon = Icons.Default.Search,
                    options = listOf("Google", "DuckDuckGo", "Bing", "Custom"),
                    selectedOption = searchEngine,
                    onOptionSelected = { searchEngine = it },
                    showDivider = searchEngine != "Custom"
                )
                if (searchEngine == "Custom") {
                    var customUrl by rememberStringPreference(prefs, "custom_search_engine_url", "https://duckduckgo.com/?q=%s")
                    androidx.compose.material3.OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Custom Search URL (use %s for query)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                var webSuggestions by rememberBooleanPreference(prefs, "search.web.suggestions", true)
                SettingsRowToggle(
                    title = "Web Suggestions",
                    subtitle = "Show search suggestions as you type",
                    icon = Icons.Default.ChatBubbleOutline,
                    isChecked = webSuggestions,
                    onCheckedChange = { webSuggestions = it },
                    showDivider = true
                )
                
                var webResultsCount by rememberIntPreference(prefs, "web_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Web Results: $webResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Android17Slider(
                        value = webResultsCount.toFloat(),
                        onValueChange = { webResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Contact selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                SettingsRow(
                    title = "Contact Menu",
                    subtitle = "Opens Contact Picker",
                    icon = Icons.Default.Contacts,
                    onClick = { launcher.launch(null) },
                    showDivider = true
                )
                var directCall by rememberBooleanPreference(prefs, "contact_direct_call", false)
                SettingsRowToggle(
                    title = "Direct Call",
                    subtitle = "Tap contact to call directly",
                    icon = Icons.Default.Call,
                    isChecked = directCall,
                    onCheckedChange = { directCall = it },
                    showDivider = true
                )
                
                var contactResultsCount by rememberIntPreference(prefs, "contact_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Contact Results: $contactResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Android17Slider(
                        value = contactResultsCount.toFloat(),
                        onValueChange = { contactResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Directory selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
            prefs.edit().putString("search.files.uri", uri.toString()).apply()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                SettingsRow(
                    title = "Select Indexing Directory",
                    subtitle = prefs.getString("search.files.uri", "None Selected") ?: "None Selected",
                    icon = Icons.Default.FolderOpen,
                    onClick = { launcher.launch(null) },
                    showDivider = true
                )
                var hiddenFiles by rememberBooleanPreference(prefs, "search.files.hidden.files", false)
                SettingsRowToggle(
                    title = "Show Hidden Files",
                    subtitle = "Include files starting with a dot",
                    icon = Icons.Default.Visibility,
                    isChecked = hiddenFiles,
                    onCheckedChange = { hiddenFiles = it },
                    showDivider = true
                )
                var thumbnails by rememberBooleanPreference(prefs, "search.files.thumbnails", true)
                SettingsRowToggle(
                    title = "Show Thumbnails",
                    subtitle = "Show image and video thumbnails",
                    icon = Icons.Default.Image,
                    isChecked = thumbnails,
                    onCheckedChange = { thumbnails = it },
                    showDivider = true
                )
                
                var fileResultsCount by rememberIntPreference(prefs, "file_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("File Results: $fileResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Android17Slider(
                        value = fileResultsCount.toFloat(),
                        onValueChange = { fileResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBehaviorScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Search Behavior", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsCard {
                var bottomSearch by rememberBooleanPreference(prefs, "settings.bottom.search", true)
                SettingsRowToggle(
                    title = "Bottom Searchbar",
                    subtitle = "Position search bar at bottom",
                    icon = Icons.Default.VerticalAlignBottom,
                    isChecked = bottomSearch,
                    onCheckedChange = { bottomSearch = it },
                    showDivider = true
                )
                var bottomResult by rememberBooleanPreference(prefs, "settings.bottom.search.result", false)
                SettingsRowToggle(
                    title = "Bottom Search Results",
                    subtitle = "Reverse list so results are at bottom",
                    icon = Icons.Default.AlignVerticalBottom,
                    isChecked = bottomResult,
                    onCheckedChange = { bottomResult = it },
                    showDivider = true
                )
                                var compactList by rememberBooleanPreference(prefs, "quick.search.horizontal", false)
                SettingsRowToggle(
                    title = "Quick App Panel",
                    subtitle = "Quickly launch recently opened applications.",
                    icon = Icons.Default.ViewCompact,
                    isChecked = compactList,
                    onCheckedChange = { compactList = it },
                    showDivider = true
                )
                var contextAwareApps by rememberBooleanPreference(prefs, "context_aware_quick_apps", false)
                SettingsRowToggle(
                    title = "Context-Aware Quick Apps",
                    subtitle = "Dynamic apps based on time of day",
                    icon = Icons.Default.AccessTime,
                    isChecked = contextAwareApps,
                    onCheckedChange = { contextAwareApps = it },
                    showDivider = true
                )
                var smartClipboard by rememberBooleanPreference(prefs, "smart_clipboard_suggestions", true)
                SettingsRowToggle(
                    title = "Smart Clipboard Suggestions",
                    subtitle = "Suggest actions based on copied text",
                    icon = Icons.Default.ContentPaste,
                    isChecked = smartClipboard,
                    onCheckedChange = { smartClipboard = it },
                    showDivider = false
                )
            }
        
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) 
                    .clipToBounds() 
            ) {
                if (morphAnimationEnabled) {
                    MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
}

// -----------------------------------------------------------------------------------------
// REUSABLE COMPONENTS
// -----------------------------------------------------------------------------------------
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    onLongClick: (() -> Unit)? = null,
    title: String,
    subtitle: String,
    icon: Any?,
    onClick: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(
                    onLongClick = onLongClick,
                    onClick = {
                    performClickHaptic(context)
                    onClick()
                })
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (icon) {
                is ImageVector -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                is android.graphics.drawable.Drawable -> {
                    Image(
                        bitmap = icon.toBitmap().asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.size(32.dp)
                    )
                }
                is androidx.compose.ui.graphics.ImageBitmap -> {
                    Image(
                        bitmap = icon,
                        contentDescription = title,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingsDropdownRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    showDivider: Boolean,
    optionIcons: Map<String, Int>? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { 
                    performClickHaptic(context)
                    expanded = true 
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconContent != null) {
                iconContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Dropdown",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            MaterialTheme(
                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(24.dp))
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            leadingIcon = if (optionIcons != null && optionIcons.containsKey(option)) {
                                {
                                    if (title == "Widget Action Icon" && option in listOf("Search", "Gemini", "Now Playing")) {
                                        ComposeActionIcon(
                                            iconType = option,
                                            modifier = Modifier.size(24.dp),
                                            primaryColor = MaterialTheme.colorScheme.primary,
                                            secondaryColor = MaterialTheme.colorScheme.secondary,
                                            tertiaryColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    } else {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = optionIcons[option]!!),
                                            contentDescription = option,
                                            modifier = Modifier.size(24.dp),
                                            tint = androidx.compose.ui.graphics.Color.Unspecified
                                        )
                                    }
                                }
                            } else null,
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingsRowToggle(
    onLongClick: (() -> Unit)? = null,
    title: String,
    subtitle: String,
    icon: Any? = null,
    customIcon: (@Composable () -> Unit)? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(
                    onLongClick = onLongClick,
                    onClick = {
                    performClickHaptic(context)
                    if (onClick != null) onClick() else onCheckedChange(!isChecked)
                })
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customIcon != null) {
                customIcon()
            } else {
                when (icon) {
                    is ImageVector -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is android.graphics.drawable.Drawable -> {
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = title,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    is androidx.compose.ui.graphics.ImageBitmap -> {
                        Image(
                            bitmap = icon,
                            contentDescription = title,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    performClickHaptic(context)
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Android17Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

// WIDGET SETTINGS SCREEN
// -----------------------------------------------------------------------------------------

@Composable
private fun WidgetCustomizationCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF1F1F1F)
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var showShortcutSheet by remember { mutableStateOf(false) }
    var showCustomUrlDialogFor by remember { mutableStateOf<String?>(null) }
    var showCustomAppDialogFor by remember { mutableStateOf<String?>(null) }
    var customInputValue by remember { mutableStateOf("") }
    var expandedDropdownFor by remember { mutableStateOf<String?>(null) }
    var showHexInput by remember { mutableStateOf(false) }
    var tempHexInput by remember { mutableStateOf("") }
    
    val shortcutOptions = listOf(
        "None" to Icons.Default.Close,
        "Google Lens" to ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_camera),
        "Live" to Icons.Default.AutoAwesome,
        "Translate (text)" to Icons.Default.Translate,
        "Translate (camera)" to Icons.Default.DocumentScanner,
        "Weather" to Icons.Default.WbSunny,
        "Sports" to Icons.Default.SportsBasketball,
        "Dictionary" to Icons.AutoMirrored.Filled.MenuBook,
        "Homework" to Icons.Default.School,
        "Finance" to Icons.AutoMirrored.Filled.TrendingUp,
        "Saved" to Icons.Default.Bookmark,
        "News" to Icons.AutoMirrored.Filled.Article
    )

    var localShowGIcon by remember { mutableStateOf(prefs.getBoolean("widget_show_g_icon", true)) }
    var localShowDoodle by remember { mutableStateOf(prefs.getBoolean("widget_show_doodle", true)) }
    var localThemeStyle by remember { mutableStateOf(prefs.getString("widget.theme.style", "System Default") ?: "System Default") }
    var localSubtheme by remember { mutableStateOf(prefs.getString("widget_subtheme", "System") ?: "System") }
    var localMaterialGIconTheme by remember { mutableStateOf(prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon") }
    var localHue by remember { mutableStateOf(prefs.getInt("widget_custom_hue", 277).toFloat()) }
    var localSaturation by remember { mutableStateOf(prefs.getInt("widget_custom_saturation", 51).toFloat()) }
    var localOpacity by remember { mutableStateOf(prefs.getInt("search.background.transparency", 28).toFloat()) }
    var localShowVoice by remember { mutableStateOf(prefs.getBoolean("widget_show_voice", true)) }
    var localActionIcon by remember { mutableStateOf(prefs.getString("widget_action_icon", "Search") ?: "Search") }
    var localShortcut by remember { mutableStateOf(prefs.getString("widget_shortcut", "Google Lens") ?: "Google Lens") }

    LaunchedEffect(
        localShowGIcon, localShowDoodle, localThemeStyle, localSubtheme,
        localMaterialGIconTheme, localHue, localSaturation, localOpacity,
        localShowVoice, localActionIcon, localShortcut
    ) {
        prefs.edit()
            .putBoolean("widget_show_g_icon", localShowGIcon)
            .putBoolean("widget_show_doodle", localShowDoodle)
            .putString("widget.theme.style", localThemeStyle)
            .putString("widget_subtheme", localSubtheme)
            .putString("widget_material_g_icon", localMaterialGIconTheme)
            .putInt("widget_custom_hue", localHue.toInt())
            .putInt("widget_custom_saturation", localSaturation.toInt())
            .putInt("search.background.transparency", localOpacity.toInt())
            .putBoolean("widget_show_voice", localShowVoice)
            .putString("widget_action_icon", localActionIcon)
            .putString("widget_shortcut", localShortcut)
            .apply()
        updateWidgets(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        localShowGIcon = true
                        localShowDoodle = false
                        localThemeStyle = "System Default"
                        localSubtheme = "System"
                        localMaterialGIconTheme = "Material G Icon"
                        localHue = 277f
                        localSaturation = 51f
                        localOpacity = 28f
                        localShowVoice = true
                        localActionIcon = "Search"
                        localShortcut = "Google Lens"
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        onBack()
                    }) {
                        Text("Save", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Preview Card
            SettingsCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF1C1B1F)),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "gemini_dots")
                    val phase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 2f * Math.PI.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(8000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "phase"
                    )

                    val customHue = localHue
                    val customSat = localSaturation / 100f
                    val alphaInt = (255 * (100 - localOpacity.toInt()) / 100).coerceIn(0, 255) / 255f
                    val accentColor = if (localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor((localOpacity * 2.55f).toInt(), floatArrayOf(customHue, customSat, 1f)))
                    } else {
                        MaterialTheme.colorScheme.primary
                    }

                    val geminiColors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF4285F4),
                        androidx.compose.ui.graphics.Color(0xFFEA4335),
                        androidx.compose.ui.graphics.Color(0xFFFBBC05),
                        androidx.compose.ui.graphics.Color(0xFF34A853)
                    )

                    val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)"
                    val matPrimary = MaterialTheme.colorScheme.primary
                    val matSecondary = MaterialTheme.colorScheme.secondary
                    val matTertiary = MaterialTheme.colorScheme.tertiary
                    val matError = MaterialTheme.colorScheme.error

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        val numParticles = 35
                        for (i in 0 until numParticles) {
                            val t = (i.toFloat() / numParticles + phase / (2f * Math.PI.toFloat())) % 1f
                            val x = t * width
                            
                            val waveAmp = height / 2.5f
                            val xOffset = Math.sin((t * 3f * Math.PI + phase * 1.2f).toDouble()).toFloat() * (width / 5f)
                            val yOffset1 = Math.sin((t * 2f * Math.PI + phase).toDouble()).toFloat() * waveAmp
                            val yOffset2 = Math.cos((t * 3f * Math.PI - phase).toDouble()).toFloat() * (waveAmp / 2f)
                            
                            val centerX = (x + xOffset + width) % width
                            val centerY = height / 2f + yOffset1 + yOffset2

                            val alphaEdge = if (t < 0.2f) t * 5f else if (t > 0.8f) (1f - t) * 5f else 1f
                            val twinkle = (Math.sin((t * 40f * Math.PI + phase * 8f).toDouble()).toFloat() + 1f) / 2f
                            val radius = (0.5.dp.toPx() + 1.dp.toPx() * twinkle) * alphaEdge
                            
                            val baseAlpha = 0.4f
                            val color = if (localSubtheme == "Custom") {
                                accentColor.copy(alpha = baseAlpha * alphaEdge * twinkle)
                            } else if (previewIsMaterialYou && localSubtheme == "Material") {
                                val dynamicColors = listOf(matPrimary, matSecondary, matTertiary, matError)
                                dynamicColors[i % dynamicColors.size].copy(alpha = baseAlpha * alphaEdge * twinkle)
                            } else {
                                geminiColors[i % geminiColors.size].copy(alpha = baseAlpha * alphaEdge * twinkle)
                            }

                            drawCircle(
                                color = color,
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                            )
                            
                            drawCircle(
                                color = color.copy(alpha = (baseAlpha / 2f) * alphaEdge * twinkle),
                                radius = radius * 2.5f,
                                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                            )
                        }
                    }
                    
                    val waterPhase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 2f * Math.PI.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(4000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    val previewRimColorAlpha = if (previewIsMaterialYou) {
                        if (localSubtheme == "Custom") {
                            accentColor.copy(alpha = alphaInt)
                        } else if (localSubtheme == "Material") {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = alphaInt)
                        } else {
                            androidx.compose.ui.graphics.Color(0xFF6C63FF).copy(alpha = alphaInt)
                        }
                    } else androidx.compose.ui.graphics.Color.Transparent
                    
                    val rimBrush = if (previewIsMaterialYou && localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = alphaInt),
                                accentColor.copy(alpha = alphaInt * 0.4f),
                                accentColor.copy(alpha = alphaInt)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(Math.cos(waterPhase.toDouble()).toFloat() * 1000f + 1000f, Math.sin(waterPhase.toDouble()).toFloat() * 500f + 500f)
                        )
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(previewRimColorAlpha, previewRimColorAlpha))
                    }
                    
                    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val previewPillColorAlpha = if (previewIsMaterialYou) {
                        androidx.compose.ui.graphics.Color(0xFF1F1F1F).copy(alpha = alphaInt)
                    } else {
                        when (localSubtheme) {
                            "Light" -> androidx.compose.ui.graphics.Color.White.copy(alpha = alphaInt)
                            "Dark" -> androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = alphaInt)
                            "Custom" -> accentColor.copy(alpha = alphaInt)
                            else -> if (isSystemDark) androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = alphaInt) else androidx.compose.ui.graphics.Color.White.copy(alpha = alphaInt)
                        }
                    }
                    
                    val previewIconTint = if (!previewIsMaterialYou) {
                        // System Design
                        when (localSubtheme) {
                            "System" -> MaterialTheme.colorScheme.onSurfaceVariant // Material 3 Color
                            "Dark" -> androidx.compose.ui.graphics.Color.White
                            "Light" -> androidx.compose.ui.graphics.Color(0xFF5F6368)
                            "Custom" -> {
                                val luminance = (0.299 * accentColor.red + 0.587 * accentColor.green + 0.114 * accentColor.blue)
                                if (luminance > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                            }
                            else -> if (isSystemDark) androidx.compose.ui.graphics.Color(0xFF9AA0A6) else androidx.compose.ui.graphics.Color(0xFF5F6368)
                        }
                    } else {
                        // Material Design
                        when (localMaterialGIconTheme) {
                            "System G Icon" -> androidx.compose.ui.graphics.Color.White
                            "Material G Icon" -> MaterialTheme.colorScheme.onSurfaceVariant // Material 3 Color
                            "Accented G Icon" -> accentColor
                            else -> androidx.compose.ui.graphics.Color.White
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(
                                Modifier.background(rimBrush, RoundedCornerShape(40.dp))
                            )
                            .padding(if (previewIsMaterialYou) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .then(
                                    Modifier.background(previewPillColorAlpha, RoundedCornerShape(28.dp))
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (localShowGIcon) {
                                val gIconTint = if (!previewIsMaterialYou && localSubtheme == "System") {
                                    previewIconTint
                                } else if (previewIsMaterialYou && localMaterialGIconTheme != "System G Icon") {
                                    previewIconTint
                                } else {
                                    androidx.compose.ui.graphics.Color.Unspecified
                                }
                                val useOriginalGIcon = !previewIsMaterialYou && localSubtheme != "System"
                                ComposeGIcon(
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    secondaryColor = MaterialTheme.colorScheme.secondary,
                                    tertiaryColor = MaterialTheme.colorScheme.tertiary,
                                    isAccented = localMaterialGIconTheme == "Accented G Icon",
                                    accentColor = accentColor,
                                    fallbackTint = gIconTint,
                                    useOriginalColors = useOriginalGIcon
                                )
                            }
                            
                            Spacer(Modifier.weight(1f))
                            
                            if (localShortcut != "None") {
                                val shortcutOption = shortcutOptions.find { it.first == localShortcut }
                                if (shortcutOption != null) {
                                    Icon(
                                        imageVector = shortcutOption.second,
                                        contentDescription = localShortcut,
                                        tint = previewIconTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (localShowVoice) {
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                }
                            }
                            if (localShowVoice) {
                                Icon(Icons.Default.Mic, contentDescription = "Mic", modifier = Modifier.size(24.dp), tint = previewIconTint)
                            }
                        }
                        
                        if (localActionIcon != "None" && previewIsMaterialYou) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(previewPillColorAlpha, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val actIcon = when (localActionIcon) {
                                    "Search" -> com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored
                                    "Gemini" -> com.pixel.intelligentsearch.R.drawable.ic_gemini
                                    "Now Playing" -> com.pixel.intelligentsearch.R.drawable.ic_music
                                    else -> com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored
                                }
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = actIcon),
                                    contentDescription = "Action",
                                    tint = previewIconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // G Icon options
            SettingsCard {
                SettingsRowToggle(
                    title = "Display G Icon",
                    subtitle = "Show Google logo in search bar",
                    icon = null,
                    customIcon = {
                        if (localThemeStyle == "System Default") {
                            Image(
                                bitmap = androidx.core.content.ContextCompat.getDrawable(
                                    LocalContext.current,
                                    R.drawable.ic_g_logo_colored
                                )!!.toBitmap().asImageBitmap(),
                                contentDescription = "G Icon",
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            val previewIsMaterialYou = localThemeStyle == "Material Design"
                            val previewIconTint = if (!previewIsMaterialYou) {
                                when (localSubtheme) {
                                    "Light" -> androidx.compose.ui.graphics.Color(0xFF4285F4)
                                    "Dark" -> androidx.compose.ui.graphics.Color.White
                                    "Custom" -> androidx.compose.ui.graphics.Color.White
                                    else -> if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF4285F4)
                                }
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val alphaInt = (255 * (100 - localOpacity.toInt()) / 100).coerceIn(0, 255) / 255f
                            val accentColor = if (localSubtheme == "Custom") {
                                androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor((localOpacity * 2.55f).toInt(), floatArrayOf(localHue, localSaturation / 100f, 1f)))
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                            val gTint = if (localMaterialGIconTheme != "System G Icon") previewIconTint else androidx.compose.ui.graphics.Color.Unspecified
                            ComposeGIcon(
                                modifier = Modifier.size(24.dp),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                secondaryColor = MaterialTheme.colorScheme.secondary,
                                tertiaryColor = MaterialTheme.colorScheme.tertiary,
                                isAccented = localMaterialGIconTheme == "Accented G Icon",
                                accentColor = accentColor,
                                fallbackTint = gTint
                            )
                        }
                    },
                    isChecked = localShowGIcon,
                    onCheckedChange = { localShowGIcon = it },
                    showDivider = true
                )
                SettingsRowToggle(
                    title = "G Icon Doodle",
                    subtitle = "Show special event doodles",
                    icon = Icons.Default.Brush,
                    isChecked = localShowDoodle,
                    onCheckedChange = { localShowDoodle = it },
                    showDivider = false
                )
            }

            // Theme Buttons
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSystem = localThemeStyle == "System Default"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) { localThemeStyle = "System Default" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("System Design", style = MaterialTheme.typography.labelLarge, color = if (isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (!isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) { localThemeStyle = "Material You (Minimal)" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Material Design", style = MaterialTheme.typography.labelLarge, color = if (!isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Floating Theme Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (localThemeStyle == "System Default") {
                    val systemOpts = listOf("System", "Light", "Dark", "Custom")
                    systemOpts.forEach { opt ->
                        val isSel = localSubtheme == opt
                        val dynColor = androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor((localOpacity * 2.55f).toInt(), floatArrayOf(localHue, localSaturation / 100f, 1f)))
                        val bgModifier = if (opt == "Custom") {
                            if (isSel) {
                                Modifier.background(dynColor, RoundedCornerShape(32.dp))
                            } else {
                                Modifier.background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(androidx.compose.ui.graphics.Color(0xFF3F51B5), androidx.compose.ui.graphics.Color(0xFFE91E63))
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                )
                            }
                        } else {
                            Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                        }
                        
                        val borderMod = Modifier
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(bgModifier)
                                .then(borderMod)
                                .clip(RoundedCornerShape(32.dp))
                                .bouncyClickable(shape = RoundedCornerShape(32.dp)) { localSubtheme = opt },
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor = if (isSel) {
                                if (opt == "Custom") {
                                    val luminance = (0.299 * dynColor.red + 0.587 * dynColor.green + 0.114 * dynColor.blue)
                                    if (luminance > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                                } else MaterialTheme.colorScheme.onPrimaryContainer
                            } else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                        }
                    }
                } else {
                    val matOpts = listOf("Material", "Custom")
                    matOpts.forEach { opt ->
                        val isSel = localSubtheme == opt
                        val dynColor = androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor((localOpacity * 2.55f).toInt(), floatArrayOf(localHue, localSaturation / 100f, 1f)))
                        val bgModifier = if (opt == "Custom") {
                            if (isSel) {
                                Modifier.background(dynColor, RoundedCornerShape(32.dp))
                            } else {
                                Modifier.background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(androidx.compose.ui.graphics.Color(0xFF5E35B1), androidx.compose.ui.graphics.Color(0xFFAD1457))
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                )
                            }
                        } else {
                            Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                        }
                        val borderMod = Modifier
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .then(bgModifier)
                                .then(borderMod)
                                .clip(RoundedCornerShape(32.dp))
                                .bouncyClickable(shape = RoundedCornerShape(32.dp)) { localSubtheme = opt },
                            contentAlignment = Alignment.Center
                        ) {
                            val textColor = if (isSel) {
                                if (opt == "Custom") {
                                    val luminance = (0.299 * dynColor.red + 0.587 * dynColor.green + 0.114 * dynColor.blue)
                                    if (luminance > 0.5f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
                                } else MaterialTheme.colorScheme.onPrimaryContainer
                            } else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                        }
                    }
                }
            }

            if (localThemeStyle == "Material You (Minimal)" && localSubtheme == "Custom") {
                // Material G Icon Row
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val opts = listOf("System G Icon", "Material G Icon", "Accented G Icon")
                        opts.forEach { opt ->
                            val isSel = localMaterialGIconTheme == opt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .bouncyClickable(shape = androidx.compose.foundation.shape.CircleShape) { localMaterialGIconTheme = opt },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(opt, style = MaterialTheme.typography.labelSmall, color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (localSubtheme == "Custom") {
                // Sliders Card
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Hue
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hue", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localHue.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Red,
                                            androidx.compose.ui.graphics.Color.Yellow,
                                            androidx.compose.ui.graphics.Color.Green,
                                            androidx.compose.ui.graphics.Color.Cyan,
                                            androidx.compose.ui.graphics.Color.Blue,
                                            androidx.compose.ui.graphics.Color.Magenta,
                                            androidx.compose.ui.graphics.Color.Red
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localHue,
                                        onValueChange = { localHue = it },
                                        valueRange = 0f..360f,
                                        modifier = Modifier.fillMaxWidth(),
                                        showTrack = false
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Saturation
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Saturation", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.White,
                                            androidx.compose.ui.graphics.Color(0xFFE91E63)
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localSaturation,
                                        onValueChange = { localSaturation = it },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth(),
                                        showTrack = false
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Opacity
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Contrast, contentDescription = "Opacity", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Opacity", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localOpacity.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color.White
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )) {
                                    Android17Slider(
                                        value = localOpacity,
                                        onValueChange = { localOpacity = it },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth(),
                                        showTrack = false
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Hex Color Box
                        val customColorHex = String.format("#%06X", (0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, 1f))))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    tempHexInput = customColorHex
                                    showHexInput = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = "Hex Color", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(customColorHex, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Edit, contentDescription = "Edit Hex", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Text("WIDGET ACTIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

            // Actions Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show voice search icon in the widget",
                    icon = Icons.Default.Mic,
                    isChecked = localShowVoice,
                    onCheckedChange = { localShowVoice = it },
                    showDivider = true
                )
                val customSat = localSaturation / 100f
                val alphaInt = (255 * (100 - localOpacity.toInt()) / 100).coerceIn(0, 255) / 255f
                val dynamicAccentColor = if (localSubtheme == "Custom") {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor((localOpacity * 2.55f).toInt(), floatArrayOf(localHue, customSat, 1f)))
                } else {
                    MaterialTheme.colorScheme.primary
                }
                SettingsDropdownRow(
                    title = "Widget Action Icon",
                    subtitle = localActionIcon,
                    iconContent = {
                        if (localActionIcon == "None") {
                            Icon(Icons.Default.Close, contentDescription = "None", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ComposeActionIcon(
                                iconType = localActionIcon,
                                modifier = Modifier.size(24.dp),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                secondaryColor = MaterialTheme.colorScheme.secondary,
                                tertiaryColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    },
                    options = listOf("None", "Search", "Gemini", "Now Playing"),
                    selectedOption = localActionIcon,
                    onOptionSelected = { localActionIcon = it },
                    showDivider = true,
                    optionIcons = mapOf(
                        "Search" to com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored,
                        "Gemini" to com.pixel.intelligentsearch.R.drawable.ic_gemini,
                        "Now Playing" to com.pixel.intelligentsearch.R.drawable.ic_music
                    )
                )
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = localShortcut,
                    icon = shortcutOptions.find { it.first == localShortcut }?.second ?: Icons.Default.AddCircleOutline,
                    onClick = { showShortcutSheet = true },
                    showDivider = false
                )
            }

            if (showShortcutSheet) {
                ModalBottomSheet(onDismissRequest = { showShortcutSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Select Widget Shortcut", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(shortcutOptions.size) { index ->
                                val option = shortcutOptions[index]
                                val isCustomizable = option.first in listOf("Weather", "Sports", "Dictionary")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.bouncyClickable {
                                        if (isCustomizable) {
                                            expandedDropdownFor = option.first
                                        } else {
                                            localShortcut = option.first
                                            showShortcutSheet = false
                                        }
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    if (localShortcut == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .padding(16.dp),
                                            tint = if (localShortcut == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isCustomizable) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Options",
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(20.dp)
                                                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                            androidx.compose.material3.DropdownMenu(
                                                expanded = expandedDropdownFor == option.first,
                                                onDismissRequest = { expandedDropdownFor = null }
                                            ) {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Default (Google)") },
                                                    onClick = {
                                                        localShortcut = option.first
                                                        showShortcutSheet = false
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom Website (URL)") },
                                                    onClick = {
                                                        showCustomUrlDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text("Custom App (APK)") },
                                                    onClick = {
                                                        showCustomAppDialogFor = option.first
                                                        expandedDropdownFor = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = option.first,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showHexInput) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showHexInput = false },
                    title = { Text("Enter Hex Color") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = tempHexInput,
                            onValueChange = { tempHexInput = it },
                            label = { Text("Hex Code") }
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            try {
                                val parsedColor = android.graphics.Color.parseColor(if (!tempHexInput.startsWith("#")) "#$tempHexInput" else tempHexInput)
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsedColor, hsv)
                                localHue = hsv[0]
                                localSaturation = hsv[1] * 100f
                            } catch (e: Exception) {}
                            showHexInput = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showHexInput = false }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomUrlDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomUrlDialogFor = null },
                    title = { Text("Custom URL for ${showCustomUrlDialogFor}") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter URL") }
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            prefs.edit()
                                .putString("${showCustomUrlDialogFor}_custom_type", "url")
                                .putString("${showCustomUrlDialogFor}_custom_value", customInputValue)
                                .apply()
                            localShortcut = showCustomUrlDialogFor!!
                            showShortcutSheet = false
                            showCustomUrlDialogFor = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCustomUrlDialogFor = null }) { Text("Cancel") }
                    }
                )
            }

            if (showCustomAppDialogFor != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCustomAppDialogFor = null },
                    title = { Text("Custom App for ${showCustomAppDialogFor}") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Enter Package Name") }
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            prefs.edit()
                                .putString("${showCustomAppDialogFor}_custom_type", "app")
                                .putString("${showCustomAppDialogFor}_custom_value", customInputValue)
                                .apply()
                            localShortcut = showCustomAppDialogFor!!
                            showShortcutSheet = false
                            showCustomAppDialogFor = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCustomAppDialogFor = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
fun ComposeActionIcon(
    iconType: String,
    modifier: Modifier = Modifier,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            when (iconType) {
                "Search" -> {
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15").toPath(),
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5").toPath(),
                        color = tertiaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 15.5,15.5 L 20,20").toPath(),
                        color = secondaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.0f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z").toPath(),
                        color = primaryColor
                    )
                }
                "Gemini" -> {
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,2L14.8,9.2L22,12L14.8,14.8L12,22L9.2,14.8L2,12L9.2,9.2L12,2Z").toPath(),
                        color = primaryColor
                    )
                }
                "Now Playing" -> {
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 19,11.5 L 19,12.5").toPath(),
                        color = tertiaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 14.5,9.5 L 14.5,15.5").toPath(),
                        color = secondaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 10,7 L 10,16").toPath(),
                        color = primaryColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawPath(
                        path = androidx.compose.ui.graphics.vector.PathParser().parsePathString("M 11.6,16.5 C 11.6,19.26 9.36,21.5 6.6,21.5 C 3.84,21.5 1.6,19.26 1.6,16.5 C 1.6,13.74 3.84,11.5 6.6,11.5 C 8.6,11.5 10.3,12.7 11.1,14.4 Z").toPath(),
                        color = primaryColor
                    )
                }
            }
        }
    }
}

fun updateWidgets(context: Context) {
    val intent = android.content.Intent(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
        action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java)
        )
        putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    }
    context.sendBroadcast(intent)
}

@Composable
fun ComposeGIcon(
    modifier: Modifier = Modifier,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color,
    isAccented: Boolean = false,
    accentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fallbackTint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    useOriginalColors: Boolean = false
) {
    if (useOriginalColors) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
            contentDescription = "G Logo",
            modifier = modifier
        )
        return
    }
    if (fallbackTint != androidx.compose.ui.graphics.Color.Unspecified) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
            contentDescription = "G Logo",
            modifier = modifier,
            tint = fallbackTint
        )
        return
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val pColor = if (isAccented) accentColor else primaryColor
            val sColor = if (isAccented) accentColor else secondaryColor
            val tColor = if (isAccented) accentColor else tertiaryColor

            drawPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M22.56,12.25C22.56,11.47 22.49,10.72 22.36,10L12,10L12,14.26L17.92,14.26C17.66,15.63 16.88,16.79 15.71,17.57L15.71,20.34L19.28,20.34C21.36,18.42 22.56,15.6 22.56,12.25Z").toPath(), color = pColor)
            drawPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,23C14.97,23 17.46,22.02 19.28,20.34L15.71,17.57C14.73,18.23 13.48,18.63 12,18.63C9.14,18.63 6.71,16.7 5.84,14.1L2.18,14.1L2.18,16.94C3.99,20.53 7.7,23 12,23Z").toPath(), color = sColor)
            drawPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M5.84,14.09C5.62,13.43 5.5,12.73 5.5,12C5.5,11.27 5.62,10.57 5.84,9.91L5.84,7.07L2.18,7.07C1.43,8.55 1,10.22 1,12C1,13.78 1.43,15.45 2.18,16.93L5.84,14.09Z").toPath(), color = tColor)
            drawPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,5.38C13.62,5.38 15.06,5.94 16.21,7.02L19.36,3.87C17.45,2.09 14.97,1 12,1C7.7,1 3.99,3.47 2.18,7.07L5.84,9.91C6.71,7.31 9.14,5.38 12,5.38Z").toPath(), color = pColor)
        }
    }
}

@Composable
fun Android17Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    showTrack: Boolean = true
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    var width by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val newFraction = (offset.x / width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start))
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    val newFraction = (change.position.x / width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val amplitude = 6.dp.toPx()
            val frequency = 0.05f
            val thumbWidth = 6.dp.toPx()
            val thumbHeight = 36.dp.toPx()

            val thumbX = (fraction * size.width).coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
            val centerY = size.height / 2f

            // Draw tick marks
            if (steps > 0) {
                val tickRadius = 2.5.dp.toPx()
                val yOffset = centerY + 18.dp.toPx()
                val segments = steps + 1
                val tickSpacing = size.width / segments
                
                for (i in 0..segments) {
                    val cx = i * tickSpacing
                    drawCircle(
                        color = if (cx <= thumbX) activeColor.copy(alpha = 0.5f) else inactiveColor.copy(alpha = 0.5f),
                        radius = tickRadius,
                        center = androidx.compose.ui.geometry.Offset(cx, yOffset)
                    )
                }
            }

            // Draw active track (Squiggle)
            if (showTrack) {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, centerY)
                var x = 0f
                while (x < thumbX) {
                    // To move towards the right, we subtract the phase
                    val y = centerY + Math.sin((x * frequency - phase).toDouble()).toFloat() * amplitude
                    path.lineTo(x, y)
                    x += 2f
                }
                
                drawPath(
                    path = path,
                    color = activeColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = trackHeight,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
    
                // Draw inactive track (Straight line)
                if (thumbX < size.width) {
                    drawLine(
                        color = inactiveColor,
                        start = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                        end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                        strokeWidth = trackHeight,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Draw thumb (vertical pill)
            drawRoundRect(
                color = thumbColor,
                topLeft = androidx.compose.ui.geometry.Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f)
            )
        }
    }
}

fun performClickHaptic(context: android.content.Context) {
    // No-op: Haptics are now handled globally inside Modifier.bouncyClickable 
    // using view.performHapticFeedback to follow Material Design principles.
}

// -----------------------------------------------------------------------------------------
// SEARCH PILLS SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPillsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    var searchPills by rememberStringPreference(prefs, "search.pills", "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files")
    val pillList = remember(searchPills) { searchPills.split(",").filter { it.isNotBlank() } }
    
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<android.content.pm.PackageInfo>>(emptyList()) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    
    var showMaxWarning by remember { mutableStateOf(false) }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker && installedApps.isEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
                installedApps = resolveInfoList.mapNotNull {
                    try {
                        context.packageManager.getPackageInfo(it.activityInfo.packageName, 0)
                    } catch (e: Exception) { null }
                }.distinctBy { it.packageName }.sortedBy { getAppName(context, it.packageName).lowercase() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showAppPicker && multiSelectMode) {
                        Text("${selectedApps.size} Selected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } else if (showAppPicker) {
                        Text("Add Applications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Application Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showAppPicker && multiSelectMode) {
                            multiSelectMode = false
                            selectedApps = emptySet()
                        } else if (showAppPicker) {
                            showAppPicker = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(if (showAppPicker && multiSelectMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showAppPicker && multiSelectMode) {
                        IconButton(onClick = {
                            val currentList = searchPills.split(",").filter { it.isNotBlank() }.toMutableList()
                            val newAppsCount = selectedApps.count { !currentList.contains(it) }
                            if (currentList.size + newAppsCount > 20) {
                                showMaxWarning = true
                            } else {
                                selectedApps.forEach { app ->
                                    if (!currentList.contains(app)) currentList.add(app)
                                }
                                searchPills = currentList.joinToString(",")
                                viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, currentList.size)
                                prefs.edit().putInt("shortcut_results_count", currentList.size).apply()
                                multiSelectMode = false
                                selectedApps = emptySet()
                                showAppPicker = false
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Add Selected")
                        }
                    } else if (!showAppPicker) {
                        IconButton(onClick = { showAppPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Pill")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showMaxWarning) {
            AlertDialog(
                onDismissRequest = { showMaxWarning = false },
                title = { Text("Maximum Reached", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                text = { Text("You cannot add more than 20 apps to the Quick Launch at a time.", style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = { showMaxWarning = false }) {
                        Text("OK", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }

        if (showAppPicker) {
            var appSearchQuery by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search apps...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
                
                val filteredApps = installedApps.filter { 
                    val label = it.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
                    label.contains(appSearchQuery, ignoreCase = true)
                }

                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredApps.size) { index ->
                        val packageInfo = filteredApps[index]
                        val packageName = packageInfo.packageName
                        val label = packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
                        val isSelected = selectedApps.contains(packageName)
                        val appIcon = remember(packageName) {
                            try {
                                packageInfo.applicationInfo?.loadIcon(context.packageManager) ?: Icons.Default.Apps
                            } catch (e: Exception) {
                                Icons.Default.Apps
                            }
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                        ) {
                            SettingsRow(
                                title = label,
                                subtitle = packageName,
                                icon = appIcon,
                                onClick = {
                                    if (multiSelectMode) {
                                        selectedApps = if (isSelected) selectedApps - packageName else selectedApps + packageName
                                        if (selectedApps.isEmpty()) multiSelectMode = false
                                    } else {
                                        if (!pillList.contains(packageName)) {
                                            val newListList = if (searchPills.isEmpty()) listOf(packageName) else searchPills.split(",").filter { it.isNotBlank() } + packageName
                                            if (newListList.size <= 20) {
                                                searchPills = newListList.joinToString(",")
                                                viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, newListList.size)
                                                prefs.edit().putInt("shortcut_results_count", newListList.size).apply()
                                            } else {
                                                showMaxWarning = true
                                            }
                                        }
                                        if (!showMaxWarning) showAppPicker = false
                                    }
                                },
                                onLongClick = {
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        selectedApps = setOf(packageName)
                                    }
                                },
                                showDivider = !isSelected && index < filteredApps.size - 1
                            )
                        }
                    }
                }
            }
        } else {
                var localPillList by remember(searchPills) {
                    mutableStateOf(searchPills.split(",").filter { it.isNotBlank() })
                }
                
                var draggingPackage by remember { mutableStateOf<String?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                val listState = rememberLazyListState()
                
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(localPillList, key = { it }) { packageName ->
                        val isDragging = draggingPackage == packageName
                        val draggingModifier = if (isDragging) {
                            Modifier.zIndex(1f).graphicsLayer {
                                translationY = dragOffset
                            }
                        } else {
                            Modifier.animateItem()
                        }
                        Box(modifier = draggingModifier) {
                            val elevation = if (isDragging) 8.dp else 0.dp
                                
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { it * 0.5f },
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                            val currentList = localPillList.toMutableList()
                                            currentList.remove(packageName)
                                            localPillList = currentList
                                            searchPills = currentList.joinToString(",")
                                            viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, currentList.size)
                                            prefs.edit().putInt("shortcut_results_count", currentList.size).apply()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = !isDragging,
                                enableDismissFromEndToStart = !isDragging,
                                backgroundContent = {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            ) {
                                val appNameState = remember(packageName) { mutableStateOf(packageName) }
                                val iconState = remember(packageName) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                                
                                LaunchedEffect(packageName) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val pm = context.packageManager
                                            val info = pm.getApplicationInfo(packageName, 0)
                                            appNameState.value = pm.getApplicationLabel(info).toString()
                                        } catch (e: Exception) {}
                                        try {
                                            val pm = context.packageManager
                                            iconState.value = pm.getApplicationIcon(packageName)
                                        } catch (e: Exception) {}
                                    }
                                }
                                val appName = appNameState.value
                                val icon = iconState.value

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(elevation, RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (icon != null) {
                                                Image(
                                                    bitmap = icon.toBitmap().asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            Text(
                                                text = appName,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.pointerInput(packageName) {
                                                detectVerticalDragGestures(
                                                    onDragStart = {
                                                        draggingPackage = packageName
                                                        dragOffset = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggingPackage = null
                                                        dragOffset = 0f
                                                        searchPills = localPillList.joinToString(",")
                                                    },
                                                    onDragCancel = {
                                                        draggingPackage = null
                                                        dragOffset = 0f
                                                    },
                                                    onVerticalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffset += dragAmount
                                                        
                                                        val currentDraggingPackage = draggingPackage ?: return@detectVerticalDragGestures
                                                        val draggingItem = listState.layoutInfo.visibleItemsInfo.find { it.key == currentDraggingPackage }
                                                        if (draggingItem != null) {
                                                            val draggingCenter = draggingItem.offset + (draggingItem.size / 2) + dragOffset
                                                            
                                                            val targetItem = listState.layoutInfo.visibleItemsInfo.find {
                                                                it.key != currentDraggingPackage &&
                                                                draggingCenter > it.offset &&
                                                                draggingCenter < (it.offset + it.size)
                                                            }
                                                            
                                                            if (targetItem != null && targetItem.key is String) {
                                                                val from = localPillList.indexOf(currentDraggingPackage)
                                                                val to = localPillList.indexOf(targetItem.key as String)
                                                                if (from != -1 && to != -1) {
                                                                    val currentList = localPillList.toMutableList()
                                                                    currentList.removeAt(from)
                                                                    currentList.add(to, currentDraggingPackage)
                                                                    localPillList = currentList
                                                                    
                                                                    val shift = targetItem.offset - draggingItem.offset
                                                                    dragOffset -= shift
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (pillList.isEmpty()) {
                        item {
                            Text("No apps added. Click + to add some.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
        }
    }
}

// -----------------------------------------------------------------------------------------
// LAUNCH PORTAL SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPortalScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", true) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Launch Portal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsCard {
                SettingsRow(
                    title = "Search Tile",
                    subtitle = "Add to quick settings notification panel",
                    icon = Icons.Default.ViewAgenda,
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            try {
                                @android.annotation.SuppressLint("WrongConstant")
                                val statusBarManager = context.getSystemService("statusbar") as? android.app.StatusBarManager
                                val componentName = android.content.ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchTileService::class.java)
                                val icon = android.graphics.drawable.Icon.createWithResource(context, com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored)
                                statusBarManager?.requestAddTileService(
                                    componentName,
                                    "Intelligent Search",
                                    icon,
                                    java.util.concurrent.Executors.newSingleThreadExecutor(),
                                    { _ -> }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Please add the tile manually from your notification shade.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    showDivider = true
                )
                
                SettingsRow(
                    title = "Home Screen Shortcut",
                    subtitle = "Add app icon to home screen",
                    icon = Icons.Default.AddHome,
                    onClick = {
                        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                            try {
                                val pinShortcutInfo = ShortcutInfo.Builder(context, "intelligent_search_main")
                                    .setShortLabel("Search")
                                    .setIcon(Icon.createWithResource(context, com.pixel.intelligentsearch.R.mipmap.ic_launcher))
                                    .setIntent(
                                        Intent(context, com.pixel.intelligentsearch.MainActivity::class.java).apply {
                                            action = Intent.ACTION_MAIN
                                        }
                                    )
                                    .build()
                                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo)
                                val successCallback = PendingIntent.getBroadcast(
                                    context, 0,
                                    pinnedShortcutCallbackIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )
                                shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.intentSender)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            Toast.makeText(context, "Pinning shortcuts not supported on this device.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    showDivider = false
                )
            }
        
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) 
                    .clipToBounds() 
            ) {
                if (morphAnimationEnabled) {
                    MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
}

@Composable
fun Modifier.waterBackground(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val transition = rememberInfiniteTransition(label = "waterTransition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waterPhase"
    )
    return this
        .clip(shape)
        .drawBehind {
            drawRect(color.copy(alpha = 0.3f))
            val path = androidx.compose.ui.graphics.Path()
            val waveHeight = this.size.height * 0.15f
            val baseLine = this.size.height * 0.55f
            path.moveTo(0f, this.size.height)
            path.lineTo(0f, baseLine)
            var x = 0f
            while (x <= this.size.width) {
                val y = baseLine + kotlin.math.sin((x / this.size.width) * 2 * Math.PI + phase).toFloat() * waveHeight
                path.lineTo(x, y)
                x += 5f
            }
            path.lineTo(this.size.width, this.size.height)
            path.close()
            drawPath(path, color.copy(alpha = 0.8f))
        }
}




@Composable
fun SettingsShellBox(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFEF7FF),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D1B20))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 14.sp, color = Color(0xFF49454F))
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. THE 40 RANDOM SHAPE GENERATOR & ENGINE LOGIC
// -----------------------------------------------------------------------------------------

private fun generate40MaterialShapes(): List<RoundedPolygon> {
    val shapes = mutableListOf<RoundedPolygon>()
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 0.2f)))
    for (i in 3..12) shapes.add(RoundedPolygon(numVertices = i, rounding = CornerRounding(radius = 1f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.5f, rounding = CornerRounding(radius = 0.2f)))
    for (i in 4..13) shapes.add(RoundedPolygon.star(numVerticesPerRadius = i, innerRadius = 0.7f, rounding = CornerRounding(radius = 0.6f)))
    return shapes.shuffled()
}

@Stable
class MorphAnimationEngine(val coroutineScope: CoroutineScope) {
    val shapePool = generate40MaterialShapes()

    val bouncers = List(4) { BouncerState(it, this) }

    val hapticEventFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
}

class BouncerState(val index: Int, val engine: MorphAnimationEngine) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    
    val rotation = Animatable(0f)
    val morphProgress = Animatable(0f)
    val alpha = Animatable(0f)
    
    var morph by mutableStateOf(Morph(engine.shapePool[index * 5], engine.shapePool[(index * 5 + 1) % engine.shapePool.size]))

    var vx = 0f
    var vy = 0f
    var boundsWidth = 0f
    var boundsHeight = 0f
    var sizePx = 0f
    
    fun updateBounds(width: Float, height: Float) {
        boundsWidth = width
        boundsHeight = height
        val baseSize = listOf(0.040f, 0.045f, 0.035f, 0.040f)[index % 4]
        sizePx = width * baseSize
    }

    init {
        val startDelay = listOf(0L, 150L, 350L, 500L)[index % 4]
        engine.coroutineScope.launch {
            rotation.animateTo(360f, infiniteRepeatable(tween(8000 + startDelay.toInt(), easing = LinearEasing)))
        }
        
        // Morph loop
        engine.coroutineScope.launch {
            var poolIdx = index * 5
            while (true) {
                morphProgress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
                poolIdx = (poolIdx + 1) % engine.shapePool.size
                morphProgress.snapTo(0f)
                morph = Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
            }
        }
        
        engine.coroutineScope.launch {
            delay(startDelay)
            
            while (boundsWidth == 0f || boundsHeight == 0f) {
                delay(16)
            }
            
            // Initial positioning
            x = boundsWidth * listOf(0.15f, 0.40f, 0.65f, 0.85f)[index % 4]
            if (index % 2 == 0) {
                y = boundsHeight - sizePx
                vy = -(1500f + Math.random().toFloat() * 1000f)
            } else {
                y = boundsHeight * 0.2f
                vy = 0f
            }
            vx = (if (Math.random() > 0.5) 1f else -1f) * (200f + Math.random().toFloat() * 400f)
            
            launch { alpha.animateTo(1f, tween(300)) }

            val gravity = 3500f 
            val restitution = 0.75f 
            val wallRestitution = 0.85f
            
            var lastTime = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val currentTime = androidx.compose.runtime.withFrameNanos { it }
                val dt = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                
                val safeDt = minOf(dt, 0.05f)
                
                vy += gravity * safeDt
                
                x += vx * safeDt
                y += vy * safeDt
                
                var bounced = false
                
                val maxY = boundsHeight - sizePx
                if (y > maxY) {
                    y = maxY
                    if (vy > 0) {
                        vy = -vy * restitution
                        bounced = true
                        
                        // Stop micro-bouncing (settle on floor)
                        if (kotlin.math.abs(vy) < 60f) {
                            vy = 0f
                            bounced = false // too small to feel
                        }
                    }
                }
                
                val minX = sizePx
                val maxX = boundsWidth - sizePx
                if (x < minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                        bounced = true
                    }
                } else if (x > maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                        bounced = true
                    }
                }
                
                // Floor friction
                if (y == maxY) {
                    vx *= (1f - 2f * safeDt) 
                }
                
                if (bounced) {
                    engine.hapticEventFlow.tryEmit(Unit)
                }
                
                // Respawn if fully settled
                if (y == maxY && vy == 0f && kotlin.math.abs(vx) < 5f) {
                    delay(1500)
                    // Reset position
                    x = sizePx + Math.random().toFloat() * (boundsWidth - 2 * sizePx)
                    if (Math.random() > 0.5) {
                        y = boundsHeight - sizePx
                        vy = -(1500f + Math.random().toFloat() * 1200f)
                    } else {
                        y = boundsHeight * 0.1f
                        vy = 0f
                    }
                    vx = (if (Math.random() > 0.5) 1f else -1f) * (300f + Math.random().toFloat() * 500f)
                    
                    lastTime = androidx.compose.runtime.withFrameNanos { it } // avoid dt spike
                }
            }
        }
    }
}

@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val engine = LocalMorphEngine.current 
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(engine, lifecycleOwner) {
        engine.hapticEventFlow.collect {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                triggerCrispHapticThump(context, view)
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        LaunchedEffect(width, height) {
            engine.bouncers.forEach { it.updateBounds(width, height) }
        }

        val baseAccentColor = MaterialTheme.colorScheme.primary
        val variant1 = MaterialTheme.colorScheme.secondary
        val variant2 = MaterialTheme.colorScheme.tertiary
        val variant3 = MaterialTheme.colorScheme.primaryContainer

        val colors = listOf(baseAccentColor, variant1, variant2, variant3)

        engine.bouncers.forEachIndexed { index, bouncer ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sizePx = bouncer.sizePx
                if (sizePx <= 0f) return@Canvas
                
                translate(left = bouncer.x, top = bouncer.y) {
                    rotate(bouncer.rotation.value) {
                        val path = android.graphics.Path()
                        bouncer.morph.toPath(progress = bouncer.morphProgress.value, path = path)
                        
                        scale(scale = sizePx, pivot = Offset.Zero) {
                            drawPath(path.asComposePath(), colors[index].copy(alpha = bouncer.alpha.value * 0.9f))
                        }
                    }
                }
            }
        }
    }
}

private fun triggerCrispHapticThump(context: Context, view: android.view.View) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            if (vibratorManager.defaultVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.45f).compose()
                )
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    } catch (e: Exception) { }
}




