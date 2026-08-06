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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
            val isMaterialYou = widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"
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
        val pendingResult = goAsync()
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val doodleBitmap = DoodleFetcher.fetchCurrentDoodleBitmap()
                updateWidgetsSync(context, appWidgetManager, appWidgetIds, doodleBitmap)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidgetsSync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        doodleBitmap: android.graphics.Bitmap?
    ) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val showVoice = prefs.getBoolean("widget_show_voice", true)
        val showGemini = prefs.getBoolean("widget_show_gemini", false)
        val showGIcon = prefs.getBoolean("widget_show_g_icon", true)
        val showDoodle = prefs.getBoolean("widget_show_doodle", false) || prefs.getBoolean("force_google_minidoodle", false)
        val actionIconStr = prefs.getString("widget_action_icon", "Search") ?: "Search"
        val widgetShortcut = prefs.getString("widget_shortcut", "None") ?: "None"

        val themeMode = prefs.getString("night.mode", "System") ?: "System"
        val isDark = when (themeMode) {
            "Material Dark", "Dark mode", "Dark" -> true
            "Material Light", "Light mode", "Light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        // ————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————————
        val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")
        val isMaterialYou = widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"
        
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

        val subthemeStr = prefs.getString("widget_subtheme", "System") ?: "System"
        val customColorInt = prefs.getInt("widget_custom_color_int", android.graphics.Color.HSVToColor(floatArrayOf(
            prefs.getInt("widget_custom_hue", 277).toFloat(),
            prefs.getInt("widget_custom_saturation", 51) / 100f,
            prefs.getInt("widget_custom_lightness", 100) / 100f
        )))
        
        val actualCustomColor = customColorInt  // Outer accent rim: system_accent2 (secondary tonal, matches wallpaper teal/color)
        val rimColor = if (isMaterialYou) {
            if (subthemeStr == "Custom") {
                actualCustomColor
            } else {
                context.getColor(
                    if (isDark) android.R.color.system_accent2_700
                    else android.R.color.system_accent2_200
                )
            }
        } else {
            android.graphics.Color.TRANSPARENT
        }

        val isBarBlack = isMaterialYou && subthemeStr == "Custom" && (actualCustomColor and 0xFFFFFF) == 0x000000

        // Inner pill: Material Black, unless the bar itself is perfectly black (then White)
        val pillColor = if (isMaterialYou) {
            if (isBarBlack) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        } else {
            if (isDark) 0xFF303134.toInt() else 0xFFFFFFFF.toInt()
        }

        // Circle button: slightly lighter than pill
        val circleColor = pillColor

        // Apply widget transparency setting to the background colors (0 = solid, 100 = invisible)
        val transparency = prefs.getInt("search.background.transparency", 30)
        val alphaInt = (255 * (100 - transparency) / 100).coerceIn(0, 255)
        
        // Use opaque colors for the filter to completely overwrite the grey base
        val rimColorOpaque = android.graphics.Color.rgb(
            android.graphics.Color.red(rimColor),
            android.graphics.Color.green(rimColor),
            android.graphics.Color.blue(rimColor)
        )
        val pillColorOpaque = android.graphics.Color.rgb(
            android.graphics.Color.red(pillColor),
            android.graphics.Color.green(pillColor),
            android.graphics.Color.blue(pillColor)
        )
        val circleColorOpaque = android.graphics.Color.rgb(
            android.graphics.Color.red(circleColor),
            android.graphics.Color.green(circleColor),
            android.graphics.Color.blue(circleColor)
        )
        
        // Luminance helper for custom color
        val customColorLuminance = (0.299 * android.graphics.Color.red(actualCustomColor) + 0.587 * android.graphics.Color.green(actualCustomColor) + 0.114 * android.graphics.Color.blue(actualCustomColor)) / 255
        val customIconTint = if (customColorLuminance > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE

        // Determine Icon Tint
        val iconTint = when {
            !isMaterialYou -> {
                when (subthemeStr) {
                    "Light" -> android.graphics.Color.BLACK
                    "System" -> if (!isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    "Custom" -> customIconTint
                    else -> android.graphics.Color.WHITE
                }
            }
            else -> {
                if (subthemeStr == "Custom") {
                    customIconTint
                } else {
                    if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                }
            }
        }
        
        // Determine Material G Icon Theme
        val materialGIconTheme = prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon"
        val gIconRes = when {
            !isMaterialYou -> {
                if (materialGIconTheme == "Accented G Icon") R.drawable.ic_g_logo else R.drawable.ic_g_logo_colored
            }
            else -> {
                when (materialGIconTheme) {
                    "System G Icon" -> R.drawable.ic_g_logo_colored
                    "Material G Icon" -> R.drawable.ic_g_logo
                    "Accented G Icon" -> R.drawable.ic_g_logo
                    else -> R.drawable.ic_g_logo_colored
                }
            }
        }
        
        val actionIconRes = when (actionIconStr) {
            "Search" -> R.drawable.ic_search_ai_colored
            "Gemini" -> R.drawable.ic_gemini
            "Now Playing" -> R.drawable.ic_music
            else -> R.drawable.ic_search_ai_colored
        }
        
        for (appWidgetId in appWidgetIds) {
            val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
            val views = RemoteViews(context.packageName, layoutId)

            // Apply Material You colors with transparency using setImageAlpha
            if (isMaterialYou) {
                views.setInt(R.id.widget_outer_background, "setColorFilter", rimColorOpaque)
                views.setInt(R.id.widget_outer_background, "setImageAlpha", alphaInt)
                
                views.setInt(R.id.widget_pill_background, "setColorFilter", pillColorOpaque)
                views.setInt(R.id.widget_pill_background, "setImageAlpha", alphaInt)
                
                views.setInt(R.id.widget_sound_background, "setColorFilter", circleColorOpaque)
                views.setInt(R.id.widget_sound_background, "setImageAlpha", alphaInt)
                
                views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                if (showDoodle && doodleBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, doodleBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                }
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic)
                views.setImageViewResource(R.id.widget_lens_search, shortcutIconRes)
                views.setImageViewResource(R.id.widget_sound_icon, actionIconRes)
                
                if (materialGIconTheme == "Accented G Icon") {
                    views.setInt(R.id.widget_g_logo, "setColorFilter", iconTint)
                    views.setInt(R.id.widget_voice_search, "setColorFilter", iconTint)
                    views.setInt(R.id.widget_lens_search, "setColorFilter", iconTint)
                    views.setInt(R.id.widget_sound_icon, "setColorFilter", iconTint)
                } else {
                    views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                    // If bar is black, override standard tinting and make icons black so they are visible on the white pill
                    if (isBarBlack) {
                        views.setInt(R.id.widget_voice_search, "setColorFilter", android.graphics.Color.BLACK)
                        views.setInt(R.id.widget_lens_search, "setColorFilter", android.graphics.Color.BLACK)
                        views.setInt(R.id.widget_sound_icon, "setColorFilter", android.graphics.Color.BLACK)
                    } else {
                        views.setInt(R.id.widget_voice_search, "setColorFilter", android.graphics.Color.TRANSPARENT)
                        views.setInt(R.id.widget_lens_search, "setColorFilter", android.graphics.Color.TRANSPARENT)
                        views.setInt(R.id.widget_sound_icon, "setColorFilter", android.graphics.Color.TRANSPARENT)
                    }
                }

            } else {
                // In Colorful mode, the outer rim should be completely hidden so the native grey drawable doesn't bleed through
                // and alter the perceived brightness of the custom inner pill.
                views.setViewVisibility(R.id.widget_outer_background, View.GONE)
                
                views.setInt(R.id.widget_pill_background, "setColorFilter", pillColorOpaque)
                views.setInt(R.id.widget_pill_background, "setImageAlpha", alphaInt)
                
                views.setInt(R.id.widget_sound_background, "setColorFilter", circleColorOpaque)
                views.setInt(R.id.widget_sound_background, "setImageAlpha", alphaInt)
                
                views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
                if (showDoodle && doodleBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, doodleBitmap)
                    views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                    if (materialGIconTheme == "Accented G Icon") {
                        views.setInt(R.id.widget_g_logo, "setColorFilter", iconTint)
                    } else {
                        // Remove color filter if previously set
                        views.setInt(R.id.widget_g_logo, "setColorFilter", android.graphics.Color.TRANSPARENT)
                    }
                }
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic_original)
                views.setInt(R.id.widget_voice_search, "setColorFilter", iconTint)
                
                views.setImageViewResource(R.id.widget_lens_search, shortcutIconRes)
                views.setInt(R.id.widget_lens_search, "setColorFilter", iconTint)
            }

            // Shortcut visibility
            views.setViewVisibility(R.id.widget_voice_search, if (showVoice) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_lens_search, if (widgetShortcut != "None") View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_gemini_search, if (showGemini) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_sound_search, if (actionIconStr != "None") View.VISIBLE else View.GONE)

            // Tap pill -> main search
            val enableSearchOverlay = prefs.getBoolean("search_overlay_enabled", true)
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

            // Tap sparkle circle inside pill -> Gemini
            val geminiPI = PendingIntent.getActivity(context, appWidgetId + 2500,
                getGeminiSearchIntent(context),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_gemini_search, geminiPI)
            
            // Set up custom action icon (Circle Button)
            if (actionIconStr == "None") {
                views.setViewVisibility(R.id.widget_sound_search, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_sound_search, View.VISIBLE)
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
            }

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



