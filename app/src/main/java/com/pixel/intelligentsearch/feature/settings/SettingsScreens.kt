package com.pixel.intelligentsearch.feature.settings
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchTileService
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.search.getAppName
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Application
import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.zIndex

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.pixel.intelligentsearch.core.data.SettingsManager

val LocalSettingsViewModel = staticCompositionLocalOf<SettingsViewModel?> {
    null
}

val LocalSettingsState = staticCompositionLocalOf<com.pixel.intelligentsearch.core.data.IntelligentSearchSettings?> {
    null
}

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
    CompositionLocalProvider(
        LocalSettingsViewModel provides viewModel,
        LocalSettingsState provides settingsState
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

    Scaffold(
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

                var nightMode by rememberStringPreference(prefs, "night.mode", "System")
                val hasMaterialYou = true
                val darkOption = "Material Dark"
                val lightOption = "Material Light"
                SettingsDropdownRow(
                    title = "Style",
                    subtitle = "System, $darkOption, or $lightOption",
                    icon = Icons.Default.DarkMode,
                    options = listOf("System", darkOption, lightOption),
                    selectedOption = nightMode,
                    onOptionSelected = { 
                        nightMode = it
                        val intent = android.content.Intent(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
                            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE

                            val ids = android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetIds(
                                android.content.ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java)
                            )
                            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    },
                    showDivider = true
                )

                var widgetThemeStyle by rememberStringPreference(prefs, "widget.theme.style", "System Default")
                SettingsDropdownRow(
                    title = "Widget Theme Style",
                    subtitle = "Matches Android 'Icons' setting automatically",
                    icon = Icons.Default.Palette,
                    options = listOf("System Default", "Material You (Minimal)", "Google App (Default)"),
                    selectedOption = widgetThemeStyle,
                    onOptionSelected = { 
                        widgetThemeStyle = it
                        updateWidgets(context)
                    },
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
    }
}

// -----------------------------------------------------------------------------------------
// SEARCH SOURCES SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourcesScreen(prefs: SharedPreferences, onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
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
    Scaffold(
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
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
    icon: ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
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
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(id = optionIcons[option]!!),
                                        contentDescription = option,
                                        modifier = Modifier.size(24.dp),
                                        tint = androidx.compose.ui.graphics.Color.Unspecified
                                    )
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
    icon: Any?,
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
            containerColor = androidx.compose.ui.graphics.Color(0xFF1E1B24).copy(alpha = 0.6f)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
            val breathingTransition = rememberInfiniteTransition(label = "breathing")
            val breathingScale by breathingTransition.animateFloat(
                initialValue = 0.95f, targetValue = 1.05f,
                animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "breathingScale"
            )
            val breathingAlpha by breathingTransition.animateFloat(
                initialValue = 0.1f, targetValue = 0.4f,
                animationSpec = infiniteRepeatable(animation = tween(2500, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "breathingAlpha"
            )

            // Luminous Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = breathingAlpha), androidx.compose.ui.graphics.Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Wrapper to apply scale to widget only without resizing container
                Box(modifier = Modifier.graphicsLayer { scaleX = breathingScale; scaleY = breathingScale }) {
                    // Fake Widget Preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(32.dp))
                            .padding(horizontal = 16.dp)
                    ) {
                        Image(
                            imageVector = ImageVector.vectorResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
                            contentDescription = "G Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            WidgetCustomizationCard {
                var showVoice by rememberBooleanPreference(prefs, "widget_show_voice", true) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show voice search icon in the widget",
                    icon = Icons.Default.Mic,
                    isChecked = showVoice,
                    onCheckedChange = { showVoice = it },
                    showDivider = true
                )
                var actionIcon by rememberStringPreference(prefs, "widget_action_icon", "Search")
                SettingsDropdownRow(
                    title = "Widget Action Icon",
                    subtitle = actionIcon,
                    icon = Icons.Default.Search,
                    options = listOf("Search", "Gemini", "Now Playing"),
                    selectedOption = actionIcon,
                    onOptionSelected = {
                        actionIcon = it
                        updateWidgets(context)
                    },
                    showDivider = true,
                    optionIcons = mapOf(
                        "Search" to com.pixel.intelligentsearch.R.drawable.ic_search_ai_colored,
                        "Gemini" to com.pixel.intelligentsearch.R.drawable.ic_gemini,
                        "Now Playing" to com.pixel.intelligentsearch.R.drawable.ic_music
                    )
                )
                var widgetShortcut by rememberStringPreference(prefs, "widget_shortcut", "None")
                SettingsRow(
                    title = "Widget Shortcut",
                    subtitle = widgetShortcut,
                    icon = Icons.Default.AddCircleOutline,
                    onClick = { showShortcutSheet = true },
                    showDivider = false
                )

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
                                            if (option.first == "Google Lens" || option.first == "Translate (camera)") {
                                                val isInstalled = try {
                                                    context.packageManager.getPackageInfo("com.google.ar.lens", 0)
                                                    true
                                                } catch (e: Exception) {
                                                    false
                                                }
                                                if (!isInstalled) {
                                                    try {
                                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.ar.lens")))
                                                    } catch (e: Exception) {
                                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.lens")))
                                                    }
                                                    showShortcutSheet = false
                                                    return@bouncyClickable
                                                }
                                            }
                                            widgetShortcut = option.first
                                            showShortcutSheet = false
                                            updateWidgets(context)
                                        }.padding(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    if (widgetShortcut == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(24.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = option.second,
                                                contentDescription = option.first,
                                                tint = if (widgetShortcut == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (isCustomizable) {
                                            Box {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.bouncyClickable { expandedDropdownFor = option.first }) {
                                                    Text(
                                                        text = option.first,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Customize", modifier = Modifier.size(16.dp))
                                                }
                                                androidx.compose.material3.DropdownMenu(
                                                    expanded = expandedDropdownFor == option.first,
                                                    onDismissRequest = { expandedDropdownFor = null },
                                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                ) {
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text("Default (Google)") },
                                                        onClick = {
                                                            prefs.edit().putString("${option.first}_custom_type", "default").apply()
                                                            expandedDropdownFor = null
                                                            widgetShortcut = option.first
                                                            showShortcutSheet = false
                                                            updateWidgets(context)
                                                        }
                                                    )
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text("Custom Website (URL)") },
                                                        onClick = {
                                                            val currentVal = prefs.getString("${option.first}_custom_value", "") ?: ""
                                                            customInputValue = if (prefs.getString("${option.first}_custom_type", "") == "url") currentVal else ""
                                                            showCustomUrlDialogFor = option.first
                                                            expandedDropdownFor = null
                                                        }
                                                    )
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text("Custom App (APK)") },
                                                        onClick = {
                                                            val currentVal = prefs.getString("${option.first}_custom_value", "") ?: ""
                                                            customInputValue = if (prefs.getString("${option.first}_custom_type", "") == "app") currentVal else ""
                                                            showCustomAppDialogFor = option.first
                                                            expandedDropdownFor = null
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = option.first,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                
                if (showCustomUrlDialogFor != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showCustomUrlDialogFor = null },
                        title = { Text("Custom URL for $showCustomUrlDialogFor") },
                        text = {
                            androidx.compose.material3.OutlinedTextField(
                                value = customInputValue,
                                onValueChange = { customInputValue = it },
                                label = { Text("Enter full URL (https://...)") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                prefs.edit()
                                    .putString("${showCustomUrlDialogFor}_custom_type", "url")
                                    .putString("${showCustomUrlDialogFor}_custom_value", customInputValue)
                                    .apply()
                                widgetShortcut = showCustomUrlDialogFor!!
                                showShortcutSheet = false
                                showCustomUrlDialogFor = null
                                updateWidgets(context)
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
                        title = { Text("Custom App for $showCustomAppDialogFor") },
                        text = {
                            androidx.compose.material3.OutlinedTextField(
                                value = customInputValue,
                                onValueChange = { customInputValue = it },
                                label = { Text("Enter Package Name (e.g. com.example.app)") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                prefs.edit()
                                    .putString("${showCustomAppDialogFor}_custom_type", "app")
                                    .putString("${showCustomAppDialogFor}_custom_value", customInputValue)
                                    .apply()
                                widgetShortcut = showCustomAppDialogFor!!
                                showShortcutSheet = false
                                showCustomAppDialogFor = null
                                updateWidgets(context)
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
}

fun updateWidgets(context: Context) {
    val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
    val ids = appWidgetManager.getAppWidgetIds(
        android.content.ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java)
    )
    if (ids != null && ids.isNotEmpty()) {
        val provider = com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider()
        provider.onUpdate(context, appWidgetManager, ids)
    }
}

@Composable
fun Android17Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0
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
    Scaffold(
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
    }
}



