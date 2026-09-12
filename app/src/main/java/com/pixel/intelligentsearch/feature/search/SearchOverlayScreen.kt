package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import android.app.SearchManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import com.pixel.intelligentsearch.feature.settings.performClickHaptic
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.core.haptics.rememberScrollDetentController
import com.pixel.intelligentsearch.core.haptics.rememberMagneticDismissController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.feature.settings.TutorialStepInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.feature.settings.SettingsActivity
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import androidx.compose.runtime.saveable.rememberSaveable
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.MathResultOneBox
import com.pixel.intelligentsearch.core.ui.ConversionOneBox
import com.pixel.intelligentsearch.core.ui.DictionaryOneBox
import com.pixel.intelligentsearch.core.ui.UrlNavigationOneBox
import com.pixel.intelligentsearch.core.ui.TimeWeatherOneBox

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppGridItem(app: AppItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val appIconState = remember(app.packageName) { mutableStateOf<AppIconResult?>(peekThemedAppIcon(app.packageName)) }
    LaunchedEffect(app.packageName) {
        if (appIconState.value == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val icon = getThemedAppIcon(context, app.packageName)
                if (icon != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appIconState.value = icon
                    }
                }
            }
        }
    }
    val appIcon = appIconState.value
    val fallbackBitmap = remember(app.packageName) {
        runCatching { app.icon.toBitmap().asImageBitmap() }.getOrNull()
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon.bitmap,
                contentDescription = app.name,
                modifier = Modifier.size(48.dp),
                colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else if (fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap,
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun SearchSettingsItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun ShortcutRow(iconRes: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}

data class AppIconResult(val bitmap: androidx.compose.ui.graphics.ImageBitmap, val isMonochrome: Boolean)

private val themedIconCache = android.util.LruCache<String, AppIconResult>(256)
private var cachedActivePack: String? = null

fun clearThemedIconCache() {
    cachedActivePack = null
    themedIconCache.evictAll()
    com.pixel.intelligentsearch.core.util.MaterialOutlineManager.clearCache()
}

fun peekThemedAppIcon(packageName: String, activePackOverride: String? = null): AppIconResult? {
    val activePack = activePackOverride ?: cachedActivePack ?: "system_default"
    return themedIconCache.get("$activePack:$packageName")
}

fun getThemedAppIcon(context: Context, packageName: String, activePackOverride: String? = null): AppIconResult? {
    try {
        val activePack = activePackOverride ?: cachedActivePack ?: run {
            val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
            val p = prefs.getString("active_icon_pack", null)
            val resolved = if (p != null) p else {
                context.getSharedPreferences("intelligent_search_settings", Context.MODE_PRIVATE).getString("active_icon_pack", "system_default") ?: "system_default"
            }
            cachedActivePack = resolved
            resolved
        }
        val cacheKey = "$activePack:$packageName"
        val cached = themedIconCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
            if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                return d.bitmap
            }
            val bmp = android.graphics.Bitmap.createBitmap(
                if (d.intrinsicWidth > 0) d.intrinsicWidth else 100,
                if (d.intrinsicHeight > 0) d.intrinsicHeight else 100,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            return bmp
        }

        if (activePack != "system_default") {
            val packDrawable = com.pixel.intelligentsearch.core.util.IconPackManager.getIconForPackage(context, activePack, packageName)
            if (packDrawable != null) {
                val res = AppIconResult(drawableToBitmap(packDrawable).asImageBitmap(), isMonochrome = false)
                themedIconCache.put(cacheKey, res)
                return res
            }
        }

        val pm = context.packageManager
        val icon = pm.getApplicationIcon(packageName)
        
        if (icon is android.graphics.drawable.AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val monochrome = icon.monochrome
                if (monochrome != null) {
                    val res = AppIconResult(drawableToBitmap(monochrome).asImageBitmap(), isMonochrome = true)
                    themedIconCache.put(cacheKey, res)
                    return res
                }
            }
        }

        if (activePack == "system_default") {
            val appLabel = try {
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) { null }

            val outlineBitmap = com.pixel.intelligentsearch.core.util.MaterialOutlineManager.getMaterialOutlineIcon(
                context, packageName, appLabel, icon
            )
            if (outlineBitmap != null) {
                val res = AppIconResult(outlineBitmap.asImageBitmap(), isMonochrome = true)
                themedIconCache.put(cacheKey, res)
                return res
            }
        }

        val res = AppIconResult(drawableToBitmap(icon).asImageBitmap(), isMonochrome = false)
        themedIconCache.put(cacheKey, res)
        return res
    } catch (e: Exception) {
        return null
    }
}

fun getAppName(context: Context, packageName: String): String {
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        "App"
    }
}

