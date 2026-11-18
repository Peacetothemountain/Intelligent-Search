package com.pixel.intelligentsearch.feature.tabs.gallery

import com.pixel.intelligentsearch.feature.tabs.ui.*
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import java.time.LocalDate

/** What applying gave: a snackbar, or a question because the saved looks are full and the current look isn't saved. */
internal sealed interface Commit {
    data class Done(val snack: Snack) : Commit
    data object AskKeep : Commit
    data object Nothing : Commit
}

/**
 * Gallery's state: the look that is on, the one before it (the lasting undo), the five saved looks (Yours), a pasted
 * look and the bar's row. The looks and the row are kept in the app's settings ([attach], GalleryDisk); the rest lasts
 * while the screen does (the page keeps it in saved state). Apply and undo change the real widget and search page
 * through the app's own settings (GalleryLive).
 */
@Stable
internal class GalleryState(val today: LocalDate) {
    /** Until [attach] reads the settings: the app's defaults, as on a new phone. */
    var current by mutableStateOf(Look(GalleryLive.LIVE_ID, "Your design", origin = Origin.Mine, hero = Design.SYSTEM))
    var before by mutableStateOf<Look?>(null)
    val saved = mutableStateListOf<Look>()
    private var context: Context? = null
    var shared by mutableStateOf<Look?>(null)
    /** The bar's row in the full preview, 0 (top) to 5 (lowest); remembered for every preview. */
    var row by mutableIntStateOf(5)
    /** How many times the full preview has opened: its hint shows for the first three. */
    var opens by mutableIntStateOf(0)
    private var seq = 0
    private var attached = false
    /** Every setting a look touches as it was before [before] was replaced: what Undo and the Before tile put back. */
    private var undoTo by mutableStateOf<Look?>(null)
    /** The stored value as this session last wrote or read it: anything else there (a restore) is read in again. */
    @Volatile private var known: String? = null

    /**
     * Look of the week, fixed for the whole week: picked once (skipping the look that was on then) and kept on the phone,
     * so applying it doesn't swap it for another look the next time Gallery opens.
     */
    var hero: Look = lookOfTheWeek(today, current.id)
        private set
    private val week = weekKey(today)

    /**
     * Reads what Gallery kept in the app's settings once, before the page first draws, and from then on writes it back
     * each time the looks or the row change. The look that is on is the kept one only while the settings still have it
     * (it may have been changed since in Widget Customization or Appearance, or by a restore); otherwise it is the
     * person's own design as the settings have it ([GalleryLive.read]).
     */
    fun attach(context: Context, scope: CoroutineScope) {
        if (attached) return
        attached = true
        val app = context.applicationContext
        this.context = app
        val prefs = GalleryLive.prefs(app)
        load(app, prefs)
        scope.launch {
            snapshotFlow { GalleryKept(current, before, saved.toList(), seq, row, week to hero.id, undoTo) }
                .drop(1)
                .collect { k ->
                    withContext(Dispatchers.IO) {
                        // Known before it is stored, so a resume in between never takes this session's own write for a restore.
                        val json = GalleryDisk.json(k)
                        known = json
                        GalleryDisk.put(prefs, json)
                    }
                }
        }
    }

    /** Reads what is stored into the session: the looks, the row, the look of the week and the look that is on. */
    private fun load(app: Context, prefs: android.content.SharedPreferences) {
        known = GalleryDisk.raw(prefs)
        GalleryLive.refreshG(app)
        val disk = GalleryDisk.read(prefs)
        before = disk?.before
        undoTo = disk?.undoTo
        saved.clear()
        disk?.let { k ->
            saved.addAll(k.saved)
            seq = maxOf(seq, k.seq)
            row = k.row
        }
        current = disk?.current?.takeIf { GalleryLive.isOn(app, it) } ?: GalleryLive.read(app)
        val kept = disk?.hero?.takeIf { it.first == week }?.let { (_, id) -> BUILT_IN_LOOKS.firstOrNull { it.id == id } }
        hero = kept ?: lookOfTheWeek(today, current.id)
    }

