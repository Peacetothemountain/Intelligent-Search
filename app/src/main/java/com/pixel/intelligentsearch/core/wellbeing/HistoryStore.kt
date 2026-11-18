package com.pixel.intelligentsearch.core.wellbeing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/**
 * The page's own daily summary, kept because the phone keeps the detail for only about 10 days: one small JSON file in
 * the app's private storage (left out of backups by the manifest), never sent anywhere. It holds whole days only, up
 * to [KEEP_DAYS]; "Clear Wellbeing history" deletes the file.
 *
 * Damage never costs more than it has to: each day is read on its own, so one bad day loses only that day; a file that
 * can't be read at all is moved aside (wellbeing_history.unreadable-<time>.json) rather than written over; a file with
 * bad days is copied aside once before the good days are saved again; and every save goes to a temporary file first,
 * synced, then moved over the old one in one step, so a save cut short leaves the last good file.
 */
class HistoryStore(private val file: File) {

    /**
     * [changed]: what was read is not what the file holds (a damaged day left out), so it should be saved again.
     * [version]: the version the file was written by (see [VERSION]).
     */
    class Contents(val days: Map<LocalDate, DaySummary>, val labels: Map<String, String>, val changed: Boolean = false, val version: Int = VERSION)

    /** Set when an unreadable file couldn't be moved aside: then nothing is written over it. */
    @Volatile private var blocked = false

    fun read(): Contents {
        if (!file.exists()) return Contents(emptyMap(), emptyMap())
        val root = runCatching { JSONObject(file.readText()) }.getOrNull()
        if (root == null) {
            // Not this page's to throw away: keep it whole, out of the way, and start again.
            if (!setAside("unreadable", move = true)) blocked = true
            return Contents(emptyMap(), emptyMap())
        }
        var damaged = false
        val labels = HashMap<String, String>()
        root.optJSONObject("labels")?.let { o ->
            o.keys().forEach { k -> o.optString(k, "").takeIf { it.isNotEmpty() }?.let { labels[k] = it } ?: run { damaged = true } }
        }
        val days = HashMap<LocalDate, DaySummary>()
        val arr = root.opt("days")
        if (arr != null && arr !is JSONArray) damaged = true
        if (arr is JSONArray) for (i in 0 until arr.length()) {
            val d = runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            if (d == null) damaged = true else days[d.date] = d
        }
        if (damaged) setAside("damaged", move = false)
        return Contents(days, labels, changed = damaged, version = root.optInt("v", 1))
    }

