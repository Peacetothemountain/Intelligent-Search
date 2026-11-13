package com.pixel.intelligentsearch

import android.app.job.JobInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import com.pixel.intelligentsearch.feature.widget.CountdownWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchBoxWidgetProvider
import com.pixel.intelligentsearch.feature.widget.SearchWidgetMaterialProvider
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider

/**
 * Redraws the widgets in a new wallpaper's colors when nothing of the app is running: the theme observer and
 * [App.onConfigurationChanged] live only with the process, so without this the widgets kept the old palette until
 * something started it. Android runs [AppJob.PALETTE_CHANGE] once one of the palette's settings changes, which starts
 * the process; it wakes for nothing else. Light and dark mode aren't among those settings.
 */
internal object WidgetPaletteWatch {

    /** The widgets drawn in the palette: the search bars and the countdown. */
    private val PROVIDERS = listOf(
        SearchWidgetProvider::class.java, SearchWidgetMaterialProvider::class.java,
        SearchBoxWidgetProvider::class.java, CountdownWidgetProvider::class.java
    )

    /** Leaves the new wallpaper's colors a moment to take effect before the widgets read them. */
    private const val SETTLED_MS = 5_000L

    /**
     * Watches for a new palette while one of the widgets is placed, and stops when none is. With [keepExisting] a watch
     * already set is kept: the start Android makes to run it must not replace it before it has run.
     */
    fun sync(context: Context, keepExisting: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val placed = manager != null && PROVIDERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        if (!placed) {
            AppJob.PALETTE_CHANGE.cancel(context)
            return
        }
        AppJob.PALETTE_CHANGE.schedule(context, keepExisting) {
            for (uri in DynamicThemeDetector.paletteSettingUris()) addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
            setTriggerContentUpdateDelay(SETTLED_MS)
        }
    }

    /** A palette setting changed: redraws the widgets if the colors did, then watches again (a watch fires once). */
    fun changed(context: Context) {
        (context.applicationContext as? App)?.redrawWidgetsIfPaletteChanged()
        // Replacing it from inside the run that is redrawing ends that run; its work is already done.
        sync(context, keepExisting = false)
    }
}