    /**
     * Back on the page: the settings may have changed meanwhile (Widget Customization, a restore). A restore replaces
     * what Gallery kept, so a stored value this session didn't write is read in whole, never written over.
     */
    fun resync() {
        val app = context ?: return
        val prefs = GalleryLive.prefs(app)
        GalleryLive.refreshG(app)
        if (GalleryDisk.raw(prefs) != known) load(app, prefs)
        else if (!GalleryLive.isOn(app, current)) current = GalleryLive.read(app)
    }

    /** Puts [look] on the real widget and search page. */
    private fun write(look: Look) {
        context?.let { GalleryLive.apply(it, look) }
    }

    val savedFull: Boolean get() = saved.size >= 5

    /** [look] is what is on the phone now: the look that is on, or Yours Now (which is the settings as they are). */
    fun isOn(look: Look): Boolean = look.id == current.id || look.id == GalleryLive.NOW_ID

    /** The current look needs saving before another replaces it: hand-made or pasted, and not saved. */
    val currentUnsaved: Boolean get() = current.origin == Origin.Mine || current.origin == Origin.Shared

    fun find(id: String): Look? = (if (id == GalleryLive.NOW_ID) GalleryLive.now else BUILT_IN_LOOKS.firstOrNull { it.id == id })
        ?: listOfNotNull(current, before, shared).firstOrNull { it.id == id }
        ?: saved.firstOrNull { it.id == id }

    fun isSaved(look: Look): Boolean = saved.any { it === look || it.id == look.id || (it.base != null && it.base == (look.base ?: look.id)) }

    /** Whether a saved look other than [except] (an id) already has [name]; names are told apart ignoring case. */
    fun nameTaken(name: String, except: String? = null): Boolean = saved.any { it.id != except && it.name.equals(name.trim(), ignoreCase = true) }

    /** [base], or [base] numbered ("Ink 2") when a saved look has that name; never longer than 24 characters. */
    fun freeName(base: String): String {
        var n = base.take(24)
        var k = 2
        while (nameTaken(n)) {
            val suffix = " ${k++}"
            n = base.take(24 - suffix.length).trimEnd() + suffix
        }
        return n
    }

    fun defaultName(look: Look) = freeName(if (look.origin == Origin.Mine) "My look" else look.name)

    /** Saves [look] into a new slot, or over slot [at]. A hand-made or pasted look becomes the saved look itself. */
    fun store(look: Look, name: String, at: Int? = null): Look {
        val own = look.origin == Origin.Mine || look.origin == Origin.Shared
        val s = look.copy(
            id = "s${++seq}", name = name, origin = Origin.Saved,
            base = if (own) null else (look.base ?: look.id), season = if (own) look.season else null
        )
        if (own && current === look) current = s
        if (at == null) saved.add(s) else saved[at] = s
        return s
    }

    /**
     * Makes [look] the look that is on. Protects hand-made work: an unsaved current look is saved as "My look" while a
     * slot is free; with all five used it asks first (unless [force]). Only the Before tile restores ([viaBefore]).
     */
    fun commit(look: Look, force: Boolean = false, viaBefore: Boolean = false, onUndo: () -> Unit): Commit {
        val prev = current
        if (isOn(look)) return Commit.Nothing
        val restore = viaBefore && look === before
        var kept = false
        var prevKept = prev
        if (!restore && currentUnsaved) {
            if (!savedFull) {
                prevKept = store(prev, defaultName(prev))
                kept = true
            } else if (!force) return Commit.AskKeep
        }
        // A built-in look leaves the theme, the G and the clock as they are, so the way back is the settings as they are
        // now, every field set, not the look that was on.
        val snapshot = context?.let { GalleryLive.read(it) }
        write(if (restore) undoTo ?: look else look)
        undoTo = snapshot
        before = prevKept
        current = look
        if (restore) return Commit.Done(Snack("Your look from before is back on your search bar and search page."))
        val msg = if (kept) "${look.name} is on your search bar and search page. Your old look is saved in Yours."
        else "${look.name} is on your search bar and search page."
        return Commit.Done(Snack(msg, "Undo", onUndo))
    }

