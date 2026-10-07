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
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val ROLE_SEARCH_ENGINE = "android.app.role.SEARCH_ENGINE"
    }

    private val requestRoleLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val roleManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getSystemService(android.app.role.RoleManager::class.java)
        } else null
        val isHeld = roleManager?.isRoleHeld(ROLE_SEARCH_ENGINE) == true
        android.util.Log.d("SettingsActivity", "ROLE_SEARCH_ENGINE request result: ${result.resultCode}, isHeld: $isHeld")
    }

    fun requestSearchEngineRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(ROLE_SEARCH_ENGINE)) {
                if (!roleManager.isRoleHeld(ROLE_SEARCH_ENGINE)) {
                    val intent = roleManager.createRequestRoleIntent(ROLE_SEARCH_ENGINE)
                    requestRoleLauncher.launch(intent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        com.pixel.intelligentsearch.core.ui.WindowFramePacing.setHighRefreshRateCategory(this)
        super.onCreate(savedInstanceState)

        // Android 17 QPR2 native ROLE_SEARCH_ENGINE request
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(ROLE_SEARCH_ENGINE)) {
                if (!roleManager.isRoleHeld(ROLE_SEARCH_ENGINE)) {
                    val intent = roleManager.createRequestRoleIntent(ROLE_SEARCH_ENGINE)
                    requestRoleLauncher.launch(intent)
                }
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                com.pixel.intelligentsearch.R.anim.slide_in_right,
                com.pixel.intelligentsearch.R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                com.pixel.intelligentsearch.R.anim.slide_in_left,
                com.pixel.intelligentsearch.R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                com.pixel.intelligentsearch.R.anim.slide_in_right,
                com.pixel.intelligentsearch.R.anim.slide_out_left
            )
        }
        
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
        val fromSearchOverlay = intent.getBooleanExtra("FROM_SEARCH_OVERLAY", false)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val themeMode = settingsState.theme
            val darkTheme = when (themeMode) {
                "Material Dark", "Dark mode", "Dark" -> true
                "Material Light", "Light mode", "Light" -> false
                "System", "System App Theme", "System Default", "system" -> isSystemInDarkTheme()
                else -> isSystemInDarkTheme()
            }

            val appTheme = when (themeMode) {
                "Material Dark", "Material Light" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.MATERIAL
                "Dark mode", "Dark" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.DARK
                "Light mode", "Light" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.LIGHT
                "System", "System App Theme", "System Default", "system" -> com.pixel.intelligentsearch.core.ui.AppColorTheme.SYSTEM
                else -> com.pixel.intelligentsearch.core.ui.AppColorTheme.SYSTEM
            }

            IntelligentSearchTheme(darkTheme = darkTheme) {
                com.pixel.intelligentsearch.core.ui.DynamicAtmosphericBackgroundContainer(
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
                                fromSearchOverlay = fromSearchOverlay,
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
        try {
            com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(this)
        } catch (e: Throwable) {
            android.util.Log.e("SettingsActivity", "Failed to update widgets on pause", e)
        }
    }
    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                com.pixel.intelligentsearch.R.anim.slide_in_left,
                com.pixel.intelligentsearch.R.anim.slide_out_right
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                com.pixel.intelligentsearch.R.anim.slide_in_left,
                com.pixel.intelligentsearch.R.anim.slide_out_right
            )
        }
    }
}
