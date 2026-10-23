package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.feature.settings.TextButton
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.feature.settings.TutorialSpotlightOverlay
import com.pixel.intelligentsearch.feature.settings.TutorialManager
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import com.pixel.intelligentsearch.core.haptics.rememberScrollDetentController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.feature.settings.TutorialStepInfo
import android.content.Intent
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.core.data.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pixel.intelligentsearch.core.ui.WindowFramePacing
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import com.pixel.intelligentsearch.core.ui.progressiveEdgeBlur
import com.pixel.intelligentsearch.core.ui.ProgressiveBlurDirection
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Material 3 emphasized-accelerate: a quick, committed exit. */
private val EmphasizedAccelerate = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** How long the panel takes to leave after a swipe to home is released. */
private const val BACK_EXIT_MS = 200

/**
 * Whether search opens with the field focused and the keyboard up (Show Keyboard When Search Opens). Every way in
 * reads it: the overlay for its focus, and the activity for the window's keyboard mode.
 */
internal fun keyboardUpOnOpen(prefs: android.content.SharedPreferences): Boolean = prefs[SettingsManager.SHOW_KEYBOARD_ON_OPEN]

/** The first-run tutorial's steps on the overlay; Settings carries on from step 3. */
internal val OverlayTutorialSteps = mapOf(
    0 to TutorialStepInfo("Welcome!", "Hello! Thank you for installing Intelligent Search. Please follow the tutorial to show you around. [bugdroid]", Alignment.Center, showArrow = false, requireButtonPress = true),
    1 to TutorialStepInfo("Search Bar", "This is your search bar. Start typing to find apps, contacts, and files instantly. You can also swipe away recent search cards to remove them.", Alignment.Center, showArrow = true, requireButtonPress = true),
    2 to TutorialStepInfo("Settings", "Tap the settings icon ( [three_bars] ) to customize your search experience. Press Next below to open Settings now.", Alignment.Center, showArrow = true, requireButtonPress = true, showCircle = true)
)

