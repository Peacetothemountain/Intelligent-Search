package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.content.res.Resources
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AnalogClock
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import com.pixel.intelligentsearch.feature.search.today.cards.SnapLayout
import com.pixel.intelligentsearch.feature.search.today.cards.SnapModel
import com.pixel.intelligentsearch.feature.search.today.cards.SnapRow
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone

/*
 * Reading a widget's applied views into a tiny card model. Runs in the reader process only. Widget text is untrusted:
 * every string leaves here plain, stripped and capped.
 *
 * NG Designs
 */

/** The longest text a model holds. */
internal const val MAX_TEXT = 80

private const val MAX_NODES = 300
private const val MAX_DEPTH = 16
private const val MAX_LABELS = 3

/** The most rows a list card keeps. */
internal const val MAX_ROWS = 6

/**
 * [text] as plain text a card can show: control, direction (bidi), zero-width and private-use characters dropped,
 * runs of spaces folded to one, and at most [MAX_TEXT] characters, the last an ellipsis when it was cut. A widget can
 * put anything in its text, and a direction mark could make a card read backwards, so nothing else gets through.
 */
internal fun cleanText(text: CharSequence?): String {
    if (text == null) return ""
    val out = StringBuilder(minOf(text.length, MAX_TEXT + 2))
    var space = false
    var i = 0
    while (i < text.length && out.length <= MAX_TEXT) {
        val cp = Character.codePointAt(text, i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            space = out.isNotEmpty()
            continue
        }
        when (Character.getType(cp).toByte()) {
            Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED -> continue
        }
        if (space) {
            out.append(' ')
            space = false
        }
        out.appendCodePoint(cp)
    }
    if (out.length > MAX_TEXT) {
        // Cut to leave room for the ellipsis, never through the middle of a character.
        var end = MAX_TEXT - 1
        if (Character.isHighSurrogate(out[end - 1])) end--
        out.setLength(end)
        while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
        out.append('…')
    }
    return out.toString()
}

/** One piece of text a widget shows, where it sits (pixels in the read view) and how big it is ([sp]). */
internal class SnapText(
    val text: String,
    val sp: Float,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val clock: Boolean,
    val clockFormat: String,
    val inList: Boolean
) {
    val area: Float get() = w.toFloat() * h
}

/**
 * What a read of a widget's views found: its texts in tree order, a fingerprint of which views held them, how many
 * pictures, whether it has an analog clock, its lists (read later, once their rows load) and, after that, the rows.
 */
internal class SnapRead {
    val texts = ArrayList<SnapText>()
    val fingerprint = StringBuilder()
    var images = 0
    var analog = false
    val adapters = ArrayList<AdapterView<*>>()

    /** Each loaded list row as its text pieces, side-by-side pieces already joined; null when the widget has no list. */
    var items: List<List<String>>? = null
    var itemsComplete = true
}

/** Reads the views under [root] (laid out, never shown) into a [SnapRead], within a fixed budget of views and depth. */
internal fun readSnap(root: View, scaled: Float): SnapRead {
    val read = SnapRead()
    val budget = intArrayOf(MAX_NODES)
    walk(root, 0, 0, 0, scaled, budget, read, false)
    return read
}

@Suppress("DEPRECATION") // AnalogClock is still what widgets draw their dials with.
private fun walk(v: View, ox: Int, oy: Int, depth: Int, scaled: Float, budget: IntArray, r: SnapRead, inList: Boolean) {
    if (v.visibility != View.VISIBLE || depth > MAX_DEPTH) return
    if (budget[0]-- <= 0) return
    val x = ox + v.left
    val y = oy + v.top
    if (v is TextView) {
        var raw: CharSequence? = v.text
        var clockFormat: String? = null
        if (v is TextClock) {
            // A TextClock only fills its text once attached; work it out from its own format and zone.
            val format = if (v.is24HourModeEnabled) v.format24Hour else v.format12Hour
            clockFormat = format?.toString().orEmpty()
            if (raw.isNullOrEmpty()) {
                val zone = v.timeZone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
                raw = if (format == null) "" else DateFormat.format(format, Calendar.getInstance(zone))
            }
        }
        val s = cleanText(raw)
        if (s.isNotEmpty() && v.width > 0 && v.height > 0) {
            r.texts += SnapText(
                text = s,
                sp = v.textSize / scaled,
                x = x, y = y, w = v.width, h = v.height,
                clock = clockFormat != null || v is Chronometer,
                clockFormat = clockFormat.orEmpty(),
                inList = inList
            )
            val id = if (v.id != View.NO_ID) {
                try {
                    v.resources.getResourceEntryName(v.id)
                } catch (_: Resources.NotFoundException) {
                    ""
                }
            } else ""
            r.fingerprint.append(v.javaClass.simpleName).append(':').append(id).append(';')
        }
    }
    if (v is ImageView && v.drawable != null) r.images++
    if (v is AnalogClock) r.analog = true
    if (v is AdapterView<*>) r.adapters += v
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) walk(v.getChildAt(i), x, y, depth + 1, scaled, budget, r, inList || v is AdapterView<*>)
    }
}

