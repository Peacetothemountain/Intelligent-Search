package com.pixel.intelligentsearch.feature.settings

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.pixel.intelligentsearch.core.system.GoogleApps
import androidx.compose.runtime.produceState
import com.pixel.intelligentsearch.core.system.openOverlayPermission
import com.pixel.intelligentsearch.core.system.openSettingsPage
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.launch.FloatingSearchOverlays
import com.pixel.intelligentsearch.feature.launch.LaunchSurfaces
import com.pixel.intelligentsearch.feature.launch.NotificationShortcut

/** The Notification Shortcut's subtitle: what it offers ([lens] when Google Lens is installed) and what a swipe does. */
internal fun notificationShortcutSubtitle(lens: Boolean): String =
    "Search from Your Notifications, with Voice${if (lens) " and Lens" else ""}. " +
        "Swiped Away, It Returns the Next Time the App Starts."

/**
 * Launch Portal's "Always Within Reach" options: the Notification Shortcut and the floating search button. Each is
 * off by default and asks for its permission when switched on, after explaining why; none shows as on
 * while its permission is missing, and one whose permission was revoked is switched off on the way back.
 */
@Composable
fun AlwaysWithinReachSection() {
    val context = LocalContext.current
    var notification by rememberSetting(SettingsManager.NOTIFICATION_SHORTCUT)
    var button by rememberSetting(SettingsManager.FLOATING_SEARCH_BUTTON)
    var canPost by remember { mutableStateOf(NotificationShortcut.canPost(context)) }
    var canDraw by remember { mutableStateOf(FloatingSearchOverlays.canDraw(context)) }
    // Sent to Android's "Display over other apps" page for the button, so coming back with it granted switches it on.
    var pendingOverlay by rememberSaveable { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showOverlayRationale by remember { mutableStateOf(false) }
    // A package query: read off the main thread, as Lens rarely comes or goes while the page is open.
    val lens by produceState(false) { value = withContext(Dispatchers.IO) { GoogleApps.isLensAvailable(context) } }

    // From here on the surfaces follow their settings; this also shows any already on.
    LaunchedEffect(Unit) { LaunchSurfaces.start(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            canPost = NotificationShortcut.canPost(context)
            canDraw = FloatingSearchOverlays.canDraw(context)
            // Back from Android's "Display over other apps" page: switch the button on, if it was allowed.
            if (pendingOverlay && canDraw) button = true
            pendingOverlay = false
            // Follow a revoked permission downward, as Search Sources does, so it can't come back on unasked.
            if (!canPost && notification) notification = false
            if (!canDraw && button) button = false
            LaunchSurfaces.sync(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = NotificationShortcut.canPost(context)
        if (granted && canPost) {
            notification = true
        } else if (!granted && context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false) {
            // Declined for good: only Android's settings can allow it now.
            showNotificationSettings = true
        }
    }

    SettingsSectionHeader("Always Within Reach")
    SettingsCard {
        SettingsRowToggle(
            title = "Notification Shortcut",
            subtitle = notificationShortcutSubtitle(lens),
            icon = Icons.Outlined.NotificationsActive,
            isChecked = notification && canPost,
            onCheckedChange = { on ->
                when {
                    !on -> notification = false
                    NotificationShortcut.canPost(context) -> notification = true
                    NotificationShortcut.needsPermission(context) -> showNotificationRationale = true
                    else -> showNotificationSettings = true
                }
            },
            showDivider = true
        )
        SettingsRowToggle(
            title = "Floating Search Button",
            subtitle = "A Bubble over Other Apps That Opens Search. Drag to Move, Hold to Hide Until You Unlock.",
            icon = Icons.Outlined.BubbleChart,
            isChecked = button && canDraw,
            onCheckedChange = { on ->
                when {
                    !on -> button = false
                    FloatingSearchOverlays.canDraw(context) -> button = true
                    else -> showOverlayRationale = true
                }
            },
            showDivider = false
        )
    }

    if (showNotificationRationale) {
        PermissionRationaleDialog(
            title = "Allow Notifications?",
            text = "The Notification Shortcut keeps search one tap away in your notifications, with Voice" +
                (if (lens) " and Lens" else "") + ". It is silent, and on the lock screen its content stays hidden. " +
                "Android asks you to allow notifications for it.",
            confirm = "Allow",
            onConfirm = {
                showNotificationRationale = false
                // Only shown where it is needed (Android 13+), which this check also tells lint.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { showNotificationRationale = false }
        )
    }
    if (showNotificationSettings) {
        PermissionRationaleDialog(
            title = "Notifications Are Off",
            text = "Notifications for Intelligent Search are turned off. Allow them in Android settings to show the Notification Shortcut.",
            confirm = "Open Settings",
            onConfirm = {
                showNotificationSettings = false
                openSettingsPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onDismiss = { showNotificationSettings = false }
        )
    }
    if (showOverlayRationale) {
        PermissionRationaleDialog(
            title = "Allow Display over Other Apps?",
            text = "The floating search button sits over the apps you use, so Android asks you to let Intelligent Search display over other apps. " +
                "It only opens search: nothing on screen is read. On the next page, turn on Allow Display over Other Apps.",
            confirm = "Open Settings",
            onConfirm = {
                showOverlayRationale = false
                pendingOverlay = true
                openOverlayPermission(context)
            },
            onDismiss = { showOverlayRationale = false }
        )
    }
}

