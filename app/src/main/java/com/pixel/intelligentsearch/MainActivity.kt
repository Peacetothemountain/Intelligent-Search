package com.pixel.intelligentsearch
import com.pixel.intelligentsearch.feature.settings.SettingsActivity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
open class MainActivity : AppCompatActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        
        if (handleIntent(intent)) return

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
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                        putExtra("extra_screen", route)
                                    })
                                },
                                onLaunchApp = { packageName ->
                                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                    if (launchIntent != null) {
                                        if (settingsState.appAnimations) {
                                            startActivity(launchIntent)
                                        } else {
                                            val options = android.app.ActivityOptions.makeCustomAnimation(this@MainActivity, 0, 0)
                                            startActivity(launchIntent, options.toBundle())
                                        }
                                        finish()
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
                    startActivity(lensStandalone)
                    finish()
                    return true
                }
            } catch (e: Exception) {}
            
            val lensIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(lensIntent)
            } catch (ex: Exception) {}
            finish()
            return true
        }
        
        if (intent?.action == "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE") {
            try {
                val lensStandalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
                if (lensStandalone != null) {
                    // Try to pass translation mode to standalone lens
                    lensStandalone.putExtra("lens_mode", "translate")
                    startActivity(lensStandalone)
                    finish()
                    return true
                }
            } catch (e: Exception) {}
            
            val translateIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("lens_mode", "translate")
            }
            try {
                startActivity(translateIntent)
            } catch (ex: Exception) {}
            finish()
            return true
        }

        return false
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) {
            finish()
        }
    }

    override fun finish() {
        super.finish()
    }

}
