package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.feature.widget.getLensSearchIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceActionIntent
import com.pixel.intelligentsearch.feature.widget.getVoiceSearchIntent
import com.pixel.intelligentsearch.core.ui.getAppName
import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.ClickDebouncer
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.feature.settings.tutorialTarget
import android.app.SearchManager
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.core.haptics.rememberScrollDetentController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.feature.settings.TutorialStepInfo
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.drawscope.scale
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
import com.pixel.intelligentsearch.core.ui.WindowFramePacing
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.MathResultOneBox
import com.pixel.intelligentsearch.core.ui.ConversionOneBox
import com.pixel.intelligentsearch.core.ui.DictionaryOneBox
import com.pixel.intelligentsearch.core.ui.UrlNavigationOneBox
import com.pixel.intelligentsearch.core.ui.TimeWeatherOneBox
import com.pixel.intelligentsearch.core.ui.progressiveEdgeBlur
import com.pixel.intelligentsearch.core.ui.ProgressiveBlurDirection
import com.pixel.intelligentsearch.core.ui.materialYouGeminiPlate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    
    val prefs = remember(context) { context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE) }
    val appWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_APPS)
    val webWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_WEB)
    val contactWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_CONTACTS)
    val fileWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_FILES)
    val suggestionsEnabled by rememberSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS)
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
        // uiState reaches composition asynchronously, so while typing it can still hold the query from a
        // few keystrokes ago. Adopting that stale echo overwrote the field and dropped characters; only
        // a value that is still the ViewModel's current query (an external change such as a clear,
        // bang chip or voice result) may replace what the user typed.
        if (uiState.query != textFieldValue.text && uiState.query == viewModel.uiState.value.query) {
            val safeStart = textFieldValue.selection.start.coerceIn(0, uiState.query.length)
            val safeEnd = textFieldValue.selection.end.coerceIn(0, uiState.query.length)
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = uiState.query,
                selection = androidx.compose.ui.text.TextRange(safeStart, safeEnd)
            )
        }
    }
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }
    val isOpening = transitionState.targetState
    
    val activity = context.findActivity()

    val density = LocalDensity.current

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

    val isListScrollInProgress by remember { derivedStateOf { searchResultsListState.isScrollInProgress } }
    LaunchedEffect(isListScrollInProgress) {
        val act = context.findActivity()
        if (isListScrollInProgress) {
            WindowFramePacing.setHighRefreshRateCategory(act)
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = true)
        } else {
            WindowFramePacing.setNormalRefreshRateCategory(act)
            WindowFramePacing.setDynamicLtpoPacing(view, isMoving = false)
        }
    }

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

    val distinctRecents by remember { derivedStateOf { uiState.recentSearches.distinct() } }
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
            val act = context.findActivity()
            WindowFramePacing.setHighRefreshRateCategory(act)
            sensoryEngine.overlayDismiss(view)
            val currentVel = overlayProgressAnim.velocity
            overlayProgressAnim.animateTo(
                targetValue = 0f,
                initialVelocity = currentVel,
                animationSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault()
            )
            viewModel.onQueryChanged("")
            finishWithoutTransition(act)
        }
    }
    
    val isForceTutorial = prefs[SettingsManager.DEBUG_UNLOCKED] && prefs[SettingsManager.FORCE_TUTORIAL]
    var showTutorial by remember {
        if (isForceTutorial) {
            TutorialManager.resetForForceTutorial(prefs)
        }
        val active = TutorialManager.isTutorialActive(prefs)
        val step = prefs[SettingsManager.TUTORIAL_STEP]
        if (active && step >= 3) {
            TutorialManager.completeTutorial(prefs)
            mutableStateOf(false)
        } else {
            mutableStateOf(active)
        }
    }
    
    var showDebugPill by remember { mutableStateOf(false) }

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
        if (transitionState.targetState) {
            transitionState.targetState = false
        } else {
            viewModel.onQueryChanged("")
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
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        finishWithoutTransition(act)
    }

    val launchWebSearch: (String) -> Unit = launchWebSearch@{ searchQuery ->
        hasStartedTyping = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val bangIntent = viewModel.resolveBangIntent(searchQuery)
        if (bangIntent != null) {
            // CLEAR_TOP like the other browser launches; CLEAR_TASK would wipe the target app's task.
            bangIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            viewModel.addSearchHistory(searchQuery)
            viewModel.onQueryChanged("")
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
        viewModel.addSearchHistory(searchQuery)
        viewModel.onQueryChanged("")
        if (!launchSafeIntent(context, intent)) {
            val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, searchQuery)
                putExtra("query", searchQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (!launchSafeIntent(context, fallbackIntent)) {
                Toast.makeText(context, "No app available to search the web", Toast.LENGTH_SHORT).show()
            }
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
            } else if (event == Lifecycle.Event.ON_RESUME) {
                hasStartedTyping = false
                isKeyboardDismissedByUser = false
                val isContactsEnabled = settingsState.searchContacts
                if (isContactsEnabled) {
                    viewModel.refreshContacts()
                }
                if (settingsState.searchFiles) {
                    viewModel.refreshFilesIfStale()
                }
                val forceTut = prefs[SettingsManager.DEBUG_UNLOCKED] && prefs[SettingsManager.FORCE_TUTORIAL]
                if (forceTut) {
                    TutorialManager.resetForForceTutorial(prefs)
                    showTutorial = true
                } else {
                    val active = TutorialManager.isTutorialActive(prefs)
                    val step = prefs[SettingsManager.TUTORIAL_STEP]
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
            val act = context.findActivity()
            com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
            progressFlow.collect { backEvent ->
                predictiveBackEdge = backEvent.swipeEdge
                val fluidProgress = androidx.compose.animation.core.FastOutSlowInEasing.transform(backEvent.progress)
                predictiveBackProgress.snapTo(fluidProgress)
            }
            if (showTorInstallPopup) {
                showTorInstallPopup = false
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = ExpressiveMotionTokens.morphSpring()
                )
            } else {
                keyboardController?.hide()
                hasStartedTyping = false
                sensoryEngine.hapticEngine.performPredictiveBackHaptic(view)
                viewModel.onQueryChanged("")
                textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                coroutineScope.launch {
                    predictiveBackProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault()
                    )
                }
                overlayProgressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault()
                )
                goToHomeScreen()
            }
        } catch (_: java.util.concurrent.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                sensoryEngine.hapticEngine.performHaptic(view, PixelHapticType.SPRING_RELEASE)
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = ExpressiveMotionTokens.morphSpring()
                )
            }
        } finally {
            com.pixel.intelligentsearch.core.ui.WindowFramePacing.setNormalRefreshRateCategory(context.findActivity())
        }
    }

    val visibleApps by remember {
        derivedStateOf {
            val hidden = settingsState.hiddenApps
            uiState.filteredApps.filter { !hidden.contains(it.packageName) }.distinctBy { it.packageName }
        }
    }

    val isAppsEnabled by remember {
        derivedStateOf {
            settingsState.searchApps
        }
    }
    val isContactsEnabled by remember {
        derivedStateOf {
            settingsState.searchContacts
        }
    }
    val isFilesEnabled by remember {
        derivedStateOf {
            settingsState.searchFiles
        }
    }

    val domainMatchPriorities by remember {
        derivedStateOf {
            listOf(
                "apps" to appWeight,
                "contacts" to contactWeight,
                "files" to fileWeight
            ).sortedByDescending { it.second }
        }
    }

    val bestMatch by remember {
        derivedStateOf {
            val q = uiState.query.trim()
            if (q.isEmpty()) return@derivedStateOf null

            // Tier 1: Exact matches across domains in user-configured priority order
            for ((domain, _) in domainMatchPriorities) {
                when (domain) {
                    "apps" -> {
                        if (isAppsEnabled) {
                            val exactApp = visibleApps.firstOrNull { it.name.equals(q, ignoreCase = true) }
                            if (exactApp != null) return@derivedStateOf exactApp
                        }
                    }
                    "contacts" -> {
                        if (isContactsEnabled) {
                            val exactContact = uiState.contacts.firstOrNull { it.name.equals(q, ignoreCase = true) }
                            if (exactContact != null) return@derivedStateOf exactContact
                        }
                    }
                    "files" -> {
                        if (isFilesEnabled) {
                            val exactFile = uiState.files.firstOrNull { it.name.equals(q, ignoreCase = true) }
                            if (exactFile != null) return@derivedStateOf exactFile
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
                            if (appMatch != null) return@derivedStateOf appMatch
                        }
                    }
                    "contacts" -> {
                        if (isContactsEnabled) {
                            val contactMatch = uiState.contacts.firstOrNull { it.name.startsWith(q, ignoreCase = true) }
                            if (contactMatch != null) return@derivedStateOf contactMatch
                        }
                    }
                    "files" -> {
                        if (isFilesEnabled) {
                            val fileMatch = uiState.files.firstOrNull { it.name.startsWith(q, ignoreCase = true) }
                            if (fileMatch != null) return@derivedStateOf fileMatch
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
                            if (wordMatch != null) return@derivedStateOf wordMatch
                        }
                    }
                    "contacts" -> {
                        if (isContactsEnabled) {
                            val wordMatch = uiState.contacts.firstOrNull { c ->
                                c.name.split("\\s+".toRegex()).any { it.startsWith(q, ignoreCase = true) }
                            }
                            if (wordMatch != null) return@derivedStateOf wordMatch
                        }
                    }
                    "files" -> {
                        if (isFilesEnabled) {
                            val wordMatch = uiState.files.firstOrNull { f ->
                                f.name.split("\\s+".toRegex()).any { it.startsWith(q, ignoreCase = true) }
                            }
                            if (wordMatch != null) return@derivedStateOf wordMatch
                        }
                    }
                }
            }
            null
        }
    }
    
    val bestMatchText by remember {
        derivedStateOf {
            when (val match = bestMatch) {
                is ContactItem -> match.name
                is AppItem -> match.name
                is FileItem -> match.name
                else -> null
            }
        }
    }

    val filteredContacts by remember {
        derivedStateOf {
            val match = bestMatch
            if (match is ContactItem) uiState.contacts.filter { it != match }
            else uiState.contacts
        }
    }
    
    val filteredApps by remember {
        derivedStateOf {
            val match = bestMatch
            if (match is AppItem) visibleApps.filter { it != match }
            else visibleApps
        }
    }
    
    val filteredFiles by remember {
        derivedStateOf {
            val match = bestMatch
            if (match is FileItem) uiState.files.filter { it != match }
            else uiState.files
        }
    }

    val searchBarContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = showDebugPill,
                enter = fadeIn(animationSpec = ExpressiveMotionTokens.morphSpring()) + expandVertically(animationSpec = ExpressiveMotionTokens.morphSpring()),
                exit = fadeOut(animationSpec = ExpressiveMotionTokens.morphSpring()) + shrinkVertically(animationSpec = ExpressiveMotionTokens.morphSpring())
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
            val showPerfStats = prefs[SettingsManager.DEBUG_SHOW_PERF_STATS]
            AnimatedVisibility(
                visible = showPerfStats,
                enter = fadeIn(animationSpec = ExpressiveMotionTokens.morphSpring()) + expandVertically(animationSpec = ExpressiveMotionTokens.morphSpring()),
                exit = fadeOut(animationSpec = ExpressiveMotionTokens.morphSpring()) + shrinkVertically(animationSpec = ExpressiveMotionTokens.morphSpring())
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
                targetValue = if (textFieldValue.text.isNotEmpty()) 20.dp else 100.dp,
                animationSpec = com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens.morphSpring(),
                label = "search_pill_shape_morph"
            )
            val searchBarShape = RoundedCornerShape(searchBarCornerRadius)
            Row(
                modifier = Modifier
                    .tutorialTarget(1, prefs)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .graphicsLayer {
                        shape = searchBarShape
                        clip = true
                    }
                    // Gemini input plate design (Android 17 QPR3 Google app): deep Material You
                    // interior, light tone pooling along the bottom edge, ambient rim glow, bottom bevel
                    .materialYouGeminiPlate(
                        cornerRadius = searchBarCornerRadius,
                        alpha = if (settingsState.pillOpacity == 50) 1f else (settingsState.pillOpacity / 100f).coerceIn(0f, 1f)
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
                SearchOverlayLensIcon(
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val ghostTextAnnotated = remember(uiState.query, bestMatchText) {
                        val bmt = bestMatchText
                        if (bmt != null && bmt.startsWith(uiState.query, ignoreCase = true) && bmt.length > uiState.query.length) {
                            androidx.compose.ui.text.buildAnnotatedString {
                                withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent)) {
                                    append(bmt.substring(0, minOf(uiState.query.length, bmt.length)))
                                }
                                withStyle(androidx.compose.ui.text.SpanStyle(color = Color.Unspecified)) {
                                    append(bmt.substring(uiState.query.length))
                                }
                            }
                        } else null
                    }

                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newTfv ->
                            textFieldValue = newTfv
                            val newQuery = newTfv.text
                            if (newQuery.isNotEmpty()) {
                                hasStartedTyping = true
                            }
                            if (com.pixel.intelligentsearch.BuildConfig.DEBUG && newQuery == "*xy88x*") {
                                prefs.edit().put(SettingsManager.DEBUG_UNLOCKED, true).apply()
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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            hasStartedTyping = false
                            keyboardController?.hide()
                            // The collected uiState can lag a keystroke behind; read the live value so Enter
                            // never acts on a query missing its last character.
                            val submittedQuery = viewModel.uiState.value.query
                            if (submittedQuery.isNotEmpty() && viewModel.resolveBangIntent(submittedQuery) != null) {
                                // A bang is an explicit request for that site: it must not be taken over by an
                                // app match or Quick Launch ("!g maps" would otherwise open Maps).
                                launchWebSearch(submittedQuery)
                            } else if (submittedQuery.isNotEmpty()) {
                                viewModel.addSearchHistory(submittedQuery)
                                val match = bestMatch
                                if (match != null) {
                                    when (match) {
                                        is ContactItem -> {
                                            val intent = if (settingsState.contactDirectCall && match.phoneNumber.isNotBlank()) {
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
                                        is AppItem -> {
                                            performAppLaunch(match.packageName)
                                        }
                                        is FileItem -> {
                                            val intent = buildOpenFileIntent(match.uri, match.mimeType)
                                            if (!launchSafeIntent(context, intent)) {
                                                intent.setPackage(null)
                                                launchSafeIntent(context, intent)
                                            }
                                        }
                                    }
                                } else if (uiState.instantAnswer?.iconType == "url") {
                                    val answer = uiState.instantAnswer
                                    if (answer != null) {
                                        val targetUrl = if (answer.title.startsWith("http://", ignoreCase = true) || answer.title.startsWith("https://", ignoreCase = true)) {
                                            answer.title
                                        } else "https://${answer.title}"
                                        launchSafeIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        })
                                    }
                                } else if (settingsState.appQuickLaunch && visibleApps.isNotEmpty()) {
                                    performAppLaunch(visibleApps.first().packageName)
                                } else {
                                    launchWebSearch(submittedQuery)
                                }
                            }
                        }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (uiState.query.isEmpty()) {
                                    Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontFamily = GoogleSansFlex)
                                } else if (ghostTextAnnotated != null) {
                                    Text(
                                        text = ghostTextAnnotated,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
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
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    IconButton(
                        onClick = {
                            viewModel.onQueryChanged("")
                            textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                        },
                        modifier = Modifier
                            .size(48.dp)
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
                        if (ClickDebouncer.canClick()) {
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
                    Icon(
                        painter = painterResource(id = R.drawable.ic_three_bars),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
                    items(uiState.bangSuggestions, key = { bang -> "bang_${bang.prefix}" }, contentType = { "bang" }) { bang ->
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
            val pillPackages by remember {
                derivedStateOf {
                    val raw = if (settingsState.contextAwareQuickApps) {
                        uiState.recentApps.map { it.packageName }
                    } else {
                        settingsState.searchPills.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    }
                    raw.distinct().take(settingsState.shortcutResultsCount)
                }
            }
            val dynamicScale = if (pillPackages.size > 6) (6f / pillPackages.size.toFloat()).coerceIn(0.6f, 1f) else 1f
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pillPackages, key = { pkg -> "pill_$pkg" }, contentType = { "pill" }) { packageName ->
                            val appIconState = remember(packageName, settingsState.activeIconPack) {
                                mutableStateOf<AppIconResult?>(peekThemedAppIcon(packageName, settingsState.activeIconPack))
                            }
                            val appNameState = remember(packageName) { mutableStateOf(getAppName(context, packageName)) }
                            LaunchedEffect(packageName, settingsState.activeIconPack) {
                                if (appIconState.value == null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val icon = getThemedAppIcon(context, packageName, activePackOverride = settingsState.activeIconPack)
                                        val name = getAppName(context, packageName)
                                        appIconState.value = icon
                                        appNameState.value = name
                                    }
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
        val sortedResultSections by remember {
            derivedStateOf {
                listOf(
                    "apps" to appWeight,
                    "web" to webWeight,
                    "contacts" to contactWeight,
                    "files" to fileWeight
                ).sortedByDescending { it.second }.map { it.first }
            }
        }
        val matchingRecent by remember {
            derivedStateOf {
                val trimmed = uiState.query.trim()
                if (!settingsState.searchPreviousSearches || trimmed.isEmpty()) {
                    emptyList()
                } else {
                    uiState.recentSearches
                        .filter { it.contains(trimmed, ignoreCase = true) }
                        .distinct()
                }
            }
        }
        val allDisplaySuggestions by remember {
            derivedStateOf {
                val maxCount = settingsState.webResultsCount.coerceAtLeast(6)
                val nonRecentWeb = uiState.webSuggestions.filter { webSugg ->
                    matchingRecent.none { it.equals(webSugg, ignoreCase = true) }
                }
                (matchingRecent + nonRecentWeb).distinct().take(maxCount)
            }
        }

        val isBottomResults = settingsState.bottomSearch && settingsState.bottomSearchResult

        LazyColumn(
            state = searchResultsListState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (settingsState.bottomSearch) Modifier.statusBarsPadding() else Modifier
                ),
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
                    item(key = "inline_shortcuts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                    item(key = "lens_shortcut", contentType = "shortcut") {
                        ShortcutRow(
                            iconRes = R.drawable.ic_camera,
                            title = "Search with Google Lens",
                            onClick = {
                                hasStartedTyping = false
                                val intent = getLensSearchIntent(context)
                                launchSafeIntent(context, intent)
                            }
                        )
                    }
                    item(key = "voice_shortcut", contentType = "shortcut") {
                        ShortcutRow(
                            iconRes = R.drawable.ic_mic,
                            title = "Search with Voice",
                            onClick = {
                                hasStartedTyping = false
                                val intent = getVoiceSearchIntent(context)
                                launchSafeIntent(context, intent)
                            }
                        )
                    }
                    item(key = "assistant_shortcut", contentType = "shortcut") {
                        ShortcutRow(
                            iconRes = R.drawable.ic_lens_action,
                            title = "Digital Assistant",
                            onClick = {
                                hasStartedTyping = false
                                val intent = getVoiceActionIntent(context)
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
                item(key = "system_toggle_card_${systemToggle.id}", contentType = "system_toggle") {
                    com.pixel.intelligentsearch.core.ui.SystemToggleCard(
                        toggleState = systemToggle,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            val volumeSlider = uiState.volumeSlider
            if (volumeSlider != null) {
                item(key = "volume_slider_${volumeSlider.streamType}", contentType = "volume_slider") {
                    com.pixel.intelligentsearch.core.ui.VolumeSliderCard(
                        title = volumeSlider.title,
                        streamType = volumeSlider.streamType,
                        currentLevel = volumeSlider.currentLevel,
                        maxLevel = volumeSlider.maxLevel,
                        onVolumeChange = volumeSlider.onVolumeChange
                    )
                }
            }

            // Parsed intents (timers, alarms, events, messages, calls, navigation) always show; clipboard
            // actions are only added to this list when smart clipboard suggestions are enabled.
            if (uiState.directActions.isNotEmpty()) {
                items(uiState.directActions, key = { action -> "direct_action_${action.title}_${action.subtitle}" }, contentType = { "direct_action" }) { action ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableFloatStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = ExpressiveMotionTokens.bouncySpring()
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
                            .animateItem(
                                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                            )
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
                                                    launch { offsetX.animateTo(0f, ExpressiveMotionTokens.bouncySpring()) }
                                                    launch { rowAlpha.animateTo(1f, ExpressiveMotionTokens.gentleSpring()) }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                launch { offsetX.animateTo(0f, ExpressiveMotionTokens.bouncySpring()) }
                                                launch { rowAlpha.animateTo(1f, ExpressiveMotionTokens.gentleSpring()) }
                                            }
                                        }
                                    )
                                }
                                .bouncyClickable {
                                    hasStartedTyping = false
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    if (action.iconType == "private_space") {
                                        val act = context.findActivity()
                                        if (act != null) {
                                            viewModel.unlockPrivateSpace(act)
                                        }
                                    } else {
                                        action.intent?.let { intent ->
                                            launchSafeIntent(context, intent)
                                        }
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
                item(key = "direct_actions_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (bestMatch != null) {
                item(key = "best_match_hero_card", contentType = "best_match") {
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
                            // Header: Expressive "BEST MATCH" Pill & "Press Enter to Open" Action Hint
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
                                                val isDirectCall = settingsState.contactDirectCall || prefs[SettingsManager.CONTACT_DIRECT_CALL] || prefs[SettingsManager.CONTACT_DIRECT_CALL]
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
                                        val isDirectCall = settingsState.contactDirectCall || prefs[SettingsManager.CONTACT_DIRECT_CALL] || prefs[SettingsManager.CONTACT_DIRECT_CALL]
                                        Icon(
                                            imageVector = if (isDirectCall) Icons.Default.Call else Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = if (isDirectCall) "Call" else "Open",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                is AppItem -> {
                                    val appIconState = remember(match.packageName, settingsState.activeIconPack) {
                                        mutableStateOf<AppIconResult?>(peekThemedAppIcon(match.packageName, settingsState.activeIconPack))
                                    }
                                    LaunchedEffect(match.packageName, settingsState.activeIconPack) {
                                        if (appIconState.value == null) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val icon = getThemedAppIcon(context, match.packageName, activePackOverride = settingsState.activeIconPack)
                                                appIconState.value = icon
                                            }
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
                                                            if (!launchSafeIntent(context, intent)) {
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
                                                val intent = buildOpenFileIntent(match.uri, match.mimeType)
                                                if (!launchSafeIntent(context, intent)) {
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
                item(key = "recent_label", contentType = "recent_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                            )
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
                items(distinctRecents, key = { query -> "recent_$query" }, contentType = { "recent_query" }) { recentQuery ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableFloatStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = ExpressiveMotionTokens.bouncySpring()
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
                            .animateItem(
                                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                            )
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
                                                        ExpressiveMotionTokens.bouncySpring()
                                                    )
                                                }
                                                launch {
                                                    rowAlpha.animateTo(
                                                        1f,
                                                        ExpressiveMotionTokens.gentleSpring()
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
                                                    ExpressiveMotionTokens.bouncySpring()
                                                )
                                            }
                                            launch {
                                                rowAlpha.animateTo(
                                                    1f,
                                                    ExpressiveMotionTokens.gentleSpring()
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val pillShape = RoundedCornerShape(28.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
                                        )
                                    ),
                                    shape = pillShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                                    shape = pillShape
                                )
                                .clip(pillShape)
                                .bouncyClickable { launchWebSearch(recentQuery) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
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
                                onClick = {
                                    hasStartedTyping = true
                                    textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                        text = recentQuery,
                                        selection = androidx.compose.ui.text.TextRange(recentQuery.length)
                                    )
                                    viewModel.onQueryChanged(recentQuery)
                                },
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

            if (settingsState.searchCalendar && uiState.calendarEvents.isNotEmpty()) {
                // Title + time can repeat (same meeting on two calendars); the instance id and begin time cannot.
                items(uiState.calendarEvents, key = { event -> "event_${event.eventId}_${event.beginMillis}_${event.title}" }, contentType = { "calendar_event" }) { event ->
                    var dismissed by remember { mutableStateOf(false) }
                    var dismissDirection by remember { mutableFloatStateOf(1f) }
                    val offsetX = remember { Animatable(0f) }
                    val rowAlpha = remember { Animatable(1f) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(dismissed) {
                        if (dismissed) {
                            sensoryEngine.deleteThud(view)
                            launch {
                                offsetX.animateTo(
                                    targetValue = dismissDirection * 1500f,
                                    animationSpec = ExpressiveMotionTokens.bouncySpring()
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
                            .animateItem(
                                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
                            )
                            .graphicsLayer {
                                translationX = offsetX.value
                                alpha = rowAlpha.value
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = event.eventId > 0L) {
                                    hasStartedTyping = false
                                    val eventIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, event.eventId)
                                    ).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
                                    launchSafeIntent(context, eventIntent)
                                }
                                .semantics {
                                    // Swipe-to-dismiss has no TalkBack equivalent without this.
                                    customActions = listOf(CustomAccessibilityAction("Dismiss") { dismissed = true; true })
                                }
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
                                                    launch { offsetX.animateTo(0f, ExpressiveMotionTokens.bouncySpring()) }
                                                    launch { rowAlpha.animateTo(1f, ExpressiveMotionTokens.gentleSpring()) }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                launch { offsetX.animateTo(0f, ExpressiveMotionTokens.bouncySpring()) }
                                                launch { rowAlpha.animateTo(1f, ExpressiveMotionTokens.gentleSpring()) }
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
                item(key = "calendar_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (settingsState.searchShortcuts && uiState.shortcuts.isNotEmpty()) {
                items(uiState.shortcuts, key = { shortcut -> "shortcut_${shortcut.packageName}_${shortcut.id}_${shortcut.userHandle.hashCode()}" }, contentType = { "app_shortcut" }) { shortcut ->
                    Row(
                        modifier = Modifier.fillMaxWidth().expressiveRowClickable {
                            hasStartedTyping = false
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
                            try {
                                launcherApps?.startShortcut(shortcut.packageName, shortcut.id, null, null, shortcut.userHandle)
                                viewModel.onQueryChanged("")
                            } catch (e: Exception) { android.util.Log.w("IntelligentSearch", "Unexpected error", e) }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(shortcut.shortLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                    }
                }
                item(key = "shortcuts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            if (uiState.mathResult != null) {
                item(key = "math_result", contentType = "math_result") {
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
                item(key = "instant_answer", contentType = "instant_answer") {
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
                                        launchWebSearch(uiState.query)
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
                        val isAppsEnabled = settingsState.searchApps
                        val displayApps = if (filteredApps.isNotEmpty()) filteredApps else visibleApps
                        if (showApps && isAppsEnabled && displayApps.isNotEmpty()) {
                            item(key = "apps_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            item(key = "apps_row", contentType = "apps_row") {
                                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    if (uiState.query.isEmpty()) {
                                        item(key = "search_settings_shortcut", contentType = "settings_shortcut") {
                                            SearchSettingsItem {
                                                if (ClickDebouncer.canClick()) {
                                                    onOpenSettings("main")
                                                }
                                            }
                                        }
                                    }
                                    items(displayApps, key = { app -> "app_${app.packageName}_${app.userHandle?.hashCode() ?: 0}_${app.profileType}" }, contentType = { "app_item" }) { app ->
                                        AppGridItem(app) { performAppLaunch(app.packageName) }
                                    }
                                }
                            }
                        }
                    }
                    "web" -> {
                        if (showWeb && (settingsState.searchWeb || suggestionsEnabled) && uiState.query.isNotEmpty()) {
                            if (allDisplaySuggestions.isNotEmpty()) {
                                items(allDisplaySuggestions, key = { suggestion -> "sugg_$suggestion" }, contentType = { "web_suggestion" }) { suggestion ->
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
                                                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                                                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                                                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
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
                        val isContactsEnabled = settingsState.searchContacts
                        val displayContacts = if (filteredContacts.isNotEmpty()) filteredContacts else uiState.contacts
                        if (showPeople && isContactsEnabled && displayContacts.isNotEmpty()) {
                            item(key = "contacts_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            items(displayContacts, key = { contact -> "contact_${contact.lookupUri.ifBlank { "${contact.name}_${contact.phoneNumber}" }}" }, contentType = { "contact" }) { contact ->
                                val isDirectCall = settingsState.contactDirectCall || prefs[SettingsManager.CONTACT_DIRECT_CALL] || prefs[SettingsManager.CONTACT_DIRECT_CALL]
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
                        val isFilesEnabled = settingsState.searchFiles
                        val displayFiles = if (filteredFiles.isNotEmpty()) filteredFiles else uiState.files
                        if (showFiles && isFilesEnabled && displayFiles.isNotEmpty()) {
                            item(key = "files_divider", contentType = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), thickness = 0.8.dp, modifier = Modifier.padding(horizontal = 16.dp)) }
                            items(displayFiles, key = { file -> "file_${file.uri}" }, contentType = { "file" }) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .expressiveRowClickable {
                                            hasStartedTyping = false
                                            val intent = buildOpenFileIntent(file.uri, file.mimeType)
                                            if (!launchSafeIntent(context, intent)) {
                                                intent.setPackage(null)
                                                launchSafeIntent(context, intent)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (settingsState.filesThumbnails) {
                                        FileIconThumbnail(file.uri, file.mimeType)
                                    } else {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(fileLocationLabel(file), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                var dragJob: kotlinx.coroutines.Job? = null
                val act = context.findActivity()
                detectVerticalDragGestures(
                    onDragStart = {
                        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(act)
                    },
                    onDragEnd = {
                        val commitThreshold = 0.75f
                        coroutineScope.launch {
                            try {
                                val target = if (overlayProgressAnim.value > commitThreshold) 1f else 0f
                                val currentVel = overlayProgressAnim.velocity
                                overlayProgressAnim.animateTo(
                                    targetValue = target,
                                    initialVelocity = currentVel,
                                    animationSpec = ExpressiveMotionTokens.criticallyDampedSpring()
                                )
                                if (target == 0f) {
                                    viewModel.onQueryChanged("")
                                    finishWithoutTransition(act)
                                }
                            } finally {
                                WindowFramePacing.setNormalRefreshRateCategory(act)
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            try {
                                val currentVel = overlayProgressAnim.velocity
                                overlayProgressAnim.animateTo(
                                    1f,
                                    initialVelocity = currentVel,
                                    animationSpec = ExpressiveMotionTokens.criticallyDampedSpring()
                                )
                            } finally {
                                WindowFramePacing.setNormalRefreshRateCategory(act)
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            change.consume()
                            com.pixel.intelligentsearch.core.ui.WindowFramePacing.setContentVelocity(view, dragAmount * 60f)
                            val deltaProgress = dragAmount / maxDragDistance
                            val newProgress = (overlayProgressAnim.value - deltaProgress).coerceIn(0f, 1f)
                            dragJob?.cancel()
                            dragJob = coroutineScope.launch {
                                overlayProgressAnim.snapTo(newProgress)
                            }
                            if (newProgress < 0.95f && !isKeyboardDismissedByUser) {
                                isKeyboardDismissedByUser = true
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
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
            settingsState.showWallpaper || prefs[SettingsManager.SHOW_WALLPAPER]
        }
        val isTransparency = if (settingsState.backgroundTransparency > 0) settingsState.backgroundTransparency else prefs[SettingsManager.BACKGROUND_TRANSPARENCY]
        val scrimColor = if (isShowWallpaper) MaterialTheme.colorScheme.scrim else MaterialTheme.colorScheme.background
        val scrimAlphaFactor = if (isShowWallpaper) ((isTransparency / 100f) * 0.7f).coerceIn(0f, 1f) else 1.0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                    val backProg = predictiveBackProgress.value.coerceIn(0f, 1f)
                    val scrimProgress = p * (1f - backProg * 0.5f)
                    val baseAlpha = if (isShowWallpaper) scrimAlphaFactor * scrimProgress else scrimProgress
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
                        WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.displayCutout)
                    } else {
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    }
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (settingsState.matrixAnimationEnabled) {
                // derivedStateOf so this only recomposes when the overlay crosses fully-hidden, not on
                // every frame of the open/close animation.
                val overlayFullyHidden by remember { derivedStateOf { overlayProgressAnim.value <= 0.001f } }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = overlayProgressAnim.value.coerceIn(0f, 1f)
                        }
                ) {
                    AnimatedMatrixBackground(isPaused = overlayFullyHidden)
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
                        val shellCornerRadius = androidx.compose.ui.unit.lerp(24.dp, 32.dp, backProg)
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
                        shape = RoundedCornerShape(shellCornerRadius)
                        clip = true
                    }
                    .then(
                        if (settingsState.bottomSearch) {
                            val outlineColor = MaterialTheme.colorScheme.outlineVariant
                            Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha))
                                .drawWithContent {
                                    drawContent()
                                    // Follows the layer clip's predictive-back radius (read in draw, so
                                    // the gesture never recomposes); a fixed 24dp outline lost its corners.
                                    val backProg = predictiveBackProgress.value.coerceIn(0f, 1f)
                                    val stroke = 1.dp.toPx()
                                    val radius = androidx.compose.ui.unit.lerp(24.dp, 32.dp, backProg).toPx() - stroke / 2f
                                    drawRoundRect(
                                        color = outlineColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
                                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                                    )
                                }
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
                    val contentOffsetPx = with(density) { 20.dp.toPx() }
                    if (!settingsState.bottomSearch) {
                        Spacer(modifier = Modifier.statusBarsPadding())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            alpha = p
                            translationY = (1f - p) * contentOffsetPx
                        }) { searchBarContent() }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                            alpha = a
                            translationY = (1f - a) * contentOffsetPx
                        }) { quickAppPanelContent() }
                        Box(modifier = Modifier.weight(1f).graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                            alpha = a
                            translationY = (1f - a) * contentOffsetPx
                        }
                        ) {
                            searchResultsContent()
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                            alpha = a
                            translationY = -(1f - a) * contentOffsetPx
                        }
                            // Android 18 progressive blur: only dissolves words as they are scrolled by under the bottom search pill
                            .then(
                                if (searchResultsListState.canScrollForward) {
                                    Modifier.progressiveEdgeBlur(
                                        edge = ProgressiveBlurDirection.BottomToTop,
                                        edgeHeight = 18.dp,
                                        maxBlur = 10.dp
                                    )
                                } else Modifier
                            )
                        ) {
                            searchResultsContent()
                        }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                            alpha = a
                            translationY = -(1f - a) * contentOffsetPx
                        }) { quickAppPanelContent() }
                        Box(modifier = Modifier.graphicsLayer {
                            val p = overlayProgressAnim.value.coerceIn(0f, 1f)
                            alpha = p
                            translationY = -(1f - p) * contentOffsetPx
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
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = if (settingsState.bottomSearch) 86.dp else 24.dp, start = 16.dp, end = 16.dp)
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
                    0 to TutorialStepInfo("Welcome!", "Hello! Thank you for installing Intelligent Search. Please follow the tutorial to show you around. [bugdroid]", Alignment.Center, showArrow = false, requireButtonPress = true),
                    1 to TutorialStepInfo("Search Bar", "This is your search bar. Start typing to find apps, contacts, and files instantly. You can also swipe away recent search cards to remove them.", Alignment.Center, showArrow = true, requireButtonPress = true),
                    2 to TutorialStepInfo("Settings", "Tap the settings icon ( [three_bars] ) to customize your search experience. Press OK below to open Settings now.", Alignment.Center, showArrow = true, requireButtonPress = true, showCircle = true)
                ),
                onComplete = {
                    if (prefs[SettingsManager.TUTORIAL_STEP] >= TutorialManager.TOTAL_STEPS - 1) {
                        TutorialManager.completeTutorial(prefs)
                    }
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

