package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.SizeF
import android.view.View
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * NG Designs
 */

/**
 * The widget reader, in the reader process only. It listens on Discover's widget host, which nothing ever shows, and
 * catches each widget's update before it is drawn. The update is applied on a background thread in the plain
 * application context (a themed context made every widget fail to apply), laid out at [READ_SIZE_DP], and read into a
 * [SnapCandidate] for [sink]. Lists are applied with the widget's own host view as their parent, which their adapter
 * needs to find the widget, and their rows are read once loaded, waiting up to about two seconds. Whether a read can
 * be trusted is decided in the app, which knows the last good one.
 */
internal class WidgetReaderCore(context: Context, private val sink: (widgetId: Int, read: SnapCandidate) -> Unit) {
    private val app = context.applicationContext
    private val density = app.resources.displayMetrics.density
    private val scaled = density * app.resources.configuration.fontScale
    private val sizePx = (READ_SIZE_DP * density).roundToInt()
    private val thread = HandlerThread("widget-reader", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val bg = Handler(thread.looper)
    private val cards = ConcurrentHashMap<Int, Card>()
    private var listening = false

    /** One widget being read: its info, which update is newest ([gen]), its placeholder text, and its last read. */
    private class Card(val id: Int, val info: AppWidgetProviderInfo) {
        @Volatile var gen = 0
        var initialTexts: Set<String>? = null
        @Volatile var last: SnapCandidate? = null
    }

    private val host = object : AppWidgetHost(app, WIDGET_HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            CaptureView(context)
    }

    /** Starts reading [ids]; a widget already being read reports its last read again. Call on the main thread. */
    fun read(ids: IntArray) {
        if (!listening) {
            listening = runCatching { host.startListening() }.isSuccess
            if (!listening) {
                ids.forEach { sink(it, SnapCandidate(ReadVerdict.WAITING)) }
                return
            }
        }
        val manager = AppWidgetManager.getInstance(app)
        for (id in ids) {
            val known = cards[id]
            if (known != null) {
                known.last?.let { sink(id, it) }
                continue
            }
            val info = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()
            if (info == null) {
                sink(id, SnapCandidate(ReadVerdict.REMOVED))
                continue
            }
            val card = Card(id, info)
            cards[id] = card
            try {
                val view = host.createView(app, id, info)
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(READ_SIZE_DP.toFloat(), READ_SIZE_DP.toFloat())))
            } catch (_: RuntimeException) {
                emit(card, SnapCandidate(ReadVerdict.WAITING))
            }
        }
    }

    /**
     * Which of [providers] (flattened component names) are only a picture: their preview and initial layouts hold
     * pictures but no text, list or clock at all. A layout that can't be applied counts as text, so a widget is only
     * left off on evidence. Runs on the reader's background thread and calls [done] there.
     */
    fun pictureOnly(providers: List<String>, done: (ArrayList<String>) -> Unit) {
        bg.post {
            val out = ArrayList<String>()
            val wanted = providers.toHashSet()
            val manager = AppWidgetManager.getInstance(app)
            val infos = runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()) }.getOrDefault(emptyList())
            for (info in infos) {
                val name = info.provider.flattenToString()
                if (name !in wanted) continue
                val layouts = listOf(info.previewLayout, info.initialLayout).filter { it != 0 }
                if (layouts.isEmpty()) continue
                var text = false
                var pictures = 0
                for (res in layouts) {
                    try {
                        val v = RemoteViews(info.provider.packageName, res).apply(app, FrameLayout(app))
                        layout(v, sizePx, sizePx)
                        val look = LayoutLook()
                        look.scan(v)
                        text = text || look.text
                        pictures += look.pictures
                    } catch (_: Throwable) {
                        text = true
                    }
                    if (text) break
                }
                if (!text && pictures > 0) out += name
            }
            done(out)
        }
    }

    /** Stops listening and ends the background thread. */
    fun stop() {
        if (listening) runCatching { host.stopListening() }
        listening = false
        thread.quitSafely()
    }

    /** The host view of a card: never shown, and never builds the widget's views on the main thread. */
    private inner class CaptureView(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            val id = appWidgetId
            val parent = this
            bg.post { read(id, remoteViews, parent) }
        }
    }

    private fun read(id: Int, rv: RemoteViews?, parent: AppWidgetHostView) {
        val card = cards[id] ?: return
        val gen = ++card.gen
        try {
            if (rv == null) {
                emit(card, SnapCandidate(ReadVerdict.EMPTY))
                return
            }
            if (card.initialTexts == null) card.initialTexts = initialTexts(card, parent)
            val v = rv.apply(app, parent)
            layout(v, sizePx, sizePx)
            val r = readSnap(v, scaled)
            if (r.adapters.isNotEmpty()) {
                bg.postDelayed({ items(card, gen, r, 0) }, 60)
                return
            }
            finish(card, r)
        } catch (_: Throwable) {
            emit(card, SnapCandidate(ReadVerdict.WAITING))
        }
    }

    /** Text the widget's own initial (placeholder) layout carries; an update showing only this is not data yet. */
    private fun initialTexts(card: Card, parent: AppWidgetHostView): Set<String> {
        if (card.info.initialLayout == 0) return emptySet()
        return try {
            val v = RemoteViews(card.info.provider.packageName, card.info.initialLayout).apply(app, parent)
            layout(v, sizePx, sizePx)
            readSnap(v, scaled).texts.filter { !it.clock }.map { it.text }.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    /** Reads up to [MAX_ROWS] rows of a widget's lists once their adapters have them, giving up after about two seconds. */
    private fun items(card: Card, gen: Int, r: SnapRead, attempt: Int) {
        if (gen != card.gen) return // a newer update replaced this one
        try {
            val items = ArrayList<List<String>>()
            var done = true
            for (av in r.adapters) {
                val adapter = av.adapter
                if (adapter == null) {
                    done = false
                    continue
                }
                for (i in 0 until adapter.count) {
                    if (items.size >= MAX_ROWS) break
                    val iv = adapter.getView(i, null, av)
                    layout(iv, if (av.width > 0) av.width else sizePx, 0)
                    val pieces = rowPieces(readSnap(iv, scaled).texts)
                    if (pieces.any { LOADING.containsMatchIn(it) }) {
                        done = false
                        break
                    }
                    if (pieces.isNotEmpty()) items += pieces
                }
            }
            if (!done && attempt < 30) {
                bg.postDelayed({ items(card, gen, r, attempt + 1) }, 70)
                return
            }
            r.items = items
            r.itemsComplete = done
            finish(card, r)
        } catch (_: Throwable) {
            emit(card, SnapCandidate(ReadVerdict.WAITING))
        }
    }

    /** A row's text pieces, pieces set side by side on one line ("51,285" + ".73") joined into one value. */
    private fun rowPieces(texts: List<SnapText>): List<String> {
        val out = ArrayList<String>()
        var prev: SnapText? = null
        for (t in texts) {
            val p = prev
            val joined = p != null && abs(t.y + t.h / 2 - (p.y + p.h / 2)) < p.h / 2 &&
                t.x - (p.x + p.w) < 2 * density && t.x >= p.x
            if (joined && out.isNotEmpty()) out[out.lastIndex] = cleanText(out.last() + t.text) else out += t.text
            prev = t
        }
        return out
    }

    private fun finish(card: Card, r: SnapRead) {
        var c = snapCandidate(r)
        val initial = card.initialTexts.orEmpty()
        if (c.verdict == ReadVerdict.GOOD && c.model?.layout != SnapLayout.CLOCK && r.items == null &&
            initial.isNotEmpty() && r.texts.isNotEmpty()
        ) {
            // Clock text is worked out live, so it says nothing about whether this is the placeholder.
            val anyClock = r.texts.any { it.clock }
            if (!anyClock && r.texts.all { it.text in initial }) c = SnapCandidate(ReadVerdict.INITIAL)
        }
        emit(card, c)
    }

    private fun emit(card: Card, c: SnapCandidate) {
        card.last = c
        sink(card.id, c)
    }

    /** What a layout holds, for [pictureOnly]: any text, list or clock, and how many pictures. */
    private class LayoutLook {
        var text = false
        var pictures = 0

        @Suppress("DEPRECATION") // AnalogClock is still what widgets draw their dials with.
        fun scan(v: View) {
            if (v is TextView || v is AdapterView<*> || v is android.widget.AnalogClock) text = true
            if (v is android.widget.ImageView) pictures++
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) scan(v.getChildAt(i))
        }
    }

    private companion object {
        fun layout(v: View, w: Int, h: Int) {
            v.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                if (h > 0) View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        }
    }
}
