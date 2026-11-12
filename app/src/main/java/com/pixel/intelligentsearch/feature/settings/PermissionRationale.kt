package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.system.openSettingsPageToGrant
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.answers.AndroidDeviceLocation

/**
 * The app's one explanation before a permission or Android settings page: what is asked for and why, then [confirm]
 * ("Allow", "Open Settings") or "Not Now". Every permission the app asks for goes through it, so each reads alike.
 */
@Composable
internal fun PermissionRationaleDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not Now", style = MaterialTheme.typography.labelLarge) } }
    )
}

/**
 * Android's page for this app's own settings, where any permission declined for good can be allowed. Opened with
 * [openSettingsPageToGrant], so Back from it comes back to the screen that asked (a waiting search with what was
 * typed), not to whatever page Android's settings were last left on.
 */
internal fun appDetailsSettings(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** A way to send the user to App info ([appDetailsSettings]) to allow a permission declined for good. */
internal fun interface SettingsGrant {
    fun open()
}

/**
 * App info for allowing [permission] after it was declined for good, and the answer when the user comes back: as
 * this screen comes back into view with [permission] allowed there, [onGranted] runs, so what they were turning on
 * (a switch, the weather where they are) turns on without another tap. Only a return from the page [SettingsGrant.open]
 * opened counts, and only once; a screen recreated meanwhile still hears it.
 *
 * A return is the screen coming back after it was left. Opened from Android's answer to its own prompt (Finish
 * Setting Up, the search's microphone), App info starts while the screen is still behind that prompt, and Android
 * brings the screen back once before App info covers it, with the permission still off. Counted, that ended the wait,
 * so allowing it on App info then turned nothing on.
 *
 * A new request brought to the screen instead (the search opened again from the Quick Settings tile, the notification
 * shortcut or the assistant gesture, which reuse the waiting search and clear App info off it) ends the wait: it isn't
 * the user coming back, and that search asked for nothing, so it must not start the microphone. Back from App info never
 * brings a new request. The wait also ends, answering nothing, when the screen is first left with the permission
 * already on: App info opened beside it (desktop windows) instead of over it, and the user allowed it there with the
 * screen still in view. Leaving and coming back later (minimized and restored) isn't coming back from App info.
 */
@Composable
internal fun rememberSettingsGrant(permission: String, onGranted: () -> Unit): SettingsGrant {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var awaitingGrant by rememberSaveable(permission) { mutableStateOf(false) }
    // Whether the screen has been left since App info was opened, so only coming back from it answers.
    var leftForGrant by rememberSaveable(permission) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context, permission) {
        fun allowed() = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val observer = LifecycleEventObserver { _, event ->
            if (!awaitingGrant) return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_PAUSE && !leftForGrant) {
                // Already allowed as the screen is left: not left for App info, which covers it before anything changes.
                if (allowed()) awaitingGrant = false else leftForGrant = true
            }
            if (event == Lifecycle.Event.ON_RESUME && leftForGrant) {
                awaitingGrant = false
                leftForGrant = false
                if (allowed()) {
                    forgetDecline(context, permission)
                    currentOnGranted()
                }
            }
        }
        // Heard before the resume that follows a new request, so that resume answers nothing.
        val newRequest = androidx.core.util.Consumer<Intent> {
            awaitingGrant = false
            leftForGrant = false
        }
        val activity = context.findActivity() as? androidx.core.app.OnNewIntentProvider
        lifecycleOwner.lifecycle.addObserver(observer)
        activity?.addOnNewIntentListener(newRequest)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.removeOnNewIntentListener(newRequest)
        }
    }
    return remember(context, permission) {
        SettingsGrant {
            awaitingGrant = true
            leftForGrant = false
            if (!openSettingsPageToGrant(context, appDetailsSettings(context.packageName))) awaitingGrant = false
        }
    }
}

/** The permissions the user has declined at least once, kept apart from the settings (never backed up or shown). */
internal const val DECLINED_PERMISSIONS = "declined_permissions"

/**
 * Whether [permission], just declined, can now only be allowed in Android's settings. [rationaleBefore] is Android's
 * reason flag (shouldShowRequestPermissionRationale) as the request started; Android sets it after a first decline
 * and clears it after the last. Set then and clear now, the user has just declined for good. Clear both times, either
 * Android didn't ask, having been told no for good before, or the user closed its prompt without choosing, which
 * changes nothing; only a user who has declined before (remembered here) can be in the first case.
 */
internal fun declinedForGood(activity: Activity, permission: String, rationaleBefore: Boolean): Boolean {
    val declined = activity.getSharedPreferences(DECLINED_PERMISSIONS, Context.MODE_PRIVATE)
    val rationaleNow = activity.shouldShowRequestPermissionRationale(permission)
    val everDeclined = rationaleBefore || rationaleNow || declined.getBoolean(permission, false)
    if (everDeclined) declined.edit { putBoolean(permission, true) }
    return !rationaleNow && everDeclined
}

