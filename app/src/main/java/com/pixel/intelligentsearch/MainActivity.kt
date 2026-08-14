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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setInheritShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(this)
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }

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

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
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
                    SideEffect {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(settingsState.backgroundBlur)
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
                                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                    if (launchIntent != null) {
                                if (settingsState.appAnimations) {
                                            val dm = resources.displayMetrics
                                            val options = android.app.ActivityOptions.makeScaleUpAnimation(
                                                window.decorView, dm.widthPixels / 2, dm.heightPixels / 2, 0, 0
                                            )
                                            startActivityForResult(launchIntent, 0, options.toBundle())
                                        } else {
                                            val options = android.app.ActivityOptions.makeCustomAnimation(this@MainActivity, 0, 0)
                                            startActivityForResult(launchIntent, 0, options.toBundle())
                                        }
                                    }
                                },
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
                val lensStandalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
                if (lensStandalone != null) {
                    startActivityForResult(lensStandalone, 0)
                    return true
                }
            } catch (e: Exception) {}
            
            val lensIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
            }
            try {
                startActivityForResult(lensIntent, 0)
            } catch (ex: Exception) {}
            return true
        }
        
        if (intent?.action == "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE") {
            try {
                val lensStandalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
                if (lensStandalone != null) {
                    // Try to pass translation mode to standalone lens
                    lensStandalone.putExtra("lens_mode", "translate")
                    startActivityForResult(lensStandalone, 0)
                    return true
                }
            } catch (e: Exception) {}
            
            val translateIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                putExtra("lens_mode", "translate")
            }
            try {
                startActivityForResult(translateIntent, 0)
            } catch (ex: Exception) {}
            return true
        }

        return false
    }

    override fun onPause() {
        super.onPause()
        
        // Force widget update when leaving the home screen app
        val updateIntent = Intent(this, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                .getAppWidgetIds(android.content.ComponentName(this@MainActivity, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java))
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(updateIntent)
    }

    override fun finish() {
        super.finish()
    }

}






