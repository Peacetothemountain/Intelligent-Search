package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.core.system.openSettingsPage
import android.Manifest
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
 * answers no; once Android won't ask again, the user is offered Android's settings instead. Shows its dialogs in
 * place while they are up.
 */
@Composable
internal fun rememberContactsRequest(onResult: (granted: Boolean) -> Unit): ContactsRequest {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.isNotEmpty() && results.values.all { it }
        currentOnResult(granted)
        // Declined for good: only Android's settings can allow it now.
        if (!granted && context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) == false) {
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
            if (contactsAllowed(context)) currentOnResult(true) else showRationale = true
        }
    }
}

/** Whether the contacts may be read. */
internal fun contactsAllowed(context: android.content.Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** What weather answers say before Android asks for approximate location. */
internal const val LOCATION_RATIONALE =
    "Weather answers can use your phone's approximate location, never its precise one. Only rounded coordinates " +
        "are sent to Open-Meteo for the forecast, and only when you ask for the weather."

/** A way to ask for approximate location: [request] answers at once when allowed, else explains first. */
internal fun interface ApproximateLocationRequest {
    fun request()
}

/**
 * Approximate location for weather answers, asked the one way the app asks for it: when it is already allowed
 * [onGranted] runs at once; otherwise the rationale ([LOCATION_RATIONALE]) shows, then Android's prompt, and
 * [onGranted] runs if the user allows it. Shows its dialog in place while it is up.
 */
@Composable
internal fun rememberApproximateLocationRequest(onGranted: () -> Unit): ApproximateLocationRequest {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    var showRationale by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentOnGranted()
    }
    if (showRationale) {
        PermissionRationaleDialog(
            title = "Allow Approximate Location?",
            text = LOCATION_RATIONALE,
            confirm = "Allow",
            onConfirm = {
                showRationale = false
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showRationale = false }
        )
    }
    return remember(context) {
        ApproximateLocationRequest {
            if (AndroidDeviceLocation(context).permitted()) currentOnGranted() else showRationale = true
        }
    }
}
