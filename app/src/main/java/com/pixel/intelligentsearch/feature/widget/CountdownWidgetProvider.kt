package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.graphics.ColorUtils
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.widget.CountdownEvent
import com.pixel.intelligentsearch.core.data.widget.CountdownEventStore
import com.pixel.intelligentsearch.core.data.widget.WidgetDesign
import com.pixel.intelligentsearch.core.system.workManagerOnceUnlocked
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.Companion.WidgetDesignConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The countdown widget: the days left until a date the user picks ("9 days · Halloween"), drawn in the search bar's
 * own design, the one saved in Widget Customization (or the design chosen for it when it was set up): the same
 * colors, Transparency, outline and corners, worked out by the search bar's own rules
 * ([SearchWidgetProvider.resolveWidgetConfig]), light or dark as the app's theme is when it's drawn. So it sits next
 * to the bar as one family and follows every change to it.
 *
 * It is responsive ([CountdownSize]): one row high it is a pill like the bar, two rows a tile with the number as its
 * hero, and wider or larger it makes room for the date. It is redrawn just after each local midnight by WorkManager
 * (no exact alarm), when the clock, time zone or language changes, and with the search bars
 * ([SearchWidgetProvider.updateAllWidgets]) whenever the design or the theme changes. Tapping it searches for the
 * event; a widget not set up yet opens its setup ([CountdownConfigActivity]).
 */
