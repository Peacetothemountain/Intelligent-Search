package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.system.openSettingsPage
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.edit
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

/** Android's page for this app's own settings, where any permission declined for good can be allowed. */
internal fun appDetailsSettings(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** The permissions the user has declined at least once, kept apart from the settings (never backed up or shown). */
private const val DECLINED_PERMISSIONS = "declined_permissions"

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

/** Android's reason flag for [permission] right now, read as a request starts, for [declinedForGood]. */
internal fun rationaleShown(context: Context, permission: String): Boolean =
    context.findActivity()?.shouldShowRequestPermissionRationale(permission) == true

/** What contact search says before Android asks for the contacts; At a Glance's birthdays ask the same way. */
internal const val CONTACTS_RATIONALE =
    "Intelligent Search needs your contacts so you can search, call, message and see contact details from the " +
        "search bar, and see their birthdays in At a Glance. Your contacts stay on your phone."

/** A way to ask for the contacts: [request] answers at once when allowed, else explains first. */
internal fun interface ContactsRequest {
    fun request()
}

/**
 * The contacts, asked the one way the app asks for them (contact search, At a Glance's birthdays): when allowed
 * [onResult] hears yes at once; otherwise [CONTACTS_RATIONALE] shows, then Android's prompt. Declining once just
 * answers no; once Android won't ask again, the user is offered Android's settings instead, but not when they only
 * closed Android's prompt ([declinedForGood]). Shows its dialogs in place while they are up.
 */
@Composable
internal fun rememberContactsRequest(onResult: (granted: Boolean) -> Unit): ContactsRequest {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var rationaleBefore by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.isNotEmpty() && results.values.all { it }
        val activity = context.findActivity()
        if (granted) forgetDecline(context, Manifest.permission.READ_CONTACTS)
        currentOnResult(granted)
        // Declined for good: only Android's settings can allow it now.
        if (!granted && activity != null && declinedForGood(activity, Manifest.permission.READ_CONTACTS, rationaleBefore)) {
            showSettings = true
        }
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = "Allow Contacts Access?",
            text = CONTACTS_RATIONALE,
            confirm = "Allow",
            onConfirm = {
                showRationale = false
                rationaleBefore = rationaleShown(context, Manifest.permission.READ_CONTACTS)
                permission.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            },
            onDismiss = {
                showRationale = false
                currentOnResult(false)
            }
        )
    }
    if (showSettings) {
        PermissionRationaleDialog(
            title = "Contacts Access Is Off",
            text = "Contacts access for Intelligent Search is turned off. Allow it in Android settings to search your contacts.",
            confirm = "Open Settings",
            onConfirm = {
                showSettings = false
                openSettingsPage(context, appDetailsSettings(context.packageName))
            },
            onDismiss = { showSettings = false }
        )
    }
    return remember(context) {
        ContactsRequest {
            if (contactsAllowed(context)) {
                forgetDecline(context, Manifest.permission.READ_CONTACTS)
                currentOnResult(true)
            } else {
                showRationale = true
            }
        }
    }
}