/** How a read turned out: [GOOD] carries a model; the rest say why there is none. */
internal enum class ReadVerdict {
    /** A model the card can show. */
    GOOD,

    /** The widget sent nothing (yet). */
    EMPTY,

    /** Loading, an error, a list still filling, no text, or the views couldn't be applied. */
    WAITING,

    /** A placeholder value such as "--" or "…". */
    PLACEHOLDER,

    /** The widget's own initial (placeholder) layout; a widget that always shows it never changes. */
    INITIAL,

    /** Only pictures, no text. */
    PICTURE,

    /** The widget asks to be set up in its app. */
    SETUP,

    /** The widget is gone. */
    REMOVED
}

/** One read of a widget: its [verdict], the [model] when [ReadVerdict.GOOD], and a short [fingerprint] of its views. */
internal class SnapCandidate(val verdict: ReadVerdict, val model: SnapModel? = null, val fingerprint: String = "")

private val WAIT = Regex(
    "(?i)\\b(loading|problem loading|can.?t load|couldn.?t load|no connection|offline|unavailable|error|try again|refreshing|updating)\\b"
)
private val SETUP_WORDS = Regex(
    "(?i)\\b(tap to (set ?up|configure|add|choose|select|sign in)|sign in|log in|set ?up|choose an? |select an? |add (a |an )?(widget|account|location|city|note|habit))"
)
private val PLACEHOLDER = Regex("^[-–—.·…\\s]+$|^(--|n/?a)$", RegexOption.IGNORE_CASE)

/** "loading" in a list row: the row isn't filled yet. */
internal val LOADING = Regex("(?i)\\bloading\\b")
private val DAY = Regex("^\\d{1,2}$")
private val TIME = Regex("^(\\d{1,2}):(\\d{2})(?:\\s?([AaPp])\\.?[Mm]\\.?)?$")
private val DURATION = Regex("^<?(?:(\\d+)\\s?(?:h|hr|hrs)\\s?)?(?:(\\d+)\\s?(?:m|min|mins))?$")
private val NUMBER = Regex("^([-+−]?\\d[\\d,]*(?:\\.\\d+)?)\\s?(.{0,8})$")
private val CHANGE = Regex("^[-+−▲▼]\\s?\\d[\\d.,]*\\s?%?$|^[-+−]?\\d[\\d.,]*\\s?%$")
private val NUMBERISH = Regex("^[-+−]?[^\\d\\s]{0,3}\\s?\\d[\\d.,]*\\s?\\S{0,4}$")

/** A TextClock format that shows the time (hours or minutes), not only a date. */
private val TIME_FORMAT = Regex("[hHkKm]")

