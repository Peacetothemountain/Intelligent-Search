package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * The notification index: what a search finds and in what order, that it forgets everything when access or the
 * opt-in goes, and that nothing it holds is ever written anywhere.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationIndexTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun entry(key: String, title: String, text: String = "", app: String = "Messages", postedAt: Long = 0L) =
        IndexedNotification(key, "com.example.$app", app, title, text, postedAt)

    @After fun tearDown() {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun aSearchMatchesEveryWordInTheTitleTextOrApp() {
        val entries = listOf(
            entry("1", "Ana", "Dinner at the café at 8?"),
            entry("2", "Delivery", "Your parcel from the cafe supplies shop", app = "Shop"),
            entry("3", "Bob", "See you tomorrow", app = "Signal")
        )
        assertEquals(listOf("1", "2"), NotificationIndex.search(entries, "cafe").map { it.key })
        assertEquals(listOf("1"), NotificationIndex.search(entries, "dinner CAFÉ").map { it.key })
        assertEquals(listOf("3"), NotificationIndex.search(entries, "signal").map { it.key })
        assertTrue(NotificationIndex.search(entries, "lunch").isEmpty())
    }

    @Test
    fun aTitleStartingWithTheQueryComesFirstThenNewest() {
        val entries = listOf(
            entry("old-text", "Reminder", "Ana's birthday", postedAt = 1),
            entry("new-text", "Calendar", "Call Ana", postedAt = 5),
            entry("word", "Message from Ana", postedAt = 2),
            entry("prefix", "Ana", "Hi!", postedAt = 0)
        )
        assertEquals(listOf("prefix", "word", "new-text", "old-text"), NotificationIndex.search(entries, "ana", limit = 10).map { it.key })
        assertEquals(2, NotificationIndex.search(entries, "ana", limit = 2).size)
    }

    @Test
    fun aOneLetterQueryFindsNothing() {
        assertTrue(NotificationIndex.search(listOf(entry("1", "Ana")), "a").isEmpty())
        assertTrue(NotificationIndex.search(listOf(entry("1", "Ana")), "  ").isEmpty())
    }

    @Test
    fun anUpdatedNotificationReplacesItsOldSelf() {
        val index = NotificationIndex()
        index.post(entry("k", "Ana", "First", postedAt = 1))
        index.post(entry("other", "Bob", postedAt = 2))
        index.post(entry("k", "Ana", "Second", postedAt = 3))
        assertEquals(listOf("Second", ""), index.entries.value.map { it.text })
        index.remove("k")
        assertEquals(listOf("other"), index.entries.value.map { it.key })
    }

    @Test
    fun theIndexIsCapped() {
        val index = NotificationIndex()
        repeat(NotificationIndex.MAX_ENTRIES + 20) { index.post(entry("$it", "n$it", postedAt = it.toLong())) }
        assertEquals(NotificationIndex.MAX_ENTRIES, index.entries.value.size)
        // The newest stay.
        assertEquals("${NotificationIndex.MAX_ENTRIES + 19}", index.entries.value.first().key)
    }

    private class Recorder {
        var unbinds = 0
    }

    private fun sbn(key: String, title: String): StatusBarNotification {
        val notification = Notification.Builder(context, "c").setContentTitle(title).setContentText("text").build()
        return StatusBarNotification("com.example.app", "com.example.app", key.hashCode(), key, 0, 0, 0, notification, android.os.Process.myUserHandle(), 0L)
    }

    private fun indexer(index: NotificationIndex, optedIn: () -> Boolean, recorder: Recorder) = NotificationIndexer(
        index = index,
        isOptedIn = optedIn,
        extract = { IndexedNotification(it.key, it.packageName, "App", it.notification.extras.getString(Notification.EXTRA_TITLE).orEmpty(), "", 0L) },
        unbind = { recorder.unbinds++ }
    )

    @Test
    fun theListenerIndexesOnlyWhileTheUserHasOptedIn() {
        val index = NotificationIndex()
        val recorder = Recorder()
        var optedIn = false
        val indexer = indexer(index, { optedIn }, recorder)

        indexer.onConnected(listOf(sbn("a", "Ana")))
        assertTrue(index.entries.value.isEmpty())
        assertEquals("connected without the opt-in, the listener lets go at once", 1, recorder.unbinds)

        optedIn = true
        indexer.onConnected(listOf(sbn("a", "Ana")))
        indexer.onPosted(sbn("b", "Bob"))
        assertEquals(setOf("Ana", "Bob"), index.entries.value.map { it.title }.toSet())
    }

    @Test
    fun revokingAccessForgetsEverything() {
        val index = NotificationIndex()
        val indexer = indexer(index, { true }, Recorder())
        indexer.onConnected(listOf(sbn("a", "Ana")))
        assertEquals(1, index.entries.value.size)
        // Android disconnects the listener when access is revoked.
        indexer.onDisconnected()
        assertTrue(index.entries.value.isEmpty())
        // A notification still queued from before the revoke can't come back.
        indexer.onPosted(sbn("b", "Bob"))
        assertTrue(index.entries.value.isEmpty())
    }

    @Test
    fun switchingTheOptInOffForgetsEverythingAndUnbinds() {
        val index = NotificationIndex()
        val recorder = Recorder()
        val indexer = indexer(index, { true }, recorder)
        indexer.onConnected(listOf(sbn("a", "Ana")))
        indexer.onOptInChanged(false)
        assertTrue(index.entries.value.isEmpty())
        assertEquals(1, recorder.unbinds)
    }

    @Test
    fun openingSearchAfterAccessIsRevokedForgetsWhatWasRead() {
        val prefs = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().put(SettingsManager.SEARCH_NOTIFICATIONS, true).commit()
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val component = NotificationSearchListener.component(context)
        val index = NotificationIndex()

        notifications.setNotificationListenerAccessGranted(component, true)
        index.post(entry("a", "Ana"))
        NotificationAccess.syncIndex(context, index)
        assertEquals(1, index.entries.value.size)

        notifications.setNotificationListenerAccessGranted(component, false)
        NotificationAccess.syncIndex(context, index)
        assertTrue(index.entries.value.isEmpty())

        // The switch off, with access still granted, forgets them too.
        notifications.setNotificationListenerAccessGranted(component, true)
        index.post(entry("a", "Ana"))
        prefs.edit().put(SettingsManager.SEARCH_NOTIFICATIONS, false).commit()
        NotificationAccess.syncIndex(context, index)
        assertTrue(index.entries.value.isEmpty())
    }

    @Test
    fun nothingIndexedIsWrittenAnywhere() {
        val marker = "zebra-crossing-7731"
        val before = filesUnder(context.dataDir).associateWith { it.length() }
        val index = NotificationIndex()
        index.post(entry("a", "Ana $marker", "Text $marker"))
        index.replaceAll(listOf(entry("b", "Bob $marker")))
        assertEquals("the index only holds memory", 1, index.entries.value.size)
        for (file in filesUnder(context.dataDir)) {
            if (before[file] == file.length()) continue
            assertFalse("${file.path} holds notification text", file.readBytes().toString(Charsets.ISO_8859_1).contains(marker))
        }
        // A new process starts with an empty index: nothing is restored from anywhere.
        assertTrue(NotificationIndex().entries.value.isEmpty())
        // Nor do the settings hold anything but the switches.
        assertTrue(context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).all.values.none { "$it".contains(marker) })
    }

    private fun filesUnder(dir: File): List<File> = dir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun theExtractorKeepsWhatANotificationShows() {
        val inbox = Notification.Builder(context, "c")
            .setContentTitle("2 new emails")
            .setStyle(Notification.InboxStyle().addLine("Ana: Lunch?").addLine("Bob: Invoice"))
            .setCategory(Notification.CATEGORY_EMAIL)
            .build()
        val entry = NotificationExtractor.fromNotification("k", "com.google.android.gm", "Gmail", 5L, inbox, emptySet())
        assertNotNull(entry)
        assertEquals("2 new emails", entry!!.title)
        assertEquals("Ana: Lunch?\nBob: Invoice", entry.text)
        assertEquals(Notification.CATEGORY_EMAIL, entry.category)

        val chat = Notification.Builder(context, "c")
            .setStyle(
                Notification.MessagingStyle(android.app.Person.Builder().setName("Me").build())
                    .setConversationTitle("Family")
                    .addMessage("Dinner at 8", 1L, android.app.Person.Builder().setName("Ana").build())
            )
            .build()
        val chatEntry = NotificationExtractor.fromNotification("m", "com.whatsapp", "WhatsApp", 5L, chat, emptySet())
        assertEquals("Family", chatEntry?.title)
        assertEquals("Dinner at 8", chatEntry?.text)
        assertTrue(chatEntry!!.isMessagingStyle)
    }

    @Test
    fun theExtractorLeavesOutSummariesMediaAndEmptyNotifications() {
        val summary = Notification.Builder(context, "c").setContentTitle("3 messages").setGroup("g").setGroupSummary(true).build()
        assertNull(NotificationExtractor.fromNotification("s", "p", "App", 0L, summary, emptySet()))
        val player = Notification.Builder(context, "c").setContentTitle("Song").build().apply {
            extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession(context, "t").sessionToken)
        }
        assertNull(NotificationExtractor.fromNotification("m", "p", "App", 0L, player, emptySet()))
        val empty = Notification.Builder(context, "c").build().apply { extras = Bundle() }
        assertNull(NotificationExtractor.fromNotification("e", "p", "App", 0L, empty, emptySet()))
    }

    @Test
    fun onlyAClockAppsChronometerIsATimer() {
        val timer = Notification.Builder(context, "c")
            .setContentTitle("5 min timer")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(90_000L)
            .build()
        val clock = setOf("com.google.android.deskclock")
        val chronometer = NotificationExtractor.chronometerOf(timer, "com.google.android.deskclock", clock)
        assertEquals(IndexedNotification.Chronometer(countDown = true, baseTime = 90_000L), chronometer)
        assertNull(NotificationExtractor.chronometerOf(timer, "com.example.game", clock))

        val stopwatch = Notification.Builder(context, "c").setUsesChronometer(true).setCategory(Notification.CATEGORY_STOPWATCH).build()
        assertEquals(false, NotificationExtractor.chronometerOf(stopwatch, "com.example.stopwatch", clock)?.countDown)
    }
}
