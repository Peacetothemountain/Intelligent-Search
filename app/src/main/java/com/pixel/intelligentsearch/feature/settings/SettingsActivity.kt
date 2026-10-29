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
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    var isExitingToHome: Boolean = false

    @javax.inject.Inject
    lateinit var widgetUpdater: com.pixel.intelligentsearch.core.system.WidgetUpdater

    // The settings the widgets were last drawn with, so leaving Settings redraws them only after a change. Null
    // when opened to configure a widget, so it is drawn on the way out whatever happened.
    private var lastPushedFingerprint: Int? = null

    private fun settingsFingerprint(): Int = runCatching {
        SettingsManager.storedPrefs(this).all.hashCode()
    }.getOrDefault(0)

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
        
        val isWidgetConfigure = intent.action == android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
        val appWidgetId = configuredWidgetId(this, intent)
        // A countdown widget is set up on its own screen, which answers the launcher in this activity's place.
        if (isWidgetConfigure && com.pixel.intelligentsearch.feature.widget.CountdownConfigActivity.isCountdownWidget(this, appWidgetId)) {
            startActivity(com.pixel.intelligentsearch.feature.widget.CountdownConfigActivity.setupIntent(this, appWidgetId))
            finish()
            return
        }
        if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            val resultValue = android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
        }
        if (!isWidgetConfigure) lastPushedFingerprint = settingsFingerprint()

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
                            // The hub leaves through this activity itself; this is only its fallback.
                            onBackToLauncher = { finish() },
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
            val fingerprint = settingsFingerprint()
            if (fingerprint != lastPushedFingerprint) {
                widgetUpdater.updateAll()
                lastPushedFingerprint = fingerprint
            }
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

    companion object {
        /**
         * The widget [intent] asks to set up, or [android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID]. This
         * activity is exported, so a widget id counts only on the launcher's configure action: any app could otherwise
         * send one to reach a widget's setup and have this activity answer it as configured. The id must also be one of
         * this app's bound widgets, as the launcher's always is, so the action alone can't name another app's widget or
         * an unbound id.
         */
        internal fun configuredWidgetId(context: Context, intent: Intent?): Int {
            val invalid = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            if (intent?.action != android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) return invalid
            val id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, invalid)
            if (id == invalid) return invalid
            val provider = runCatching {
                android.appwidget.AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
            }.getOrNull()
            return if (provider?.packageName == context.packageName) id else invalid
        }
    }
}
