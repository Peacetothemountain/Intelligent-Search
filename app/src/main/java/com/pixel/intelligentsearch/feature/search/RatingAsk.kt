package com.pixel.intelligentsearch.feature.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.system.launchSafeIntent

/**
 * The one time the app asks for a rating: a line in At a Glance, once the app has been installed a week and something
 * has been opened from search [OPENS] times. Rating it, sending feedback or "Not Now" all put it away for good.
 * Settings keeps the same two actions as plain rows, which are always there.
 *
 * Rating opens the app's own Play Store page; nothing from Google Play is built into the app for it.
 */
internal object RatingAsk {
    const val DAYS = 7
    const val OPENS = 30
    private const val SUPPORT_ADDRESS = "support.ngdesigns@gmail.com"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Whether the ask shows now: not yet answered, past the tutorial, a week in and well used. */
    fun due(firstInstallMs: Long, nowMs: Long, opens: Int, done: Boolean, tutorialDone: Boolean): Boolean =
        !done && tutorialDone && firstInstallMs > 0L && nowMs - firstInstallMs >= DAYS * DAY_MS && opens >= OPENS

    /** Puts the ask away for good. */
    fun finish(context: Context) {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit { put(SettingsManager.RATING_ASK_DONE, true) }
    }

    /** The app's Play Store page, in the Play Store app when there is one. */
    fun rate(context: Context) {
        finish(context)
        val id = context.packageName
        val inStore = Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!launchSafeIntent(context, inStore)) {
            val onTheWeb = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchSafeIntent(context, onTheWeb)
        }
    }

    /** An email to the developer with the app's version in its subject; with no email app, the address is copied. */
    fun feedback(context: Context) {
        finish(context)
        if (!launchSafeIntent(context, feedbackIntent(versionName(context)))) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Support email", SUPPORT_ADDRESS))
            Toast.makeText(context, "No email app found. Address copied.", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun feedbackIntent(version: String): Intent =
        Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_ADDRESS".toUri())
            .putExtra(Intent.EXTRA_SUBJECT, "Intelligent Search feedback${if (version.isBlank()) "" else " ($version)"}")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }
}

/** Whether At a Glance shows the rating line now; it follows the answer and the count as they change. */
@Composable
internal fun rememberRatingAskDue(): Boolean {
    val context = LocalContext.current
    val done by rememberSetting(SettingsManager.RATING_ASK_DONE)
    val opens by rememberSetting(SettingsManager.RATING_OPENS)
    val tutorialDone by rememberSetting(SettingsManager.TUTORIAL_COMPLETED)
    val installed = remember(context) { SearchHints.firstInstallMs(context) }
    return RatingAsk.due(installed, System.currentTimeMillis(), opens, done, tutorialDone)
}
