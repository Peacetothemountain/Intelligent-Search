package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.util.getApplicationInfoCompat
import com.pixel.intelligentsearch.core.util.resolveActivityCompat
import com.pixel.intelligentsearch.di.appEntryPoint
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
import android.app.PendingIntent
import android.app.SearchManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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

            // The launcher's own search bar follows the design saved in Widget Customization. Pixel Launcher binds
            // it with no options, so it is known only by its provider (see SearchBoxWidgetProvider).
            if (providerClassName == SearchBoxWidgetProvider::class.java.name) {
                return isMaterialStyle(widgetThemeStyle)
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
                return isMaterialStyle(widgetThemeStyle)
            }

            // 3. Regular home screen widget from Android Widget Selector (SearchWidgetProvider):
            if (providerClassName == SearchWidgetProvider::class.java.name) {
                return false
            }

            // 4. Fallback when neither provider info nor host category is available (e.g. unit tests):
            return isMaterialStyle(widgetThemeStyle)
        }

        /** Whether [widgetThemeStyle] (the design last saved in Widget Customization) is the Material design. */
        internal fun isMaterialStyle(widgetThemeStyle: String?): Boolean =
            widgetThemeStyle == "Material You (Minimal)" || widgetThemeStyle == "Material Design"

        // Slot identifiers for bit-shifted unique request codes
        private const val SLOT_PILL = 0
        private const val SLOT_G_LOGO = 1
        private const val SLOT_CIRCLE = 3
        private const val SLOT_SHORTCUT_0 = 4

        internal fun getWidgetRequestCode(appWidgetId: Int, slot: Int): Int {
            return (appWidgetId shl 4) or (slot and 0x0F)
        }

        internal fun getPendingIntentActivityOptions(context: Context): Bundle? {
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

        /**
         * Redraws every widget the app has: the search bars of each design and the countdown widgets, which are drawn
         * from the same saved design and theme, so a design saved in Widget Customization or a theme change reaches
         * them all. Then the Android 15+ widget picker's previews.
         */
        fun updateAllWidgets(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
            val appContext = context.applicationContext
            context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(appContext) ?: return@launch
                    val prefs = SettingsManager.storedPrefs(appContext)
                    val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                        .isDark(ThemeMode.systemIsDark(appContext.resources.configuration))
                    val widgetColors = DynamicThemeDetector.resolveWidgetColors(appContext, isDark)

                    val providers = listOf(
                        SearchWidgetProvider::class.java,
                        SearchWidgetMaterialProvider::class.java,
                        SearchBoxWidgetProvider::class.java
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
                    try {
                        CountdownWidgetProvider.redrawAll(appContext, appWidgetManager)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Couldn't redraw the countdown widgets", e)
                    }

                    // Android 15+ (API 35) / Android 16 / Android 17 (Canary ZP11 / API 37)
                    // Push dynamic personalized previews to system widget picker
                    if (Build.VERSION.SDK_INT >= 35) {
                        updateWidgetPickerPreviews(appContext, prefs, isDark, widgetColors) { provider, preview ->
                            appWidgetManager.setWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, preview)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update all widgets asynchronously", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        /**
         * What each widget's picker preview was drawn from when the picker last took it, by provider class name. Kept
         * in memory only, so a new process (after an app update, say) pushes the previews once more.
         */
        internal val publishedPreviews = ConcurrentHashMap<String, List<Any?>>()

        /**
         * Gives the Android 15+ widget picker a preview of each widget as the user has styled it. The picker takes
         * only a few previews an hour and refuses the rest, so a widget's preview is pushed only when what it's drawn
         * from has changed since the picker last took one, and a refused one is pushed again on the next redraw.
         * [publish] hands a preview to the picker and says whether it was taken.
         */
        internal fun updateWidgetPickerPreviews(
            context: Context,
            prefs: SharedPreferences,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors,
            publish: (ComponentName, RemoteViews) -> Boolean
        ) {
            for (design in WidgetDesign.entries) {
                val isMaterialYou = design == WidgetDesign.MATERIAL
                val provider = if (isMaterialYou) SearchWidgetMaterialProvider::class.java else SearchWidgetProvider::class.java
                try {
                    val inputs = previewInputs(context, prefs, design, isDark, widgetColors)
                    if (publishedPreviews[provider.name] == inputs) continue
                    // No doodle: the picker keeps its preview long after the holiday is over.
                    val preview = buildPickerPreview(context, prefs, isMaterialYou, isDark, widgetColors)
                    if (publish(ComponentName(context, provider), preview)) {
                        publishedPreviews[provider.name] = inputs
                    } else {
                        Log.w(TAG, "The widget picker refused the ${provider.simpleName} preview (rate limited); pushing it again next redraw")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Couldn't update the ${provider.simpleName} widget picker preview", e)
                }
            }
            // The countdown widget's: a sample countdown in the design it would be placed in, the search bar's.
            val countdown = CountdownWidgetProvider::class.java
            try {
                val design = if (isMaterialStyle(prefs[SettingsManager.WIDGET_THEME_STYLE])) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
                val inputs = previewInputs(context, prefs, design, isDark, widgetColors) + design
                if (publishedPreviews[countdown.name] != inputs) {
                    if (publish(ComponentName(context, countdown), CountdownWidgetProvider.pickerPreview(context, prefs))) {
                        publishedPreviews[countdown.name] = inputs
                    } else {
                        Log.w(TAG, "The widget picker refused the countdown preview (rate limited); pushing it again next redraw")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Couldn't update the countdown widget picker preview", e)
            }
        }

        /** Everything a picker preview of [design] is drawn from, so an unchanged widget isn't pushed again. */
        private fun previewInputs(
            context: Context,
            prefs: SharedPreferences,
            design: WidgetDesign,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors
        ): List<Any?> = listOf(
            WidgetSettingsStore(prefs).load(design, WidgetDefaults(widgetColors.primary, widgetColors.isDynamicActive)),
            isDark,
            widgetColors,
            context.resources.displayMetrics.densityDpi,
            // A shortcut set to open an app or a website is described by that app or site.
            prefs.all.filterKeys { it.endsWith("_custom_type") || it.endsWith("_custom_value") }
        )

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

        internal data class ActiveShortcutItem(
            val slotKey: String,
            val actionName: String,
            val iconRes: Int,
            val intent: Intent
        )

        /**
         * Encapsulates all resolved design and dynamic color attributes for widget rendering.
         */
        internal data class WidgetDesignConfig(
            val isMaterialYou: Boolean,
            val rimColorOpaque: Int,
            val pillColorOpaque: Int,
            val circleColorOpaque: Int,
            /** How see-through the outline, pill and circle are drawn, as one group ([WidgetColorRules.backdropAlphas]). */
            val backdrop: WidgetColorRules.BackdropAlphas,
            val showGIcon: Boolean,
            /** Whether the G is Google's four-color one: the System G style, or the Custom style with Keep System G. */
            val useOriginalG: Boolean,
            /** The Accented G style's G, mic, shortcuts and circle icon. */
            val themePColor: Int,
            /** What the Material G style's icons are worked out from, the search overlay's way ([SearchIconColors]). */
            val iconAccents: SearchIconColors.Accents,
            val effectiveIconTheme: String,
            val actionIconStr: String,
            val allActiveItems: List<ActiveShortcutItem>,
            val isPillLight: Boolean,
            /** The Custom G style's color for each element as drawn (see [WidgetColorRules.customIconColors]). */
            val customIcons: WidgetElementColors,
            /** The Custom subtheme's bar, themed from its seed; null for the theme-colored subthemes. */
            val customBar: WidgetColorRules.CustomBarColors? = null,
            /** The bar's outline, height and corners. */
            val shape: WidgetBarShape,
            /** The System G style's plain icon color on this pill. */
            val plainIconTint: Int
        )

        fun updateWidgetsSync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            forcedIsMaterial: Boolean? = null
        ) {
            val prefs = SettingsManager.storedPrefs(context)
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
            prefs: SharedPreferences = SettingsManager.storedPrefs(context)
        ): Map<SizeF, RemoteViews> {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                .isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val widgetColors = DynamicThemeDetector.resolveWidgetColors(context, isDark)
            return buildResponsiveViews(context, prefs, appWidgetId, isMaterialYou, isDark, widgetColors)
        }

        /** [buildResponsiveViews] in the given theme; [withDoodle] false leaves today's doodle out (for previews). */
        private fun buildResponsiveViews(
            context: Context,
            prefs: SharedPreferences,
            appWidgetId: Int,
            isMaterialYou: Boolean,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors,
            withDoodle: Boolean = true
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
            val doodle = if (withDoodle) currentDoodle(prefs, isMaterialYou) else null
            // Strictly positive SizeF keys compliant with Android 12+ RemoteViews validation:
            // SizeF(0f, 0f) is forbidden by AOSP RemoteViews constructor.
            return listOf(ULTRA_COMPACT_WIDTH, COMPACT_WIDTH, STANDARD_WIDTH, EXPANDED_WIDTH).associateWith { width ->
                buildRemoteViews(
                    context = context,
                    appWidgetId = appWidgetId,
                    prefs = prefs,
                    maxShortcuts = shortcutsAt(width, config),
                    config = config,
                    doodle = doodle
                )
            }
        }

        /** How many shortcuts fit beside the G at [width]; the Material design's circle icon takes one place. */
        private fun shortcutsAt(width: SizeF, config: WidgetDesignConfig): Int {
            val hasCircle = config.isMaterialYou && (config.actionIconStr != "None")
            return when (width) {
                ULTRA_COMPACT_WIDTH -> if (hasCircle) 0 else 1
                COMPACT_WIDTH -> if (hasCircle) 1 else 2
                STANDARD_WIDTH -> if (hasCircle) 2 else 3
                else -> 4
            }
        }

        /** The doodle the G of the given design wears today, if any and if the user shows doodles. */
        private fun currentDoodle(prefs: SharedPreferences, isMaterialYou: Boolean): Occasion? {
            val design = if (isMaterialYou) WidgetDesign.MATERIAL else WidgetDesign.SYSTEM
            return if (WidgetSettingsStore(prefs).showsDoodle(design)) WidgetDoodles.current(prefs) else null
        }

        /**
         * The widget of the given design as Widget Customization's live preview shows it, for the launcher's
         * add-widget dialog when the widget is pinned from there: drawn from the saved settings (the editor saves its
         * design first) exactly as a placed widget draws itself, and responsive like one, so the dialog shows the
         * size the widget will be placed at. A 4-cell phone widget is wide enough for every shortcut the editor's
         * preview shows; the older single preview was always the narrow 4-cell layout, which dropped shortcuts the
         * editor and the placed widget both show. Like the editor's preview it has no doodle, which only lasts the
         * day. Built on a background thread: it draws bitmaps.
         */
        internal fun buildPinPreview(
            context: Context,
            isMaterialYou: Boolean
        ): RemoteViews = RemoteViews(pinPreviewSizes(context, isMaterialYou))

        /** The views [buildPinPreview] shows at each width: a placed widget's, without the doodle. */
        internal fun pinPreviewSizes(
            context: Context,
            isMaterialYou: Boolean,
            prefs: SharedPreferences = SettingsManager.storedPrefs(context)
        ): Map<SizeF, RemoteViews> {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME])
                .isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val widgetColors = DynamicThemeDetector.resolveWidgetColors(context, isDark)
            return buildResponsiveViews(context, prefs, appWidgetId = 0, isMaterialYou, isDark, widgetColors, withDoodle = false)
        }

        /** A placed widget's [STANDARD_WIDTH] views without the doodle, for the widget picker's preview. */
        private fun buildPickerPreview(
            context: Context,
            prefs: SharedPreferences,
            isMaterialYou: Boolean,
            isDark: Boolean,
            widgetColors: DynamicThemeDetector.ResolvedWidgetColors
        ): RemoteViews {
            val config = resolveWidgetConfig(context, prefs, isMaterialYou, isDark, widgetColors)
            return buildRemoteViews(
                context = context,
                appWidgetId = 0,
                prefs = prefs,
                maxShortcuts = shortcutsAt(STANDARD_WIDTH, config),
                config = pickerPreviewConfig(config, editorPageColor(context, isDark))
            )
        }

        /**
         * The bar as the picker shows it: exactly as Widget Customization's preview draws it. That preview stands on
         * the editor's page; the picker would show its own lighter card through every see-through part of the bar (the
         * outline at any Transparency, the whole bar unlocked), so the same bar looked different in the two places. So
         * here each see-through part is drawn already blended over the editor's page color [page], fully opaque:
         * the outline at its own opacity within the bar's, the pill and the circle at the bar's.
         */
        internal fun pickerPreviewConfig(config: WidgetDesignConfig, page: Int): WidgetDesignConfig {
            val alphas = config.backdrop
            return config.copy(
                rimColorOpaque = WidgetColorRules.blendOver(config.rimColorOpaque, alphas.backdrop * alphas.rim, page),
                pillColorOpaque = WidgetColorRules.blendOver(config.pillColorOpaque, alphas.backdrop, page),
                circleColorOpaque = WidgetColorRules.blendOver(config.circleColorOpaque, alphas.backdrop, page),
                backdrop = WidgetColorRules.BackdropAlphas(backdrop = 1f, rim = 1f)
            )
        }

        /** The page color Widget Customization's preview stands on: the app theme's background, as the editor draws it. */
        internal fun editorPageColor(context: Context, isDark: Boolean): Int = runCatching<Int> {
            val scheme = if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
            android.graphics.Color.valueOf(scheme.background.red, scheme.background.green, scheme.background.blue).toArgb()
        }.getOrDefault(if (isDark) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())

        /**
         * Everything the search bar of the Material ([isMaterialYou]) or System design is drawn in: its colors, outline
         * and corners from the design saved in Widget Customization, light or dark as the app's theme is now. The
         * countdown widget is drawn in the same, so it always matches the bar.
         */
        internal fun resolveWidgetConfig(context: Context, prefs: SharedPreferences, isMaterialYou: Boolean): WidgetDesignConfig {
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(ThemeMode.systemIsDark(context.resources.configuration))
            return resolveWidgetConfig(context, prefs, isMaterialYou, isDark, DynamicThemeDetector.resolveWidgetColors(context, isDark))
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
            val backdrop = WidgetColorRules.backdropAlphas(settings, isMaterialYou)
            val customBar = if (isCustom) WidgetColorRules.customBarColors(settings, isMaterialYou) else null
            val actualCustomColor = settings.customColorInt
            val effectiveIconTheme = settings.gIconTheme
            // Keep System G is a choice within the Custom G style only, as the editor's preview shows.
            val useOriginalG = effectiveIconTheme == "System G Icon" || (effectiveIconTheme == "Custom G Icon" && settings.keepSystemG)

            return if (!isMaterialYou) {
                // SYSTEM DESIGN BAR: Colorful Google branding, no outer accent rim
                val pillColor = when (settings.subtheme) {
                    "Light" -> 0xFFF1F3F4.toInt()
                    "Dark" -> 0xFF303134.toInt()
                    "Custom" -> actualCustomColor
                    else -> widgetColors.surfaceContainer
                }
                val isPillLight = when (settings.subtheme) {
                    "Light" -> true
                    "Dark" -> false
                    "Custom" -> customBar?.darkIcons == true
                    else -> !isDark
                }

                // The theme's accent for the pill's shade: the Light and Dark pills keep it whatever the theme.
                val themePColor = customBar?.accent
                    ?: (if (isPillLight == !isDark) widgetColors else DynamicThemeDetector.resolveWidgetColors(context, !isPillLight)).primary
                val iconAccents = WidgetColorRules.materialIconAccents(settings, isPillLight) { SearchIconColors.themeAccents(context, it) }

                val useMaterialYouIcons = WidgetColorRules.usesMaterialYouIcons(false, effectiveIconTheme, isPillLight, settings.subtheme)
                val micIcon = if (useMaterialYouIcons) R.drawable.ic_mic else R.drawable.ic_mic_original
                val pillColorOpaque = Color.rgb(Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))

                WidgetDesignConfig(
                    isMaterialYou = false,
                    // The optional outline is the accent, as see-through as the pill: the backdrop fades both.
                    rimColorOpaque = themePColor or Color.BLACK,
                    pillColorOpaque = pillColorOpaque,
                    circleColorOpaque = pillColorOpaque,
                    backdrop = backdrop,
                    showGIcon = settings.showGIcon,
                    useOriginalG = useOriginalG,
                    themePColor = themePColor,
                    iconAccents = iconAccents,
                    effectiveIconTheme = effectiveIconTheme,
                    actionIconStr = "None",
                    allActiveItems = activeItems(context, settings, micIcon, useMaterialYouIcons),
                    isPillLight = isPillLight,
                    customIcons = WidgetColorRules.customIconColors(settings, isMaterialDesign = false, isPillLight = isPillLight),
                    customBar = customBar,
                    shape = WidgetBarShape.of(settings, isMaterialDesign = false),
                    plainIconTint = plainIconTint(context, customBar, isPillLight)
                )
            } else {
                // MATERIAL DESIGN BAR: Dual-layer accent rim, companion circle button
                // Inner Pill & Circle forces the pill and circle to one color; whether it is light or dark decides the
                // theme the rim and icons are drawn in, as the theme's own light or dark pill does.
                val lockBlack = settings.lockBlack
                val lockDark = WidgetColorRules.locksDark(settings)
                val lockLight = WidgetColorRules.locksLight(settings)
                val isDarkForWidget = if (lockBlack) lockDark else isDark
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
                    WidgetColorRules.lockedPill(settings)
                } else {
                    if (isCustom) actualCustomColor else effectiveWidgetColors.surfaceContainer
                }

                val circleColor = pillColor

                val rimColorOpaque = Color.rgb(Color.red(rimColor), Color.green(rimColor), Color.blue(rimColor))
                val pillColorOpaque = Color.rgb(Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
                val circleColorOpaque = Color.rgb(Color.red(circleColor), Color.green(circleColor), Color.blue(circleColor))

                val isPillLight = customBar?.darkIcons ?: if (lockBlack) lockLight else !isDark

                // The Custom subtheme's accent already keeps its contrast on the pill; the theme's is darkened on a
                // light one.
                val themePColor = customBar?.accent
                    ?: WidgetColorRules.iconColor(effectiveWidgetColors.primary, true, lockDark, isPillLight, targetTone = 32.0)
                val iconAccents = WidgetColorRules.materialIconAccents(settings, isPillLight) { SearchIconColors.themeAccents(context, it) }

                WidgetDesignConfig(
                    isMaterialYou = true,
                    rimColorOpaque = rimColorOpaque,
                    pillColorOpaque = pillColorOpaque,
                    circleColorOpaque = circleColorOpaque,
                    backdrop = backdrop,
                    showGIcon = settings.showGIcon,
                    useOriginalG = useOriginalG,
                    themePColor = themePColor,
                    iconAccents = iconAccents,
                    effectiveIconTheme = effectiveIconTheme,
                    actionIconStr = settings.actionIcon,
                    allActiveItems = activeItems(context, settings, R.drawable.ic_mic, useMaterialYouIcons = true),
                    isPillLight = isPillLight,
                    customIcons = WidgetColorRules.customIconColors(settings, isMaterialDesign = true, isPillLight = isPillLight),
                    customBar = customBar,
                    shape = WidgetBarShape.of(settings, isMaterialDesign = true),
                    plainIconTint = plainIconTint(context, customBar, isPillLight)
                )
            }
        }

        /**
         * The System G style's color for the mic, shortcuts and circle icon (its G stays Google's): the Custom
         * subtheme's plain icon, else the light theme's on-surface on a light pill and white on a dark one.
         */
        private fun plainIconTint(context: Context, customBar: WidgetColorRules.CustomBarColors?, isPillLight: Boolean): Int =
            customBar?.plainIcon ?: if (isPillLight) DynamicThemeDetector.resolveWidgetColors(context, false).onSurface else Color.WHITE

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

            bindShape(views, context, config)
            bindBackdrop(views, config)

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
                        // Each design keeps its own saved colors, so a System and a Material widget on the
                        // same home screen each follow their own customization.
                        val colors = config.customIcons
                        val customScColor = when (item.slotKey) {
                            "mic" -> colors.mic
                            "shortcut1" -> colors.shortcut1
                            "shortcut2" -> colors.shortcut2
                            "shortcut3" -> colors.shortcut3
                            else -> config.themePColor
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
                            val a = config.iconAccents
                            val themedBitmap = createThemedShortcutBitmap(context, item.iconRes, a.primary, a.secondary, config.isPillLight, a.tertiary)
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
                            views.setColorStateList(targetViewId, "setImageTintList", ColorStateList.valueOf(config.plainIconTint))
                            views.setInt(targetViewId, "setImageAlpha", 255)
                        }
                    }

                    val slotCode = SLOT_SHORTCUT_0 + i
                    val targetIntent = Intent(item.intent).apply {
                        identifier = "widget_${appWidgetId}_slot_$slotCode"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val pi = overlayPendingIntent(context, getWidgetRequestCode(appWidgetId, slotCode), targetIntent)
                    views.setOnClickPendingIntent(targetViewId, pi)
                } else {
                    views.setViewVisibility(targetViewId, View.GONE)
                }
            }

            // Tap pill -> main search overlay
            val mainPI = overlayPendingIntent(context, getWidgetRequestCode(appWidgetId, SLOT_PILL), mainSearchIntent(context, prefs, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_pill_container, mainPI)
            // On a doodle day, tapping the G searches for the day.
            views.setOnClickPendingIntent(
                R.id.widget_g_logo,
                if (doodle == null || !config.showGIcon) mainPI else overlayPendingIntent(
                    context,
                    getWidgetRequestCode(appWidgetId, SLOT_G_LOGO),
                    doodleSearchIntent(context, prefs, appWidgetId, doodle)
                )
            )
            views.setContentDescription(R.id.widget_pill_container, context.getString(R.string.widget_search_description))

            // Circle button handling (Material Design bar). Its background, in the backdrop, shows with it.
            if (!config.isMaterialYou || config.actionIconStr == "None") {
                views.setViewVisibility(R.id.widget_sound_search, View.GONE)
                views.setViewVisibility(R.id.widget_sound_background, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_sound_search, View.VISIBLE)
                views.setViewVisibility(R.id.widget_sound_background, View.VISIBLE)
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

                val circleActionPI = overlayPendingIntent(context, getWidgetRequestCode(appWidgetId, SLOT_CIRCLE), circleActionIntent)
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
                    val customActColor = config.customIcons.action
                    views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                    views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(customActColor))
                    views.setInt(R.id.widget_sound_icon, "setImageAlpha", 255)
                } else when (config.effectiveIconTheme) {
                    "Accented G Icon" -> {
                        views.setImageViewResource(R.id.widget_sound_icon, circleActionIconRes)
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(config.themePColor))
                    }
                    "Material G Icon" -> {
                        val a = config.iconAccents
                        val themedBitmap = createThemedActionIconBitmap(context, circleActionIconRes, a.primary, a.secondary, config.isPillLight, a.tertiary)
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
                        views.setColorStateList(R.id.widget_sound_icon, "setImageTintList", ColorStateList.valueOf(config.plainIconTint))
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
                "Calculator" -> context.getString(R.string.widget_talkback_calculator)
                "Discover" -> context.getString(R.string.widget_talkback_discover)
                else -> key
            }
        }

        /**
         * A tap on the search bar that opens [intent]. With Pixel Launcher as the home screen it goes through
         * [WidgetLaunchReceiver], so the launcher's widget launch animation never plays (a copy of the bar as a lavender
         * bar, a black fill, square boxes over the rounded bar, the bar sliding back in on the way home); with any other
         * launcher it stays an activity tap, which always opens.
         */
        internal fun overlayPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            if (WidgetLaunchReceiver.homeIsPixelLauncher(context)) {
                WidgetLaunchReceiver.pendingIntent(context, requestCode, intent)
            } else {
                PendingIntent.getActivity(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    getPendingIntentActivityOptions(context)
                )
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
                    setPackage(com.pixel.intelligentsearch.core.system.GoogleApps.GOOGLE_APP)
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
         * Gives the bar the design's outline and corners ([WidgetBarShape]): the bar's height, the outline (the
         * Material design's rim, the System design's optional outline) and the rounded backgrounds of the outline, the
         * pill and the circle, each drawn with its radius ([WidgetBarShape.background]). The pill keeps its height,
         * so the buttons keep their touch targets; the outline grows the bar around it.
         *
         * Every shape is set on every redraw, whatever it is: a launcher redraws a placed widget by applying the new
         * views onto the ones it already shows, so a background left unset keeps the previous design's corners (the
         * circle stayed square after going back to fully round).
         */
        private fun bindShape(views: RemoteViews, context: Context, config: WidgetDesignConfig) {
            val shape = config.shape
            views.setViewLayoutHeight(android.R.id.background, shape.height.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewVisibility(R.id.widget_outer_background, if (shape.outline > 0) View.VISIBLE else View.GONE)
            views.setImageViewResource(R.id.widget_outer_background, WidgetBarShape.background(shape.outerRadius))
            // The backdrop's pill and circle lie exactly under the buttons they back, so both get the same insets.
            if (config.isMaterialYou) {
                // As the layout's padding is read: rounded to whole pixels.
                val inset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, shape.outline.toFloat(), context.resources.displayMetrics).roundToInt()
                for (row in listOf(R.id.widget_bar_row, R.id.widget_backdrop_row)) views.setViewPadding(row, inset, inset, inset, inset)
                // A 48dp square with 24dp corners is the round circle.
                views.setImageViewResource(R.id.widget_sound_background, WidgetBarShape.background(shape.circleRadius))
            } else {
                val inset = shape.outline.toFloat()
                for (id in listOf(R.id.widget_pill_container, R.id.widget_pill_background)) {
                    for (side in listOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM)) {
                        views.setViewLayoutMargin(id, side, inset, TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
            }
            views.setImageViewResource(R.id.widget_pill_background, WidgetBarShape.background(shape.pillRadius))
            // The bar's outer corners, told to the launcher: android:id/background clips to its outline, so the
            // launcher draws no corners of its own and takes the widget's shape from this radius when it grows an app
            // out of the bar or shrinks one back into it. Without it the bar counts as a sharp rectangle, and a square
            // box appears over the rounded pill.
            views.setViewOutlinePreferredRadius(android.R.id.background, shape.outerRadius.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }

        /**
         * Paints the bar's backdrop: the outline, the pill and the circle in their colors, each at its opacity within
         * the backdrop, and the whole backdrop faded once by Transparency ([WidgetColorRules.backdropAlphas]; a pill
         * locked black stays solid and only the rim fades), so where the pill and circle sit over the outline they are
         * neither painted twice nor tinted by it. The icons are
         * outside the backdrop and stay fully opaque. Everything is set on every redraw, as in [bindShape].
         */
        private fun bindBackdrop(views: RemoteViews, config: WidgetDesignConfig) {
            val alphas = config.backdrop
            views.setFloat(R.id.widget_backdrop, "setAlpha", alphas.backdrop)
            views.setColorStateList(R.id.widget_outer_background, "setImageTintList", ColorStateList.valueOf(config.rimColorOpaque))
            views.setInt(R.id.widget_outer_background, "setImageAlpha", WidgetColorRules.alphaInt(alphas.rim))
            views.setColorStateList(R.id.widget_pill_background, "setImageTintList", ColorStateList.valueOf(config.pillColorOpaque))
            views.setInt(R.id.widget_pill_background, "setImageAlpha", 255)
            views.setColorStateList(R.id.widget_sound_background, "setImageTintList", ColorStateList.valueOf(config.circleColorOpaque))
            views.setInt(R.id.widget_sound_background, "setImageAlpha", 255)
            views.setContentDescription(R.id.widget_outer_background, null)
            views.setContentDescription(R.id.widget_pill_background, null)
            views.setContentDescription(R.id.widget_sound_background, null)
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

            if (config.useOriginalG) {
                views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo_colored)
                views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
            } else if (materialGIconTheme == "Material G Icon") {
                val a = config.iconAccents
                val gBitmap = createCustomMaterialGBitmap(a.primary, a.secondary, a.tertiary, isPillLight, context)
                if (gBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_g_logo, gBitmap)
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", null)
                } else {
                    views.setImageViewResource(R.id.widget_g_logo, R.drawable.ic_g_logo)
                    views.setColorStateList(R.id.widget_g_logo, "setImageTintList", ColorStateList.valueOf(pColor))
                }
            } else if (materialGIconTheme == "Custom G Icon") {
                val customGColor = config.customIcons.gIcon
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
