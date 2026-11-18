package com.pixel.intelligentsearch.core.wellbeing

import java.time.LocalDate

/** One app on one day. Times in seconds. */
class AppDay(
    val totalSec: Int,
    /** Seconds on screen in each hour of the day. */
    val hours: IntArray,
    /** Times opened: stretches in front, with gaps under 30 seconds merged. */
    val opens: Int,
    val longestSec: Int,
    /** Stretches of 15 minutes or more. */
    val over15: Int,
    /** Notifications it sent (that showed or alerted). */
    val notifications: Int,
    /** Of those, how many it came to the front within 10 minutes of, when it wasn't already in front. */
    val followed: Int
)

/**
 * One day of Wellbeing: what the page keeps for it in its own daily summary (see [HistoryStore]). Times of day are
 * minutes from that day's midnight ([phoneDownStartMin] is negative when the phone went down the evening before).
 */
class DaySummary(
    val date: LocalDate,
    /** Time any counted app was on screen: split screen counts once. */
    val totalSec: Int,
    val hours: IntArray,
    val unlocks: IntArray,
    val notifications: IntArray,
    /** By app key (a package; Gemini's activities inside the Google app count as Gemini, see [Attribution]). */
    val apps: Map<String, AppDay>,
    val unlocksAfterNotification: Int,
    /** For unlocks with no notification just before: the first app opened, and how often. */
    val firstAfterOwnUnlock: Map<String, Int>,
    val firstUseMin: Int?,
    val firstApp: String?,
    val lastUseMin: Int?,
    val phoneDownStartMin: Int?,
    val awayStartMin: Int?,
    val awayLenMin: Int?,
    val notificationsWhileDown: Int,
    /** The whole day is recorded (it has ended and the phone's records cover it from midnight). */
    val complete: Boolean,
    /**
     * The phone was in use as the day began, with no night's rest before the first use: [firstUseMin] is then null, as
     * there was no first pick-up that day to name.
     */
    val inUseAtMidnight: Boolean = false,
    /** How long the phone was down for the night, in real minutes (the clock can jump an hour in a night). */
    val phoneDownLenMin: Int? = null,
    /** Brief looks at the phone inside that night, and the time on screen they added up to. */
    val nightPickups: Int = 0,
    val nightUseSec: Int = 0,
    /** The longest sitting: on screen with no break of a minute or more. Its start and its length. */
    val longestOnStartMin: Int? = null,
    val longestOnLenMin: Int? = null,
    /** The rules it was worked out by ([DayCalculator.RULES]); 0 for a day saved before days carried them. */
    val rules: Int = 0
) {
    val unlockTotal: Int get() = unlocks.sum()
    val notificationTotal: Int get() = notifications.sum()

    /** The same day, not taken as whole: shown, but neither counted in averages nor kept in the summary. */
    fun notWhole() = DaySummary(
        date, totalSec, hours, unlocks, notifications, apps, unlocksAfterNotification, firstAfterOwnUnlock,
        firstUseMin, firstApp, lastUseMin, phoneDownStartMin, awayStartMin, awayLenMin, notificationsWhileDown,
        complete = false, inUseAtMidnight = inUseAtMidnight, phoneDownLenMin = phoneDownLenMin, nightPickups = nightPickups,
        nightUseSec = nightUseSec, longestOnStartMin = longestOnStartMin, longestOnLenMin = longestOnLenMin, rules = rules
    )

    companion object {
        /** A day with no records (today before the phone's records are read, or a day it didn't keep). */
        fun empty(date: LocalDate) = DaySummary(
            date, 0, IntArray(24), IntArray(24), IntArray(24), emptyMap(), 0, emptyMap(),
            null, null, null, null, null, null, 0, complete = false
        )
    }
}
