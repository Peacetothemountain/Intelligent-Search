package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import android.app.SearchManager
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import com.pixel.intelligentsearch.feature.settings.SettingsDebouncer
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
import androidx.compose.material.icons.outlined.*
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
            color = MaterialTheme.colorScheme.onSurface,
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
private val fileThumbnailCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(128)
private var cachedActivePack: String? = null

fun clearThemedIconCache() {
    cachedActivePack = null
    themedIconCache.evictAll()
    com.pixel.intelligentsearch.core.util.MaterialOutlineManager.clearCache()
}

fun clearAllUiMemoryCaches() {
    clearThemedIconCache()
    fileThumbnailCache.evictAll()
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
        activity.finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}

private fun launchSafeIntent(context: Context, intent: Intent, options: android.os.Bundle? = null) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (options != null) {
            context.startActivity(intent, options)
        } else {
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
    var appWeight by remember { mutableIntStateOf(prefs.getInt("search_weight_apps", 50)) }
    var webWeight by remember { mutableIntStateOf(prefs.getInt("search_weight_web", 50)) }
    var contactWeight by remember { mutableIntStateOf(prefs.getInt("search_weight_contacts", 50)) }
    var fileWeight by remember { mutableIntStateOf(prefs.getInt("search_weight_files", 50)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "search_weight_apps" -> appWeight = prefs.getInt("search_weight_apps", 50)
                "search_weight_web" -> webWeight = prefs.getInt("search_weight_web", 50)
                "search_weight_contacts" -> contactWeight = prefs.getInt("search_weight_contacts", 50)
                "search_weight_files" -> fileWeight = prefs.getInt("search_weight_files", 50)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
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
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(uiState.query, androidx.compose.ui.text.TextRange(uiState.query.length))) }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotEmpty()) {
            hasStartedTyping = true
        }
        if (uiState.query != textFieldValue.text) {
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = uiState.query,
                selection = textFieldValue.selection
            )
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
    // Animatable for the overlay expansion progress: starts fully expanded (1f) so returning from apps is instant and never transparent
    val overlayProgressAnim = remember { Animatable(1f) }
    val predictiveBackProgress = remember { Animatable(0f) }
    var predictiveBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val sensoryEngine = rememberTactileSonicEngine()
    val view = androidx.compose.ui.platform.LocalView.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResultsListState = rememberLazyListState()
    rememberScrollDetentController(searchResultsListState, sensoryEngine)

    val performAppLaunch: (String) -> Unit = remember(sensoryEngine, view, onLaunchApp, keyboardController, focusManager) {
        { packageName ->
            hasStartedTyping = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            sensoryEngine.appLaunch(view)
            onLaunchApp(packageName)
        }
    }

    LaunchedEffect(uiState.mathResult) {
        if (!uiState.mathResult.isNullOrBlank()) {
            sensoryEngine.mathCalculation(view)
        }
    }

    val distinctRecents = remember(uiState.recentSearches) { uiState.recentSearches.distinct() }
    var showHistoryClearedSnackbar by remember { mutableStateOf(false) }
    LaunchedEffect(showHistoryClearedSnackbar) {
        if (showHistoryClearedSnackbar) {
            delay(5000)
            showHistoryClearedSnackbar = false
        }
    }

    LaunchedEffect(isOpening) {
        if (isOpening) {
            overlayProgressAnim.snapTo(1f)
        } else {
            sensoryEngine.overlayDismiss(view)
            val currentVel = overlayProgressAnim.velocity
            overlayProgressAnim.animateTo(
                targetValue = 0f,
                initialVelocity = currentVel,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 340f
                )
            )
            val act = context.findActivity()
            finishWithoutTransition(act)
        }
    }
    
    val isForceTutorial = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
    var showTutorial by remember {
        if (isForceTutorial) {
            TutorialManager.resetForForceTutorial(prefs)
        }
        val active = TutorialManager.isTutorialActive(prefs)
        val step = TutorialManager.getStep(prefs)
        if (active && step >= 3) {
            TutorialManager.completeTutorial(prefs)
            mutableStateOf(false)
        } else {
            mutableStateOf(active)
        }
    }
    
    var showDebugPill by remember { mutableStateOf(false) }
    val hapticContext = LocalContext.current

    var isKeyboardDismissedByUser by rememberSaveable { mutableStateOf(false) }
    var showTorInstallPopup by remember { mutableStateOf(false) }

    LaunchedEffect(showTutorial) {
        if (showTutorial) {
            keyboardController?.hide()
        }
    }

    val closeOverlay = {
        hasStartedTyping = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val act = context.findActivity()
        if (act != null) {
            androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        viewModel.onQueryChanged("")
        if (transitionState.targetState) {
            transitionState.targetState = false
        } else {
            finishWithoutTransition(act)
        }
    }

    val goToHomeScreen: () -> Unit = {
        hasStartedTyping = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val act = context.findActivity()
        if (act != null) {
            androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        viewModel.onQueryChanged("")
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
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val bangMgr = com.pixel.intelligentsearch.core.bangs.SearchBangManager(context, com.pixel.intelligentsearch.core.data.SettingsManager(context))
        val parsedBang = bangMgr.parseBangQuery(searchQuery)
        if (parsedBang != null) {
            val bangIntent = bangMgr.dispatchBangSearch(parsedBang).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            launchSafeIntent(context, bangIntent)
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
                    Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                }
            }
            "DuckDuckGo" -> {
                val ddgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=$encodedQuery")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pm = context.packageManager
                if (pm.resolveActivity(ddgIntent, 0) != null) {
                    ddgIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                }
            }
            "Bing" -> {
                val bingIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com/search?q=$encodedQuery")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pm = context.packageManager
                if (pm.resolveActivity(bingIntent, 0) != null) {
                    bingIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                }
            }
            "Tor Project", "Tor Browser" -> {
                val pm = context.packageManager
                val torPkg = when {
                    pm.getLaunchIntentForPackage("org.torproject.torbrowser") != null -> "org.torproject.torbrowser"
                    pm.getLaunchIntentForPackage("org.torproject.torbrowser_alpha") != null -> "org.torproject.torbrowser_alpha"
                    else -> null
                }
                if (torPkg != null) {
                    val torUrl = "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/?q=$encodedQuery"
                    Intent(Intent.ACTION_VIEW, Uri.parse(torUrl)).apply {
                        setPackage(torPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                } else {
                    showTorInstallPopup = true
                    null
                }
            }
            else -> {
                val googleIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    putExtra(SearchManager.QUERY, searchQuery)
                    putExtra("query", searchQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val pm = context.packageManager
                if (pm.resolveActivity(googleIntent, 0) != null) {
                    googleIntent
                } else {
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, searchQuery)
                        putExtra("query", searchQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                }
            }
        }
        if (intent == null) return@launchWebSearch
        try {
            viewModel.addSearchHistory(searchQuery)
            viewModel.onQueryChanged("")
            launchSafeIntent(context, intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchQuery)
                putExtra("query", searchQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            try { 
                viewModel.onQueryChanged("")
                launchSafeIntent(context, fallbackIntent) 
            } catch (ex: Exception) {}
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    LaunchedEffect(windowInfo.isWindowFocused, transitionState.targetState, showTutorial, isKeyboardDisabled, isKeyboardDismissedByUser) {
        if (windowInfo.isWindowFocused && transitionState.targetState && !isKeyboardDisabled && !showTutorial && !isKeyboardDismissedByUser) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                hasStartedTyping = false
                viewModel.onQueryChanged("")
                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
            } else if (event == Lifecycle.Event.ON_RESUME) {
                hasStartedTyping = false
                isKeyboardDismissedByUser = false
                appWeight = prefs.getInt("search_weight_apps", 50)
                webWeight = prefs.getInt("search_weight_web", 50)
                contactWeight = prefs.getInt("search_weight_contacts", 50)
                fileWeight = prefs.getInt("search_weight_files", 50)
                val isContactsEnabled = settingsState.searchContacts || prefs.getBoolean("search.contacts", false) || prefs.getBoolean("search_contacts", false)
                if (isContactsEnabled) {
                    viewModel.refreshContacts()
                }
                val forceTut = prefs.getBoolean("debug_unlocked", false) && prefs.getBoolean("force_tutorial", false)
                if (forceTut) {
                    TutorialManager.resetForForceTutorial(prefs)
                    showTutorial = true
                } else {
                    val active = TutorialManager.isTutorialActive(prefs)
                    val step = TutorialManager.getStep(prefs)
                    if (active && step >= 3) {
                        TutorialManager.completeTutorial(prefs)
                        showTutorial = false
                    } else {
                        showTutorial = active
                    }
                }
                
                transitionState.targetState = true
                coroutineScope.launch {
                    predictiveBackProgress.snapTo(0f)
                    overlayProgressAnim.snapTo(1f)
                }

                if (showTutorial) {
                    keyboardController?.hide()
                } else if (!isKeyboardDisabled && !isKeyboardDismissedByUser) {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
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
            progressFlow.collect { backEvent ->
                predictiveBackEdge = backEvent.swipeEdge
                predictiveBackProgress.snapTo(backEvent.progress)
            }
            if (uiState.query.isNotEmpty()) {
                sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                viewModel.onQueryChanged("")
                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                hasStartedTyping = false
                predictiveBackProgress.snapTo(0f)
            } else {
                keyboardController?.hide()
                hasStartedTyping = false
                viewModel.onQueryChanged("")
                sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                predictiveBackProgress.snapTo(0f)
                goToHomeScreen()
            }
        } catch (_: java.util.concurrent.CancellationException) {
            predictiveBackProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)
            )
        }
    }

    val visibleApps = remember(uiState.filteredApps, settingsState.hiddenApps) {
        uiState.filteredApps.filter { !settingsState.hiddenApps.contains(it.packageName) }.distinctBy { it.packageName }
    }

    val isAppsEnabled = settingsState.searchApps || prefs.getBoolean("search.apps", true) || prefs.getBoolean("search_apps", true)
    val isContactsEnabled = settingsState.searchContacts || prefs.getBoolean("search.contacts", false) || prefs.getBoolean("search_contacts", false)
    val isFilesEnabled = settingsState.searchFiles || prefs.getBoolean("search.files", false) || prefs.getBoolean("search_files", false)

    val domainMatchPriorities = remember(appWeight, contactWeight, fileWeight) {
        listOf(
            "apps" to appWeight,
            "contacts" to contactWeight,
            "files" to fileWeight
        ).sortedByDescending { it.second }
    }

    val bestMatch = remember(uiState.query, uiState.contacts, visibleApps, uiState.files, isAppsEnabled, isContactsEnabled, isFilesEnabled, domainMatchPriorities) {
        val q = uiState.query.trim()
        if (q.isEmpty()) return@remember null

        // Tier 1: Exact matches across domains in user-configured priority order
        for ((domain, _) in domainMatchPriorities) {
            when (domain) {
                "apps" -> {
                    if (isAppsEnabled) {
                        val exactApp = visibleApps.firstOrNull { it.name.equals(q, ignoreCase = true) }
                        if (exactApp != null) return@remember exactApp
                    }
                }
                "contacts" -> {
                    if (isContactsEnabled) {
                        val exactContact = uiState.contacts.firstOrNull { it.name.equals(q, ignoreCase = true) }
                        if (exactContact != null) return@remember exactContact
                    }
                }
                "files" -> {
                    if (isFilesEnabled) {
                        val exactFile = uiState.files.firstOrNull { it.name.equals(q, ignoreCase = true) }
                        if (exactFile != null) return@remember exactFile
                    }
                }
            }
        }

        // Tier 2: Prefix matches across domains in priority order
        for ((domain, _) in domainMatchPriorities) {
            when (domain) {
                "apps" -> {
                    if (isAppsEnabled) {
                        val appMatch = visibleApps.firstOrNull { it.name.startsWith(q, ignoreCase = true) }
                        if (appMatch != null) return@remember appMatch
                    }
                }
                "contacts" -> {
                    if (isContactsEnabled) {
                        val contactMatch = uiState.contacts.firstOrNull { it.name.startsWith(q, ignoreCase = true) }
                        if (contactMatch != null) return@remember contactMatch
                    }
                }
                "files" -> {
                    if (isFilesEnabled) {
                        val fileMatch = uiState.files.firstOrNull { it.name.startsWith(q, ignoreCase = true) }
                        if (fileMatch != null) return@remember fileMatch
                    }
                }
            }
        }

        // Tier 3: Word-boundary prefix matches (e.g. "notes" matching "Keep Notes")
        for ((domain, _) in domainMatchPriorities) {
            when (domain) {
                "apps" -> {
                    if (isAppsEnabled) {
                        val wordMatch = visibleApps.firstOrNull { app ->
                            app.name.split("\\s+".toRegex()).any { it.startsWith(q, ignoreCase = true) }
                        }
                        if (wordMatch != null) return@remember wordMatch
                    }
                }
                "contacts" -> {
                    if (isContactsEnabled) {
                        val wordMatch = uiState.contacts.firstOrNull { c ->
                            c.name.split("\\s+".toRegex()).any { it.startsWith(q, ignoreCase = true) }
                        }
                        if (wordMatch != null) return@remember wordMatch
                    }
                }
                "files" -> {
                    if (isFilesEnabled) {
                        val wordMatch = uiState.files.firstOrNull { f ->
                            f.name.split("\\s+".toRegex()).any { it.startsWith(q, ignoreCase = true) }
                        }
                        if (wordMatch != null) return@remember wordMatch
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
            
            val searchBarCornerRadius by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (textFieldValue.text.isNotEmpty()) 20.dp else 28.dp,
                animationSpec = com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.morphSpring(),
                label = "search_pill_shape_morph"
            )
            val searchBarShape = RoundedCornerShape(searchBarCornerRadius)
            
            Row(
                modifier = Modifier
                    .tutorialTarget(1, prefs)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        searchBarShape
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = (settingsState.pillOpacity / 100f).coerceIn(0f, 1f)
                        ),
                        searchBarShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        try {
                            isKeyboardDismissedByUser = false
                            focusRequester.requestFocus()
                            keyboardController?.show()
                            val act = context.findActivity()
                            if (act != null) {
                                androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView)
                                    .show(androidx.core.view.WindowInsetsCompat.Type.ime())
                            }
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_search_lens_expressive),
                    contentDescription = "Search",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newTfv ->
                            textFieldValue = newTfv
                            val newQuery = newTfv.text
                            if (newQuery.isNotEmpty()) {
                                hasStartedTyping = true
                            }
                            if (newQuery == "*xy88x*") {
                                prefs.edit().putBoolean("debug_unlocked", true).apply()
                                viewModel.onQueryChanged("")
                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
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
                                            val intent = if (settingsState.contactDirectCall && bestMatch.phoneNumber.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${bestMatch.phoneNumber}"))
                                            } else if (bestMatch.lookupUri.isNotBlank()) {
                                                Intent(Intent.ACTION_VIEW, Uri.parse(bestMatch.lookupUri)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            } else if (bestMatch.phoneNumber.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${bestMatch.phoneNumber}"))
                                            } else {
                                                Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
                                            }
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
                                    builder.append(bestMatchText.substring(0, minOf(uiState.query.length, bestMatchText.length)))
                                    builder.pop()
                                    builder.pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)))
                                    builder.append(bestMatchText.substring(uiState.query.length))
                                    builder.pop()
                                    Text(
                                        text = builder.toAnnotatedString(),
                                        fontSize = 20.sp,
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
                            .size(48.dp)
                            .bouncyClickable { viewModel.onQueryChanged("") }
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { 
                        if (SettingsDebouncer.canClick()) {
                            hasStartedTyping = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onOpenSettings("main") 
                        }
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
                    items(uiState.bangSuggestions, key = { bang -> "bang_${bang.prefix}" }) { bang ->
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
                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = newQ,
                                    selection = androidx.compose.ui.text.TextRange(newQ.length)
                                )
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
                items(pillPackages, key = { pkg -> "pill_$pkg" }) { packageName ->
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
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { 
                                                    setPackage(packageName)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                }
                                            }
                                            "com.google.android.apps.maps" -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(searchStr.ifEmpty { "Restaurants" })}")).apply { 
                                                    setPackage(packageName)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                }
                                            }
                                            "com.google.android.youtube" -> {
                                                Intent(Intent.ACTION_SEARCH).apply { 
                                                    setPackage(packageName)
                                                    putExtra("query", searchStr.ifEmpty { "Music" })
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                }
                                            }
                                            "com.android.vending" -> {
                                                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(searchStr)}")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                }
                                            }
                                            "com.google.android.contacts" -> {
                                                (context.packageManager.getLaunchIntentForPackage(packageName) 
                                                    ?: Intent(Intent.ACTION_PICK).apply { type = android.provider.ContactsContract.Contacts.CONTENT_TYPE })
                                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                                            }
                                            "com.google.android.apps.nbu.files" -> {
                                                (context.packageManager.getLaunchIntentForPackage(packageName) 
                                                    ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
                                            }
                                            else -> {
                                                 val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                                                     setPackage(packageName)
                                                     putExtra("query", searchStr)
                                                     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                 }
                                                 val resolved = context.packageManager.queryIntentActivities(searchIntent, 0)
                                                 val hasExportedSearch = resolved.any { it.activityInfo.exported }
                                                 if (hasExportedSearch) {
                                                     searchIntent
                                                 } else {
                                                     context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                                         addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                                     }
                                                 }
                                             }
                                        }
                                        if (intent != null) {
                                            hasStartedTyping = false
                                            launchSafeIntent(context, intent)
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
        val matchingRecent = remember(uiState.query, uiState.recentSearches, settingsState.searchPreviousSearches) {
            val trimmed = uiState.query.trim()
            if (!settingsState.searchPreviousSearches || trimmed.isEmpty()) {
                emptyList()
            } else {
                uiState.recentSearches
                    .filter { it.contains(trimmed, ignoreCase = true) }
                    .distinct()
            }
        }
        val allDisplaySuggestions = remember(matchingRecent, uiState.webSuggestions, settingsState.webResultsCount) {
            val maxCount = settingsState.webResultsCount.coerceAtLeast(6)
            val nonRecentWeb = uiState.webSuggestions.filter { webSugg ->
                matchingRecent.none { it.equals(webSugg, ignoreCase = true) }
            }
            (matchingRecent + nonRecentWeb).distinct().take(maxCount)
        }

        val isBottomResults = settingsState.bottomSearch && settingsState.bottomSearchResult

        LazyColumn(
            state = searchResultsListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 8.dp
            ),
            reverseLayout = false,
            verticalArrangement = if (isBottomResults) Arrangement.Bottom else Arrangement.Top
        ) {
            val showApps = true
            val showWeb = true
            val showPeople = true
            val showFiles = true

            val renderInlineShortcuts = {
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

            if (isBottomResults) {
                renderInlineShortcuts()
            }

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
                itemsIndexed(uiState.directActions, key = { index, action -> "direct_action_${index}_${action.title}_${action.subtitle}" }) { _, action ->
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
                                                sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
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
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
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
                item(key = "best_match_hero_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Expressive "BEST MATCH" Pill & "Press ↵ to Open" Action Hint
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BEST MATCH",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = GoogleSansFlex,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Press ↵ to open",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = GoogleSansFlex
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            when (val match = bestMatch) {
                                is ContactItem -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bouncyClickable {
                                                hasStartedTyping = false
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                val isDirectCall = settingsState.contactDirectCall || prefs.getBoolean("contact_direct_call", false) || prefs.getBoolean("contact.direct.call", false)
                                                val intent = if (isDirectCall && match.phoneNumber.isNotBlank()) {
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.phoneNumber}"))
                                                } else if (match.lookupUri.isNotBlank()) {
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(match.lookupUri)).apply {
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                } else if (match.phoneNumber.isNotBlank()) {
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.phoneNumber}"))
                                                } else {
                                                    Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
                                                }
                                                launchSafeIntent(context, intent)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                match.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = GoogleSansFlex
                                            )
                                            Text(
                                                match.phoneNumber,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                fontFamily = GoogleSansFlex
                                            )
                                        }
                                        val isDirectCall = settingsState.contactDirectCall || prefs.getBoolean("contact_direct_call", false) || prefs.getBoolean("contact.direct.call", false)
                                        Icon(
                                            imageVector = if (isDirectCall) Icons.Default.Call else Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = if (isDirectCall) "Call" else "Open",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
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

                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .bouncyClickable { performAppLaunch(match.packageName) }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val appIcon = appIconState.value
                                            if (appIcon != null) {
                                                Image(
                                                    bitmap = appIcon.bitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(52.dp),
                                                    colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                                                )
                                            } else if (fallbackBitmap != null) {
                                                Image(bitmap = fallbackBitmap, contentDescription = null, modifier = Modifier.size(52.dp))
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    match.name,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 20.sp,
                                                    fontFamily = GoogleSansFlex,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Application",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    fontFamily = GoogleSansFlex
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Open",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (match.actions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                                        shape = RoundedCornerShape(16.dp)
                                                    )
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
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (settingsState.filesThumbnails) {
                                            FileIconThumbnail(match.uri, match.mimeType)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                match.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 16.sp,
                                                fontFamily = GoogleSansFlex,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "Local Document",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                fontFamily = GoogleSansFlex
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (settingsState.searchPreviousSearches && uiState.query.isEmpty() && distinctRecents.isNotEmpty()) {
                val isSingleRecent = distinctRecents.size == 1
                if (!isSingleRecent) {
                    item(key = "recent_label") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (distinctRecents.size > 7) {
                                Surface(
                                    onClick = {
                                        sensoryEngine.deleteThud(view)
                                        viewModel.clearSearchHistory()
                                        showHistoryClearedSnackbar = true
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteSweep,
                                            contentDescription = "Delete All",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Delete All",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.5.sp,
                                            fontFamily = GoogleSansFlex,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                items(distinctRecents, key = { query -> "recent_$query" }) { recentQuery ->
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
                                            sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
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
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { viewModel.removeSearchHistory(recentQuery) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                                                sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
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
                itemsIndexed(uiState.shortcuts, key = { index, shortcut -> "shortcut_${index}_${shortcut.packageName}_${shortcut.id}" }) { _, shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().expressiveRowClickable {
                            hasStartedTyping = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                            try {
                                launcherApps?.startShortcut(shortcut.packageName, shortcut.id, null, null, shortcut.userHandle)
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
                                    if (settingsState.searchEngine == "Tor Project" || settingsState.searchEngine == "Tor Browser") {
                                        val pm = context.packageManager
                                        val torPkg = when {
                                            pm.getLaunchIntentForPackage("org.torproject.torbrowser") != null -> "org.torproject.torbrowser"
                                            pm.getLaunchIntentForPackage("org.torproject.torbrowser_alpha") != null -> "org.torproject.torbrowser_alpha"
                                            else -> null
                                        }
                                        if (torPkg != null) {
                                            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                                setPackage(torPkg)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            }
                                            launchSafeIntent(context, urlIntent)
                                        } else {
                                            showTorInstallPopup = true
                                        }
                                    } else {
                                        val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        launchSafeIntent(context, urlIntent)
                                    }
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
                        val isAppsEnabled = settingsState.searchApps || prefs.getBoolean("search.apps", true) || prefs.getBoolean("search_apps", true)
                        val displayApps = if (filteredApps.isNotEmpty()) filteredApps else visibleApps
                        if (showApps && isAppsEnabled && displayApps.isNotEmpty()) {
                            item(key = "apps_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            item(key = "apps_row") {
                                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    if (uiState.query.isEmpty()) {
                                        item(key = "search_settings_shortcut") {
                                            SearchSettingsItem {
                                                if (SettingsDebouncer.canClick()) {
                                                    onOpenSettings("main")
                                                }
                                            }
                                        }
                                    }
                                    items(displayApps, key = { app -> "app_${app.packageName}_${app.userHandle?.hashCode() ?: 0}_${app.profileType}" }) { app ->
                                        AppGridItem(app) { performAppLaunch(app.packageName) }
                                    }
                                }
                            }
                        }
                    }
                    "web" -> {
                        if (showWeb && (settingsState.searchWeb || suggestionsEnabled) && uiState.query.isNotEmpty()) {
                            if (allDisplaySuggestions.isNotEmpty()) {
                                itemsIndexed(allDisplaySuggestions, key = { index, suggestion -> "sugg_${index}_$suggestion" }) { _, suggestion ->
                                    val isRecent = uiState.recentSearches.any { it.equals(suggestion, ignoreCase = true) }
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
                                            .expressiveRowClickable {
                                                viewModel.onQueryChanged(suggestion)
                                                viewModel.addSearchHistory(suggestion)
                                                launchWebSearch(suggestion)
                                            }
                                            .padding(
                                                start = if (isRecent) 16.dp else 20.dp,
                                                end = 8.dp,
                                                top = 10.dp,
                                                bottom = 10.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isRecent) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                        }
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
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                hasStartedTyping = true
                                                textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                                    text = suggestion,
                                                    selection = androidx.compose.ui.text.TextRange(suggestion.length)
                                                )
                                                viewModel.onQueryChanged(suggestion)
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NorthWest,
                                                contentDescription = "Insert query",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "contacts" -> {
                        val isContactsEnabled = settingsState.searchContacts || prefs.getBoolean("search.contacts", false) || prefs.getBoolean("search_contacts", false)
                        val displayContacts = if (filteredContacts.isNotEmpty()) filteredContacts else uiState.contacts
                        if (showPeople && isContactsEnabled && displayContacts.isNotEmpty()) {
                            item(key = "contacts_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            itemsIndexed(displayContacts, key = { index, contact -> "contact_${index}_${contact.lookupUri.ifBlank { contact.phoneNumber }}_${contact.name}" }) { _, contact ->
                                val isDirectCall = settingsState.contactDirectCall || prefs.getBoolean("contact_direct_call", false) || prefs.getBoolean("contact.direct.call", false)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .expressiveRowClickable {
                                            hasStartedTyping = false
                                            val intent = if (isDirectCall && contact.phoneNumber.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            } else if (contact.lookupUri.isNotBlank()) {
                                                Intent(Intent.ACTION_VIEW, Uri.parse(contact.lookupUri)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            } else if (contact.phoneNumber.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            } else {
                                                Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                                        Text(contact.phoneNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex)
                                    }
                                    IconButton(
                                        onClick = {
                                            if (contact.phoneNumber.isNotBlank()) {
                                                hasStartedTyping = false
                                                launchSafeIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}")))
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    "files" -> {
                        val isFilesEnabled = settingsState.searchFiles || prefs.getBoolean("search.files", false) || prefs.getBoolean("search_files", false)
                        val displayFiles = if (filteredFiles.isNotEmpty()) filteredFiles else uiState.files
                        if (showFiles && isFilesEnabled && displayFiles.isNotEmpty()) {
                            item(key = "files_divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            itemsIndexed(displayFiles, key = { index, file -> "file_${index}_${file.uri}" }) { _, file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .expressiveRowClickable {
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

            if (!isBottomResults) {
                renderInlineShortcuts()
            }
        }
    }

    val maxDragDistance = with(density) { 400.dp.toPx() } // Approx swipe distance

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                                    isKeyboardDismissedByUser = true
                                    focusManager.clearFocus(force = true)
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
        val isShowWallpaper = remember(settingsState.showWallpaper) {
            settingsState.showWallpaper || prefs.getBoolean("search.background.show.wall", prefs.getBoolean("show.wallpaper", true))
        }
        val isTransparency = if (settingsState.backgroundTransparency > 0) settingsState.backgroundTransparency else prefs.getInt("search.background.transparency", prefs.getInt("background.transparency", 50))
        val scrimColor = if (isShowWallpaper) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.background
        val scrimAlphaFactor = if (isShowWallpaper) ((isTransparency / 100f) * 0.7f).coerceIn(0f, 1f) else 1.0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                    val baseAlpha = if (isShowWallpaper) scrimAlphaFactor * p else p
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                scrimColor.copy(alpha = (baseAlpha * 1.05f).coerceIn(0f, 1f)),
                                scrimColor.copy(alpha = baseAlpha),
                                scrimColor.copy(alpha = (baseAlpha * 0.95f).coerceIn(0f, 1f))
                            )
                        )
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    if (!isKeyboardDisabled) {
                        WindowInsets.ime.union(WindowInsets.navigationBars)
                    } else {
                        WindowInsets.navigationBars
                    }
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
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
            
            val surfaceAlpha = if (isShowWallpaper) ((100 - isTransparency) / 100f).coerceIn(0f, 1f) else 1f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val progress = overlayProgressAnim.value.coerceIn(0.001f, 1f)
                        val backProg = predictiveBackProgress.value.coerceIn(0f, 1f)
                        val predictiveScale = 1f - (backProg * 0.08f)

                        scaleX = predictiveScale
                        scaleY = predictiveScale

                        val maxShiftPx = 48.dp.toPx()
                        translationX = if (predictiveBackEdge == BackEventCompat.EDGE_LEFT) {
                            backProg * maxShiftPx
                        } else {
                            -backProg * maxShiftPx
                        }

                        transformOrigin = TransformOrigin(
                            if (predictiveBackEdge == BackEventCompat.EDGE_LEFT) 0.05f else 0.95f,
                            0.5f
                        )
                        alpha = (progress * (1f - backProg * 0.25f)).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(androidx.compose.ui.unit.lerp(24.dp, 32.dp, predictiveBackProgress.value.coerceIn(0f, 1f))))
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
        }

        AnimatedVisibility(
            visible = showHistoryClearedSnackbar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (settingsState.bottomSearch) 86.dp else 24.dp, start = 16.dp, end = 16.dp)
                .imePadding()
                .zIndex(200f)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Search History Cleared",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            sensoryEngine.click(view)
                            viewModel.undoClearSearchHistory()
                            showHistoryClearedSnackbar = false
                        }
                    ) {
                        Text(
                            text = "Undo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (showTutorial) {
            TutorialSpotlightOverlay(
                prefs = prefs,
                stepsInfo = mapOf(
                    0 to TutorialStepInfo("Welcome!", "Hello! :) Thank you for installing Intelligent Search. Please follow the tutorial to show you around.", Alignment.Center, showArrow = false, requireButtonPress = true),
                    1 to TutorialStepInfo("Search Bar", "This is your search bar. Start typing to find apps, contacts, and files instantly. You can also swipe away recent search cards to remove them.", Alignment.Center, showArrow = true, requireButtonPress = true),
                    2 to TutorialStepInfo("Settings", "Tap the settings icon (the ⋮ button) to customize your search experience. Press OK below to open Settings now.", Alignment.Center, showArrow = true, requireButtonPress = true, showCircle = true)
                ),
                onComplete = {
                    TutorialManager.completeTutorial(prefs)
                    showTutorial = false
                },
                onStepAdvance = { step ->
                    if (step == 3) onOpenSettings("main")
                }
            )
        }

        if (showTorInstallPopup) {
            TorBrowserInstallDialog(
                onDismiss = { showTorInstallPopup = false }
            )
        }
    }
}

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