/**
 * Forgets that [permission] was declined ([declinedForGood]), once it is seen allowed. Android can take it back later
 * and ask afresh (a one-time grant lapsing, the permissions of an app left unused being reset); only closing that
 * prompt then mustn't read as declined for good.
 */
internal fun forgetDecline(context: Context, permission: String) {
    val declined = context.getSharedPreferences(DECLINED_PERMISSIONS, Context.MODE_PRIVATE)
    if (declined.contains(permission)) declined.edit { remove(permission) }
}

/**
 * [forgetDecline] for every declined permission that is allowed now, however it was allowed: most often on the App
 * info page "Open Settings" leads to, where no prompt of the app's sees it. Run each time the search or Settings comes
 * back into view, so the grant is seen before Android can take it back.
 *
 * "Ask every time" on App info (location, the microphone) isn't allowing, so the decline is kept. Android asks again at
 * the next request, and an allow or a decline there is read right. Only closing that prompt without answering reads
 * as declined for good: Android sets no reason flag for "Ask every time" (it marks it one-time only), so it looks
 * like a request Android answered by itself. Forgetting the decline on the way back instead would leave a user who
 * changed nothing there with no way to Android's settings at all, which is worse.
 */
internal fun forgetDeclinesNowAllowed(context: Context) {
    runCatching {
        val declined = context.getSharedPreferences(DECLINED_PERMISSIONS, Context.MODE_PRIVATE)
        val allowed = declined.all.keys.filter { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (allowed.isNotEmpty()) declined.edit { allowed.forEach { remove(it) } }
    }
}

/** Records [permission] as declined, for [declinedForGood], when that is known some other way than a prompt's answer. */
internal fun noteDecline(context: Context, permission: String) {
    context.getSharedPreferences(DECLINED_PERMISSIONS, Context.MODE_PRIVATE).edit { putBoolean(permission, true) }
}

/** Android's reason flag for [permission] right now, read as a request starts, for [declinedForGood]. */
internal fun rationaleShown(context: Context, permission: String): Boolean =
    context.findActivity()?.shouldShowRequestPermissionRationale(permission) == true

/** What contact search says before Android asks for the contacts; At a Glance's birthdays ask the same way. */
internal const val CONTACTS_RATIONALE =
    "Intelligent Search needs your contacts so you can search, call, message and see contact details from the " +
        "search bar, and see their birthdays in At a Glance. Your contacts stay on your phone."

/** A way to ask for a permission: [request] answers at once when it is allowed, else explains first. */
internal fun interface PermissionRequest {
    fun request()
}

/** What the app says when it asks for [permission], and how it knows the permission is [allowed]. */
private class PermissionPrompt(
    val permission: String,
    val askTitle: String,
    val askText: String,
    val offTitle: String,
    val offText: String,
    val allowed: (Context) -> Boolean = { it.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED }
)

/**
 * A permission, asked the one way the app asks: when allowed [onGranted] runs at once; otherwise the explanation shows,
 * then Android's prompt, and [onGranted] runs if the user allows it. Declining, or Not Now, runs [onNotGranted]. Once
 * Android won't ask again ("Allow" then answers no at once), the user is offered Android's settings instead, and
 * [onGranted] runs on the way back if they allowed it there ([rememberSettingsGrant]); not when they only closed
 * Android's prompt ([declinedForGood]). Shows its dialogs in place while they are up.
 */
@Composable
private fun rememberPermissionRequest(prompt: PermissionPrompt, onGranted: () -> Unit, onNotGranted: () -> Unit = {}): PermissionRequest {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnNotGranted by rememberUpdatedState(onNotGranted)
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var rationaleBefore by remember { mutableStateOf(false) }
    val settingsGrant = rememberSettingsGrant(prompt.permission) { currentOnGranted() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            forgetDecline(context, prompt.permission)
            currentOnGranted()
        } else {
            currentOnNotGranted()
            // Declined for good: only Android's settings can allow it now.
            val activity = context.findActivity()
            if (activity != null && declinedForGood(activity, prompt.permission, rationaleBefore)) showSettings = true
        }
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = prompt.askTitle,
            text = prompt.askText,
            confirm = "Allow",
            onConfirm = {
                showRationale = false
                rationaleBefore = rationaleShown(context, prompt.permission)
                launcher.launch(prompt.permission)
            },
            onDismiss = {
                showRationale = false
                currentOnNotGranted()
            }
        )
    }
    if (showSettings) {
        PermissionRationaleDialog(
            title = prompt.offTitle,
            text = prompt.offText,
            confirm = "Open Settings",
            onConfirm = {
                showSettings = false
                settingsGrant.open()
            },
            onDismiss = { showSettings = false }
        )
    }
    return remember(context) {
        PermissionRequest {
            if (prompt.allowed(context)) {
                forgetDecline(context, prompt.permission)
                currentOnGranted()
            } else {
                showRationale = true
            }
        }
    }
}

