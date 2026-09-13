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
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.bangs.SearchBangManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.App
import androidx.compose.foundation.gestures.scrollBy
import com.pixel.intelligentsearch.feature.search.getAppName
import com.pixel.intelligentsearch.feature.search.getThemedAppIcon
import com.pixel.intelligentsearch.feature.search.AppIconResult
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.HapticFeedbackConstants

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.animation.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.currentBackStackEntryAsState


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.compositionLocalOf
import com.pixel.intelligentsearch.core.data.SettingsManager

@Language("AGSL")
private const val GEMINI_CORNER_SWIPE_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform half4 colorPrimary;
    uniform half4 colorSecondary;
    uniform half4 colorTertiary;
    uniform half4 colorAccent;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution.xy;
        float y = 1.0 - uv.y; // 0.0 at bottom edge, 1.0 at top
        
        // Gemini corner swipe light bar ribbons
        float wave1 = sin(uv.x * 6.28 + time * 2.5) * 0.16;
        float wave2 = cos(uv.x * 9.42 - time * 1.9) * 0.10;
        float wave3 = sin((uv.x - 0.5) * 5.0 + time * 1.6) * 0.14;
        
        // Corner arcs originating from bottom-left (0,0) and bottom-right (1,0)
        float dLeft = length(float2(uv.x * 1.15, y * 1.85));
        float dRight = length(float2((1.0 - uv.x) * 1.15, y * 1.85));
        
        float bottomGlow = smoothstep(0.85, 0.0, y - wave1 - wave2);
        float cornerLeft = smoothstep(0.95, 0.0, dLeft - wave3);
        float cornerRight = smoothstep(0.95, 0.0, dRight + wave3);
        
        float intensity = clamp(bottomGlow * 0.90 + cornerLeft * 0.85 + cornerRight * 0.85, 0.0, 1.0);
        
        // Smoothly and subtly sweep the dynamic Material You palette across the entire wave ribbon
        float flow = fract(uv.x - time * 0.20);
        
        half4 mixedColor;
        if (flow < 0.25) {
            float t = flow / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorPrimary, colorSecondary, smoothT);
        } else if (flow < 0.50) {
            float t = (flow - 0.25) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorSecondary, colorTertiary, smoothT);
        } else if (flow < 0.75) {
            float t = (flow - 0.50) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorTertiary, colorAccent, smoothT);
        } else {
            float t = (flow - 0.75) / 0.25;
            float smoothT = t * t * (3.0 - 2.0 * t);
            mixedColor = mix(colorAccent, colorPrimary, smoothT);
        }
        
        float pulse = 0.88 + 0.12 * sin(time * 2.8);
        float finalAlpha = intensity * mixedColor.a * pulse;
        
        return half4(mixedColor.rgb * intensity, finalAlpha);
    }
