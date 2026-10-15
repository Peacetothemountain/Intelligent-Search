package com.pixel.intelligentsearch
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
import androidx.lifecycle.lifecycleScope
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
        com.pixel.intelligentsearch.core.system.SearchRoleManager.ensureUserSetupSearchEngineAllowlist(this)
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
        if (!queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        } else {
            searchViewModel.onQueryChanged("")
        }
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

        val prefs = getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        var restoredDefaultPills: String? = null
        if (!prefs.getBoolean("profile_1_restored_to_default_v918", false)) {
            val defaultPills = "com.android.chrome,com.google.android.apps.maps,com.google.android.youtube,com.android.vending,com.google.android.contacts,com.google.android.apps.nbu.files"
            restoredDefaultPills = defaultPills
            prefs.edit()
                .putString("app_search_profile_1", defaultPills)
                .putString("app_search_profile_1_saved", defaultPills)
                .putString("search.pills", defaultPills)
                .putInt("shortcut_results_count", 6)
                .putBoolean("profile_1_restored_to_default_v918", true)
                .apply()
        }
        val initialShowWallpaper = prefs.getBoolean("search.background.show.wall", prefs.getBoolean("show_wallpaper", prefs.getBoolean("show.wallpaper", true)))
        val initialBlur = prefs.getInt("search.background.blur", prefs.getInt("background.blur", 50))
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

        // The overlay reads pills from DataStore, so the one-time reset above has to land there too.
        restoredDefaultPills?.let { pills ->
            lifecycleScope.launch {
                settingsManager.updateSetting(com.pixel.intelligentsearch.core.data.SettingsManager.SEARCH_PILLS, pills)
                settingsManager.updateSetting(com.pixel.intelligentsearch.core.data.SettingsManager.SHORTCUT_RESULTS_COUNT, 6)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        runCatching {
            if (initialShowWallpaper && initialBlur > 0) {
                window.setBackgroundBlurRadius(initialBlur)
            } else {
                window.setBackgroundBlurRadius(0)
            }
        }

        if (handleIntent(intent)) return
        if (checkAndForwardIfSearchOverlayDisabled()) return
        val queryExtra = (intent?.getStringExtra("query") ?: intent?.getStringExtra(SearchManager.QUERY) ?: intent?.getStringExtra(Intent.EXTRA_TEXT))?.take(512)
        if (!queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        }
        
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            
            val darkTheme = when (settingsState.theme) {
                "Material Dark", "Dark mode", "Dark" -> true
                "Material Light", "Light mode", "Light" -> false
                "System", "System App Theme", "System Default", "system" -> isSystemInDarkTheme()
                else -> isSystemInDarkTheme()
            }
            
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
                                    window.setBackgroundBlurRadius(targetBlur)
                                } else {
                                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                    window.setBackgroundBlurRadius(0)
                                }
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
                                    val intent = Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                        putExtra("extra_screen", route)
                                        putExtra("FROM_SEARCH_OVERLAY", true)
                                    }
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
                                onLaunchApp = { packageName ->
                                    searchViewModel.notifyAppLaunch(packageName)
                                    searchViewModel.onQueryChanged("")
                                    try {
                                        multiProfileManager.launchApp(packageName = packageName, activity = this@MainActivity)
                                    } catch (e: Throwable) {
                                        android.util.Log.e("MainActivity", "Failed to launch app $packageName", e)
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

    private fun handleIntent(intent: Intent?): Boolean {
        if (this is com.pixel.intelligentsearch.feature.search.SearchActivity) {
            return false
        }
        if (intent?.action == "com.pixel.intelligentsearch.LAUNCH_GEMINI_OVERLAY" ||
            intent?.getBooleanExtra("launch_gemini_overlay", false) == true) {
            com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.launchGeminiCornerOverlay(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            finish()
            return true
        }

        if (intent?.action == "com.pixel.intelligentsearch.LAUNCH_LENS") {
            try {
                val lensStandalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (lensStandalone != null) {
                    startActivity(lensStandalone)
                } else {
                    val lensIntent = Intent().apply {
                        setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(lensIntent)
                }
            } catch (e: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            finish()
            return true
        }
        
        if (intent?.action == "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE") {
            try {
                val lensStandalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
                    putExtra("lens_mode", "translate")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (lensStandalone != null) {
                    startActivity(lensStandalone)
                } else {
                    val translateIntent = Intent().apply {
                        setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                        putExtra("lens_mode", "translate")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(translateIntent)
                }
            } catch (ex: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            finish()
            return true
        }

        return false
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            com.pixel.intelligentsearch.feature.search.clearAllUiMemoryCaches()
            com.pixel.intelligentsearch.core.data.SystemDataProvider.invalidateAppsCache()
        }
    }

    private fun checkAndForwardIfSearchOverlayDisabled(): Boolean {
        val prefs = getSharedPreferences("PREFERENCES_CUSTOMISATIONS", android.content.Context.MODE_PRIVATE)
        val searchOverlayEnabled = prefs.getBoolean("search_overlay_enabled", true)
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






