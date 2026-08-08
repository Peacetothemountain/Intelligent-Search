package com.pixel.intelligentsearch.feature.settings
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.feature.settings.SettingsScreensHub
import com.pixel.intelligentsearch.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        
        var appWidgetId = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        intent.extras?.let { extras ->
            appWidgetId = extras.getInt(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        val resultValue = android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)

        val screen = intent.getStringExtra("extra_screen") ?: "main"

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val themeMode = settingsState.theme
            val darkTheme = when (themeMode) {
                "Material Dark", "Dark mode", "Dark" -> true
                "Material Light", "Light mode", "Light" -> false
                else -> isSystemInDarkTheme()
            }

            val appTheme = when (themeMode) {
                "Material Dark", "Material Light" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.MATERIAL
                "Dark mode", "Dark" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.DARK
                "Light mode", "Light" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.LIGHT
                else -> com.pixel.intelligentsearch.core.ui.AppColorTheme.SYSTEM
            }

            IntelligentSearchTheme(darkTheme = darkTheme) {
                com.pixel.intelligentsearch.core.ui.GeminiAppBackgroundContainer(
                    appDesign = com.pixel.intelligentsearch.core.ui.AppDesignTheme.SYSTEM,
                    appTheme = appTheme,
                    customColor = null
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        val prefs = getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.pixel.intelligentsearch.feature.settings.LocalSettingsViewModel provides settingsViewModel,
                            com.pixel.intelligentsearch.feature.settings.LocalSettingsState provides settingsState
                        ) {
                            SettingsScreensHub(
                                initialScreen = screen,
                                prefs = prefs,
                                onBackToLauncher = {
                                    finish()
                                },
                                context = this@SettingsActivity
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        
        // Force widget update when leaving settings
        val updateIntent = android.content.Intent(this, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java).apply {
            action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = android.appwidget.AppWidgetManager.getInstance(this@SettingsActivity)
                .getAppWidgetIds(android.content.ComponentName(this@SettingsActivity, com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider::class.java))
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        sendBroadcast(updateIntent)
    }
}