/** Whether the contacts may be read. */
internal fun contactsAllowed(context: android.content.Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** What Direct Call says before Android asks to let the app make phone calls. */
internal const val PHONE_CALLS_RATIONALE =
    "Direct Call places the call as soon as you tap a contact. Allow phone calls for that; otherwise tapping a " +
        "contact opens the dialer with the number ready."

/** A way to ask to make phone calls: [request] does nothing when allowed, else explains first. */
internal fun interface PhoneCallsRequest {
    fun request()
}

/**
 * Phone calls for Direct Call, asked the one way the app asks: when they are allowed nothing more is needed;
 * otherwise [PHONE_CALLS_RATIONALE] shows, then Android's prompt. Declining keeps Direct Call on, opening the dialer
 * instead ([com.pixel.intelligentsearch.feature.search.ContactActions.open]). Once Android won't ask again, the user
 * is offered Android's settings instead, as contacts and location do. Shows its dialogs in place while they are up.
 */
@Composable
internal fun rememberPhoneCallsRequest(): PhoneCallsRequest {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var rationaleBefore by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        if (granted) forgetDecline(context, Manifest.permission.CALL_PHONE)
        else if (activity != null && declinedForGood(activity, Manifest.permission.CALL_PHONE, rationaleBefore)) {
            showSettings = true
        }
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = "Allow Phone Calls?",
            text = PHONE_CALLS_RATIONALE,
            confirm = "Allow",
            onConfirm = {
                showRationale = false
                rationaleBefore = rationaleShown(context, Manifest.permission.CALL_PHONE)
                permission.launch(Manifest.permission.CALL_PHONE)
            },
            onDismiss = { showRationale = false }
        )
    }
    if (showSettings) {
        PermissionRationaleDialog(
            title = "Phone Calls Are Off",
            text = "Phone calls for Intelligent Search are turned off. Allow them in Android settings for Direct Call " +
                "to place your calls; until then, tapping a contact opens the dialer with the number ready.",
            confirm = "Open Settings",
            onConfirm = {
                showSettings = false
                openSettingsPage(context, appDetailsSettings(context.packageName))
            },
            onDismiss = { showSettings = false }
        )
    }
    return remember(context) {
        PhoneCallsRequest {
            if (com.pixel.intelligentsearch.feature.search.ContactActions.canCallDirectly(context)) {
                forgetDecline(context, Manifest.permission.CALL_PHONE)
            } else {
                showRationale = true
            }
        }
    }
}

/** What weather answers say before Android asks for approximate location. */
internal const val LOCATION_RATIONALE =
    "The weather can use your phone's approximate location, never its precise one. Only rounded coordinates " +
        "are sent to Open-Meteo for the forecast, and only when you ask for the weather."

/** A way to ask for approximate location: [request] answers at once when allowed, else explains first. */
internal fun interface ApproximateLocationRequest {
    fun request()
}

/**
 * Approximate location for weather answers, asked the one way the app asks for it: when it is already allowed
 * [onGranted] runs at once; otherwise the rationale ([LOCATION_RATIONALE]) shows, then Android's prompt, and
 * [onGranted] runs if the user allows it. Once Android won't ask again ("Allow" then answers no at once), the user
 * is offered Android's settings instead, as contacts do; not when they only closed Android's prompt
 * ([declinedForGood]). Shows its dialogs in place while they are up.
 */
@Composable
internal fun rememberApproximateLocationRequest(onGranted: () -> Unit): ApproximateLocationRequest {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var rationaleBefore by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        if (granted) {
            forgetDecline(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            currentOnGranted()
        }
        // Declined for good: only Android's settings can allow it now.
        else if (activity != null && declinedForGood(activity, Manifest.permission.ACCESS_COARSE_LOCATION, rationaleBefore)) {
            showSettings = true
        }
    }
    if (showSettings) {
        PermissionRationaleDialog(
            title = "Location Access Is Off",
            text = "Location access for Intelligent Search is turned off. Allow approximate location in Android " +
                "settings to get the weather where you are, or choose a place with Set Location on the weather card.",
            confirm = "Open Settings",
            onConfirm = {
                showSettings = false
                openSettingsPage(context, appDetailsSettings(context.packageName))
            },
            onDismiss = { showSettings = false }
        )
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = "Allow Approximate Location?",
            text = LOCATION_RATIONALE,
            confirm = "Allow",
            onConfirm = {
                showRationale = false
                rationaleBefore = rationaleShown(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showRationale = false }
        )
    }
    return remember(context) {
        ApproximateLocationRequest {
            if (AndroidDeviceLocation(context).permitted()) {
                forgetDecline(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                currentOnGranted()
            } else {
                showRationale = true
            }
        }
    }
}
