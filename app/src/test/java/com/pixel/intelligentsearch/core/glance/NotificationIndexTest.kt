package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
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

    /** Notifications are matched the way every other result is (QueryNormalizer), not by a looser fold of their own. */
    @Test
    fun searchFoldsTextLikeTheRestOfSearch() {
        val entries = listOf(
            entry("lotus", "ハス", "Garden"),
            entry("bus", "バス", "Leaves at 8"),
            entry("street", "Weißstraße 5", "Your parcel"),
            entry("hindi", "किताब", "Library")
        )
        // A voicing mark makes another letter: "バス" (bus) is not "ハス" (lotus).
        assertEquals(listOf("bus"), NotificationIndex.search(entries, "バス").map { it.key })
        assertEquals(listOf("lotus"), NotificationIndex.search(entries, "ハス").map { it.key })
        // Typed without the special letters, as other results allow.
        assertEquals(listOf("street"), NotificationIndex.search(entries, "weissstrasse").map { it.key })
        // A Devanagari vowel sign is part of the word.
        assertEquals(listOf("hindi"), NotificationIndex.search(entries, "किताब").map { it.key })
        assertTrue(NotificationIndex.search(entries, "कतब").isEmpty())
        // Punctuation doesn't have to be typed.
        assertEquals(listOf("bus"), NotificationIndex.search(entries, "at 8").map { it.key })
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
        val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        val component = NotificationAccessListener.component(context)
        val index = NotificationIndex()

        notifications.setNotificationListenerAccessGranted(component, true)
        index.post(entry("a", "Ana"))
        NotificationAccess.syncIndex(context, index)
        assertEquals(1, index.entries.value.size)

        notifications.setNotificationListenerAccessGranted(component, false)
        NotificationAccess.syncIndex(context, index)
        assertTrue(index.entries.value.isEmpty())

        // Granted again, with no switch of the app's own in the way, what is read stays.
        notifications.setNotificationListenerAccessGranted(component, true)
        index.post(entry("a", "Ana"))
        NotificationAccess.syncIndex(context, index)
        assertEquals(1, index.entries.value.size)
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
    fun secretNotificationsAreNeverIndexed() {
        fun code(visibility: Int) = Notification.Builder(context, "c")
            .setContentTitle("Your bank")
            .setContentText("Your statement is ready")
            .setVisibility(visibility)
            .build()
        assertNull(NotificationExtractor.fromNotification("s", "com.example.bank", "Bank", 0L, code(Notification.VISIBILITY_SECRET), emptySet()))
        // Kept off the lock screen by its channel or the user rather than by the app.
        assertNull(
            NotificationExtractor.fromNotification(
                "c", "com.example.bank", "Bank", 0L, code(Notification.VISIBILITY_PUBLIC), emptySet(),
                lockscreenVisibilityOverride = Notification.VISIBILITY_SECRET
            )
        )
        // Private is every notification's default; it only hides the content on a lock screen set to.
        assertEquals("Your statement is ready", NotificationExtractor.fromNotification("p", "com.example.bank", "Bank", 0L, code(Notification.VISIBILITY_PRIVATE), emptySet())?.text)
        assertEquals(
            "Your statement is ready",
            NotificationExtractor.fromNotification(
                "o", "com.example.bank", "Bank", 0L, code(Notification.VISIBILITY_PRIVATE), emptySet(),
                lockscreenVisibilityOverride = NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE
            )?.text
        )
    }

    @Test
    fun aOneTimeCodeIsNeverIndexedEvenWhenOnlyPrivate() {
        fun posted(title: String, text: String) = NotificationExtractor.fromNotification(
            "k", "com.example.bank", "Bank", 0L,
            Notification.Builder(context, "c").setContentTitle(title).setContentText(text)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build(),
            emptySet()
        )
        // Posted as private, the default, these were indexed and found by searching the code or the bank.
        for ((title, text) in listOf(
            "Your bank" to "Code 482913",
            "Messages" to "Your verification code is 482913. Don't share it.",
            "Acme" to "482913 is your Acme security code",
            "Sign in" to "Use 482 913 as your one-time passcode",
            "Login" to "OTP: 4829-1300",
            "Google" to "G-482913 is your Google verification code.",
            "Your login code" to "4829",
            "2FA" to "Enter 48291300 to sign in"
        )) {
            assertNull("$title: $text", posted(title, text))
        }
        // A number with no word saying it's a code, or one far from such a word, is still found.
        for ((title, text) in listOf(
            "Shop" to "Your order 123456 has shipped",
            "Bank" to "You paid 1250 to Sam",
            "Delivery" to "Parcel 123456 arrives today. Questions about the delivery or how to return it? Enter your code online."
        )) {
            assertEquals(text, posted(title, text)?.text)
        }
    }

    @Test
    fun aOneTimeCodeIsKnownInOtherLanguages() {
        // Only English whole words were known, so these were indexed and found by searching the code.
        for (text in listOf(
            "Tu código de verificación es 482913",
            "Tu clave de acceso: 482913",
            "Seu código é 482913",
            "Ihr Bestätigungscode lautet 482913",
            "Sicherheitscode: 482913",
            "Votre code est 482913",
            "482913 est votre mot de passe à usage unique",
            "Il tuo codice è 482913",
            "Uw verificatiecode is 482913",
            "Ваш код подтверждения: 482913",
            "Ваш код: 482913",
            "Twój kod weryfikacyjny to 482913",
            "Doğrulama kodunuz 482913",
            "Kode verifikasi Anda 482913",
            "Kod pengesahan anda ialah 482913",
            "您的验证码是482913",
            "您的驗證碼為482913",
            "校验码：482913",
            "認証コードは482913です",
            "確認コード：482913",
            "인증번호 [482913]",
            "인증 코드는 482913입니다",
            "رمز التحقق الخاص بك هو 482913",
            "आपका कोड 482913 है",
            "ओटीपी 482913"
        )) {
            assertTrue(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
        for (text in listOf(
            "Your order 123456 has shipped",
            "Su pedido 123456 ha sido enviado",
            "Ihre Bestellung 123456 wurde versandt",
            "Votre commande 123456 a été expédiée",
            "您的订单123456已发货",
            "Scan the barcode 12345678 at the till",
            "Postcode 10115, Berlin",
            "Episode 1234 is out now",
            "Kodak 2024 calendar"
        )) {
            assertFalse(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
    }

    @Test
    fun aOneTimeCodeIsKnownInMoreShapesAndPostalCodesAreNot() {
        // These were indexed and found by searching the code.
        for (text in listOf(
            "Your temporary password: 48291756",
            "Enter 482913 to sign in",
            "Your OTP-482913",
            "Your token is 123456",
            "Your authentication number is 482913",
            "Ihr Einmalpasswort: 123456",
            "Your verification code is 12 34 56",
            "Your code is 1 2 3 4 5 6",
            "Your code: K7P-2Q9",
            "Steam Guard code: 4X7QF",
            "Your code is A1B2C3",
            "رمز التحقق ١٢٣٤٥٦",
            "आपका ओटीपी १२३४५६ है",
            "Mã xác thực của bạn là 482913",
            "קוד האימות שלך הוא 482913"
        )) {
            assertTrue(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
        // These were hidden from search as if they were sign-in codes.
        for (text in listOf(
            "Please enter the pincode 560001 for delivery",
            "Your PIN code 560001 is not serviceable for delivery",
            "Your zip code 94103 is in the delivery area",
            "Dress code: black tie. Doors open at 2000",
            "Read the source code at github.com/x/1234",
            "Use promo code SAVE20 by 2026"
        )) {
            assertFalse(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
        // A card's PIN is still a secret.
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your new card's PIN code is 4821"))
        // Beside a shop's offer a letters-and-digits sign-in code was taken for a promo code and left searchable.
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your code is A1B2C3. Free shipping on orders over $50"))
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your code: K7P-2Q9. 20% off your next order"))
        // The shop's own codes still are promo codes.
        // "GET20OFF" (a word, its amount and OFF) was hidden as a sign-in code.
        for (text in listOf(
            "Use code SPRING2026 for 25% off", "Flash sale: code BF2026 saves you more", "Code 50OFF at checkout",
            "Use code GET20OFF at checkout"
        )) {
            assertFalse(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
        // A number in a phone number isn't a code beside "OTP".
        assertFalse(NotificationExtractor.looksLikeOneTimeCode("Questions about OTP? Call 555-12345678901"))
    }

    @Test
    fun promoCodesPinsOnAMapAndAddressesAreNotSignInCodes() {
        // These were hidden from search as if they were sign-in codes.
        for (text in listOf(
            "Use code FALL2026 at checkout for 20% off",
            "Use code SUMMER2026 for free shipping",
            "Dropped pin near 2045 Oak Ave",
            "Dropped pin https://maps.google.com/?q=37.4219,-122.0840",
            "Pin 1234 Main St to your map",
            "Error code 50058 when signing in? Try again",
            "Please verify your address: 1600 Amphitheatre Pkwy",
            "Course code CS1010 starts Monday",
            "Your tax code 1257L has changed",
            "Meeting at 1400 in room 2300, bring the verification forms"
        )) {
            assertFalse(text, NotificationExtractor.looksLikeOneTimeCode(text))
        }
        // A code of digits alone beside a shop's words, or named outright, is still one.
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Use code 482913 to complete checkout"))
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your verification code for checkout is 482913"))
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your PIN is 4821. Do not share it"))
    }

    @Test
    fun aParcelLockerOrDeliveryPinCodeIsAnAccessCode() {
        // These were taken for India's postal PIN code because "parcel" or "delivery" was near.
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Your PIN code for the parcel locker is 1234"))
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("Share this PIN code 4821 with the delivery agent"))
        // As before: the Packstation PIN and a postal PIN code.
        assertTrue(NotificationExtractor.looksLikeOneTimeCode("DHL: Your Packstation PIN is 4821"))
        assertFalse(NotificationExtractor.looksLikeOneTimeCode("Your PIN code 560001 is not serviceable for delivery"))
        assertFalse(NotificationExtractor.looksLikeOneTimeCode("Deliver to PIN code 560 001, Bengaluru"))
    }

    @Test
    fun androidSeventeensClockTimerIsATimer() {
        // As Android 17's Clock posts a running 10-minute timer: MetricStyle, no title and no chronometer; its time
        // counts down to zeroElapsedRealtime, in time since boot (captured from a Pixel with dumpsys notification).
        fun clockTimer(value: Bundle, label: String) = Notification.Builder(context, "Timers v2").build().apply {
            extras.putBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
            extras.putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$MetricStyle")
            extras.putInt("android.metrics.criticalIndex", 0)
            extras.putParcelableArrayList(
                "android.metrics",
                arrayListOf(Bundle().apply { putCharSequence("label", label); putBundle("value", value); putInt("semanticStyle", 0) })
            )
        }
        val running = clockTimer(
            Bundle().apply { putLong("zeroElapsedRealtime", 6_895_293L); putInt("format", 2); putInt("_type", 1); putBoolean("countDown", true) },
            "10m Timer"
        )
        val bootTime = 1_793_314_000_000L
        val clock = setOf("com.google.android.deskclock")
        val entry = NotificationExtractor.fromNotification(
            "t", "com.google.android.deskclock", "Clock", 0L, running, clock, bootTime = bootTime
        )
        assertEquals(IndexedNotification.Chronometer(countDown = true, baseTime = bootTime + 6_895_293L), entry?.chronometer)
        assertEquals("10m Timer", entry?.title)
        val timer = timersFrom(listOfNotNull(entry)).single()
        assertEquals(GlanceTimer.Kind.Timer, timer.kind)
        assertEquals("10m Timer", timer.label)
        // Not from a clock: not a timer.
        assertNull(NotificationExtractor.chronometerOf(running, "com.example.game", clock, bootTime))

        // A paused timer has no running time, as it had none before.
        val paused = clockTimer(
            Bundle().apply { putLong("pausedDuration", 479_224L); putInt("format", 2); putInt("_type", 1); putBoolean("countDown", true) },
            "Timer paused"
        )
        assertNull(NotificationExtractor.chronometerOf(paused, "com.google.android.deskclock", clock, bootTime))

        // A stopwatch counting up from a wall-clock zero time.
        val stopwatch = clockTimer(Bundle().apply { putLong("zeroTime", 5_000L); putBoolean("countDown", false) }, "Stopwatch")
        assertEquals(
            IndexedNotification.Chronometer(countDown = false, baseTime = 5_000L),
            NotificationExtractor.chronometerOf(stopwatch, "com.google.android.deskclock", clock, bootTime)
        )
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
