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
        updateWidgetsSync(context, appWidgetManager, appWidgetIds)
    }

    private fun updateWidgetsSync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("PREFERENCES_CUSTOMISATIONS", Context.MODE_PRIVATE)
        val showVoice = prefs.getBoolean("widget_show_voice", true)
        val showGemini = prefs.getBoolean("widget_show_gemini", false)
        val showGIcon = prefs.getBoolean("widget_show_g_icon", true)
        val actionIconStr = prefs.getString("widget_action_icon", "Search") ?: "Search"
        val shortcut1Str = prefs.getString("widget_shortcut_1", prefs.getString("widget_shortcut", "Google Lens")) ?: "Google Lens"
        val shortcut2Str = prefs.getString("widget_shortcut_2", "None") ?: "None"
        val shortcut3Str = prefs.getString("widget_shortcut_3", "None") ?: "None"

        val themeMode = prefs.getString("night.mode", "System") ?: "System"
        val isDark = when (themeMode) {
            "Material Dark", "Dark mode", "Dark" -> true
            "Material Light", "Light mode", "Light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")
        val isMaterialYou = widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"

        val subthemeStr = prefs.getString("widget_subtheme", "System") ?: "System"
        val customColorInt = prefs.getInt("widget_custom_color_int", android.graphics.Color.HSVToColor(floatArrayOf(
            prefs.getInt("widget_custom_hue", 277).toFloat(),
            prefs.getInt("widget_custom_saturation", 51) / 100f,
            prefs.getInt("widget_custom_lightness", 100) / 100f
        )))
        
        val actualCustomColor = customColorInt  // Outer accent rim: system_accent1 (primary tonal)
        val rimColor = if (isMaterialYou) {
            if (subthemeStr == "Custom") {
                actualCustomColor
            } else {
                context.getColor(
                    if (isDark) android.R.color.system_accent1_800
                    else android.R.color.system_accent1_200
                )
            }
        } else {
            android.graphics.Color.TRANSPARENT
        }

        val lockBlack = prefs.getBoolean("widget_material_lock_black", true)

        // Inner pill: Material Black for Material You, System Design uses specific colors based on subtheme
        val pillColor = if (isMaterialYou) {
            if (lockBlack) 0xFF121212.toInt() else actualCustomColor
        } else {
            when (subthemeStr) {
                "Light" -> 0xFFF8F9FA.toInt()
                "Dark" -> 0xFF303134.toInt()
                "Custom" -> actualCustomColor
                else -> if (isDark) 0xFF303134.toInt() else 0xFFF8F9FA.toInt()
            }
        }

        // Circle button: slightly lighter than pill
        val circleColor = pillColor

        // Apply widget transparency setting to the background colors (0 = solid, 100 = invisible)
        val transparency = prefs.getInt("widget.background.transparency", 28)
        val alphaInt = if (subthemeStr == "Custom") { (255 * (100 - transparency) / 100).coerceIn(0, 255) } else { 255 }
        
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
                android.graphics.Color.WHITE
            }
        }
        
        // Determine Material G Icon Theme
        val materialGIconTheme = prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon"
        val accentIconTint = if (materialGIconTheme == "Accented G Icon") actualCustomColor else iconTint

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
                views.setColorStateList(R.id.widget_outer_background, "setImageTintList", android.content.res.ColorStateList.valueOf(rimColorOpaque))
                views.setInt(R.id.widget_outer_background, "setImageAlpha", alphaInt)
                
                views.setColorStateList(R.id.widget_pill_background, "setImageTintList", android.content.res.ColorStateList.valueOf(pillColorOpaque))
                views.setInt(R.id.widget_pill_background, "setImageAlpha", if (lockBlack) 255 else alphaInt)
                
                views.setColorStateList(R.id.widget_sound_background, "setImageTintList", android.content.res.ColorStateList.valueOf(circleColorOpaque))
                views.setInt(R.id.widget_sound_background, "setImageAlpha", if (lockBlack) 255 else alphaInt)
                
                bindGIcon(views, showGIcon, gIconRes, accentIconTint, materialGIconTheme)
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic)
                if (materialGIconTheme == "Accented G Icon") {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
                    views.setColorStateList(R.id.widget_voice_search, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
                    views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
                } else {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
                    views.setColorStateList(R.id.widget_voice_search, "setImageTintList", null)
                    views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", null)
                }

            } else {
                // In Colorful mode, outer rim is hidden
                views.setViewVisibility(R.id.widget_outer_background, View.GONE)
                
                views.setColorStateList(R.id.widget_pill_background, "setImageTintList", android.content.res.ColorStateList.valueOf(pillColorOpaque))
                views.setInt(R.id.widget_pill_background, "setImageAlpha", alphaInt)
                
                views.setColorStateList(R.id.widget_sound_background, "setImageTintList", android.content.res.ColorStateList.valueOf(circleColorOpaque))
                views.setInt(R.id.widget_sound_background, "setImageAlpha", alphaInt)
                
                bindGIcon(views, showGIcon, gIconRes, accentIconTint, materialGIconTheme)
                views.setImageViewResource(R.id.widget_voice_search, R.drawable.ic_mic_original)
                views.setColorStateList(R.id.widget_voice_search, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
            }

            // Bind 4 ordered shortcut and microphone slots
            val slotOrderStr = prefs.getString("widget_shortcut_order", "shortcut1,mic,shortcut2,shortcut3") ?: "shortcut1,mic,shortcut2,shortcut3"
            val slotOrder = slotOrderStr.split(",").filter { it.isNotBlank() }

            val viewIdTargets = listOf(
                R.id.widget_voice_search,
                R.id.widget_lens_search,
                R.id.widget_shortcut_2,
                R.id.widget_shortcut_3
            )

            val activeItems = mutableListOf<Triple<String, Int, Intent>>()
            for (key in slotOrder) {
                when (key) {
                    "mic" -> {
                        if (showVoice) {
                            val micIcon = if (isMaterialYou) R.drawable.ic_mic else R.drawable.ic_mic_original
                            activeItems.add(Triple("mic", micIcon, getVoiceSearchIntent(context)))
                        }
                    }
                    "shortcut1" -> {
                        if (shortcut1Str != "None") {
                            activeItems.add(Triple("shortcut1", getShortcutIconRes(shortcut1Str, isMaterialYou), getShortcutIntent(context, shortcut1Str)))
                        }
                    }
                    "shortcut2" -> {
                        if (shortcut2Str != "None") {
                            activeItems.add(Triple("shortcut2", getShortcutIconRes(shortcut2Str, isMaterialYou), getShortcutIntent(context, shortcut2Str)))
                        }
                    }
                    "shortcut3" -> {
                        if (shortcut3Str != "None") {
                            activeItems.add(Triple("shortcut3", getShortcutIconRes(shortcut3Str, isMaterialYou), getShortcutIntent(context, shortcut3Str)))
                        }
                    }
                }
            }

            for (i in 0 until 4) {
                val targetViewId = viewIdTargets[i]
                if (i < activeItems.size) {
                    val item = activeItems[i]
                    views.setViewVisibility(targetViewId, View.VISIBLE)
                    views.setImageViewResource(targetViewId, item.second)

                    if (isMaterialYou) {
                        if (materialGIconTheme == "Accented G Icon") {
                            views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
                        } else {
                            views.setColorStateList(targetViewId, "setImageTintList", null)
                        }
                    } else {
                        views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(accentIconTint))
                    }

                    val pi = PendingIntent.getActivity(
                        context, appWidgetId + 2000 + i,
                        item.third,
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(targetViewId, pi)
                } else {
                    views.setViewVisibility(targetViewId, View.GONE)
                }
            }

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

            // Tap sparkle circle inside pill -> Gemini
            val geminiPI = PendingIntent.getActivity(context, appWidgetId + 2500,
                getGeminiSearchIntent(context),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_gemini_search, geminiPI)
            
            // Set up custom action icon (Circle Button) - Material Design ONLY
            if (!isMaterialYou || actionIconStr == "None") {
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

        fun getShortcutIconRes(shortcut: String, isMaterialYou: Boolean = true): Int {
            return when (shortcut) {
                "Voice Search" -> if (isMaterialYou) R.drawable.ic_mic else R.drawable.ic_mic_original
                "Google Lens" -> R.drawable.ic_camera
                "Live" -> R.drawable.ic_gemini
                "Translate (text)" -> R.drawable.ic_translate
                "Translate (camera)" -> R.drawable.ic_document_scanner
                "Weather" -> R.drawable.ic_weather
                "Sports" -> R.drawable.ic_sports
                "Dictionary" -> R.drawable.ic_dictionary
                "Homework" -> R.drawable.ic_homework
                "Finance" -> R.drawable.ic_finance
                "Saved" -> R.drawable.ic_saved
                "News" -> R.drawable.ic_news
                else -> R.drawable.ic_camera
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

        // Triggers the system's native voice command overlay via Intent.ACTION_VOICE_COMMAND
        fun getGeminiSearchIntent(context: Context): Intent {
            return Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        
        fun getShortcutIntent(context: Context, shortcut: String): Intent {
            return when (shortcut) {
                "Voice Search" -> getVoiceSearchIntent(context)
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

          private fun bindGIcon(
            views: RemoteViews, 
            showGIcon: Boolean, 
            gIconRes: Int, 
            iconTint: Int,
            materialGIconTheme: String
        ) {
            views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
            views.setImageViewResource(R.id.widget_g_logo, gIconRes)
            if (materialGIconTheme == "Accented G Icon" || materialGIconTheme == "Colorful") {
                if (materialGIconTheme == "Accented G Icon") {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(iconTint))
                }
            } else {
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            }
        }

        fun getNowPlayingIntent(context: Context): Intent {
            return context.packageManager.getLaunchIntentForPackage("com.google.android.apps.pixel.nowplaying")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.pixel.nowplaying")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    }
}