@AndroidEntryPoint
class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Exported for the launcher: only the system's own broadcasts trigger anything beyond the widget ones.
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAll(context, goAsync())
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        context.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
            try {
                redraw(context, appWidgetManager, appWidgetIds)
                scheduleMidnight(context)
            } catch (e: Throwable) {
                Log.e(TAG, "Couldn't draw the countdown widgets", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CountdownEventStore(SettingsManager.storedPrefs(context)).remove(appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        runCatching { context.workManagerOnceUnlocked()?.cancelUniqueWork(MIDNIGHT_WORK) }
    }

    /**
     * The layouts the widget has, each for the sizes from [size] up ([RemoteViews] shows the one whose size is the
     * nearest below the widget's): one row high a pill, two rows a tile, wider a tile with the hero beside the words,
     * and larger a tile with the date too.
     */
    internal enum class CountdownSize(val size: SizeF, @param:LayoutRes val layout: Int) {
        PILL(SizeF(100f, 40f), R.layout.widget_countdown_pill),
        TILE(SizeF(100f, 100f), R.layout.widget_countdown_tile),
        WIDE(SizeF(240f, 100f), R.layout.widget_countdown_wide),
        LARGE(SizeF(200f, 240f), R.layout.widget_countdown_tile)
    }

    /** What the widget says: the [hero] (the days, or "Today"), what they are ([unit]), the event and its date. */
    internal data class CountdownTexts(
        val hero: String,
        val unit: String,
        val title: String,
        val date: String,
        /** All of it as TalkBack reads it. */
        val spoken: String
    ) {
        /** Whether the widget is waiting to be set up: it shows a calendar instead of the hero. */
        val isSetUp: Boolean get() = hero.isNotEmpty()
    }

    /**
     * The widget's colors, outline and corners: the search bar's ([WidgetDesignConfig]). The backdrop is the bar's
     * outline (the Material design's rim) around its pill, here the widget's [surface]; the number is the bar's
     * accent (its icons' color), and the words the bar's plain icon color, which both keep their contrast with it.
     */
    internal data class CountdownStyle(
        val rim: Int,
        val surface: Int,
        val backdrop: WidgetColorRules.BackdropAlphas,
        val shape: WidgetBarShape,
        val hero: Int,
        val text: Int,
        val secondaryText: Int
    )

    companion object {
        private const val TAG = "CountdownWidget"
        private const val MIDNIGHT_WORK = "countdown_widget_midnight"
        private val AFTER_MIDNIGHT_MS = TimeUnit.SECONDS.toMillis(30)

        /** How far the words sit inside the pill or the tile's surface, in dp. */
        private const val PILL_INSET = 20
        private const val TILE_INSET = 20

        /** The date and the secondary words: the words' color at Material's medium-emphasis opacity. */
        private const val SECONDARY_ALPHA = 0xC2

        /** The phone's clock; tests set it. */
        internal var now: () -> ZonedDateTime = { ZonedDateTime.now() }

        /** Redraws every countdown widget on a background thread, then [pendingResult] finishes. */
        fun updateAll(context: Context, pendingResult: android.content.BroadcastReceiver.PendingResult? = null) {
            val appContext = context.applicationContext
            appContext.appEntryPoint.applicationScope().launch(Dispatchers.IO) {
                try {
                    AppWidgetManager.getInstance(appContext)?.let { redrawAll(appContext, it) }
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't redraw the countdown widgets", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        /** Redraws every countdown widget now (on a background thread) and arranges the next midnight's redraw. */
        internal fun redrawAll(context: Context, manager: AppWidgetManager) {
            val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
            if (ids.isEmpty()) return
            redraw(context, manager, ids)
            scheduleMidnight(context)
        }

        private fun redraw(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
            val prefs = SettingsManager.storedPrefs(context)
            val events = CountdownEventStore(prefs)
            for (id in appWidgetIds) {
                try {
                    manager.updateAppWidget(id, buildViews(context, id, events.get(id), prefs))
                } catch (e: Throwable) {
                    Log.e(TAG, "Couldn't draw countdown widget $id", e)
                }
            }
        }

        /**
         * Arranges the redraw just after the next local midnight, replacing any arranged before. Skipped before the
         * first unlock after a restart (see [workManagerOnceUnlocked]); the redraw at the unlock arranges it.
         */
        fun scheduleMidnight(context: Context) {
            val current = now()
            val delayMs = Duration.between(current, CountdownEventStore.nextMidnight(current)).toMillis() + AFTER_MIDNIGHT_MS
            val request = OneTimeWorkRequestBuilder<CountdownMidnightWorker>().setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
            runCatching {
                context.workManagerOnceUnlocked()?.enqueueUniqueWork(MIDNIGHT_WORK, ExistingWorkPolicy.REPLACE, request)
            }.onFailure { Log.w(TAG, "Couldn't schedule the midnight redraw", it) }
        }

        /** The design [event] is drawn in: its own, or the one last saved in Widget Customization (the search bar's). */
        internal fun designOf(event: CountdownEvent?, prefs: SharedPreferences): WidgetDesign =
            event?.design ?: if (SearchWidgetProvider.isMaterialStyle(prefs[SettingsManager.WIDGET_THEME_STYLE])) {
                WidgetDesign.MATERIAL
            } else {
                WidgetDesign.SYSTEM
            }

        /** What the widget shows for [event] (null: not set up yet) on [today]. */
        internal fun texts(context: Context, event: CountdownEvent?, today: LocalDate): CountdownTexts {
            if (event == null) {
                return CountdownTexts(
                    hero = "",
                    unit = context.getString(R.string.widget_countdown_set_up),
                    title = context.getString(R.string.widget_countdown_label),
                    date = "",
                    spoken = context.getString(R.string.widget_talkback_countdown_set_up)
                )
            }
            val res = context.resources
            val days = event.daysUntil(today)
            val target = event.target(today)
            val count = kotlin.math.abs(days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val (hero, unit, summary) = when {
                days == 0L -> Triple(
                    context.getString(R.string.widget_countdown_today),
                    "",
                    context.getString(R.string.widget_talkback_countdown_today, event.title)
                )
                days > 0 -> Triple(
                    count.toString(),
                    res.getQuantityString(R.plurals.widget_countdown_days_left, count),
                    res.getQuantityString(R.plurals.widget_talkback_countdown_until, count, count, event.title)
                )
                else -> Triple(
                    count.toString(),
                    res.getQuantityString(R.plurals.widget_countdown_days_ago, count),
                    res.getQuantityString(R.plurals.widget_talkback_countdown_since, count, count, event.title)
                )
            }
            // The year only when it isn't this one.
            val sameYear = target.year == today.year
            val date = target.format(dateFormat(if (sameYear) "EEEMMMd" else "EEEMMMdyyyy"))
            val spokenDate = target.format(dateFormat(if (sameYear) "EEEEMMMMd" else "EEEEMMMMdyyyy"))
            return CountdownTexts(
                hero = hero,
                unit = unit,
                title = event.title,
                date = date,
                spoken = context.getString(R.string.widget_talkback_countdown, summary, spokenDate, event.title)
            )
        }

        private fun dateFormat(skeleton: String): DateTimeFormatter {
            val locale = Locale.getDefault()
            return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        }

        /**
         * One row high, the words after the number: "days · Halloween", the event alone on the day, and until the
         * widget is set up what to do.
         */
        internal fun caption(context: Context, texts: CountdownTexts): String = when {
            !texts.isSetUp -> texts.unit
            texts.unit.isEmpty() -> texts.title
            else -> context.getString(R.string.widget_countdown_caption, texts.unit, texts.title)
        }

        /** The countdown's colors, outline and corners in the search bar's design [config]. */
        internal fun style(config: WidgetDesignConfig): CountdownStyle = CountdownStyle(
            rim = config.rimColorOpaque,
            surface = config.pillColorOpaque,
            backdrop = config.backdrop,
            shape = config.shape,
            // The bar's icons: the Custom G style's own G color, or the accent the other styles draw them in.
            hero = if (config.effectiveIconTheme == "Custom G Icon") config.customIcons.gIcon else config.themePColor,
            text = config.plainIconTint,
            secondaryText = ColorUtils.setAlphaComponent(config.plainIconTint, SECONDARY_ALPHA)
        )

        /** The search bar's design [design], resolved as the bar resolves it now. */
        private fun designConfig(context: Context, prefs: SharedPreferences, design: WidgetDesign): WidgetDesignConfig =
            SearchWidgetProvider.resolveWidgetConfig(context, prefs, isMaterialYou = design == WidgetDesign.MATERIAL)

        /** The countdown widget [appWidgetId] for [event] (null: not set up yet), at every size. */
        internal fun buildViews(context: Context, appWidgetId: Int, event: CountdownEvent?, prefs: SharedPreferences): RemoteViews =
            RemoteViews(buildSizes(context, appWidgetId, event, prefs))

        /** [buildViews]' views for each [CountdownSize], keyed the way [RemoteViews] expects for a responsive widget. */
        internal fun buildSizes(
            context: Context,
            appWidgetId: Int,
            event: CountdownEvent?,
            prefs: SharedPreferences,
            style: CountdownStyle = style(designConfig(context, prefs, designOf(event, prefs)))
        ): Map<SizeF, RemoteViews> {
            val texts = texts(context, event, now().toLocalDate())
            val tap = if (event == null) {
                Intent(context, CountdownConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                SearchWidgetProvider.mainSearchIntent(context, prefs, appWidgetId, query = event.title)
            }
            tap.identifier = "countdown_$appWidgetId"
            val tapIntent = SearchWidgetProvider.overlayPendingIntent(context, appWidgetId, tap)
            return CountdownSize.entries.associate { size ->
                val views = RemoteViews(context.packageName, size.layout)
                bind(views, context, size, texts, style)
                views.setOnClickPendingIntent(android.R.id.background, tapIntent)
                size.size to views
            }
        }

        /**
         * The Android 15+ widget picker's preview: a sample countdown in the design the widget would be placed in,
         * drawn already blended over the editor's page as the search bars' previews are
         * ([SearchWidgetProvider.pickerPreviewConfig]), so it shows the same colors there.
         */
        internal fun pickerPreview(context: Context, prefs: SharedPreferences): RemoteViews {
            val sample = CountdownEvent(
                context.getString(R.string.widget_countdown_preview_title),
                now().toLocalDate().plusDays(context.getString(R.string.widget_countdown_preview_days).toLong()),
                yearly = false
            )
            val isDark = ThemeMode.from(prefs[SettingsManager.THEME]).isDark(ThemeMode.systemIsDark(context.resources.configuration))
            val config = SearchWidgetProvider.pickerPreviewConfig(
                designConfig(context, prefs, designOf(null, prefs)),
                SearchWidgetProvider.editorPageColor(context, isDark)
            )
            return RemoteViews(buildSizes(context, appWidgetId = 0, sample, prefs, style(config)))
        }

        /**
         * Draws [texts] in [style] on the layout of [size]. Everything is set on every redraw, whatever it is: a
         * launcher redraws a placed widget by applying the new views onto the ones it shows, so anything left unset
         * would keep the previous design's or state's.
         */
        private fun bind(views: RemoteViews, context: Context, size: CountdownSize, texts: CountdownTexts, style: CountdownStyle) {
            val shape = style.shape
            val density = context.resources.displayMetrics.density
            fun px(dp: Int) = (dp * density).roundToInt()

            // The backdrop, as the search bar's: the outline around the surface, faded at once by Transparency.
            views.setViewVisibility(R.id.countdown_outline, if (shape.outline > 0) View.VISIBLE else View.GONE)
            views.setImageViewResource(R.id.countdown_outline, WidgetBarShape.background(shape.outerRadius))
            views.setImageViewResource(R.id.countdown_surface, WidgetBarShape.background(shape.pillRadius))
            for (side in listOf(RemoteViews.MARGIN_LEFT, RemoteViews.MARGIN_TOP, RemoteViews.MARGIN_RIGHT, RemoteViews.MARGIN_BOTTOM)) {
                views.setViewLayoutMargin(R.id.countdown_surface, side, shape.outline.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            views.setFloat(R.id.countdown_backdrop, "setAlpha", style.backdrop.backdrop)
            views.setColorStateList(R.id.countdown_outline, "setImageTintList", ColorStateList.valueOf(style.rim))
            views.setInt(R.id.countdown_outline, "setImageAlpha", WidgetColorRules.alphaInt(style.backdrop.rim))
            views.setColorStateList(R.id.countdown_surface, "setImageTintList", ColorStateList.valueOf(style.surface))
            views.setInt(R.id.countdown_surface, "setImageAlpha", 255)
            // The outer corners, told to the launcher, so its open and close animations keep them (see the bar's).
            views.setViewOutlinePreferredRadius(android.R.id.background, shape.outerRadius.toFloat(), TypedValue.COMPLEX_UNIT_DIP)

            val setUp = texts.isSetUp
            views.setViewVisibility(R.id.countdown_hero, if (setUp) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.countdown_icon, if (setUp) View.GONE else View.VISIBLE)
            views.setColorStateList(R.id.countdown_icon, "setImageTintList", ColorStateList.valueOf(style.hero))
            views.setTextViewText(R.id.countdown_hero, texts.hero)
            views.setTextColor(R.id.countdown_hero, style.hero)

            if (size == CountdownSize.PILL) {
                // As the bar: the pill's height (or more, for large text) inside the outline.
                val inset = px(shape.outline)
                views.setViewPadding(R.id.countdown_content, inset + px(PILL_INSET), inset, inset + px(PILL_INSET), inset)
                // The words sit on the number's baseline, so the number alone sets the height.
                views.setInt(R.id.countdown_hero, "setMinHeight", px(shape.pillHeight))
                views.setTextViewText(R.id.countdown_caption, caption(context, texts))
                views.setTextColor(R.id.countdown_caption, style.text)
            } else {
                val inset = px(shape.outline + TILE_INSET)
                views.setViewPadding(R.id.countdown_content, inset, inset, inset, inset)
                views.setTextViewText(R.id.countdown_title, texts.title)
                views.setTextColor(R.id.countdown_title, style.text)
                views.setTextViewText(R.id.countdown_unit, texts.unit)
                views.setTextColor(R.id.countdown_unit, if (size == CountdownSize.WIDE) style.hero else style.text)
                views.setViewVisibility(R.id.countdown_unit, if (texts.unit.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.countdown_date, texts.date)
                views.setTextColor(R.id.countdown_date, style.secondaryText)
                // The 2 x 2 tile leaves the date out, to give the number its room.
                val showsDate = texts.date.isNotEmpty() && size != CountdownSize.TILE
                views.setViewVisibility(R.id.countdown_date, if (showsDate) View.VISIBLE else View.GONE)
                if (size == CountdownSize.LARGE) {
                    views.setTextViewTextSize(R.id.countdown_title, TypedValue.COMPLEX_UNIT_SP, 22f)
                    views.setTextViewTextSize(R.id.countdown_unit, TypedValue.COMPLEX_UNIT_SP, 16f)
                }
            }
            views.setContentDescription(android.R.id.background, texts.spoken)
        }
    }
}

/** Redraws the countdown widgets just after local midnight, then arranges the next night's. */
class CountdownMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Drawn before the work counts as done, so the process isn't let go half way through.
        withContext(Dispatchers.IO) {
            AppWidgetManager.getInstance(applicationContext)?.let { CountdownWidgetProvider.redrawAll(applicationContext, it) }
        }
        return Result.success()
    }
}