    /** Saves the whole days before [today] (a day dated today or later can't be whole, whatever the clock did). */
    fun write(days: Collection<DaySummary>, labels: Map<String, String>, today: LocalDate) {
        if (blocked) return
        val kept = days.filter { it.complete && it.date.isBefore(today) }.sortedBy { it.date }.takeLast(KEEP_DAYS)
        val used = kept.flatMap { it.apps.keys + listOfNotNull(it.firstApp) }.toSet()
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("labels", JSONObject().apply { labels.filterKeys { it in used }.forEach { (k, v) -> put(k, v) } })
        root.put("days", JSONArray().apply { kept.forEach { put(toJson(it)) } })
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Keeps the file as it is under another name, once per kind of damage, so nothing in it is lost. */
    private fun setAside(kind: String, move: Boolean): Boolean {
        val dir = file.parentFile ?: return false
        if (!move && asides().any { it.name.contains(".$kind-") }) return true
        val aside = File(dir, "${file.name.removeSuffix(".json")}.$kind-${System.currentTimeMillis()}.json")
        return runCatching {
            if (move) Files.move(file.toPath(), aside.toPath(), StandardCopyOption.ATOMIC_MOVE)
            else Files.copy(file.toPath(), aside.toPath())
            true
        }.getOrElse { move && file.renameTo(aside) }
    }

    private fun asides(): List<File> {
        val base = file.name.removeSuffix(".json") + "."
        return file.parentFile?.listFiles()?.filter { it.name.startsWith(base) && it.name != file.name && it.name.endsWith(".json") }.orEmpty()
    }

    /** Deletes the summary, and any copy kept aside after damage: they hold the same history. */
    fun clear() {
        file.delete()
        asides().forEach { it.delete() }
        blocked = false
    }

    companion object {
        const val KEEP_DAYS = 400
        /**
         * 2: days are whole only from when the phone's records really begin (version 1 took the oldest day they reached
         * as whole, see WellbeingRepository); a night's length and pick-ups, and the longest stretch on screen.
         */
        const val VERSION = 2
        const val FILE_NAME = "wellbeing_history.json"

        private fun ints(a: IntArray) = JSONArray().apply { a.forEach { put(it) } }
        private fun ints(a: JSONArray?, n: Int = 24) = IntArray(n) { a?.optInt(it) ?: 0 }

        /** Hours with nothing are left out: an app is on screen in a few hours of the day. */
        private fun sparse(a: IntArray) = JSONObject().apply { a.forEachIndexed { h, v -> if (v != 0) put(h.toString(), v) } }
        private fun sparse(o: JSONObject?) = IntArray(24) { o?.optInt(it.toString()) ?: 0 }

        fun toJson(d: DaySummary): JSONObject = JSONObject().apply {
            put("d", d.date.toString())
            put("t", d.totalSec)
            put("h", ints(d.hours))
            put("u", ints(d.unlocks))
            put("n", ints(d.notifications))
            put("a", JSONObject().apply {
                d.apps.forEach { (k, a) ->
                    put(k, JSONObject().apply {
                        put("t", a.totalSec); put("h", sparse(a.hours)); put("o", a.opens); put("l", a.longestSec)
                        put("x", a.over15); put("n", a.notifications); put("f", a.followed)
                    })
                }
            })
            put("ua", d.unlocksAfterNotification)
            put("fo", JSONObject().apply { d.firstAfterOwnUnlock.forEach { (k, v) -> put(k, v) } })
            d.firstUseMin?.let { put("fu", it) }
            if (d.inUseAtMidnight) put("um", true)
            d.firstApp?.let { put("fa", it) }
            d.lastUseMin?.let { put("lu", it) }
            d.phoneDownStartMin?.let { put("pd", it) }
            d.awayStartMin?.let { put("as", it) }
            d.awayLenMin?.let { put("al", it) }
            put("nd", d.notificationsWhileDown)
            put("c", d.complete)
            d.phoneDownLenMin?.let { put("pl", it) }
            if (d.nightPickups > 0) { put("np", d.nightPickups); put("nu", d.nightUseSec) }
            d.longestOnStartMin?.let { put("ls", it) }
            d.longestOnLenMin?.let { put("ll", it) }
            put("r", d.rules)
        }

        fun fromJson(o: JSONObject): DaySummary {
            val apps = HashMap<String, AppDay>()
            o.optJSONObject("a")?.let { a ->
                a.keys().forEach { k ->
                    val x = a.getJSONObject(k)
                    apps[k] = AppDay(x.optInt("t"), sparse(x.optJSONObject("h")), x.optInt("o"), x.optInt("l"), x.optInt("x"), x.optInt("n"), x.optInt("f"))
                }
            }
            val firstOwn = HashMap<String, Int>()
            o.optJSONObject("fo")?.let { f -> f.keys().forEach { firstOwn[it] = f.getInt(it) } }
            fun optInt(name: String): Int? = if (o.has(name)) o.getInt(name) else null
            return DaySummary(
                date = LocalDate.parse(o.getString("d")),
                totalSec = o.optInt("t"),
                hours = ints(o.optJSONArray("h")),
                unlocks = ints(o.optJSONArray("u")),
                notifications = ints(o.optJSONArray("n")),
                apps = apps,
                unlocksAfterNotification = o.optInt("ua"),
                firstAfterOwnUnlock = firstOwn,
                firstUseMin = optInt("fu"),
                firstApp = if (o.has("fa")) o.getString("fa") else null,
                lastUseMin = optInt("lu"),
                phoneDownStartMin = optInt("pd"),
                awayStartMin = optInt("as"),
                awayLenMin = optInt("al"),
                notificationsWhileDown = o.optInt("nd"),
                complete = o.optBoolean("c"),
                inUseAtMidnight = o.optBoolean("um"),
                phoneDownLenMin = optInt("pl"),
                nightPickups = o.optInt("np"),
                nightUseSec = o.optInt("nu"),
                longestOnStartMin = optInt("ls"),
                longestOnLenMin = optInt("ll"),
                rules = o.optInt("r", 0)
            )
        }
    }
}
