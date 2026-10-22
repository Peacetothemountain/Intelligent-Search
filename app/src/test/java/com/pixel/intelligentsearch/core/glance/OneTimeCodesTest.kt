package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.copyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One-time codes: which notifications hold one, which numbers are not codes, when they expire, and the sensitive copy. */
@RunWith(RobolectricTestRunner::class)
class OneTimeCodesTest {

    @Test
    fun findsTheCodesMessagesAndEmailsSend() {
        val cases = mapOf(
            "Your verification code is 482913" to "482913",
            "G-482913 is your Google verification code." to "482913",
            "123456 is your OTP for login. Do not share it." to "123456",
            "Use 4821 to verify your account" to "4821",
            "Your Uber code: 4821. Never share this code." to "4821",
            "Your sign-in code: 123-456" to "123456",
            "Your one-time passcode is 90210554" to "90210554",
            "Votre code de vérification est 7391" to "7391",
            // The number nearest the word "code", not the validity period or a year.
            "Code 739104, valid for 10 minutes. © 2026 Example" to "739104"
        )
        for ((text, code) in cases) assertEquals(text, code, OneTimeCodes.findCode(text))
    }

    @Test
    fun leavesOutNumbersThatAreNotCodes() {
        val notCodes = listOf(
            "Lunch tomorrow at 12?",
            "Your order #482913 has shipped, tracking code to follow",
            "Use promo code 4821 at checkout",
            "Use code SAVE20 for 20% off",
            "Call me at 555-123-4567 about the code",
            "Meeting at 10:30, bring the access code",
            "Code review on 2026-10-22",
            "Your balance is 4821.50, see the code of conduct",
            "You paid $1234 with code",
            "Account 12345678901 verification pending",
            "Your package 4821 is on its way"
        )
        for (text in notCodes) assertNull(text, OneTimeCodes.findCode(text))
    }

    private fun entry(packageName: String, text: String, category: String? = null, messaging: Boolean = false, postedAt: Long = 1_000L) =
        IndexedNotification(
            key = "0|$packageName|1", packageName = packageName, appLabel = "App", title = "Bank",
            text = text, postedAt = postedAt, category = category, isMessagingStyle = messaging
        )

    @Test
    fun onlyMessagingAndEmailAppsCarryCodes() {
        val text = "Your verification code is 482913"
        assertNull(OneTimeCodes.find(entry("com.example.game", text), smsPackage = null))
        assertNotNull(OneTimeCodes.find(entry("com.google.android.apps.messaging", text), smsPackage = null))
        assertNotNull(OneTimeCodes.find(entry("com.google.android.gm", text), smsPackage = null))
        assertNotNull(OneTimeCodes.find(entry("com.example.chat", text, category = Notification.CATEGORY_MESSAGE), null))
        assertNotNull(OneTimeCodes.find(entry("com.example.mail", text, category = Notification.CATEGORY_EMAIL), null))
        assertNotNull(OneTimeCodes.find(entry("com.example.chat", text, messaging = true), null))
        assertNotNull(OneTimeCodes.find(entry("com.example.sms", text), smsPackage = "com.example.sms"))
    }

    @Test
    fun theTitleCanSayItIsACode() {
        val found = OneTimeCodes.find(
            entry("com.google.android.apps.messaging", "482913").copy(title = "Verification code"), smsPackage = null
        )
        assertEquals("482913", found?.code)
    }

    @Test
    fun aCodeShowsForTenMinutesFromItsNotification() {
        val code = OneTimeCode("482913", "Messages", "com.google.android.apps.messaging", "k", postedAt = 100_000L)
        assertTrue(code.isFresh(100_000L))
        assertTrue(code.isFresh(100_000L + 9 * 60_000L + 59_999L))
        assertFalse(code.isFresh(100_000L + 10 * 60_000L))
        // A clock set back before the notification came doesn't show it.
        assertFalse(code.isFresh(99_999L))
    }

    @Test
    fun theNewestOfEachCodeIsKept() {
        val entries = listOf(
            entry("com.google.android.apps.messaging", "Your code is 111111", postedAt = 1).copy(key = "a"),
            entry("com.google.android.apps.messaging", "Your code is 222222", postedAt = 3).copy(key = "b"),
            entry("com.google.android.apps.messaging", "Reminder: your code is 111111", postedAt = 2).copy(key = "c")
        )
        val codes = OneTimeCodes.fromNotifications(entries, smsPackage = null)
        assertEquals(listOf("222222", "111111"), codes.map { it.code })
        assertEquals("c", codes.last().notificationKey)
    }

    @Test
    fun theCopyIsMarkedSensitive() {
        assertEquals(ClipDescription.EXTRA_IS_SENSITIVE, OneTimeCodes.EXTRA_IS_SENSITIVE)
        val clip = OneTimeCodes.clipFor("482913")
        assertEquals("482913", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    fun copyingPutsTheSensitiveCodeOnTheClipboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(copyCode(context, "482913"))
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        assertEquals("482913", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }
}