@Composable
fun SearchPill(iconRes: Int? = null, iconBitmap: AppIconResult? = null, title: String, scale: Float = 1f, onClick: () -> Unit) {
    val hPadding = (12 * scale).dp
    val vPadding = (8 * scale).dp
    val iconSize = (18 * scale).dp
    val textSize = (14 * scale).sp

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 24.dp,
        animationSpec = ExpressiveMotionTokens.morphSpring(),
        label = "pill_corner_morph"
    )

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Row(
        modifier = Modifier
            .padding(end = (8 * scale).dp)
            .graphicsLayer {
                this.shape = shape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPressed) 0.65f else 0.4f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isPressed) 0.35f else 0.15f), shape)
            .bouncyClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = hPadding, vertical = vPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap.bitmap,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = if (iconBitmap.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else if (iconRes != null) {
            Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.width((8 * scale).dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}

@Suppress("DEPRECATION")
private fun finishWithoutTransition(activity: android.app.Activity?) {
    if (activity != null && !activity.isFinishing) {
        val moved = activity.moveTaskToBack(true)
        if (!moved) {
            activity.finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        }
    }
}

private fun launchSafeIntent(context: Context, intent: Intent, options: android.os.Bundle? = null) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (options != null) {
            context.startActivity(intent, options)
        } else {
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.findActivity()?.startActivity(intent, options)
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

@Composable
fun SearchOverlayScreen(
    onOpenSettings: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    isKeyboardDisabled: Boolean = false
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
    
    val prefs = remember(context) { context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE) }
    val suggestionsEnabled = remember(prefs) { prefs.getBoolean("search.web.suggestions", true) }
    val searchProviderName = remember(settingsState.searchEngine, settingsState.customSearchEngineUrl) {
        when (settingsState.searchEngine) {
            "DuckDuckGo" -> "DuckDuckGo"
            "Bing" -> "Bing"
            "Google" -> "Google"
            "Custom" -> {
                if (settingsState.customSearchEngineUrl.isNotBlank()) {
                    try {
                        val host = Uri.parse(
                            if (settingsState.customSearchEngineUrl.startsWith("http")) settingsState.customSearchEngineUrl 
                            else "https://${settingsState.customSearchEngineUrl}"
                        ).host?.removePrefix("www.")?.substringBefore(".")?.replaceFirstChar { it.uppercase() }
                        if (!host.isNullOrBlank()) host else "Web"
                    } catch (e: Exception) {
                        "Web"
                    }
                } else {
                    "Web"
                }
            }
            else -> if (settingsState.searchEngine.isNotBlank()) settingsState.searchEngine else "Google"
        }
    }
    var hasStartedTyping by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotEmpty()) {
            hasStartedTyping = true
        }
    }
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
    val isOpening = transitionState.targetState
    
    val activity = context.findActivity()
    val isFromBackSwipe = remember(activity) {
        activity?.intent?.getBooleanExtra("FROM_BACK_SWIPE", false) == true
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val coroutineScope = rememberCoroutineScope()
    // Animatable for the overlay expansion progress: 0f = collapsed pill, 1f = fully expanded
    val overlayProgressAnim = remember { Animatable(if (isFromBackSwipe) 1f else 0f) }
    val predictiveBackProgress = remember { Animatable(0f) }
    var predictiveBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val sensoryEngine = rememberTactileSonicEngine()
    val view = androidx.compose.ui.platform.LocalView.current
    val searchResultsListState = rememberLazyListState()
    rememberScrollDetentController(searchResultsListState, sensoryEngine)

    val performAppLaunch: (String) -> Unit = remember(sensoryEngine, view, onLaunchApp) {
        { packageName ->
            hasStartedTyping = false
            sensoryEngine.appLaunch(view)
            onLaunchApp(packageName)
        }
    }

    LaunchedEffect(uiState.mathResult) {
        if (!uiState.mathResult.isNullOrBlank()) {
            sensoryEngine.mathCalculation(view)
        }
    }

    LaunchedEffect(isOpening) {
        if (isOpening) {
            sensoryEngine.overlayOpen(view)
            if (!isFromBackSwipe) {
                overlayProgressAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 180f
                    )
                )
            } else {
                overlayProgressAnim.snapTo(1f)
            }
        } else {
            sensoryEngine.overlayDismiss(view)
            val currentVel = overlayProgressAnim.velocity
            overlayProgressAnim.animateTo(
                targetValue = 0f,
                initialVelocity = currentVel,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = 250f
                )
            )
            val act = context.findActivity()
            finishWithoutTransition(act)
        }
    }

    val focusRequester = remember { FocusRequester() }
    

    
    val isForceTutorial = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
    var showTutorial by remember {
        if (isForceTutorial) {
            TutorialManager.resetForForceTutorial(prefs)
        }
        mutableStateOf(TutorialManager.isTutorialActive(prefs))
    }
    
    var showDebugPill by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticContext = LocalContext.current

    val closeOverlay = {
        hasStartedTyping = false
        keyboardController?.hide()
        viewModel.onQueryChanged("")
        if (transitionState.targetState) {
            transitionState.targetState = false
        } else {
            val act = context.findActivity()
            finishWithoutTransition(act)
        }
    }

    val goToHomeScreen: () -> Unit = {
        hasStartedTyping = false
        keyboardController?.hide()
        viewModel.onQueryChanged("")
        val act = context.findActivity()
        act?.moveTaskToBack(true)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finishWithoutTransition(act)
    }

    val launchWebSearch: (String) -> Unit = launchWebSearch@{ searchQuery ->
        hasStartedTyping = false
        val bangMgr = com.pixel.intelligentsearch.core.bangs.SearchBangManager(context, com.pixel.intelligentsearch.core.data.SettingsManager(context))
        val parsedBang = bangMgr.parseBangQuery(searchQuery)
        if (parsedBang != null) {
            val bangIntent = bangMgr.dispatchBangSearch(parsedBang)
            launchSafeIntent(context, bangIntent)
            val act = context.findActivity()
            finishWithoutTransition(act)
            act?.finish()
            return@launchWebSearch
        }

        val engine = settingsState.searchEngine
        val customUrl = settingsState.customSearchEngineUrl
        val encodedQuery = Uri.encode(searchQuery)
        
        val intent = when (engine) {
            "Custom" -> {
                if (customUrl.isNotBlank()) {
                    val rawUrl = if (customUrl.contains("%s")) {
                        customUrl.replace("%s", encodedQuery)
                    } else {
                        "$customUrl$encodedQuery"
                    }
                    val fullUrl = if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                        rawUrl
                    } else {
                        "https://$rawUrl"
                    }
                    Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, searchQuery) }
                }
            }
            "DuckDuckGo" -> {
                val ddgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=$encodedQuery"))
                val pm = context.packageManager
                if (pm.resolveActivity(ddgIntent, 0) != null) {
                    ddgIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, searchQuery) }
                }
            }
            "Bing" -> {
                val bingIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com/search?q=$encodedQuery"))
                val pm = context.packageManager
                if (pm.resolveActivity(bingIntent, 0) != null) {
                    bingIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, searchQuery) }
                }
            }
            else -> {
                val googleIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    putExtra(SearchManager.QUERY, searchQuery)
                }
                val pm = context.packageManager
                if (pm.resolveActivity(googleIntent, 0) != null) {
                    googleIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, searchQuery) }
                }
            }
        }
        try {
            viewModel.addSearchHistory(searchQuery)
            viewModel.onQueryChanged("")
            launchSafeIntent(context, intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchQuery)
            }
            try { 
                viewModel.onQueryChanged("")
                launchSafeIntent(context, fallbackIntent) 
            } catch (ex: Exception) {}
        }
    }

    LaunchedEffect(transitionState.targetState) {
        if (transitionState.targetState) {
            if (!showTutorial) {
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {}
            }
        }
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                hasStartedTyping = false
                keyboardController?.hide()
                viewModel.onQueryChanged("")
            } else if (event == Lifecycle.Event.ON_RESUME) {
                hasStartedTyping = false
                val forceTut = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
                if (forceTut) {
                    TutorialManager.resetForForceTutorial(prefs)
                    showTutorial = true
                } else {
                    showTutorial = TutorialManager.isTutorialActive(prefs)
                }
                
                transitionState.targetState = true
                viewModel.loadInitialData()

                val fromBack = activity?.intent?.getBooleanExtra("FROM_BACK_SWIPE", false) == true
                coroutineScope.launch {
                    if (!fromBack) {
                        overlayProgressAnim.snapTo(0f)
                        overlayProgressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.86f,
                                stiffness = 180f
                            )
                        )
                    } else {
                        overlayProgressAnim.snapTo(1f)
                    }
                }

                try {
                    coroutineScope.launch {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                } catch (e: Exception) {}
                val currentStep = TutorialManager.getStep(prefs)
                
                if (showTutorial && currentStep == 2) {
                    TutorialManager.setStep(prefs, 3)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            hasStartedTyping = false
            viewModel.onQueryChanged("")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    PredictiveBackHandler(enabled = !showTutorial) { progressFlow ->
        try {
            var lastEmittedBackProg = 0f
            progressFlow.collect { backEvent ->
                predictiveBackEdge = backEvent.swipeEdge
                predictiveBackProgress.snapTo(backEvent.progress)
                if (backEvent.progress > 0.15f && kotlin.math.abs(backEvent.progress - lastEmittedBackProg) > 0.18f) {
                    lastEmittedBackProg = backEvent.progress
                    sensoryEngine.magneticResistance(view, backEvent.progress)
                }
            }
            keyboardController?.hide()
            hasStartedTyping = false
            viewModel.onQueryChanged("")
            sensoryEngine.springReleaseSnap(view)
            goToHomeScreen()
        } catch (_: java.util.concurrent.CancellationException) {
            sensoryEngine.tick(view, scale = 0.5f)
            predictiveBackProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    val visibleApps = remember(uiState.filteredApps, settingsState.hiddenApps) {
        uiState.filteredApps.filter { !settingsState.hiddenApps.contains(it.packageName) }.distinctBy { it.packageName }
    }

    val appWeight = remember(prefs) { prefs.getInt("search_weight_apps", 50) }
    val webWeight = remember(prefs) { prefs.getInt("search_weight_web", 50) }
    val contactWeight = remember(prefs) { prefs.getInt("search_weight_contacts", 50) }
    val fileWeight = remember(prefs) { prefs.getInt("search_weight_files", 50) }

    val domainMatchPriorities = remember(appWeight, contactWeight, fileWeight) {
        listOf(
            "contacts" to contactWeight,
            "apps" to appWeight,
            "files" to fileWeight
        ).sortedByDescending { it.second }
    }

    val bestMatch = remember(uiState.query, uiState.contacts, visibleApps, uiState.files, settingsState.searchApps, settingsState.searchContacts, settingsState.searchFiles, domainMatchPriorities) {
        if (uiState.query.isEmpty()) return@remember null
        for ((domain, _) in domainMatchPriorities) {
            when (domain) {
                "contacts" -> {
                    if (settingsState.searchContacts) {
                        val contactMatch = uiState.contacts.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
                        if (contactMatch != null) return@remember contactMatch
                    }
                }
                "apps" -> {
                    if (settingsState.searchApps) {
                        val appMatch = visibleApps.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
                        if (appMatch != null) return@remember appMatch
                    }
                }
                "files" -> {
                    if (settingsState.searchFiles) {
                        val fileMatch = uiState.files.firstOrNull { it.name.startsWith(uiState.query, ignoreCase = true) }
                        if (fileMatch != null) return@remember fileMatch
                    }
                }
            }
        }
        null
    }
    
    val bestMatchText = when (bestMatch) {
        is ContactItem -> bestMatch.name
        is AppItem -> bestMatch.name
        is FileItem -> bestMatch.name
        else -> null
    }

    val filteredContacts = remember(uiState.contacts, bestMatch) {
        if (bestMatch is ContactItem) uiState.contacts.filter { it != bestMatch }
        else uiState.contacts
    }
    
    val filteredApps = remember(visibleApps, bestMatch) {
        if (bestMatch is AppItem) visibleApps.filter { it != bestMatch }
        else visibleApps
    }
    
    val filteredFiles = remember(uiState.files, bestMatch) {
        if (bestMatch is FileItem) uiState.files.filter { it != bestMatch }
        else uiState.files
    }

    val searchBarContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = showDebugPill,
                enter = fadeIn(animationSpec = spring(0.72f, 400f)) + expandVertically(animationSpec = spring(0.72f, 400f)),
                exit = fadeOut(animationSpec = spring(0.72f, 400f)) + shrinkVertically(animationSpec = spring(0.72f, 400f))
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Text(
                        text = "Debug Enabled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            val showPerfStats = prefs.getBoolean("debug.show_perf_stats", false)
            AnimatedVisibility(
                visible = showPerfStats,
                enter = fadeIn(animationSpec = spring(0.72f, 400f)) + expandVertically(animationSpec = spring(0.72f, 400f)),
                exit = fadeOut(animationSpec = spring(0.72f, 400f)) + shrinkVertically(animationSpec = spring(0.72f, 400f))
            ) {
                val totalResults = uiState.filteredApps.size + uiState.contacts.size + uiState.files.size + uiState.webSuggestions.size
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Text(
                        text = "Latency: ${uiState.lastQueryLatency}ms | Results: $totalResults",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .tutorialTarget(1, prefs)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        RoundedCornerShape(percent = 50)
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = (settingsState.pillOpacity / 100f).coerceIn(0f, 1f)
                        ),
                        RoundedCornerShape(percent = 50)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_search_lens_expressive),
                    contentDescription = "Google",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = uiState.query,
                        onValueChange = { newQuery ->
                            if (newQuery.isNotEmpty()) {
                                hasStartedTyping = true
                            }
                            if (newQuery == "*xy88x*") {
                                prefs.edit().putBoolean("debug_unlocked", true).apply()
                                viewModel.onQueryChanged("")
                                showDebugPill = true
                                coroutineScope.launch {
                                    delay(3000)
                                    showDebugPill = false
                                    onOpenSettings("debug")
                                    /* closeOverlay() */
                                }
                            } else {
                                viewModel.onQueryChanged(newQuery)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            hasStartedTyping = false
                            keyboardController?.hide()
                            if (uiState.query.isNotEmpty()) {
                                viewModel.addSearchHistory(uiState.query)
                                if (bestMatch != null) {
                                    when (bestMatch) {
                                        is ContactItem -> {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(bestMatch.lookupUri))
                                            launchSafeIntent(context, intent)
                                        }
                                        is AppItem -> {
                                            performAppLaunch(bestMatch.packageName)
                                        }
                                        is FileItem -> {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(bestMatch.uri), bestMatch.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                setPackage("com.google.android.apps.nbu.files")
                                            }
                                            try {
                                                launchSafeIntent(context, intent)
                                            } catch (e: Exception) {
                                                intent.setPackage(null)
                                                launchSafeIntent(context, intent)
                                            }
                                        }
                                    }
                                    /* closeOverlay() */
                                } else if (settingsState.appQuickLaunch && visibleApps.isNotEmpty()) {
                                    performAppLaunch(visibleApps.first().packageName)
                                } else {
                                    launchWebSearch(uiState.query)
                                 }
                            }
                        }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                 if (uiState.query.isEmpty()) {
                                     Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex)
                                } else if (bestMatchText != null && bestMatchText.startsWith(uiState.query, ignoreCase = true)) {
                                    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
                                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent))
                                    builder.append(bestMatchText.substring(0, uiState.query.length))
                                    builder.pop()
                                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Gray))
                                    builder.append(bestMatchText.substring(uiState.query.length))
                                    builder.pop()
                                    Text(
                                        text = builder.toAnnotatedString(),
                                        fontSize = 18.sp,
                                        fontFamily = GoogleSansFlex,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = hasStartedTyping,
                        enter = fadeIn(ExpressiveMotionTokens.gentleSpring()) + expandVertically(ExpressiveMotionTokens.gentleSpring()),
                        exit = fadeOut(ExpressiveMotionTokens.gentleSpring()) + shrinkVertically(ExpressiveMotionTokens.gentleSpring())
                    ) {
                        Text(
                            text = searchProviderName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = GoogleSansFlex,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = uiState.query.isNotEmpty() && !uiState.isLoading,
                    enter = fadeIn(ExpressiveMotionTokens.bouncySpring()) + scaleIn(ExpressiveMotionTokens.bouncySpring()),
                    exit = fadeOut(ExpressiveMotionTokens.gentleSpring()) + scaleOut(ExpressiveMotionTokens.gentleSpring())
                ) {
                    IconButton(
                        onClick = { viewModel.onQueryChanged("") },
                        modifier = Modifier
                            .size(36.dp)
                            .bouncyClickable { viewModel.onQueryChanged("") }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { 
                        hasStartedTyping = false
                        onOpenSettings("main") 
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .zIndex(if (showTutorial) 10000f else 0f)
                        .tutorialTarget(2, prefs)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(
                visible = uiState.bangSuggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.bangSuggestions, key = { index, bang -> "bang_${index}_${bang.prefix}" }) { _, bang ->
                        AssistChip(
                            onClick = {
                                val currentQ = uiState.query
                                val bangTrigger = if (bang.prefix.isNotEmpty() && !bang.prefix[0].isLetterOrDigit()) {
                                    bang.prefix[0].toString()
                                } else {
                                    "!"
                                }
                                val newQ = if (currentQ.startsWith(bangTrigger)) {
                                    "${bang.prefix} "
                                } else if (currentQ.contains(bangTrigger)) {
                                    "${currentQ.substringBeforeLast(bangTrigger)}${bang.prefix} "
                                } else {
                                    "${bang.prefix} "
                                }
                                viewModel.onQueryChanged(newQ)
                            },
                            label = { Text("${bang.prefix} ${bang.name}", fontSize = 12.sp, fontFamily = GoogleSansFlex) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = null,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }

    val verticalPad = 14.dp

    val quickAppPanelContent = @Composable {
        if (settingsState.quickSearchHorizontal) {
            val pillPackages = remember(settingsState.contextAwareQuickApps, settingsState.searchPills, settingsState.shortcutResultsCount, uiState.recentApps) {
                val raw = if (settingsState.contextAwareQuickApps) {
                    uiState.recentApps.map { it.packageName }
                } else {
                    settingsState.searchPills.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                raw.distinct().take(settingsState.shortcutResultsCount)
            }
            val dynamicScale = if (pillPackages.size > 6) (6f / pillPackages.size.toFloat()).coerceIn(0.6f, 1f) else 1f
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(pillPackages, key = { index, pkg -> "pill_${index}_$pkg" }) { _, packageName ->
                            val appIconState = remember(packageName, settingsState.activeIconPack) { mutableStateOf<AppIconResult?>(null) }
                            val appNameState = remember(packageName) { mutableStateOf("App") }
                            LaunchedEffect(packageName, settingsState.activeIconPack) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val icon = getThemedAppIcon(context, packageName, activePackOverride = settingsState.activeIconPack)
                                    val name = getAppName(context, packageName)
                                    appIconState.value = icon
                                    appNameState.value = name
                                }
                            }
                            val appIcon = appIconState.value
                            val appName = appNameState.value
                            if (appIcon != null) {
                                SearchPill(iconBitmap = appIcon, title = appName, scale = dynamicScale) {
                                    if (uiState.query.isNotEmpty()) viewModel.addSearchHistory(uiState.query)
                                    
                                    val searchStr = uiState.query
                                    if (searchStr.isEmpty()) {
                                        performAppLaunch(packageName)
                                    } else {
                                        val intent = when (packageName) {
                                            "com.android.chrome" -> {
                                                val url = "https://google.com/search?q=${Uri.encode(searchStr)}"
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage(packageName) }
                                            }
                                            "com.google.android.apps.maps" -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(searchStr.ifEmpty { "Restaurants" })}")).apply { setPackage(packageName) }
                                            }
                                            "com.google.android.youtube" -> {
                                                Intent(Intent.ACTION_SEARCH).apply { setPackage(packageName); putExtra("query", searchStr.ifEmpty { "Music" }) }
                                            }
                                            "com.android.vending" -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(searchStr)}"))
                                            }
                                            "com.google.android.contacts" -> {
                                                context.packageManager.getLaunchIntentForPackage(packageName) 
                                                    ?: Intent(Intent.ACTION_PICK).apply { type = android.provider.ContactsContract.Contacts.CONTENT_TYPE }
                                            }
                                            "com.google.android.apps.nbu.files" -> {
                                                context.packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                            }
                                            else -> {
                                                 val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                                                     setPackage(packageName)
                                                     putExtra("query", searchStr)
                                                 }
                                                 val resolved = context.packageManager.queryIntentActivities(searchIntent, 0)
                                                 val hasExportedSearch = resolved.any { it.activityInfo.exported }
                                                 if (hasExportedSearch) {
                                                     searchIntent
                                                 } else {
                                                     context.packageManager.getLaunchIntentForPackage(packageName)
                                                 }
                                             }
                                        }
                                        if (intent != null) {
                                            hasStartedTyping = false
                                            launchSafeIntent(context, intent)
                                            val act = context.findActivity()
                                            finishWithoutTransition(act)
                                            act?.finish()
                                        }
                                    }
                                }
                            }
                        }
            }
        }
    }

    val searchResultsContent = @Composable {
        val sortedResultSections = remember(appWeight, webWeight, contactWeight, fileWeight) {
            listOf(
                "apps" to appWeight,
                "web" to webWeight,
                "contacts" to contactWeight,
                "files" to fileWeight
            ).sortedByDescending { it.second }.map { it.first }
        }

        LazyColumn(
            state = searchResultsListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = if (settingsState.bottomSearch && settingsState.bottomSearchResult) 72.dp else 8.dp,
                bottom = 8.dp
            ),
            reverseLayout = if (!settingsState.bottomSearch) false else settingsState.bottomSearchResult,
            verticalArrangement = if (settingsState.bottomSearch) Arrangement.Bottom else Arrangement.Top
        ) {
            val showApps = true
            val showWeb = true
            val showPeople = true
            val showFiles = true

            val systemToggle = uiState.systemToggle
            if (systemToggle != null) {
                item(key = "system_toggle_card_${systemToggle.id}") {
                    com.pixel.intelligentsearch.core.ui.SystemToggleCard(
                        toggleState = systemToggle,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            if (settingsState.smartClipboardSuggestions && uiState.directActions.isNotEmpty()) {
                itemsIndexed(uiState.directActions, key = { index, action -> "direct_action_${action.title}_$index" }) { _, action ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                rowAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                                )
                            }
                            delay(160)
                            viewModel.dismissDirectAction(action)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = rowAlpha.value
                            }
                    ) {
                        val actionIcon = when (action.iconType) {
                            "link" -> Icons.Default.Link
                            "phone" -> Icons.Default.Call
                            "search" -> Icons.Default.Search
                            "calendar" -> Icons.Default.Event
                            "message" -> Icons.AutoMirrored.Filled.Message
                            else -> Icons.Default.ContentPaste
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                                .clip(RoundedCornerShape(32.dp))
                                .pointerInput(action) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            scope.launch {
                                                val newOffset = offsetX.value + dragAmount
                                                offsetX.snapTo(newOffset)
                                                val dragProgress = (kotlin.math.abs(newOffset) / 600f).coerceIn(0f, 0.6f)
                                                rowAlpha.snapTo(1f - dragProgress)
                                            }
                                        },
                                        onDragEnd = {
                                            if (kotlin.math.abs(offsetX.value) > 130f) {
                                                dismissDirection = if (offsetX.value >= 0f) 1f else -1f
                                                dismissed = true
                                            } else {
                                                scope.launch {
                                                    launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                                                    launch { rowAlpha.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                                                launch { rowAlpha.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
                                            }
                                        }
                                    )
                                }
                                .bouncyClickable {
                                    hasStartedTyping = false
                                    action.intent?.let { intent ->
                                        launchSafeIntent(context, intent)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = action.title,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = GoogleSansFlex
                                )
                                Text(
                                    text = action.subtitle,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSansFlex,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                item(key = "direct_actions_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (bestMatch != null) {
                item(key = "top_hit_label") {
                    Text("Top Hit", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontFamily = GoogleSansFlex, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                item(key = "top_hit_content") {
                    when (val match = bestMatch) {
                        is ContactItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        hasStartedTyping = false
                                        val intent = if (settingsState.contactDirectCall) {
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.phoneNumber}"))
                                        } else {
                                            Intent(Intent.ACTION_VIEW, Uri.parse(match.lookupUri))
                                        }
                                        launchSafeIntent(context, intent)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                    Text(match.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                }
                            }
                        }
                        is AppItem -> {
                            val appIconState = remember(match.packageName) { mutableStateOf<AppIconResult?>(null) }
                            LaunchedEffect(match.packageName) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val icon = getThemedAppIcon(context, match.packageName)
                                    appIconState.value = icon
                                }
                            }
                            val fallbackBitmap = remember(match.packageName) {
                                runCatching { match.icon.toBitmap().asImageBitmap() }.getOrNull()
                            }

                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().bouncyClickable { performAppLaunch(match.packageName) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val appIcon = appIconState.value
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.bitmap,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                                            )
                                        } else if (fallbackBitmap != null) {
                                            Image(bitmap = fallbackBitmap, contentDescription = null, modifier = Modifier.size(48.dp))
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontFamily = GoogleSansFlex, fontWeight = FontWeight.Medium)
                                            Text("App", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontFamily = GoogleSansFlex)
                                        }
                                    }
                                    if (match.actions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            match.actions.forEach { action ->
                                                AssistChip(
                                                    onClick = {
                                                        hasStartedTyping = false
                                                        val intent = Intent(action.action)
                                                        if (action.dataUri != null) intent.data = android.net.Uri.parse(action.dataUri)
                                                        intent.setPackage(match.packageName)
                                                        try {
                                                            launchSafeIntent(context, intent)
                                                        } catch (e: Exception) {
                                                            intent.setPackage(null)
                                                            launchSafeIntent(context, intent)
                                                        }
                                                    },
                                                    label = { Text(action.title, color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = GoogleSansFlex) },
                                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                                    border = null,
                                                    shape = RoundedCornerShape(32.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is FileItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        hasStartedTyping = false
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(match.uri), match.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            setPackage("com.google.android.apps.nbu.files")
                                        }
                                        try {
                                            launchSafeIntent(context, intent)
                                        } catch(e: Exception) {
                                            intent.setPackage(null)
                                            launchSafeIntent(context, intent)
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (settingsState.filesThumbnails) {
                                    FileIconThumbnail(match.uri, match.mimeType)
                                } else {
                                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(match.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (settingsState.searchPreviousSearches && uiState.query.isEmpty() && uiState.recentSearches.isNotEmpty()) {
                val isSingleRecent = uiState.recentSearches.size == 1
                if (!isSingleRecent) {
                    item(key = "recent_label") {
                        Text(
                            text = "Recent",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                    }
                }
                itemsIndexed(uiState.recentSearches.distinct(), key = { index, query -> "recent_${index}_$query" }) { _, recentQuery ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                rowAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                                )
                            }
                            delay(160)
                            viewModel.removeSearchHistory(recentQuery)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = rowAlpha.value
                            }
                            .pointerInput(recentQuery) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            val newOffset = offsetX.value + dragAmount
                                            offsetX.snapTo(newOffset)
                                            val dragProgress = (kotlin.math.abs(newOffset) / 500f).coerceIn(0f, 0.7f)
                                            rowAlpha.snapTo(1f - dragProgress)
                                        }
                                    },
                                    onDragEnd = {
                                        if (kotlin.math.abs(offsetX.value) > 120f) {
                                            dismissDirection = if (offsetX.value >= 0f) 1f else -1f
                                            dismissed = true
                                        } else {
                                            scope.launch {
                                                launch {
                                                    offsetX.animateTo(
                                                        0f,
                                                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                                    )
                                                }
                                                launch {
                                                    rowAlpha.animateTo(
                                                        1f,
                                                        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            launch {
                                                offsetX.animateTo(
                                                    0f,
                                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                                )
                                            }
                                            launch {
                                                rowAlpha.animateTo(
                                                    1f,
                                                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isSingleRecent) {
                                Text(
                                    text = "Recent",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSansFlex,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(32.dp)
                                    )
                                    .clip(RoundedCornerShape(32.dp))
                                    .bouncyClickable { launchWebSearch(recentQuery) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = recentQuery,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontFamily = GoogleSansFlex,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.onQueryChanged(recentQuery) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NorthWest,
                                        contentDescription = "Insert query",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (settingsState.searchCalendar && uiState.calendarEvents.isNotEmpty()) {
                itemsIndexed(uiState.calendarEvents, key = { index, event -> "event_${index}_${event.title}_${event.startTime}" }) { _, event ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                rowAlpha.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                                )
                            }
                            delay(160)
                            viewModel.dismissCalendarEvent(event)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = rowAlpha.value
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(event) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            scope.launch {
                                                val newOffset = offsetX.value + dragAmount
                                                offsetX.snapTo(newOffset)
                                                val dragProgress = (kotlin.math.abs(newOffset) / 600f).coerceIn(0f, 0.6f)
                                                rowAlpha.snapTo(1f - dragProgress)
                                            }
                                        },
                                        onDragEnd = {
                                            if (kotlin.math.abs(offsetX.value) > 130f) {
                                                dismissDirection = if (offsetX.value >= 0f) 1f else -1f
                                                dismissed = true
                                            } else {
                                                scope.launch {
                                                    launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                                                    launch { rowAlpha.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                                                launch { rowAlpha.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) }
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = verticalPad),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(event.startTime.split(":").first(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = GoogleSansFlex)
                                Text(event.startTime, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                            }
                        }
                    }
                }
                item(key = "calendar_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (settingsState.searchShortcuts && uiState.shortcuts.isNotEmpty()) {
                itemsIndexed(uiState.shortcuts, key = { index, shortcut -> "shortcut_${index}_${shortcut.id}" }) { _, shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().bouncyClickable {
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                            try {
                                launcherApps?.startShortcut(shortcut.packageName, shortcut.id, null, null, android.os.Process.myUserHandle())
                                viewModel.onQueryChanged("")
                            } catch (e: Exception) { e.printStackTrace() }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(shortcut.shortLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                    }
                }
                item(key = "shortcuts_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (uiState.mathResult != null) {
                item(key = "math_result") {
                    MathResultOneBox(
                        expression = uiState.query,
                        result = uiState.mathResult ?: "",
                        onOpenCalculator = {
                            hasStartedTyping = false
                            try {
                                val calcIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
                                launchSafeIntent(context, calcIntent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }

            val instantAnswer = uiState.instantAnswer
            if (instantAnswer != null) {
                item(key = "instant_answer") {
                    when (instantAnswer.iconType) {
                        "conversion" -> {
                            ConversionOneBox(conversionText = instantAnswer.title)
                        }
                        "dictionary" -> {
                            DictionaryOneBox(
                                word = instantAnswer.title,
                                providerName = searchProviderName,
                                onClick = { launchWebSearch("define ${instantAnswer.title}") }
                            )
                        }
                        "url" -> {
                            UrlNavigationOneBox(
                                url = instantAnswer.title,
                                onClick = {
                                    hasStartedTyping = false
                                    val targetUrl = if (instantAnswer.title.startsWith("http://") || instantAnswer.title.startsWith("https://")) {
                                        instantAnswer.title
                                    } else "https://${instantAnswer.title}"
                                    launchSafeIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                }
                            )
                        }
                        "weather", "time" -> {
                            TimeWeatherOneBox(
                                title = instantAnswer.title,
                                subtitle = instantAnswer.subtitle,
                                iconType = instantAnswer.iconType,
                                onClick = {
                                    hasStartedTyping = false
                                    if (instantAnswer.iconType == "time") {
                                        launchSafeIntent(context, Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                                    } else {
                                        launchWebSearch("weather ${uiState.query}")
                                    }
                                }
                            )
                        }
                        else -> {
                            TimeWeatherOneBox(
                                title = instantAnswer.title,
                                subtitle = instantAnswer.subtitle,
                                iconType = "info",
                                onClick = { launchWebSearch(uiState.query) }
                            )
                        }
                    }
                }
            }

            for (sec in sortedResultSections) {
                when (sec) {
                    "apps" -> {
                        if (showApps && settingsState.searchApps && filteredApps.isNotEmpty()) {
                            item(key = "apps_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            item(key = "apps_row") {
                                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    if (uiState.query.isEmpty()) {
                                        item(key = "search_settings_shortcut") {
                                            SearchSettingsItem {
                                                val intent = Intent(context, SettingsActivity::class.java)
                                                val options = android.app.ActivityOptions.makeCustomAnimation(
                                                    context,
                                                    R.anim.slide_in_right,
                                                    R.anim.slide_out_left
                                                )
                                                launchSafeIntent(context, intent, options.toBundle())
                                            }
                                        }
                                    }
                                    itemsIndexed(filteredApps, key = { index, app -> "app_${app.packageName}_$index" }) { _, app ->
                                        AppGridItem(app) { performAppLaunch(app.packageName) }
                                    }
                                }
                            }
                        }

                        if (settingsState.shortcutInline && uiState.query.isNotEmpty()) {
                            item(key = "inline_shortcuts_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            item(key = "lens_shortcut") {
                                ShortcutRow(
                                    iconRes = R.drawable.ic_camera,
                                    title = "Search with Google Lens",
                                    onClick = {
                                        hasStartedTyping = false
                                        val intent = SearchWidgetProvider.getLensSearchIntent(context)
                                        launchSafeIntent(context, intent)
                                    }
                                )
                            }
                            item(key = "voice_shortcut") {
                                ShortcutRow(
                                    iconRes = R.drawable.ic_mic,
                                    title = "Search with Voice",
                                    onClick = {
                                        hasStartedTyping = false
                                        val intent = SearchWidgetProvider.getVoiceSearchIntent(context)
                                        launchSafeIntent(context, intent)
                                    }
                                )
                            }
                            item(key = "assistant_shortcut") {
                                ShortcutRow(
                                    iconRes = R.drawable.ic_lens_action,
                                    title = "Digital Assistant",
                                    onClick = {
                                        hasStartedTyping = false
                                        val intent = SearchWidgetProvider.getVoiceActionIntent(context)
                                        launchSafeIntent(context, intent)
                                    }
                                )
                            }
                        }
                    }
                    "web" -> {
                        if (showWeb && (settingsState.searchWeb || suggestionsEnabled) && uiState.query.isNotEmpty()) {
                            if (uiState.webSuggestions.isNotEmpty()) {
                                itemsIndexed(uiState.webSuggestions.distinct().take(settingsState.webResultsCount.coerceAtLeast(5)), key = { index, suggestion -> "web_suggest_${index}_$suggestion" }) { _, suggestion ->
                                    val isRecent = uiState.recentSearches.contains(suggestion)
                                    val trimmed = uiState.query.trim()
                                    val annotatedSuggestion = remember(suggestion, trimmed) {
                                        androidx.compose.ui.text.buildAnnotatedString {
                                            if (trimmed.isNotEmpty() && suggestion.startsWith(trimmed, ignoreCase = true)) {
                                                append(suggestion.substring(0, trimmed.length))
                                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                                                    append(suggestion.substring(trimmed.length))
                                                }
                                            } else {
                                                append(suggestion)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(
                                                placementSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                                                fadeInSpec = androidx.compose.animation.core.tween(150),
                                                fadeOutSpec = androidx.compose.animation.core.tween(150)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 3.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                                            .clip(RoundedCornerShape(28.dp))
                                            .bouncyClickable {
                                                viewModel.onQueryChanged(suggestion)
                                                viewModel.addSearchHistory(suggestion)
                                                launchWebSearch(suggestion)
                                            }
                                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isRecent) Icons.Default.History else Icons.Default.Search,
                                            contentDescription = null,
                                            tint = if (isRecent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = annotatedSuggestion,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp,
                                            fontFamily = GoogleSansFlex,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isRecent) {
                                            IconButton(
                                                onClick = { viewModel.removeSearchHistory(suggestion) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.onQueryChanged(suggestion) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NorthWest,
                                                contentDescription = "Insert query",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "contacts" -> {
                        if (showPeople && settingsState.searchContacts && filteredContacts.isNotEmpty()) {
                            item(key = "contacts_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            itemsIndexed(filteredContacts, key = { index, contact -> "contact_${contact.lookupUri}_${contact.phoneNumber}_$index" }) { index, contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bouncyClickable {
                                            hasStartedTyping = false
                                            val intent = if (settingsState.contactDirectCall) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            } else {
                                                Intent(Intent.ACTION_VIEW, Uri.parse(contact.lookupUri))
                                            }
                                            launchSafeIntent(context, intent)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                        Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                    }
                                }
                            }
                        }
                    }
                    "files" -> {
                        if (showFiles && settingsState.searchFiles && filteredFiles.isNotEmpty()) {
                            item(key = "files_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            itemsIndexed(filteredFiles, key = { index, file -> "file_${file.uri}_$index" }) { index, file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bouncyClickable {
                                            hasStartedTyping = false
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(file.uri), file.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            launchSafeIntent(context, intent)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(file.mimeType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val maxDragDistance = with(density) { 400.dp.toPx() } // Approx swipe distance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .then(if (!isKeyboardDisabled) Modifier.imePadding() else Modifier)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val commitThreshold = 0.75f
                        coroutineScope.launch {
                            val target = if (overlayProgressAnim.value > commitThreshold) 1f else 0f
                            val currentVel = overlayProgressAnim.velocity
                            overlayProgressAnim.animateTo(
                                targetValue = target,
                                initialVelocity = currentVel,
                                animationSpec = spring(dampingRatio = 0.92f, stiffness = 250f)
                            )
                            if (target == 0f) {
                                viewModel.onQueryChanged("")
                                val act = context.findActivity()
                                finishWithoutTransition(act)
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            val currentVel = overlayProgressAnim.velocity
                            overlayProgressAnim.animateTo(1f, initialVelocity = currentVel, animationSpec = spring(0.92f, 250f))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            change.consume()
                            coroutineScope.launch {
                                val deltaProgress = dragAmount / maxDragDistance
                                val newProgress = (overlayProgressAnim.value - deltaProgress).coerceIn(0f, 1f)
                                overlayProgressAnim.snapTo(newProgress)
                                if (newProgress < 0.95f) {
                                    keyboardController?.hide()
                                }
                            }
                        }
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!showTutorial) closeOverlay()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val scrimColor = if (settingsState.showWallpaper) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.background
        val scrimAlphaFactor = if (settingsState.showWallpaper) ((settingsState.backgroundTransparency / 100f) * 0.7f).coerceIn(0f, 1f) else 1.0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                    drawRect(color = scrimColor, alpha = if (settingsState.showWallpaper) scrimAlphaFactor * p else p)
                }
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (settingsState.matrixAnimationEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = overlayProgressAnim.value.coerceIn(0f, 1f)
                        }
                ) {
                    AnimatedMatrixBackground(isPaused = uiState.query.isNotBlank())
                }
            }
            
            val surfaceAlpha = if (settingsState.showWallpaper) ((100 - settingsState.backgroundTransparency) / 100f).coerceIn(0f, 1f) else 1f
            
            val targetHeight = screenHeight - 32.dp
            val initialHeight = 56.dp
            
            val targetWidth = screenWidth - 32.dp
            val initialWidth = screenWidth - 64.dp

            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val progress = overlayProgressAnim.value.coerceIn(0.001f, 1f)
                        val backProg = predictiveBackProgress.value.coerceIn(0f, 1f)
                        val predictiveScale = 1f - (backProg * 0.08f)

                        scaleX = predictiveScale
                        scaleY = predictiveScale

                        translationX = 0f

                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        alpha = (progress * (1f - backProg * 0.25f)).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .then(
                        if (settingsState.bottomSearch) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!settingsState.bottomSearch) {
                        Spacer(modifier = Modifier.statusBarsPadding())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            alpha = p
                            translationY = (1f - p) * 24f
                        }) { searchBarContent() }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                            alpha = a
                            translationY = (1f - a) * 24f
                        }) { quickAppPanelContent() }
                        Box(modifier = Modifier.weight(1f).graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                            alpha = a
                            translationY = (1f - a) * 24f
                        }) { searchResultsContent() }
                    } else {
                        Box(modifier = Modifier.weight(1f).graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                            alpha = a
                            translationY = -(1f - a) * 24f
                        }) { searchResultsContent() }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                            alpha = a
                            translationY = -(1f - a) * 24f
                        }) { quickAppPanelContent() }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            alpha = p
                            translationY = -(1f - p) * 24f
                        }) { searchBarContent() }
                    }
                }
            }
            TutorialSpotlightOverlay(
                prefs = prefs,
                stepsInfo = mapOf(
                    0 to TutorialStepInfo("Welcome!", "Hello! :) Thank you for installing Intelligent Search. Please follow the tutorial to show you around.", Alignment.Center, showArrow = false, requireButtonPress = true),
                    1 to TutorialStepInfo("Search Bar", "This is your search bar. Start typing to find apps, contacts, and files instantly. You can also swipe away recent search cards to remove them.", Alignment.Center, showArrow = true, requireButtonPress = true),
                    2 to TutorialStepInfo("Settings", "Tap the settings icon (the ⋮ button) to customize your search experience. Press OK below to open Settings now.", Alignment.Center, showArrow = true, requireButtonPress = true, showCircle = true)
                ),
                onComplete = { showTutorial = false },
                onStepAdvance = { step ->
                    if (step == 3) onOpenSettings("main")
                }
            )
            } // Close the Box
        }
    }

private val fileThumbnailCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(128)

@Composable
fun FileIconThumbnail(uri: String, mimeType: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by androidx.compose.runtime.remember(uri) { 
        androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(fileThumbnailCache.get(uri)) 
    }

    androidx.compose.runtime.LaunchedEffect(uri) {
        if (bitmap == null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val parsedUri = android.net.Uri.parse(uri)
                        val id = android.content.ContentUris.parseId(parsedUri)
                        val specificUri = if (mimeType.startsWith("image/")) {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        } else {
                            android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        }
                        context.contentResolver.loadThumbnail(specificUri, android.util.Size(96, 96), null)
                    } else null
                    
                    if (bmp != null) {
                        val img = bmp.asImageBitmap()
                        fileThumbnailCache.put(uri, img)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            bitmap = img
                        }
                    }
                } catch (e: Exception) {
                    // Ignore, fallback to standard icon
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}





