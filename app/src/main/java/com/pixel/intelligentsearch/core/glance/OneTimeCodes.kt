package com.pixel.intelligentsearch.core.glance

import android.app.Notification
import android.content.ClipData
import android.os.PersistableBundle

/** A one-time code read from a notification, shown in At a Glance until [expiresAt]. */
data class OneTimeCode(
    val code: String,
    val appLabel: String,
    val packageName: String,
    val notificationKey: String,
    val postedAt: Long
) {
    val expiresAt: Long get() = postedAt + OneTimeCodes.LIFETIME_MS

    /** Shown from the moment it was posted until ten minutes later; a clock set back never revives it early. */
    fun isFresh(now: Long): Boolean = now in postedAt..<expiresAt
}

/**
 * Finds one-time codes ("Your verification code is 482913") in notifications from messaging and email apps. Pure, so
 * every positive and negative case is unit tested: a code is 4 to 8 digits (or two groups of 3 or 4 split by a space
 * or dash), standing on its own, in a notification whose words say it is a code; amounts, times, dates, order
 * numbers, phone numbers and promo codes are not codes.
 */
object OneTimeCodes {

    /** How long a code shows: ten minutes after its notification was posted, as codes themselves expire. */
    const val LIFETIME_MS = 10 * 60 * 1000L

    /** Messaging and email apps whose notifications don't always carry the message or email category. */
    private val MESSAGING_PACKAGES = setOf(
        "com.google.android.apps.messaging", "com.google.android.gm", "com.android.mms", "com.android.messaging",
        "com.samsung.android.messaging", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
        "ch.protonmail.android", "me.proton.android.mail", "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thoughtcrime.securesms",
        "com.fsck.k9", "com.google.android.apps.googlevoice", "com.facebook.orca", "com.apple.android.mail"
    )

    /** Words that say a number is a code; matched as whole words, case-insensitive. */
    private val CONTEXT = Regex(
        """\b(code|codes|otp|verification|verify|passcode|one[- ]time|2fa|two[- ]factor|security code|login code|sign[- ]in code|auth(entication)? code)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Words that make a "code" a discount rather than a sign-in. */
    private val PROMOTION = Regex("""\b(promo|coupon|discount|voucher|% off|off your)\b""", RegexOption.IGNORE_CASE)

    /**
     * A candidate: 4 to 8 digits, or 3+3 / 4+4 digit groups split by one space or dash. Not part of a longer number,
     * a word, an amount (currency sign before, % after), a decimal, a time or date (":" "/" "." between digits),
     * an order or ticket number ("#") or a phone number ("+", or digits and a dash before it). A letter and a dash
     * before it ("G-482913", as Google sends them) still make a code.
     */
    private val CANDIDATE = Regex(
        """(?<![\p{L}\d$€£¥₹#+.,:/\\])(?<!\d-)(\d{3}[ -]\d{3}|\d{4}[ -]\d{4}|\d{4,8})(?![\p{L}\d%€$£¥₹]|[.,:/\\-]\d)"""
    )

    /** Whether [packageName]'s notification can hold a code: a messaging or email app, or the default SMS app. */
    fun isMessagingSource(packageName: String, category: String?, isMessagingStyle: Boolean, smsPackage: String?): Boolean =
        category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL || isMessagingStyle ||
            packageName in MESSAGING_PACKAGES || (smsPackage != null && packageName == smsPackage)

    /** The code in [text], or null when it holds none. */
    fun findCode(text: String): String? {
        if (text.isBlank() || !CONTEXT.containsMatchIn(text) || PROMOTION.containsMatchIn(text)) return null
        val keywords = CONTEXT.findAll(text).map { it.range.first }.toList()
        // Several numbers ("code 4821, valid 10 minutes"): the one nearest a code word.
        return CANDIDATE.findAll(text)
            .filterNot { looksLikeYear(it.value) && keywords.none { k -> distance(k, it.range) <= 24 } }
            .minByOrNull { match -> keywords.minOf { distance(it, match.range) } }
            ?.value?.filter(Char::isDigit)
    }

    /** The code in [entry]'s text (or its title and text together) when it comes from a messaging or email app, or null. */
    fun find(entry: IndexedNotification, smsPackage: String?): OneTimeCode? {
        if (!isMessagingSource(entry.packageName, entry.category, entry.isMessagingStyle, smsPackage)) return null
        val code = findCode(entry.text) ?: findCode("${entry.title} ${entry.text}") ?: return null
        return OneTimeCode(code, entry.appLabel, entry.packageName, entry.key, entry.postedAt)
    }

    /** The codes among [entries], newest first, one per code; [OneTimeCode.isFresh] decides which still show. */
    fun fromNotifications(entries: List<IndexedNotification>, smsPackage: String?): List<OneTimeCode> =
        entries.mapNotNull { find(it, smsPackage) }.sortedByDescending { it.postedAt }.distinctBy { it.code }

    /**
     * The clipboard entry for [code], marked sensitive so the keyboard's clipboard strip, the clipboard preview and
     * other apps reading the clipboard treat it as a password (Android 13's
     * [android.content.ClipDescription.EXTRA_IS_SENSITIVE]; keyboards honour the same key before 13).
     */
    fun clipFor(code: String): ClipData = ClipData.newPlainText("One-Time Code", code).apply {
        description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }

    /** [android.content.ClipDescription.EXTRA_IS_SENSITIVE], spelled out so it also reaches keyboards on Android 12. */
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private fun looksLikeYear(value: String) = value.length == 4 && value.toInt() in 1900..2099

    private fun distance(at: Int, range: IntRange): Int = when {
        at < range.first -> range.first - at
        at > range.last -> at - range.last
        else -> 0
    }
}
