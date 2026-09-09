package com.pixel.intelligentsearch
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
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import com.pixel.intelligentsearch.feature.search.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    private val searchViewModel: SearchViewModel by viewModels()
    private val adpfThermalManager by lazy { com.pixel.intelligentsearch.core.performance.ADPFThermalManager.getInstance(this) }
    private val frameMetricsMonitor by lazy { com.pixel.intelligentsearch.core.performance.FrameMetricsMonitor(adpfThermalManager) }

    override fun onStart() {
        super.onStart()
        frameMetricsMonitor.attach(this)
    }

    override fun onStop() {
        frameMetricsMonitor.detach()
        super.onStop()
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
        val queryExtra = intent.getStringExtra("query") ?: intent.getStringExtra(SearchManager.QUERY)
        if (queryExtra != null) {
            searchViewModel.onQueryChanged(queryExtra)
        } else {
            searchViewModel.onQueryChanged("")
        }
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setInheritShowWhenLocked(true)
        }
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(this)
        super.onCreate(savedInstanceState)

        if (handleIntent(intent)) return

        val prefs = getSharedPreferences("PREFERENCES_CUSTOMISATIONS", android.content.Context.MODE_PRIVATE)
        val searchOverlayEnabled = prefs.getBoolean("search_overlay_enabled", true)
        if (!searchOverlayEnabled) {
            val fallbackIntent = Intent("android.search.action.GLOBAL_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (packageManager.resolveActivity(fallbackIntent, 0) != null) {
                startActivity(fallbackIntent)
            } else {
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
            finish()
            return
        }
        
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            
            val darkTheme = when (settingsState.theme) {
                "Material Dark", "Dark mode", "Dark" -> true
                "Material Light", "Light mode", "Light" -> false
                else -> isSystemInDarkTheme()
            }
            
            IntelligentSearchTheme(darkTheme = darkTheme) {
                Surface(
                     modifier = Modifier.fillMaxSize(),
                      color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    val throttleLevel by adpfThermalManager.thermalThrottleLevel.collectAsStateWithLifecycle()
                    DisposableEffect(settingsState.backgroundBlur, settingsState.showWallpaper, throttleLevel) {
                        val recommendedBlur = adpfThermalManager.getRecommendedBlurRadius(settingsState.backgroundBlur.toFloat()).toInt()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (settingsState.showWallpaper && recommendedBlur > 0) {
                                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                window.setBackgroundBlurRadius(recommendedBlur)
                            } else {
                                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                window.setBackgroundBlurRadius(0)
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
                                    }
                                    val options = android.app.ActivityOptions.makeCustomAnimation(
                                        this@MainActivity,
                                        R.anim.slide_in_right,
                                        R.anim.slide_out_left
                                    )
                                    startActivity(intent, options.toBundle())
                                },
                                onLaunchApp = { packageName ->
                                    searchViewModel.onQueryChanged("")
                                    val multiProfileManager = com.pixel.intelligentsearch.core.profile.MultiProfileManager(this@MainActivity)
                                    val launched = multiProfileManager.launchApp(packageName = packageName)
                                    if (launched) {
                                        if (settingsState.appAnimations) {
                                            finish()
                                        } else {
                                            finish()
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                                            } else {
                                                @Suppress("DEPRECATION")
                                                overridePendingTransition(0, 0)
                                            }
                                        }
                                    }
                                },
                                viewModel = searchViewModel,
                                isKeyboardDisabled = false
                            )
                        }
                    }
                }
            }
        }
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
        
        // Force widget update when leaving the home screen app
        val updateIntent = Intent(this, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                .getAppWidgetIds(android.content.ComponentName(this@MainActivity, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java))
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(updateIntent)
    }

    fun dismissOverlayToLauncher() {
        searchViewModel.onQueryChanged("")
        moveTaskToBack(true)
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}






