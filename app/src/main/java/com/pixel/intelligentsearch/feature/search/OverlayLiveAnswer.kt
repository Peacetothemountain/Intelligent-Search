package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.answers.GoogleTranslate
import com.pixel.intelligentsearch.core.answers.LiveAnswer
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.ui.DefinitionOneBox
import com.pixel.intelligentsearch.core.ui.ScreenTimeOneBox
import com.pixel.intelligentsearch.core.ui.TranslationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherLocationOneBox
import com.pixel.intelligentsearch.core.ui.WeatherOneBox
import com.pixel.intelligentsearch.feature.widget.resolveActivityCompat

/** The weather app to open from a forecast: Pixel Weather, else the Google app's weather page; null for neither. */
internal fun weatherAppIntent(context: Context): Intent? {
    val pm = context.packageManager
    pm.getLaunchIntentForPackage("com.google.android.apps.weather")?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    val google = Intent(Intent.ACTION_VIEW)
        .setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.weather.WeatherExportedActivity")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return google.takeIf { pm.resolveActivity(it, 0) != null }
}

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
        is LiveAnswer.Weather -> WeatherOneBox(
            report = answer.report,
            onOpenWeather = {
                s.ui.hasStartedTyping = false
                val app = weatherAppIntent(context)
                if (app == null || !launchSafeIntent(context, app)) s.launchWebSearch(answer.query)
            }
        )
        is LiveAnswer.WeatherNeedsLocation -> WeatherLocationPrompt(s)
        is LiveAnswer.Translation -> TranslationOneBox(
            answer = answer,
            onOpenGoogleTranslate = {
                s.ui.hasStartedTyping = false
                // The Google Translate app with the text, as the widget's Translate shortcut opens; else its web page.
                val app = GoogleTranslate.appIntent(answer.text, answer.source, answer.target) {
                    context.packageManager.resolveActivityCompat(it) != null
                }
                if (app == null || !launchSafeIntent(context, app)) {
                    s.openWebAddress(GoogleTranslate.webAddress(answer.text, answer.source, answer.target))
                }
            }
        )
        is LiveAnswer.ScreenTime -> ScreenTimePrompt(s, answer)
    }
}

/** The weather card asking where "here" is, and the approximate-location request behind its button. */
@Composable
private fun WeatherLocationPrompt(s: OverlayScope) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            s.prefs.edit { put(SettingsManager.WEATHER_USE_DEVICE_LOCATION, true) }
            s.viewModel.retryLiveAnswer()
        }
    }
    WeatherLocationOneBox(
        onUseApproximateLocation = {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                s.prefs.edit { put(SettingsManager.WEATHER_USE_DEVICE_LOCATION, true) }
                s.viewModel.retryLiveAnswer()
            } else {
                showRationale = true
            }
        },
        onSetHomeLocation = { s.onOpenSettings("web_search") }
    )
    if (showRationale) {
        LocationRationaleDialog(
            onAllow = {
                showRationale = false
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showRationale = false }
        )
    }
}

/** Why weather answers would like approximate location, shown before Android's own prompt. */
@Composable
internal fun LocationRationaleDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow Approximate Location?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Weather answers can use your phone's approximate location, never its precise one. Only rounded " +
                    "coordinates are sent to Open-Meteo for the forecast, and only when you ask for the weather.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not Now", style = MaterialTheme.typography.labelLarge) } }
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
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Allow Usage Access?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Intelligent Search reads how long you've used each app today to show your screen time, and to " +
                        "suggest the apps you use most. It stays on your phone. Turn on Usage Access for Intelligent " +
                        "Search on the next screen.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    s.ui.hasStartedTyping = false
                    val forThisApp = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!launchSafeIntent(context, forThisApp)) {
                        launchSafeIntent(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Open Settings", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not Now", style = MaterialTheme.typography.labelLarge) } }
        )
    }
}
