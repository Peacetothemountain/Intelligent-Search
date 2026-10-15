package com.pixel.intelligentsearch.feature.settings
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.core.util.findActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
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
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.search.AnimatedMatrixBackground
import com.pixel.intelligentsearch.R
import android.app.Activity
import android.content.Context

import android.os.Build
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.pm.PackageManager
import org.intellij.lang.annotations.Language
import android.graphics.RuntimeShader
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ShaderBrush
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.animation.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
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

import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.ui.materialYouMeshGradientBackground
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import com.pixel.intelligentsearch.core.data.SettingsManager

@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.material3.IconButton(
        onClick = {
            com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context).performPredictiveBackHaptic(view)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.material3.TextButton(
        onClick = {
            com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context).performPredictiveBackHaptic(view)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.material3.Button(
        onClick = {
            com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context).performPredictiveBackHaptic(view)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
internal fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.material3.FilledTonalButton(
        onClick = {
            com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context).performPredictiveBackHaptic(view)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
internal fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.material3.OutlinedButton(
        onClick = {
            com.pixel.intelligentsearch.core.haptics.PixelHapticEngine.get(context).performPredictiveBackHaptic(view)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

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
        
        // Multi-frequency harmonic dispersion ribbons
        float wave1 = sin(uv.x * 6.28 + time * 2.2) * 0.14 + sin(uv.x * 12.56 - time * 1.5) * 0.04;
        float wave2 = cos(uv.x * 9.42 - time * 1.8) * 0.09 + cos(uv.x * 15.7 + time * 2.1) * 0.03;
        float wave3 = sin((uv.x - 0.5) * 5.0 + time * 1.5) * 0.12;
        
        // Corner arcs originating from bottom-left (0,0) and bottom-right (1,0)
        float dLeft = length(float2(uv.x * 1.15, y * 1.85));
        float dRight = length(float2((1.0 - uv.x) * 1.15, y * 1.85));
        
        float bottomGlow = 1.0 - smoothstep(0.0, 0.85, y - wave1 - wave2);
        float cornerLeft = 1.0 - smoothstep(0.0, 0.95, dLeft - wave3);
        float cornerRight = 1.0 - smoothstep(0.0, 0.95, dRight + wave3);
        
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
        
        float pulse = 0.90 + 0.10 * sin(time * 2.4);
        float finalAlpha = intensity * mixedColor.a * pulse;
        
        return half4(mixedColor.rgb * finalAlpha, finalAlpha);
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

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && shader != null) {
        val brush = remember(shader) { androidx.compose.ui.graphics.ShaderBrush(shader) }
        var time by remember { mutableFloatStateOf(0f) }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var lastFrame = androidx.compose.runtime.withFrameNanos { it }
                while (isActive) {
                    androidx.compose.runtime.withFrameNanos { frameTime ->
                        time += (frameTime - lastFrame) / 1_000_000_000f
                        lastFrame = frameTime
                    }
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreensHub(
    initialScreen: String,
    fromSearchOverlay: Boolean = false,
    prefs: SharedPreferences,
    onBackToLauncher: () -> Unit,
    context: Context
) {
    SettingsScreensHub(
        initialRoute = com.pixel.intelligentsearch.core.navigation.Route.fromKey(initialScreen),
        fromSearchOverlay = fromSearchOverlay,
        prefs = prefs,
        onBackToLauncher = onBackToLauncher,
        context = context
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SettingsScreensHub(
    initialRoute: com.pixel.intelligentsearch.core.navigation.Route = com.pixel.intelligentsearch.core.navigation.Route.Main,
    fromSearchOverlay: Boolean = false,
    prefs: SharedPreferences,
    onBackToLauncher: () -> Unit,
    context: Context
) {
    val viewModel: SettingsViewModel = LocalSettingsViewModel.current ?: androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val view = LocalView.current
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
        val exoPlayer = remember(context) {
            runCatching {
                val uri = android.net.Uri.parse("android.resource://" + context.packageName + "/" + com.pixel.intelligentsearch.R.raw.bugdroid_video)
                val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
                val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).createMediaSource(mediaItem)
                androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    setMediaSource(mediaSource)
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    volume = 0f
                    prepare()
                    // MainSettingsScreen starts playback while it is resumed; deep links into other
                    // settings pages must not decode the looping video in the background.
                    playWhenReady = false
                }
            }.getOrNull()
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner, exoPlayer) {
            val player = exoPlayer
            onDispose {
                player?.release()
            }
        }

        val onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit = { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        val handleExitBack: () -> Unit = {
            SettingsDebouncer.recordClick()
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
        val hasSubScreensInNavHost = navController.previousBackStackEntry != null
        val isAtRoot = !hasSubScreensInNavHost || currentBackStackEntry?.destination?.hasRoute<com.pixel.intelligentsearch.core.navigation.Route.Main>() == true

        val onBack: () -> Unit = onBack@{
            // A quick double tap would pop the sub-page, then see the root and exit Settings entirely.
            if (!SettingsDebouncer.canClick(cooldownMs = 400L, key = "settings_top_bar_back")) return@onBack
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                handleExitBack()
            }
        }

        val exitBackProgress = remember { Animatable(0f) }
        var predictiveBackSwipeEdge by remember { mutableIntStateOf(androidx.activity.BackEventCompat.EDGE_LEFT) }

        androidx.activity.compose.PredictiveBackHandler(enabled = !hasSubScreensInNavHost) { progressFlow ->
            val act = context.findActivity()
            try {
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
                progressFlow.collect { backEvent ->
                    predictiveBackSwipeEdge = backEvent.swipeEdge
                    exitBackProgress.snapTo(backEvent.progress)
                }
                exitBackProgress.snapTo(0f)
                handleExitBack()
            } catch (_: java.util.concurrent.CancellationException) {
                exitBackProgress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 300f))
            } finally {
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.setNormalRefreshRateCategory(act)
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

            val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showTutorial) Modifier.blur(24.dp) else Modifier)
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
                        if (com.pixel.intelligentsearch.BuildConfig.DEBUG && prefs[SettingsManager.DEBUG_UNLOCKED]) {
                            DebugScreen(
                                prefs = prefs,
                                onBack = onBack,
                                onDisableDebug = {
                                    prefs.edit().put(SettingsManager.DEBUG_UNLOCKED, false).apply()
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
                        3 to TutorialStepInfo("Settings & Features", "Here you can personalize your experience by customizing your widget and search controls.", Alignment.Center, requireButtonPress = true, showArrow = false),
                        4 to TutorialStepInfo("Make it Yours", "Explore all the categories below to fine-tune Intelligent Search perfectly to your workflow. You're all set!", Alignment.Center, requireButtonPress = true, showArrow = false)
                    ),
                    onComplete = { showTutorial = false }
                )
            }
        }
    }
}

@Composable
fun BugdroidPlayer(
    player: androidx.media3.exoplayer.ExoPlayer?,
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1.0f,
    videoAspect: Float = 16f / 9f
) {
    val shaderSrc = """
        uniform shader content;
        half4 main(float2 coords) {
            half4 color = content.eval(coords);
            // Perceptually weighted Rec. 709 luminance (Green dominance: 0.7152)
            float luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
            // Smooth cubic Hermite keying with edge antialiasing
            float alpha = smoothstep(0.14, 0.28, luma);
            // Premultiplied alpha output preventing dark fringe halos
            return half4(color.rgb * alpha, alpha);
        }
    """.trimIndent()

    val cachedVideoRenderEffect = remember(shaderSrc) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val shader = android.graphics.RuntimeShader(shaderSrc)
            val frameworkEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
            frameworkEffect.asComposeRenderEffect()
        } else null
    }

    val surfaceHolder = remember {
        object {
            var currentSurface: android.view.Surface? = null
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).apply {
                isOpaque = false
                val adjustAspectRatio: (android.view.TextureView) -> Unit = { tv ->
                    val vw = tv.width
                    val vh = tv.height
                    if (vw > 0 && vh > 0) {
                        val matrix = android.graphics.Matrix()
                        val viewAspect = vw.toFloat() / vh.toFloat()
                        var scaleX = 1f
                        var scaleY = 1f
                        if (viewAspect > videoAspect) {
                            scaleY = (vw.toFloat() / videoAspect) / vh.toFloat()
                        } else {
                            scaleX = (vh.toFloat() * videoAspect) / vw.toFloat()
                        }
                        scaleX *= scaleFactor
                        scaleY *= scaleFactor
                        matrix.setScale(scaleX, scaleY, vw / 2f, vh / 2f)
                        tv.setTransform(matrix)
                    }
                }

                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        surfaceHolder.currentSurface?.release()
                        val surface = android.view.Surface(surfaceTexture)
                        surfaceHolder.currentSurface = surface
                        player?.setVideoSurface(surface)
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
                        surfaceHolder.currentSurface?.let {
                            player?.clearVideoSurface(it)
                            it.release()
                        }
                        surfaceHolder.currentSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(
                        surfaceTexture: android.graphics.SurfaceTexture
                    ) {}
                }

                if (isAvailable && surfaceTexture != null) {
                    val surface = surfaceHolder.currentSurface ?: android.view.Surface(surfaceTexture).also {
                        surfaceHolder.currentSurface = it
                    }
                    player?.setVideoSurface(surface)
                    adjustAspectRatio(this@apply)
                }
            }
        },
        update = { tv ->
            val existingSurface = surfaceHolder.currentSurface
            if (existingSurface != null) {
                player?.setVideoSurface(existingSurface)
            } else if (tv.isAvailable && tv.surfaceTexture != null) {
                val surface = android.view.Surface(tv.surfaceTexture)
                surfaceHolder.currentSurface = surface
                player?.setVideoSurface(surface)
            }
        },
        modifier = modifier.graphicsLayer {
            renderEffect = cachedVideoRenderEffect
        }
    )
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
    exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null,
    showTutorial: Boolean = false
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                player?.play()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player?.play()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.pause()
        }
    }

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
            val keystore = remember { context.appEntryPoint.strongBoxSecurityManager() }
            val attestationVerifier = remember { com.pixel.intelligentsearch.core.security.KeyAttestationVerifier(context) }
            val hardwareLevel by androidx.compose.runtime.produceState(initialValue = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.STRONGBOX else com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.TEE) {
                // A Keystore failure keeps the feature-flag estimate instead of crashing Settings.
                value = try {
                    withContext(Dispatchers.IO) { keystore.deviceKeySecurityLevel() }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("SettingsScreens", "Could not read hardware security level", e)
                    value
                }
            }
            val hardwareInfo = remember(hardwareLevel) {
                com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detectSecurityHardware(context, hardwareLevel)
            }
            val attestationResult by androidx.compose.runtime.produceState<com.pixel.intelligentsearch.core.security.AttestationResult?>(initialValue = null) {
                value = withContext(Dispatchers.IO) {
                    attestationVerifier.generateAndVerifyAttestation()
                }
            }
            val view = androidx.compose.ui.platform.LocalView.current

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
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                    // Window size rather than Configuration.screenHeightDp, so split-screen and
                    // freeform windows size the pager to the space the dialog actually has.
                    val windowHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
                    val pagerDensity = androidx.compose.ui.platform.LocalDensity.current
                    val pagerHeight = remember(windowHeightPx, pagerDensity) {
                        with(pagerDensity) { (windowHeightPx.toDp().value * 0.54f).coerceIn(368f, 480f).dp }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pagerHeight)
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.TravelExplore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "Default Search Engine",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Starting with Android 17 QPR2, you can choose Intelligent Search as your system-wide default search engine to natively power your home screen search bar.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (com.pixel.intelligentsearch.core.system.SearchRoleManager.isSearchRoleHeld(context)) {
                                        Surface(
                                            onClick = {
                                                com.pixel.intelligentsearch.core.system.SearchRoleManager.openDefaultSearchEngineSettings(context)
                                            },
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
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Intelligent Search is your active Default Search Engine.",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "Natively active as your home search bar. Tap to manage in system settings.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = "Open Settings",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "Select Intelligent Search in Android settings to activate it on your home screen:",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Button(
                                                    onClick = {
                                                        com.pixel.intelligentsearch.core.system.SearchRoleManager.syncAllSearchEngineSettings(context)
                                                        com.pixel.intelligentsearch.core.system.SearchRoleManager.openDefaultSearchEngineSettings(context)
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Set as Default Search Engine")
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Terminal,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Manual ADB Activation",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                val adbCommands = "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch\nadb shell settings put secure selected_search_engine com.pixel.intelligentsearch"
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ADB Commands", adbCommands))
                                                        Toast.makeText(context, "ADB commands copied to clipboard", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ContentCopy,
                                                        contentDescription = "Copy ADB Commands",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "If you're running Android 17 QPR2 or an older version, bypass the system package allow list and set Intelligent Search as default manually via ADB:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Compatibility & Integration Notes",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "• Package Allow List: Settings.Secure.user_setup_search_engine bypasses the QPR2 package allow list so Intelligent Search always qualifies as the system search engine.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "• At a Glance: Configuring the default search engine on Android 17 QPR2 and newer will not affect At a Glance sports and finance integrations; however, configuring via ADB command will still bypass these integrations.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
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
                                                text = "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): " + when {
                                                    attestationResult == null -> "Verifying..."
                                                    attestationResult?.isHardwareAttested == true -> "Hardware Verified ✓"
                                                    attestationResult?.securityLevel == com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.STRONGBOX -> "StrongBox Verified ✓"
                                                    attestationResult?.securityLevel == com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.TEE -> "TEE Backed (TrustZone)"
                                                    else -> "Software Backed"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (attestationResult?.isHardwareAttested == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Certificate Chain Depth: ${attestationResult?.certificateCount ?: "..."} certificates",
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
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }) {
                                    Text("Security Info")
                                }
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch")
                                    clipboard?.setPrimaryClip(clip)
                                    com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context).click(view)
                                    Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Copy Command")
                                }
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
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }) {
                                Text("Developer Note")
                            }
                        }
                    }
                },
                dismissButton = when (pagerState.currentPage) {
                    1 -> {
                        {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }) {
                                Text("Developer Note")
                            }
                        }
                    }
                    2 -> {
                        {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }) {
                                Text("Security Info")
                            }
                        }
                    }
                    else -> null
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
                    icon = Icons.Outlined.SavedSearch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchSources) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Search Behavior",
                    subtitle = "Custom Search Overlay and Display Settings.",
                    icon = Icons.Outlined.Tune,
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
                    icon = Icons.Outlined.RocketLaunch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal) },
                    showDivider = false,
                )
            }

            SettingsCard {
                SettingsRow(
                    title = "Default Digital Assistant",
                    subtitle = "Manage Android Assistant Settings.",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                            // Fallback if the specific intent is not available
                            val fallbackIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                android.util.Log.w("IntelligentSearch", "Unexpected error", e2)
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
                val rawDebugUnlocked by rememberSetting(SettingsManager.DEBUG_UNLOCKED)
                val isDebugUnlocked = com.pixel.intelligentsearch.BuildConfig.DEBUG && rawDebugUnlocked
                val searchEngine = prefs[SettingsManager.SEARCH_ENGINE]
                var showTorBrowserSettingsDialog by remember { mutableStateOf(false) }
                val browserHistorySubtitle = when (searchEngine) {
                    "Google" -> "View Your Chrome and Google History."
                    "Bing" -> "View Your Bing History."
                    "DuckDuckGo" -> "Open Your DuckDuckGo App History."
                    "Tor Project", "Tor Browser" -> "Open Tor Browser."
                    else -> "View Your $searchEngine History."
                }
                SettingsRow(
                    title = "Browser History",
                    subtitle = browserHistorySubtitle,
                    icon = Icons.Outlined.TravelExplore,
                    onClick = {
                        when (searchEngine) {
                            "Bing" -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.bing.com/profile/history")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
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
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                                }
                            }
                            "Tor Project", "Tor Browser" -> {
                                try {
                                    val torIntent = context.packageManager.getLaunchIntentForPackage("org.torproject.torbrowser")
                                        ?: context.packageManager.getLaunchIntentForPackage("org.torproject.torbrowser_alpha")
                                    if (torIntent != null) {
                                        context.startActivity(torIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    } else {
                                        showTorBrowserSettingsDialog = true
                                    }
                                } catch (e: Exception) {
                                    showTorBrowserSettingsDialog = true
                                }
                            }
                            else -> {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity?product=6")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                                }
                            }
                        }
                    },
                    showDivider = true,
                )

                if (showTorBrowserSettingsDialog) {
                    TorBrowserInstallDialog(
                        onDismiss = { showTorBrowserSettingsDialog = false }
                    )
                }
                SettingsRow(
                    title = "Encrypted Backup",
                    subtitle = "Import, Export, and Restore Backup App Data.",
                    icon = Icons.Outlined.Security,
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
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val containerWidth = maxWidth
                val density = LocalDensity.current
                val fontScale = density.fontScale.coerceIn(0.85f, 1.5f)

                val textTargetWidth = (222.dp * (if (fontScale > 1f) (fontScale * 0.9f) else 1f)).coerceIn(205.dp, 255.dp)
                val remainingForPlayer = containerWidth - textTargetWidth - 12.dp
                val playerWidth = remainingForPlayer.coerceIn(80.dp, 160.dp)
                val playerHeight = playerWidth * (200f / 160f)

                val baseScale = (containerWidth / 390.dp).coerceIn(0.82f, 1.0f) / fontScale.coerceAtLeast(1.0f)
                val titleFontSize = (18.8f * baseScale).coerceIn(15.0f, 18.8f).sp
                val titleLineHeight = (titleFontSize.value * 1.28f).sp
                val subtextFontSize = (13.5f * baseScale).coerceIn(11.0f, 13.5f).sp
                val subtextLineHeight = (subtextFontSize.value * 1.36f).sp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BugdroidPlayer(
                        player = exoPlayer,
                        modifier = Modifier
                            .width(playerWidth)
                            .height(playerHeight),
                        scaleFactor = 0.69f
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Thank you for using\nIntelligent\u00A0Search.",
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = titleLineHeight,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Text(
                            text = "If you need support please email me at:\nsupport.ngdesigns@gmail.com",
                            fontSize = subtextFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = subtextLineHeight,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            maxLines = 2,
                            modifier = Modifier.bouncyClickable {
                                try {
                                    uriHandler.openUri("mailto:support.ngdesigns@gmail.com")
                                } catch (e: Exception) {
                                    // No mail app installed: copy the address instead of crashing.
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Support email", "support.ngdesigns@gmail.com"))
                                    android.widget.Toast.makeText(context, "No email app found. Address copied.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
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
            .background(MaterialTheme.colorScheme.error),
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
                    colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF00A4EF))
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
fun TorOfficialAppIcon(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val torDrawable = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("org.torproject.torbrowser")
        }.getOrNull() ?: runCatching {
            context.packageManager.getApplicationIcon("org.torproject.torbrowser_alpha")
        }.getOrNull()
    }
    if (torDrawable != null) {
        val bitmap = remember(torDrawable) {
            runCatching { torDrawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Tor Project", modifier = modifier.clip(RoundedCornerShape(6.dp)))
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF7D4698)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.55f

            val stemPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - w * 0.08f, cy - h * 0.35f)
                cubicTo(cx - w * 0.04f, cy - h * 0.48f, cx + w * 0.04f, cy - h * 0.48f, cx + w * 0.08f, cy - h * 0.35f)
                close()
            }
            drawPath(stemPath, Color(0xFFC594D8))

            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = w * 0.34f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = w * 0.22f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.06f)
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.10f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
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
        normalized.contains("Tor", ignoreCase = true) -> {
            TorOfficialAppIcon(modifier = modifier)
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

// REUSABLE COMPONENTS
// -----------------------------------------------------------------------------------------
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveCornerTokens.ShapeExtraLarge,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .semantics { isTraversalGroup = true }
            .clip(shape)
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
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                }
                .bouncyClickable(
                    onLongClick = onLongClick,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is androidx.compose.ui.graphics.ImageBitmap -> {
                        Image(
                            bitmap = icon,
                            contentDescription = title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
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
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                }
                .bouncyClickable { 
                    performClickHaptic(context)
                    expanded = true 
                }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconContent != null) {
                iconContent()
                Spacer(modifier = Modifier.width(16.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    if (title == "Primary Search App") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            SearchAppColorfulIcon(appName = subtitle, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                ) {
                    options.forEach { option ->
                        val isSelected = option == selectedOption
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option,
                                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
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
                                            painter = androidx.compose.ui.res.painterResource(id = optionIcons[option] ?: R.drawable.ic_search_lens_expressive),
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
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null || iconContent != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    containerColor: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveCornerTokens.ShapeLarge,
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

    val currentIsChecked by rememberUpdatedState(isChecked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentOnClick by rememberUpdatedState(onClick)

    // Rows with a sub-page (onClick != null) navigate when the row body is tapped, while the switch
    // itself must always toggle the setting. Both paths share one debounce key so a single tap that
    // reaches both handlers cannot toggle twice.
    val debounceKey = remember { Any() }
    val applyToggle: (Boolean) -> Unit = remember(view, sensoryEngine, debounceKey) {
        { next ->
            if (SettingsDebouncer.canClick(120L, debounceKey)) {
                sensoryEngine.toggle(view, next)
                currentOnCheckedChange(next)
            }
        }
    }
    val toggleAction = remember(view, sensoryEngine, applyToggle) {
        {
            val navigate = currentOnClick
            if (navigate != null) {
                navigate()
            } else {
                applyToggle(!currentIsChecked)
            }
        }
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.PRESS_SCALE_ROW else 1f,
        animationSpec = if (isPressed) {
            com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.Effects.FastSpatial()
        } else {
            com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.Spatial.ExpressiveDefault()
        },
        label = "messages_row_press_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (containerColor != Color.Transparent) {
                    Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (containerColor != Color.Transparent) {
                        Modifier
                            .clip(shape)
                            .background(color = containerColor, shape = shape)
                    } else Modifier
                )
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            onLongClick = onLongClick,
                            onClick = toggleAction
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            onClick = toggleAction
                        )
                    }
                )
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customIcon != null) {
                customIcon()
                Spacer(modifier = Modifier.width(16.dp))
            } else if (icon != null) {
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is androidx.compose.ui.graphics.ImageBitmap -> {
                        Image(
                            bitmap = icon,
                            contentDescription = title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            com.pixel.intelligentsearch.core.ui.expressive.GoogleMessagesSwitch(
                checked = isChecked,
                onCheckedChange = { next -> applyToggle(next) }
            )
        }
        if (showDivider && containerColor == Color.Transparent) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null || customIcon != null) 56.dp else 16.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.8.dp
            )
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                    steps = steps,
                    label = title
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
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
    isSquiggly: Boolean = true,
    enableHaptics: Boolean = false,
    /** Accessible name; without it TalkBack announces an unlabeled adjustable. */
    label: String? = null
) {
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    val infiniteTransition = rememberInfiniteTransition(label = "squiggle")
    // Kept as State and read only inside the Canvas below: reading it here recomposed the slider (and its
    // semantics and gesture setup) on every frame for as long as the screen was open.
    val phase: State<Float> = if (isSquiggly && showTrack) {
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
            if (enableHaptics && steps > 0) {
                hapticEngine.performHaptic(view, com.pixel.intelligentsearch.core.haptics.PixelHapticType.TICK)
            }
        }
    }

    val cachedPath = remember { androidx.compose.ui.graphics.Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics(mergeDescendants = true) {
                if (label != null) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = valueRange,
                    steps = steps
                )
                setProgress { targetValue: Float ->
                    val snapped = snapValue(((targetValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f))
                    if (snapped != currentValue) {
                        currentOnValueChange(snapped)
                        true
                    } else false
                }
            }
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
            val effectivePhase = phase.value + dragPhaseOffset.floatValue

            // Draw active track
            if (showTrack) {
                if (isSquiggly) {
                    cachedPath.rewind()
                    cachedPath.moveTo(0f, centerY)
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

                        cachedPath.lineTo(x, y)
                        x += step
                    }
                    cachedPath.lineTo(thumbX, centerY)
                    
                    drawPath(
                        path = cachedPath,
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




