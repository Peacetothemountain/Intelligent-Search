package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.feature.settings.rememberApproximateLocationRequest
import com.pixel.intelligentsearch.feature.settings.PermissionRationaleDialog
import com.pixel.intelligentsearch.core.system.openSettingsPageToGrant
import com.pixel.intelligentsearch.core.system.GoogleApps
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.answers.GoogleTranslate
import com.pixel.intelligentsearch.core.answers.HomeLocationCodec
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.ui.DefinitionOneBox
import com.pixel.intelligentsearch.core.ui.ScreenTimeOneBox
import com.pixel.intelligentsearch.core.ui.TranslationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherLocationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherOneBox
import com.pixel.intelligentsearch.core.util.resolveActivityCompat

/** Where today's screen time is broken down in full: Digital Wellbeing (Pixel's, or Samsung's), if the phone has it. */
internal fun digitalWellbeingIntent(context: Context): Intent? =
    listOf("com.google.android.apps.wellbeing", "com.samsung.android.forest")
        .firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)


/**
 * The overlay's live instant answer for the current query, with its actions: web searches and addresses go through
 * the overlay's own launchers (so Tor is honoured), and permissions are asked for at the moment of use, after a
 * dialog saying why, as Search Sources does.
 */
@Composable
internal fun OverlayLiveAnswer(s: OverlayScope, answer: LiveAnswer) {
    val context = LocalContext.current
    when (answer) {
        is LiveAnswer.Definition -> DefinitionOneBox(
            entry = answer.entry,
            onOpenFullDefinition = { s.launchWebSearch("define ${answer.entry.word}") }
        )
        is LiveAnswer.Weather -> WeatherAnswer(s, answer)
        is LiveAnswer.WeatherNeedsLocation -> WeatherLocationPrompt(s)
        is LiveAnswer.Translation -> TranslationOneBox(
            answer = answer,
            onOpenGoogleTranslate = { openInGoogleTranslate(context, s, answer.text, answer.source, answer.target) }
        )
        is LiveAnswer.ScreenTime -> ScreenTimePrompt(s, answer)
    }
}

/**
 * Hands [text] to Google Translate, [source] to [target]: the app with the text, as the widget's Translate shortcut
 * opens, else its web page. For the translation card's button and the keyboard's Enter on it.
 */
internal fun openInGoogleTranslate(context: android.content.Context, s: OverlayScope, text: String, source: String?, target: String) {
    s.ui.hasStartedTyping = false
    val app = GoogleTranslate.appIntent(text, source, target) { context.packageManager.resolveActivityCompat(it) != null }
    if (app == null || !launchSafeIntent(context, app)) s.openWebAddress(GoogleTranslate.webAddress(text, source, target))
}

/** A forecast; one for "here" can have its location changed from the card. */
@Composable
private fun WeatherAnswer(s: OverlayScope, answer: LiveAnswer.Weather) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    WeatherOneBox(
        report = answer.report,
        onChangeLocation = if (answer.here) ({ choosing = true }) else null,
        onOpenWeather = {
            s.ui.hasStartedTyping = false
            val app = GoogleApps.weatherIntent(context)
            if (app == null || !launchSafeIntent(context, app)) s.launchWebSearch(answer.query)
        }
    )
    if (choosing) WeatherLocationChooser(s, onDone = { choosing = false })
}

/**
 * The weather card asking where "here" is: Set Location opens the city search, Use Approximate Location asks for the
 * permission (after the app's usual explanation). Either way the forecast is looked up again.
 */
@Composable
private fun WeatherLocationPrompt(s: OverlayScope) {
    var choosing by remember { mutableStateOf(false) }
    val location = rememberApproximateLocationRequest {
        // The set location would win over the phone's; this is the user choosing the phone's instead.
        s.prefs.edit { put(SettingsManager.WEATHER_HOME_LOCATION, "") }
        s.viewModel.retryLiveAnswer()
    }
    WeatherLocationOneBox(
        onUseApproximateLocation = location::request,
        onSetLocation = { choosing = true }
    )
    if (choosing) WeatherLocationChooser(s, onDone = { choosing = false })
}

/** [WeatherLocationDialog] writing the weather's location, then looking the forecast up again. */
@Composable
private fun WeatherLocationChooser(s: OverlayScope, onDone: () -> Unit) {
    val home = s.prefs[SettingsManager.WEATHER_HOME_LOCATION]
    WeatherLocationDialog(
        hasLocation = HomeLocationCodec.decode(home) != null,
        onChosen = { place ->
            s.prefs.edit { put(SettingsManager.WEATHER_HOME_LOCATION, HomeLocationCodec.encode(place)) }
            onDone()
            s.viewModel.retryLiveAnswer()
        },
        onCleared = {
            s.prefs.edit { put(SettingsManager.WEATHER_HOME_LOCATION, "") }
            onDone()
            s.viewModel.retryLiveAnswer()
        },
        onDismiss = onDone
    )
}

/** The screen time card, the Usage Access dialog behind its button, and a fresh look once the user comes back. */
@Composable
private fun ScreenTimePrompt(s: OverlayScope, answer: LiveAnswer.ScreenTime) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val needsAccess by rememberUpdatedState(answer.report == null)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Usage Access is granted in Android's settings; coming back from there should show the answer.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && needsAccess) s.viewModel.retryLiveAnswer()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val details = remember { digitalWellbeingIntent(context) }
    ScreenTimeOneBox(
        report = answer.report,
        onAllowUsageAccess = { showRationale = true },
        onOpenDetails = details?.let { intent ->
            {
                s.ui.hasStartedTyping = false
                launchSafeIntent(context, intent)
            }
        }
    )
    if (showRationale) {
        PermissionRationaleDialog(
            title = "Allow Usage Access?",
            text = "Intelligent Search reads how long you've used each app today to show your screen time, and to " +
                "suggest the apps you use most. It stays on your phone. Turn on Usage Access for Intelligent " +
                "Search on the next screen.",
            confirm = "Open Settings",
            onConfirm = {
                showRationale = false
                s.ui.hasStartedTyping = false
                // Back from Android's page comes back here, with the search, to show the answer.
                openSettingsPageToGrant(
                    context,
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            },
            onDismiss = { showRationale = false }
        )
    }
}
