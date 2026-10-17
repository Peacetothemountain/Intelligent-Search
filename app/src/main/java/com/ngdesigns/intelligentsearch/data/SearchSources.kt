package com.ngdesigns.intelligentsearch.data

import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.device.AppSource
import com.ngdesigns.intelligentsearch.data.device.CalendarSource
import com.ngdesigns.intelligentsearch.data.device.ContactSource
import com.ngdesigns.intelligentsearch.data.device.FileSource
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.data.device.UsageSource
import com.ngdesigns.intelligentsearch.data.history.Memory
import com.ngdesigns.intelligentsearch.data.settings.Settings
import com.ngdesigns.intelligentsearch.engine.Candidate
import com.ngdesigns.intelligentsearch.engine.Catalog
import com.ngdesigns.intelligentsearch.engine.Kind
import com.ngdesigns.intelligentsearch.engine.Usage

/** A setting search can jump to, from the settings screens' own index. */
data class SettingEntry(val key: String, val title: String, val section: String, val keywords: List<String> = emptyList())

/** Everything search can find right now: one catalog per kind, and what each result opens. */
data class Snapshot(
    val catalogs: Map<Kind, Catalog> = emptyMap(),
    val targets: Map<String, Target> = emptyMap(),
    /** Visible apps, for suggestions and the quick app panel. */
    val apps: List<AppEntry> = emptyList(),
    val usage: Map<String, Usage> = emptyMap(),
) {
    fun target(candidate: Candidate): Target? = targets[candidate.id]
}

/** The raw sources search reads, gathered so they can be swapped for stand-ins in tests. */
class DeviceSources(
    val apps: AppSource,
    val usage: UsageSource,
    val contacts: ContactSource,
    val calendar: CalendarSource,
    val files: FileSource,
)

/**
 * Builds a [Snapshot] from the device and the user's settings: only enabled sources, hidden apps removed, app usage
 * folded into ranking. Runs off the main thread; reading contacts and walking folders can take a moment.
 */
class SnapshotBuilder(private val device: DeviceSources) {

    fun build(settings: Settings, memory: Memory, settingsIndex: List<SettingEntry>): Snapshot {
        val targets = HashMap<String, Target>()
        val catalogs = HashMap<Kind, Catalog>()
        val src = settings.sources
        val hidden = settings.privacy.hiddenApps
        val usage = device.usage.usage()
        val launches = memory.launches

        val apps = if (src.apps) device.apps.apps().filter { it.target.packageName !in hidden } else emptyList()
        if (apps.isNotEmpty()) {
            catalogs[Kind.App] = Catalog(apps.map { app ->
                val id = "app:${app.target.component.flattenToShortString()}@${app.target.user.hashCode()}"
                targets[id] = app.target
                val pkg = app.target.packageName
                val used = usage[pkg] ?: launches[pkg]?.let { Usage(it.count, it.lastMillis) } ?: Usage.None
                Candidate(id, Kind.App, app.label, keywords = listOf(pkg), usage = used)
            })
        }

        if (src.apps && src.shortcuts) {
            val shortcuts = device.apps.shortcuts().filter { it.target.packageName !in hidden }
            if (shortcuts.isNotEmpty()) catalogs[Kind.Shortcut] = Catalog(shortcuts.map { s ->
                val id = "shortcut:${s.target.packageName}/${s.target.shortcutId}@${s.target.user.hashCode()}"
                targets[id] = s.target
                Candidate(id, Kind.Shortcut, s.label, subtitle = s.appLabel, keywords = listOf(s.longLabel, s.appLabel), usage = usage[s.target.packageName] ?: Usage.None)
            })
        }

        if (src.contacts) {
            val contacts = device.contacts.contacts()
            if (contacts.isNotEmpty()) catalogs[Kind.Contact] = Catalog(contacts.map { c ->
                val id = "contact:${c.target.lookupUri}"
                targets[id] = c.target
                Candidate(
                    id, Kind.Contact, c.name, subtitle = c.phones.firstOrNull(),
                    keywords = c.phones.map { p -> p.filter(Char::isDigit) }, soundsLike = true,
                    usage = if (c.starred) Usage(count = 8) else Usage.None,
                )
            })
        }

        if (src.calendar) {
            val events = device.calendar.upcoming()
            if (events.isNotEmpty()) catalogs[Kind.Event] = Catalog(events.map { e ->
                val id = "event:${e.target.eventId}:${e.target.beginMillis}"
                targets[id] = e.target
                Candidate(id, Kind.Event, e.title, subtitle = e.location, keywords = listOfNotNull(e.location))
            })
        }

        if (src.files) {
            val files = device.files.files(settings.files.folders, settings.files.media, settings.files.showHidden)
            if (files.isNotEmpty()) catalogs[Kind.File] = Catalog(files.map { f ->
                val id = "file:${f.target.uri}"
                targets[id] = f.target
                Candidate(id, Kind.File, f.name, usage = if (f.modifiedMillis > 0) Usage(lastUsedMillis = f.modifiedMillis) else Usage.None)
            })
        }

        if (src.history && memory.searches.isNotEmpty()) {
            catalogs[Kind.History] = Catalog(memory.searches.map { s ->
                val id = "history:${s.query}"
                targets[id] = Target.Query(s.query)
                Candidate(id, Kind.History, s.query, usage = Usage(lastUsedMillis = s.atMillis))
            })
        }

        if (settingsIndex.isNotEmpty()) {
            catalogs[Kind.Setting] = Catalog(settingsIndex.map { e ->
                val id = "setting:${e.key}"
                targets[id] = Target.Setting(e.key)
                Candidate(id, Kind.Setting, e.title, subtitle = e.section, keywords = e.keywords)
            })
        }

        return Snapshot(catalogs, targets, apps.sortedBy { it.label.lowercase() }, usage)
    }
}