/** The model for [r], or why there is none. Port of the proven reader's rules; see [ReadVerdict]. */
internal fun snapCandidate(r: SnapRead): SnapCandidate {
    val texts = r.texts
    if (r.analog) return SnapCandidate(ReadVerdict.GOOD, SnapModel(SnapLayout.CLOCK), "analog")
    if (!r.itemsComplete) return SnapCandidate(ReadVerdict.WAITING)
    if (texts.isEmpty()) return SnapCandidate(if (r.images > 0) ReadVerdict.PICTURE else ReadVerdict.WAITING)
    val all = texts.joinToString(" ") { it.text }
    if (SETUP_WORDS.containsMatchIn(all)) return SnapCandidate(ReadVerdict.SETUP)
    if (WAIT.containsMatchIn(all)) return SnapCandidate(ReadVerdict.WAITING)

    val fp = Integer.toHexString(r.fingerprint.toString().hashCode())
    val bySize = texts.sortedWith(compareByDescending<SnapText> { it.sp }.thenByDescending { it.area })
    val hero = bySize[0]
    val reading = texts.sortedWith(compareBy<SnapText> { it.y }.thenBy { it.x })

    if (texts.count { DAY.matches(it.text) } >= 28) {
        // A month grid: the card draws its own month from the date, titled as the widget titles it.
        val today = LocalDate.now()
        val title = bySize.firstOrNull { !DAY.matches(it.text) && it.text.length > 3 }?.text.orEmpty()
        return SnapCandidate(
            ReadVerdict.GOOD,
            SnapModel(SnapLayout.MONTH, title = title, day = today.dayOfMonth, weekday = today.dayOfWeek.value),
            fp
        )
    }
    r.items?.let { items ->
        // The list's title is the biggest text outside the list (its header); without one the card uses the app name.
        val title = bySize.firstOrNull { !it.inList && !it.clock }?.text.orEmpty()
        return SnapCandidate(ReadVerdict.GOOD, SnapModel(SnapLayout.LIST, title = title, rows = items.take(MAX_ROWS).map(::rowOf)), fp)
    }
    val rows = bySize.size > 2 && hero.sp < bySize[1].sp * 1.15f && !hero.clock
    if (rows) {
        // Several lines of about the same size and no clear main value: shown as a short list in reading order.
        return SnapCandidate(ReadVerdict.GOOD, SnapModel(SnapLayout.LIST, rows = reading.take(4).map { SnapRow(it.text) }), fp)
    }
    if (hero.clock && TIME_FORMAT.containsMatchIn(hero.clockFormat)) {
        // A clock: the card shows the time now itself. Other clock text (a date) would be stale by tomorrow, so only
        // the widget's own lines stay.
        val labels = reading.filter { it !== hero && !it.clock }.map { it.text }.distinct().take(MAX_LABELS)
        return SnapCandidate(ReadVerdict.GOOD, SnapModel(SnapLayout.CLOCK, labels = labels), fp)
    }
    if (PLACEHOLDER.containsMatchIn(hero.text)) return SnapCandidate(ReadVerdict.PLACEHOLDER)
    val labels = reading.filter { it !== hero }.map { it.text }.filter { it != hero.text }.distinct().take(MAX_LABELS)
    return SnapCandidate(ReadVerdict.GOOD, heroModel(hero.text, labels, quantity = !hero.clock), fp)
}

/**
 * A hero model for [text]: a duration in minutes, a number with its unit and decimals, or plain text. A time of day
 * ("7:00 AM") and clock text that isn't a [quantity] (a date) stay text, so they never count up.
 */
private fun heroModel(text: String, labels: List<String>, quantity: Boolean): SnapModel {
    val base = SnapModel(SnapLayout.HERO, hero = text, labels = labels)
    if (!quantity || TIME.matches(text)) return base
    DURATION.matchEntire(text)?.let { m ->
        val h = m.groupValues[1]
        val min = m.groupValues[2]
        if (h.isNotEmpty() || min.isNotEmpty()) {
            val minutes = (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
            return base.copy(number = minutes.toDouble(), unit = "min", duration = true)
        }
    }
    NUMBER.matchEntire(text)?.let { m ->
        val digits = m.groupValues[1]
        val value = digits.replace(",", "").replace('−', '-').toDoubleOrNull()
        if (value != null && value.isFinite()) {
            val dot = digits.indexOf('.')
            return base.copy(number = value, unit = m.groupValues[2].trim(), decimals = if (dot < 0) 0 else digits.length - dot - 1)
        }
    }
    return base
}

/** A list row from its pieces: the change ("-0.46%"), the value (the first number-like piece) and the name. */
private fun rowOf(pieces: List<String>): SnapRow {
    val change = pieces.firstOrNull { CHANGE.matches(it) }
    val rest = pieces.filter { it !== change }
    if (rest.isEmpty()) return SnapRow(change.orEmpty())
    val value = rest.firstOrNull { NUMBERISH.matches(it) }
    return if (value != null) {
        SnapRow(value, rest.firstOrNull { it !== value }.orEmpty(), change)
    } else {
        SnapRow(rest[0], rest.getOrNull(1).orEmpty(), change)
    }
}
