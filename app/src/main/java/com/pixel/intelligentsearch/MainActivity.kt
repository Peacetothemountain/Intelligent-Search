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
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.search.SearchOverlayScreen
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
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
        val filter = android.content.IntentFilter("com.pixel.intelligentsearch.DISMISS_OVERLAY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissOverlayReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissOverlayReceiver, filter)
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(dismissOverlayReceiver)
        } catch (_: Exception) {}
        frameMetricsMonitor.detach()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (checkAndForwardIfSearchOverlayDisabled()) return
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        setIntent(intent)
        if (checkAndForwardIfSearchOverlayDisabled()) return
        val queryExtra = intent.getStringExtra("query") ?: intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        } else {
            searchViewModel.onQueryChanged("")
        }
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        // Immediately dismiss system splash screen to avoid any black splash or flicker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setDimAmount(0f)

        val prefs = getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val initialShowWallpaper = prefs.getBoolean("search.background.show.wall", prefs.getBoolean("show.wallpaper", true))
        val initialBlur = prefs.getInt("search.background.blur", prefs.getInt("background.blur", 50))
        if (initialShowWallpaper) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && initialBlur > 0) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // Intentionally NOT setShowWhenLocked(true): the overlay surfaces contacts, files and
        // history, so Android must require device unlock before it is shown from the keyguard.
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(this)
        val isTutorial = com.pixel.intelligentsearch.feature.settings.TutorialManager.isTutorialActive(prefs)
        window.setSoftInputMode(
            if (isTutorial) {
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                if (initialShowWallpaper && initialBlur > 0) {
                    window.setBackgroundBlurRadius(initialBlur)
                } else {
                    window.setBackgroundBlurRadius(0)
                }
            }
        }

        if (checkAndForwardIfSearchOverlayDisabled()) return
        val queryExtra = intent?.getStringExtra("query") ?: intent?.getStringExtra(SearchManager.QUERY) ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!queryExtra.isNullOrBlank()) {
            searchViewModel.onQueryChanged(queryExtra)
        }
        if (handleIntent(intent)) return
        
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetBlur != lastAppliedBlur) {
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    private fun handleIntent(intent: Intent?): Boolean {
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
        
        // Force widget update when leaving the search overlay
        try {
            com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(this)
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
            val queryExtra = intent?.getStringExtra("query") ?: intent?.getStringExtra(SearchManager.QUERY) ?: ""
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

    fun dismissOverlayToLauncher() {
        searchViewModel.onQueryChanged("")
        val moved = moveTaskToBack(true)
        if (!moved) {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }
}