private val CONTACTS_PROMPT = PermissionPrompt(
    permission = Manifest.permission.READ_CONTACTS,
    askTitle = "Allow Contacts Access?",
    askText = CONTACTS_RATIONALE,
    offTitle = "Contacts Access Is Off",
    offText = "Contacts access for Intelligent Search is turned off. Allow it in Android settings to search your contacts."
)

/** The contacts, for contact search and At a Glance's birthdays: [onResult] hears yes or no. */
@Composable
internal fun rememberContactsRequest(onResult: (granted: Boolean) -> Unit): PermissionRequest =
    rememberPermissionRequest(CONTACTS_PROMPT, onGranted = { onResult(true) }, onNotGranted = { onResult(false) })

/** Whether the contacts may be read. */
internal fun contactsAllowed(context: android.content.Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** What At a Glance's Calendar Events says before Android asks for the calendar. */
internal const val CALENDAR_RATIONALE =
    "Intelligent Search needs your calendar to show your events for the next day in At a Glance. Your calendar " +
        "stays on your phone."

private val CALENDAR_PROMPT = PermissionPrompt(
    permission = Manifest.permission.READ_CALENDAR,
    askTitle = "Allow Calendar Access?",
    askText = CALENDAR_RATIONALE,
    offTitle = "Calendar Access Is Off",
    offText = "Calendar access for Intelligent Search is turned off. Allow it in Android settings to see your " +
        "upcoming events in At a Glance."
)

/** The calendar, asked only as At a Glance's Calendar Events is turned on (setup no longer asks for it). */
@Composable
internal fun rememberCalendarRequest(onGranted: () -> Unit): PermissionRequest =
    rememberPermissionRequest(CALENDAR_PROMPT, onGranted)

/** Whether the calendar may be read. */
internal fun calendarAllowed(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** What Direct Call says before Android asks to let the app make phone calls. */
internal const val PHONE_CALLS_RATIONALE =
    "Direct Call places the call as soon as you tap a contact. Allow phone calls for that; otherwise tapping a " +
        "contact opens the dialer with the number ready."

private val PHONE_CALLS_PROMPT = PermissionPrompt(
    permission = Manifest.permission.CALL_PHONE,
    askTitle = "Allow Phone Calls?",
    askText = PHONE_CALLS_RATIONALE,
    offTitle = "Phone Calls Are Off",
    offText = "Phone calls for Intelligent Search are turned off. Allow them in Android settings for Direct Call " +
        "to place your calls; until then, tapping a contact opens the dialer with the number ready.",
    allowed = com.pixel.intelligentsearch.feature.search.ContactActions::canCallDirectly
)

/**
 * Phone calls for Direct Call. Declining keeps Direct Call on, opening the dialer instead
 * ([com.pixel.intelligentsearch.feature.search.ContactActions.open]), so there is nothing to answer.
 */
@Composable
internal fun rememberPhoneCallsRequest(): PermissionRequest = rememberPermissionRequest(PHONE_CALLS_PROMPT, onGranted = {})

/** What weather answers say before Android asks for approximate location. */
internal const val LOCATION_RATIONALE =
    "The weather can use your phone's approximate location, never its precise one. Only rounded coordinates " +
        "are sent to Open-Meteo for the forecast, and only when you ask for the weather."

private val LOCATION_PROMPT = PermissionPrompt(
    permission = Manifest.permission.ACCESS_COARSE_LOCATION,
    askTitle = "Allow Approximate Location?",
    askText = LOCATION_RATIONALE,
    offTitle = "Location Access Is Off",
    offText = "Location access for Intelligent Search is turned off. Allow approximate location in Android " +
        "settings to get the weather where you are, or choose a place with Set Location on the weather card.",
    allowed = { AndroidDeviceLocation(it).permitted() }
)

/** Approximate location for weather answers. */
@Composable
internal fun rememberApproximateLocationRequest(onGranted: () -> Unit): PermissionRequest =
    rememberPermissionRequest(LOCATION_PROMPT, onGranted)

/** What Discover says before Android asks for approximate location, for its weather and its leave-by time. */
internal const val TODAY_LOCATION_RATIONALE =
    "Discover can show the weather where you are and tell you when to leave for your next event. It uses your " +
        "phone's approximate location, never its precise one. Rounded coordinates are sent to Open-Meteo for the " +
        "forecast, and Android's own map lookup finds the event's address. Nothing is kept."

private val TODAY_LOCATION_PROMPT = PermissionPrompt(
    permission = Manifest.permission.ACCESS_COARSE_LOCATION,
    askTitle = "Allow Approximate Location?",
    askText = TODAY_LOCATION_RATIONALE,
    offTitle = "Location Access Is Off",
    offText = "Location access for Intelligent Search is turned off. Allow approximate location in Android " +
        "settings to see the weather where you are and when to leave for your next event.",
    allowed = { AndroidDeviceLocation(it).permitted() }
)

/** Approximate location for Today's leave-by time. */
@Composable
internal fun rememberTodayLocationRequest(onGranted: () -> Unit): PermissionRequest =
    rememberPermissionRequest(TODAY_LOCATION_PROMPT, onGranted)

