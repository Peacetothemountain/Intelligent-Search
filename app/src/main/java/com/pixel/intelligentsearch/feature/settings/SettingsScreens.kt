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
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.AppItem
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
import org.intellij.lang.annotations.Language
import android.graphics.RuntimeShader
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ShaderBrush
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

@Language("AGSL")
private const val GEMINI_STARDUST_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform half4 targetColor;

    // Artifact-free hash using 3-component mixing (no periodic banding)
    float hash21(float2 p) {
        float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
        p3 += dot(p3, float3(p3.y, p3.z, p3.x) + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    // Value noise: smooth interpolation between hash values
    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution.xy;
        uv.y = 1.0 - uv.y;

        // 1. FLOWING LIGHT — two overlapping soft glows sweep side-to-side
        float sweep1 = 0.5 + 0.48 * sin(time * 0.35);
        float sweep2 = 0.5 + 0.38 * sin(time * 0.5 + 2.1);
        float glow1 = smoothstep(0.62, 0.0, abs(uv.x - sweep1));
        float glow2 = smoothstep(0.48, 0.0, abs(uv.x - sweep2)) * 0.55;
        // Ground the light hard to the bottom
        float lightAlpha = (glow1 + glow2) * smoothstep(0.42, 0.0, uv.y);
        // Keep base glow at the original budget
        float baseGlow = lightAlpha * 0.10;

        // 2. NOISE-DISTORTED DOT GRID
        float2 noiseCoord = uv * 3.5 + float2(time * 0.08, time * 0.05);
        float nx = noise(noiseCoord) * 2.0 - 1.0;
        float ny = noise(noiseCoord + float2(5.3, 9.1)) * 2.0 - 1.0;
        float2 distortedUv = uv + float2(nx, ny) * 0.009;

        // Dense dot grid — fixed uniform radius (no size-variation noise that caused the artifact bar)
        float2 gridUv = distortedUv * float2(72.0, 40.0);
        float2 cellCenter = floor(gridUv) + 0.5;
        float dist = length(gridUv - cellCenter);
        float dotAlpha = 1.0 - smoothstep(0.24, 0.30, dist);

        // Dim the light so it's not overpowering
        float dimLight = lightAlpha * 0.55;

        // 3. SPATIAL COLOR SEPARATION — no overflow possible:
        //    Pixels INSIDE a dot  → pure Material You color, lit by the light
        //    Pixels BETWEEN dots  → pure white glow (dimmed), lit by the light
        //    Pixels with NO light → fully transparent (invisible)
        float dotLight      = dotAlpha * dimLight;                  // Material You channel
        float interDotLight = (1.0 - dotAlpha) * dimLight * 0.15;  // white glow channel (much dimmer)
        float totalAlpha    = clamp(dotLight + interDotLight, 0.0, 1.0);

        half3 glowColor = half3(1.0, 1.0, 1.0);
        half3 dotColor  = targetColor.rgb;
        // Color is spatially determined by whether this pixel is inside a dot or not
        half3 finalColor = mix(glowColor, dotColor, dotAlpha);
        return half4(finalColor, targetColor.a * totalAlpha);
    }


"""

@Composable
fun GeminiBackgroundLayer(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { android.graphics.RuntimeShader(GEMINI_STARDUST_SHADER) }
        var time by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            var lastFrame = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                androidx.compose.runtime.withFrameNanos { frameTime ->
                    time += (frameTime - lastFrame) / 1_000_000_000f
                    lastFrame = frameTime
                }
            }
        }
        androidx.compose.foundation.Canvas(modifier = modifier) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("targetColor", color.red, color.green, color.blue, color.alpha)
            drawRect(brush = androidx.compose.ui.graphics.ShaderBrush(shader))
        }
    }
}

// Allows the animation state to persist seamlessly across all settings pages!

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
    val viewModel: SettingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    
    val animationTime by androidx.compose.runtime.produceState(0L) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { value = it }
        }
    }

    CompositionLocalProvider(
        LocalSettingsViewModel provides viewModel,
        LocalSettingsState provides settingsState,
        LocalAnimationTime provides animationTime
    ) {
        val navController = androidx.navigation.compose.rememberNavController()
        
        val exoPlayer = androidx.compose.runtime.remember {
            val uri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.pixel.intelligentsearch.R.raw.bugdroid_video)
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

            val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showTutorial) Modifier.blur(24.dp) else Modifier)
            ) {
                
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }) + androidx.compose.animation.fadeIn() },
                    exitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it / 3 }) + androidx.compose.animation.fadeOut() },
                    popEnterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it / 3 }) + androidx.compose.animation.fadeIn() },
                    popExitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }) + androidx.compose.animation.fadeOut() }
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
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                icon = Icons.Outlined.Warning,
                isChecked = forceTutorial,
                onCheckedChange = { forceTutorial = it }
            )
            
            SettingsRow(
                title = "Reset Tutorial Progress",
                subtitle = "Mark tutorial as incomplete and restart step guide",
                icon = Icons.Outlined.Refresh,
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
                icon = Icons.Outlined.Delete,
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
                icon = Icons.Outlined.HourglassEmpty,
                isChecked = simulateLatency,
                onCheckedChange = { simulateLatency = it },
                showDivider = true
            )
            
            var mockLargeDataset by rememberBooleanPreference(prefs, "debug.mock_large_dataset", false)
            SettingsRowToggle(
                title = "Mock Large Dataset",
                subtitle = "Injects 50 mock contacts and files into search lists",
                icon = Icons.Outlined.Layers,
                isChecked = mockLargeDataset,
                onCheckedChange = { mockLargeDataset = it },
                showDivider = true
            )
            
            var verboseLogging by rememberBooleanPreference(prefs, "debug.verbose_logging", false)
            SettingsRowToggle(
                title = "Enable Verbose Logging",
                subtitle = "Print query logs and load frame times in Logcat",
                icon = Icons.Outlined.Code,
                isChecked = verboseLogging,
                onCheckedChange = { verboseLogging = it },
                showDivider = true
            )
            

            var showPerfStats by rememberBooleanPreference(prefs, "debug.show_perf_stats", false)
            SettingsRowToggle(
                title = "Show Performance HUD",
                subtitle = "Render search latency and results count at the top of overlay",
                icon = Icons.Outlined.Speed,
                isChecked = showPerfStats,
                onCheckedChange = { showPerfStats = it },
                showDivider = true
            )
            
            var mockZeroState by rememberBooleanPreference(prefs, "debug.mock_zero_state", false)
            SettingsRowToggle(
                title = "Mock Trending Queries",
                subtitle = "Force mock trending query topics when the search bar is empty",
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                isChecked = mockZeroState,
                onCheckedChange = { mockZeroState = it },
                showDivider = true
            )
            
            var forceSearchError by rememberBooleanPreference(prefs, "debug.force_search_error", false)
            SettingsRowToggle(
                title = "Force Search API Error",
                subtitle = "Simulate suggestion fetch failure and display error banner",
                icon = Icons.Outlined.BugReport,
                isChecked = forceSearchError,
                onCheckedChange = { forceSearchError = it },
                showDivider = true
            )
            
            SettingsRow(
                title = "Disable Debug Mode",
                subtitle = "Turn off developer settings and exit",
                icon = Icons.Outlined.Close,
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                    title = "Apperence",
                    subtitle = "Theme, Wallpaper, Material Design layouts.",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Appearance) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Shortcuts",
                    subtitle = "Apps, Contacts, Files, Ect.",
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchSources) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Behavior",
                    subtitle = "Custom Search Over Display Settings.",
                    icon = Icons.Outlined.Settings,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Widget Custimization",
                    subtitle = "Custimize widget colors, themes, and Actions.",
                    icon = Icons.Outlined.Widgets,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Launch Portal",
                    subtitle = "Quick Search Tile and App Shortcuts",
                    icon = Icons.AutoMirrored.Outlined.Launch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal) },
                    showDivider = false,
                )
            }

            SettingsCard {
                SettingsRow(
                    title = "Default Digital Assistant",
                    subtitle = "Mange Android Assistant Settings.",
                    icon = Icons.Outlined.Assistant,
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
                    title = "Google Activity",
                    subtitle = "View and mange your Google Activity.",
                    icon = Icons.Outlined.History,
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
                    subtitle = "View your Chrome/Webpage History.",
                    icon = Icons.Outlined.HistoryEdu,
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
                        icon = Icons.Outlined.BugReport,
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
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Apperence", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val defaultThemeValue = if (isSystemDark) "Material Dark" else "Material Light"
                var rawThemeMode by rememberStringPreference(prefs, "night.mode", defaultThemeValue)
                val themeMode = if (rawThemeMode == "System") "Material Dark" else rawThemeMode
                SettingsDropdownRow(
                    title = "App Theme",
                    subtitle = themeMode,
                    icon = Icons.Outlined.BrightnessMedium,
                    options = listOf("Material Dark", "Material Light"),
                    selectedOption = themeMode,
                    onOptionSelected = { rawThemeMode = it },
                    showDivider = true
                )

                var enableSearchOverlay by rememberBooleanPreference(prefs, "search_overlay_enabled", true) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Enable Search Overlay Page",
                    subtitle = "If off, widget opens native Google Search app directly.",
                    icon = Icons.Outlined.Layers,
                    isChecked = enableSearchOverlay,
                    onCheckedChange = { enableSearchOverlay = it },
                    showDivider = true
                )

                var matrixAnimationEnabled by rememberBooleanPreference(prefs, "matrix_animation_enabled", false) {}
                SettingsRowToggle(
                    title = "Enable Matrix Animation on Search Overlay Page",
                    subtitle = "Enable Search Overpay Page Animation.",
                    icon = Icons.Outlined.AutoAwesome,
                    isChecked = matrixAnimationEnabled,
                    onCheckedChange = { matrixAnimationEnabled = it },
                    showDivider = true
                )

                var morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
                SettingsRowToggle(
                    title = "Enable Material Morph Animation",
                    subtitle = "Enable Material Expressive Boucing Shapes.",
                    icon = Icons.Outlined.Animation,
                    isChecked = morphAnimationEnabled,
                    onCheckedChange = { morphAnimationEnabled = it },
                    showDivider = true
                )


                
                var showWall by rememberBooleanPreference(prefs, "search.background.show.wall", false) { updateWidgets(context) }
                SettingsRowToggle(
                    title = "Show Wallpaper",
                    subtitle = "Show User's wallpaper in Search Overlay Page.",
                    icon = Icons.Outlined.Wallpaper,
                    isChecked = showWall,
                    onCheckedChange = { showWall = it },
                    showDivider = true
                )
                
                var blur by rememberIntPreference(prefs, "search.background.blur", 50) { updateWidgets(context) }
                var transparency by rememberIntPreference(prefs, "search.background.transparency", 50) { updateWidgets(context) }
                var pillOpacity by rememberIntPreference(prefs, "search.pill.opacity", 50) { updateWidgets(context) }

                SettingsSliderRow(
                    title = "Background Blur",
                    value = blur.toFloat(),
                    onValueChange = { blur = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Outlined.BlurOn,
                    showDivider = true,
                    steps = 9
                )
                
                SettingsSliderRow(
                    title = "Background Transparency",
                    value = transparency.toFloat(),
                    onValueChange = { transparency = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Outlined.Opacity,
                    showDivider = true,
                    steps = 9
                )
                
                SettingsSliderRow(
                    title = "Search Pill Opacity",
                    value = pillOpacity.toFloat(),
                    onValueChange = { pillOpacity = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Outlined.BrightnessMedium,
                    showDivider = false,
                    steps = 9
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
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Search Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    subtitle = "Search Installed Applications.",
                    icon = Icons.Outlined.Apps,
                    isChecked = searchApps,
                    onCheckedChange = { searchApps = it },
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.AppSearch) },
                    showDivider = true,

                )
                var searchWeb by rememberBooleanPreference(prefs, "search.web", false)
                SettingsRowToggle(
                    title = "Web",
                    subtitle = "View Search Suggestions from Websites",
                    icon = Icons.Outlined.Language,
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
                    subtitle = "Search Contacts.",
                    icon = Icons.Outlined.Contacts,
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
                    subtitle = "Search Local Files.",
                    icon = Icons.Outlined.Folder,
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
                    subtitle = "Calculate Mathamatical Equations Inside Search Bar.",
                    icon = Icons.Outlined.Calculate,
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
                    subtitle = "Show Calendar Events.",
                    icon = Icons.Outlined.Event,
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
    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apps = SystemDataProvider.getAllApps(context).sortedBy { it.name }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            title = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Manage Hidden Apps", 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSearching,
                        enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.End) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.End) + androidx.compose.animation.fadeOut(),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        androidx.compose.material3.Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search apps...", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 14.sp
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { 
                    if (isSearching) {
                        isSearching = false
                        searchQuery = ""
                    } else {
                        isSearching = true 
                    }
                }) {
                    Icon(if (isSearching) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = "Search")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                val filteredApps = if (searchQuery.isBlank()) {
                    installedApps
                } else {
                    installedApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
                }

                items(
                    count = filteredApps.size,
                    key = { index -> filteredApps[index].packageName }
                ) { index ->
                    val app = filteredApps[index]
                    val isHidden = hiddenApps.contains(app.packageName)
                    
                    SettingsRowToggle(
                        title = app.name,
                        subtitle = app.packageName,
                        icon = app.icon,
                        isChecked = isHidden,
                        onCheckedChange = { hide ->
                            val newSet = hiddenApps.toMutableSet()
                            if (hide) newSet.add(app.packageName) else newSet.remove(app.packageName)
                            hiddenApps = newSet
                            prefs.edit().putStringSet("hidden_apps", newSet).apply()
                            viewModel?.updateSetting(SettingsManager.HIDDEN_APPS, newSet)
                        },
                        showDivider = index < filteredApps.size - 1
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
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("App & Shortcut Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    subtitle = "Custimize Quick Launch Apps in the Search Overlay Screen.",
                    icon = Icons.Outlined.ViewCarousel,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchPills) },
                    showDivider = true
                )
                SettingsRow(
                    title = "Manage Hidden Apps",
                    subtitle = "Search Apps to Dynamically Hide From Search.",
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps) },
                    showDivider = true
                )
                var fuzzySearch by rememberBooleanPreference(prefs, "app.fuzzy.search", false)
                SettingsRowToggle(
                    title = "Fuzzy Search",
                    subtitle = "Allow Typos When Searching for Apps.",
                    icon = Icons.Outlined.Spellcheck,
                    isChecked = fuzzySearch,
                    onCheckedChange = { fuzzySearch = it },
                    showDivider = true
                )
                var appAnimation by rememberBooleanPreference(prefs, "app.animation", true)
                SettingsRowToggle(
                    title = "App Animations",
                    subtitle = "Use Dynamic Animations When Launching Apps from Search Overlay Page.",
                    icon = Icons.Outlined.Animation,
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
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    isChecked = searchShortcuts,
                    onCheckedChange = { searchShortcuts = it },
                    showDivider = true
                )
                
                var recentShortcuts by rememberBooleanPreference(prefs, "shortcut.recent", true)
                SettingsRowToggle(
                    title = "Include Recent Shortcuts",
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
                    Text("Max Shortcuts Suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Web Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    icon = Icons.Outlined.Search,
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
                    icon = Icons.Outlined.ChatBubbleOutline,
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
    
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Contact Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    icon = Icons.Outlined.Contacts,
                    onClick = { launcher.launch(null) },
                    showDivider = true
                )
                var directCall by rememberBooleanPreference(prefs, "contact_direct_call", false)
                SettingsRowToggle(
                    title = "Direct Call",
                    subtitle = "Tap Contact to Call Directly.",
                    icon = Icons.Outlined.Call,
                    isChecked = directCall,
                    onCheckedChange = { directCall = it },
                    showDivider = true
                )
                
                var contactResultsCount by rememberIntPreference(prefs, "contact_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Contact Results: $contactResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("File Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    icon = Icons.Outlined.FolderOpen,
                    onClick = { launcher.launch(null) },
                    showDivider = true
                )
                var hiddenFiles by rememberBooleanPreference(prefs, "search.files.hidden.files", false)
                SettingsRowToggle(
                    title = "Show Hidden Files",
                    subtitle = "Include Files Hidden from Android Index.",
                    icon = Icons.Outlined.Visibility,
                    isChecked = hiddenFiles,
                    onCheckedChange = { hiddenFiles = it },
                    showDivider = true
                )
                var thumbnails by rememberBooleanPreference(prefs, "search.files.thumbnails", true)
                SettingsRowToggle(
                    title = "Show Thumbnails",
                    subtitle = "Show Images and Video Thumbnails.",
                    icon = Icons.Outlined.Image,
                    isChecked = thumbnails,
                    onCheckedChange = { thumbnails = it },
                    showDivider = true
                )
                
                var fileResultsCount by rememberIntPreference(prefs, "file_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("File Results: $fileResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Search Behavior", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    subtitle = "Position Search Bar at Bottom of Search Overlay Page.",
                    icon = Icons.Outlined.VerticalAlignBottom,
                    isChecked = bottomSearch,
                    onCheckedChange = { bottomSearch = it },
                    showDivider = true
                )
                var bottomResult by rememberBooleanPreference(prefs, "settings.bottom.search.result", false)
                SettingsRowToggle(
                    title = "Bottom Search Results",
                    subtitle = "Order List from Bottom Up depending on Search Bar Placement.",
                    icon = Icons.Outlined.AlignVerticalBottom,
                    isChecked = bottomResult,
                    onCheckedChange = { bottomResult = it },
                    showDivider = true
                )
                                var compactList by rememberBooleanPreference(prefs, "quick.search.horizontal", false)
                SettingsRowToggle(
                    title = "Quick App Pannel",
                    subtitle = "Quickly Launch Apps Slected from Search Sorce Apps.",
                    icon = Icons.Outlined.ViewCompact,
                    isChecked = compactList,
                    onCheckedChange = { compactList = it },
                    showDivider = true
                )
                var contextAwareApps by rememberBooleanPreference(prefs, "context_aware_quick_apps", false)
                SettingsRowToggle(
                    title = "Context Aware Quick Apps",
                    subtitle = "Dynamic chosen Apps Based on User's App Opening Cycles.",
                    icon = Icons.Outlined.AccessTime,
                    isChecked = contextAwareApps,
                    onCheckedChange = { contextAwareApps = it },
                    showDivider = true
                )
                var smartClipboard by rememberBooleanPreference(prefs, "smart_clipboard_suggestions", false)
                var showClipboardWarning by remember { mutableStateOf(false) }

                if (showClipboardWarning) {
                    AlertDialog(
                        onDismissRequest = { showClipboardWarning = false },
                        title = { Text("Privacy Warning", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = { Text("Smart Clipboard requires a foreground lifecycle observer to monitor your clipboard due to Android 10+ restrictions. Do you want to enable this feature?", style = MaterialTheme.typography.bodyLarge) },
                        confirmButton = {
                            TextButton(onClick = {
                                smartClipboard = true
                                showClipboardWarning = false
                            }) {
                                Text("Enable", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClipboardWarning = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }

                SettingsRowToggle(
                    title = "Smart Clipboard Suggestions",
                    subtitle = "Suggestion Actions Based on Clipboard Text. I.E. Open Photos, Open Music Player, Ect.",
                    icon = Icons.Outlined.ContentPaste,
                    isChecked = smartClipboard,
                    onCheckedChange = { 
                        if (it) {
                            showClipboardWarning = true
                        } else {
                            smartClipboard = false
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
    subtitle: String? = null,
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
                if (subtitle != null) {
                    Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
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
    showDivider: Boolean,
    steps: Int = 0
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
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    steps = steps
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
                androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
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

    var localThemeStyle by remember { mutableStateOf(prefs.getString("widget.theme.style", "System Default") ?: "System Default") }
    var localSubtheme by remember { mutableStateOf(prefs.getString("widget_subtheme", "System") ?: "System") }
    var localMaterialGIconTheme by remember { mutableStateOf(prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon") }
    var localHue by remember { mutableStateOf(prefs.getInt("widget_custom_hue", 277).toFloat()) }
    var localSaturation by remember { mutableStateOf(prefs.getInt("widget_custom_saturation", 51).toFloat()) }
    var localLightness by remember { mutableStateOf(prefs.getInt("widget_custom_lightness", 100).toFloat()) }
    var localCustomColorInt by remember { mutableStateOf(prefs.getInt("widget_custom_color_int", android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, localLightness / 100f)))) }
    var localOpacity by remember { mutableStateOf(prefs.getInt("widget.background.transparency", 28).toFloat()) }
    var localLockBlack by remember { mutableStateOf(prefs.getBoolean("widget_material_lock_black", true)) }
    var localShowVoice by remember { mutableStateOf(prefs.getBoolean("widget_show_voice", true)) }
    var localActionIcon by remember { mutableStateOf(prefs.getString("widget_action_icon", "Search") ?: "Search") }
    var localShortcut1 by remember { mutableStateOf(prefs.getString("widget_shortcut_1", prefs.getString("widget_shortcut", "Google Lens")) ?: "Google Lens") }
    var localShortcut2 by remember { mutableStateOf(prefs.getString("widget_shortcut_2", "None") ?: "None") }
    var localShortcut3 by remember { mutableStateOf(prefs.getString("widget_shortcut_3", "None") ?: "None") }
    var activeShortcutSlot by remember { mutableIntStateOf(1) }

    var isInitialSetup by remember { mutableStateOf(true) }

    LaunchedEffect(
        localShowGIcon, localThemeStyle, localSubtheme,
        localMaterialGIconTheme, localHue, localSaturation, localLightness, localOpacity, localLockBlack,
        localShowVoice, localActionIcon, localShortcut1, localShortcut2, localShortcut3, localCustomColorInt
    ) {
        if (isInitialSetup) {
            isInitialSetup = false
            return@LaunchedEffect
        }
        prefs.edit()
            .putBoolean("widget_show_g_icon", localShowGIcon)

            .putString("widget.theme.style", localThemeStyle)
            .putString("widget_subtheme", localSubtheme)
            .putString("widget_material_g_icon", localMaterialGIconTheme)
            .putInt("widget_custom_hue", localHue.toInt())
            .putInt("widget_custom_saturation", localSaturation.toInt())
            .putInt("widget_custom_lightness", localLightness.toInt())
            .putInt("widget_custom_color_int", localCustomColorInt)
            .putInt("widget.background.transparency", localOpacity.toInt())
            .putBoolean("widget_material_lock_black", localLockBlack)
            .putBoolean("widget_show_voice", localShowVoice)
            .putString("widget_action_icon", localActionIcon)
            .putString("widget_shortcut_1", localShortcut1)
            .putString("widget_shortcut_2", localShortcut2)
            .putString("widget_shortcut_3", localShortcut3)
            .putString("widget_shortcut", localShortcut1)
            .apply()
        updateWidgets(context)
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val view = androidx.compose.ui.platform.LocalView.current
                    androidx.compose.material3.TextButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        localShowGIcon = true

                        localThemeStyle = "Material Design"
                        localSubtheme = "System"
                        localMaterialGIconTheme = "Material G Icon"
                        localHue = 277f
                        localSaturation = 51f
                        localLightness = 100f
                        localCustomColorInt = android.graphics.Color.HSVToColor(floatArrayOf(277f, 0.51f, 1f))
                        localOpacity = 28f
                        localLockBlack = true
                        localShowVoice = true
                        localActionIcon = "Search"
                        localShortcut1 = "Google Lens"
                        localShortcut2 = "None"
                        localShortcut3 = "None"
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        android.widget.Toast.makeText(context, "Settings Saved", android.widget.Toast.LENGTH_SHORT).show()
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                    val alphaInt = (255 * (100 - localOpacity.toInt()) / 100).coerceIn(0, 255) / 255f
                    val accentColor = if (localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.Color(localCustomColorInt)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)"
                    val matPrimary = MaterialTheme.colorScheme.primary
                    val matSecondary = MaterialTheme.colorScheme.secondary
                    val matTertiary = MaterialTheme.colorScheme.tertiary
                    val matError = MaterialTheme.colorScheme.error
                    val matSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    val activeColor = remember(previewIsMaterialYou, localSubtheme, localCustomColorInt, matPrimary, matSurfaceVariant, alphaInt) {
                        if (localSubtheme == "Custom") {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(localCustomColorInt, hsv)
                            androidx.compose.ui.graphics.Color(
                                android.graphics.Color.HSVToColor(
                                    255, 
                                    floatArrayOf(hsv[0], hsv[1], 1f)
                                )
                            )
                        } else if (!previewIsMaterialYou) {
                            matSurfaceVariant
                        } else {
                            matPrimary
                        }
                    }

                    GeminiBackgroundLayer(
                        color = activeColor,
                        modifier = Modifier.fillMaxSize()
                    )

                    val previewRimColorAlpha = if (previewIsMaterialYou) {
                        if (localSubtheme == "Custom") {
                            accentColor.copy(alpha = alphaInt)
                        } else if (localSubtheme == "Material") {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            androidx.compose.ui.graphics.Color(0xFF6C63FF)
                        }
                    } else androidx.compose.ui.graphics.Color.Transparent
                    
                    val finalPreviewIconTint = if (localMaterialGIconTheme == "Accented G Icon") {
                        accentColor
                    } else if (previewIsMaterialYou && localMaterialGIconTheme == "Material G Icon") {
                        androidx.compose.ui.graphics.Color.Unspecified
                    } else if (previewIsMaterialYou) {
                        androidx.compose.ui.graphics.Color.White
                    } else {
                        val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
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
                        previewIconTint
                    }
                    
                    val rimBrush = if (previewIsMaterialYou && localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = alphaInt))
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(previewRimColorAlpha, previewRimColorAlpha))
                    }
                    
                    val previewPillColorAlpha = if (previewIsMaterialYou) {
                        if (localLockBlack) {
                            androidx.compose.ui.graphics.Color(0xFF121212)
                        } else {
                            accentColor.copy(alpha = alphaInt)
                        }
                    } else {
                        when (localSubtheme) {
                            "Light" -> androidx.compose.ui.graphics.Color(0xFFF8F9FA)
                            "Dark" -> androidx.compose.ui.graphics.Color(0xFF303134)
                            "Custom" -> accentColor.copy(alpha = alphaInt)
                            else -> if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF303134) else androidx.compose.ui.graphics.Color(0xFFF8F9FA)
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
                                val gIconTint = when (localMaterialGIconTheme) {
                                    "System G Icon" -> androidx.compose.ui.graphics.Color.Unspecified
                                    "Accented G Icon" -> accentColor
                                    "Material G Icon" -> {
                                        if (previewIsMaterialYou) androidx.compose.ui.graphics.Color.Unspecified else finalPreviewIconTint
                                    }
                                    else -> androidx.compose.ui.graphics.Color.Unspecified
                                }
                                val useOriginalGIcon = if (previewIsMaterialYou) {
                                    localMaterialGIconTheme == "System G Icon"
                                } else {
                                    if (localSubtheme == "Custom") {
                                        localMaterialGIconTheme == "System G Icon"
                                    } else {
                                        localSubtheme != "System"
                                    }
                                }
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
                            
                            val activeShortcuts = listOf(localShortcut1, localShortcut2, localShortcut3).filter { it != "None" }
                            activeShortcuts.forEachIndexed { idx, sc ->
                                val shortcutRes = when (sc) {
                                    "Live" -> com.pixel.intelligentsearch.R.drawable.ic_gemini
                                    "Translate (text)" -> com.pixel.intelligentsearch.R.drawable.ic_translate
                                    "Translate (camera)" -> com.pixel.intelligentsearch.R.drawable.ic_document_scanner
                                    "Weather" -> com.pixel.intelligentsearch.R.drawable.ic_weather
                                    "Sports" -> com.pixel.intelligentsearch.R.drawable.ic_sports
                                    "Dictionary" -> com.pixel.intelligentsearch.R.drawable.ic_dictionary
                                    "Homework" -> com.pixel.intelligentsearch.R.drawable.ic_homework
                                    "Finance" -> com.pixel.intelligentsearch.R.drawable.ic_finance
                                    "Saved" -> com.pixel.intelligentsearch.R.drawable.ic_saved
                                    "News" -> com.pixel.intelligentsearch.R.drawable.ic_news
                                    else -> com.pixel.intelligentsearch.R.drawable.ic_camera
                                }
                                if (idx > 0) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = shortcutRes),
                                    contentDescription = sc,
                                    tint = finalPreviewIconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (activeShortcuts.isNotEmpty() && localShowVoice) {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            if (localShowVoice) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_mic),
                                    contentDescription = "Mic",
                                    modifier = Modifier.size(24.dp),
                                    tint = finalPreviewIconTint
                                )
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
                                    tint = finalPreviewIconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
            }

            // G Icon options
            SettingsCard {
                SettingsRowToggle(
                    title = "Display G Icon",
                    subtitle = "Show Google Logo on Search Bar Widget.",
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
                            ComposeGIcon(
                                modifier = Modifier.size(24.dp),
                                primaryColor = MaterialTheme.colorScheme.primary,
                                secondaryColor = MaterialTheme.colorScheme.secondary,
                                tertiaryColor = MaterialTheme.colorScheme.tertiary,
                                isAccented = false,
                                useOriginalColors = false
                            )
                        }
                    },
                    isChecked = localShowGIcon,
                    onCheckedChange = { localShowGIcon = it },
                    showDivider = true
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
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) { localThemeStyle = "System Default"; localSubtheme = "System" },
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
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) { localThemeStyle = "Material You (Minimal)"; localSubtheme = "Material" },
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
                        val dynColor = androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(((100 - localOpacity) * 2.55f).toInt(), floatArrayOf(localHue, localSaturation / 100f, 1f)))
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
                            } else if (opt == "Custom") {
                                androidx.compose.ui.graphics.Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                        }
                    }
                } else {
                    val matOpts = listOf("Material", "Custom")
                    matOpts.forEach { opt ->
                        val isSel = localSubtheme == opt
                        val dynColor = androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(((100 - localOpacity) * 2.55f).toInt(), floatArrayOf(localHue, localSaturation / 100f, 1f)))
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
                            } else if (opt == "Custom") {
                                androidx.compose.ui.graphics.Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(opt, style = MaterialTheme.typography.labelMedium, color = textColor)
                        }
                    }
                }
            }

            if (localSubtheme == "Custom") {
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
                            Icon(Icons.Default.Palette, contentDescription = "Hue", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Hue", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localHue.toInt()}%", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
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
                            showTrack = false,
                            value = localHue,
                                        onValueChange = { 
                                            localHue = it 
                                            localCustomColorInt = android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, localLightness / 100f))
                                        },
                                        valueRange = 0f..360f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Saturation
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Saturation", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
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
                            showTrack = false,
                            value = localSaturation,
                                        onValueChange = { 
                                            localSaturation = it 
                                            localCustomColorInt = android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, localLightness / 100f))
                                        },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Opacity
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Contrast, contentDescription = "Opacity", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Opacity", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${localOpacity.toInt()}%", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White)
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
                        val customColorHex = String.format("#%06X", (0xFFFFFF and localCustomColorInt))
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
                        
                        if (localThemeStyle == "Material You (Minimal)") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { localLockBlack = !localLockBlack }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Material Design Inner Pill and Circle Color State", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Force Inner Pill and Circle to be Hex #121212", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                androidx.compose.material3.Switch(
                                    checked = localLockBlack,
                                    onCheckedChange = { localLockBlack = it },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Text("WIDGET ACTIONS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

            // Actions Card
            SettingsCard {
                SettingsRowToggle(
                    title = "Voice Search Icon",
                    subtitle = "Show Voice Search Icon on Search Bar Widget.",
                    icon = Icons.Default.Mic,
                    isChecked = localShowVoice,
                    onCheckedChange = { localShowVoice = it },
                    showDivider = true
                )
                val customSat = localSaturation / 100f
                val alphaInt = (255 * (100 - localOpacity.toInt()) / 100).coerceIn(0, 255) / 255f
                val dynamicAccentColor = if (localSubtheme == "Custom") {
                    androidx.compose.ui.graphics.Color(localCustomColorInt)
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val isMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                if (isMaterialYou) {
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
                }
                Text("WIDGET SHORTCUTS (UP TO 3)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
                
                var draggingShortcutSlot by remember { mutableStateOf<Int?>(null) }
                var shortcutDragOffset by remember { mutableFloatStateOf(0f) }
                val coroutineScope = rememberCoroutineScope()
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val slotsList = listOf(1, 2, 3)
                    slotsList.forEach { slotNum ->
                        val currentVal = when (slotNum) {
                            1 -> localShortcut1
                            2 -> localShortcut2
                            else -> localShortcut3
                        }
                        val isDragging = draggingShortcutSlot == slotNum
                        
                        val swipeOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }

                        val cardModifier = if (isDragging) {
                            Modifier
                                .zIndex(2f)
                                .graphicsLayer { translationY = shortcutDragOffset }
                        } else {
                            Modifier.graphicsLayer { translationX = swipeOffsetX.value }
                        }

                        Surface(
                            modifier = cardModifier
                                .fillMaxWidth()
                                .pointerInput(slotNum, isDragging) {
                                    if (!isDragging) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    if (kotlin.math.abs(swipeOffsetX.value) > 200f) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        when (slotNum) {
                                                            1 -> localShortcut1 = "None"
                                                            2 -> localShortcut2 = "None"
                                                            3 -> localShortcut3 = "None"
                                                        }
                                                    }
                                                    swipeOffsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = androidx.compose.animation.core.spring(
                                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    swipeOffsetX.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = androidx.compose.animation.core.spring(
                                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            },
                                            onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount)
                                                }
                                            }
                                        )
                                    }
                                },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = if (isDragging) 8.dp else 2.dp,
                            shadowElevation = if (isDragging) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeShortcutSlot = slotNum; showShortcutSheet = true }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val iconVector = shortcutOptions.find { it.first == currentVal }?.second ?: Icons.Default.Close
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = currentVal,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (currentVal == "None") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Shortcut Slot: $slotNum",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentVal,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(24.dp)
                                        .pointerInput(slotNum) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    draggingShortcutSlot = slotNum
                                                    shortcutDragOffset = 0f
                                                },
                                                onDragEnd = {
                                                    draggingShortcutSlot = null
                                                    shortcutDragOffset = 0f
                                                },
                                                onDragCancel = {
                                                    draggingShortcutSlot = null
                                                    shortcutDragOffset = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    shortcutDragOffset += dragAmount
                                                    val currentDragSlot = draggingShortcutSlot ?: return@detectVerticalDragGestures
                                                    
                                                    if (shortcutDragOffset > 120f) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        if (currentDragSlot == 1) {
                                                            val temp = localShortcut1
                                                            localShortcut1 = localShortcut2
                                                            localShortcut2 = temp
                                                            draggingShortcutSlot = 2
                                                            shortcutDragOffset = 0f
                                                        } else if (currentDragSlot == 2) {
                                                            val temp = localShortcut2
                                                            localShortcut2 = localShortcut3
                                                            localShortcut3 = temp
                                                            draggingShortcutSlot = 3
                                                            shortcutDragOffset = 0f
                                                        }
                                                    } else if (shortcutDragOffset < -120f) {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                        if (currentDragSlot == 3) {
                                                            val temp = localShortcut3
                                                            localShortcut3 = localShortcut2
                                                            localShortcut2 = temp
                                                            draggingShortcutSlot = 2
                                                            shortcutDragOffset = 0f
                                                        } else if (currentDragSlot == 2) {
                                                            val temp = localShortcut2
                                                            localShortcut2 = localShortcut1
                                                            localShortcut1 = temp
                                                            draggingShortcutSlot = 1
                                                            shortcutDragOffset = 0f
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
                                            when (activeShortcutSlot) {
                                                1 -> localShortcut1 = option.first
                                                2 -> localShortcut2 = option.first
                                                3 -> localShortcut3 = option.first
                                            }
                                            showShortcutSheet = false
                                        }
                                    }
                                ) {
                                    val currentShortcutSlotVal = when (activeShortcutSlot) {
                                        1 -> localShortcut1
                                        2 -> localShortcut2
                                        else -> localShortcut3
                                    }
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = option.second,
                                            contentDescription = option.first,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(
                                                    if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .padding(16.dp),
                                            tint = if (currentShortcutSlotVal == option.first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                                        when (activeShortcutSlot) {
                                                            1 -> localShortcut1 = option.first
                                                            2 -> localShortcut2 = option.first
                                                            3 -> localShortcut3 = option.first
                                                        }
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
                                val color = android.graphics.Color.parseColor(if (tempHexInput.startsWith("#")) tempHexInput else "#$tempHexInput")
                                localCustomColorInt = color
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color, hsv)
                                localHue = hsv[0]
                                localSaturation = hsv[1] * 100
                                localLightness = hsv[2] * 100
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
                            when (activeShortcutSlot) {
                                1 -> localShortcut1 = showCustomUrlDialogFor!!
                                2 -> localShortcut2 = showCustomUrlDialogFor!!
                                3 -> localShortcut3 = showCustomUrlDialogFor!!
                            }
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
                            when (activeShortcutSlot) {
                                1 -> localShortcut1 = showCustomAppDialogFor!!
                                2 -> localShortcut2 = showCustomAppDialogFor!!
                                3 -> localShortcut3 = showCustomAppDialogFor!!
                            }
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

    Scaffold(containerColor = Color.Transparent, topBar = {
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
                        Icon(if (showAppPicker && multiSelectMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Outlined.Check, contentDescription = "Add Selected")
                        }
                    } else if (!showAppPicker) {
                        IconButton(onClick = { showAppPicker = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Pill")
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
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
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
                                packageInfo.applicationInfo?.loadIcon(context.packageManager) ?: Icons.Outlined.Apps
                            } catch (e: Exception) {
                                Icons.Outlined.Apps
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
                                    positionalThreshold = { it * 0.5f }
                                )
                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart || dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                        val currentList = localPillList.toMutableList()
                                        currentList.remove(packageName)
                                        localPillList = currentList
                                        searchPills = currentList.joinToString(",")
                                        viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, currentList.size)
                                        prefs.edit().putInt("shortcut_results_count", currentList.size).apply()
                                    }
                                }

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
                                            imageVector = Icons.Outlined.DragHandle,
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
        val morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Launch Portal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
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
                    icon = Icons.Outlined.ViewAgenda,
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
                    icon = Icons.Outlined.AddHome,
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
    
    // Pre-shuffle starting configurations to ensure a different opening animation every time
    val startXs = listOf(0.15f, 0.40f, 0.65f, 0.85f).shuffled()
    val startFromBottom = listOf(true, true, false, false).shuffled()

    val bouncers = List(4) { BouncerState(it, this, startXs[it], startFromBottom[it]) }
}

class BouncerState(val index: Int, val engine: MorphAnimationEngine, val startX: Float, val startFromBottom: Boolean) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    
    val rotation = Animatable(0f)
    val morphProgress = Animatable(0f)
    val alpha = Animatable(1f)
    
    var morph by mutableStateOf(
        engine.shapePool.shuffled().let { Morph(it[0], it[1]) }
    )

    var vx = 0f
    var vy = 0f
    var boundsWidth = 0f
    var boundsHeight = 0f
    var deviceWidth = 0f
    var deviceHeight = 0f
    var sizePx = 0f
    var mass = 1f
    
    fun updateBounds(width: Float, height: Float, dWidth: Float, dHeight: Float) {
        boundsWidth = width
        boundsHeight = height
        deviceWidth = dWidth
        deviceHeight = dHeight
        // "None are smaller though. Some are a medium size bigger."
        // Base is 0.04f. Multipliers: 1.0, 1.2, 1.4, 1.6
        val sizeMultiplier = 1.0f + (index % 4) * 0.2f
        sizePx = dWidth * 0.04f * sizeMultiplier
        // Mass scales with 2D area (radius squared)
        mass = sizeMultiplier * sizeMultiplier
    }

    init {
        engine.coroutineScope.launch {
            rotation.animateTo(360f, infiniteRepeatable(tween(8000, easing = LinearEasing)))
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
            
            while (boundsWidth == 0f || boundsHeight == 0f) {
                delay(16)
            }
            
            
            // Initial positioning
            x = boundsWidth * startX
            
            // Physics constraints based on the real device screen size
            val gravity = deviceHeight * 0.8f 
            val restitution = 0.82f // realistic bouncy ball
            val wallRestitution = 0.85f
            val airDragCoeff = 0.8f // scales air resistance
            val groundFrictionCoeff = 2.5f // scales friction when touching the floor

            if (startFromBottom) {
                y = boundsHeight - sizePx
                // Cap the velocity so it reaches a height inside the bounds, but uses device physics
                // v = sqrt(2 * g * h) where h is the desired apex height relative to bounds
                val targetApex = boundsHeight * (0.6f + Math.random().toFloat() * 0.3f)
                vy = -kotlin.math.sqrt(2f * gravity * targetApex)
                // Shoot from bottom and arc immediately
                vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.1f + Math.random().toFloat() * deviceWidth * 0.15f)
            } else {
                y = 0f
                vy = 0f
                // Fall straight down initially
                vx = 0f
            }
            
            var timeSinceSettled = 0f
            
            var lastTime = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val currentTime = androidx.compose.runtime.withFrameNanos { it }
                val dt = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime
                
                val safeDt = minOf(dt, 0.05f)
                
                // Acceleration = Gravity - (Drag / Mass) * Velocity
                val ax = -(airDragCoeff / mass) * vx
                val ay = gravity - (airDragCoeff / mass) * vy
                
                vx += ax * safeDt
                vy += ay * safeDt
                
                x += vx * safeDt
                y += vy * safeDt
                
                var touchingGround = false
                
                val maxY = boundsHeight - sizePx
                val minY = sizePx // ceiling
                if (y >= maxY) {
                    y = maxY
                    touchingGround = true
                    if (vy > 0) {
                        vy = -vy * restitution
                        // Prevent micro-vibrations going infinitely (Zeno's paradox for physics engines)
                        if (kotlin.math.abs(vy) < 15f) {
                            vy = 0f
                        } else if (vx == 0f) {
                            // "bounces before arcing away": kick horizontal velocity on the first ground impact
                            vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                        }
                    }
                } else if (y <= minY) {
                    y = minY
                    if (vy < 0) {
                        vy = -vy * restitution
                    }
                }
                
                val minX = sizePx
                val maxX = boundsWidth - sizePx
                if (x <= minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                    }
                } else if (x >= maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                    }
                }
                
                // Apply ground friction if it's on the ground
                if (touchingGround) {
                    val frictionDrag = groundFrictionCoeff * mass * gravity
                    // apply friction opposing velocity
                    if (vx > 0) {
                        vx -= frictionDrag * safeDt / mass
                        if (vx < 0) vx = 0f
                    } else if (vx < 0) {
                        vx += frictionDrag * safeDt / mass
                        if (vx > 0) vx = 0f
                    }
                }
                
                // Continuous Animation: Relaunch shapes if they settle on the ground for too long
                // This ensures they animate correctly as long as the user remains inside the setting pages
                val isSettled = touchingGround && vy == 0f && kotlin.math.abs(vx) < 5f
                if (isSettled) {
                    timeSinceSettled += safeDt
                    // Launch again after resting for a short 0.5 to 1.5 seconds
                    if (timeSinceSettled > 0.5f + Math.random().toFloat()) {
                        val targetApex = boundsHeight * (0.4f + Math.random().toFloat() * 0.4f)
                        vy = -kotlin.math.sqrt(2f * gravity * targetApex)
                        vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                        timeSinceSettled = 0f
                    }
                } else {
                    timeSinceSettled = 0f
                }
            }
        }
    }
}

@Composable
fun MaterialMorphAnimation(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { MorphAnimationEngine(coroutineScope) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val deviceWidth = configuration.screenWidthDp * density.density
    val deviceHeight = configuration.screenHeightDp * density.density

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Update synchronously during composition to guarantee zero frame delay
        engine.bouncers.forEach { it.updateBounds(width, height, deviceWidth, deviceHeight) }

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












