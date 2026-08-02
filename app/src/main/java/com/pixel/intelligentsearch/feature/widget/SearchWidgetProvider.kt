package com.pixel.intelligentsearch.feature.widget
import com.pixel.intelligentsearch.App
import com.pixel.intelligentsearch.R
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.pixel.intelligentsearch.ACTION_HIDE_WIDGET" || intent.action == "com.pixel.intelligentsearch.ACTION_SHOW_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, SearchWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val isHidden = intent.action == "com.pixel.intelligentsearch.ACTION_HIDE_WIDGET"
            val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
            val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")
            val isMaterialYou = when (widgetThemeStyle) {
                "Material You (Minimal)" -> true
                "Google App (Default)" -> false
                else -> {
                    try {
                        val packTheme = android.provider.Settings.Secure.getInt(context.contentResolver, "pack_theme_feature_enabled", -1)
                        if (packTheme == 0) false else true
                    } catch (e: Exception) {
                        true
                    }
                }
            }
            val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, layoutId)
                val visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
                views.setViewVisibility(R.id.widget_outer_background, visibility)
                views.setViewVisibility(R.id.widget_pill_container, visibility)
                views.setViewVisibility(R.id.widget_sound_search, visibility)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val showVoice = prefs.getBoolean("widget_show_voice", true)
        val showGemini = prefs.getBoolean("widget_show_gemini", false)
        val actionIconStr = prefs.getString("widget_action_icon", "Search") ?: "Search"
        val widgetShortcut = prefs.getString("widget_shortcut", "None") ?: "None"

        val themeMode = prefs.getString("night.mode", "System") ?: "System"
        val isDark = when (themeMode) {
            "Material Dark", "Dark mode", "Dark" -> true
            "Material Light", "Light mode", "Light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        // â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”â€”
        val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")
        val isMaterialYou = when (widgetThemeStyle) {
            "Material You (Minimal)" -> true
            "Google App (Default)" -> false
            else -> {
                try {
                    val packTheme = android.provider.Settings.Secure.getInt(context.contentResolver, "pack_theme_feature_enabled", -1)
                    if (packTheme == 0) false else true
                } catch (e: Exception) {
                    true
                }
            }
        }
        
        val shortcutIconRes = when (widgetShortcut) {
            "Live" -> R.drawable.ic_gemini
            "Translate (text)" -> R.drawable.ic_translate
            "Translate (camera)" -> R.drawable.ic_camera
            "Weather" -> R.drawable.ic_weather
            "Sports" -> R.drawable.ic_sports
            "Dictionary" -> R.drawable.ic_dictionary
            "Homework" -> R.drawable.ic_homework
            "Finance" -> R.drawable.ic_finance
            "Saved" -> R.drawable.ic_saved
            "News" -> R.drawable.ic_news
            else -> R.drawable.ic_camera
        }

        // Outer accent rim: system_accent2 (secondary tonal, matches wallpaper teal/color)
        val rimColor = if (isMaterialYou) {
            context.getColor(
                if (isDark) android.R.color.system_accent2_700
                else android.R.color.system_accent2_200
            )
        } else {
            android.graphics.Color.TRANSPARENT
        }

        // Inner pill: deepest neutral surface (near-black on dark, near-white on light)
        val pillColor = if (isMaterialYou) {
            context.getColor(
                // Use a much darker neutral color instead of the bright accent color
                if (isDark) android.R.color.system_neutral1_900
                else android.R.color.system_neutral1_100
            )
        } else {
            if (isDark) 0xFF303134.toInt() else 0xFFFFFFFF.toInt()
        }

        // Circle button: slightly lighter than pill
        val circleColor = pillColor

        // Apply widget transparency setting to the background colors (0 = solid, 100 = invisible)
        val transparency = prefs.getInt("search.background.transparency", 30)
        val alphaInt = (255 * (100 - transparency) / 100).coerceIn(0, 255)
        
        val rimColorAlpha = android.graphics.Color.argb(
            alphaInt,
            android.graphics.Color.red(rimColor),
            android.graphics.Color.green(rimColor),
            android.graphics.Color.blue(rimColor)
        )
        val pillColorAlpha = android.graphics.Color.argb(
            alphaInt,
            android.graphics.Color.red(pillColor),
            android.graphics.Color.green(pillColor),
            android.graphics.Color.blue(pillColor)
        )
        val circleColorAlpha = android.graphics.Color.argb(
            alphaInt,
            android.graphics.Color.red(circleColor),
            android.graphics.Color.green(circleColor),
            android.graphics.Color.blue(circleColor)
        )

        for (appWidgetId in appWidgetIds) {
            val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
            val views = RemoteViews(context.packageName, layoutId)

            // Make circle button visible by default

            // Apply Material You colors with transparency
            if (isMaterialYou) {
                views.setInt(R.id.widget_outer_background, "setColorFilter", rimColorAlpha)
                views.setInt(R.id.widget_pill_background, "setColorFilter", pillColorAlpha)
                views.setInt(R.id.widget_sound_background, "setColorFilter", circleColorAlpha)
                
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic)
                views.setImageViewResource(R.id.widget_lens_search, shortcutIconRes)

            } else {
                // In Colorful mode, do not tint the outer rim transparent drawable,
                // but tint the pill backgrounds white/dark grey.
                views.setInt(R.id.widget_outer_background, "setColorFilter", android.graphics.Color.TRANSPARENT)
                views.setInt(R.id.widget_pill_background, "setColorFilter", pillColorAlpha)
                views.setInt(R.id.widget_sound_background, "setColorFilter", circleColorAlpha)
                
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo_colored)
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic_original)
                // Use default camera image or a colored lens equivalent
                val coloredShortcutRes = when (widgetShortcut) {
                    "Sports" -> R.drawable.ic_sports_colored
                    else -> shortcutIconRes
                }
                views.setImageViewResource(R.id.widget_lens_search, coloredShortcutRes)
            }

            // Shortcut visibility
            views.setViewVisibility(R.id.widget_voice_search, if (showVoice) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_lens_search, if (widgetShortcut != "None") View.VISIBLE else View.GONE)

            // Tap pill -> main search
            val enableSearchOverlay = prefs.getBoolean("search_overlay_enabled", true) && isMaterialYou
            val mainIntent = if (enableSearchOverlay) {
                Intent(context, WidgetActivity::class.java).apply {
                    putExtra("SHOW_GEMINI_OVERLAY", false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent("android.search.action.GLOBAL_SEARCH").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }.takeIf { context.packageManager.resolveActivity(it, 0) != null }
                ?: Intent(Intent.ACTION_WEB_SEARCH).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            
            val requestCode = appWidgetId + (if (enableSearchOverlay) 0 else 50000)
            
            val mainPI = PendingIntent.getActivity(context, requestCode, mainIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_pill_container, mainPI)
            views.setOnClickPendingIntent(R.id.widget_g_logo, mainPI)

            // Tap mic -> Voice Search
            val voicePI = PendingIntent.getActivity(context, appWidgetId + 1000,
                getVoiceSearchIntent(context),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_voice_search, voicePI)

            // Tap Shortcut
            val shortcutPI = PendingIntent.getActivity(context, appWidgetId + 2000,
                getShortcutIntent(context, widgetShortcut),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_lens_search, shortcutPI)

            // Gemini intent removed as view is no longer in layout
            
            // Set up custom action icon (Circle Button)
            val actionIntent = when (actionIconStr) {
                "Gemini" -> getGeminiSearchIntent(context)
                "Now Playing" -> getNowPlayingIntent(context)
                else -> getGeminiSearchIntent(context) // Search acts as Gemini
            }
            val actionPI = PendingIntent.getActivity(context, appWidgetId + 3000,
                actionIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_sound_search, actionPI)
            
            // Set the icon
            val actionIconRes = when (actionIconStr) {
                "Gemini" -> R.drawable.ic_gemini
                "Now Playing" -> R.drawable.ic_music
                else -> R.drawable.ic_search_ai_colored // Search
            }
            views.setImageViewResource(R.id.widget_sound_icon, actionIconRes)
            
            // --- Doodle Logic ---
            val doodleIntent = Intent(context, com.pixel.intelligentsearch.feature.widget.doodle.DoodleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_doodle_flipper, doodleIntent)
            
            // Toggle visibility to show doodle instead of the static G logo
            views.setViewVisibility(R.id.widget_doodle_flipper, View.VISIBLE)
            views.setViewVisibility(R.id.widget_g_logo, View.GONE)
            // --------------------

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun getVoiceSearchIntent(context: Context): Intent {
            return Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox.VoiceSearchActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun getLensSearchIntent(context: Context): Intent {
            return Intent(context, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.LAUNCH_LENS"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        fun getLensTranslateIntent(context: Context): Intent {
            return Intent(context, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        // =================================================================================================
        // WARNING: DO NOT MODIFY THIS FUNCTION OR THE INTENT IT RETURNS UNDER ANY CIRCUMSTANCES.
        // The user has explicitly mandated that this logic must NEVER be changed.
        // It must use Intent.ACTION_VOICE_COMMAND to correctly trigger the system's native Gemini overlay.
        // DO NOT change this to ACTION_ASSIST or launch any custom Activities. 
        // DO NOT TOUCH!
        // =================================================================================================
        fun getGeminiSearchIntent(context: Context): Intent {
            return Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        
        fun getShortcutIntent(context: Context, shortcut: String): Intent {
            return when (shortcut) {
                "Live" -> getGeminiSearchIntent(context)
                "Translate (text)" -> context.packageManager.getLaunchIntentForPackage("com.google.android.apps.translate") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "Translate (camera)" -> getLensTranslateIntent(context)
                "Weather" -> getCustomIntentOrDefault(context, "Weather") {
                    val intentWeatherApp = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.weather")
                    if (intentWeatherApp != null) {
                        intentWeatherApp.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else {
                        val intentSearchWeather = Intent(Intent.ACTION_VIEW).apply {
                            setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.weather.WeatherExportedActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (context.packageManager.resolveActivity(intentSearchWeather, 0) != null) {
                            intentSearchWeather
                        } else {
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=weather")).apply { setPackage("com.google.android.googlequicksearchbox"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        }
                    }
                }
                "Sports" -> getCustomIntentOrDefault(context, "Sports") {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra("query", "Sports")
                        setPackage("com.google.android.googlequicksearchbox")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        intent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=Sports")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    }
                }
                "Dictionary" -> getCustomIntentOrDefault(context, "Dictionary") {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.books")
                    if (intent != null) {
                        intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.books")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    }
                }
                "Homework" -> {
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage("com.google.android.apps.labs.language.tailwind")
                    intent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.labs.language.tailwind")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                }
                "Finance" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/finance")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "Saved" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/save")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "News" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                else -> getLensSearchIntent(context)
            }
        }

        fun getCustomIntentOrDefault(context: Context, shortcut: String, defaultIntent: () -> Intent): Intent {
            val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
            val customType = prefs.getString("${shortcut}_custom_type", "default")
            val customValue = prefs.getString("${shortcut}_custom_value", "")
            
            if (customType == "url" && !customValue.isNullOrEmpty()) {
                val urlString = if (!customValue.startsWith("http://") && !customValue.startsWith("https://")) "https://$customValue" else customValue
                return Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else if (customType == "app" && !customValue.isNullOrEmpty()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(customValue)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return launchIntent
                }
            }
            
            return defaultIntent()
        }

        fun getNowPlayingIntent(context: Context): Intent {
            return context.packageManager.getLaunchIntentForPackage("com.google.android.apps.pixel.nowplaying")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.pixel.nowplaying")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    }
}

