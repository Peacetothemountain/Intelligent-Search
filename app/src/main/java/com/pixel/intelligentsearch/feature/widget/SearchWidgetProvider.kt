package com.pixel.intelligentsearch.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.LruCache
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import androidx.core.os.UserManagerCompat
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class SearchWidgetProvider : AppWidgetProvider() {

    open val forcedIsMaterial: Boolean? = null

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "com.pixel.intelligentsearch.ACTION_UPDATE_WIDGET" -> {
                val pendingResult = goAsync()
                updateAllWidgets(context, pendingResult)
            }
            "com.pixel.intelligentsearch.ACTION_HIDE_WIDGET",
            "com.pixel.intelligentsearch.ACTION_SHOW_WIDGET" -> {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = android.content.ComponentName(context, this::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
                val isHidden = intent.action == "com.pixel.intelligentsearch.ACTION_HIDE_WIDGET"
                val prefs = getSafeSharedPreferences(context)
                val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")

                for (appWidgetId in appWidgetIds) {
                    val isMaterialYou = resolveIsMaterialYou(appWidgetManager, appWidgetId, forcedIsMaterial, widgetThemeStyle)
                    val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
                    val views = RemoteViews(context.packageName, layoutId)
                    val visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
                    views.setViewVisibility(R.id.widget_outer_background, visibility)
                    views.setViewVisibility(R.id.widget_pill_container, visibility)
                    views.setViewVisibility(R.id.widget_sound_search, visibility)
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                updateWidgetsSync(context, appWidgetManager, appWidgetIds, forcedIsMaterial)
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "Error updating widgets in onUpdate", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                updateWidgetsSync(context, appWidgetManager, intArrayOf(appWidgetId), forcedIsMaterial)
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "Error updating widget options", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SearchWidgetProvider"
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun resolveIsMaterialYou(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            forcedIsMaterial: Boolean?,
            widgetThemeStyle: String?
        ): Boolean {
            if (forcedIsMaterial != null) {
                return forcedIsMaterial
            }
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val hostCategory = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) ?: -1
            val isSearchbox = (hostCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0

            return if (isSearchbox) {
                widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"
            } else {
                false
            }
        }

        // Slot identifiers for bit-shifted unique request codes
        private const val SLOT_PILL = 0
        private const val SLOT_G_LOGO = 1
        private const val SLOT_ACTION = 2
        private const val SLOT_CIRCLE = 3
        private const val SLOT_SHORTCUT_0 = 4
        private const val SLOT_SHORTCUT_1 = 5
        private const val SLOT_SHORTCUT_2 = 6
        private const val SLOT_SHORTCUT_3 = 7

        private fun getWidgetRequestCode(appWidgetId: Int, slot: Int): Int {
            return (appWidgetId shl 4) or (slot and 0x0F)
        }

        // Memory cache for generated vector bitmaps
        private val bitmapCache = LruCache<String, Bitmap>(32)

        // Pre-parsed Vector Paths for 0-allocation high performance rendering
        private val PATH_G_1 by lazy { PathParser.createPathFromPathData("M22.56,12.25C22.56,11.47 22.49,10.72 22.36,10L12,10L12,14.26L17.92,14.26C17.66,15.63 16.88,16.79 15.71,17.57L15.71,20.34L19.28,20.34C21.36,18.42 22.56,15.6 22.56,12.25Z") }
        private val PATH_G_2 by lazy { PathParser.createPathFromPathData("M12,23C14.97,23 17.46,22.02 19.28,20.34L15.71,17.57C14.73,18.23 13.48,18.63 12,18.63C9.14,18.63 6.71,16.7 5.84,14.1L2.18,14.1L2.18,16.94C3.99,20.53 7.7,23 12,23Z") }
        private val PATH_G_3 by lazy { PathParser.createPathFromPathData("M5.84,14.09C5.62,13.43 5.5,12.73 5.5,12C5.5,11.27 5.62,10.57 5.84,9.91L5.84,7.07L2.18,7.07C1.43,8.55 1,10.22 1,12C1,13.78 1.43,15.45 2.18,16.93L5.84,14.09Z") }
        private val PATH_G_4 by lazy { PathParser.createPathFromPathData("M12,5.38C13.62,5.38 15.06,5.94 16.21,7.02L19.36,3.87C17.45,2.09 14.97,1 12,1C7.7,1 3.99,3.47 2.18,7.07L5.84,9.91C6.71,7.31 9.14,5.38 12,5.38Z") }

        private val PATH_MIC_1 by lazy { PathParser.createPathFromPathData("M12,15c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v7c0,1.66 1.34,3 3,3z") }
        private val PATH_MIC_2 by lazy { PathParser.createPathFromPathData("M11,18.92h2V22h-2z") }
        private val PATH_MIC_3 by lazy { PathParser.createPathFromPathData("M7,12H5c0,1.93 0.78,3.68 2.05,4.95l1.41,-1.41C7.56,14.63 7,13.38 7,12z") }
        private val PATH_MIC_4 by lazy { PathParser.createPathFromPathData("M12,17c-1.38,0 -2.63,-0.56 -3.54,-1.47l-1.41,1.41C8.32,18.21 10.07,19 12.01,19c3.87,0 6.98,-3.14 6.98,-7h-2c0,2.76 -2.23,5 -4.99,5z") }

        private val PATH_LENS_1 by lazy { PathParser.createPathFromPathData("M75.0365 83.3333C79.6388 83.3333 83.3698 79.6023 83.3698 75C83.3698 70.3976 79.6388 66.6666 75.0365 66.6666C70.4341 66.6666 66.7031 70.3976 66.7031 75C66.7031 79.6023 70.4341 83.3333 75.0365 83.3333Z") }
        private val PATH_LENS_2 by lazy { PathParser.createPathFromPathData("M50.0364 66.6666C56.9399 66.6666 62.5364 61.0702 62.5364 54.1666C62.5364 47.2631 56.9399 41.6666 50.0364 41.6666C43.1328 41.6666 37.5364 47.2631 37.5364 54.1666C37.5364 61.0702 43.1328 66.6666 50.0364 66.6666Z") }
        private val PATH_LENS_3 by lazy { PathParser.createPathFromPathData("M12.5 70.4166C12.5 79.8489 20.151 87.5 29.5833 87.5H50V79.1666L29.1146 79.1145C24.5313 79.1145 20.8333 74.8489 20.8333 69.7916V60.4166H12.5V70.4166Z") }
        private val PATH_LENS_4 by lazy { PathParser.createPathFromPathData("M87.5001 37.9167C87.5001 28.4844 79.849 20.8334 70.4167 20.8334H60.4167L70.8334 29.1667C75.4167 29.1667 79.1667 33.4844 79.1667 38.5417V54.1667H87.5001V37.9167Z") }
        private val PATH_LENS_5 by lazy { PathParser.createPathFromPathData("M58.3333 12.5H41.6667L35.4167 20.8333H29.5833C20.151 20.8333 12.5 28.4844 12.5 37.9167V47.9167H20.8333V38.5417C20.8333 33.4844 24.5833 29.1667 29.1667 29.1667H70.8333L58.3333 12.5Z") }

        private val PATH_SEARCH_EXPR_1 by lazy { PathParser.createPathFromPathData("M 10.5,4 A 6.5,6.5 0 0 0 4,10.5 A 6.5,6.5 0 0 0 10.5,17 A 6.5,6.5 0 0 0 15,15") }
        private val PATH_SEARCH_EXPR_2 by lazy { PathParser.createPathFromPathData("M 14,5 A 6.5,6.5 0 0 0 10.5,4 A 6.5,6.5 0 0 0 5,7.5") }
        private val PATH_SEARCH_EXPR_3 by lazy { PathParser.createPathFromPathData("M 15.5,15.5 L 20,20") }
        private val PATH_SEARCH_EXPR_4 by lazy { PathParser.createPathFromPathData("M 18,0.5 C 18,4 21,6.5 24,6.5 C 21,6.5 18,9 18,12.5 C 18,9 15,6.5 12,6.5 C 15,6.5 18,4 18,0.5 Z") }

        private val PATH_MUSIC_1 by lazy { PathParser.createPathFromPathData("M 19,11.5 L 19,12.5") }
        private val PATH_MUSIC_2 by lazy { PathParser.createPathFromPathData("M 14.5,9.5 L 14.5,15.5") }
        private val PATH_MUSIC_3 by lazy { PathParser.createPathFromPathData("M 10,7 L 10,16") }
        private val PATH_MUSIC_4 by lazy { PathParser.createPathFromPathData("M 11.6,16.5 C 11.6,19.26 9.36,21.5 6.6,21.5 C 3.84,21.5 1.6,19.26 1.6,16.5 C 1.6,13.74 3.84,11.5 6.6,11.5 C 8.6,11.5 10.3,12.7 11.1,14.4 Z") }

        fun getSafeSharedPreferences(context: Context, name: String = "PREFERENCES_CUSTOMISATIONS"): SharedPreferences {
            val isUnlocked = UserManagerCompat.isUserUnlocked(context)
            val targetContext = if (isUnlocked) {
                context
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.createDeviceProtectedStorageContext()
                } else {
                    context
                }
            }
            return targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        fun updateAllWidgets(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
            widgetScope.launch {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch
                    
                    val providers = listOf(
                        SearchWidgetProvider::class.java,
                        SearchWidgetMaterialProvider::class.java
                    )
                    
                    for (providerClass in providers) {
                        try {
                            val componentName = android.content.ComponentName(context, providerClass)
                            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                                val forcedIsMaterial = when(providerClass) {
                                    SearchWidgetMaterialProvider::class.java -> true
                                    else -> null
                                }
                                updateWidgetsSync(context, appWidgetManager, appWidgetIds, forcedIsMaterial)
                            }
                        } catch (e: Throwable) {
                            // Class might not exist yet
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e(TAG, "Failed to update all widgets asynchronously", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        fun updateWidgetsSync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            forcedIsMaterial: Boolean? = null
        ) {
            val prefs = getSafeSharedPreferences(context)
            val showVoice = prefs.getBoolean("widget_show_voice", true)
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

            val dynamicScheme = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else null
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to get dynamicColorScheme", e)
                null
            }

            val widgetThemeStyle = prefs.getString("widget.theme.style", "System Default")

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

            val lockBlack = prefs.getBoolean("widget_material_lock_black", true)

            val transparency = prefs.getInt("widget.background.transparency", 28)
            val containerAlpha = ((100 - transparency) / 100f).coerceIn(0f, 1f)
            val containerAlphaInt = (containerAlpha * 255).toInt().coerceIn(0, 255)

            val colorAlpha = customColorOpacity.coerceIn(0f, 1f)
            val effectiveColorAlphaInt = (colorAlpha * containerAlpha * 255).toInt().coerceIn(0, 255)

            val customColorLuminance = (0.299 * android.graphics.Color.red(actualCustomColor) + 0.587 * android.graphics.Color.green(actualCustomColor) + 0.114 * android.graphics.Color.blue(actualCustomColor)) / 255

            val materialGIconTheme = prefs.getString("widget_material_g_icon", "Material G Icon") ?: "Material G Icon"

            val slotOrderStr = prefs.getString("widget_shortcut_order", "shortcut1,mic,shortcut2,shortcut3") ?: "shortcut1,mic,shortcut2,shortcut3"
            val slotOrder = slotOrderStr.split(",").filter { it.isNotBlank() }

            for (appWidgetId in appWidgetIds) {
                try {
                    val isMaterialYou = resolveIsMaterialYou(
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        forcedIsMaterial = forcedIsMaterial,
                        widgetThemeStyle = widgetThemeStyle
                    )

                    val rimColor = if (isMaterialYou) {
                        when (subthemeStr) {
                            "Custom" -> actualCustomColor
                            "Material" -> dynamicScheme?.primaryContainer?.toArgb()
                                ?: (if (isDark) context.getColor(android.R.color.system_accent1_800) else context.getColor(android.R.color.system_accent1_200))
                            else -> dynamicScheme?.primary?.toArgb()
                                ?: (if (isDark) context.getColor(android.R.color.system_accent1_800) else context.getColor(android.R.color.system_accent1_200))
                        }
                    } else {
                        android.graphics.Color.TRANSPARENT
                    }

                    val pillColor = if (isMaterialYou) {
                        if (lockBlack) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getColor(android.R.color.system_neutral1_900) else 0xFF121212.toInt()
                        } else actualCustomColor
                    } else {
                        when (subthemeStr) {
                            "Light" -> 0xFFF1F3F4.toInt()
                            "Dark" -> 0xFF303134.toInt()
                            else -> if (isDark) 0xFF303134.toInt() else 0xFFF1F3F4.toInt()
                        }
                    }

                    val circleColor = pillColor
                    val rimAlphaInt = if (isMaterialYou) effectiveColorAlphaInt else 0
                    val pillAlphaInt = if (isMaterialYou) {
                        if (lockBlack) containerAlphaInt else effectiveColorAlphaInt
                    } else {
                        containerAlphaInt
                    }
                    val circleAlphaInt = pillAlphaInt

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

                    val isPillLight = if (!isMaterialYou) {
                        subthemeStr == "Light" || (subthemeStr != "Dark" && !isDark)
                    } else {
                        !lockBlack && (customColorLuminance > 0.5)
                    }

                    val effectiveIconTheme = if (isMaterialYou) {
                        materialGIconTheme
                    } else {
                        "System G Icon"
                    }

                    val gIconRes = when (effectiveIconTheme) {
                        "System G Icon" -> R.drawable.ic_g_logo_colored
                        "Material G Icon" -> R.drawable.ic_g_logo
                        "Accented G Icon" -> R.drawable.ic_g_logo
                        else -> if (isMaterialYou) R.drawable.ic_g_logo else R.drawable.ic_g_logo_colored
                    }

                    val (themePColor, themeSColor, themeTColor) = if (isMaterialYou && subthemeStr == "Custom") {
                        val cHue = prefs.getInt("widget_custom_hue", 277).toFloat()
                        val cSaturation = prefs.getInt("widget_custom_saturation", 51).toFloat()
                        val isDarkSurface = !isPillLight
                        val m3Colors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
                            hue = cHue,
                            saturation = cSaturation,
                            isDarkSurface = isDarkSurface
                        )
                        Triple(m3Colors.primary, m3Colors.secondary, m3Colors.tertiary)
                    } else {
                        val p = dynamicScheme?.primary?.toArgb() ?: (if (isDark) 0xFF8AB4F8.toInt() else 0xFF1973E8.toInt())
                        val s = dynamicScheme?.secondary?.toArgb() ?: (if (isDark) 0xFFBDC1C6.toInt() else 0xFF5F6368.toInt())
                        val t = dynamicScheme?.tertiary?.toArgb() ?: (if (isDark) 0xFF81C995.toInt() else 0xFF188038.toInt())
                        Triple(p, s, t)
                    }

                    val useMaterialYouIcons = isMaterialYou

                    val allActiveItems = mutableListOf<Triple<String, Int, Intent>>()
                    for (key in slotOrder) {
                        when (key) {
                            "mic" -> {
                                if (showVoice) {
                                    val micIcon = if (useMaterialYouIcons) R.drawable.ic_mic else R.drawable.ic_mic_original
                                    allActiveItems.add(Triple("mic", micIcon, getVoiceSearchIntent(context)))
                                }
                            }
                            "shortcut1" -> {
                                if (shortcut1Str != "None") {
                                    allActiveItems.add(Triple("shortcut1", getShortcutIconRes(shortcut1Str, useMaterialYouIcons), getShortcutIntent(context, shortcut1Str)))
                                }
                            }
                            "shortcut2" -> {
                                if (shortcut2Str != "None") {
                                    allActiveItems.add(Triple("shortcut2", getShortcutIconRes(shortcut2Str, useMaterialYouIcons), getShortcutIntent(context, shortcut2Str)))
                                }
                            }
                            "shortcut3" -> {
                                if (shortcut3Str != "None") {
                                    allActiveItems.add(Triple("shortcut3", getShortcutIconRes(shortcut3Str, useMaterialYouIcons), getShortcutIntent(context, shortcut3Str)))
                                }
                            }
                        }
                    }

                    val buildViews = { isCompact: Boolean ->
                        buildRemoteViews(
                            context = context,
                            appWidgetId = appWidgetId,
                            prefs = prefs,
                            isMaterialYou = isMaterialYou,
                            isCompact = isCompact,
                            rimColorOpaque = rimColorOpaque,
                            rimAlphaInt = rimAlphaInt,
                            pillColorOpaque = pillColorOpaque,
                            pillAlphaInt = pillAlphaInt,
                            circleColorOpaque = circleColorOpaque,
                            circleAlphaInt = circleAlphaInt,
                            showGIcon = showGIcon,
                            gIconRes = gIconRes,
                            themePColor = themePColor,
                            themeSColor = themeSColor,
                            themeTColor = themeTColor,
                            effectiveIconTheme = effectiveIconTheme,
                            actionIconStr = actionIconStr,
                            activeItems = if (isCompact) allActiveItems.take(1) else allActiveItems,
                            isPillLight = isPillLight
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val compactViews = buildViews(true)
                        val standardViews = buildViews(false)
                        val responsiveViews = RemoteViews(
                            mapOf(
                                SizeF(0f, 0f) to compactViews,
                                SizeF(180f, 48f) to compactViews,
                                SizeF(250f, 48f) to standardViews
                            )
                        )
                        appWidgetManager.updateAppWidget(appWidgetId, responsiveViews)
                    } else {
                        val views = buildViews(false)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e(TAG, "Failed to update widget $appWidgetId", e)
                }
            }
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            prefs: SharedPreferences,
            isMaterialYou: Boolean,
            isCompact: Boolean,
            rimColorOpaque: Int,
            rimAlphaInt: Int,
            pillColorOpaque: Int,
            pillAlphaInt: Int,
            circleColorOpaque: Int,
            circleAlphaInt: Int,
            showGIcon: Boolean,
            gIconRes: Int,
            themePColor: Int,
            themeSColor: Int,
            themeTColor: Int,
            effectiveIconTheme: String,
            actionIconStr: String,
            activeItems: List<Triple<String, Int, Intent>>,
            isPillLight: Boolean
        ): RemoteViews {
            val layoutId = if (isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
            val views = RemoteViews(context.packageName, layoutId)

            if (isMaterialYou) {
                views.setViewVisibility(R.id.widget_outer_background, View.VISIBLE)
                views.setColorStateList(R.id.widget_outer_background, "setImageTintList", android.content.res.ColorStateList.valueOf(rimColorOpaque))
                views.setInt(R.id.widget_outer_background, "setImageAlpha", rimAlphaInt)
            } else {
                views.setViewVisibility(R.id.widget_outer_background, View.GONE)
            }

            views.setColorStateList(R.id.widget_pill_background, "setImageTintList", android.content.res.ColorStateList.valueOf(pillColorOpaque))
            views.setInt(R.id.widget_pill_background, "setImageAlpha", pillAlphaInt)

            views.setColorStateList(R.id.widget_sound_background, "setImageTintList", android.content.res.ColorStateList.valueOf(circleColorOpaque))
            views.setInt(R.id.widget_sound_background, "setImageAlpha", circleAlphaInt)

            bindGIcon(views, showGIcon, gIconRes, themePColor, themeSColor, themeTColor, effectiveIconTheme, context)

            val viewIdTargets = listOf(
                R.id.widget_voice_search,
                R.id.widget_lens_search,
                R.id.widget_shortcut_2,
                R.id.widget_shortcut_3
            )

            for (i in 0 until 4) {
                val targetViewId = viewIdTargets[i]
                if (i < activeItems.size) {
                    val item = activeItems[i]
                    views.setViewVisibility(targetViewId, View.VISIBLE)

                    when (effectiveIconTheme) {
                        "Accented G Icon" -> {
                            views.setImageViewResource(targetViewId, item.second)
                            views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(themePColor))
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                        "Material G Icon" -> {
                            val themedBitmap = createThemedShortcutBitmap(context, item.second, themePColor, themeSColor, themeTColor)
                            if (themedBitmap != null) {
                                views.setImageViewBitmap(targetViewId, themedBitmap)
                                views.setColorStateList(targetViewId, "setImageTintList", null)
                            } else {
                                views.setImageViewResource(targetViewId, item.second)
                                views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(themePColor))
                            }
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                        else -> {
                            if (item.first == "mic" || item.second == R.drawable.ic_mic_original || item.second == R.drawable.ic_mic) {
                                val micBitmap = createGoogleMicColoredBitmap(context)
                                views.setImageViewBitmap(targetViewId, micBitmap)
                                views.setColorStateList(targetViewId, "setImageTintList", null)
                            } else if (item.first == "Google Lens" || item.second == R.drawable.ic_camera) {
                                val lensBitmap = createGoogleLensColoredBitmap(context)
                                views.setImageViewBitmap(targetViewId, lensBitmap)
                                views.setColorStateList(targetViewId, "setImageTintList", null)
                            } else {
                                views.setImageViewResource(targetViewId, item.second)
                                val sysTint = if (isPillLight) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        context.getColor(android.R.color.system_accent1_700)
                                    } else {
                                        android.graphics.Color.parseColor("#1F1F1F")
                                    }
                                } else {
                                    android.graphics.Color.WHITE
                                }
                                views.setColorStateList(targetViewId, "setImageTintList", android.content.res.ColorStateList.valueOf(sysTint))
                            }
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                    }

                    val slotCode = SLOT_SHORTCUT_0 + i
                    val targetIntent = item.third.apply {
                        data = Uri.parse("widget://slot/$appWidgetId/$slotCode")
                    }
                    val pi = PendingIntent.getActivity(
                        context,
                        getWidgetRequestCode(appWidgetId, slotCode),
                        targetIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(targetViewId, pi)
                } else {
                    views.setViewVisibility(targetViewId, View.GONE)
                }
            }

            // Tap pill -> main search overlay
            val enableSearchOverlay = prefs.getBoolean("search_overlay_enabled", true)
            val mainIntent = if (enableSearchOverlay) {
                Intent(context, WidgetActivity::class.java).apply {
                    data = Uri.parse("widget://pill/$appWidgetId")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                Intent("android.search.action.GLOBAL_SEARCH").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    data = Uri.parse("widget://pill_global/$appWidgetId")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }.takeIf { context.packageManager.resolveActivity(it, 0) != null }
                    ?: Intent(Intent.ACTION_WEB_SEARCH).apply {
                        data = Uri.parse("widget://pill_web/$appWidgetId")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            }

            val mainPI = PendingIntent.getActivity(
                context,
                getWidgetRequestCode(appWidgetId, SLOT_PILL),
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_pill_container, mainPI)
            views.setOnClickPendingIntent(R.id.widget_g_logo, mainPI)

            // Tap action button inside search pill
            val actionIntent = getVoiceActionIntent(context).apply {
                data = Uri.parse("widget://action/$appWidgetId")
            }
            val actionPI = PendingIntent.getActivity(
                context,
                getWidgetRequestCode(appWidgetId, SLOT_ACTION),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_search, actionPI)

            // Circle button handling
            if (!isMaterialYou || actionIconStr == "None" || isCompact) {
                views.setViewVisibility(R.id.widget_sound_search, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_sound_search, View.VISIBLE)
                val circleActionIntent = when (actionIconStr) {
                    "Assistant", "Voice", "Gemini" -> getVoiceActionIntent(context)
                    "Now Playing" -> getNowPlayingIntent(context)
                    else -> getVoiceActionIntent(context)
                }.apply {
                    data = Uri.parse("widget://circle/$appWidgetId")
                }

                val circleActionPI = PendingIntent.getActivity(
                    context,
                    getWidgetRequestCode(appWidgetId, SLOT_CIRCLE),
                    circleActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_sound_search, circleActionPI)

                val circleActionIconRes = when (actionIconStr) {
                    "Assistant", "Voice", "Gemini" -> R.drawable.ic_lens_action
                    "Now Playing" -> R.drawable.ic_music
                    else -> R.drawable.ic_search_lens_expressive
                }

                when (effectiveIconTheme) {
                    "Accented G Icon" -> {
                        views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", android.content.res.ColorStateList.valueOf(themePColor))
                    }
                    "Material G Icon" -> {
                        val themedBitmap = createThemedActionIconBitmap(context, circleActionIconRes, themePColor, themeSColor, themeTColor)
                        if (themedBitmap != null) {
                            views.setImageViewBitmap(R.id.widget_sound_icon, themedBitmap)
                            views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", null)
                        } else {
                            views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                            views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", android.content.res.ColorStateList.valueOf(themePColor))
                        }
                    }
                    else -> {
                        views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
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
                views.setInt(R.id.widget_sound_icon, "setImageAlpha", 255)
            }

            return views
        }

        fun getVoiceSearchIntent(context: Context): Intent {
            val googleVoice = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.googlequicksearchbox.VoiceSearchActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(googleVoice, 0) != null) {
                return googleVoice
            }
            val webSearch = Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(webSearch, 0) != null) {
                return webSearch
            }
            val speech = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(speech, 0) != null) {
                return speech
            }
            return Intent(context, WidgetActivity::class.java).apply {
                putExtra("auto_voice", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        fun getShortcutIconRes(shortcut: String, isMaterialYou: Boolean = true): Int {
            return when (shortcut) {
                "Voice Search" -> if (isMaterialYou) R.drawable.ic_mic else R.drawable.ic_mic_original
                "Google Lens" -> R.drawable.ic_camera
                "Assistant", "Live" -> R.drawable.ic_lens_action
                "Translate (text)" -> R.drawable.ic_translate
                "Translate (camera)" -> R.drawable.ic_document_scanner
                "Song Search" -> R.drawable.ic_music
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
                if (lensStandalone != null && context.packageManager.resolveActivity(lensStandalone, 0) != null) {
                    return lensStandalone
                }

                val lensIntent = Intent().apply {
                    setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(lensIntent, 0) != null) {
                    return lensIntent
                }
            } catch (_: Exception) {}

            val webLens = Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(webLens, 0) != null) {
                return webLens
            }
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
                if (lensStandalone != null && context.packageManager.resolveActivity(lensStandalone, 0) != null) {
                    return lensStandalone
                }

                val translateIntent = Intent().apply {
                    setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.deeplink.LensDeeplink")
                    putExtra("lens_mode", "translate")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(translateIntent, 0) != null) {
                    return translateIntent
                }
            } catch (_: Exception) {}
            return Intent(context, WidgetActivity::class.java).apply {
                action = "com.pixel.intelligentsearch.LAUNCH_LENS_TRANSLATE"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }

        fun getVoiceActionIntent(context: Context): Intent {
            val voiceAction = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (context.packageManager.resolveActivity(voiceAction, 0) != null) {
                return voiceAction
            }
            val assist = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (context.packageManager.resolveActivity(assist, 0) != null) {
                return assist
            }
            return getVoiceSearchIntent(context)
        }

        fun getGeminiSearchIntent(context: Context): Intent = getVoiceActionIntent(context)

        fun getShortcutIntent(context: Context, shortcut: String): Intent {
            return when (shortcut) {
                "Voice Search" -> getVoiceSearchIntent(context)
                "Assistant", "Live" -> getVoiceActionIntent(context)
                "Translate (text)" -> context.packageManager.getLaunchIntentForPackage("com.google.android.apps.translate")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "Translate (camera)" -> getLensTranslateIntent(context)
                "Song Search" -> getNowPlayingIntent(context)
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
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=weather")).apply {
                                setPackage("com.google.android.googlequicksearchbox")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
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
            val prefs = getSafeSharedPreferences(context)
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
            pColor: Int,
            sColor: Int,
            tColor: Int,
            materialGIconTheme: String,
            context: Context
        ) {
            views.setViewVisibility(R.id.widget_g_logo, if (showGIcon) View.VISIBLE else View.GONE)
            views.setInt(R.id.widget_g_logo, "setImageAlpha", 255)
            if (materialGIconTheme == "Material G Icon") {
                val bitmap = createCustomMaterialGBitmap(pColor, sColor, tColor, context)
                views.setImageViewBitmap(R.id.widget_g_logo, bitmap)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            } else if (materialGIconTheme == "Accented G Icon") {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", android.content.res.ColorStateList.valueOf(pColor))
            } else {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo_colored)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            }
        }

        private fun createCustomMaterialGBitmap(pColor: Int, sColor: Int, tColor: Int, context: Context): Bitmap {
            val cacheKey = "g_${pColor}_${sColor}_$tColor"
            bitmapCache.get(cacheKey)?.let { return it }

            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (24 * density).toInt().coerceAtLeast(48)
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

                paint.color = pColor
                canvas.drawPath(PATH_G_1, paint)
                paint.color = sColor
                canvas.drawPath(PATH_G_2, paint)
                paint.color = tColor
                canvas.drawPath(PATH_G_3, paint)
                paint.color = pColor
                canvas.drawPath(PATH_G_4, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to create custom material G bitmap", e)
                val fallbackSize = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
                Bitmap.createBitmap(fallbackSize, fallbackSize, Bitmap.Config.ARGB_8888)
            }
        }

        private fun createThemedShortcutBitmap(
            context: Context,
            resId: Int,
            pColor: Int,
            sColor: Int,
            tColor: Int
        ): Bitmap? {
            val cacheKey = "shortcut_${resId}_${pColor}_${sColor}_$tColor"
            bitmapCache.get(cacheKey)?.let { return it }

            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (24 * density).toInt().coerceAtLeast(48)
                when (resId) {
                    R.drawable.ic_mic -> {
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        val scale = sizePx / 24f
                        canvas.scale(scale, scale)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

                        paint.color = pColor
                        canvas.drawPath(PATH_MIC_1, paint)
                        paint.color = sColor
                        canvas.drawPath(PATH_MIC_2, paint)
                        paint.color = tColor
                        canvas.drawPath(PATH_MIC_3, paint)
                        paint.color = pColor
                        canvas.drawPath(PATH_MIC_4, paint)

                        bitmapCache.put(cacheKey, bitmap)
                        bitmap
                    }
                    R.drawable.ic_camera -> {
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        val scale = sizePx / 100f
                        canvas.scale(scale, scale)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

                        paint.color = pColor
                        canvas.drawPath(PATH_LENS_1, paint)
                        paint.color = sColor
                        canvas.drawPath(PATH_LENS_2, paint)
                        paint.color = tColor
                        canvas.drawPath(PATH_LENS_3, paint)
                        paint.color = pColor
                        canvas.drawPath(PATH_LENS_4, paint)
                        paint.color = sColor
                        canvas.drawPath(PATH_LENS_5, paint)

                        bitmapCache.put(cacheKey, bitmap)
                        bitmap
                    }
                    else -> null
                }
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to create themed shortcut bitmap", e)
                null
            }
        }

        private fun createGoogleLensColoredBitmap(context: Context): Bitmap {
            val cacheKey = "lens_colored_google"
            bitmapCache.get(cacheKey)?.let { return it }

            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (24 * density).toInt().coerceAtLeast(48)
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val scale = sizePx / 100f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

                // 1. Bottom-right dot (Green)
                paint.color = 0xFF34A853.toInt()
                canvas.drawPath(PATH_LENS_1, paint)

                // 2. Center aperture circle (Blue)
                paint.color = 0xFF4285F4.toInt()
                canvas.drawPath(PATH_LENS_2, paint)

                // 3. Bottom-left bracket (Yellow)
                paint.color = 0xFFFBBC05.toInt()
                canvas.drawPath(PATH_LENS_3, paint)

                // 4. Top-right bracket (Green)
                paint.color = 0xFF34A853.toInt()
                canvas.drawPath(PATH_LENS_4, paint)

                // 5. Top-left bracket (Red)
                paint.color = 0xFFEA4335.toInt()
                canvas.drawPath(PATH_LENS_5, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to create Google Lens colored bitmap", e)
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }

        private fun createGoogleMicColoredBitmap(context: Context): Bitmap {
            val cacheKey = "mic_colored_google"
            bitmapCache.get(cacheKey)?.let { return it }

            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (24 * density).toInt().coerceAtLeast(48)
                val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val scale = sizePx / 24f
                canvas.scale(scale, scale)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

                // Google Mic official 4 colors
                // 1. Center microphone body (Blue)
                paint.color = 0xFF4285F4.toInt()
                canvas.drawPath(PATH_MIC_1, paint)

                // 2. Base stem (Green)
                paint.color = 0xFF34A853.toInt()
                canvas.drawPath(PATH_MIC_2, paint)

                // 3. Left cradle (Yellow)
                paint.color = 0xFFFBBC05.toInt()
                canvas.drawPath(PATH_MIC_3, paint)

                // 4. Right / bottom cradle (Red)
                paint.color = 0xFFEA4335.toInt()
                canvas.drawPath(PATH_MIC_4, paint)

                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to create Google Mic colored bitmap", e)
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }

        private fun createThemedActionIconBitmap(
            context: Context,
            resId: Int,
            pColor: Int,
            sColor: Int,
            tColor: Int
        ): Bitmap? {
            val cacheKey = "action_${resId}_${pColor}_${sColor}_$tColor"
            bitmapCache.get(cacheKey)?.let { return it }

            return try {
                val density = context.resources.displayMetrics.density
                val sizePx = (24 * density).toInt().coerceAtLeast(48)
                when (resId) {
                    R.drawable.ic_search_lens_expressive -> {
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        val scale = sizePx / 24f
                        canvas.scale(scale, scale)
                        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                        }
                        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                        }

                        strokePaint.strokeWidth = 2.5f
                        strokePaint.color = pColor
                        canvas.drawPath(PATH_SEARCH_EXPR_1, strokePaint)

                        strokePaint.strokeWidth = 2.5f
                        strokePaint.color = tColor
                        canvas.drawPath(PATH_SEARCH_EXPR_2, strokePaint)

                        strokePaint.strokeWidth = 3.0f
                        strokePaint.color = sColor
                        canvas.drawPath(PATH_SEARCH_EXPR_3, strokePaint)

                        fillPaint.color = pColor
                        canvas.drawPath(PATH_SEARCH_EXPR_4, fillPaint)

                        bitmapCache.put(cacheKey, bitmap)
                        bitmap
                    }
                    R.drawable.ic_music -> {
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        val scale = sizePx / 24f
                        canvas.scale(scale, scale)
                        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeWidth = 3.2f
                        }
                        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                        }

                        strokePaint.color = tColor
                        canvas.drawPath(PATH_MUSIC_1, strokePaint)

                        strokePaint.color = sColor
                        canvas.drawPath(PATH_MUSIC_2, strokePaint)

                        strokePaint.color = pColor
                        canvas.drawPath(PATH_MUSIC_3, strokePaint)

                        fillPaint.color = pColor
                        canvas.drawPath(PATH_MUSIC_4, fillPaint)

                        bitmapCache.put(cacheKey, bitmap)
                        bitmap
                    }
                    else -> null
                }
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to create themed action icon bitmap", e)
                null
            }
        }

        fun getNowPlayingIntent(context: Context): Intent {
            // 1. Pixel Now Playing
            val nowPlayingIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.pixel.nowplaying")
            if (nowPlayingIntent != null && context.packageManager.resolveActivity(nowPlayingIntent, 0) != null) {
                return nowPlayingIntent
            }

            // 2. Google Sound Search / Assistant Music Search
            val googleSoundSearchIntent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.queryIntentActivities(googleSoundSearchIntent, 0).isNotEmpty()) {
                return googleSoundSearchIntent
            }

            // 3. Shazam
            val shazamIntent = context.packageManager.getLaunchIntentForPackage("com.shazam.android")
            if (shazamIntent != null && context.packageManager.resolveActivity(shazamIntent, 0) != null) {
                return shazamIntent
            }

            // 4. SoundHound
            val soundHoundIntent = context.packageManager.getLaunchIntentForPackage("com.melodis.midomiMusicIdentifier.freemium")
            if (soundHoundIntent != null && context.packageManager.resolveActivity(soundHoundIntent, 0) != null) {
                return soundHoundIntent
            }

            // 5. Universal Google voice music search query
            return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=what+song+is+this")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
