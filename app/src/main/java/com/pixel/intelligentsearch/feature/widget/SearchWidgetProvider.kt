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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
        val asyncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
        asyncScope.launch {
            try {
                updateWidgetsSync(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
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
        val rawShortcut1 = prefs.getString("widget_shortcut_1", prefs.getString("widget_shortcut", "Google Lens")) ?: "Google Lens"
        val rawShortcut2 = prefs.getString("widget_shortcut_2", "None") ?: "None"
        val rawShortcut3 = prefs.getString("widget_shortcut_3", "None") ?: "None"
        val shortcut1Str = if (rawShortcut1 == "Voice Search") "None" else rawShortcut1
        val shortcut2Str = if (rawShortcut2 == "Voice Search") "None" else rawShortcut2
        val shortcut3Str = if (rawShortcut3 == "Voice Search") "None" else rawShortcut3

        val themeMode = prefs.getString("night.mode", "System") ?: "System"
        val isDark = when (themeMode) {
            "Material Dark", "Dark mode", "Dark" -> true
            "Material Light", "Light mode", "Light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")
        val isMaterialYou = widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"

        val subthemeStr = prefs.getString("widget_subtheme", "System") ?: "System"
        val customHue = prefs.getInt("widget_custom_hue", 277).toFloat()
        val customSat = prefs.getInt("widget_custom_saturation", 51) / 100f
        val customLightness = prefs.getInt("widget_custom_lightness", 100) / 100f
        val customColorOpacity = prefs.getInt("widget_custom_color_opacity", 100) / 100f
        val colorAlphaInt = (customColorOpacity * 255).toInt().coerceIn(0, 255)
        val actualCustomColor = android.graphics.Color.HSVToColor(
            colorAlphaInt,
            floatArrayOf(customHue, customSat, customLightness)
        )
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

        val pillColor = if (isMaterialYou) {
            if (lockBlack) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getColor(android.R.color.system_neutral1_900) else 0xFF121212.toInt()
            } else actualCustomColor
        } else {
            when (subthemeStr) {
                "Light" -> 0xFFF1F3F4.toInt()
                "Dark" -> 0xFF303134.toInt()
                "Custom" -> actualCustomColor
                else -> if (isDark) 0xFF303134.toInt() else 0xFFF1F3F4.toInt()
            }
        }

        // Circle button: slightly lighter than pill
        val circleColor = pillColor

        // Apply widget transparency & color opacity settings to background layers
        val transparency = prefs.getInt("widget.background.transparency", 28)
        val containerAlpha = ((100 - transparency) / 100f).coerceIn(0f, 1f)
        val containerAlphaInt = (containerAlpha * 255).toInt().coerceIn(0, 255)

        val colorAlpha = customColorOpacity.coerceIn(0f, 1f)
        val effectiveColorAlphaInt = (colorAlpha * containerAlpha * 255).toInt().coerceIn(0, 255)

        val rimAlphaInt = if (isMaterialYou) effectiveColorAlphaInt else 0
        val pillAlphaInt = if (isMaterialYou) {
            if (lockBlack) containerAlphaInt else effectiveColorAlphaInt
        } else {
            if (subthemeStr == "Custom") effectiveColorAlphaInt else containerAlphaInt
        }
        val circleAlphaInt = pillAlphaInt
        
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
        val isPillLight = if (!isMaterialYou) {
            subthemeStr == "Light" || (subthemeStr == "System" && !isDark) || (subthemeStr == "Custom" && customColorLuminance > 0.5)
        } else {
            !lockBlack && (customColorLuminance > 0.5)
        }

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
        val effectiveIconTheme = if (subthemeStr == "Custom") {
            materialGIconTheme
        } else if (!isMaterialYou) {
            "System G Icon"
        } else {
            "Material G Icon"
        }
        val accentIconTint = if (effectiveIconTheme == "Accented G Icon") actualCustomColor else iconTint

        val gIconRes = when (effectiveIconTheme) {
            "System G Icon" -> R.drawable.ic_g_logo_colored
            "Material G Icon" -> R.drawable.ic_g_logo
            "Accented G Icon" -> R.drawable.ic_g_logo
            else -> if (isMaterialYou) R.drawable.ic_g_logo else R.drawable.ic_g_logo_colored
        }
        
        val actionIconRes = when (actionIconStr) {
            "Search" -> R.drawable.ic_search_lens_expressive
            "Assistant", "Voice", "Gemini" -> R.drawable.ic_lens_action
            "Now Playing" -> R.drawable.ic_music
            else -> R.drawable.ic_search_lens_expressive
        }
        
        for (appWidgetId in appWidgetIds) {
            val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
            val views = RemoteViews(context.packageName, layoutId)

            // Apply Material You colors with transparency using setImageAlpha
            if (isMaterialYou) {
                views.setColorStateList(R.id.widget_outer_background, "setImageTintList", android.content.res.ColorStateList.valueOf(rimColorOpaque))
                views.setInt(R.id.widget_outer_background, "setImageAlpha", rimAlphaInt)
                
                views.setColorStateList(R.id.widget_pill_background, "setImageTintList", android.content.res.ColorStateList.valueOf(pillColorOpaque))
                views.setInt(R.id.widget_pill_background, "setImageAlpha", pillAlphaInt)
                
                views.setColorStateList(R.id.widget_sound_background, "setImageTintList", android.content.res.ColorStateList.valueOf(circleColorOpaque))
                views.setInt(R.id.widget_sound_background, "setImageAlpha", circleAlphaInt)
                
                bindGIcon(views, showGIcon, gIconRes, actualCustomColor, effectiveIconTheme, subthemeStr, isMaterialYou, context, isPillLight)

            } else {
                // In Colorful mode, outer rim is hidden
                views.setViewVisibility(R.id.widget_outer_background, View.GONE)
                
                views.setColorStateList(R.id.widget_pill_background, "setImageTintList", android.content.res.ColorStateList.valueOf(pillColorOpaque))
                views.setInt(R.id.widget_pill_background, "setImageAlpha", pillAlphaInt)
                
                views.setColorStateList(R.id.widget_sound_background, "setImageTintList", android.content.res.ColorStateList.valueOf(circleColorOpaque))
                views.setInt(R.id.widget_sound_background, "setImageAlpha", circleAlphaInt)
                
                bindGIcon(views, showGIcon, gIconRes, actualCustomColor, effectiveIconTheme, subthemeStr, isMaterialYou, context, isPillLight)
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

            val useMaterialYouIcons = isMaterialYou || effectiveIconTheme == "Material G Icon" || isPillLight
            val activeItems = mutableListOf<Triple<String, Int, Intent>>()
            for (key in slotOrder) {
                when (key) {
                    "mic" -> {
                        if (showVoice) {
                            val micIcon = if (useMaterialYouIcons) R.drawable.ic_mic else R.drawable.ic_mic_original
                            activeItems.add(Triple("mic", micIcon, getVoiceSearchIntent(context)))
                        }
                    }
                    "shortcut1" -> {
                        if (shortcut1Str != "None") {
                            activeItems.add(Triple("shortcut1", getShortcutIconRes(shortcut1Str, useMaterialYouIcons), getShortcutIntent(context, shortcut1Str)))
                        }
                    }
                    "shortcut2" -> {
                        if (shortcut2Str != "None") {
                            activeItems.add(Triple("shortcut2", getShortcutIconRes(shortcut2Str, useMaterialYouIcons), getShortcutIntent(context, shortcut2Str)))
                        }
                    }
                    "shortcut3" -> {
                        if (shortcut3Str != "None") {
                            activeItems.add(Triple("shortcut3", getShortcutIconRes(shortcut3Str, useMaterialYouIcons), getShortcutIntent(context, shortcut3Str)))
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

                    when (effectiveIconTheme) {
                        "Accented G Icon" -> {
                            views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(actualCustomColor))
                        }
                        "Material G Icon" -> {
                            views.setColorStateList(targetViewId, "setImageTintList", null)
                        }
                        else -> {
                            // System G Icon
                            if (isPillLight) {
                                val materialDarkTint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.getColor(android.R.color.system_accent1_700)
                                } else {
                                    android.graphics.Color.parseColor("#1F1F1F")
                                }
                                views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(materialDarkTint))
                            } else {
                                views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE))
                            }
                        }
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

            // Tap action button inside search pill
            val actionPI = PendingIntent.getActivity(context, appWidgetId + 2500,
                getVoiceActionIntent(context),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_action_search, actionPI)
            
            // Set up custom action icon (Circle Button) - Material Design ONLY
            if (!isMaterialYou || actionIconStr == "None") {
                views.setViewVisibility(R.id.widget_sound_search, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_sound_search, View.VISIBLE)
                val circleActionIntent = when (actionIconStr) {
                    "Assistant", "Voice", "Gemini" -> getVoiceActionIntent(context)
                    "Now Playing" -> getNowPlayingIntent(context)
                    else -> getVoiceActionIntent(context)
                }
                val circleActionPI = PendingIntent.getActivity(context, appWidgetId + 3000,
                    circleActionIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_sound_search, circleActionPI)
                
                // Set the icon
                val circleActionIconRes = when (actionIconStr) {
                    "Assistant", "Voice", "Gemini" -> R.drawable.ic_lens_action
                    "Now Playing" -> R.drawable.ic_music
                    else -> R.drawable.ic_search_lens_expressive // Search
                }
                views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                when (effectiveIconTheme) {
                    "Accented G Icon" -> {
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", android.content.res.ColorStateList.valueOf(actualCustomColor))
                    }
                    "Material G Icon" -> {
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", null)
                    }
                    else -> {
                        val sysActionTint = if (isPillLight) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.getColor(android.R.color.system_accent1_700)
                            } else {
                                android.graphics.Color.parseColor("#1F1F1F")
                            }
                        } else {
                            android.graphics.Color.WHITE
                        }
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", android.content.res.ColorStateList.valueOf(sysActionTint))
                    }
                }
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
                "Assistant", "Live" -> R.drawable.ic_lens_action
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
            try {
                val lensStandalone = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (lensStandalone != null) return lensStandalone

                val lensIntent = Intent().apply {
                    setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(lensIntent, 0) != null) {
                    return lensIntent
                }
            } catch (e: Exception) {}
            return Intent(context, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.LAUNCH_LENS"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        fun getLensTranslateIntent(context: Context): Intent {
            try {
                val lensStandalone = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")?.apply {
                    putExtra("lens_mode", "translate")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (lensStandalone != null) return lensStandalone

                val translateIntent = Intent().apply {
                    setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                    putExtra("lens_mode", "translate")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(translateIntent, 0) != null) {
                    return translateIntent
                }
            } catch (e: Exception) {}
            return Intent(context, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        // Triggers the system's native voice command overlay via Intent.ACTION_VOICE_COMMAND
        fun getVoiceActionIntent(context: Context): Intent {
            return Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        
        // Backward compatibility alias
        fun getGeminiSearchIntent(context: Context): Intent = getVoiceActionIntent(context)
        
        fun getShortcutIntent(context: Context, shortcut: String): Intent {
            return when (shortcut) {
                "Voice Search" -> getVoiceSearchIntent(context)
                "Assistant", "Live" -> getVoiceActionIntent(context)
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
            materialGIconTheme: String,
            subthemeStr: String,
            isMaterialYou: Boolean,
            context: Context,
            isPillLight: Boolean
        ) {
            views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
            if (materialGIconTheme == "Material G Icon" && subthemeStr == "Custom") {
                val bitmap = createCustomMaterialGBitmap(iconTint, context)
                views.setImageViewBitmap(R.id.widget_g_logo, bitmap)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            } else {
                views.setImageViewResource(R.id.widget_g_logo, gIconRes)
                if (materialGIconTheme == "Accented G Icon") {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(iconTint))
                } else if (isPillLight && materialGIconTheme == "Material G Icon") {
                    val darkGTint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getColor(android.R.color.system_accent1_700)
                    } else {
                        android.graphics.Color.parseColor("#1F1F1F")
                    }
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(darkGTint))
                } else if (isPillLight && materialGIconTheme != "System G Icon") {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(android.graphics.Color.DKGRAY))
                } else {
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
                }
            }
        }

        private fun createCustomMaterialGBitmap(customColor: Int, context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (24 * density).toInt().coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = sizePx / 24f
            canvas.scale(scale, scale)

            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(customColor, hsv)
            val pColor = customColor
            val sColor = android.graphics.Color.HSVToColor(255, floatArrayOf((hsv[0] + 18f) % 360f, (hsv[1] * 0.70f).coerceIn(0.1f, 1f), hsv[2].coerceIn(0.6f, 1f)))
            val tColor = android.graphics.Color.HSVToColor(255, floatArrayOf((hsv[0] + 60f) % 360f, (hsv[1] * 0.85f).coerceIn(0.1f, 1f), hsv[2].coerceIn(0.7f, 1f)))

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            val path1 = androidx.core.graphics.PathParser.createPathFromPathData("M22.56,12.25C22.56,11.47 22.49,10.72 22.36,10L12,10L12,14.26L17.92,14.26C17.66,15.63 16.88,16.79 15.71,17.57L15.71,20.34L19.28,20.34C21.36,18.42 22.56,15.6 22.56,12.25Z")
            val path2 = androidx.core.graphics.PathParser.createPathFromPathData("M12,23C14.97,23 17.46,22.02 19.28,20.34L15.71,17.57C14.73,18.23 13.48,18.63 12,18.63C9.14,18.63 6.71,16.7 5.84,14.1L2.18,14.1L2.18,16.94C3.99,20.53 7.7,23 12,23Z")
            val path3 = androidx.core.graphics.PathParser.createPathFromPathData("M5.84,14.09C5.62,13.43 5.5,12.73 5.5,12C5.5,11.27 5.62,10.57 5.84,9.91L5.84,7.07L2.18,7.07C1.43,8.55 1,10.22 1,12C1,13.78 1.43,15.45 2.18,16.93L5.84,14.09Z")
            val path4 = androidx.core.graphics.PathParser.createPathFromPathData("M12,5.38C13.62,5.38 15.06,5.94 16.21,7.02L19.36,3.87C17.45,2.09 14.97,1 12,1C7.7,1 3.99,3.47 2.18,7.07L5.84,9.91C6.71,7.31 9.14,5.38 12,5.38Z")

            paint.color = pColor
            canvas.drawPath(path1, paint)
            paint.color = sColor
            canvas.drawPath(path2, paint)
            paint.color = tColor
            canvas.drawPath(path3, paint)
            paint.color = pColor
            canvas.drawPath(path4, paint)

            return bitmap
        }

        fun getNowPlayingIntent(context: Context): Intent {
            // 1. Pixel Now Playing (if present on device)
            val nowPlayingIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.pixel.nowplaying")
            if (nowPlayingIntent != null) return nowPlayingIntent

            // 2. Google Sound Search / Assistant Music Search (works on any Android device with Google app)
            val googleSoundSearchIntent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(googleSoundSearchIntent, 0).isNotEmpty()) {
                return googleSoundSearchIntent
            }

            // 3. Shazam
            val shazamIntent = context.packageManager.getLaunchIntentForPackage("com.shazam.android")
            if (shazamIntent != null) return shazamIntent

            // 4. SoundHound
            val soundHoundIntent = context.packageManager.getLaunchIntentForPackage("com.melodis.midomiMusicIdentifier.freemium")
            if (soundHoundIntent != null) return soundHoundIntent

            // 5. Universal Google voice music search query
            return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=what+song+is+this")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}



