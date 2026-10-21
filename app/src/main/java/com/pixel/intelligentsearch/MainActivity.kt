package com.pixel.intelligentsearch
import com.pixel.intelligentsearch.feature.widget.launchGeminiCornerOverlay
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import com.pixel.intelligentsearch.feature.settings.SettingsActivity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.SearchOverlayScreen
import androidx.compose.foundation.layout.Box
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import android.app.SearchManager

import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var multiProfileManager: com.pixel.intelligentsearch.core.profile.MultiProfileManager

    @javax.inject.Inject
    lateinit var settingsManager: com.pixel.intelligentsearch.core.data.SettingsManager

    private val searchViewModel: SearchViewModel by viewModels()
    private val adpfThermalManager by lazy { com.pixel.intelligentsearch.core.performance.ADPFThermalManager.getInstance(this) }
    private val frameMetricsMonitor by lazy { com.pixel.intelligentsearch.core.performance.FrameMetricsMonitor(adpfThermalManager) }

    private val dismissOverlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        frameMetricsMonitor.attach(this)
        adpfThermalManager.startHeadroomMonitoring()
        // Only this app sends DISMISS_OVERLAY (always with setPackage), so it is never exported. On
        // API 31-32, where the flag does not exist, ContextCompat enforces the same isolation with a
        // signature-protected permission.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            dismissOverlayReceiver,
            android.content.IntentFilter("com.pixel.intelligentsearch.DISMISS_OVERLAY"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(dismissOverlayReceiver)
        } catch (_: Exception) {}
        frameMetricsMonitor.detach()
        adpfThermalManager.stopHeadroomMonitoring()
        super.onStop()
    }

    // Settings snapshot taken on resume, so onPause only re-renders widgets when something actually changed.
    private var settingsFingerprintAtResume = 0

    private fun settingsFingerprint(): Int = runCatching {
        com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.getSafeSharedPreferences(this).all.hashCode()
    }.getOrDefault(0)

    override fun onResume() {
        super.onResume()
        settingsFingerprintAtResume = settingsFingerprint()
        if (checkAndForwardIfSearchOverlayDisabled()) return
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        window.setWindowAnimations(0)
        window.decorView.elevation = 0f
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        setIntent(intent)
        if (handleIntent(intent)) return
        if (checkAndForwardIfSearchOverlayDisabled()) return
        val queryExtra = (intent.getStringExtra("query") ?: intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT))?.take(512)
        clearConsumedQuery()
        if (!queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        } else {
            searchViewModel.onQueryChanged("")
        }
        requestVoiceIfAsked(intent, restoreKeyboard = true)
    }

    /**
     * The assistant gesture with Start Assistant with Voice on asks the search screen to start listening, with the
     * keyboard kept down. With [restoreKeyboard], a plain open puts the keyboard mode back after an earlier voice one.
     * The assistant asks through the private WidgetActivity. SearchActivity is exported, so any app could send it
     * the same extra and open search with the microphone on; there it is ignored.
     */
    private fun requestVoiceIfAsked(intent: Intent?, restoreKeyboard: Boolean = false) {
        val startVoice = this !is com.pixel.intelligentsearch.feature.search.SearchActivity &&
            intent?.getBooleanExtra(com.pixel.intelligentsearch.feature.voice.IntelligentVoiceInteractionSession.EXTRA_START_VOICE, false) == true
        if (startVoice) {
            applySoftInputMode(keyboardVisible = false)
            searchViewModel.requestVoice()
        } else if (restoreKeyboard) {
            val prefs = getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            applySoftInputMode(keyboardVisible = !com.pixel.intelligentsearch.feature.settings.TutorialManager.isTutorialActive(prefs))
        }
    }

    /** The background blur the settings and thermal state ask for. */
    private var targetBlurRadius = 0
    /** How much of it to show: the overlay sets this from its open/close progress. Starts at 0 for the entrance. */
    private var blurFraction = 0f
    private var lastWindowBlur = -1

    /**
     * Scales the window's background blur by [fraction] (0 to 1). The overlay calls this every frame of its
     * entrance and exit, so the blur fades with the panel instead of snapping on or off; only the radius changes.
     */
    fun setBlurFraction(fraction: Float) {
        blurFraction = fraction.coerceIn(0f, 1f)
        applyWindowBlur()
    }

    private fun applyWindowBlur() {
        val radius = (targetBlurRadius * blurFraction).toInt()
        if (radius == lastWindowBlur) return
        lastWindowBlur = radius
        runCatching { window.setBackgroundBlurRadius(radius) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Immediately dismiss system splash screen to avoid any black splash or flicker
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setDimAmount(0f)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setWindowAnimations(0)
        window.decorView.elevation = 0f

        val prefs = getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val initialShowWallpaper = prefs[SettingsManager.SHOW_WALLPAPER]
        val initialBlur = prefs[SettingsManager.BACKGROUND_BLUR]
        if (initialShowWallpaper) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (initialBlur > 0) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        disableLegacyStatusBarContrastScrim()
        // Intentionally NOT setShowWhenLocked(true): the overlay surfaces contacts, files and
        // history, so Android must require device unlock before it is shown from the keyguard.
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(this)
        val isTutorial = com.pixel.intelligentsearch.feature.settings.TutorialManager.isTutorialActive(prefs)
        applySoftInputMode(keyboardVisible = !isTutorial)
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // The overlay scales this with its entrance (see setBlurFraction), so the blur fades in rather than snapping.
        targetBlurRadius = if (initialShowWallpaper && initialBlur > 0) initialBlur else 0
        applyWindowBlur()

        if (handleIntent(intent)) return
        // A recreated activity gets its launch query back, though the first instance already used it.
        if (savedInstanceState != null) clearConsumedQuery()
        if (checkAndForwardIfSearchOverlayDisabled()) return
        val queryExtra = (intent?.getStringExtra("query") ?: intent?.getStringExtra(SearchManager.QUERY) ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.take(512)
        clearConsumedQuery()
        // Only a fresh open: a restored activity still carries its launch query, which was already used and cleared.
        if (savedInstanceState == null && !queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        }
        // Only a fresh open starts voice: a recreated activity (rotation) still carries the extra but must not restart it.
        if (savedInstanceState == null) requestVoiceIfAsked(intent)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            
            val darkTheme = ThemeMode.from(settingsState.theme).isDark(isSystemInDarkTheme())
            
            IntelligentSearchTheme(darkTheme = darkTheme) {
                Surface(
                     modifier = Modifier.fillMaxSize(),
                      color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    val throttleLevel by adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
                    var lastAppliedWall by remember { mutableStateOf<Boolean?>(null) }
                    var lastAppliedBlur by remember { mutableIntStateOf(-1) }
                    DisposableEffect(settingsState.backgroundBlur, settingsState.showWallpaper, throttleLevel) {
                        val isWall = settingsState.showWallpaper
                        if (lastAppliedWall != isWall) {
                            if (isWall) {
                                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                            } else {
                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                            }
                            lastAppliedWall = isWall
                        }
                        val blurRadius = settingsState.backgroundBlur
                        val targetBlur = if (isWall) adpfThermalManager.getRecommendedBlurRadius(blurRadius.toFloat()).toInt() else 0
                        if (targetBlur != lastAppliedBlur) {
                            runCatching {
                                if (targetBlur > 0) {
                                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                } else {
                                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                }
                                targetBlurRadius = targetBlur
                                applyWindowBlur()
                                lastAppliedBlur = targetBlur
                            }
                        }
                        onDispose {}
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            SearchOverlayScreen(
                                onOpenSettings = { route ->
                                    val intent = com.pixel.intelligentsearch.core.security.InternalLaunchToken.stamp(
                                        Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                            putExtra("extra_screen", route)
                                            putExtra("FROM_SEARCH_OVERLAY", true)
                                        }
                                    )
                                    val options = android.app.ActivityOptions.makeCustomAnimation(
                                        this@MainActivity,
                                        com.pixel.intelligentsearch.R.anim.slide_in_right,
                                        com.pixel.intelligentsearch.R.anim.slide_out_left
                                    )
                                    try {
                                        startActivity(intent, options.toBundle())
                                    } catch (e: Throwable) {
                                        android.util.Log.e("MainActivity", "Failed to open settings", e)
                                    }
                                },
                                onLaunchApp = { packageName, user, activityName, options ->
                                    searchViewModel.notifyAppLaunch(packageName)
                                    searchViewModel.onQueryChanged("")
                                    try {
                                        multiProfileManager.launchApp(
                                            packageName = packageName,
                                            userHandle = user,
                                            activityName = activityName,
                                            opts = options,
                                            activity = this@MainActivity
                                        )
                                    } catch (e: Throwable) {
                                        android.util.Log.e("MainActivity", "App launch failed: ${e.javaClass.simpleName}") // no package name: it may be a hidden or Private Space app
                                    }
                                },
                                viewModel = searchViewModel,
                                settingsViewModel = settingsViewModel,
                                isKeyboardDisabled = false
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The status-bar contrast scrim was removed in Android 15 (API 35), where this setter is deprecated
     * and has no effect, so it is only applied on the releases that still draw the scrim (API 31-34).
     */
    private fun disableLegacyStatusBarContrastScrim() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }
    }

    /**
     * Applies the soft-input policy. ADJUST_RESIZE is deprecated since API 30 in favor of consuming IME
     * insets, which the Compose UI already does; the flag is kept for the legacy resize dispatch that
     * the overlay's keyboard-docked layout was tuned against, and is isolated here so the suppression
     * stays narrowly scoped.
     */
    private fun applySoftInputMode(keyboardVisible: Boolean) {
        @Suppress("DEPRECATION")
        val adjustResize = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        val stateFlag = if (keyboardVisible) {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        window.setSoftInputMode(stateFlag or adjustResize)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    /** Handles intents that hand off to another surface (Gemini overlay, Lens, voice) and close this activity. */
    private fun handleIntent(intent: Intent?): Boolean {
        if (intent == null || this is com.pixel.intelligentsearch.feature.search.SearchActivity) return false
        when {
            intent.action == "com.pixel.intelligentsearch.LAUNCH_GEMINI_OVERLAY" ||
                intent.getBooleanExtra("launch_gemini_overlay", false) -> launchGeminiCornerOverlay(this)
            intent.action == "com.pixel.intelligentsearch.LAUNCH_LENS" -> launchLens(translate = false)
            intent.action == "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE" -> launchLens(translate = true)
            // The launcher's "Voice search" shortcut. With no recognizer installed, the overlay opens instead.
            intent.getBooleanExtra("auto_voice", false) -> {
                val voice = com.pixel.intelligentsearch.feature.widget.getVoiceSearchIntent(this)
                if (voice.component?.packageName == packageName) return false
                try { startActivity(voice) } catch (e: Exception) { return false }
            }
            else -> return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        finish()
        return true
    }

    /** Opens Google Lens: the standalone app if installed, otherwise the Google app's Lens deep link. */
    private fun launchLens(translate: Boolean) {
        val lens = packageManager.getLaunchIntentForPackage("com.google.ar.lens") ?: Intent().setClassName(
            "com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink"
        )
        lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (translate) lens.putExtra("lens_mode", "translate")
        try { startActivity(lens) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        searchViewModel.onQueryChanged("")
        
        // Re-render widgets only if a setting changed while the overlay was open. Doing it on every dismissal
        // rebuilt every widget and spent the rate-limited widget-preview quota for nothing.
        try {
            if (settingsFingerprint() != settingsFingerprintAtResume) {
                com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to update widget on pause", e)
        }
    }

    /**
     * Drops the query from [getIntent] once it has been used, so nothing reads it again later. Otherwise turning the
     * overlay off in Settings and coming back would hand the old query, such as selected text, to a web search on
     * resume, searching it without the user asking.
     */
    private fun clearConsumedQuery() {
        val current = intent ?: return
        if (QUERY_EXTRAS.none { current.hasExtra(it) }) return
        setIntent(Intent(current).apply { QUERY_EXTRAS.forEach { removeExtra(it) } })
    }

    private companion object {
        /** Every extra a query can arrive in ([SearchManager.QUERY] is also the plain "query" extra). */
        val QUERY_EXTRAS = listOf(SearchManager.QUERY, Intent.EXTRA_TEXT)
    }

    private fun checkAndForwardIfSearchOverlayDisabled(): Boolean {
        val prefs = getSharedPreferences(SettingsManager.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val searchOverlayEnabled = prefs[SettingsManager.SEARCH_OVERLAY_ENABLED]
        if (!searchOverlayEnabled) {
            val queryExtra = (intent?.getStringExtra("query") ?: intent?.getStringExtra(SearchManager.QUERY) ?: "").take(512)
            val fallbackIntent = Intent("android.search.action.GLOBAL_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
                if (queryExtra.isNotEmpty()) {
                    putExtra(SearchManager.QUERY, queryExtra)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (packageManager.resolveActivity(fallbackIntent, 0) != null) {
                startActivity(fallbackIntent)
            } else if (intent?.action == Intent.ACTION_WEB_SEARCH) {
                // This activity was itself picked to handle the web search, so handing it on as another web search
                // would only bring it back here, over and over. With nowhere else to send it, the overlay shows it.
                return false
            } else {
                val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    if (queryExtra.isNotEmpty()) {
                        putExtra(SearchManager.QUERY, queryExtra)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (packageManager.resolveActivity(webIntent, 0) != null) {
                    startActivity(webIntent)
                }
            }
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            return true
        }
        return false
    }

}