@Composable
fun SearchOverlayScreen(
    onOpenSettings: (String) -> Unit,
    /**
     * Launches an app in its own profile: package, profile (null = personal), launcher activity if known, and the
     * launch's animation and source bounds (null for the system's default animation).
     */
    onLaunchApp: (packageName: String, user: android.os.UserHandle?, activityName: String?, launch: AppLaunchOptions?) -> Unit,
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
    val enterKeyActionValue by rememberSetting(SettingsManager.ENTER_KEY_ACTION)
    val enterKeyAction = com.pixel.intelligentsearch.core.data.settings.EnterKeyAction.from(enterKeyActionValue)
    val searchProviderName = remember(settingsState.searchEngine, settingsState.customSearchEngineUrl) {
        when (settingsState.searchEngine) {
            "DuckDuckGo" -> "DuckDuckGo"
            "Bing" -> "Bing"
            "Google" -> "Google"
            "Custom" -> com.pixel.intelligentsearch.core.security.CustomSearchUrl.displayName(settingsState.customSearchEngineUrl) ?: "Web"
            else -> if (settingsState.searchEngine.isNotBlank()) settingsState.searchEngine else "Google"
        }
    }
    // State the overlay's sections share; the screen binds it under the same names so its code reads the same.
    val savedHasStartedTyping = rememberSaveable { mutableStateOf(false) }
    // With Show Keyboard When Search Opens off, search opens as if the keyboard had been put away: the field waits for a tap.
    val savedKeyboardDismissed = rememberSaveable { mutableStateOf(!keyboardUpOnOpen(prefs)) }
    val ui = remember { OverlayUiState(savedHasStartedTyping, savedKeyboardDismissed, initialQuery = uiState.query) }
    var hasStartedTyping by ui::hasStartedTyping
    var textFieldValue by ui::textFieldValue

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotEmpty()) {
            hasStartedTyping = true
        }
        // New words, new results: the keyboard's highlight starts again from the search field.
        ui.highlightedKey = null
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
    // Overlay expansion progress. A fresh open starts at 0 and plays the entrance (scrim, panel, then search bar,
    // quick apps and results rising in turn); returning from an app snaps to 1, so it is instant and never transparent.
    val overlayProgressAnim = remember { Animatable(0f) }
    var hasEntered by remember { mutableStateOf(false) }
    // The window blur fades in and out with the overlay rather than snapping (the home screen sharpens as the
    // panel leaves after a swipe home).
    val blurHost = remember(context) { context.findActivity() as? com.pixel.intelligentsearch.MainActivity }
    LaunchedEffect(blurHost) {
        val host = blurHost ?: return@LaunchedEffect
        snapshotFlow { overlayProgressAnim.value }.collect { host.setBlurFraction(it) }
    }
    LaunchedEffect(Unit) {
        overlayProgressAnim.animateTo(1f, ExpressiveMotionTokens.Spatial.ExpressiveDefault())
        hasEntered = true
    }
    val predictiveBackProgress = remember { Animatable(0f) }
    // Swipe-to-home after release: 0 to 1 as the panel shrinks and slides away. While it runs the panel moves as
    // one piece (its contents skip their own entrance and exit motion).
    val backExitProgress = remember { Animatable(0f) }
    var exitingByBack by remember { mutableStateOf(false) }
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

    // App Animations: apps opened here open out of the icon they were opened from, as from the launcher.
    val launchOrigin = remember { LaunchOrigin() }
    val launchIcons = remember { LaunchIcons() }
    val appAnimations = settingsState.appAnimations
    val appLaunch: (LaunchSource) -> AppLaunchOptions? = remember(view, appAnimations) {
        { from ->
            AppLaunchAnimation.launch(view, appAnimations, launchIcons.bounds(from.iconKeys), launchOrigin.point.takeIf { from.byTouch })
        }
    }
    val performLaunch: (String, android.os.UserHandle?, String?, LaunchSource) -> Unit = remember(sensoryEngine, view, onLaunchApp, keyboardController, focusManager, appLaunch) {
        { packageName, user, activityName, from ->
            hasStartedTyping = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            sensoryEngine.appLaunch(view)
            onLaunchApp(packageName, user, activityName, appLaunch(from))
        }
    }
    // Work and Private Space copies of an app share its package name, so launches carry the profile too.
    val performAppLaunch: (AppItem, LaunchSource) -> Unit = remember(performLaunch) {
        { app, from -> performLaunch(app.packageName, app.userHandle, app.activityName, from) }
    }

    LaunchedEffect(uiState.mathResult) {
        if (!uiState.mathResult.isNullOrBlank()) {
            sensoryEngine.mathCalculation(view)
        }
    }

    val distinctRecents by remember { derivedStateOf { uiState.recentSearches.distinct() } }
    var showHistoryClearedSnackbar by ui::showHistoryClearedSnackbar
    // Five seconds, or longer when an accessibility service asks for more time to read and reach the Undo button.
    val accessibilityManager = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LaunchedEffect(showHistoryClearedSnackbar) {
        if (showHistoryClearedSnackbar) {
            delay(
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    5000L, containsIcons = false, containsText = true, containsControls = true
                ) ?: 5000L
            )
            showHistoryClearedSnackbar = false
        }
    }

    LaunchedEffect(isOpening) {
        if (isOpening) {
            if (hasEntered) overlayProgressAnim.snapTo(1f)
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
    

    var isKeyboardDismissedByUser by ui::isKeyboardDismissedByUser
    var showTorInstallPopup by ui::showTorInstallPopup

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
            // Our own exit has just played; the launcher's default return animation would be a second motion.
            context.startActivity(homeIntent, android.app.ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        } catch (e: Exception) {
            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
        }
        finishWithoutTransition(act)
    }

    val launchWebSearch: (String) -> Unit = launchWebSearch@{ searchQuery ->
        hasStartedTyping = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val torBangAddress = if (com.pixel.intelligentsearch.core.security.NetworkPolicy.isTorEngine(settingsState.searchEngine)) {
            viewModel.resolveBangWebAddress(searchQuery)
        } else null
        if (torBangAddress != null) {
            // A Tor user's search never reaches a normal browser or another app: the bang's address opens in Tor Browser.
            viewModel.addSearchHistory(searchQuery)
            viewModel.onQueryChanged("")
            when (val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.forAddress(context, torBangAddress, settingsState.searchEngine)) {
                is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open -> launchSafeIntent(context, launch.intent)
                else -> showTorInstallPopup = true
            }
            return@launchWebSearch
        }
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
        val intent = when (val launch = webSearchLaunch(context, engine, settingsState.customSearchEngineUrl, searchQuery)) {
            is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open -> launch.intent
            else -> {
                showTorInstallPopup = true
                null
            }
        }
        if (intent == null) return@launchWebSearch
        viewModel.addSearchHistory(searchQuery)
        viewModel.onQueryChanged("")
        if (!launchSafeIntent(context, intent)) {
            // A failed Tor launch must not fall back to a normal browser search.
            if (com.pixel.intelligentsearch.core.security.NetworkPolicy.isTorEngine(engine)) {
                showTorInstallPopup = true
                return@launchWebSearch
            }
            if (!launchSafeIntent(context, systemWebSearchIntent(searchQuery))) {
                Toast.makeText(context, "No app available to search the web.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Typed or tapped web addresses: http(s) only, browsable apps only, and only Tor Browser when Tor is the engine.
    val openWebAddress: (String) -> Unit = { address ->
        when (val launch = com.pixel.intelligentsearch.core.security.SafeWebLaunch.forAddress(context, address, settingsState.searchEngine)) {
            is com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.Open -> launchSafeIntent(context, launch.intent)
            com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.NeedsTorBrowser -> showTorInstallPopup = true
            com.pixel.intelligentsearch.core.security.SafeWebLaunch.Launch.NotAWebAddress -> launchWebSearch(address)
        }
    }

    // Voice search, Rambler style: listens until the check button, then fills in the tidied words and goes, the way
    // Enter would; the keyboard stays down. The spoken words wait here until the results for them are in.
    var spokenQuery by remember { mutableStateOf<String?>(null) }
    val voice = rememberOverlayVoice(
        onHeard = onHeard@{ text ->
            // Nothing was said: listening just ends, without a message, and the keyboard may come back.
            if (text.isBlank()) {
                ui.isKeyboardDismissedByUser = false
                return@onHeard
            }
            ui.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))
            ui.hasStartedTyping = true
            viewModel.onQueryChanged(text)
            spokenQuery = text
        },
        onProblem = { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            // Voice is over without words, so the keyboard may come back.
            ui.isKeyboardDismissedByUser = false
        },
    )
    val voiceRequests by viewModel.voiceRequests.collectAsStateWithLifecycle()
    LaunchedEffect(voiceRequests) {
        // Each request is claimed once, so recreating the screen doesn't start listening again.
        if (!viewModel.takeVoiceRequest()) return@LaunchedEffect
        // A second gesture while already listening must not throw away the words heard so far.
        if (voice.active) return@LaunchedEffect
        ui.isKeyboardDismissedByUser = true
        voice.start()
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    // Android hands the clipboard only to the app in focus, so its suggestions are read as the window gains focus:
    // on every open and every return from another app.
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) viewModel.refreshClipboardActions()
    }
    // The keyboard never rises over the listening bar: it waits until voice is done or cancelled.
    LaunchedEffect(windowInfo.isWindowFocused, transitionState.targetState, showTutorial, isKeyboardDisabled, isKeyboardDismissedByUser, voice.active) {
        if (voice.active) {
            keyboardController?.hide()
        } else if (windowInfo.isWindowFocused && transitionState.targetState && !isKeyboardDisabled && !showTutorial && !isKeyboardDismissedByUser) {
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
                // Resuming into a voice request (the assistant gesture) keeps the keyboard down, and so does Show
                // Keyboard When Search Opens being off (the field may still hold focus from before: it lets go).
                if (!voice.active) isKeyboardDismissedByUser = !keyboardUpOnOpen(prefs)
                if (!keyboardUpOnOpen(prefs)) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
                // Each return brings quick apps, upcoming events and contacts up to date, and app usage for ranking.
                viewModel.reloadOnResume()
                viewModel.refreshAppUsage()
                if (settingsState.searchFiles) {
                    viewModel.refreshStaleIndexes()
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
                    // The first resume belongs to the entrance animation; later ones (back from an app) are instant.
                    if (hasEntered) overlayProgressAnim.snapTo(1f)
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
    
    val backSwipes = remember { java.util.concurrent.atomic.AtomicInteger() }
    PredictiveBackHandler(enabled = !showTutorial) { progressFlow ->
        val swipe = backSwipes.incrementAndGet()
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
                exitingByBack = true
                val exitSpec = androidx.compose.animation.core.tween<Float>(BACK_EXIT_MS, easing = EmphasizedAccelerate)
                // The scrim and backdrop fade with the panel, which shrinks and slides out from where it was let go.
                coroutineScope.launch { overlayProgressAnim.animateTo(0f, exitSpec) }
                backExitProgress.animateTo(1f, exitSpec)
                goToHomeScreen()
            }
        } catch (_: java.util.concurrent.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                sensoryEngine.hapticEngine.performPredictiveBackCancelHaptic(view)
                predictiveBackProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = ExpressiveMotionTokens.morphSpring()
                )
            }
        } finally {
            // A swipe started while this one sprang back is already running at the high rate; resetting here
            // would drop it to the normal rate (and switch touch boost off) for the rest of that swipe.
            if (backSwipes.get() == swipe) {
                com.pixel.intelligentsearch.core.ui.WindowFramePacing.setNormalRefreshRateCategory(context.findActivity())
            }
        }
    }

    val visibleApps by remember {
        derivedStateOf {
            val hidden = settingsState.hiddenApps
            uiState.filteredApps.filter { !hidden.contains(it.packageName) }.distinctBy { it.packageName to it.userHandle }
        }
    }

    val bestMatch by remember {
        derivedStateOf {
            BestMatch.find(
                uiState.query,
                listOf(
                    BestMatch.Domain(appWeight, settingsState.searchApps, visibleApps, { uiState.shortcuts.nicknamesOf(com.pixel.intelligentsearch.core.search.ResultKeys.app(it)) }) { it.name },
                    BestMatch.Domain(contactWeight, settingsState.searchContacts, uiState.contacts, { uiState.shortcuts.nicknamesOf(com.pixel.intelligentsearch.core.search.ResultKeys.contact(it)) }) { it.name },
                    BestMatch.Domain(fileWeight, settingsState.searchFiles, uiState.files) { it.name }
                )
            )
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
    val contactActionTargets = rememberContactActionTargets(settingsState.searchContacts && settingsState.contactActions)
    
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

    // The one way to "go" once the results are in, for Enter and for the voice check button, following Enter Key
    // Action (OverlayEnterKey): a bang goes to its site; with Open Top Result the short phrasing's action runs, else
    // the best match opens, then a typed web address, Quick Launch, and finally a web search; with Search the Web,
    // a web search.
    val submitQuery: (String, Boolean) -> Unit = submit@{ submittedQuery, fromVoice ->
        val outcome = OverlayEnterKey.decide(
            query = submittedQuery,
            triggered = null,
            highlighted = null,
            phrasingAction = uiState.enterAction?.takeIf { it.intent != null },
            // A bang is an explicit request for that site: it must not be taken over by an app match or Quick Launch
            // ("!g maps" would otherwise open Maps).
            isBang = submittedQuery.isNotEmpty() && viewModel.resolveBangIntent(submittedQuery) != null,
            setting = enterKeyAction
        )
        when (outcome) {
            EnterOutcome.SearchWeb -> {
                launchWebSearch(submittedQuery)
                return@submit
            }
            // "15 min", "7:30am", "set a timer for 10 minutes": the top action, which opens the clock or calendar to
            // confirm. It is an action, not a search, so it isn't kept in the history.
            is EnterOutcome.RunAction -> {
                ui.hasStartedTyping = false
                outcome.action.intent?.let { launchSafeIntent(context, android.content.Intent(it)) }
                return@submit
            }
            EnterOutcome.OpenTopResult -> Unit
            // Nothing typed; a trigger word and the highlight were handled by onEnter before the results settled.
            else -> return@submit
        }
        viewModel.addSearchHistory(submittedQuery)
        when (val match = bestMatch) {
            is ContactItem -> {
                // A spoken name can be misheard, so voice opens the contact rather than dialling straight away.
                launchSafeIntent(context, ContactActions.openIntent(match, directCall = settingsState.contactDirectCall && !fromVoice))
            }
            is AppItem -> performAppLaunch(match, LaunchSource.keyboard(LaunchIconKeys.BEST_MATCH, LaunchIconKeys.app(match)))
            is FileItem -> openFileResult(context, prefs, match)
            else -> when {
                uiState.instantAnswer?.iconType == "url" -> uiState.instantAnswer?.let { answer -> openWebAddress(answer.title) }
                settingsState.appQuickLaunch && visibleApps.isNotEmpty() -> visibleApps.first().let { app -> performAppLaunch(app, LaunchSource.keyboard(LaunchIconKeys.app(app))) }
                else -> launchWebSearch(submittedQuery)
            }
        }
    }
    // "Go" for Enter and the voice check button. Either can come before the results for the last letters or words
    // are in, and acting then opened the previous query's Best Match or Quick Launch app; so it waits for this query's
    // results first.
    val submitWhenSettled: suspend (String, Boolean) -> Unit = { query, fromVoice ->
        viewModel.awaitSearch()
        // The results reach this screen through a collector; wait until what is composed here is what the view
        // model holds, so bestMatch is for this query and not the one before it.
        kotlinx.coroutines.withTimeoutOrNull(500) {
            snapshotFlow { uiState === viewModel.uiState.value && uiState.query == query }.first { it }
        }
        // The user may have typed on meanwhile; then this query is theirs no longer.
        if (viewModel.uiState.value.query == query) submitQuery(query, fromVoice)
    }
    // The check button's "go": once the results for the spoken words are in, act on them exactly as Enter would.
    LaunchedEffect(spokenQuery) {
        val spoken = spokenQuery ?: return@LaunchedEffect
        submitWhenSettled(spoken, true)
        spokenQuery = null
    }

    val sections = OverlayScope(
        onOpenSettings = onOpenSettings,
        viewModel = viewModel,
        uiState = uiState,
        settingsState = settingsState,
        prefs = prefs,
        ui = ui,
        showTutorial = showTutorial,
        searchProviderName = searchProviderName,
        appWeight = appWeight,
        webWeight = webWeight,
        contactWeight = contactWeight,
        fileWeight = fileWeight,
        suggestionsEnabled = suggestionsEnabled,
        focusRequester = focusRequester,
        voice = voice,
        listState = searchResultsListState,
        performLaunch = performLaunch,
        performAppLaunch = performAppLaunch,
        launchIcons = launchIcons,
        appLaunch = appLaunch,
        closeOverlay = closeOverlay,
        launchWebSearch = launchWebSearch,
        openWebAddress = openWebAddress,
        submitQuery = { query, fromVoice -> coroutineScope.launch { submitWhenSettled(query, fromVoice) } },
        enterKeyAction = enterKeyAction,
        visibleApps = visibleApps,
        bestMatch = bestMatch,
        bestMatchText = bestMatchText,
        filteredContacts = filteredContacts,
        contactActionTargets = contactActionTargets,
        filteredApps = filteredApps,
        filteredFiles = filteredFiles,
        distinctRecents = distinctRecents
    )
    val searchBarContent = @Composable { OverlaySearchBar(sections) }


    val quickAppPanelContent = @Composable { OverlayQuickAppPanel(sections) }

    val searchResultsContent = @Composable { OverlaySearchResults(sections) }

    val maxDragDistance = with(density) { 400.dp.toPx() } // Approx swipe distance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .recordLaunchOrigin(launchOrigin)
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
            // Tap on the empty background closes the overlay. A plain gesture rather than clickable(): clickable
            // made the whole overlay one merged accessibility node (TalkBack read it as a single button, and the
            // tutorial's pane title inside it crashed). Children's own clicks consume the tap first, as before.
            .pointerInput(showTutorial) {
                detectTapGestures(onTap = { if (!showTutorial) closeOverlay() })
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val isShowWallpaper = settingsState.showWallpaper
        val isTransparency = settingsState.backgroundTransparency
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
                // While the tutorial covers the screen, TalkBack can't reach or activate what lies beneath.
                .then(if (showTutorial) Modifier.clearAndSetSemantics {} else Modifier)
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
            // Expanded screens (an unfolded Pixel Fold or a tablet in landscape) get list and detail: the search bar,
            // quick apps and results at the start, the highlighted or top result's details beside them, in a wider
            // panel. Everything narrower keeps one column in the 720dp panel (useListDetailLayout).
            // Measured from this window, so the app in half of a split screen counts as the half it has.
            val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            val windowWidthDp = with(density) { windowSize.width.toDp() }
            val windowHeightDp = with(density) { windowSize.height.toDp() }
            val listDetail = useListDetailLayout(windowWidthDp.value, windowHeightDp.value)
            val listPaneWidth = (windowWidthDp * 0.42f).coerceIn(360.dp, 520.dp)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (listDetail) 1200.dp else 720.dp)
                    .fillMaxWidth()
                    // Nearly edge to edge: just enough margin to keep the panel's rounded outline on screen.
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        val progress = overlayProgressAnim.value.coerceIn(0.001f, 1f)
                        val backProg = predictiveBackProgress.value.coerceIn(0f, 1f)
                        val exit = backExitProgress.value.coerceIn(0f, 1f)
                        val shellCornerRadius = androidx.compose.ui.unit.lerp(24.dp, 32.dp, backProg)
                        // Material 3 predictive back for full-screen surfaces: toward 90% while dragging, then on
                        // release on down to about 80% as it leaves.
                        val predictiveScale = (1f - backProg * 0.10f) * (1f - exit * 0.12f)
                        scaleX = predictiveScale
                        scaleY = predictiveScale

                        // Follows the swipe toward the side it came from (Material: a twentieth of the width less
                        // 8 dp), then carries on that way out of view.
                        val direction = if (predictiveBackEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        val gestureShiftPx = (size.width / 20f - 8.dp.toPx()).coerceAtLeast(0f)
                        translationX = direction * (backProg * gestureShiftPx + exit * size.width * 0.12f)

                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        alpha = if (exitingByBack) (1f - exit) else (progress * (1f - backProg * 0.15f)).coerceIn(0f, 1f)
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
                    // Taps on the panel stay in it rather than reaching the background, which closes the overlay. A
                    // plain gesture, not clickable(): that merged every result without its own action into one
                    // TalkBack button.
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                
                // The search bar, quick apps and results: the whole panel on a phone, the start pane on a large screen.
                val searchColumn = @Composable { columnModifier: Modifier ->
                    Column(
                        modifier = columnModifier,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val contentOffsetPx = with(density) { 20.dp.toPx() }
                        if (!settingsState.bottomSearch) {
                            Spacer(modifier = Modifier.statusBarsPadding())
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.graphicsLayer {
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                alpha = p
                                translationY = (1f - p) * contentOffsetPx
                            }) { searchBarContent() }
                            Box(modifier = Modifier.graphicsLayer {
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                                alpha = a
                                translationY = (1f - a) * contentOffsetPx
                            }) { quickAppPanelContent() }
                            Box(modifier = Modifier.weight(1f).graphicsLayer {
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                                alpha = a
                                translationY = (1f - a) * contentOffsetPx
                            }
                            ) {
                                searchResultsContent()
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).graphicsLayer {
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
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
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                val a = (p - 0.1f).coerceIn(0f, 0.9f) / 0.9f
                                alpha = a
                                translationY = -(1f - a) * contentOffsetPx
                            }) { quickAppPanelContent() }
                            Box(modifier = Modifier.graphicsLayer {
                                val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                alpha = p
                                translationY = -(1f - p) * contentOffsetPx
                            }) { searchBarContent() }
                        }
                    }
                }
                if (listDetail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        searchColumn(Modifier.width(listPaneWidth).fillMaxHeight())
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .graphicsLayer {
                                    val p = if (exitingByBack) 1f else overlayProgressAnim.value.coerceIn(0f, 1f)
                                    val a = (p - 0.2f).coerceIn(0f, 0.8f) / 0.8f
                                    alpha = a
                                    translationY = (1f - a) * with(density) { 20.dp.toPx() }
                                }
                        ) {
                            OverlayDetailPane(sections)
                        }
                    }
                } else {
                    searchColumn(Modifier.fillMaxSize())
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
                    // Announced when it appears, so TalkBack users learn the history went and that Undo is there.
                    .semantics { liveRegion = LiveRegionMode.Polite }
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
                stepsInfo = OverlayTutorialSteps,
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

