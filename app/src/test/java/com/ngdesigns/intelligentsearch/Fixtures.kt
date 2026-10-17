package com.ngdesigns.intelligentsearch

import android.content.ComponentName
import android.os.Process
import com.ngdesigns.intelligentsearch.data.DeviceSources
import com.ngdesigns.intelligentsearch.data.SnapshotBuilder
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.device.AppSource
import com.ngdesigns.intelligentsearch.data.device.CalendarSource
import com.ngdesigns.intelligentsearch.data.device.ContactEntry
import com.ngdesigns.intelligentsearch.data.device.ContactSource
import com.ngdesigns.intelligentsearch.data.device.EventEntry
import com.ngdesigns.intelligentsearch.data.device.FileSource
import com.ngdesigns.intelligentsearch.data.device.Profile
import com.ngdesigns.intelligentsearch.data.device.ShortcutEntry
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.data.device.UsageSource
import com.ngdesigns.intelligentsearch.data.history.Memory
import com.ngdesigns.intelligentsearch.data.history.PastSearch
import com.ngdesigns.intelligentsearch.data.settings.Settings
import com.ngdesigns.intelligentsearch.data.settings.Sources
import com.ngdesigns.intelligentsearch.engine.Search
import com.ngdesigns.intelligentsearch.engine.Usage
import com.ngdesigns.intelligentsearch.ui.search.SearchUiState
import com.ngdesigns.intelligentsearch.ui.search.SearchViewModel
import com.ngdesigns.intelligentsearch.ui.settings.SettingsIndex
import java.time.LocalDateTime
import kotlinx.coroutines.flow.emptyFlow

/** A believable phone's worth of stand-in data for screenshots: names are made up, numbers are 555 fiction numbers. */
object Fixtures {
    private val me get() = Process.myUserHandle()
    private fun app(label: String, pkg: String) = AppEntry(label, Target.App(ComponentName(pkg, "$pkg.Main"), me, Profile.Personal))

    val device: DeviceSources get() = DeviceSources(
        apps = object : AppSource {
            override fun apps() = listOf(
                app("Calendar", "com.google.android.calendar"), app("Calculator", "com.google.android.calculator"),
                app("Camera", "com.google.android.GoogleCamera"), app("Chrome", "com.android.chrome"), app("Clock", "com.google.android.deskclock"),
                app("Gmail", "com.google.android.gm"), app("Maps", "com.google.android.apps.maps"), app("Photos", "com.google.android.apps.photos"),
                app("Keep Notes", "com.google.android.keep"), app("YouTube", "com.google.android.youtube"), app("Spotify", "com.spotify.music"),
            )
            override fun shortcuts() = listOf(
                ShortcutEntry("New event", "Create a new event", Target.Shortcut("com.google.android.calendar", "new_event", me), "Calendar"),
            )
            override fun changes() = emptyFlow<Unit>()
        },
        usage = object : UsageSource {
            override fun hasAccess() = true
            override fun usage() = mapOf(
                "com.google.android.calendar" to Usage(20, 1L), "com.google.android.gm" to Usage(25, 1L), "com.google.android.apps.maps" to Usage(14, 1L),
                "com.android.chrome" to Usage(28, 1L), "com.spotify.music" to Usage(18, 1L), "com.google.android.keep" to Usage(9, 1L),
                "com.google.android.youtube" to Usage(22, 1L), "com.google.android.apps.photos" to Usage(12, 1L),
            )
        },
        contacts = object : ContactSource {
            override fun hasAccess() = true
            override fun contacts() = listOf(
                ContactEntry("Callum Price", listOf("+1 555-0142"), null, false, Target.Contact("content://contacts/1", "+1 555-0142")),
                ContactEntry("Ada Lovelace", listOf("+1 555-0177"), null, true, Target.Contact("content://contacts/2", "+1 555-0177")),
            )
        },
        calendar = object : CalendarSource {
            override fun hasAccess() = true
            override fun upcoming(days: Int) = listOf(EventEntry("Dentist", "Main Street Clinic", false, Target.Event(7, 0, 0)))
        },
        files = object : FileSource {
            override fun files(folders: List<String>, media: List<String>, includeHidden: Boolean) = emptyList<com.ngdesigns.intelligentsearch.data.device.FileEntry>()
        },
    )

    val settings = Settings(sources = Sources(contacts = true))
    val memory = Memory(searches = listOf(PastSearch("weather tomorrow", 3L), PastSearch("timer 12 minutes", 2L), PastSearch("directions home", 1L)))

    fun state(query: String, settings: Settings = this.settings, web: List<String> = emptyList()): SearchUiState {
        val snapshot = SnapshotBuilder(device).build(settings, memory, SettingsIndex.entries)
        val result = if (query.isBlank()) null else Search.run(query, snapshot.catalogs, SearchViewModel.preferences(settings), LocalDateTime.of(2026, 3, 10, 12, 0), 10L)
        return SearchUiState(
            settings = settings, query = query, result = result ?: if (query.isBlank()) null else result, webSuggestions = web,
            recents = memory.searches, suggestedApps = snapshot.apps.take(8), snapshot = snapshot,
        )
    }
}