    fun undo(): Boolean {
        val b = before ?: return false
        val snapshot = context?.let { GalleryLive.read(it) }
        write(undoTo ?: b)
        undoTo = snapshot
        before = current
        current = b
        return true
    }

    /** The list the full preview swipes through, opened from [context] on [look]. */
    fun previewList(context: String, look: Look): List<Look> = when (context) {
        "yours" -> buildList { add(current); before?.let { add(it) }; saved.forEach { if (it !in this) add(it) } }
        "shared" -> listOf(look)
        // From the fan, the look of the week or the season's looks: every look in the order it sits in the fan, as the
        // approved Gallery steps through them, so a look is the same "n of 40" wherever it was opened.
        else -> if (look.id in FAN_ORDER) FAN_ORDER.map { fanLook(it) } else listOf(look)
    }
}

/**
 * Gallery's part of the preview's session (TabsSession): its looks ([state]), the full preview ([pv]) and the
 * snackbar showing, all kept above the tabs, so a tab switch or a recreated screen finds Gallery as it was left. [scope]
 * outlives the page: setting the phone's wallpaper runs on it, so the confirmation still shows when Android recreates
 * the screen for the wallpaper's new colors.
 */
@Stable
internal class GallerySession(today: LocalDate, val scope: CoroutineScope) {
    val state = GalleryState(today)
    val pv = PreviewController()
    var snack by mutableStateOf<Snack?>(null)

    /** True once the page has shown in this process: a saved state handed back later is older than what is here. */
    var uiSettled = false

    /** Where the fan stands (the look upright, or the one it is turning to, by its place in the order): kept with the session, not the page. */
    var fanAt = 0

    /** What the screen's saved state keeps: the fan's place, a pasted look (as its code), and the preview if it is open. */
    fun saveUi(): android.os.Bundle = android.os.Bundle().apply {
        putInt("fanAt", fanAt)
        state.shared?.let { putString("shared", LookCode.encode(it)) }
        putInt("opens", state.opens)
        val look = pv.look
        if (pv.open && !pv.closing && look != null) {
            putString("previewLook", look.id)
            // The looks it swipes through, in their order (the look of the week's list starts at the look opened).
            putStringArrayList("previewList", ArrayList(pv.list.map { it.id }))
            putInt("previewIndex", pv.index)
            putString("previewFrom", pv.context)
            putBoolean("previewSearch", pv.search)
        }
    }

    /** Puts back what [saveUi] kept, when Android brings the app back after closing it; never over a live page. */
    fun restoreUi(saved: android.os.Bundle) {
        if (uiSettled) return
        fanAt = saved.getInt("fanAt").coerceIn(0, FAN_ORDER.size - 1)
        saved.getString("shared")?.let { LookCode.decode(it) }?.let { state.shared = it }
        state.opens = saved.getInt("opens")
        val id = saved.getString("previewLook") ?: return
        val look = state.find(id) ?: return
        val from = saved.getString("previewFrom") ?: "rack"
        val kept = saved.getStringArrayList("previewList")?.mapNotNull { state.find(it) }
        pv.list = kept?.takeIf { it.isNotEmpty() && it.any { l -> l.id == id } } ?: state.previewList(from, look)
        pv.index = saved.getInt("previewIndex", -1).takeIf { pv.list.getOrNull(it)?.id == id }
            ?: pv.list.indexOfFirst { it.id == id }.coerceAtLeast(0)
        pv.context = from
        pv.search = saved.getBoolean("previewSearch")
        pv.open = true
    }
}
