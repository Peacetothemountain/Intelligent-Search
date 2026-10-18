package com.pixel.intelligentsearch.feature.settings
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.Context
import android.content.Intent
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
import com.pixel.intelligentsearch.core.navigation.Route
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

    var isExitingToHome: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        // No session-wide high refresh rate here: pinning it kept the panel at its maximum rate for the whole
        // settings session and defeated LTPO. The system raises the rate for scrolling and animations, and
        // the predictive-back gesture boosts it only while it runs.
        super.onCreate(savedInstanceState)

        
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
        val isWidgetConfigure = intent.action == android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE ||
                appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
        }

        // This activity is exported (launcher, widget setup), so page and overlay extras are honoured only when
        // they carry this app's own launch token. Other apps always land on the main settings page.
        val isInternalLaunch = com.pixel.intelligentsearch.core.security.InternalLaunchToken.isInternal(intent)
        val fromSearchOverlay = isInternalLaunch && intent.getBooleanExtra("FROM_SEARCH_OVERLAY", false)
        // The debug page opens only from the overlay's debug code, which carries the launch token like every page.
        val sanitizedScreenKey = if (isInternalLaunch) intent.getStringExtra("extra_screen") else null

        // A new user opening the app from the launcher starts on the search screen, where the tutorial begins; its
        // last steps bring them back here, and the Finish Setting Up card greets them once it ends.
        val prefsForTutorial = getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        val fromLauncher = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        // With the search overlay switched off the search screen only forwards to Google, so settings must open.
        if (fromLauncher && !prefsForTutorial[SettingsManager.TUTORIAL_COMPLETED] && prefsForTutorial[SettingsManager.TUTORIAL_STEP] < 3 &&
            prefsForTutorial[SettingsManager.SEARCH_OVERLAY_ENABLED]
        ) {
            startActivity(
                Intent(this, com.pixel.intelligentsearch.feature.search.SearchActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
            return
        }
        val initialRoute = if (isWidgetConfigure && sanitizedScreenKey == null) {
            Route.WidgetCustomization
        } else {
            Route.fromKey(sanitizedScreenKey)
        }

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()
            val darkTheme = ThemeMode.from(settingsState.theme).isDark(isSystemInDarkTheme())

            IntelligentSearchTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.pixel.intelligentsearch.feature.settings.LocalSettingsViewModel provides settingsViewModel,
                        com.pixel.intelligentsearch.feature.settings.LocalSettingsState provides settingsState
                    ) {
                        SettingsScreensHub(
                            initialRoute = initialRoute,
                            fromSearchOverlay = fromSearchOverlay,
                            prefs = prefs,
                            onBackToLauncher = {
                                val isBackToOverlay = prefs[SettingsManager.BACK_TO_SEARCH_OVERLAY]
                                if (isBackToOverlay) {
                                    if (isTaskRoot || !fromSearchOverlay) {
                                        val searchIntent = Intent(this@SettingsActivity, com.pixel.intelligentsearch.feature.search.SearchActivity::class.java).apply {
                                            putExtra("FROM_BACK_SWIPE", true)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        startActivity(searchIntent)
                                    }
                                    finish()
                                } else {
                                    isExitingToHome = true
                                    sendBroadcast(Intent("com.pixel.intelligentsearch.DISMISS_OVERLAY").setPackage(packageName))
                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(homeIntent)
                                    finishAffinity()
                                }
                            },
                            context = this@SettingsActivity
                        )
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
        if (isExitingToHome) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            return
        }
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
