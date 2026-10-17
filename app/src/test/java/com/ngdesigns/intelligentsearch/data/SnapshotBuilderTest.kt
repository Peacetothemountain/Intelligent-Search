package com.ngdesigns.intelligentsearch.data

import android.content.ComponentName
import android.os.Process
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.device.AppSource
import com.ngdesigns.intelligentsearch.data.device.CalendarSource
import com.ngdesigns.intelligentsearch.data.device.ContactEntry
import com.ngdesigns.intelligentsearch.data.device.ContactSource
import com.ngdesigns.intelligentsearch.data.device.EventEntry
import com.ngdesigns.intelligentsearch.data.device.FileEntry
import com.ngdesigns.intelligentsearch.data.device.FileSource
import com.ngdesigns.intelligentsearch.data.device.Profile
import com.ngdesigns.intelligentsearch.data.device.ShortcutEntry
import com.ngdesigns.intelligentsearch.data.device.Suggestions
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.data.device.UsageSource
import com.ngdesigns.intelligentsearch.data.device.WebSuggestionSource
import com.ngdesigns.intelligentsearch.data.history.Launch
import com.ngdesigns.intelligentsearch.data.history.Memory
import com.ngdesigns.intelligentsearch.data.history.PastSearch
import com.ngdesigns.intelligentsearch.data.settings.Privacy
import com.ngdesigns.intelligentsearch.data.settings.Settings
import com.ngdesigns.intelligentsearch.data.settings.Sources
import com.ngdesigns.intelligentsearch.engine.Kind
import com.ngdesigns.intelligentsearch.engine.Search
import com.ngdesigns.intelligentsearch.engine.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Stand-in device data; the snapshot must reflect the settings (sources, hidden apps) and usage. */
@RunWith(RobolectricTestRunner::class)
class SnapshotBuilderTest {
    private val me = Process.myUserHandle()
    private fun app(label: String, pkg: String, profile: Profile = Profile.Personal) =
        AppEntry(label, Target.App(ComponentName(pkg, "$pkg.Main"), me, profile))

    private val device = DeviceSources(
        apps = object : AppSource {
            override fun apps() = listOf(app("Calendar", "com.google.android.calendar"), app("Camera", "com.google.android.GoogleCamera"), app("Secret", "com.example.secret"))
            override fun shortcuts() = listOf(ShortcutEntry("New event", "Create a new event", Target.Shortcut("com.google.android.calendar", "new", me), "Calendar"))
            override fun changes(): Flow<Unit> = emptyFlow()
        },
        usage = object : UsageSource {
            override fun hasAccess() = true
            override fun usage() = mapOf("com.google.android.GoogleCamera" to Usage(count = 20, lastUsedMillis = 1L))
        },
        contacts = object : ContactSource {
            override fun hasAccess() = true
            override fun contacts() = listOf(ContactEntry("Callum Price", listOf("+1 555-0142"), null, false, Target.Contact("content://contacts/1", "+1 555-0142")))
        },
        calendar = object : CalendarSource {
            override fun hasAccess() = true
            override fun upcoming(days: Int) = listOf(EventEntry("Dentist", "Main St", false, Target.Event(7, 100, 200)))
        },
        files = object : FileSource {
            override fun files(folders: List<String>, media: List<String>, includeHidden: Boolean) = listOf(FileEntry("Camping list.pdf", 10, 5, Target.File("content://f/1", "application/pdf")))
        },
    )
    private val index = listOf(SettingEntry("look.blur", "Background blur", "Look", listOf("blurry")))

    @Test fun onlyEnabledSourcesAppear() {
        val snapshot = SnapshotBuilder(device).build(Settings(), Memory(), index)
        assertEquals(setOf(Kind.App, Kind.Shortcut, Kind.Event, Kind.Setting), snapshot.catalogs.keys)
        val everything = Settings(sources = Sources(contacts = true, files = true))
        assertTrue(SnapshotBuilder(device).build(everything, Memory(), index).catalogs.keys.containsAll(listOf(Kind.Contact, Kind.File)))
    }

    @Test fun hiddenAppsNeverAppear() {
        val snapshot = SnapshotBuilder(device).build(Settings(privacy = Privacy(hiddenApps = setOf("com.example.secret"))), Memory(), index)
        assertTrue(Search.run("secret", snapshot.catalogs).sections.none { it.kind == Kind.App })
        assertTrue(snapshot.apps.none { it.target.packageName == "com.example.secret" })
    }

    @Test fun resultsMapBackToWhatTheyOpen() {
        val snapshot = SnapshotBuilder(device).build(Settings(sources = Sources(contacts = true)), Memory(), index)
        val best = Search.run("callum", snapshot.catalogs).best!!
        assertEquals(Target.Contact("content://contacts/1", "+1 555-0142"), snapshot.target(best.candidate))
        val setting = Search.run("blur", snapshot.catalogs).sections.single { it.kind == Kind.Setting }.items.first()
        assertEquals(Target.Setting("look.blur"), snapshot.target(setting.candidate))
    }

    @Test fun usageRanksAppsAndHistoryIsSearchable() {
        val memory = Memory(searches = listOf(PastSearch("camping gear", 10L)))
        val snapshot = SnapshotBuilder(device).build(Settings(), memory, index)
        assertEquals("Camera", Search.run("ca", snapshot.catalogs).sections.first { it.kind == Kind.App }.items.first().candidate.title)
        assertTrue(Search.run("camping", snapshot.catalogs).sections.any { it.kind == Kind.History })
        assertNull(SnapshotBuilder(device).build(Settings(sources = Sources(history = false)), memory, index).catalogs[Kind.History])
    }

    @Test fun suggestedAppsFallBackInOrder() {
        val apps = listOf(app("Alpha", "a.a"), app("Beta", "b.b"), app("Gamma", "c.c"), app("Work Mail", "w.w", Profile.Work))
        val now = 100L * 3600_000
        assertEquals(listOf("b.b", "a.a", "c.c"), Suggestions.apps(apps, emptyList(), mapOf("b.b" to Usage(3, now)), emptyMap(), now).map { it.target.packageName })
        assertEquals("c.c", Suggestions.apps(apps, emptyList(), emptyMap(), mapOf("c.c" to Launch(4, 1)), now).first().target.packageName)
        assertEquals(listOf("c.c", "a.a"), Suggestions.apps(apps, listOf("c.c", "a.a", "w.w"), emptyMap(), emptyMap(), now, count = 2).map { it.target.packageName })
    }

    @Test fun parsesSuggestionFeeds() {
        assertEquals(listOf("calendar 2027", "calories"), WebSuggestionSource.parse("""["cal",["calendar 2027","calories","calories"],[],{}]""", 5))
        assertEquals(emptyList<String>(), WebSuggestionSource.parse("<html>", 5))
    }
}