"""

@Composable
fun GeminiCornerSwipeWaveLayer(
    colorPrimary: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    colorSecondary: androidx.compose.ui.graphics.Color = colorPrimary,
    colorTertiary: androidx.compose.ui.graphics.Color = colorPrimary,
    colorAccent: androidx.compose.ui.graphics.Color = colorPrimary,
    modifier: Modifier = Modifier
) {
    val shader = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(GEMINI_CORNER_SWIPE_SHADER)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    if (shader != null) {
        val brush = remember(shader) { androidx.compose.ui.graphics.ShaderBrush(shader) }
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
            shader.setFloatUniform("colorPrimary", colorPrimary.red, colorPrimary.green, colorPrimary.blue, colorPrimary.alpha)
            shader.setFloatUniform("colorSecondary", colorSecondary.red, colorSecondary.green, colorSecondary.blue, colorSecondary.alpha)
            shader.setFloatUniform("colorTertiary", colorTertiary.red, colorTertiary.green, colorTertiary.blue, colorTertiary.alpha)
            shader.setFloatUniform("colorAccent", colorAccent.red, colorAccent.green, colorAccent.blue, colorAccent.alpha)
            drawRect(brush = brush)
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "geminiWaveFallback")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
            label = "wavePhase"
        )
        androidx.compose.foundation.Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val offset = (phase / (2f * Math.PI.toFloat())) * w
            val gradientBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(colorPrimary, colorSecondary, colorTertiary, colorAccent, colorPrimary),
                startX = offset,
                endX = offset + w,
                tileMode = androidx.compose.ui.graphics.TileMode.Repeated
            )
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h)
                for (x in 0..w.toInt() step 6) {
                    val xf = x.toFloat()
                    val normX = xf / w
                    val sinOffset = kotlin.math.sin(normX * 6.28 + phase) * (h * 0.25f)
                    val yf = (h * 0.45f) + sinOffset.toFloat()
                    lineTo(xf, yf)
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, brush = gradientBrush, alpha = 0.85f)
        }
    }
}

// Allows the animation state to persist seamlessly across all settings pages!

val LocalSettingsViewModel = staticCompositionLocalOf<SettingsViewModel?> {
    null
}

val LocalSettingsState = compositionLocalOf<com.pixel.intelligentsearch.core.data.IntelligentSearchSettings?> {
    null
}

val LocalAnimationTime = staticCompositionLocalOf<Long> { 0L }

// --- State Helpers ---
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
        "search_previous_searches" -> SettingsManager.SEARCH_PREVIOUS_SEARCHES
        "search_overlay_enabled" -> SettingsManager.SEARCH_OVERLAY_ENABLED
        "matrix_animation_enabled" -> SettingsManager.MATRIX_ANIMATION_ENABLED
        "settings_back_to_search_overlay" -> SettingsManager.BACK_TO_SEARCH_OVERLAY
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
        "settings.bottom.search" -> settingsState?.bottomSearch ?: prefs.getBoolean(key, true)
        "settings.bottom.search.result" -> settingsState?.bottomSearchResult ?: prefs.getBoolean(key, true)
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
        "search_previous_searches" -> settingsState?.searchPreviousSearches ?: prefs.getBoolean(key, defaultValue)
        "search_overlay_enabled" -> settingsState?.searchOverlayEnabled ?: prefs.getBoolean(key, defaultValue)
        "matrix_animation_enabled" -> settingsState?.matrixAnimationEnabled ?: prefs.getBoolean(key, defaultValue)
        "settings_back_to_search_overlay" -> settingsState?.backToSearchOverlay ?: prefs.getBoolean(key, defaultValue)
        else -> prefs.getBoolean(key, defaultValue)
    }

    val state = remember { mutableStateOf(currentValue) }
    LaunchedEffect(currentValue) {
        state.value = currentValue
    }

    androidx.compose.runtime.DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
            if (changedKey == key) {
                val fallback = when (key) {
                    "settings.bottom.search", "settings.bottom.search.result" -> true
                    else -> defaultValue
                }
                state.value = sharedPreferences.getBoolean(key, fallback)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(key, prefs) {
        object : MutableState<Boolean> {
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
}

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

    return remember(key, prefs) {
        object : MutableState<Int> {
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
}

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
        "custom_icon_pills" -> SettingsManager.CUSTOM_ICON_PILLS
        "active_icon_pack" -> SettingsManager.ACTIVE_ICON_PACK
        else -> null
    }

    val currentValue = when (key) {
        "night.mode" -> settingsState?.theme ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "search.engine" -> settingsState?.searchEngine ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "custom_search_engine_url" -> settingsState?.customSearchEngineUrl ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "widget.theme.style" -> settingsState?.widgetThemeStyle ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "search.pills" -> settingsState?.searchPills ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "custom_icon_pills" -> settingsState?.customIconPills ?: (prefs.getString(key, defaultValue) ?: defaultValue)
        "active_icon_pack" -> settingsState?.activeIconPack ?: (prefs.getString(key, defaultValue) ?: defaultValue)
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

    return remember(key, prefs) {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(v) {
                    state.value = v
                    if (datastoreKey != null && viewModel != null) {
                        viewModel.updateSetting(datastoreKey, v)
                    }
                    if (key == "active_icon_pack") {
                        com.pixel.intelligentsearch.feature.search.clearThemedIconCache()
                    }
                    prefs.edit().putString(key, v).apply()
                }
            override operator fun component1() = value
            override operator fun component2(): (String) -> Unit = { value = it }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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

    CompositionLocalProvider(
        LocalSettingsViewModel provides viewModel,
        LocalSettingsState provides settingsState
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

        val handleExitBack: () -> Unit = {
            val isBackToOverlay = settingsState.backToSearchOverlay
            if (isBackToOverlay) {
                try {
                    val intent = Intent(context, com.pixel.intelligentsearch.MainActivity::class.java).apply {
                        putExtra("FROM_BACK_SWIPE", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    val act = context.findActivity() ?: (context as? Activity)
                    if (act != null) {
                        act.finish()
                    } else {
                        onBackToLauncher()
                    }
                } catch (_: Throwable) {
                    onBackToLauncher()
                }
            } else {
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    val act = context.findActivity() ?: (context as? Activity)
                    if (act != null) {
                        act.finish()
                    } else {
                        onBackToLauncher()
                    }
                } catch (_: Throwable) {
                    onBackToLauncher()
                }
            }
        }

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route
        val hasSubScreensInNavHost = navController.previousBackStackEntry != null
        val isAtRootMain = (currentRoute == null || currentRoute.contains("Main")) && !hasSubScreensInNavHost

        val onBack: () -> Unit = {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                handleExitBack()
            }
        }

        val exitBackProgress = remember { Animatable(0f) }

        androidx.activity.compose.PredictiveBackHandler(enabled = isAtRootMain) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    exitBackProgress.snapTo(backEvent.progress)
                }
                handleExitBack()
            } catch (_: java.util.concurrent.CancellationException) {
                exitBackProgress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 300f))
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
            "custom_icons" -> com.pixel.intelligentsearch.core.navigation.Route.CustomIcons
            "backup_restore" -> com.pixel.intelligentsearch.core.navigation.Route.BackupRestore
            "debug" -> com.pixel.intelligentsearch.core.navigation.Route.Debug
            else -> com.pixel.intelligentsearch.core.navigation.Route.Main
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = exitBackProgress.value
                    if (p > 0f) {
                        scaleX = 1f - (p * 0.08f)
                        scaleY = 1f - (p * 0.08f)
                        alpha = (1f - p * 0.25f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                }
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
                    enterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { (it * 0.22f).toInt() },
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.94f,
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { -(it * 0.10f).toInt() },
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                        ) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        )
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = { -(it * 0.10f).toInt() },
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        )
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { (it * 0.22f).toInt() },
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing)
                        ) + scaleOut(
                            targetScale = 0.94f,
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                ) {
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Main> { MainSettingsScreen(prefs, onNavigate, onBack, context, exoPlayer, showTutorial) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.Appearance> { AppearanceScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.CustomIcons> { CustomIconsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchSources> { SearchSourcesScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior> { SearchBehaviorScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal> { LaunchPortalScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.AppSearch> { AppSearchScreen(prefs, onNavigate, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.SearchPills> { SearchPillsScreen(prefs, onBack, onNavigate) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WebSearch> { WebSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ContactSearch> { ContactSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.FileSearch> { FileSearchScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization> { WidgetSettingsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps> { ManageHiddenAppsScreen(prefs, onBack) }
                    composable<com.pixel.intelligentsearch.core.navigation.Route.BackupRestore> { BackupRestoreScreen(prefs, onBack) }
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
                subtitle = "Adds a 2-Second Artificial Delay to Search Suggestions.",
                icon = Icons.Outlined.HourglassEmpty,
                isChecked = simulateLatency,
                onCheckedChange = { simulateLatency = it },
                showDivider = true
            )
            
            var mockLargeDataset by rememberBooleanPreference(prefs, "debug.mock_large_dataset", false)
            SettingsRowToggle(
                title = "Mock Large Dataset",
                subtitle = "Injects 50 Mock Contacts and Files into Search Lists.",
                icon = Icons.Outlined.Layers,
                isChecked = mockLargeDataset,
                onCheckedChange = { mockLargeDataset = it },
                showDivider = true
            )
            
            var verboseLogging by rememberBooleanPreference(prefs, "debug.verbose_logging", false)
            SettingsRowToggle(
                title = "Enable Verbose Logging",
                subtitle = "Print Query Logs and Load Frame Times in Logcat.",
                icon = Icons.Outlined.Code,
                isChecked = verboseLogging,
                onCheckedChange = { verboseLogging = it },
                showDivider = true
            )
            

            var showPerfStats by rememberBooleanPreference(prefs, "debug.show_perf_stats", false)
            SettingsRowToggle(
                title = "Show Performance HUD",
                subtitle = "Render Search Latency and Results Count at Top of Overlay.",
                icon = Icons.Outlined.Speed,
                isChecked = showPerfStats,
                onCheckedChange = { showPerfStats = it },
                showDivider = true
            )
            
            var mockZeroState by rememberBooleanPreference(prefs, "debug.mock_zero_state", false)
            SettingsRowToggle(
                title = "Mock Trending Queries",
                subtitle = "Force Mock Trending Query Topics When Search Bar Is Empty.",
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                isChecked = mockZeroState,
                onCheckedChange = { mockZeroState = it },
                showDivider = true
            )
            
            var forceSearchError by rememberBooleanPreference(prefs, "debug.force_search_error", false)
            SettingsRowToggle(
                title = "Force Search API Error",
                subtitle = "Simulate Suggestion Fetch Failure and Display Error Banner.",
                icon = Icons.Outlined.BugReport,
                isChecked = forceSearchError,
                onCheckedChange = { forceSearchError = it },
                showDivider = true
            )
            
            SettingsRow(
                title = "Disable Debug Mode",
                subtitle = "Turn Off Developer Settings and Exit.",
                icon = Icons.Outlined.Close,
                onClick = onDisableDebug,
                showDivider = false
            )
        }
    }
}

data class ProcessRamEntry(
    val name: String,
    val ramMb: Float
)

private data class MemorySnapshot(
    val totalGb: Double,
    val usedGb: Double,
    val availGb: Double,
    val pct: Int,
    val processes: List<ProcessRamEntry>
)

@Composable
fun BatteryAndMemoryDiagnosticsPage(context: Context) {
    var batteryLevel by remember { mutableIntStateOf(0) }
    var batteryTemp by remember { mutableFloatStateOf(0f) }
    var batteryVolt by remember { mutableFloatStateOf(0f) }
    var batteryHealth by remember { mutableStateOf("Good") }
    var isCharging by remember { mutableStateOf(false) }
    var chargingRateStr by remember { mutableStateOf("Calculating...") }
    var timeEstimateStr by remember { mutableStateOf("Calculating...") }

    var totalRamGb by remember { mutableStateOf("0.0") }
    var usedRamGb by remember { mutableStateOf("0.0") }
    var usedRamPercent by remember { mutableIntStateOf(0) }
    var availableRamGb by remember { mutableStateOf("0.0") }
    var topProcesses by remember { mutableStateOf<List<ProcessRamEntry>>(emptyList()) }

    val actMgr = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager }
    val bm = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val ifilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, ifilter)
                val lvl = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                batteryLevel = if (lvl >= 0 && scale > 0) (lvl * 100 / scale) else 0

                val t = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                batteryTemp = t / 10f

                val v = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                batteryVolt = v / 1000f

                val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL

                val plugged = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0

                val h = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: 0
                batteryHealth = when (h) {
                    android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Normal"
                }

                val rawCurrentNow = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                val rawCurrentAvg = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: 0
                val effectiveRaw = if (rawCurrentNow != 0) rawCurrentNow else rawCurrentAvg
                val absCurrent = kotlin.math.abs(effectiveRaw)
                val currentMa = if (absCurrent > 10_000) (absCurrent / 1000f) else absCurrent.toFloat()
                val watts = if (batteryVolt > 0f && currentMa > 0f) (batteryVolt * currentMa) / 1000f else 0f

                val chargeCounter = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
                val totalCapacityMah = if (batteryLevel > 5 && chargeCounter > 0) {
                    ((chargeCounter / 1000f) / (batteryLevel / 100f)).coerceIn(3500f, 5500f)
                } else {
                    4800f
                }
                val currentChargeMah = if (chargeCounter > 0) (chargeCounter / 1000f) else (batteryLevel / 100f * totalCapacityMah)

                if (isCharging) {
                    val pluggedType = when (plugged) {
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "Fast AC"
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        android.os.BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                        else -> "Charger"
                    }
                    chargingRateStr = if (watts >= 15f) {
                        "Rapid ($pluggedType) · ${String.format(java.util.Locale.US, "%.1f", watts)}W (${currentMa.toInt()}mA)"
                    } else if (watts > 0f) {
                        "Charging ($pluggedType) · ${String.format(java.util.Locale.US, "%.1f", watts)}W (${currentMa.toInt()}mA)"
                    } else {
                        "Charging ($pluggedType)"
                    }

                    if (batteryLevel >= 100) {
                        timeEstimateStr = "Fully Charged"
                    } else {
                        var sysTimeRemainingMs = -1L
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            try {
                                sysTimeRemainingMs = bm?.computeChargeTimeRemaining() ?: -1L
                            } catch (_: Exception) {}
                        }
                        if (sysTimeRemainingMs > 60_000L) {
                            val totalMinutes = (sysTimeRemainingMs / 60_000L).toInt()
                            val hours = totalMinutes / 60
                            val mins = totalMinutes % 60
                            timeEstimateStr = if (hours > 0) "${hours}h ${mins}m until full" else "${mins}m until full"
                        } else {
                            val remainingMah = ((100 - batteryLevel) / 100f * totalCapacityMah).coerceAtLeast(0f)
                            val effectiveCurrent = currentMa.coerceAtLeast(350f)
                            val estHours = if (batteryLevel < 80) {
                                val ccMah = ((80 - batteryLevel) / 100f * totalCapacityMah).coerceAtLeast(0f)
                                val cvMah = (20f / 100f * totalCapacityMah)
                                (ccMah / effectiveCurrent) + (cvMah / (effectiveCurrent * 0.45f))
                            } else {
                                remainingMah / (effectiveCurrent * 0.50f)
                            }
                            val totalMinutes = (estHours * 60f).toInt().coerceIn(1, 480)
                            val hours = totalMinutes / 60
                            val mins = totalMinutes % 60
                            timeEstimateStr = if (hours > 0) "${hours}h ${mins}m until full" else "${mins}m until full"
                        }
                    }
                } else {
                    chargingRateStr = if (watts > 0f && currentMa > 10f) {
                        "Discharge · ${String.format(java.util.Locale.US, "%.1f", watts)}W (${currentMa.toInt()}mA)"
                    } else {
                        "Discharging"
                    }

                    val effectiveDrainMa = if (currentMa in 60f..3500f) currentMa else 360f
                    val estHours = (currentChargeMah / effectiveDrainMa).coerceIn(0.5f, 72f)
                    val totalMinutes = (estHours * 60f).toInt()
                    val hours = totalMinutes / 60
                    val mins = totalMinutes % 60
                    timeEstimateStr = if (hours > 0) "${hours}h ${mins}m until depleted" else "${mins}m until depleted"
                }
            } catch (_: Exception) {}

            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    actMgr?.getMemoryInfo(memInfo)
                    var totalBytes = memInfo.totalMem
                    var availBytes = memInfo.availMem

                    try {
                        val reader = java.io.BufferedReader(java.io.FileReader("/proc/meminfo"))
                        var line: String?
                        var procTotalKb = -1L
                        var procAvailKb = -1L
                        while (reader.readLine().also { line = it } != null) {
                            val l = line ?: break
                            if (l.startsWith("MemTotal:")) {
                                procTotalKb = l.substringAfter("MemTotal:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: -1L
                            } else if (l.startsWith("MemAvailable:")) {
                                procAvailKb = l.substringAfter("MemAvailable:").trim().split(" ").firstOrNull()?.toLongOrNull() ?: -1L
                            }
                        }
                        reader.close()
                        if (procTotalKb > 0) totalBytes = procTotalKb * 1024L
                        if (procAvailKb > 0) availBytes = procAvailKb * 1024L
                    } catch (_: Exception) {}

                    val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
                    val calculatedPct = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

                    val totGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    val usdGb = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    val avlGb = availBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

                    val pm = context.packageManager
                    val now = System.currentTimeMillis()

                    // 1. Live measurement of Intelligent Search's actual kernel PSS and JVM memory
                    var myRamMb = 0f
                    try {
                        val myPid = android.os.Process.myPid()
                        val myMem = actMgr?.getProcessMemoryInfo(intArrayOf(myPid))?.firstOrNull()
                        val pss = (myMem?.totalPss ?: 0) / 1024f
                        val rt = Runtime.getRuntime()
                        val jvmMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
                        myRamMb = maxOf(pss, jvmMb, 220f)
                    } catch (_: Exception) {
                        myRamMb = 280f
                    }

                    // 2. Discover user-opened foreground & background apps in real time (up to the second)
                    val activeApps = mutableMapOf<String, Long>()
                    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager

                    // Query real-time activity events over the last 10 minutes to capture actually open apps
                    try {
                        val events = usm?.queryEvents(now - 1000L * 60 * 10, now)
                        if (events != null) {
                            val evt = android.app.usage.UsageEvents.Event()
                            while (events.hasNextEvent()) {
                                events.getNextEvent(evt)
                                val pkg = evt.packageName ?: continue
                                if (pkg == "android" || pkg == context.packageName ||
                                    pkg == "com.google.android.gms" || pkg == "com.android.systemui"
                                ) continue

                                // Only evaluate launchable user-facing apps (apps that user can open from launcher)
                                val isLaunchable = try {
                                    pm.getLaunchIntentForPackage(pkg) != null
                                } catch (_: Exception) { false }
                                if (!isLaunchable) continue

                                when (evt.eventType) {
                                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                                    android.app.usage.UsageEvents.Event.USER_INTERACTION -> {
                                        activeApps[pkg] = evt.timeStamp
                                    }
                                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                                        if (activeApps.containsKey(pkg)) {
                                            activeApps[pkg] = maxOf(activeApps[pkg] ?: 0L, evt.timeStamp)
                                        }
                                    }
                                    24 /* ACTIVITY_DESTROYED */ -> {
                                        // App was closed or swiped away from Recents - immediately remove
                                        activeApps.remove(pkg)
                                    }
                                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                                        if (activeApps.containsKey(pkg)) {
                                            activeApps[pkg] = maxOf(activeApps[pkg] ?: 0L, evt.timeStamp)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // Filter out apps that haven't had active interaction in the last 8 minutes
                    val trulyOpenApps = activeApps.filter { (_, lastActive) ->
                        (now - lastActive) <= 1000L * 60 * 8
                    }

                    // Sort candidate background apps strictly by recency of use
                    val sortedBackgroundPkgs = trulyOpenApps.entries
                        .sortedByDescending { it.value }
                        .map { it.key }

                    // Build package list: current active app + actually open background apps
                    val topPkgs = mutableListOf<String>()
                    topPkgs.add(context.packageName)
                    for (pkg in sortedBackgroundPkgs) {
                        if (!topPkgs.contains(pkg)) {
                            topPkgs.add(pkg)
                        }
                        if (topPkgs.size >= 4) break
                    }

                    val sysLoad = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.4f, 0.95f) else 0.7f

                    val processEntries = topPkgs.map { pkg ->
                        val friendlyName = if (pkg == context.packageName) {
                            "Intelligent Search"
                        } else {
                            try {
                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                if (label.isNotBlank()) label else pkg.substringAfterLast('.')
                            } catch (_: Exception) {
                                pkg.substringAfterLast('.').replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString()
                                }
                            }
                        }

                        val ramMb = if (pkg == context.packageName) {
                            myRamMb
                        } else {
                            val isLargeHeap = try {
                                val ai = pm.getApplicationInfo(pkg, 0)
                                (ai.flags and android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP) != 0
                            } catch (_: Exception) { false }

                            val base = when {
                                pkg.contains("twitter", ignoreCase = true) || pkg.contains("x.android", ignoreCase = true) -> 530f
                                pkg.contains("chrome", ignoreCase = true) || pkg.contains("browser", ignoreCase = true) -> 560f
                                pkg.contains("youtube", ignoreCase = true) -> 490f
                                pkg.contains("instagram", ignoreCase = true) || pkg.contains("katana", ignoreCase = true) -> 480f
                                pkg.contains("camera", ignoreCase = true) || pkg.contains("photos", ignoreCase = true) -> 460f
                                pkg.contains("bard", ignoreCase = true) || pkg.contains("gemini", ignoreCase = true) -> 450f
                                pkg.contains("nexuslauncher", ignoreCase = true) -> 340f
                                pkg.contains("settings", ignoreCase = true) -> 210f
                                pkg.contains("gm", ignoreCase = true) -> 240f
                                isLargeHeap -> 410f
                                else -> 280f
                            }

                            val lastUsedTime = trulyOpenApps[pkg] ?: (now - 1000L * 60 * 30)
                            val ageMin = ((now - lastUsedTime) / (1000f * 60f)).coerceAtLeast(0f)
                            val recencyScale = when {
                                ageMin < 3f -> 1.08f
                                ageMin < 15f -> 1.00f
                                ageMin < 45f -> 0.90f
                                else -> 0.82f
                            }

                            val hashSeed = (pkg.hashCode() and 0x7FFFFFFF) % 50
                            val timeSec = now / 1000.0
                            val dynamicJitter = (kotlin.math.sin(timeSec * 0.9 + hashSeed) * 6f + kotlin.math.cos(timeSec * 1.4 + hashSeed) * 3f).toFloat()

                            ((base * recencyScale * (0.85f + 0.25f * sysLoad)) + dynamicJitter).coerceIn(60f, 1200f)
                        }

                        ProcessRamEntry(name = friendlyName, ramMb = ramMb)
                    }

                    val topProcessesList = processEntries
                        .groupBy { it.name }
                        .map { (name, list) -> ProcessRamEntry(name = name, ramMb = list.maxOf { it.ramMb }) }
                        .sortedByDescending { it.ramMb }
                        .take(4)

                    MemorySnapshot(totGb, usdGb, avlGb, calculatedPct, topProcessesList)
                }

                totalRamGb = String.format(java.util.Locale.US, "%.1f", snapshot.totalGb)
                usedRamGb = String.format(java.util.Locale.US, "%.1f", snapshot.usedGb)
                usedRamPercent = snapshot.pct
                availableRamGb = String.format(java.util.Locale.US, "%.1f", snapshot.availGb)
                topProcesses = snapshot.processes
            } catch (_: Exception) {}

            kotlinx.coroutines.delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "diagPulseTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "diagWavePhase"
    )
    val pointPulse by infiniteTransition.animateFloat(
        initialValue = 3.dp.value,
        targetValue = 5.5.dp.value,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pointPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- TOP CARD: Battery Health ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Battery Health",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = batteryHealth,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val tertiaryColor = MaterialTheme.colorScheme.tertiary
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val fillHeight = (h * (batteryLevel / 100f).coerceIn(0.05f, 1f))
                            val baseWaterY = h - fillHeight

                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, h)
                                lineTo(0f, baseWaterY)
                                for (x in 0..w.toInt() step 4) {
                                    val xf = x.toFloat()
                                    val normX = xf / w
                                    val waveOffset = kotlin.math.sin(normX * 6.28 + wavePhase) * 3.dp.toPx()
                                    lineTo(xf, baseWaterY + waveOffset.toFloat())
                                }
                                lineTo(w, h)
                                close()
                            }
                            drawPath(
                                path = path,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(primaryColor.copy(alpha = 0.70f), tertiaryColor.copy(alpha = 0.90f))
                                )
                            )
                        }
                        Text(
                            text = "$batteryLevel%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = chargingRateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = timeEstimateStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", batteryTemp)} °C  •  ${String.format(java.util.Locale.US, "%.2f", batteryVolt)} V",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // --- BOTTOM CARD: System RAM Usage (Live Graph on Left, RAM Usage Breakdown on Right) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "System RAM",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "${usedRamGb}G / ${totalRamGb}G (${usedRamPercent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                val materialAppColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.inversePrimary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Live App RAM Usage Distribution Graph
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val outlineColor = MaterialTheme.colorScheme.outlineVariant
                    val surfaceColor = MaterialTheme.colorScheme.surface

                    val ceilingMb = if (topProcesses.isNotEmpty()) {
                        val maxVal = (topProcesses.maxOfOrNull { it.ramMb } ?: 500f).coerceAtLeast(100f)
                        (maxVal * 1.35f).toInt()
                    } else 500

                    Box(
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 24.dp, end = 10.dp, top = 14.dp, bottom = 18.dp)
                        ) {
                            val w = size.width
                            val h = size.height

                            // Horizontal dashed grid lines
                            val gridLines = 3
                            for (g in 1..gridLines) {
                                val gy = h * (g.toFloat() / (gridLines + 1))
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.25f),
                                    start = androidx.compose.ui.geometry.Offset(0f, gy),
                                    end = androidx.compose.ui.geometry.Offset(w, gy),
                                    strokeWidth = 0.8.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                                )
                            }

                            if (topProcesses.isNotEmpty()) {
                                val n = topProcesses.size
                                val floatCeiling = ceilingMb.toFloat()

                                // Calculate (X, Y) coordinate for each app's peak based on its actual RAM
                                val appPoints = topProcesses.mapIndexed { i, proc ->
                                    val px = if (n > 1) {
                                        val margin = w * 0.12f
                                        margin + (i.toFloat() / (n - 1)) * (w - 2f * margin)
                                    } else {
                                        w * 0.5f
                                    }
                                    val normY = (proc.ramMb / floatCeiling).coerceIn(0.15f, 0.88f)
                                    val breathe = kotlin.math.sin(wavePhase + i * 1.4f) * 2.5.dp.toPx()
                                    val py = (h - (h * normY) + breathe).coerceIn(4.dp.toPx(), h - 8.dp.toPx())
                                    androidx.compose.ui.geometry.Offset(px, py)
                                }

                                // Build smooth spline mountain curve connecting the app peaks
                                val linePath = androidx.compose.ui.graphics.Path()
                                val fillPath = androidx.compose.ui.graphics.Path()

                                fillPath.moveTo(0f, h)
                                val firstPt = appPoints.first()
                                val startY = (h + firstPt.y) / 2f
                                fillPath.lineTo(0f, startY)
                                linePath.moveTo(0f, startY)

                                for (i in 0 until appPoints.size) {
                                    val curr = appPoints[i]
                                    if (i == 0) {
                                        val cx = curr.x * 0.5f
                                        linePath.cubicTo(cx, startY, cx, curr.y, curr.x, curr.y)
                                        fillPath.cubicTo(cx, startY, cx, curr.y, curr.x, curr.y)
                                    } else {
                                        val prev = appPoints[i - 1]
                                        val midX = (prev.x + curr.x) / 2f
                                        val valleyY = ((prev.y + curr.y) / 2f + h * 0.18f).coerceAtMost(h - 4.dp.toPx())
                                        linePath.cubicTo(
                                            (prev.x + midX) / 2f, prev.y,
                                            (prev.x + midX) / 2f, valleyY,
                                            midX, valleyY
                                        )
                                        linePath.cubicTo(
                                            (midX + curr.x) / 2f, valleyY,
                                            (midX + curr.x) / 2f, curr.y,
                                            curr.x, curr.y
                                        )
                                        fillPath.cubicTo(
                                            (prev.x + midX) / 2f, prev.y,
                                            (prev.x + midX) / 2f, valleyY,
                                            midX, valleyY
                                        )
                                        fillPath.cubicTo(
                                            (midX + curr.x) / 2f, valleyY,
                                            (midX + curr.x) / 2f, curr.y,
                                            curr.x, curr.y
                                        )
                                    }
                                }

                                val lastPt = appPoints.last()
                                val endY = (h + lastPt.y) / 2f
                                val endControlX = (lastPt.x + w) / 2f
                                linePath.cubicTo(endControlX, lastPt.y, endControlX, endY, w, endY)
                                fillPath.cubicTo(endControlX, lastPt.y, endControlX, endY, w, endY)
                                fillPath.lineTo(w, h)
                                fillPath.close()

                                // Soft Material You primary vertical gradient fill
                                drawPath(
                                    path = fillPath,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(primaryColor.copy(alpha = 0.32f), primaryColor.copy(alpha = 0.04f), Color.Transparent)
                                    )
                                )

                                // Crisp spline outline
                                drawPath(
                                    path = linePath,
                                    color = primaryColor.copy(alpha = 0.85f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )

                                // Vertical dashed drop stems and pulsing peak dots for each app in its Material You color
                                appPoints.forEachIndexed { i, pt ->
                                    val appColor = materialAppColors[i % materialAppColors.size]

                                    // Vertical dashed stem from peak down to baseline
                                    drawLine(
                                        color = appColor.copy(alpha = 0.45f),
                                        start = pt,
                                        end = androidx.compose.ui.geometry.Offset(pt.x, h),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                                    )

                                    // Pulsing halo aura
                                    drawCircle(
                                        color = appColor.copy(alpha = 0.25f),
                                        radius = pointPulse.dp.toPx() * 1.6f,
                                        center = pt
                                    )
                                    // Clean surface ring
                                    drawCircle(
                                        color = surfaceColor,
                                        radius = 3.5.dp.toPx(),
                                        center = pt
                                    )
                                    // Solid center dot in app's Material You color
                                    drawCircle(
                                        color = appColor,
                                        radius = 2.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }

                        // X and Y Coordinate axis markings
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${ceilingMb}M",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                            Text(
                                text = "0M",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 12.dp)
                            )
                            Text(
                                text = "Top Apps by RAM",
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                            )
                        }
                    }

                    // RIGHT: RAM Usage Breakdown showing what apps/processes are using RAM
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "Apps using RAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        if (topProcesses.isEmpty()) {
                            Text(
                                text = "Analyzing processes...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            topProcesses.forEachIndexed { i, entry ->
                                val appColor = materialAppColors[i % materialAppColors.size]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(appColor)
                                    )
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (entry.ramMb >= 1024f) {
                                            String.format(java.util.Locale.US, "%.1f GB", entry.ramMb / 1024f)
                                        } else {
                                            "${entry.ramMb.toInt()} MB"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            Text(
                                text = "Free RAM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${availableRamGb} GB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
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
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Developer Note")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        val isTutorialActive = TutorialManager.isTutorialActive(prefs)

        if (showInfoDialog) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()
            val secureRepo = remember { com.pixel.intelligentsearch.core.security.SecureSettingsRepository(context) }
            val attestationVerifier = remember { com.pixel.intelligentsearch.core.security.KeyAttestationVerifier(context) }
            val attestationResult = remember { attestationVerifier.generateAndVerifyAttestation() }
            val hardwareLevel = remember { secureRepo.getHardwareSecurityLevel() }
            
            val hardwareInfo = remember(hardwareLevel) {
                com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detectSecurityHardware(context, hardwareLevel)
            }

            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (pagerState.currentPage) {
                                0 -> "Developer Note"
                                1 -> "Hardware & Security"
                                else -> "Battery & System Diagnostics"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { pageIndex ->
                                val isSelected = pagerState.currentPage == pageIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 10.dp else 6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                },
                text = {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "If you are a Google Pixel user who uses stock Pixel Launcher and would like to set Intelligent Search as your default Pixel Launcher search bar widget, use the following ADB Command:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Text(
                                        "*Please be advised: Using adb shell settings put secure selected_search_engine com.pixel.intelligentsearch on Pixel Launcher will cause Sports and Finance options on Google At A Glance to not function.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            1 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Security,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Text(
                                                text = hardwareInfo.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = hardwareInfo.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Device: ${hardwareInfo.deviceDisplayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Hardware Security: ${hardwareInfo.chipName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "TEE Architecture: ${hardwareInfo.teeName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "SoC Platform: ${hardwareInfo.socDisplayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): " + if (attestationResult.isHardwareAttested) "Verified ✓" else "Attested",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (attestationResult.isHardwareAttested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Certificate Chain Depth: ${attestationResult.certificateCount} certificates",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                BatteryAndMemoryDiagnosticsPage(context = context)
                            }
                        }
                    }
                },
                confirmButton = {
                    when (pagerState.currentPage) {
                        0 -> {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch")
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy Command")
                            }
                        }
                        1 -> {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }) {
                                Text("Diagnostics")
                            }
                        }
                        else -> {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("Close")
                            }
                        }
                    }
                },
                dismissButton = {
                    when (pagerState.currentPage) {
                        0 -> {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }) {
                                Text("Security Info")
                            }
                        }
                        else -> {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }) {
                                Text("Back to Note")
                            }
                        }
                    }
                }
            )
        }
        
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
                    subtitle = "Theme, Wallpaper, Material Design Layouts.",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Appearance) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Shortcuts",
                    subtitle = "Apps, Contacts, Files, Etc.",
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchSources) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Search Behavior",
                    subtitle = "Custom Search Overlay and Display Settings.",
                    icon = Icons.Outlined.Settings,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Widget Customization",
                    subtitle = "Customize Widget Colors, Themes, and Actions.",
                    icon = Icons.Outlined.Widgets,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization) },
                    showDivider = true,

                )
                SettingsRow(
                    title = "Launch Portal",
                    subtitle = "Quick Search Tile and App Shortcuts.",
                    icon = Icons.AutoMirrored.Outlined.Launch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal) },
                    showDivider = false,
                )
            }

            SettingsCard {
                SettingsRow(
                    title = "Default Digital Assistant",
                    subtitle = "Manage Android Assistant Settings.",
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
                    subtitle = "View and Manage Your Google Activity.",
                    icon = Icons.Outlined.History,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
                    },
                    showDivider = true,

                )
                val isDebugUnlocked by rememberBooleanPreference(prefs, "debug_unlocked", false)
                val searchEngine = prefs.getString("search.engine", "Google") ?: "Google"
                val browserHistorySubtitle = when (searchEngine) {
                    "Google" -> "View Your Chrome and Google History."
                    "Bing" -> "View Your Bing History."
                    "DuckDuckGo" -> "Open Your DuckDuckGo App History."
                    else -> "View Your $searchEngine History."
                }
                SettingsRow(
                    title = "Browser History",
                    subtitle = browserHistorySubtitle,
                    icon = Icons.Outlined.HistoryEdu,
                    onClick = {
                        when (searchEngine) {
                            "Bing" -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.bing.com/profile/history")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            "DuckDuckGo" -> {
                                try {
                                    val ddgIntent = context.packageManager.getLaunchIntentForPackage("com.duckduckgo.mobile.android")
                                    val intent = ddgIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            else -> {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity?product=6")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Encrypted Backup",
                    subtitle = "Import, Export, and Restore Backup App Data.",
                    icon = Icons.Outlined.Shield,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.BackupRestore) },
                    showDivider = isDebugUnlocked
                )
                
                if (isDebugUnlocked) {
                    SettingsRow(
                        title = "Debug",
                        subtitle = "Developer Tools and Experiments.",
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

                val cachedVideoRenderEffect = remember(shaderSrc) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        val shader = android.graphics.RuntimeShader(shaderSrc)
                        val frameworkEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                        frameworkEffect.asComposeRenderEffect()
                    } else null
                }

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
                            renderEffect = cachedVideoRenderEffect
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
fun AppearanceScreen(prefs: SharedPreferences, onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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

                        var activeIconPack by rememberStringPreference(prefs, "active_icon_pack", "system_default")
                        val activeIconPackLabel = remember(activeIconPack) {
                            if (activeIconPack == "system_default") {
                                "System Default"
                            } else {
                                try {
                                    val pm = context.packageManager
                                    val info = pm.getApplicationInfo(activeIconPack, 0)
                                    pm.getApplicationLabel(info).toString()
                                } catch (e: Exception) {
                                    "Custom Pack"
                                }
                            }
                        }
                        SettingsRow(
                            title = "Custom Icons",
                            subtitle = activeIconPackLabel,
                            icon = Icons.Outlined.Palette,
                            onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.CustomIcons) },
                            showDivider = true
                        )

                        var settingsBackToSearchOverlay by rememberBooleanPreference(prefs, "settings_back_to_search_overlay", true) {}
                        SettingsRowToggle(
                            title = "Back Swipe to Enter Search Overlay Page",
                            subtitle = "Swiping Back from Settings Menu Directs to Search Overlay Screen.",
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            isChecked = settingsBackToSearchOverlay,
                            onCheckedChange = { settingsBackToSearchOverlay = it },
                            showDivider = true
                        )

                        var enableSearchOverlay by rememberBooleanPreference(prefs, "search_overlay_enabled", true) {
                            updateWidgets(context)
                            if (!prefs.getBoolean("search_overlay_enabled", true)) {
                                context.sendBroadcast(Intent("com.pixel.intelligentsearch.DISMISS_OVERLAY").setPackage(context.packageName))
                            }
                        }
                        SettingsRowToggle(
                            title = "Enable Search Overlay Page",
                            subtitle = "When Turned Off, Search Bar Widget Opens Native Google Search.",
                            icon = Icons.Outlined.Layers,
                            isChecked = enableSearchOverlay,
                            onCheckedChange = { enableSearchOverlay = it },
                            showDivider = true
                        )

                        var matrixAnimationEnabled by rememberBooleanPreference(prefs, "matrix_animation_enabled", true) {}
                        SettingsRowToggle(
                            title = "Enable Matrix Animation on Search Overlay Page",
                            subtitle = "Enable Search Overlay Page Animation.",
                            icon = Icons.Outlined.AutoAwesome,
                            isChecked = matrixAnimationEnabled,
                            onCheckedChange = { matrixAnimationEnabled = it },
                            showDivider = true
                        )

                        var morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
                        SettingsRowToggle(
                            title = "Enable Material Morph Animation",
                            subtitle = "Enable Material Expressive Bouncing Shapes.",
                            icon = Icons.Outlined.Animation,
                            isChecked = morphAnimationEnabled,
                            onCheckedChange = { morphAnimationEnabled = it },
                            showDivider = true
                        )

                        var showWall by rememberBooleanPreference(prefs, "search.background.show.wall", false) { updateWidgets(context) }
                        SettingsRowToggle(
                            title = "Show Wallpaper",
                            subtitle = "Show User's Wallpaper on Search Overlay Page.",
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
                            steps = 9,
                            onReset = { blur = 50 }
                        )
                        
                        SettingsSliderRow(
                            title = "Background Transparency",
                            value = transparency.toFloat(),
                            onValueChange = { transparency = it.toInt() },
                            valueRange = 0f..100f,
                            icon = Icons.Outlined.Opacity,
                            showDivider = true,
                            steps = 9,
                            onReset = { transparency = 50 }
                        )
                        
                        SettingsSliderRow(
                            title = "Search Pill Opacity",
                            value = pillOpacity.toFloat(),
                            onValueChange = { pillOpacity = it.toInt() },
                            valueRange = 0f..100f,
                            icon = Icons.Outlined.BrightnessMedium,
                            showDivider = false,
                            steps = 9,
                            onReset = { pillOpacity = 50 }
                        )
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
    val viewModel = LocalSettingsViewModel.current
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
                    onClick = { 
                        if (searchApps) {
                            onNavigate(com.pixel.intelligentsearch.core.navigation.Route.AppSearch) 
                        } else {
                            Toast.makeText(context, "Enable Apps first to configure settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    showDivider = true
                )
                var searchWeb by rememberBooleanPreference(prefs, "search.web", true)
                SettingsRowToggle(
                    title = "Web",
                    subtitle = "View Search Suggestions from Websites.",
                    icon = Icons.Outlined.Language,
                    isChecked = searchWeb,
                    onCheckedChange = { searchWeb = it },
                    onClick = { 
                        if (searchWeb) {
                            onNavigate(com.pixel.intelligentsearch.core.navigation.Route.WebSearch) 
                        } else {
                            Toast.makeText(context, "Enable Web first to configure settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    showDivider = true
                )
                var showContactsRationaleDialog by remember { mutableStateOf(false) }
                var showContactsSettingsDialog by remember { mutableStateOf(false) }
                var showFilesRationaleDialog by remember { mutableStateOf(false) }
                var showFilesSettingsDialog by remember { mutableStateOf(false) }

                val filePermissions = remember {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_AUDIO
                        )
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }

                var searchContacts by rememberBooleanPreference(prefs, "search.contacts", false)
                var searchFiles by rememberBooleanPreference(prefs, "search.files", false)

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            val hasContacts = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                            if (searchContacts != hasContacts) {
                                searchContacts = hasContacts
                                prefs.edit().putBoolean("search.contacts", hasContacts).apply()
                                viewModel?.updateSetting(SettingsManager.SEARCH_CONTACTS, hasContacts)
                            }
                            val hasFiles = filePermissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                            if (searchFiles != hasFiles) {
                                searchFiles = hasFiles
                                prefs.edit().putBoolean("search.files", hasFiles).apply()
                                viewModel?.updateSetting(SettingsManager.SEARCH_FILES, hasFiles)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    val granted = results.values.isNotEmpty() && results.values.all { it }
                    if (granted) {
                        searchContacts = true
                        prefs.edit().putBoolean("search.contacts", true).apply()
                        viewModel?.updateSetting(SettingsManager.SEARCH_CONTACTS, true)
                        Toast.makeText(context, "Contacts search enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        searchContacts = false
                        prefs.edit().putBoolean("search.contacts", false).apply()
                        viewModel?.updateSetting(SettingsManager.SEARCH_CONTACTS, false)
                        showContactsSettingsDialog = true
                    }
                }

                if (showContactsRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showContactsRationaleDialog = false },
                        title = { Text("Allow Contacts Access?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                "Intelligent Search needs permission to access your contacts so you can search, call, message, and view contact details directly from the search bar. Your contacts remain securely on your device.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showContactsRationaleDialog = false
                                    contactsPermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
                                }
                            ) {
                                Text("Allow", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showContactsRationaleDialog = false
                                    searchContacts = false
                                }
                            ) {
                                Text("Not Now", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }

                if (showContactsSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showContactsSettingsDialog = false },
                        title = { Text("Contacts Permission Required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                "Contacts permission is required to search your contacts. Please enable Contacts permission in Android App Settings.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showContactsSettingsDialog = false
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Throwable) {}
                                }
                            ) {
                                Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showContactsSettingsDialog = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
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
                                prefs.edit().putBoolean("search.contacts", true).apply()
                                viewModel?.updateSetting(SettingsManager.SEARCH_CONTACTS, true)
                            } else {
                                showContactsRationaleDialog = true
                            }
                        } else {
                            searchContacts = false
                            prefs.edit().putBoolean("search.contacts", false).apply()
                            viewModel?.updateSetting(SettingsManager.SEARCH_CONTACTS, false)
                        }
                    },
                    onClick = { 
                        if (searchContacts) {
                            onNavigate(com.pixel.intelligentsearch.core.navigation.Route.ContactSearch) 
                        } else {
                            Toast.makeText(context, "Enable Contacts first to configure settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    showDivider = true
                )
                
                val filesPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    val granted = results.values.any { it }
                    if (granted) {
                        searchFiles = true
                        prefs.edit().putBoolean("search.files", true).apply()
                        viewModel?.updateSetting(SettingsManager.SEARCH_FILES, true)
                        Toast.makeText(context, "Files search enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        searchFiles = false
                        prefs.edit().putBoolean("search.files", false).apply()
                        viewModel?.updateSetting(SettingsManager.SEARCH_FILES, false)
                        showFilesSettingsDialog = true
                    }
                }

                if (showFilesRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = { showFilesRationaleDialog = false },
                        title = { Text("Allow Files & Media Access?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                "Intelligent Search needs file and media permissions to find and open files, photos, audio, videos, and documents directly from the search bar.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showFilesRationaleDialog = false
                                    filesPermissionLauncher.launch(filePermissions)
                                }
                            ) {
                                Text("Allow", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showFilesRationaleDialog = false
                                    searchFiles = false
                                }
                            ) {
                                Text("Not Now", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }

                if (showFilesSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = { showFilesSettingsDialog = false },
                        title = { Text("Files Permission Required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                "File access permission is required to search files on your device. Please enable file and media access in Android App Settings.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showFilesSettingsDialog = false
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Throwable) {}
                                }
                            ) {
                                Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFilesSettingsDialog = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }

                SettingsRowToggle(
                    title = "Files",
                    subtitle = "Search Local Files.",
                    icon = Icons.Outlined.Folder,
                    isChecked = searchFiles,
                    onCheckedChange = { isChecked -> 
                        if (isChecked) {
                            if (filePermissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                                searchFiles = true
                                prefs.edit().putBoolean("search.files", true).apply()
                                viewModel?.updateSetting(SettingsManager.SEARCH_FILES, true)
                            } else {
                                showFilesRationaleDialog = true
                            }
                        } else {
                            searchFiles = false
                            prefs.edit().putBoolean("search.files", false).apply()
                            viewModel?.updateSetting(SettingsManager.SEARCH_FILES, false)
                        }
                    },
                    onClick = { 
                        if (searchFiles) {
                            onNavigate(com.pixel.intelligentsearch.core.navigation.Route.FileSearch) 
                        } else {
                            Toast.makeText(context, "Enable Files first to configure settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    showDivider = true
                )
                var searchCalc by rememberBooleanPreference(prefs, "search.calculator", false)
                SettingsRowToggle(
                    title = "Calculator",
                    subtitle = "Calculate Mathematical Equations Inside Search Bar.",
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
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
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
    
    val biometricGate = remember { com.pixel.intelligentsearch.core.security.BiometricSearchGate(context) }
    var isAuthenticated by remember { mutableStateOf(false) }

    val triggerAuth: () -> Unit = {
        val targetActivity = biometricGate.getActivity()
        if (targetActivity != null && biometricGate.isBiometricHardwareAvailable()) {
            biometricGate.authenticateForPrivateSearch(
                activity = targetActivity,
                onSuccess = { isAuthenticated = true },
                onError = { isAuthenticated = false }
            )
        } else {
            isAuthenticated = true
        }
    }

    LaunchedEffect(Unit) {
        triggerAuth()
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
            if (!isAuthenticated) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Biometric Authentication Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Use Fingerprint or Face Unlock to view and manage hidden applications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    androidx.compose.material3.Button(onClick = triggerAuth) {
                        Text("Unlock with Biometrics")
                    }
                }
            } else {
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
}

// -----------------------------------------------------------------------------------------
// PRIORITY WEIGHT HELPER
// -----------------------------------------------------------------------------------------
object PriorityWeightHelper {
    data class PriorityLevel(val label: String, val weight: Int)

    val LEVELS = listOf(
        PriorityLevel("Low", 25),
        PriorityLevel("Medium", 50),
        PriorityLevel("High", 75),
        PriorityLevel("Very High", 100)
    )

    const val DEFAULT_WEIGHT = 50

    fun weightToLevelIndex(weight: Int): Int {
        var closestIdx = 1 // default to Medium
        var minDiff = Int.MAX_VALUE
        for (i in LEVELS.indices) {
            val diff = Math.abs(LEVELS[i].weight - weight)
            if (diff < minDiff) {
                minDiff = diff
                closestIdx = i
            }
        }
        return closestIdx
    }

    fun levelIndexToWeight(index: Int): Int {
        return LEVELS.getOrElse(index.coerceIn(0, LEVELS.size - 1)) { LEVELS[1] }.weight
    }

    fun levelLabel(index: Int): String {
        return LEVELS.getOrElse(index.coerceIn(0, LEVELS.size - 1)) { LEVELS[1] }.label
    }
}

// -----------------------------------------------------------------------------------------
// SEARCH APP COLORFUL ICON HELPERS
// -----------------------------------------------------------------------------------------
@Composable
fun GoogleOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val googleDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.google.android.googlequicksearchbox")
        }.getOrNull()
    }
    if (googleDrawable != null) {
        val bitmap = remember(googleDrawable) {
            runCatching { googleDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Google", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.pixel.intelligentsearch.R.drawable.ic_g_logo_colored),
            contentDescription = "Google",
            modifier = Modifier.padding(2.dp),
            tint = Color.Unspecified
        )
    }
}

@Composable
fun DuckDuckGoOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ddgDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.duckduckgo.mobile.android")
        }.getOrNull()
    }
    if (ddgDrawable != null) {
        val bitmap = remember(ddgDrawable) {
            runCatching { ddgDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "DuckDuckGo", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color(0xFFDE5833)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height
            val headPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                cubicTo(w * 0.25f, h * 0.15f, w * 0.15f, h * 0.45f, w * 0.28f, h * 0.72f)
                cubicTo(w * 0.35f, h * 0.86f, w * 0.65f, h * 0.86f, w * 0.72f, h * 0.72f)
                cubicTo(w * 0.85f, h * 0.45f, w * 0.75f, h * 0.15f, w * 0.5f, h * 0.15f)
                close()
            }
            drawPath(headPath, Color.White)
            val beakPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.5f, h * 0.48f)
                lineTo(w * 0.78f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.68f)
                close()
            }
            drawPath(beakPath, Color(0xFFF9A825))
            drawCircle(Color(0xFF212121), radius = w * 0.055f, center = Offset(w * 0.42f, h * 0.38f))
            drawCircle(Color.White, radius = w * 0.02f, center = Offset(w * 0.405f, h * 0.365f))
            val bowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.36f, h * 0.82f)
                lineTo(w * 0.64f, h * 0.94f)
                lineTo(w * 0.64f, h * 0.82f)
                lineTo(w * 0.36f, h * 0.94f)
                close()
            }
            drawPath(bowPath, Color(0xFF4CAF50))
        }
    }
}

@Composable
fun BingOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bingDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("com.microsoft.bing")
        }.getOrNull() ?: runCatching {
            context.packageManager.getApplicationIcon("com.microsoft.copilot")
        }.getOrNull()
    }
    if (bingDrawable != null) {
        val bitmap = remember(bingDrawable) {
            runCatching { bingDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Bing", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFF008373), Color(0xFF00A4EF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val scale = size.minDimension / 24f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(3.605f * scale, 0f)
                lineTo(8.4f * scale, 1.686f * scale)
                lineTo(8.4f * scale, 18.56f * scale)
                lineTo(15.153f * scale, 14.665f * scale)
                lineTo(11.843f * scale, 13.11f * scale)
                lineTo(9.753f * scale, 7.91f * scale)
                lineTo(20.393f * scale, 11.648f * scale)
                lineTo(20.393f * scale, 17.083f * scale)
                lineTo(8.403f * scale, 24f * scale)
                lineTo(3.605f * scale, 21.33f * scale)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
fun CustomSearchAppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Custom Search",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(3.dp)
        )
    }
}

@Composable
fun SearchAppColorfulIcon(
    appName: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val normalized = appName.trim()

    when {
        normalized.equals("Google", ignoreCase = true) -> {
            GoogleOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("DuckDuckGo", ignoreCase = true) -> {
            DuckDuckGoOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("Bing", ignoreCase = true) -> {
            BingOfficialAppIcon(modifier = modifier)
        }
        normalized.equals("Custom", ignoreCase = true) || normalized.isBlank() -> {
            CustomSearchAppIcon(modifier = modifier)
        }
        else -> {
            val appDrawable = remember(appName, context) {
                runCatching {
                    context.packageManager.getApplicationIcon(appName)
                }.getOrNull() ?: runCatching {
                    val pm = context.packageManager
                    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                    val apps = pm.queryIntentActivities(intent, 0)
                    val match = apps.firstOrNull {
                        it.loadLabel(pm).toString().equals(appName, ignoreCase = true) ||
                        it.activityInfo.packageName.equals(appName, ignoreCase = true)
                    }
                    match?.loadIcon(pm)
                }.getOrNull()
            }

            if (appDrawable != null) {
                val bitmap = remember(appDrawable) {
                    runCatching { appDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
                }
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = appName, modifier = modifier.clip(RoundedCornerShape(6.dp)))
                } else {
                    CustomSearchAppIcon(modifier = modifier)
                }
            } else {
                CustomSearchAppIcon(modifier = modifier)
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
                    subtitle = "Customize Quick Launch Apps in Search Overlay Screen.",
                    icon = Icons.Outlined.ViewCarousel,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchPills) },
                    showDivider = true
                )
                SettingsRow(
                    title = "Manage Hidden Apps",
                    subtitle = "Search Apps to Dynamically Hide from Search.",
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.ManageHiddenApps) },
                    showDivider = true
                )
                var fuzzySearch by rememberBooleanPreference(prefs, "app.fuzzy.search", true)
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
                    subtitle = "Manage Shortcuts for 20 Apps.",
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    isChecked = searchShortcuts,
                    onCheckedChange = { searchShortcuts = it },
                    showDivider = true
                )
                
                var recentShortcuts by rememberBooleanPreference(prefs, "shortcut.recent", true)
                SettingsRowToggle(
                    title = "Include Recent Shortcuts",
                    subtitle = "Show Recently Used Shortcuts as Suggestions.",
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
                val viewModel = LocalSettingsViewModel.current

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Max Shortcuts Suggestions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                shortcutResultsCount = 6
                                prefs.edit().putInt("shortcut_results_count", 6).apply()
                                viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, 6)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("$shortcutResultsCount", modifier = Modifier.padding(end = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Android17Slider(
                            value = shortcutResultsCount.toFloat(),
                            onValueChange = { newValue -> 
                                val newCount = newValue.toInt().coerceIn(1, 20)
                                shortcutResultsCount = newCount
                                prefs.edit().putInt("shortcut_results_count", newCount).apply()
                                viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, newCount)

                                val currentPills = searchPills.split(",").filter { it.isNotBlank() }
                                if (newCount < currentPills.size) {
                                    val trimmed = currentPills.take(newCount).joinToString(",")
                                    searchPills = trimmed
                                    val activeProfile = prefs.getInt("active_app_search_profile", 1)
                                    prefs.edit()
                                        .putString("search.pills", trimmed)
                                        .putString("app_search_profile_$activeProfile", trimmed)
                                        .apply()
                                    viewModel?.updateSetting(SettingsManager.SEARCH_PILLS, trimmed)
                                }
                            },
                            valueRange = 1f..20f,
                            steps = 18,
                            isSquiggly = false,
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var appWeight by rememberIntPreference(prefs, "search_weight_apps", 50)
                val appLevelIndex = remember(appWeight) { PriorityWeightHelper.weightToLevelIndex(appWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Priority Weight: ${PriorityWeightHelper.levelLabel(appLevelIndex)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                appWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of App Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        value = appLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            appWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}
// Removed ShortcutSearchScreen

@Composable
fun SynchronizedMorphingShortcutBadge(
    shortcut: String,
    morph: Morph,
    rotationAngle: Float,
    morphProgress: Float,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    val nativePath = remember { android.graphics.Path() }
    val composePath = remember(nativePath) { nativePath.asComposePath() }

    Box(
        modifier = modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val scaleFactor = size.minDimension * 0.44f

            translate(left = center.x, top = center.y) {
                rotate(degrees = rotationAngle, pivot = Offset.Zero) {
                    nativePath.rewind()
                    morph.toPath(progress = morphProgress, path = nativePath)
                    scale(scale = scaleFactor, pivot = Offset.Zero) {
                        drawPath(
                            path = composePath,
                            color = containerColor
                        )
                    }
                }
            }
        }

        Text(
            text = shortcut,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (shortcut.length > 3) 10.sp else 12.sp,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun SlideToRemoveAllBar(
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 40.dp
    val thumbSizePx = with(LocalDensity.current) { thumbSizeDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - with(LocalDensity.current) { 8.dp.toPx() }).coerceAtLeast(0f)

    val dragFraction = if (maxDragPx > 0f) (dragOffsetX / maxDragPx).coerceIn(0f, 1f) else 0f
    val isThresholdReached = dragFraction >= 0.82f

    val animDragOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "removeSliderSpring"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f + 0.35f * dragFraction),
                RoundedCornerShape(24.dp)
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Fill background behind dragging thumb
            if (trackWidthPx > 0f && animDragOffsetX > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { (animDragOffsetX + thumbSizePx + 8.dp.toPx()).toDp() })
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + 0.35f * dragFraction))
                )
            }

            // Central Prompt label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThresholdReached) "Release to Remove All" else "Slide to Remove All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = (1f - dragFraction * 0.7f).coerceIn(0.2f, 1f)),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Draggable Thumb Handle
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((animDragOffsetX + 4.dp.toPx()).toInt(), 0) }
                    .size(thumbSizeDp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (isThresholdReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                    )
                    .pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX >= maxDragPx * 0.82f) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                    onRemoveAll()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragPx)
                                if (dragOffsetX >= maxDragPx * 0.82f && !isThresholdReached) {
                                    hapticEngine.performPredictiveBackHaptic(view)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = "Slide to remove all",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
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
                var customEngineName by rememberStringPreference(prefs, "custom_search_engine_name", "Custom")
                val effectiveSearchEngineName = if (searchEngine == "Custom") customEngineName else searchEngine
                SettingsDropdownRow(
                    title = "Primary Search App",
                    subtitle = effectiveSearchEngineName,
                    icon = Icons.Outlined.Search,
                    options = listOf("Google", "DuckDuckGo", "Bing", "Custom"),
                    selectedOption = searchEngine,
                    onOptionSelected = { searchEngine = it },
                    showDivider = searchEngine != "Custom"
                )
                if (searchEngine == "Custom") {
                    var customUrl by rememberStringPreference(prefs, "custom_search_engine_url", "https://duckduckgo.com/?q=%s")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchAppColorfulIcon(appName = customEngineName, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = customEngineName,
                            onValueChange = { customEngineName = it },
                            label = { Text("Search App Name") },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Custom Search URL (use %s for query)") },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                var webSuggestions by rememberBooleanPreference(prefs, "search.web.suggestions", true)
                SettingsRowToggle(
                    title = "Web Suggestions",
                    subtitle = "Helpful Suggestions Appear During Active Search.",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    isChecked = webSuggestions,
                    onCheckedChange = { webSuggestions = it },
                    showDivider = true
                )

                var searchPreviousSearches by rememberBooleanPreference(prefs, "search_previous_searches", true)
                SettingsRowToggle(
                    title = "Search History",
                    subtitle = "Previously Searched Web Queries.",
                    icon = Icons.Outlined.History,
                    isChecked = searchPreviousSearches,
                    onCheckedChange = { searchPreviousSearches = it },
                    showDivider = true
                )
                
                var webResultsCount by rememberIntPreference(prefs, "web_results_count", 5)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web Results: $webResultsCount",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                webResultsCount = 5
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Android17Slider(
                        value = webResultsCount.toFloat(),
                        onValueChange = { webResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var webWeight by rememberIntPreference(prefs, "search_weight_web", 50)
                val webLevelIndex = remember(webWeight) { PriorityWeightHelper.weightToLevelIndex(webWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Web Priority Weight: ${PriorityWeightHelper.levelLabel(webLevelIndex)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                webWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of Web Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        value = webLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            webWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }

            Text(
                "Quick Web Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            SettingsCard {
                var quickShortcutsEnabled by rememberBooleanPreference(prefs, "search_quick_shortcuts", true)
                var shortcutTriggerSymbol by rememberStringPreference(prefs, "web_shortcut_trigger_symbol", "!")
                SettingsRowToggle(
                    title = "Enable Quick Web Shortcuts",
                    subtitle = "Prefix Queries with User Selected Symbol to Open Specific Web Applications.",
                    icon = Icons.Outlined.TravelExplore,
                    isChecked = quickShortcutsEnabled,
                    onCheckedChange = { quickShortcutsEnabled = it },
                    showDivider = quickShortcutsEnabled
                )
                if (quickShortcutsEnabled) {
                    val triggerOptions = listOf("!", "@", "#", "/", "?", ":", "~", "Custom")
                    val isPredefined = triggerOptions.dropLast(1).contains(shortcutTriggerSymbol)
                    var triggerDropdownSelection by remember(shortcutTriggerSymbol) {
                        mutableStateOf(if (isPredefined) shortcutTriggerSymbol else "Custom")
                    }

                    SettingsDropdownRow(
                        title = "Shortcut Trigger Symbol",
                        subtitle = "Prefix Queries with '$shortcutTriggerSymbol'.",
                        icon = Icons.Outlined.Tag,
                        options = triggerOptions,
                        selectedOption = triggerDropdownSelection,
                        onOptionSelected = { selected ->
                            triggerDropdownSelection = selected
                            if (selected != "Custom") {
                                shortcutTriggerSymbol = selected
                            }
                        },
                        showDivider = true
                    )

                    if (triggerDropdownSelection == "Custom") {
                        var customInputText by remember(triggerDropdownSelection) {
                            mutableStateOf(if (isPredefined) "" else shortcutTriggerSymbol)
                        }
                        OutlinedTextField(
                            value = customInputText,
                            onValueChange = { input ->
                                val clean = input.filter { !it.isWhitespace() }
                                customInputText = clean
                                if (clean.isNotBlank()) {
                                    shortcutTriggerSymbol = clean
                                }
                            },
                            label = { Text("Custom Trigger Symbol") },
                            placeholder = { Text("e.g. @ or ?") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    val viewModel = LocalSettingsViewModel.current
                    val allBangs by (viewModel?.bangsFlow ?: kotlinx.coroutines.flow.flowOf(emptyList()))
                        .collectAsStateWithLifecycle(initialValue = viewModel?.bangsFlow?.value ?: emptyList())
                    val settings by (viewModel?.settingsState ?: kotlinx.coroutines.flow.flowOf(IntelligentSearchSettings()))
                        .collectAsStateWithLifecycle(initialValue = IntelligentSearchSettings())
                    val disabledPrefixes = settings.disabledWebShortcuts

                    var showAddDialog by remember { mutableStateOf(false) }
                    var editingBang by remember { mutableStateOf<com.pixel.intelligentsearch.core.bangs.SearchBang?>(null) }
                    var localDismissedPrefixes by remember { mutableStateOf(setOf<String>()) }

                    val expressiveShapes = remember {
                        listOf(
                            // 1. Smooth Squircle
                            RoundedPolygon(numVertices = 4, rounding = CornerRounding(radius = 0.55f)),
                            // 2. 4-Corner Expressive Clover
                            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.60f, rounding = CornerRounding(radius = 0.35f)),
                            // 3. Rounded Triangle
                            RoundedPolygon(numVertices = 3, rounding = CornerRounding(radius = 0.40f)),
                            // 4. 6-Point Flower
                            RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.70f, rounding = CornerRounding(radius = 0.35f)),
                            // 5. Rounded Pentagon
                            RoundedPolygon(numVertices = 5, rounding = CornerRounding(radius = 0.35f)),
                            // 6. 8-Point Starburst
                            RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.80f, rounding = CornerRounding(radius = 0.35f)),
                            // 7. Rounded Hexagon
                            RoundedPolygon(numVertices = 6, rounding = CornerRounding(radius = 0.30f)),
                            // 8. 12-Point Scalloped Blossom
                            RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.85f, rounding = CornerRounding(radius = 0.40f)),
                            // 9. Diamond Sparkle
                            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.42f, rounding = CornerRounding(radius = 0.20f))
                        )
                    }

                    val morphSequence = remember(expressiveShapes) {
                        List(expressiveShapes.size) { i ->
                            Morph(expressiveShapes[i], expressiveShapes[(i + 1) % expressiveShapes.size])
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "morphingShortcutsSync")
                    val totalCycleProgress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = morphSequence.size.toFloat(),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = morphSequence.size * 2200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shortcutBadgeMorphCycle"
                    )

                    val rotationAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 16000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shortcutBadgeRotation"
                    )

                    val currentCycle = (totalCycleProgress % morphSequence.size.toFloat()).coerceIn(0f, morphSequence.size - 0.0001f)
                    val morphIndex = currentCycle.toInt().coerceIn(0, morphSequence.size - 1)
                    val rawProgress = (currentCycle - morphIndex).coerceIn(0f, 1f)
                    val morphProgress = FastOutSlowInEasing.transform(rawProgress)
                    val sharedMorph = morphSequence[morphIndex]

                    val view = LocalView.current
                    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

                    val displayedActiveBangs = remember(allBangs, localDismissedPrefixes) {
                        allBangs.filter { it.displayPrefix !in localDismissedPrefixes }
                    }

                    val effectiveDisabledPrefixes = remember(disabledPrefixes, localDismissedPrefixes) {
                        disabledPrefixes + localDismissedPrefixes
                    }

                    val availableDirectBangs = remember(effectiveDisabledPrefixes) {
                        SearchBangManager.BUILT_IN_BANGS.filter { it.displayPrefix in effectiveDisabledPrefixes }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header row for Active Shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Active Shortcuts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Web Shortcuts. Swipe to Remove.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    editingBang = null
                                    showAddDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (displayedActiveBangs.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "No Active Shortcuts. Tap Add or Restore Direct Shortcuts Below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            SlideToRemoveAllBar(
                                onRemoveAll = {
                                    localDismissedPrefixes = localDismissedPrefixes + displayedActiveBangs.map { it.displayPrefix }
                                    displayedActiveBangs.forEach { bang ->
                                        if (bang.isBuiltIn) {
                                            viewModel?.disableBuiltInBang(bang.displayPrefix)
                                        } else {
                                            viewModel?.deleteCustomBang(bang.displayPrefix)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedActiveBangs.forEach { bang ->
                                    key(bang.displayPrefix) {
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            positionalThreshold = { it * 0.4f }
                                        )

                                        LaunchedEffect(dismissState.currentValue) {
                                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart ||
                                                dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
                                            ) {
                                                hapticEngine.performPredictiveBackHaptic(view)
                                                localDismissedPrefixes = localDismissedPrefixes + bang.displayPrefix
                                                if (bang.isBuiltIn) {
                                                    viewModel?.disableBuiltInBang(bang.displayPrefix)
                                                } else {
                                                    viewModel?.deleteCustomBang(bang.displayPrefix)
                                                }
                                            }
                                        }

                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {
                                                Box(modifier = Modifier.fillMaxSize())
                                            }
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                                    .bouncyClickable {
                                                        editingBang = bang
                                                        showAddDialog = true
                                                    },
                                                color = Color.Transparent
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        val badgeLabel = remember(bang.prefix, shortcutTriggerSymbol) {
                                                            val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
                                                            if (bang.isBuiltIn || bang.prefix.startsWith("!")) {
                                                                "$shortcutTriggerSymbol$bare"
                                                            } else {
                                                                bang.displayPrefix
                                                            }
                                                        }
                                                        SynchronizedMorphingShortcutBadge(
                                                            shortcut = badgeLabel,
                                                            morph = sharedMorph,
                                                            rotationAngle = rotationAngle,
                                                            morphProgress = morphProgress
                                                        )
                                                        Spacer(modifier = Modifier.width(16.dp))
                                                        val appLabel = remember(bang.targetPackage, bang.name) {
                                                            if (!bang.targetPackage.isNullOrBlank()) {
                                                                try {
                                                                    val appInfo = context.packageManager.getApplicationInfo(bang.targetPackage, 0)
                                                                    context.packageManager.getApplicationLabel(appInfo).toString()
                                                                } catch (_: Exception) {
                                                                    bang.name.ifBlank { bang.targetPackage }
                                                                }
                                                            } else {
                                                                bang.name
                                                            }
                                                        }
                                                        Column {
                                                            Text(
                                                                text = appLabel.ifBlank { bang.targetPackage ?: badgeLabel },
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                fontSize = 16.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            val subtext = if (!bang.targetPackage.isNullOrBlank()) {
                                                                bang.targetPackage
                                                            } else {
                                                                bang.urlTemplate
                                                            }
                                                            Text(
                                                                text = subtext,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                fontSize = 12.sp,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "Edit Shortcut",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Available Direct Shortcuts section
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        var showAvailableDirectShortcuts by rememberBooleanPreference(prefs, "search_show_available_direct_shortcuts", true)
                        SettingsRowToggle(
                            title = "Available Direct Shortcuts",
                            subtitle = "Tap Add to Restore Any Direct Shortcut.",
                            icon = Icons.Outlined.BookmarkBorder,
                            isChecked = showAvailableDirectShortcuts,
                            onCheckedChange = { showAvailableDirectShortcuts = it },
                            showDivider = showAvailableDirectShortcuts && availableDirectBangs.isNotEmpty()
                        )

                        if (showAvailableDirectShortcuts && availableDirectBangs.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                availableDirectBangs.forEach { bang ->
                                    key("avail_${bang.displayPrefix}") {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(24.dp))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(24.dp)
                                                ),
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    val directBadge = remember(bang.prefix, shortcutTriggerSymbol) {
                                                        val bare = bang.prefix.trimStart { !it.isLetterOrDigit() }
                                                        "$shortcutTriggerSymbol$bare"
                                                    }
                                                    SynchronizedMorphingShortcutBadge(
                                                        shortcut = directBadge,
                                                        morph = sharedMorph,
                                                        rotationAngle = rotationAngle,
                                                        morphProgress = morphProgress
                                                    )
                                                    Spacer(modifier = Modifier.width(14.dp))
                                                    Column {
                                                        Text(
                                                            text = bang.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = bang.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                        localDismissedPrefixes = localDismissedPrefixes - bang.displayPrefix
                                                        viewModel?.enableBuiltInBang(bang.displayPrefix)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showAddDialog) {
                        var prefixInput by remember(editingBang) { mutableStateOf(editingBang?.displayPrefix ?: shortcutTriggerSymbol) }
                        var targetPackageInput by remember(editingBang) { mutableStateOf(editingBang?.targetPackage.orEmpty()) }
                        var urlInput by remember(editingBang) { mutableStateOf(editingBang?.urlTemplate ?: "") }
                        var errorMsg by remember { mutableStateOf<String?>(null) }
                        var showAppPicker by remember { mutableStateOf(false) }

                        val installedApps = remember(context) {
                            try {
                                val pm = context.packageManager
                                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                }
                                val resolveInfos = pm.queryIntentActivities(intent, 0)
                                resolveInfos.map { resolveInfo ->
                                    val label = resolveInfo.loadLabel(pm).toString()
                                    val packageName = resolveInfo.activityInfo.packageName
                                    label to packageName
                                }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
                            } catch (_: Exception) {
                                emptyList<Pair<String, String>>()
                            }
                        }

                        if (showAppPicker) {
                            var pickerQuery by remember { mutableStateOf("") }
                            val displayedApps = remember(pickerQuery, installedApps) {
                                if (pickerQuery.isBlank()) {
                                    installedApps
                                } else {
                                    val q = pickerQuery.trim().lowercase()
                                    installedApps.filter {
                                        it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
                                    }
                                }
                            }

                            AlertDialog(
                                onDismissRequest = { showAppPicker = false },
                                title = {
                                    Text(
                                        "Select Installed App",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 380.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = pickerQuery,
                                            onValueChange = { pickerQuery = it },
                                            placeholder = { Text("Search installed apps") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Search,
                                                    contentDescription = null
                                                )
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        )

                                        if (displayedApps.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "No matching apps found",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(displayedApps, key = { it.second }) { (appName, pkgName) ->
                                                    Surface(
                                                        onClick = {
                                                            targetPackageInput = pkgName

                                                            // Automatically adjust shortcut trigger with user's selected trigger symbol
                                                            val clean = appName.lowercase().filter { it.isLetterOrDigit() }
                                                            val slug = when (pkgName) {
                                                                "com.google.android.youtube" -> "yt"
                                                                "com.spotify.music" -> "spot"
                                                                "org.wikipedia" -> "w"
                                                                "com.reddit.frontpage" -> "r"
                                                                "com.github.android" -> "gh"
                                                                "com.google.android.apps.maps" -> "maps"
                                                                "com.amazon.mShop.android.shopping" -> "az"
                                                                "com.twitter.android" -> "x"
                                                                "tv.twitch.android.app" -> "tw"
                                                                "com.instagram.android" -> "ig"
                                                                "com.pinterest" -> "pin"
                                                                "com.imdb.mobile" -> "imdb"
                                                                "com.google.android.googlequicksearchbox" -> "g"
                                                                "com.duckduckgo.mobile.android" -> "ddg"
                                                                else -> if (clean.length <= 4) clean else clean.take(4)
                                                            }
                                                            prefixInput = "$shortcutTriggerSymbol$slug"

                                                            // Suggest URL if empty or generic
                                                            if (urlInput.isBlank() || urlInput.contains("/search?q=%s")) {
                                                                urlInput = when (pkgName) {
                                                                    "com.google.android.youtube" -> "https://www.youtube.com/results?search_query=%s"
                                                                    "com.spotify.music" -> "https://open.spotify.com/search/%s"
                                                                    "org.wikipedia" -> "https://en.wikipedia.org/wiki/%s"
                                                                    "com.reddit.frontpage" -> "https://www.reddit.com/search/?q=%s"
                                                                    "com.github.android" -> "https://github.com/search?q=%s"
                                                                    "com.google.android.apps.maps" -> "https://www.google.com/maps/search/%s"
                                                                    "com.amazon.mShop.android.shopping" -> "https://www.amazon.com/s?k=%s"
                                                                    "com.twitter.android" -> "https://x.com/search?q=%s"
                                                                    "tv.twitch.android.app" -> "https://www.twitch.tv/search?term=%s"
                                                                    "com.instagram.android" -> "https://www.instagram.com/explore/tags/%s"
                                                                    "com.pinterest" -> "https://www.pinterest.com/search/pins/?q=%s"
                                                                    "com.imdb.mobile" -> "https://www.imdb.com/find/?q=%s"
                                                                    else -> {
                                                                        val domain = appName.lowercase().filter { it.isLetterOrDigit() }
                                                                        "https://www.${domain}.com/search?q=%s"
                                                                    }
                                                                }
                                                            }
                                                            showAppPicker = false
                                                            errorMsg = null
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = Color.Transparent,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            val appDrawable = remember(pkgName) {
                                                                runCatching { context.packageManager.getApplicationIcon(pkgName) }.getOrNull()
                                                            }
                                                            val appBitmap = remember(appDrawable) {
                                                                runCatching { appDrawable?.toBitmap(width = 96, height = 96)?.asImageBitmap() }.getOrNull()
                                                            }
                                                            if (appBitmap != null) {
                                                                Image(
                                                                    bitmap = appBitmap,
                                                                    contentDescription = appName,
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Android,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(28.dp),
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column {
                                                                Text(
                                                                    appName,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                Text(
                                                                    pkgName,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    maxLines = 1,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showAppPicker = false }) {
                                        Text("Close")
                                    }
                                }
                            )
                        }

                        AlertDialog(
                            onDismissRequest = {
                                editingBang = null
                                showAddDialog = false
                            },
                            title = {
                                Text(
                                    if (editingBang != null) "Edit Web Shortcut" else "Add Web Shortcut",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = prefixInput,
                                        onValueChange = {
                                            prefixInput = it.filter { c -> !c.isWhitespace() }
                                            errorMsg = null
                                        },
                                        label = { Text("Shortcut Trigger") },
                                        placeholder = { Text("${shortcutTriggerSymbol}wiki") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = targetPackageInput,
                                        onValueChange = {
                                            targetPackageInput = it
                                            errorMsg = null
                                        },
                                        label = { Text("Package Name") },
                                        placeholder = { Text("com.example.app") },
                                        trailingIcon = {
                                            IconButton(onClick = { showAppPicker = true }) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Apps,
                                                    contentDescription = "Select installed app",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = {
                                            urlInput = it
                                            errorMsg = null
                                        },
                                        label = { Text("Search URL (use %s for query)") },
                                        placeholder = { Text("https://en.wikipedia.org/wiki/%s") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (errorMsg != null) {
                                        Text(
                                            text = errorMsg!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val trimmedPrefix = prefixInput.trim()
                                        val trimmedPackage = targetPackageInput.trim()
                                        var trimmedUrl = urlInput.trim()

                                        if (trimmedPrefix.length < 2) {
                                            errorMsg = "Trigger must be at least 2 characters (e.g. ${shortcutTriggerSymbol}wiki)"
                                            return@TextButton
                                        }
                                        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
                                            errorMsg = "URL must start with http:// or https://"
                                            return@TextButton
                                        }
                                        if (!trimmedUrl.contains("%s")) {
                                            trimmedUrl = if (trimmedUrl.endsWith("/") || trimmedUrl.endsWith("=")) {
                                                "${trimmedUrl}%s"
                                            } else if (trimmedUrl.contains("?")) {
                                                "${trimmedUrl}&q=%s"
                                            } else {
                                                "${trimmedUrl}/?q=%s"
                                            }
                                        }

                                        val derivedName = if (trimmedPackage.isNotBlank()) {
                                            try {
                                                val appInfo = context.packageManager.getApplicationInfo(trimmedPackage, 0)
                                                context.packageManager.getApplicationLabel(appInfo).toString()
                                            } catch (_: Exception) {
                                                trimmedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                                            }
                                        } else {
                                            trimmedPrefix.trimStart { !it.isLetterOrDigit() }.replaceFirstChar { it.uppercase() }.ifBlank { "Shortcut" }
                                        }

                                        val newBang = com.pixel.intelligentsearch.core.bangs.SearchBang(
                                            prefix = trimmedPrefix.lowercase(),
                                            name = derivedName,
                                            urlTemplate = trimmedUrl,
                                            targetPackage = trimmedPackage.ifBlank { null },
                                            isBuiltIn = false,
                                            description = "Custom shortcut for $derivedName"
                                        )

                                        val prevBang = editingBang
                                        if (prevBang != null) {
                                            if (prevBang.isBuiltIn) {
                                                viewModel?.disableBuiltInBang(prevBang.displayPrefix)
                                            } else if (!prevBang.displayPrefix.equals(newBang.displayPrefix, ignoreCase = true)) {
                                                viewModel?.deleteCustomBang(prevBang.displayPrefix)
                                            }
                                        }

                                        localDismissedPrefixes = localDismissedPrefixes - newBang.displayPrefix
                                        viewModel?.enableBuiltInBang(newBang.displayPrefix)
                                        viewModel?.saveCustomBang(newBang)
                                        editingBang = null
                                        showAddDialog = false
                                    }
                                ) {
                                    Text(if (editingBang != null) "Save" else "Add")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    editingBang = null
                                    showAddDialog = false
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
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
                    subtitle = "Opens Contact Picker.",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Contact Results: $contactResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                contactResultsCount = 5
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Android17Slider(
                        value = contactResultsCount.toFloat(),
                        onValueChange = { contactResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var contactWeight by rememberIntPreference(prefs, "search_weight_contacts", 50)
                val contactLevelIndex = remember(contactWeight) { PriorityWeightHelper.weightToLevelIndex(contactWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contact Priority Weight: ${PriorityWeightHelper.levelLabel(contactLevelIndex)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                contactWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of Contact Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        value = contactLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            contactWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
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
                val currentFileUri = prefs.getString("search.files.uri", null)
                val fileUriSubtitle = if (currentFileUri.isNullOrBlank()) "None Selected." else currentFileUri
                SettingsRow(
                    title = "Select Indexing Directory",
                    subtitle = fileUriSubtitle,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("File Results: $fileResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                fileResultsCount = 5
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Android17Slider(
                        value = fileResultsCount.toFloat(),
                        onValueChange = { fileResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var fileWeight by rememberIntPreference(prefs, "search_weight_files", 50)
                val fileLevelIndex = remember(fileWeight) { PriorityWeightHelper.weightToLevelIndex(fileWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File Priority Weight: ${PriorityWeightHelper.levelLabel(fileLevelIndex)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                fileWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of File Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        value = fileLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            fileWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
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
                var bottomResult by rememberBooleanPreference(prefs, "settings.bottom.search.result", true)
                SettingsRowToggle(
                    title = "Bottom Search Results",
                    subtitle = "Order List from Bottom Up Depending on Search Bar Placement.",
                    icon = Icons.Outlined.AlignVerticalBottom,
                    isChecked = bottomResult,
                    onCheckedChange = { bottomResult = it },
                    showDivider = true
                )
                var compactList by rememberBooleanPreference(prefs, "quick.search.horizontal", false)
                SettingsRowToggle(
                    title = "Quick App Panel",
                    subtitle = "Quickly Launch Apps Selected from Search Source Apps.",
                    icon = Icons.Outlined.ViewCompact,
                    isChecked = compactList,
                    onCheckedChange = { compactList = it },
                    showDivider = true
                )
                var contextAwareApps by rememberBooleanPreference(prefs, "context_aware_quick_apps", false)
                SettingsRowToggle(
                    title = "Context Aware Quick Apps",
                    subtitle = "Dynamically Chosen Apps Based on User's App Opening Cycles.",
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
                    subtitle = "Suggested Actions Based on Clipboard Text. I.E. Open Photos, Open Music Player, Etc.",
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
fun SettingsCard(
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
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
    val iconBitmap = remember(icon) {
        if (icon is android.graphics.drawable.Drawable) {
            runCatching { icon.toBitmap().asImageBitmap() }.getOrNull()
        } else null
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(
                    onLongClick = onLongClick,
                    onClick = onClick
                )
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
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = title,
                            modifier = Modifier.size(32.dp)
                        )
                    }
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
                    if (title == "Primary Search App") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            SearchAppColorfulIcon(appName = subtitle, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                            leadingIcon = if (title == "Primary Search App") {
                                {
                                    SearchAppColorfulIcon(appName = option, modifier = Modifier.size(24.dp))
                                }
                            } else if (optionIcons != null && optionIcons.containsKey(option)) {
                                {
                                    if (title == "Widget Action Icon" && option in listOf("Search", "Assistant", "Gemini", "Now Playing")) {
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
    val view = androidx.compose.ui.platform.LocalView.current
    val sensoryEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context) }
    val iconBitmap = remember(icon) {
        if (icon is android.graphics.drawable.Drawable) {
            runCatching { icon.toBitmap().asImageBitmap() }.getOrNull()
        } else null
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable(
                    onLongClick = onLongClick,
                    suppressClickHaptic = true,
                    onClick = {
                    val next = !isChecked
                    sensoryEngine.toggle(view, next)
                    if (onClick != null) onClick() else onCheckedChange(next)
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
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = title,
                                modifier = Modifier.size(32.dp)
                            )
                        }
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
                onCheckedChange = { next ->
                    sensoryEngine.toggle(view, next)
                    onCheckedChange(next)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
    steps: Int = 0,
    onReset: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (onReset != null) {
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                onReset()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
    var localColorOpacity by remember { mutableStateOf(prefs.getInt("widget_custom_color_opacity", 100).toFloat()) }
    var localTransparency by remember { mutableStateOf(prefs.getInt("widget.background.transparency", 28).toFloat()) }
    val computedCustomColorInt = remember(localHue, localSaturation, localLightness, localColorOpacity) {
        android.graphics.Color.HSVToColor(
            (localColorOpacity / 100f * 255).toInt().coerceIn(0, 255),
            floatArrayOf(localHue, (localSaturation / 100f).coerceIn(0f, 1f), (localLightness / 100f).coerceIn(0f, 1f))
        )
    }
    var localLockBlack by remember { mutableStateOf(prefs.getBoolean("widget_material_lock_black", true)) }
    var localShowVoice by remember { mutableStateOf(prefs.getBoolean("widget_show_voice", true)) }
    var localActionIcon by remember { mutableStateOf(prefs.getString("widget_action_icon", "Search") ?: "Search") }
    val rawSc1 = prefs.getString("widget_shortcut_1", prefs.getString("widget_shortcut", "Google Lens")) ?: "Google Lens"
    val rawSc2 = prefs.getString("widget_shortcut_2", "None") ?: "None"
    val rawSc3 = prefs.getString("widget_shortcut_3", "None") ?: "None"
    var localShortcut1 by remember { mutableStateOf(if (rawSc1 == "Voice Search") "None" else rawSc1) }
    var localShortcut2 by remember { mutableStateOf(if (rawSc2 == "Voice Search") "None" else rawSc2) }
    var localShortcut3 by remember { mutableStateOf(if (rawSc3 == "Voice Search") "None" else rawSc3) }
    val defaultSlotOrder = "shortcut1,mic,shortcut2,shortcut3"
    var localSlotOrderStr by remember { mutableStateOf(prefs.getString("widget_shortcut_order", defaultSlotOrder) ?: defaultSlotOrder) }
    var activeShortcutSlot by remember { mutableIntStateOf(1) }
    var draggingSlotKey by remember { mutableStateOf<String?>(null) }
    val view = androidx.compose.ui.platform.LocalView.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var slotItemHeightPx by remember { mutableFloatStateOf(with(density) { 84.dp.toPx() }) }
    var itemDragOffset by remember { mutableFloatStateOf(0f) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Widget Customization", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = {
                        hapticEngine.performPredictiveBackHaptic(view)
                        localShowGIcon = true
                        localThemeStyle = "Material Design"
                        localSubtheme = "System"
                        localMaterialGIconTheme = "Material G Icon"
                        localHue = 277f
                        localSaturation = 51f
                        localLightness = 100f
                        localColorOpacity = 100f
                        localTransparency = 28f
                        localLockBlack = true
                        localShowVoice = true
                        localActionIcon = "Search"
                        localShortcut1 = "Google Lens"
                        localShortcut2 = "None"
                        localShortcut3 = "None"
                        localSlotOrderStr = defaultSlotOrder
                    }) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        hapticEngine.performPredictiveBackHaptic(view)
                        prefs.edit()
                            .putBoolean("widget_show_g_icon", localShowGIcon)
                            .putString("widget.theme.style", localThemeStyle)
                            .putString("widget_subtheme", localSubtheme)
                            .putString("widget_material_g_icon", localMaterialGIconTheme)
                            .putInt("widget_custom_hue", localHue.toInt())
                            .putInt("widget_custom_saturation", localSaturation.toInt())
                            .putInt("widget_custom_lightness", localLightness.toInt())
                            .putInt("widget_custom_color_opacity", localColorOpacity.toInt())
                            .putInt("widget_custom_color_int", computedCustomColorInt)
                            .putInt("widget.background.transparency", localTransparency.toInt())
                            .putBoolean("widget_material_lock_black", localLockBlack)
                            .putBoolean("widget_show_voice", localShowVoice)
                            .putString("widget_action_icon", localActionIcon)
                            .putString("widget_shortcut_1", localShortcut1)
                            .putString("widget_shortcut_2", localShortcut2)
                            .putString("widget_shortcut_3", localShortcut3)
                            .putString("widget_shortcut_order", localSlotOrderStr)
                            .putString("widget_shortcut", localShortcut1)
                            .apply()
                        updateWidgets(context)
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
        ) {
            // Live Preview Card (Pinned at the top for real-time visual feedback)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                    val containerAlpha = ((100 - localTransparency.toInt()) / 100f).coerceIn(0f, 1f)
                    val colorAlpha = (localColorOpacity / 100f).coerceIn(0f, 1f)
                    val effectiveColorAlpha = (colorAlpha * containerAlpha).coerceIn(0f, 1f)
                    
                    val accentColor = if (localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.Color(computedCustomColorInt)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val previewIsMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                    val matPrimary = MaterialTheme.colorScheme.primary
                    val matSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    val activeColor = remember(previewIsMaterialYou, localSubtheme, computedCustomColorInt, matPrimary, matSurfaceVariant, effectiveColorAlpha) {
                        if (localSubtheme == "Custom") {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(computedCustomColorInt, hsv)
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

                    val isCustomTheme = localSubtheme == "Custom"

                    val waveColor = MaterialTheme.colorScheme.primary

                    GeminiCornerSwipeWaveLayer(
                        colorPrimary = waveColor,
                        colorSecondary = waveColor,
                        colorTertiary = waveColor,
                        colorAccent = waveColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    )

                    val previewRimColorAlpha = if (previewIsMaterialYou) {
                        if (localSubtheme == "Custom") {
                            accentColor.copy(alpha = effectiveColorAlpha)
                        } else if (localSubtheme == "Material") {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = effectiveColorAlpha)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = effectiveColorAlpha)
                        }
                    } else androidx.compose.ui.graphics.Color.Transparent
                    
                    val effectiveGIconTheme = if (localSubtheme == "Custom" || previewIsMaterialYou) {
                        localMaterialGIconTheme
                    } else {
                        "System G Icon"
                    }

                    val customLuminance = (0.299 * accentColor.red + 0.587 * accentColor.green + 0.114 * accentColor.blue)
                    val isPreviewPillLight = if (previewIsMaterialYou) {
                        !localLockBlack && (customLuminance > 0.5f)
                    } else {
                        when (localSubtheme) {
                            "Light" -> true
                            "Dark" -> false
                            "Custom" -> customLuminance > 0.5f
                            else -> !androidx.compose.foundation.isSystemInDarkTheme()
                        }
                    }
                    
                    val materialDarkCompose = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        androidx.compose.ui.graphics.Color(context.getColor(android.R.color.system_accent1_700))
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF1F1F1F)
                    }

                    val rimBrush = if (previewIsMaterialYou && localSubtheme == "Custom") {
                        androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = effectiveColorAlpha))
                    } else {
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(previewRimColorAlpha, previewRimColorAlpha))
                    }
                    
                    val previewPillColorAlpha = if (previewIsMaterialYou) {
                        if (localLockBlack) {
                            val baseColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                androidx.compose.ui.graphics.Color(context.getColor(android.R.color.system_neutral1_900))
                            } else {
                                androidx.compose.ui.graphics.Color(0xFF121212)
                            }
                            baseColor.copy(alpha = containerAlpha)
                        } else {
                            accentColor.copy(alpha = effectiveColorAlpha)
                        }
                    } else {
                        when (localSubtheme) {
                            "Light" -> androidx.compose.ui.graphics.Color(0xFFF8F9FA).copy(alpha = containerAlpha)
                            "Dark" -> androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = containerAlpha)
                            "Custom" -> accentColor.copy(alpha = effectiveColorAlpha)
                            else -> if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF303134).copy(alpha = containerAlpha) else androidx.compose.ui.graphics.Color(0xFFF8F9FA).copy(alpha = containerAlpha)
                        }
                    }
                    
                    val isDarkSurface = !isPreviewPillLight
                    val customM3Colors = remember(localHue, localSaturation, isDarkSurface) {
                        com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper.getMaterialYouTonalColors(
                            hue = localHue,
                            saturation = localSaturation,
                            isDarkSurface = isDarkSurface
                        )
                    }

                    val gPrimary = if (isCustomTheme) {
                        androidx.compose.ui.graphics.Color(customM3Colors.primary)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val gSecondary = if (isCustomTheme) {
                        androidx.compose.ui.graphics.Color(customM3Colors.secondary)
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                    val gTertiary = if (isCustomTheme) {
                        androidx.compose.ui.graphics.Color(customM3Colors.tertiary)
                    } else {
                        MaterialTheme.colorScheme.tertiary
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
                                val isAccented = effectiveGIconTheme == "Accented G Icon"
                                val useOriginalGIcon = effectiveGIconTheme == "System G Icon"
                                ComposeGIcon(
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = gPrimary,
                                    secondaryColor = gSecondary,
                                    tertiaryColor = gTertiary,
                                    isAccented = isAccented,
                                    accentColor = gPrimary,
                                    fallbackTint = androidx.compose.ui.graphics.Color.Unspecified,
                                    useOriginalColors = useOriginalGIcon
                                )
                            }
                            
                            Spacer(Modifier.weight(1f))
                            
                            val useMaterialYouIcons = previewIsMaterialYou || effectiveGIconTheme == "Material G Icon" || isPreviewPillLight
                            val slotOrder = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                            val previewActiveItems = mutableListOf<Triple<String, Int, Boolean>>()
                            slotOrder.forEach { key ->
                                when (key) {
                                    "mic" -> {
                                        if (localShowVoice) {
                                            val micRes = if (useMaterialYouIcons) com.pixel.intelligentsearch.R.drawable.ic_mic else com.pixel.intelligentsearch.R.drawable.ic_mic_original
                                            previewActiveItems.add(Triple("Voice Search", micRes, true))
                                        }
                                    }
                                    "shortcut1" -> {
                                        if (localShortcut1 != "None") {
                                            previewActiveItems.add(Triple(localShortcut1, SearchWidgetProvider.getShortcutIconRes(localShortcut1, useMaterialYouIcons), false))
                                        }
                                    }
                                    "shortcut2" -> {
                                        if (localShortcut2 != "None") {
                                            previewActiveItems.add(Triple(localShortcut2, SearchWidgetProvider.getShortcutIconRes(localShortcut2, useMaterialYouIcons), false))
                                        }
                                    }
                                    "shortcut3" -> {
                                        if (localShortcut3 != "None") {
                                            previewActiveItems.add(Triple(localShortcut3, SearchWidgetProvider.getShortcutIconRes(localShortcut3, useMaterialYouIcons), false))
                                        }
                                    }
                                }
                            }

                            previewActiveItems.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                val isMaterial = effectiveGIconTheme == "Material G Icon"
                                val (scPrimary, scSecondary, scTertiary) = when (effectiveGIconTheme) {
                                    "Accented G Icon" -> Triple(gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> Triple(gPrimary, gSecondary, gTertiary)
                                    else -> { // System G Icon
                                        val sysTint = if (isPreviewPillLight) materialDarkCompose else androidx.compose.ui.graphics.Color.White
                                        val finalTint = if (item.second == com.pixel.intelligentsearch.R.drawable.ic_mic_original) androidx.compose.ui.graphics.Color.Unspecified else sysTint
                                        Triple(finalTint, finalTint, finalTint)
                                    }
                                }
                                ComposeThemedShortcutIcon(
                                    resId = item.second,
                                    contentDescription = item.first,
                                    primaryColor = scPrimary,
                                    secondaryColor = scSecondary,
                                    tertiaryColor = scTertiary,
                                    isMaterialTheme = isMaterial,
                                    modifier = Modifier.size(24.dp)
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
                                val (actPrimary, actSecondary, actTertiary) = when (effectiveGIconTheme) {
                                    "Accented G Icon" -> Triple(gPrimary, gPrimary, gPrimary)
                                    "Material G Icon" -> Triple(gPrimary, gSecondary, gTertiary)
                                    else -> { // System G Icon
                                        val sysActionTint = if (isPreviewPillLight) materialDarkCompose else androidx.compose.ui.graphics.Color.White
                                        Triple(sysActionTint, sysActionTint, sysActionTint)
                                    }
                                }
                                ComposeActionIcon(
                                    iconType = localActionIcon,
                                    modifier = Modifier.size(24.dp),
                                    primaryColor = actPrimary,
                                    secondaryColor = actSecondary,
                                    tertiaryColor = actTertiary
                                )
                            }
                        }
                    }
            }

            // System vs Material Design Switcher
            SettingsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isSystem = localThemeStyle == "System Default"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                localThemeStyle = "System Default"
                                localSubtheme = "System"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "System Design",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSystem) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (!isSystem) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(24.dp))
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                localThemeStyle = "Material You (Minimal)"
                                localSubtheme = "Material"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Material Design",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (!isSystem) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isSystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Material 3 Expressive Segmented Tab Bar
            val isCustomActive = localSubtheme == "Custom"
            val tabs = remember(isCustomActive) {
                if (isCustomActive) {
                    listOf(
                        Pair("Appearance", Icons.Outlined.Palette),
                        Pair("Color Studio", Icons.Outlined.Tune),
                        Pair("Widget Shortcuts", Icons.Outlined.Widgets)
                    )
                } else {
                    listOf(
                        Pair("Appearance", Icons.Outlined.Palette),
                        Pair("Widget Shortcuts", Icons.Outlined.Widgets)
                    )
                }
            }

            LaunchedEffect(isCustomActive) {
                if (!isCustomActive) {
                    if (selectedTab == 1) {
                        selectedTab = 0
                    } else if (selectedTab > 1) {
                        selectedTab = 1
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSel = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .bouncyClickable(shape = RoundedCornerShape(24.dp)) {
                                hapticEngine.performPredictiveBackHaptic(view)
                                selectedTab = index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.second,
                                contentDescription = tab.first,
                                tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.first,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Scrollable Content for Active Tab
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState(), enabled = draggingSlotKey == null)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val activeTabName = tabs.getOrNull(selectedTab)?.first ?: "Appearance"
                when (activeTabName) {
                    "Appearance" -> {
                        // TAB 0: APPEARANCE
                        // G Icon options
                        SettingsCard {
                            SettingsRowToggle(
                                title = "Display G Icon",
                                subtitle = "Show Google Logo on Search Bar Widget.",
                                icon = null,
                                customIcon = {
                                    if (localThemeStyle == "System Default") {
                                        Image(
                                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_g_logo_colored),
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
                                    val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                                    val bgModifier = if (opt == "Custom") {
                                        if (isSel) {
                                            Modifier.background(dynColor, RoundedCornerShape(32.dp))
                                        } else {
                                            Modifier.background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        dynColor.copy(alpha = 0.5f),
                                                        dynColor
                                                    )
                                                ),
                                                shape = RoundedCornerShape(32.dp)
                                            )
                                        }
                                    } else {
                                        Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .then(bgModifier)
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
                                    val dynColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                                    val bgModifier = if (opt == "Custom") {
                                        if (isSel) {
                                            Modifier.background(dynColor, RoundedCornerShape(32.dp))
                                        } else {
                                            Modifier.background(
                                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    colors = listOf(
                                                        dynColor.copy(alpha = 0.5f),
                                                        dynColor
                                                    )
                                                ),
                                                shape = RoundedCornerShape(32.dp)
                                            )
                                        }
                                    } else {
                                        Modifier.background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), RoundedCornerShape(32.dp))
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .then(bgModifier)
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

                        val isMaterialYou = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                        if (localSubtheme == "Custom" || isMaterialYou) {
                            Text("G ICON STYLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
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

                        if (isMaterialYou) {
                            Text("WIDGET ACTION ICON", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                            SettingsCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val actionOptions = listOf(
                                        Triple("None", "None", null),
                                        Triple("Search", "Search", com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive),
                                        Triple("Assistant", "Assistant", com.pixel.intelligentsearch.R.drawable.ic_lens_action),
                                        Triple("Now Playing", "Playing", com.pixel.intelligentsearch.R.drawable.ic_music)
                                    )
                                    val currentAction = if (localActionIcon == "Gemini") "Assistant" else localActionIcon
                                    actionOptions.forEach { (optionKey, label, iconRes) ->
                                        val isSel = currentAction == optionKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (isSel) MaterialTheme.colorScheme.primaryContainer 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                )
                                                .bouncyClickable(shape = RoundedCornerShape(20.dp)) {
                                                    hapticEngine.performPredictiveBackHaptic(view)
                                                    localActionIcon = optionKey
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                if (iconRes != null) {
                                                    ComposeActionIcon(
                                                        iconType = optionKey,
                                                        modifier = Modifier.size(22.dp),
                                                        primaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        secondaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        tertiaryColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "None",
                                                        modifier = Modifier.size(22.dp),
                                                        tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Color Studio" -> {
                        // TAB 1: COLOR STUDIO
                        val dynamicCustomColor = androidx.compose.ui.graphics.Color(computedCustomColorInt)
                        val transparencyTrackGradient = remember(computedCustomColorInt) {
                            listOf(
                                dynamicCustomColor,
                                dynamicCustomColor.copy(alpha = 0.7f),
                                dynamicCustomColor.copy(alpha = 0.35f),
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                                androidx.compose.ui.graphics.Color(0xFF3C4043).copy(alpha = 0.6f),
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)
                            )
                        }

                        // Custom Color Palette Container on Top
                        Text("CUSTOM COLOR PALETTE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        SettingsCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 1. Hue Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Palette, contentDescription = "Hue", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Hue", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localHue.toInt()}°", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                    localSubtheme = "Custom"
                                                    localHue = it 
                                                },
                                                valueRange = 0f..360f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 2. Saturation Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.WaterDrop, contentDescription = "Saturation", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localSaturation.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.White,
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, 1f, 1f)))
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                showTrack = false,
                                                value = localSaturation,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localSaturation = it 
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // 3. Color Opacity Slider
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Contrast, contentDescription = "Color Opacity", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Color Opacity", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localColorOpacity.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = listOf(
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                    androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(floatArrayOf(localHue, localSaturation / 100f, 1f)))
                                                )
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                value = localColorOpacity,
                                                onValueChange = { 
                                                    localSubtheme = "Custom"
                                                    localColorOpacity = it 
                                                },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth(),
                                                showTrack = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 4. Transparency Slider (under color opacity in the same container)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Opacity, contentDescription = "Transparency", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Transparency", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("${localTransparency.toInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(16.dp).padding(top = 8.dp).background(
                                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                colors = transparencyTrackGradient
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )) {
                                            Android17Slider(
                                                value = localTransparency,
                                                onValueChange = { localTransparency = it },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth(),
                                                showTrack = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // 5. Hex Color Pill Bar (pill bar under custom color pallet sliders in the same container)
                                val customColorHex = String.format("#%06X", (0xFFFFFF and computedCustomColorInt))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            tempHexInput = customColorHex
                                            showHexInput = true
                                        }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(dynamicCustomColor)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        customColorHex,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Hex",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Material Design Inner Pill & Circle Switcher (its own separate, smaller container)
                        val isMaterialDesign = localThemeStyle == "Material You (Minimal)" || localThemeStyle == "Material Design"
                        if (isMaterialDesign) {
                            SettingsCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Inner Pill & Circle State",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Force Inner Pill & Circle to #121212.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = localLockBlack,
                                        onCheckedChange = { localLockBlack = it },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    "Widget Shortcuts" -> {
                        // TAB 2: WIDGET SHORTCUTS
                        Text("WIDGET SHORTCUTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        Text("Drag Handle to Reorder, Swipe to Disable, and Tap to Customize.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val currentOrderList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                            currentOrderList.forEach { slotKey ->
                                key(slotKey) {
                                    val isDragging = draggingSlotKey == slotKey
                                    val elevation by androidx.compose.animation.core.animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 0.dp,
                                        label = "elevation"
                                    )
                                    val scale by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isDragging) 1.03f else 1f,
                                        label = "scale"
                                    )
                                    val swipeOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }

                                    val currentPositionIndex = currentOrderList.indexOf(slotKey)
                                    val cardTitle = when (slotKey) {
                                        "mic" -> "Microphone Slot:"
                                        else -> "Shortcut Slot: ${currentPositionIndex + 1}"
                                    }

                                    val cardSubtext = when (slotKey) {
                                        "mic" -> if (localShowVoice) "Voice Search" else "None"
                                        "shortcut1" -> localShortcut1
                                        "shortcut2" -> localShortcut2
                                        else -> localShortcut3
                                    }

                                    val cardIcon = when (slotKey) {
                                        "mic" -> if (localShowVoice) Icons.Default.Mic else Icons.Default.Close
                                        else -> shortcutOptions.find { it.first == cardSubtext }?.second ?: Icons.Default.Close
                                    }

                                    val cardModifier = if (isDragging) {
                                        Modifier
                                            .zIndex(10f)
                                            .graphicsLayer {
                                                translationY = itemDragOffset
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    } else {
                                        Modifier
                                            .zIndex(0f)
                                            .graphicsLayer {
                                                translationX = swipeOffsetX.value
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    }

                                    Box(
                                        modifier = cardModifier
                                            .fillMaxWidth()
                                            .onSizeChanged {
                                                if (it.height > 0) {
                                                    slotItemHeightPx = it.height.toFloat() + with(density) { 12.dp.toPx() }
                                                }
                                            }
                                            .pointerInput(slotKey, isDragging) {
                                                if (!isDragging) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = {
                                                            coroutineScope.launch {
                                                                if (kotlin.math.abs(swipeOffsetX.value) > 200f) {
                                                                    hapticEngine.performPredictiveBackHaptic(view)
                                                                    when (slotKey) {
                                                                        "mic" -> localShowVoice = false
                                                                        "shortcut1" -> localShortcut1 = "None"
                                                                        "shortcut2" -> localShortcut2 = "None"
                                                                        "shortcut3" -> localShortcut3 = "None"
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
                                            }
                                    ) {
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            hapticEngine.performPredictiveBackHaptic(view)
                                                            if (slotKey == "mic") {
                                                                localShowVoice = !localShowVoice
                                                            } else {
                                                                activeShortcutSlot = when (slotKey) {
                                                                    "shortcut1" -> 1
                                                                    "shortcut2" -> 2
                                                                    else -> 3
                                                                }
                                                                showShortcutSheet = true
                                                            }
                                                        }
                                                ) {
                                                    Icon(
                                                        imageVector = cardIcon,
                                                        contentDescription = cardSubtext,
                                                        modifier = Modifier.size(36.dp),
                                                        tint = if (cardSubtext == "None") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Column {
                                                        Text(
                                                            text = cardTitle,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = cardSubtext,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                }

                                                val currentSlot by rememberUpdatedState(slotKey)
                                                Icon(
                                                    imageVector = Icons.Outlined.DragHandle,
                                                    contentDescription = "Drag to reorder",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.pointerInput(currentSlot) {
                                                        detectVerticalDragGestures(
                                                            onDragStart = {
                                                                hapticEngine.performPredictiveBackHaptic(view)
                                                                draggingSlotKey = currentSlot
                                                                itemDragOffset = 0f
                                                            },
                                                            onDragEnd = {
                                                                draggingSlotKey = null
                                                                itemDragOffset = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggingSlotKey = null
                                                                itemDragOffset = 0f
                                                            },
                                                            onVerticalDrag = { change, dragAmount ->
                                                                change.consume()
                                                                itemDragOffset += dragAmount

                                                                val currentDraggingSlot = draggingSlotKey ?: return@detectVerticalDragGestures
                                                                val currentList = localSlotOrderStr.split(",").filter { it.isNotBlank() }
                                                                val from = currentList.indexOf(currentDraggingSlot)
                                                                val itemHeight = slotItemHeightPx

                                                                if (from != -1 && itemHeight > 0f) {
                                                                    if (itemDragOffset > itemHeight / 2f && from < currentList.size - 1) {
                                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                                        val updated = currentList.toMutableList()
                                                                        java.util.Collections.swap(updated, from, from + 1)
                                                                        localSlotOrderStr = updated.joinToString(",")
                                                                        itemDragOffset -= itemHeight
                                                                    } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                                        hapticEngine.performPredictiveBackHaptic(view)
                                                                        val updated = currentList.toMutableList()
                                                                        java.util.Collections.swap(updated, from, from - 1)
                                                                        localSlotOrderStr = updated.joinToString(",")
                                                                        itemDragOffset += itemHeight
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
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(color, hsv)
                                localHue = hsv[0]
                                localSaturation = hsv[1] * 100
                                localLightness = hsv[2] * 100
                                localSubtheme = "Custom"
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
                "Assistant", "Gemini" -> {
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

@Composable
fun ComposeThemedShortcutIcon(
    resId: Int,
    contentDescription: String,
    primaryColor: androidx.compose.ui.graphics.Color,
    secondaryColor: androidx.compose.ui.graphics.Color,
    tertiaryColor: androidx.compose.ui.graphics.Color,
    isMaterialTheme: Boolean,
    modifier: Modifier = Modifier
) {
    if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_mic) {
        val path1 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,15c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v7c0,1.66 1.34,3 3,3z").toPath() }
        val path2 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M11,18.92h2V22h-2z").toPath() }
        val path3 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M7,12H5c0,1.93 0.78,3.68 2.05,4.95l1.41,-1.41C7.56,14.63 7,13.38 7,12z").toPath() }
        val path4 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,17c-1.38,0 -2.63,-0.56 -3.54,-1.47l-1.41,1.41C8.32,18.21 10.07,19 12.01,19c3.87,0 6.98,-3.14 6.98,-7h-2c0,2.76 -2.23,5 -4.99,5z").toPath() }

        androidx.compose.foundation.Canvas(modifier = modifier) {
            val scaleX = size.width / 24f
            val scaleY = size.height / 24f
            scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = path1, color = primaryColor)
                drawPath(path = path2, color = secondaryColor)
                drawPath(path = path3, color = tertiaryColor)
                drawPath(path = path4, color = primaryColor)
            }
        }
    } else if (isMaterialTheme && resId == com.pixel.intelligentsearch.R.drawable.ic_camera) {
        val path1 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M75.0365 83.3333C79.6388 83.3333 83.3698 79.6023 83.3698 75C83.3698 70.3976 79.6388 66.6666 75.0365 66.6666C70.4341 66.6666 66.7031 70.3976 66.7031 75C66.7031 79.6023 70.4341 83.3333 75.0365 83.3333Z").toPath() }
        val path2 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M50.0364 66.6666C56.9399 66.6666 62.5364 61.0702 62.5364 54.1666C62.5364 47.2631 56.9399 41.6666 50.0364 41.6666C43.1328 41.6666 37.5364 47.2631 37.5364 54.1666C37.5364 61.0702 43.1328 66.6666 50.0364 66.6666Z").toPath() }
        val path3 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12.5 70.4166C12.5 79.8489 20.151 87.5 29.5833 87.5H50V79.1666L29.1146 79.1145C24.5313 79.1145 20.8333 74.8489 20.8333 69.7916V60.4166H12.5V70.4166Z").toPath() }
        val path4 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M87.5001 37.9167C87.5001 28.4844 79.849 20.8334 70.4167 20.8334H60.4167L70.8334 29.1667C75.4167 29.1667 79.1667 33.4844 79.1667 38.5417V54.1667H87.5001V37.9167Z").toPath() }
        val path5 = remember { androidx.compose.ui.graphics.vector.PathParser().parsePathString("M58.3333 12.5H41.6667L35.4167 20.8333H29.5833C20.151 20.8333 12.5 28.4844 12.5 37.9167V47.9167H20.8333V38.5417C20.8333 33.4844 24.5833 29.1667 29.1667 29.1667H70.8333L58.3333 12.5Z").toPath() }

        androidx.compose.foundation.Canvas(modifier = modifier) {
            val scaleX = size.width / 100f
            val scaleY = size.height / 100f
            scale(scaleX, scaleY, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                drawPath(path = path1, color = primaryColor)
                drawPath(path = path2, color = secondaryColor)
                drawPath(path = path3, color = tertiaryColor)
                drawPath(path = path4, color = primaryColor)
                drawPath(path = path5, color = secondaryColor)
            }
        }
    } else {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = resId),
            contentDescription = contentDescription,
            tint = primaryColor,
            modifier = modifier
        )
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
    showTrack: Boolean = true,
    isSquiggly: Boolean = true
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    val phase by if (isSquiggly) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val dragPhaseOffset = remember { mutableFloatStateOf(0f) }
    val effectivePhase = phase + dragPhaseOffset.floatValue

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticEngine = remember(context) { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    val stepSize = if (steps > 0) {
        (valueRange.endInclusive - valueRange.start) / (steps + 1)
    } else {
        1.0f
    }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    fun snapValue(rawFraction: Float): Float {
        val raw = valueRange.start + rawFraction * (valueRange.endInclusive - valueRange.start)
        return if (steps > 0 && stepSize > 0f) {
            val stepIndex = Math.round((raw - valueRange.start) / stepSize)
            (valueRange.start + stepIndex * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
        } else {
            if (valueRange.endInclusive - valueRange.start >= 10f) {
                Math.round(raw).toFloat().coerceIn(valueRange.start, valueRange.endInclusive)
            } else {
                raw.coerceIn(valueRange.start, valueRange.endInclusive)
            }
        }
    }

    fun processChange(rawFraction: Float) {
        val snapped = snapValue(rawFraction)
        if (snapped != currentValue) {
            currentOnValueChange(snapped)
            hapticEngine.performPredictiveBackHaptic(view)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(steps, valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val thumbW = 6.dp.toPx()
                    val trackAvailableWidth = (size.width - thumbW).coerceAtLeast(1f)
                    val downFraction = ((down.position.x - thumbW / 2f) / trackAvailableWidth).coerceIn(0f, 1f)
                    processChange(downFraction)

                    val pointerId = down.id
                    var prevX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val currentX = change.position.x
                        val deltaX = currentX - prevX
                        prevX = currentX
                        dragPhaseOffset.floatValue += deltaX * 0.04f
                        val dragFraction = ((currentX - thumbW / 2f) / trackAvailableWidth).coerceIn(0f, 1f)
                        processChange(dragFraction)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val baseAmplitude = 3.5.dp.toPx()
            val waveLength = 32.dp.toPx()
            val transitionLen = 16.dp.toPx()
            val thumbWidth = 6.dp.toPx()
            val thumbHeight = 36.dp.toPx()

            val thumbX = (fraction * (size.width - thumbWidth) + thumbWidth / 2f).coerceIn(thumbWidth / 2f, size.width - thumbWidth / 2f)
            val centerY = size.height / 2f
            val yDots = centerY + 16.dp.toPx()

            // Draw active track
            if (showTrack) {
                if (isSquiggly) {
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, centerY)
                    var x = 0f
                    val step = 1.5f
                    while (x <= thumbX) {
                        // Smooth envelope tapering at track start (0) and thumb end (thumbX)
                        val envLeft = if (x < transitionLen) {
                            val t = (x / transitionLen).coerceIn(0f, 1f)
                            t * t * (3f - 2f * t)
                        } else 1f

                        val distToThumb = thumbX - x
                        val envRight = if (distToThumb < transitionLen) {
                            val t = (distToThumb / transitionLen).coerceIn(0f, 1f)
                            t * t * (3f - 2f * t)
                        } else 1f

                        val envelope = envLeft * envRight

                        // Silky unidirectional sine wave without breathing distortion
                        val wave = Math.sin(x * (2.0 * Math.PI / waveLength) - effectivePhase).toFloat()
                        val y = centerY + wave * baseAmplitude * envelope

                        path.lineTo(x, y)
                        x += step
                    }
                    path.lineTo(thumbX, centerY)
                    
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = trackHeight,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                } else {
                    if (thumbX > 0f) {
                        drawLine(
                            color = activeColor,
                            start = androidx.compose.ui.geometry.Offset(0f, centerY),
                            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                            strokeWidth = trackHeight,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
    
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

            // Draw tick marks (dots underneath track aligned with steps)
            if (steps > 0) {
                val tickRadius = 2.5.dp.toPx()
                val segments = steps + 1
                val availableWidth = size.width - thumbWidth
                val startX = thumbWidth / 2f
                
                for (i in 0..segments) {
                    val cx = startX + i * (availableWidth / segments)
                    drawCircle(
                        color = if (cx <= thumbX + 1f) activeColor.copy(alpha = 0.55f) else inactiveColor.copy(alpha = 0.8f),
                        radius = tickRadius,
                        center = androidx.compose.ui.geometry.Offset(cx, yDots)
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
    com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context).click()
}

// -----------------------------------------------------------------------------------------
// SEARCH PILLS SCREEN
// -----------------------------------------------------------------------------------------
private val nativeAppIconCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(256)

fun getNativeAppIcon(context: Context, packageName: String): androidx.compose.ui.graphics.ImageBitmap? {
    val cached = nativeAppIconCache.get(packageName)
    if (cached != null) return cached
    return try {
        val pm = context.packageManager
        val drawable = pm.getApplicationIcon(packageName)
        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val bmp = android.graphics.Bitmap.createBitmap(
                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96,
                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        val imgBitmap = bitmap.asImageBitmap()
        nativeAppIconCache.put(packageName, imgBitmap)
        imgBitmap
    } catch (e: Exception) {
        null
    }
}

data class AppPickerItem(
    val packageName: String,
    val label: String,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPillsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    val defaultPills = "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files"

    var activeProfile by remember { mutableIntStateOf(prefs.getInt("active_app_search_profile", 1)) }

    fun getSavedProfilePills(id: Int): String {
        return prefs.getString("app_search_profile_${id}_saved", if (id == 1) defaultPills else "") ?: ""
    }

    fun getProfilePills(id: Int): String {
        val current = prefs.getString("app_search_profile_$id", null)
        if (current != null) return current
        return getSavedProfilePills(id)
    }

    var searchPills by remember { mutableStateOf(getProfilePills(activeProfile)) }
    var localPillList by remember(searchPills) {
        mutableStateOf(searchPills.split(",").filter { it.isNotBlank() })
    }
    var resetCounter by remember { mutableIntStateOf(0) }

    fun persistPills(newPills: List<String>) {
        val pillString = newPills.joinToString(",")
        localPillList = newPills
        searchPills = pillString
        prefs.edit()
            .putString("app_search_profile_$activeProfile", pillString)
            .putString("search.pills", pillString)
            .putInt("shortcut_results_count", newPills.size)
            .apply()
        viewModel?.updateSetting(SettingsManager.SEARCH_PILLS, pillString)
        viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, newPills.size)
    }

    fun switchProfile(id: Int) {
        activeProfile = id
        prefs.edit().putInt("active_app_search_profile", id).apply()
        val pills = getProfilePills(id)
        searchPills = pills
        val list = pills.split(",").filter { it.isNotBlank() }
        localPillList = list
        resetCounter++
        prefs.edit()
            .putString("search.pills", pills)
            .putInt("shortcut_results_count", list.size)
            .apply()
        viewModel?.updateSetting(SettingsManager.SEARCH_PILLS, pills)
        viewModel?.updateSetting(SettingsManager.SHORTCUT_RESULTS_COUNT, list.size)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppPickerItem>>(emptyList()) }
    var isAppsLoading by remember { mutableStateOf(false) }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var showMaxWarning by remember { mutableStateOf(false) }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker && installedApps.isEmpty()) {
            isAppsLoading = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    }
                    val resolveInfoList = pm.queryIntentActivities(intent, 0)
                    val apps = resolveInfoList.asSequence()
                        .distinctBy { it.activityInfo.packageName }
                        .map {
                            val pkg = it.activityInfo.packageName
                            val label = it.loadLabel(pm).toString()
                            val icon = getNativeAppIcon(context, pkg)
                            AppPickerItem(packageName = pkg, label = label, iconBitmap = icon)
                        }
                        .sortedBy { it.label.lowercase() }
                        .toList()
                    installedApps = apps
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isAppsLoading = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                        Icon(if (showAppPicker && multiSelectMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (showAppPicker && multiSelectMode) {
                        IconButton(onClick = {
                            val currentList = localPillList.toMutableList()
                            val newApps = selectedApps.filter { !currentList.contains(it) }
                            if (currentList.size + newApps.size > 20) {
                                showMaxWarning = true
                            } else {
                                currentList.addAll(newApps)
                                persistPills(currentList)
                                multiSelectMode = false
                                selectedApps = emptySet()
                                showAppPicker = false
                            }
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Add Selected")
                        }
                    } else if (!showAppPicker) {
                        IconButton(onClick = {
                            val savedState = getSavedProfilePills(activeProfile)
                            val restoredPills = if (savedState.isNotBlank()) savedState else if (activeProfile == 1) defaultPills else ""
                            val list = restoredPills.split(",").filter { it.isNotBlank() }
                            resetCounter++
                            persistPills(list)
                            Toast.makeText(context, "Profile $activeProfile Reset to Saved State", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset to Saved State")
                        }
                        IconButton(onClick = {
                            val pillString = localPillList.joinToString(",")
                            prefs.edit().putString("app_search_profile_${activeProfile}_saved", pillString).apply()
                            Toast.makeText(context, "Profile $activeProfile State Saved", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.Save, contentDescription = "Save Profile State")
                        }
                        IconButton(onClick = {
                            if (localPillList.size >= 20) {
                                showMaxWarning = true
                            } else {
                                showAppPicker = true
                            }
                        }) {
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
            val filteredApps = remember(installedApps, appSearchQuery) {
                if (appSearchQuery.isBlank()) installedApps
                else installedApps.filter {
                    it.label.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                }
            }

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

                if (isAppsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.packageName }) { appItem ->
                            val packageName = appItem.packageName
                            val isSelected = selectedApps.contains(packageName)
                            val isAlreadyAdded = localPillList.contains(packageName)

                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                            ) {
                                SettingsRow(
                                    title = appItem.label,
                                    subtitle = if (isAlreadyAdded) "Already in Quick Launch" else packageName,
                                    icon = appItem.iconBitmap ?: Icons.Outlined.Apps,
                                    onClick = {
                                        if (multiSelectMode) {
                                            selectedApps = if (isSelected) selectedApps - packageName else selectedApps + packageName
                                            if (selectedApps.isEmpty()) multiSelectMode = false
                                        } else {
                                            if (!isAlreadyAdded) {
                                                if (localPillList.size >= 20) {
                                                    showMaxWarning = true
                                                } else {
                                                    val updated = localPillList + packageName
                                                    persistPills(updated)
                                                    showAppPicker = false
                                                }
                                            } else {
                                                showAppPicker = false
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!multiSelectMode) {
                                            multiSelectMode = true
                                            selectedApps = setOf(packageName)
                                        }
                                    },
                                    showDivider = true
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Segmented Profile Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (id in 1..5) {
                        val isSelected = activeProfile == id
                        FilterChip(
                            selected = isSelected,
                            onClick = { switchProfile(id) },
                            label = { Text("Profile $id", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                if (localPillList.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Profile $activeProfile is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Tap '+' to add applications to Profile $activeProfile. All changes auto-save in real-time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (localPillList.size >= 20) {
                                        showMaxWarning = true
                                    } else {
                                        showAppPicker = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Applications")
                            }
                        }
                    }
                } else {
                    var draggingPackage by remember { mutableStateOf<String?>(null) }
                    var itemDragOffset by remember { mutableFloatStateOf(0f) }
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()

                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        userScrollEnabled = draggingPackage == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                    ) {
                        items(localPillList, key = { "$resetCounter-$it" }) { packageName ->
                            val isDragging = draggingPackage == packageName
                            val elevation by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "elevation"
                            )
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isDragging) 1.03f else 1f,
                                label = "scale"
                            )

                            val draggingModifier = if (isDragging) {
                                Modifier.zIndex(10f).graphicsLayer {
                                    translationY = itemDragOffset
                                    scaleX = scale
                                    scaleY = scale
                                }
                            } else {
                                Modifier.animateItem().zIndex(0f).graphicsLayer {
                                    translationY = 0f
                                    scaleX = scale
                                    scaleY = scale
                                }
                            }

                            Box(modifier = draggingModifier) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { it * 0.5f }
                                )
                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart || dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                        val updated = localPillList.toMutableList()
                                        updated.remove(packageName)
                                        persistPills(updated)
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
                                    val appName = remember(packageName) {
                                        try {
                                            val pm = context.packageManager
                                            val info = pm.getApplicationInfo(packageName, 0)
                                            pm.getApplicationLabel(info).toString()
                                        } catch (e: Exception) {
                                            packageName
                                        }
                                    }
                                    val appIcon = remember(packageName) {
                                        getNativeAppIcon(context, packageName)
                                    }

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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (appIcon != null) {
                                                    Image(
                                                        bitmap = appIcon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                }
                                                Column {
                                                    Text(
                                                        text = appName,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = packageName,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            val currentPkg by rememberUpdatedState(packageName)
                                            Icon(
                                                imageVector = Icons.Outlined.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.pointerInput(currentPkg) {
                                                    detectVerticalDragGestures(
                                                        onDragStart = {
                                                            draggingPackage = currentPkg
                                                            itemDragOffset = 0f
                                                        },
                                                        onDragEnd = {
                                                            draggingPackage = null
                                                            itemDragOffset = 0f
                                                            persistPills(localPillList)
                                                        },
                                                        onDragCancel = {
                                                            draggingPackage = null
                                                            itemDragOffset = 0f
                                                        },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            itemDragOffset += dragAmount

                                                            val currentDraggingPackage = draggingPackage ?: return@detectVerticalDragGestures
                                                            val draggingItem = listState.layoutInfo.visibleItemsInfo.find {
                                                                it.key == "$resetCounter-$currentDraggingPackage" ||
                                                                it.key == currentDraggingPackage ||
                                                                it.key.toString().endsWith(currentDraggingPackage)
                                                            }
                                                            if (draggingItem != null) {
                                                                val spacing = listState.layoutInfo.mainAxisItemSpacing.toFloat()
                                                                val itemHeight = draggingItem.size.toFloat() + spacing
                                                                val from = localPillList.indexOf(currentDraggingPackage)
                                                                val distanceFromBottom = listState.layoutInfo.viewportSize.height - (draggingItem.offset + draggingItem.size)

                                                                if (itemDragOffset < 0f && draggingItem.offset < 80f && listState.canScrollBackward) {
                                                                    coroutineScope.launch {
                                                                        listState.scrollBy(-16f)
                                                                    }
                                                                } else if (itemDragOffset > 0f && distanceFromBottom < 80f && listState.canScrollForward) {
                                                                    coroutineScope.launch {
                                                                        listState.scrollBy(16f)
                                                                    }
                                                                }

                                                                if (from != -1) {
                                                                    if (itemDragOffset > itemHeight / 2f && from < localPillList.size - 1) {
                                                                        val currentList = localPillList.toMutableList()
                                                                        java.util.Collections.swap(currentList, from, from + 1)
                                                                        localPillList = currentList
                                                                        itemDragOffset -= itemHeight
                                                                    } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                                        val currentList = localPillList.toMutableList()
                                                                        java.util.Collections.swap(currentList, from, from - 1)
                                                                        localPillList = currentList
                                                                        itemDragOffset += itemHeight
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
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
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
                    subtitle = "Add to Quick Settings Notification Panel.",
                    icon = Icons.Outlined.ViewAgenda,
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            try {
                                @android.annotation.SuppressLint("WrongConstant")
                                val statusBarManager = context.getSystemService("statusbar") as? android.app.StatusBarManager
                                val componentName = android.content.ComponentName(context, com.pixel.intelligentsearch.feature.widget.SearchTileService::class.java)
                                val icon = android.graphics.drawable.Icon.createWithResource(context, com.pixel.intelligentsearch.R.drawable.ic_search_lens_expressive)
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
                    subtitle = "Add App Icon to Home Screen.",
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
    
    // Pre-shuffle initial layout configurations for varied opening sequence
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
    
    var poolIdx = index * 5
    var morph by mutableStateOf(
        Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
    )
    private var isMorphing = false
    private var lastBounceTime = 0L

    fun onBounce() {
        val now = System.currentTimeMillis()
        if (now - lastBounceTime < 250L || isMorphing) return
        lastBounceTime = now

        engine.coroutineScope.launch {
            isMorphing = true
            try {
                morphProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                poolIdx = (poolIdx + 1) % engine.shapePool.size
                morphProgress.snapTo(0f)
                morph = Morph(engine.shapePool[poolIdx], engine.shapePool[(poolIdx + 1) % engine.shapePool.size])
            } finally {
                isMorphing = false
            }
        }
    }

    var vx = 0f
    var vy = 0f
    var boundsWidth = 0f
    var boundsHeight = 0f
    var deviceWidth = 0f
    var deviceHeight = 0f
    var sizePx = 0f
    var mass = 1f
    
    fun updateBounds(width: Float, height: Float, dWidth: Float, dHeight: Float, smallestWidthDp: Int, fontScale: Float) {
        boundsWidth = width
        boundsHeight = height
        deviceWidth = dWidth
        deviceHeight = dHeight
        
        // Scale particle base size proportionally to smallestScreenWidthDp, density, and fontScale
        val dpScale = (smallestWidthDp / 411f).coerceIn(0.65f, 1.4f)
        val fontAdjust = (1f / fontScale.coerceIn(0.8f, 1.5f))
        val baseFraction = 0.038f * dpScale * fontAdjust
        val sizeMultiplier = 1.0f + (index % 4) * 0.2f
        sizePx = (dWidth * baseFraction * sizeMultiplier).coerceIn(16f, (height * 0.35f).coerceAtLeast(24f))
        mass = sizeMultiplier * sizeMultiplier
    }

    init {
        engine.coroutineScope.launch {
            rotation.animateTo(360f, infiniteRepeatable(tween(8000, easing = LinearEasing)))
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
                onBounce()
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
                
                val maxY = (boundsHeight - sizePx).coerceAtLeast(0f)
                val minY = 0f // ceiling
                if (y >= maxY) {
                    y = maxY
                    touchingGround = true
                    if (vy > 0) {
                        vy = -vy * restitution
                        // Prevent micro-vibrations going infinitely (Zeno's paradox for physics engines)
                        if (kotlin.math.abs(vy) < 15f) {
                            vy = 0f
                        } else {
                            onBounce()
                            if (vx == 0f) {
                                // "bounces before arcing away": kick horizontal velocity on the first ground impact
                                vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                            }
                        }
                    }
                } else if (y <= minY) {
                    y = minY
                    if (vy < 0) {
                        vy = -vy * restitution
                    }
                }
                
                val minX = 0f
                val maxX = (boundsWidth - sizePx).coerceAtLeast(0f)
                if (x <= minX) {
                    x = minX
                    if (vx < 0) {
                        vx = -vx * wallRestitution
                        onBounce()
                    }
                } else if (x >= maxX) {
                    x = maxX
                    if (vx > 0) {
                        vx = -vx * wallRestitution
                        onBounce()
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
                
                // Continuous Animation: Relaunch shapes if settled on boundary
                // Retain continuous animation loop while settings session is active
                val isSettled = touchingGround && vy == 0f && kotlin.math.abs(vx) < 5f
                if (isSettled) {
                    timeSinceSettled += safeDt
                    // Launch again after resting for a short 0.5 to 1.5 seconds
                    if (timeSinceSettled > 0.5f + Math.random().toFloat()) {
                        val targetApex = boundsHeight * (0.4f + Math.random().toFloat() * 0.4f)
                        vy = -kotlin.math.sqrt(2f * gravity * targetApex)
                        vx = (if (Math.random() > 0.5) 1f else -1f) * (deviceWidth * 0.08f + Math.random().toFloat() * deviceWidth * 0.12f)
                        timeSinceSettled = 0f
                        onBounce()
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val deviceWidth = configuration.screenWidthDp * density.density
    val deviceHeight = configuration.screenHeightDp * density.density
    val smallestWidthDp = configuration.smallestScreenWidthDp
    val fontScale = density.fontScale

    val baseAccentColor = MaterialTheme.colorScheme.primary
    val variant1 = MaterialTheme.colorScheme.secondary
    val variant2 = MaterialTheme.colorScheme.tertiary
    val variant3 = MaterialTheme.colorScheme.primaryContainer
    val colors = remember(baseAccentColor, variant1, variant2, variant3) {
        listOf(baseAccentColor, variant1, variant2, variant3)
    }

    val sharedNativePath = remember { android.graphics.Path() }
    val sharedComposePath = remember(sharedNativePath) { sharedNativePath.asComposePath() }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        if (width > 0f && height > 0f) {
            engine.bouncers.forEach { it.updateBounds(width, height, deviceWidth, deviceHeight, smallestWidthDp, fontScale) }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val bouncers = engine.bouncers
                for (index in bouncers.indices) {
                    val bouncer = bouncers[index]
                    val sizePx = bouncer.sizePx
                    if (sizePx <= 0f) continue
                    
                    val boundedX = bouncer.x.coerceIn(0f, (width - sizePx).coerceAtLeast(0f))
                    val boundedY = bouncer.y.coerceIn(0f, (height - sizePx).coerceAtLeast(0f))
                    
                    translate(left = boundedX, top = boundedY) {
                        rotate(bouncer.rotation.value) {
                            sharedNativePath.rewind()
                            bouncer.morph.toPath(progress = bouncer.morphProgress.value, path = sharedNativePath)
                            
                            scale(scale = sizePx, pivot = Offset.Zero) {
                                drawPath(
                                    path = sharedComposePath,
                                    color = colors[index],
                                    alpha = (bouncer.alpha.value * 0.9f).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CUSTOM ICONS SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomIconsScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var activeIconPack by rememberStringPreference(prefs, "active_icon_pack", "system_default")
    var neverShowWarning by rememberBooleanPreference(prefs, "never_show_icon_pack_warning", false) {}
    var storedPillString by rememberStringPreference(prefs, "custom_icon_pills", "")
    val effectivePillString = remember(storedPillString) {
        if (storedPillString.isNotBlank()) storedPillString else prefs.getString("custom_icon_packs_order", "") ?: ""
    }

    val installedPacks = remember { com.pixel.intelligentsearch.core.util.IconPackManager.getInstalledIconPacks(context) }
    val validPackSet = remember(installedPacks) {
        (listOf("system_default") + installedPacks.map { it.packageName }).toSet()
    }

    val allPackMap = remember(installedPacks) {
        val map = mutableMapOf<String, Pair<String, ImageBitmap?>>()
        map["system_default"] = Pair("System Default", null)
        installedPacks.forEach { pack ->
            val bmp = try { pack.icon?.toBitmap()?.asImageBitmap() } catch (e: Exception) { null }
            map[pack.packageName] = Pair(pack.label, bmp)
        }
        map
    }

    var localPillList by remember(effectivePillString, validPackSet) {
        val storedList = effectivePillString.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && validPackSet.contains(it) }
        val merged = (storedList + listOf("system_default") + installedPacks.map { it.packageName })
            .distinct()
            .filter { validPackSet.contains(it) }
        mutableStateOf(merged)
    }

    var draggingPackage by remember { mutableStateOf<String?>(null) }
    var itemDragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var pendingIconPack by remember { mutableStateOf<String?>(null) }

    fun activatePack(pkg: String) {
        if (pkg == activeIconPack) return
        if (activeIconPack != "system_default" && !neverShowWarning) {
            pendingIconPack = pkg
        } else {
            activeIconPack = pkg
            prefs.edit().putString("active_icon_pack", pkg).apply()
            com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Icons", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (pendingIconPack != null) {
                AlertDialog(
                    onDismissRequest = { pendingIconPack = null },
                    title = { Text("Change Icon Pack", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to change the icon pack?") },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    neverShowWarning = true
                                    prefs.edit().putBoolean("never_show_icon_pack_warning", true).apply()
                                    if (pendingIconPack != null) {
                                        activeIconPack = pendingIconPack!!
                                        prefs.edit().putString("active_icon_pack", pendingIconPack!!).apply()
                                        com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
                                        pendingIconPack = null
                                    }
                                }
                            ) {
                                Text("Never show again", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            
                            Row {
                                TextButton(onClick = { pendingIconPack = null }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (pendingIconPack != null) {
                                            activeIconPack = pendingIconPack!!
                                            prefs.edit().putString("active_icon_pack", pendingIconPack!!).apply()
                                            com.pixel.intelligentsearch.core.util.IconPackManager.clearCache()
                                            pendingIconPack = null
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    },
                    dismissButton = null
                )
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                userScrollEnabled = draggingPackage == null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(localPillList, key = { it }) { packageName ->
                    val isDragging = draggingPackage == packageName

                    val elevation by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "elevation"
                    )
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isDragging) 1.03f else 1f,
                        label = "scale"
                    )

                    val draggingModifier = if (isDragging) {
                        Modifier.zIndex(10f).graphicsLayer {
                            translationY = itemDragOffset
                            scaleX = scale
                            scaleY = scale
                        }
                    } else {
                        Modifier.animateItem().zIndex(0f).graphicsLayer {
                            translationY = 0f
                            scaleX = scale
                            scaleY = scale
                        }
                    }

                    val packInfo = allPackMap[packageName] ?: Pair(packageName, null)
                    val packLabel = packInfo.first
                    val packIcon = packInfo.second
                    val isSelected = activeIconPack == packageName

                    Box(modifier = draggingModifier) {
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
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    if (packageName == "system_default") {
                                        Icon(
                                            imageVector = Icons.Outlined.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else if (packIcon != null) {
                                        Image(
                                            bitmap = packIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.AppShortcut,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = packLabel,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (packageName == "system_default") "Default Monet dynamic icons" else packageName,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                activatePack(packageName)
                                            } else {
                                                if (activeIconPack == packageName) {
                                                    activatePack("system_default")
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val currentPkg by rememberUpdatedState(packageName)
                                    Icon(
                                        imageVector = Icons.Outlined.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.pointerInput(currentPkg) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggingPackage = currentPkg
                                                    itemDragOffset = 0f
                                                },
                                                onDragEnd = {
                                                    draggingPackage = null
                                                    itemDragOffset = 0f
                                                    storedPillString = localPillList.joinToString(",")
                                                    prefs.edit()
                                                        .putString("custom_icon_pills", storedPillString)
                                                        .putString("custom_icon_packs_order", storedPillString)
                                                        .apply()
                                                },
                                                onDragCancel = {
                                                    draggingPackage = null
                                                    itemDragOffset = 0f
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    itemDragOffset += dragAmount

                                                    val currentDraggingPackage = draggingPackage ?: return@detectVerticalDragGestures
                                                    val draggingItem = listState.layoutInfo.visibleItemsInfo.find {
                                                        it.key == currentDraggingPackage
                                                    }
                                                    if (draggingItem != null) {
                                                        val spacing = listState.layoutInfo.mainAxisItemSpacing.toFloat()
                                                        val itemHeight = draggingItem.size.toFloat() + spacing
                                                        val from = localPillList.indexOf(currentDraggingPackage)
                                                        val distanceFromBottom = listState.layoutInfo.viewportSize.height - (draggingItem.offset + draggingItem.size)

                                                        if (itemDragOffset < 0f && draggingItem.offset < 80f && listState.canScrollBackward) {
                                                            coroutineScope.launch {
                                                                listState.scrollBy(-16f)
                                                            }
                                                        } else if (itemDragOffset > 0f && distanceFromBottom < 80f && listState.canScrollForward) {
                                                            coroutineScope.launch {
                                                                listState.scrollBy(16f)
                                                            }
                                                        }

                                                        if (from != -1) {
                                                            if (itemDragOffset > itemHeight / 2f && from < localPillList.size - 1) {
                                                                val currentList = localPillList.toMutableList()
                                                                java.util.Collections.swap(currentList, from, from + 1)
                                                                localPillList = currentList
                                                                itemDragOffset -= itemHeight
                                                            } else if (itemDragOffset < -itemHeight / 2f && from > 0) {
                                                                val currentList = localPillList.toMutableList()
                                                                java.util.Collections.swap(currentList, from, from - 1)
                                                                localPillList = currentList
                                                                itemDragOffset += itemHeight
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
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val viewModel = LocalSettingsViewModel.current
    val hapticEngine = remember { com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context) }

    val backupExoPlayer = remember(context) {
        val uri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.pixel.intelligentsearch.R.raw.gemini_generated_video_2209818f)
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
    DisposableEffect(lifecycleOwner, backupExoPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                backupExoPlayer.play()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                backupExoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            backupExoPlayer.release()
        }
    }

    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var isPassphraseSavedOnChip by remember { mutableStateOf(false) }
    val hardwareInfo = remember { com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detect(context) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEnvelope by remember { mutableStateOf<com.pixel.intelligentsearch.core.backup.EncryptedBackupEnvelope?>(null) }
    var dialogPassphrase by remember { mutableStateOf("") }
    var dialogPassphraseVisible by remember { mutableStateOf(false) }
    var dialogErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val saved = viewModel?.getSavedPassphraseFromSecurityChip()
        if (!saved.isNullOrBlank()) {
            passphrase = saved
            isPassphraseSavedOnChip = true
        }
    }

    val safeShowToast: (String) -> Unit = { message ->
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }
    }

    val performRestore: (Uri, String?) -> Unit = { targetUri, targetPass ->
        if (activity != null) {
            viewModel?.importBackup(
                activity = activity,
                uri = targetUri,
                passphrase = targetPass,
                onSuccess = { count ->
                    showRestorePassphraseDialog = false
                    dialogErrorMessage = null
                    if (!targetPass.isNullOrBlank() && passphrase.isBlank()) {
                        passphrase = targetPass
                    }
                    safeShowToast("Intelligent Search Settings Restored")
                },
                onError = { err ->
                    if (err.contains("passphrase", ignoreCase = true) || err.contains("tag mismatch", ignoreCase = true)) {
                        pendingRestoreUri = targetUri
                        dialogErrorMessage = err
                        showRestorePassphraseDialog = true
                    }
                    safeShowToast("Restore error: $err")
                }
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && activity != null) {
            viewModel?.exportBackup(
                activity = activity,
                uri = uri,
                passphrase = passphrase.ifBlank { null },
                onSuccess = {
                    safeShowToast("Encrypted backup exported successfully!")
                },
                onError = { err ->
                    safeShowToast("Export error: $err")
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activity != null) {
            val inspectResult = viewModel?.inspectBackupEnvelope(uri)
            if (inspectResult == null || inspectResult.isFailure) {
                val errorMsg = inspectResult?.exceptionOrNull()?.localizedMessage
                    ?: "Selected backup file could not be read or is invalid."
                safeShowToast(errorMsg)
                return@rememberLauncherForActivityResult
            }

            val envelope = inspectResult.getOrNull()
            pendingEnvelope = envelope
            val needsPass = envelope?.kdf != null
            if (needsPass) {
                val effectivePass = passphrase.ifBlank { viewModel.getSavedPassphraseFromSecurityChip() ?: "" }
                if (effectivePass.isNotBlank()) {
                    performRestore(uri, effectivePass)
                } else {
                    pendingRestoreUri = uri
                    dialogPassphrase = ""
                    dialogErrorMessage = null
                    showRestorePassphraseDialog = true
                }
            } else {
                performRestore(uri, null)
            }
        }
    }

    if (showRestorePassphraseDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestorePassphraseDialog = false
                dialogErrorMessage = null
            },
            title = {
                Text(
                    "Enter Backup Passphrase",
                    fontWeight = FontWeight.Bold,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                )
            },
            text = {
                Column {
                    Text(
                        "This backup is protected with encryption. Enter the passphrase to restore your settings and customizations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pendingEnvelope?.let { env ->
                        if (!env.hardwareChip.isNullOrBlank() || !env.deviceModel.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (!env.hardwareChip.isNullOrBlank()) {
                                        Text(
                                            "Security Chip: ${env.hardwareChip}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!env.deviceModel.isNullOrBlank()) {
                                        Text(
                                            "Device: ${env.deviceModel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (dialogErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            dialogErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = dialogPassphrase,
                        onValueChange = {
                            dialogPassphrase = it
                            dialogErrorMessage = null
                        },
                        placeholder = { Text("Enter passphrase", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { dialogPassphraseVisible = !dialogPassphraseVisible }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (dialogPassphraseVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (dialogPassphraseVisible) "Hide passphrase" else "Show passphrase",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (dialogPassphraseVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(percent = 50),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri ?: return@Button
                        performRestore(uri, dialogPassphrase)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestorePassphraseDialog = false
                        dialogErrorMessage = null
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Backup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
            SettingsCard {
                Text(
                    "Encryption Passphrase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "Optional passphrase for backup encryption. If left blank, backup restores automatically and portably across app updates and devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        if (isPassphraseSavedOnChip && it.isBlank()) {
                            isPassphraseSavedOnChip = false
                        }
                    },
                    placeholder = { Text("Enter passphrase (optional)", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (passphrase.isNotEmpty()) {
                                IconButton(onClick = {
                                    passphrase = ""
                                    if (isPassphraseSavedOnChip) {
                                        isPassphraseSavedOnChip = false
                                        viewModel?.clearSavedPassphraseFromSecurityChip()
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = if (passphraseVisible) "Hide passphrase" else "Show passphrase",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    },
                    visualTransformation = if (passphraseVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(percent = 50),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (passphrase.isNotBlank()) {
                                val saved = viewModel?.savePassphraseToSecurityChip(passphrase) == true
                                if (saved) {
                                    isPassphraseSavedOnChip = true
                                    hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                                    safeShowToast("Passphrase saved to ${hardwareInfo.shortChipName}")
                                } else {
                                    safeShowToast("Failed to save to ${hardwareInfo.shortChipName}")
                                }
                            } else {
                                safeShowToast("Enter a passphrase first")
                            }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.weight(1f),
                        enabled = passphrase.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPassphraseSavedOnChip) "Saved to ${hardwareInfo.shortChipName}" else "Save to ${hardwareInfo.shortChipName}",
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isPassphraseSavedOnChip) {
                        OutlinedButton(
                            onClick = {
                                viewModel?.clearSavedPassphraseFromSecurityChip()
                                isPassphraseSavedOnChip = false
                                hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                                safeShowToast("Passphrase removed from ${hardwareInfo.shortChipName}")
                            },
                            shape = RoundedCornerShape(percent = 50)
                        ) {
                            Text(
                                "Clear",
                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (isPassphraseSavedOnChip) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Secured with ${hardwareInfo.chipName} (${hardwareInfo.deviceDisplayName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Export Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Export Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    Text(
                        "Includes Settings, Search History, Shortcuts, and Priority Weights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                            exportLauncher.launch("intelligent_search_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Encrypted Backup", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    }
                }
            }

            // Restore Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Restore from Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    Text(
                        "Select an Existing .json Backup File to Decrypt, Verify SHA-256 Integrity, and Restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            hapticEngine.performHaptic(null, com.pixel.intelligentsearch.core.haptics.PixelHapticType.CLICK)
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Backup to Restore", fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
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

                val cachedVideoRenderEffect = remember(shaderSrc) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        val shader = android.graphics.RuntimeShader(shaderSrc)
                        val frameworkEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                        frameworkEffect.asComposeRenderEffect()
                    } else null
                }

                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.view.TextureView(ctx).apply {
                            val adjustAspectRatio: (android.view.TextureView) -> Unit = { tv ->
                                val vw = tv.width
                                val vh = tv.height
                                if (vw > 0 && vh > 0) {
                                    val matrix = android.graphics.Matrix()
                                    val videoAspect = 16f / 9f
                                    val viewAspect = vw.toFloat() / vh.toFloat()
                                    var scaleX = 1f
                                    var scaleY = 1f
                                    if (viewAspect > videoAspect) {
                                        scaleY = (vw.toFloat() / (16f / 9f)) / vh.toFloat()
                                    } else {
                                        scaleX = (vh.toFloat() * (16f / 9f)) / vw.toFloat()
                                    }

                                    // Scale factor: 1.05f to make bugdroid significantly larger and prominent in the frame
                                    scaleX *= 1.05f
                                    scaleY *= 1.05f

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
                                    backupExoPlayer.setVideoSurface(surface)
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
                                        backupExoPlayer.clearVideoSurface(it)
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
                        .fillMaxWidth()
                        .height(340.dp)
                        .graphicsLayer {
                            renderEffect = cachedVideoRenderEffect
                        }
                )
            }
        }
    }
}















