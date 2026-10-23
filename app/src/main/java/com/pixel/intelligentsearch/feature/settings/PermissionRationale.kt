package com.pixel.intelligentsearch.feature.settings

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
