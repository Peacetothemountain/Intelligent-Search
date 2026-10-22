package com.pixel.intelligentsearch.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.system.MediaSessionAccess

/**
 * What a search bar widget shows above its bar once it is tall enough: the favourite apps row, then what's playing
 * ([SearchWidgetProvider]'s taller sizes). Loaded once per redraw and shared by every size, so each app icon is drawn
 * once and the launcher keeps one copy of it.
 */
internal data class WidgetExtras(
    /** The favourite apps that are installed and can be opened, in the user's order. */
    val favourites: List<Favourite>,
    /** What's playing, when the design shows it and the app may read media sessions. */
    val nowPlaying: MediaSessionAccess.NowPlaying?
) {
    data class Favourite(val packageName: String, val label: String, val icon: Bitmap, val launch: Intent)

    /** These extras as a widget with room for [count] rows above its bar shows them: favourites first, then media. */
    fun rows(count: Int): WidgetExtras {
        val showFavourites = favourites.isNotEmpty() && count >= 1
        val showMedia = nowPlaying != null && count >= (if (favourites.isNotEmpty()) 2 else 1)
        return WidgetExtras(if (showFavourites) favourites else emptyList(), nowPlaying.takeIf { showMedia })
    }

    companion object {
        val NONE = WidgetExtras(emptyList(), null)

        /** Where what's playing comes from; tests replace it. */
        internal var nowPlaying: (Context) -> MediaSessionAccess.NowPlaying? = { MediaSessionAccess.nowPlaying(it) }

        private val FAVOURITE_VIEWS = listOf(
            R.id.widget_favourite_0, R.id.widget_favourite_1, R.id.widget_favourite_2, R.id.widget_favourite_3, R.id.widget_favourite_4
        )

        // Request codes for the rows' taps, after the bar's own slots (see SearchWidgetProvider).
        private const val SLOT_FAVOURITE_0 = 8
        private const val SLOT_MEDIA_OPEN = 13
        private const val SLOT_MEDIA_PLAY_PAUSE = 14
        private const val SLOT_MEDIA_NEXT = 15

        /** The design's extras for this redraw; runs on a background thread (it draws icons and asks for sessions). */
        fun load(context: Context, config: SearchWidgetProvider.Companion.WidgetDesignConfig): WidgetExtras {
            val pm = context.packageManager
            val plate = (config.pillColorOpaque and 0xFFFFFF) or (config.pillAlphaInt shl 24)
            val favourites = config.favouriteApps.mapNotNull { packageName ->
                val launch = pm.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfoCompat(packageName)).toString() }.getOrDefault(packageName)
                val icon = createFavouriteAppBitmap(context, packageName, accent = config.themePColor, plate = plate) ?: return@mapNotNull null
                Favourite(packageName, label, icon, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            val playing = if (config.showMedia) runCatching { nowPlaying(context) }.getOrNull() else null
            return WidgetExtras(favourites, playing)
        }

        /** Shows [extras] above the bar in [views] (nothing at all for [NONE]), coloured and shaped like the bar. */
        fun bind(
            views: RemoteViews,
            context: Context,
            appWidgetId: Int,
            config: SearchWidgetProvider.Companion.WidgetDesignConfig,
            extras: WidgetExtras
        ) {
            bindFavourites(views, context, appWidgetId, extras.favourites)
            bindMedia(views, context, appWidgetId, config, extras.nowPlaying)
        }

        private fun bindFavourites(views: RemoteViews, context: Context, appWidgetId: Int, favourites: List<Favourite>) {
            views.setViewVisibility(R.id.widget_favourites, if (favourites.isEmpty()) View.GONE else View.VISIBLE)
            if (favourites.isEmpty()) return
            FAVOURITE_VIEWS.forEachIndexed { index, viewId ->
                val app = favourites.getOrNull(index)
                if (app == null) {
                    views.setViewVisibility(viewId, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setImageViewBitmap(viewId, app.icon)
                views.setContentDescription(viewId, context.getString(R.string.widget_talkback_open_app, app.label))
                val slot = SLOT_FAVOURITE_0 + index
                val intent = Intent(app.launch).apply { identifier = "widget_${appWidgetId}_slot_$slot" }
                views.setOnClickPendingIntent(viewId, activityIntent(context, appWidgetId, slot, intent))
            }
        }

        private fun bindMedia(
            views: RemoteViews,
            context: Context,
            appWidgetId: Int,
            config: SearchWidgetProvider.Companion.WidgetDesignConfig,
            playing: MediaSessionAccess.NowPlaying?
        ) {
            views.setViewVisibility(R.id.widget_media, if (playing == null) View.GONE else View.VISIBLE)
            if (playing == null) return
            val shape = config.shape
            views.setViewLayoutHeight(R.id.widget_media, shape.pillHeight.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setImageViewResource(R.id.widget_media_background, WidgetBarShape.background(shape.pillRadius))
            views.setColorStateList(R.id.widget_media_background, "setImageTintList", ColorStateList.valueOf(config.pillColorOpaque))
            views.setInt(R.id.widget_media_background, "setImageAlpha", config.pillAlphaInt)

            val accent = ColorStateList.valueOf(config.themePColor)
            views.setColorStateList(R.id.widget_media_icon, "setImageTintList", accent)
            views.setTextViewText(R.id.widget_media_title, playing.line)
            views.setTextColor(R.id.widget_media_title, config.plainIconTint)
            val appLabel = runCatching {
                context.packageManager.let { it.getApplicationLabel(it.getApplicationInfoCompat(playing.packageName)).toString() }
            }.getOrDefault(playing.packageName)
            views.setContentDescription(R.id.widget_media_open, context.getString(R.string.widget_talkback_media_open, playing.line, appLabel))
            val open = playing.sessionActivity ?: context.packageManager.getLaunchIntentForPackage(playing.packageName)?.let {
                activityIntent(context, appWidgetId, SLOT_MEDIA_OPEN, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { identifier = "widget_${appWidgetId}_media_open" })
            }
            if (open != null) views.setOnClickPendingIntent(R.id.widget_media_open, open)

            views.setImageViewResource(R.id.widget_media_play_pause, if (playing.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play)
            views.setContentDescription(
                R.id.widget_media_play_pause,
                context.getString(if (playing.isPlaying) R.string.widget_talkback_media_pause else R.string.widget_talkback_media_play)
            )
            views.setColorStateList(R.id.widget_media_play_pause, "setImageTintList", accent)
            views.setColorStateList(R.id.widget_media_next, "setImageTintList", accent)
            views.setOnClickPendingIntent(
                R.id.widget_media_play_pause,
                mediaIntent(context, appWidgetId, SLOT_MEDIA_PLAY_PAUSE, WidgetMediaReceiver.ACTION_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(R.id.widget_media_next, mediaIntent(context, appWidgetId, SLOT_MEDIA_NEXT, WidgetMediaReceiver.ACTION_NEXT))
        }

        private fun activityIntent(context: Context, appWidgetId: Int, slot: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                SearchWidgetProvider.getWidgetRequestCode(appWidgetId, slot),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                SearchWidgetProvider.getPendingIntentActivityOptions(context)
            )

        private fun mediaIntent(context: Context, appWidgetId: Int, slot: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                SearchWidgetProvider.getWidgetRequestCode(appWidgetId, slot),
                Intent(context, WidgetMediaReceiver::class.java).setAction(action).apply { identifier = "widget_${appWidgetId}_$action" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
