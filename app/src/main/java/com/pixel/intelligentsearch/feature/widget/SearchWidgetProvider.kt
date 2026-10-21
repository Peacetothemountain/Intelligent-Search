package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.core.util.storageContext
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.widget.WidgetDefaults
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.data.widget.WidgetDesignSettings
import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.data.widget.WidgetSettingsStore
import com.pixel.intelligentsearch.core.occasion.Occasion
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import com.pixel.intelligentsearch.feature.search.webSearchLaunch
import androidx.core.graphics.toColorInt
import android.app.PendingIntent
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.MaterialYouPaletteHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Modernized AppWidgetProvider for Intelligent Search.
 * Fully compliant with Android 12+ (API 31) through Android 17 (Canary ZP11 / API 37).
 *
 * Implements:
 * - Multi-tier responsive RemoteViews breakpoints (Ultra-compact, Compact, Standard, Expanded)
 * - Material You dynamic color extraction & M3 HCT tonal harmonization
 * - Density-aware vector bitmap caching for foldable and multi-density displays
 * - Full accessibility and TalkBack dynamic content descriptions
 * - Trampoline-free, secure pending intent execution
 */
@AndroidEntryPoint
open class SearchWidgetProvider : AppWidgetProvider() {

    open val forcedIsMaterial: Boolean? = null

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // This receiver is exported (the launcher binds widgets through it), so it ignores custom actions any
        // app could send to force redraws; only an app update, or the clock, time zone or language changing
        // (broadcasts only the system can send), triggers a full refresh here. (Wallpaper and configuration
        // broadcasts are never delivered to manifest receivers.)
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DynamicThemeDetector.invalidateCache()
                clearBitmapCache()
                WidgetDoodles.scheduleNextChange(context)
                val pendingResult = goAsync()
                updateAllWidgets(context, pendingResult)
            }
            // Which doodle the G shows depends on the date where the phone is, and some doodles on its region
            // (the language setting's country).
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                WidgetDoodles.scheduleNextChange(context)
                updateAllWidgets(context, goAsync())
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetDoodles.scheduleNextChange(context)
        val pendingResult = goAsync()
        context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
            try {
                updateWidgetsSync(context, appWidgetManager, appWidgetIds, forcedIsMaterial)
            } catch (e: Throwable) {
                Log.e(TAG, "Error updating widgets in onUpdate", e)
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
        context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
            try {
                updateWidgetsSync(context, appWidgetManager, intArrayOf(appWidgetId), forcedIsMaterial)
            } catch (e: Throwable) {
                Log.e(TAG, "Error updating widget options", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SearchWidgetProvider"

        fun resolveIsMaterialYou(
            appWidgetManager: AppWidgetManager?,
            appWidgetId: Int,
            forcedIsMaterial: Boolean?,
            widgetThemeStyle: String?,
            options: Bundle? = null
        ): Boolean {
            if (forcedIsMaterial != null) {
                return forcedIsMaterial
            }

            val providerClassName = try {
                appWidgetManager?.getAppWidgetInfo(appWidgetId)?.provider?.className
            } catch (_: Throwable) {
                null
            }

            // 1. Dedicated Material Search Bar from widget selector is ALWAYS Material
            if (providerClassName == SearchWidgetMaterialProvider::class.java.name) {
                return true
            }

            // 2. Identify if this widget is the dedicated SearchBox (Default search engine / ADB bar)
            val widgetOpts = options ?: try {
                appWidgetManager?.getAppWidgetOptions(appWidgetId)
            } catch (_: Throwable) {
                null
            }
            val hostCat = widgetOpts?.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) ?: -1
            val isSearchBox = hostCat > 0 && ((hostCat and AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0)

            if (isSearchBox) {
                // The search bar set on Pixel via ADB or Default search provider
                // dynamically follows the user's choice in Widget Customization!
                return widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"
            }

            // 3. Regular home screen widget from Android Widget Selector (SearchWidgetProvider):
            if (providerClassName == SearchWidgetProvider::class.java.name) {
                return false
            }

            // 4. Fallback when neither provider info nor host category is available (e.g. unit tests):
            return widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"
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

        private fun getPendingIntentActivityOptions(context: Context): Bundle? {
            return try {
                BackgroundLaunchOptions.forCreator(context).toBundle()
            } catch (e: Exception) {
                Log.w(TAG, "Widget launch options unavailable", e)
                null
            }
        }

        init {
            DynamicThemeDetector.onThemeChangedCallback = { ctx ->
                clearBitmapCache()
                updateAllWidgets(ctx)
            }
        }

        fun getSafeSharedPreferences(context: Context, name: String = SettingsManager.PREFS_NAME): SharedPreferences =
            context.storageContext().getSharedPreferences(name, Context.MODE_PRIVATE)

        fun updateAllWidgets(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
            val appContext = context.applicationContext
            context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return@launch
                    val prefs = getSafeSharedPreferences(appContext)
                    val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                        .isDark(ThemeMode.systemIsDark(appContext.resources.configuration))
                    val widgetColors = DynamicThemeDetector.resolveWidgetColors(appContext, isDark)

                    val providers = listOf(
                        SearchWidgetProvider::class.java,
                        SearchWidgetMaterialProvider::class.java
                    )

                    for (providerClass in providers) {
                        try {
                            val componentName = android.content.ComponentName(appContext, providerClass)
                            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                                val forcedIsMaterial = when (providerClass) {
                                    SearchWidgetMaterialProvider::class.java -> true
                                    else -> null
                                }
                                updateWidgetsSync(appContext, appWidgetManager, appWidgetIds, forcedIsMaterial)
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Provider query failed for ${providerClass.simpleName}", e)
                        }
                    }

                    // Android 15+ (API 35) / Android 16 / Android 17 (Canary ZP11 / API 37)
                    // Push dynamic personalized previews to system widget picker
                    updateWidgetPickerPreviews(appContext, appWidgetManager, prefs, isDark, widgetColors)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update all widgets asynchronously", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        /**
         * Android 15+ (API 35+) dynamic widget preview pushing.
         * Updates the system widget picker with personalized RemoteViews representing current user styling.
         */
        private fun updateWidgetPickerPreviews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            prefs: SharedPreferences,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors
        ) {
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    val systemConfig = resolveWidgetConfig(
                        context = context,
                        prefs = prefs,
                        isMaterialYou = false,
                        isDark = isDark,
                        widgetColors = widgetColors
                    )
                    val systemPreview = buildRemoteViews(
                        context = context,
                        appWidgetId = 0,
                        prefs = prefs,
                        maxShortcuts = 3,
                        config = systemConfig
                    )
                    appWidgetManager.setWidgetPreview(
                        android.content.ComponentName(context, SearchWidgetProvider::class.java),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        systemPreview
                    )

                    val materialConfig = resolveWidgetConfig(
                        context = context,
                        prefs = prefs,
                        isMaterialYou = true,
                        isDark = isDark,
                        widgetColors = widgetColors
                    )
                    val materialPreview = buildRemoteViews(
                        context = context,
                        appWidgetId = 0,
                        prefs = prefs,
                        maxShortcuts = 2,
                        config = materialConfig
                    )
                    appWidgetManager.setWidgetPreview(
                        android.content.ComponentName(context, SearchWidgetMaterialProvider::class.java),
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        materialPreview
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Dynamic widget preview update skipped or rate-limited", e)
                }
            }
        }

        fun updateWidgetsAsync(
            context: Context,
            appWidgetIds: IntArray,
            forcedIsMaterial: Boolean? = null,
            pendingResult: android.content.BroadcastReceiver.PendingResult? = null
        ) {
            val appContext = context.applicationContext
            context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return@launch
                    updateWidgetsSync(appContext, appWidgetManager, appWidgetIds, forcedIsMaterial)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update widgets asynchronously", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private data class ActiveShortcutItem(
            val slotKey: String,
            val actionName: String,
            val iconRes: Int,
            val intent: Intent
        )

        /**
         * Encapsulates all resolved design and dynamic color attributes for widget rendering.
         */
        private data class WidgetDesignConfig(
            val isMaterialYou: Boolean,
            val rimColorOpaque: Int,
            val rimAlphaInt: Int,
            val pillColorOpaque: Int,
            val pillAlphaInt: Int,
            val circleColorOpaque: Int,
            val circleAlphaInt: Int,
            val showGIcon: Boolean,
            /** Whether the G gets the day's doodle around it on doodle days. */
            val showDoodles: Boolean,
            val gIconRes: Int,
            val themePColor: Int,
            val themeSColor: Int,
            val themeTColor: Int,
            val effectiveIconTheme: String,
            val actionIconStr: String,
            val allActiveItems: List<ActiveShortcutItem>,
            val isPillLight: Boolean,
            val customSubtheme: Boolean,
            /** The design's custom element colours and whether they all follow the G icon's. */
            val elementColors: WidgetElementColors,
            val syncColors: Boolean,
            val keepSystemG: Boolean
        )

        fun updateWidgetsSync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            forcedIsMaterial: Boolean? = null
        ) {
            val prefs = getSafeSharedPreferences(context)
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                .isDark(ThemeMode.systemIsDark(context.resources.configuration))

            val widgetColors = DynamicThemeDetector.resolveWidgetColors(context, isDark)
            val widgetThemeStyle = prefs[SettingsManager.WIDGET_THEME_STYLE]

            for (appWidgetId in appWidgetIds) {
                try {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    val isMaterialYou = resolveIsMaterialYou(
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        forcedIsMaterial = forcedIsMaterial,
                        widgetThemeStyle = widgetThemeStyle,
                        options = options
                    )

                    val responsiveViews = RemoteViews(
                        buildResponsiveViews(context, prefs, appWidgetId, isMaterialYou, isDark, widgetColors)
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, responsiveViews)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update widget $appWidgetId", e)
                }
            }
        }

        /** The widths the launcher can give the widget, in dp; [STANDARD_WIDTH] is a phone's default 4 cells. */
        val ULTRA_COMPACT_WIDTH = SizeF(110f, 48f)
        val COMPACT_WIDTH = SizeF(180f, 48f)
        val STANDARD_WIDTH = SizeF(260f, 48f)
        val EXPANDED_WIDTH = SizeF(340f, 48f)

        /**
         * The views one widget shows at each supported width, built from the current settings and theme, keyed
         * the way [RemoteViews] expects for a responsive widget. The render goldens draw these directly.
         */
        internal fun buildResponsiveViews(
            context: Context,
            appWidgetId: Int,
            isMaterialYou: Boolean,
            prefs: SharedPreferences = getSafeSharedPreferences(context)
        ): Map<SizeF, RemoteViews> {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                .isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val widgetColors = DynamicThemeDetector.resolveWidgetColors(context, isDark)
            return buildResponsiveViews(context, prefs, appWidgetId, isMaterialYou, isDark, widgetColors)
        }

        private fun buildResponsiveViews(
            context: Context,
            prefs: SharedPreferences,
            appWidgetId: Int,
            isMaterialYou: Boolean,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors
        ): Map<SizeF, RemoteViews> {
            val config = resolveWidgetConfig(
                context = context,
                prefs = prefs,
                isMaterialYou = isMaterialYou,
                isDark = isDark,
                widgetColors = widgetColors
            )

            // Android 12+ (API 31) through Android 17 (Canary ZP11 / API 37)
            // Multi-tier responsive RemoteViews definitions mapped to launcher grid constraints:
            // 1. Ultra-compact (130dp / 2 cells & glanceable hubs): G logo + companion circle (or 1 shortcut)
            // 2. Compact (180dp / 3 cells): G logo + 1 shortcut + circle (or 2 shortcuts)
            // 3. Standard (260dp / 4 cells - default phone width): G logo + 2 shortcuts + circle (or 3 shortcuts)
            // 4. Expanded (340dp / 5 cells, foldables & tablets): G logo + all shortcuts
            // Only placed widgets come through here; the widget picker's previews call buildRemoteViews without one.
            val doodle = if (config.showGIcon && config.showDoodles) WidgetDoodles.current(prefs) else null
            val buildViews = { maxShortcuts: Int ->
                buildRemoteViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    prefs = prefs,
                    maxShortcuts = maxShortcuts,
                    config = config,
                    doodle = doodle
                )
            }

            val hasCircle = isMaterialYou && (config.actionIconStr != "None")
            // Strictly positive SizeF keys compliant with Android 12+ RemoteViews validation:
            // SizeF(0f, 0f) is forbidden by AOSP RemoteViews constructor.
            return mapOf(
                ULTRA_COMPACT_WIDTH to buildViews(if (hasCircle) 0 else 1),
                COMPACT_WIDTH to buildViews(if (hasCircle) 1 else 2),
                STANDARD_WIDTH to buildViews(if (hasCircle) 2 else 3),
                EXPANDED_WIDTH to buildViews(4)
            )
        }

        private fun resolveWidgetConfig(
            context: Context,
            prefs: SharedPreferences,
            isMaterialYou: Boolean,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors
        ): WidgetDesignConfig {
            // The same reader as Widget Customization, so the widget shows what the editor showed.
            val design = if (isMaterialYou) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
            val settings = WidgetSettingsStore(prefs).load(design, WidgetDefaults(widgetColors.primary, widgetColors.isDynamicActive))
            val isCustom = settings.subtheme == "Custom"
            val customSat = settings.saturation / 100f
            val customColorOpacity = (settings.colorOpacity / 100f).coerceIn(0f, 1f)
            val containerAlpha = ((100 - settings.transparency.toInt()) / 100f).coerceIn(0f, 1f)
            val containerAlphaInt = (containerAlpha * 255).toInt().coerceIn(0, 255)
            val effectiveColorAlphaInt = (customColorOpacity * containerAlpha * 255).toInt().coerceIn(0, 255)
            val actualCustomColor = settings.customColorInt
            val customColorLuminance = (0.299 * Color.red(actualCustomColor) + 0.587 * Color.green(actualCustomColor) + 0.114 * Color.blue(actualCustomColor)) / 255.0
            val effectiveIconTheme = settings.gIconTheme

            return if (!isMaterialYou) {
                // SYSTEM DESIGN BAR: Colorful Google branding, no outer accent rim
                val pillColor = when (settings.subtheme) {
                    "Light" -> 0xFFF1F3F4.toInt()
                    "Dark" -> 0xFF303134.toInt()
                    "Custom" -> actualCustomColor
                    else -> widgetColors.surfaceContainer
                }
                val pillAlphaInt = if (isCustom) effectiveColorAlphaInt else containerAlphaInt
                val isPillLight = settings.subtheme == "Light" || (settings.subtheme == "System" && !isDark) || (isCustom && customColorLuminance > 0.5)

                val gIconRes = when (effectiveIconTheme) {
                    "System G Icon" -> R.drawable.ic_g_logo_colored
                    "Material G Icon", "Accented G Icon", "Custom G Icon" -> R.drawable.ic_g_logo
                    else -> R.drawable.ic_g_logo_colored
                }

                val (themePColor, themeSColor, themeTColor) = if (isCustom) {
                    val m3Colors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
                        hue = settings.hue,
                        saturation = customSat * 100f,
                        isDarkSurface = !isPillLight
                    )
                    Triple(m3Colors.primary, m3Colors.secondary, m3Colors.tertiary)
                } else {
                    Triple(widgetColors.primary, widgetColors.secondary, widgetColors.tertiary)
                }

                val useMaterialYouIcons = (effectiveIconTheme != "System G Icon") || isPillLight || isCustom
                val micIcon = if (useMaterialYouIcons) R.drawable.ic_mic else R.drawable.ic_mic_original
                val pillColorOpaque = Color.rgb(Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))

                WidgetDesignConfig(
                    isMaterialYou = false,
                    rimColorOpaque = Color.TRANSPARENT,
                    rimAlphaInt = 0,
                    pillColorOpaque = pillColorOpaque,
                    pillAlphaInt = pillAlphaInt,
                    circleColorOpaque = pillColorOpaque,
                    circleAlphaInt = pillAlphaInt,
                    showGIcon = settings.showGIcon,
                    showDoodles = settings.showDoodles,
                    gIconRes = gIconRes,
                    themePColor = themePColor,
                    themeSColor = themeSColor,
                    themeTColor = themeTColor,
                    effectiveIconTheme = effectiveIconTheme,
                    actionIconStr = "None",
                    allActiveItems = activeItems(context, settings, micIcon, useMaterialYouIcons),
                    isPillLight = isPillLight,
                    customSubtheme = isCustom,
                    elementColors = settings.elementColors,
                    syncColors = settings.syncColors,
                    keepSystemG = settings.keepSystemG
                )
            } else {
                // MATERIAL DESIGN BAR: Dual-layer accent rim, companion circle button
                val lockBlack = settings.lockBlack
                val isDarkForWidget = if (lockBlack) true else isDark
                val effectiveWidgetColors = if (isDarkForWidget != isDark) {
                    DynamicThemeDetector.resolveWidgetColors(context, isDarkForWidget)
                } else {
                    widgetColors
                }

                val rimColor = when (settings.subtheme) {
                    "Custom" -> actualCustomColor
                    "Material" -> effectiveWidgetColors.primaryContainer
                    else -> effectiveWidgetColors.primary
                }

                val pillColor = if (lockBlack) {
                    "#121212".toColorInt()
                } else {
                    if (isCustom) actualCustomColor else effectiveWidgetColors.surfaceContainer
                }

                val circleColor = pillColor
                val rimAlphaInt = effectiveColorAlphaInt
                val pillAlphaInt = if (lockBlack) 255 else (if (isCustom) effectiveColorAlphaInt else containerAlphaInt)
                val circleAlphaInt = pillAlphaInt

                val rimColorOpaque = Color.rgb(Color.red(rimColor), Color.green(rimColor), Color.blue(rimColor))
                val pillColorOpaque = Color.rgb(Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
                val circleColorOpaque = Color.rgb(Color.red(circleColor), Color.green(circleColor), Color.blue(circleColor))

                val isPillLight = if (isCustom) (!lockBlack && (customColorLuminance > 0.5)) else (!isDark && !lockBlack)

                val gIconRes = when {
                    effectiveIconTheme == "System G Icon" || (effectiveIconTheme == "Custom G Icon" && settings.keepSystemG) -> R.drawable.ic_g_logo_colored
                    else -> R.drawable.ic_g_logo
                }

                val (rawPColor, rawSColor, rawTColor) = if (isCustom) {
                    val m3Colors = MaterialYouPaletteHelper.getMaterialYouTonalColors(
                        hue = settings.hue,
                        saturation = customSat * 100f,
                        isDarkSurface = !isPillLight
                    )
                    Triple(m3Colors.primary, m3Colors.secondary, m3Colors.tertiary)
                } else {
                    Triple(effectiveWidgetColors.primary, effectiveWidgetColors.secondary, effectiveWidgetColors.tertiary)
                }

                val (themePColor, themeSColor, themeTColor) = if (!lockBlack && isPillLight) {
                    Triple(
                        MaterialYouPaletteHelper.darkenForContrast(rawPColor, 32.0),
                        MaterialYouPaletteHelper.darkenForContrast(rawSColor, 34.0),
                        MaterialYouPaletteHelper.darkenForContrast(rawTColor, 32.0)
                    )
                } else {
                    Triple(rawPColor, rawSColor, rawTColor)
                }

                WidgetDesignConfig(
                    isMaterialYou = true,
                    rimColorOpaque = rimColorOpaque,
                    rimAlphaInt = rimAlphaInt,
                    pillColorOpaque = pillColorOpaque,
                    pillAlphaInt = pillAlphaInt,
                    circleColorOpaque = circleColorOpaque,
                    circleAlphaInt = circleAlphaInt,
                    showGIcon = settings.showGIcon,
                    showDoodles = settings.showDoodles,
                    gIconRes = gIconRes,
                    themePColor = themePColor,
                    themeSColor = themeSColor,
                    themeTColor = themeTColor,
                    effectiveIconTheme = effectiveIconTheme,
                    actionIconStr = settings.actionIcon,
                    allActiveItems = activeItems(context, settings, R.drawable.ic_mic, useMaterialYouIcons = true),
                    isPillLight = isPillLight,
                    customSubtheme = isCustom,
                    elementColors = settings.elementColors,
                    syncColors = settings.syncColors,
                    keepSystemG = settings.keepSystemG
                )
            }
        }

        /** The slots the widget shows, in the user's order: the mic when voice is on, and each shortcut that is set. */
        private fun activeItems(
            context: Context,
            settings: WidgetDesignSettings,
            micIcon: Int,
            useMaterialYouIcons: Boolean
        ): List<ActiveShortcutItem> {
            val shortcuts = mapOf("shortcut1" to settings.shortcut1, "shortcut2" to settings.shortcut2, "shortcut3" to settings.shortcut3)
            val items = mutableListOf<ActiveShortcutItem>()
            for (key in settings.slotOrder.split(",").filter { it.isNotBlank() }) {
                if (key == "mic") {
                    if (settings.showVoice) items.add(ActiveShortcutItem("mic", "mic", micIcon, getVoiceSearchIntent(context)))
                } else {
                    val name = shortcuts[key] ?: continue
                    if (name != "None") {
                        items.add(ActiveShortcutItem(key, name, getShortcutIconRes(name, useMaterialYouIcons), getShortcutIntent(context, name)))
                    }
                }
            }
            return items
        }

        private fun buildRemoteViews(
            context: Context,
            appWidgetId: Int,
            prefs: SharedPreferences,
            maxShortcuts: Int,
            config: WidgetDesignConfig,
            doodle: Occasion? = null
        ): RemoteViews {
            val layoutId = if (config.isMaterialYou) R.layout.widget_search else R.layout.widget_search_colorful
            val views = RemoteViews(context.packageName, layoutId)

            if (config.isMaterialYou) {
                views.setViewVisibility(R.id.widget_outer_background, View.VISIBLE)
                views.setColorStateList(R.id.widget_outer_background, "setImageTintList", ColorStateList.valueOf(config.rimColorOpaque))
                views.setInt(R.id.widget_outer_background, "setImageAlpha", config.rimAlphaInt)
            } else {
                views.setViewVisibility(R.id.widget_outer_background, View.GONE)
            }

            views.setContentDescription(R.id.widget_outer_background, null)
            views.setContentDescription(R.id.widget_pill_background, null)
            views.setContentDescription(R.id.widget_sound_background, null)

            views.setColorStateList(R.id.widget_pill_background, "setImageTintList", ColorStateList.valueOf(config.pillColorOpaque))
            views.setInt(R.id.widget_pill_background, "setImageAlpha", config.pillAlphaInt)

            views.setColorStateList(R.id.widget_sound_background, "setImageTintList", ColorStateList.valueOf(config.circleColorOpaque))
            views.setInt(R.id.widget_sound_background, "setImageAlpha", config.circleAlphaInt)

            bindGIcon(views, config, context, doodle)

            val activeItems = config.allActiveItems.take(maxShortcuts)
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

                    // Dynamic TalkBack Accessibility Content Description
                    val contentDesc = getShortcutDescription(context, item.actionName, prefs)
                    views.setContentDescription(targetViewId, contentDesc)

                    val hasCustomShortcuts = config.effectiveIconTheme == "Custom G Icon"

                    if (hasCustomShortcuts) {
                        // Each design keeps its own saved colours, so a System and a Material widget on the
                        // same home screen each follow their own customization.
                        val colors = config.elementColors
                        val customScColor = if (config.syncColors) {
                            colors.gIcon
                        } else {
                            when (item.slotKey) {
                                "mic" -> colors.mic
                                "shortcut1" -> colors.shortcut1
                                "shortcut2" -> colors.shortcut2
                                "shortcut3" -> colors.shortcut3
                                else -> config.themePColor
                            }
                        }
                        views.setImageViewResource(targetViewId, item.iconRes)
                        views.setColorStateList(targetViewId, "setImageTintList", ColorStateList.valueOf(customScColor))
                        views.setInt(targetViewId, "setImageAlpha", 255)
                    } else when (config.effectiveIconTheme) {
                        "Accented G Icon" -> {
                            views.setImageViewResource(targetViewId, item.iconRes)
                            views.setColorStateList(targetViewId, "setImageTintList", ColorStateList.valueOf(config.themePColor))
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                        "Material G Icon" -> {
                            val themedBitmap = createThemedShortcutBitmap(context, item.iconRes, config.themePColor, config.themeSColor, config.themeTColor, config.isPillLight)
                            if (themedBitmap != null) {
                                views.setImageViewBitmap(targetViewId, themedBitmap)
                                views.setColorStateList(targetViewId, "setImageTintList", null)
                            } else {
                                views.setImageViewResource(targetViewId, item.iconRes)
                                views.setColorStateList(targetViewId, "setImageTintList", ColorStateList.valueOf(config.themePColor))
                            }
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                        else -> { // System G Icon
                            views.setImageViewResource(targetViewId, item.iconRes)
                            val sysTint = if (config.isPillLight) {
                                DynamicThemeDetector.resolveWidgetColors(context, false).onSurface
                            } else {
                                Color.WHITE
                            }
                            views.setColorStateList(targetViewId, "setImageTintList", ColorStateList.valueOf(sysTint))
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                    }

                    val slotCode = SLOT_SHORTCUT_0 + i
                    val targetIntent = Intent(item.intent).apply {
                        identifier = "widget_${appWidgetId}_slot_$slotCode"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val pi = PendingIntent.getActivity(
                        context,
                        getWidgetRequestCode(appWidgetId, slotCode),
                        targetIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        getPendingIntentActivityOptions(context)
                    )
                    views.setOnClickPendingIntent(targetViewId, pi)
                } else {
                    views.setViewVisibility(targetViewId, View.GONE)
                }
            }

            // Tap pill -> main search overlay
            val mainPI = PendingIntent.getActivity(
                context,
                getWidgetRequestCode(appWidgetId, SLOT_PILL),
                mainSearchIntent(context, prefs, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                getPendingIntentActivityOptions(context)
            )
            views.setOnClickPendingIntent(R.id.widget_pill_container, mainPI)
            // On a doodle day, tapping the G searches for the day.
            views.setOnClickPendingIntent(
                R.id.widget_g_logo,
                if (doodle == null || !config.showGIcon) mainPI else PendingIntent.getActivity(
                    context,
                    getWidgetRequestCode(appWidgetId, SLOT_G_LOGO),
                    doodleSearchIntent(context, prefs, appWidgetId, doodle),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    getPendingIntentActivityOptions(context)
                )
            )
            views.setContentDescription(R.id.widget_pill_container, context.getString(R.string.widget_search_description))

            // Tap action button inside search pill
            val actionIntent = Intent(getVoiceActionIntent(context)).apply {
                identifier = "widget_${appWidgetId}_slot_action"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            val actionPI = PendingIntent.getActivity(
                context,
                getWidgetRequestCode(appWidgetId, SLOT_ACTION),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                getPendingIntentActivityOptions(context)
            )
            views.setOnClickPendingIntent(R.id.widget_action_search, actionPI)
            views.setContentDescription(R.id.widget_action_search, context.getString(R.string.widget_talkback_gemini_search))

            // Circle button handling (Material Design bar)
            if (!config.isMaterialYou || config.actionIconStr == "None") {
                views.setViewVisibility(R.id.widget_sound_search, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_sound_search, View.VISIBLE)
                val circleActionIntent = Intent(when (config.actionIconStr) {
                    "Search" -> getGeminiSearchIntent(context)
                    "Assistant", "Voice", "Gemini" -> getGeminiSearchIntent(context)
                    "Now Playing" -> getNowPlayingIntent(context)
                    "Google Lens" -> getLensSearchIntent(context)
                    else -> getGeminiSearchIntent(context)
                }).apply {
                    identifier = "widget_${appWidgetId}_slot_circle"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }

                val circleActionPI = PendingIntent.getActivity(
                    context,
                    getWidgetRequestCode(appWidgetId, SLOT_CIRCLE),
                    circleActionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    getPendingIntentActivityOptions(context)
                )
                views.setOnClickPendingIntent(R.id.widget_sound_search, circleActionPI)
                views.setOnClickPendingIntent(R.id.widget_sound_icon, circleActionPI)

                val circleActionIconRes = when (config.actionIconStr) {
                    "Assistant", "Voice", "Gemini" -> R.drawable.ic_lens_action
                    "Now Playing" -> R.drawable.ic_music
                    else -> R.drawable.ic_search_lens_expressive
                }

                val circleDesc = when (config.actionIconStr) {
                    "Search" -> context.getString(R.string.widget_talkback_gemini_search)
                    "Assistant", "Voice", "Gemini" -> context.getString(R.string.widget_talkback_assistant)
                    "Now Playing" -> context.getString(R.string.widget_talkback_now_playing)
                    "Google Lens" -> context.getString(R.string.widget_talkback_lens)
                    else -> context.getString(R.string.widget_talkback_gemini_search)
                }
                views.setContentDescription(R.id.widget_sound_search, circleDesc)
                views.setContentDescription(R.id.widget_sound_icon, null)

                val hasCustomAction = config.effectiveIconTheme == "Custom G Icon"

                if (hasCustomAction) {
                    val customActColor = if (config.syncColors) config.elementColors.gIcon else config.elementColors.action
                    views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                    views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(customActColor))
                    views.setInt(R.id.widget_sound_icon, "setImageAlpha", 255)
                } else when (config.effectiveIconTheme) {
                    "Accented G Icon" -> {
                        views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(config.themePColor))
                    }
                    "Material G Icon" -> {
                        val themedBitmap = createThemedActionIconBitmap(context, circleActionIconRes, config.themePColor, config.themeSColor, config.themeTColor, config.isPillLight)
                        if (themedBitmap != null) {
                            views.setImageViewBitmap(R.id.widget_sound_icon, themedBitmap)
                            views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", null)
                        } else {
                            views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                            views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(config.themePColor))
                        }
                    }
                    else -> { // System G Icon
                        views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                        val sysActionTint = if (config.isPillLight) {
                            DynamicThemeDetector.resolveWidgetColors(context, false).onSurface
                        } else {
                            Color.WHITE
                        }
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(sysActionTint))
                    }
                }
                views.setInt(R.id.widget_sound_icon, "setImageAlpha", 255)
            }

            return views
        }

        private fun getShortcutDescription(context: Context, key: String, prefs: SharedPreferences? = null): String {
            if (prefs != null) {
                val customType = prefs.getString("${key}_custom_type", "default")
                val customValue = prefs.getString("${key}_custom_value", "")
                if (customType == "app" && !customValue.isNullOrBlank()) {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfoCompat(customValue)
                        val appLabel = pm.getApplicationLabel(appInfo).toString()
                        return context.getString(R.string.widget_talkback_open_app, appLabel)
                    } catch (_: Throwable) {}
                } else if (customType == "url" && !customValue.isNullOrBlank()) {
                    val host = try {
                        customShortcutUri(customValue).host ?: customValue
                    } catch (_: Throwable) {
                        customValue
                    }
                    return context.getString(R.string.widget_talkback_open_web, host)
                }
            }

            return when (key) {
                "mic", "Voice Search" -> context.getString(R.string.widget_talkback_voice_search)
                "Google Lens" -> context.getString(R.string.widget_talkback_lens)
                "Assistant", "Live", "Gemini" -> context.getString(R.string.widget_talkback_assistant)
                "Search" -> context.getString(R.string.widget_talkback_gemini_search)
                "Song Search", "Now Playing" -> context.getString(R.string.widget_talkback_now_playing)
                "Translate (text)" -> context.getString(R.string.widget_talkback_translate_text)
                "Translate (camera)" -> context.getString(R.string.widget_talkback_translate_camera)
                "Weather" -> context.getString(R.string.widget_talkback_weather)
                "Sports" -> context.getString(R.string.widget_talkback_sports)
                "Dictionary" -> context.getString(R.string.widget_talkback_dictionary)
                "Homework" -> context.getString(R.string.widget_talkback_homework)
                "Finance" -> context.getString(R.string.widget_talkback_finance)
                "Saved" -> context.getString(R.string.widget_talkback_saved)
                "News" -> context.getString(R.string.widget_talkback_news)
                else -> key
            }
        }

        /**
         * What tapping the search bar opens: the search overlay, or the Google app's search while the overlay is
         * off, with [query] typed in when there is one.
         */
        internal fun mainSearchIntent(context: Context, prefs: SharedPreferences, appWidgetId: Int, query: String? = null): Intent {
            val intent = if (prefs[SettingsManager.SEARCH_OVERLAY_ENABLED]) {
                Intent(context, WidgetActivity::class.java).apply {
                    identifier = "widget_${appWidgetId}_slot_${if (query == null) "pill" else "doodle"}"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
            } else {
                Intent("android.search.action.GLOBAL_SEARCH").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }.takeIf { context.packageManager.resolveActivityCompat(it) != null }
                    ?: Intent(Intent.ACTION_WEB_SEARCH).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            }
            if (query != null) intent.putExtra(SearchManager.QUERY, query)
            return intent
        }

        /**
         * Tapping a doodle opens the web search for its day straight away, as tapping one of Google's does, with the
         * search engine chosen in settings. If that can't open (Tor without Tor Browser), the search bar opens with
         * the day typed in instead.
         */
        internal fun doodleSearchIntent(context: Context, prefs: SharedPreferences, appWidgetId: Int, doodle: Occasion): Intent {
            val day = context.getString(doodle.title)
            val launch = webSearchLaunch(context, prefs[SettingsManager.SEARCH_ENGINE], prefs[SettingsManager.CUSTOM_SEARCH_ENGINE_URL], day)
            return (launch as? SafeWebLaunch.Launch.Open)?.intent ?: mainSearchIntent(context, prefs, appWidgetId, day)
        }

        /**
         * Sets the G as the design asks, and on a doodle day plays the day's doodle around it (in the G's 48dp slot,
         * see the widget layouts), with TalkBack reading the day.
         */
        private fun bindGIcon(views: RemoteViews, config: WidgetDesignConfig, context: Context, doodle: Occasion?) {
            val showGIcon = config.showGIcon
            val materialGIconTheme = config.effectiveIconTheme
            val pColor = config.themePColor
            val isPillLight = config.isPillLight
            views.setViewVisibility(R.id.widget_g_slot, if (showGIcon) View.VISIBLE else View.GONE)
            views.setInt(R.id.widget_g_logo, "setImageAlpha", 255)
            views.removeAllViews(R.id.widget_doodle)
            if (showGIcon && doodle != null) {
                views.addView(R.id.widget_doodle, RemoteViews(context.packageName, doodle.layout))
                views.setViewVisibility(R.id.widget_doodle, View.VISIBLE)
                val day = context.getString(doodle.title)
                views.setContentDescription(R.id.widget_g_logo, context.getString(R.string.widget_talkback_doodle, day))
            } else {
                views.setViewVisibility(R.id.widget_doodle, View.GONE)
                views.setContentDescription(R.id.widget_g_logo, context.getString(R.string.widget_talkback_google_logo))
            }

            if (config.keepSystemG || materialGIconTheme == "System G Icon") {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo_colored)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            } else if (materialGIconTheme == "Material G Icon") {
                val gBitmap = createCustomMaterialGBitmap(pColor, config.themeSColor, config.themeTColor, isPillLight, context)
                if (gBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, gBitmap)
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", ColorStateList.valueOf(pColor))
                }
            } else if (materialGIconTheme == "Custom G Icon") {
                val customGColor = config.elementColors.gIcon
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", ColorStateList.valueOf(customGColor))
            } else if (materialGIconTheme == "Accented G Icon") {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", ColorStateList.valueOf(pColor))
            } else {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo_colored)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            }
        }

    }
}
