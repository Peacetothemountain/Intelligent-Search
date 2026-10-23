package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.system.openSettingsPage
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.SwipeRight
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.launch.FloatingSearchOverlays
import com.pixel.intelligentsearch.feature.launch.LaunchSurfaces
import com.pixel.intelligentsearch.feature.launch.ScreenEdge
import com.pixel.intelligentsearch.feature.launch.SearchShortcutNotification

/** The Search Notification's subtitle: what it offers ([lens] when Google Lens is installed) and what a swipe does. */
internal fun searchNotificationSubtitle(lens: Boolean): String =
    "Search from Your Notifications, with Voice${if (lens) " and Lens" else ""}. " +
        "Swiped Away, It Returns the Next Time the App Starts."

/** The edge swipe's subtitle: where to swipe from, and the warning that it can take that stretch from Back. */
internal fun edgeSwipeSubtitle(edge: ScreenEdge): String =
    "Swipe In from the ${edge.label} Edge to Search. It Can Block the Back Gesture on That Part of the Edge."

/** Which overlay option asked for "Display over other apps", so coming back with it granted switches that one on. */
private enum class OverlayRequest { BUTTON, EDGE_SWIPE }

/**
 * Launch Portal's "Always Within Reach" options: the Search Notification, the floating search button and the edge
 * swipe. Each is off by default and asks for its permission when switched on, after explaining why; none shows as on
 * while its permission is missing, and one whose permission was revoked is switched off on the way back.
 */
@Composable
fun AlwaysWithinReachSection() {
    val context = LocalContext.current
    var notification by rememberSetting(SettingsManager.SEARCH_NOTIFICATION_SHORTCUT)
    var button by rememberSetting(SettingsManager.FLOATING_SEARCH_BUTTON)
    var edgeSwipe by rememberSetting(SettingsManager.EDGE_SWIPE)
    var canPost by remember { mutableStateOf(SearchShortcutNotification.canPost(context)) }
    var canDraw by remember { mutableStateOf(FloatingSearchOverlays.canDraw(context)) }
    var pendingOverlay by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var overlayRationaleFor by remember { mutableStateOf<OverlayRequest?>(null) }
    val lens = remember { com.pixel.intelligentsearch.core.system.GoogleApps.isLensAvailable(context) }

    // From here on the surfaces follow their settings; this also shows any already on.
    LaunchedEffect(Unit) { LaunchSurfaces.start(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            canPost = SearchShortcutNotification.canPost(context)
            canDraw = FloatingSearchOverlays.canDraw(context)
            // Back from Android's "Display over other apps" page: switch on what asked for it, if it was allowed.
            when (pendingOverlay?.let(OverlayRequest::valueOf)) {
                OverlayRequest.BUTTON -> if (canDraw) button = true
                OverlayRequest.EDGE_SWIPE -> if (canDraw) edgeSwipe = true
                null -> Unit
            }
            pendingOverlay = null
            // Follow a revoked permission downward, as Search Sources does, so it can't come back on unasked.
            if (!canPost && notification) notification = false
            if (!canDraw && button) button = false
            if (!canDraw && edgeSwipe) edgeSwipe = false
            LaunchSurfaces.sync(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = SearchShortcutNotification.canPost(context)
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
            title = "Search Notification",
            subtitle = searchNotificationSubtitle(lens),
            icon = Icons.Outlined.NotificationsActive,
            isChecked = notification && canPost,
            onCheckedChange = { on ->
                when {
                    !on -> notification = false
                    SearchShortcutNotification.canPost(context) -> notification = true
                    SearchShortcutNotification.needsPermission(context) -> showNotificationRationale = true
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
                    else -> overlayRationaleFor = OverlayRequest.BUTTON
                }
            },
            showDivider = true
        )
        var edgeName by rememberSetting(SettingsManager.EDGE_SWIPE_EDGE)
        val edge = ScreenEdge.from(edgeName)
        val swipeOn = edgeSwipe && canDraw
        SettingsRowToggle(
            title = "Edge Swipe",
            subtitle = edgeSwipeSubtitle(edge),
            icon = Icons.Outlined.Swipe,
            isChecked = swipeOn,
            onCheckedChange = { on ->
                when {
                    !on -> edgeSwipe = false
                    FloatingSearchOverlays.canDraw(context) -> edgeSwipe = true
                    else -> overlayRationaleFor = OverlayRequest.EDGE_SWIPE
                }
            },
            showDivider = swipeOn
        )
        AnimatedVisibility(visible = swipeOn) {
            Column {
                SettingsSelectorRow(
                    title = "Edge",
                    subtitle = "The Side of the Screen You Swipe In From.",
                    icon = if (edge == ScreenEdge.LEFT) Icons.Outlined.SwipeRight else Icons.Outlined.SwipeLeft,
                    options = ScreenEdge.entries,
                    selected = edge,
                    onSelect = { edgeName = it.stored },
                    label = { it.label },
                    showDivider = true
                )
                var position by rememberSetting(SettingsManager.EDGE_SWIPE_POSITION)
                SettingsSliderRow(
                    title = "Position",
                    value = position.toFloat(),
                    onValueChange = { position = it.toInt() },
                    valueRange = 0f..100f,
                    icon = Icons.Outlined.UnfoldMore,
                    showDivider = true
                )
                var height by rememberSetting(SettingsManager.EDGE_SWIPE_HEIGHT)
                SettingsSliderRow(
                    title = "Height",
                    value = height.toFloat(),
                    onValueChange = { height = it.toInt() },
                    valueRange = 10f..60f,
                    icon = Icons.Outlined.Height,
                    showDivider = false
                )
            }
        }
    }

    if (showNotificationRationale) {
        PermissionRationaleDialog(
            title = "Allow Notifications?",
            text = "The Search Notification keeps search one tap away in your notifications, with Voice" +
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
            text = "Notifications for Intelligent Search are turned off. Allow them in Android settings to show the Search Notification.",
            confirm = "Open Settings",
            onConfirm = {
                showNotificationSettings = false
                openSettingsPage(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            onDismiss = { showNotificationSettings = false }
        )
    }
    overlayRationaleFor?.let { request ->
        PermissionRationaleDialog(
            title = "Allow Display over Other Apps?",
            text = (if (request == OverlayRequest.BUTTON) "The floating search button" else "The edge swipe") +
                " sits over the apps you use, so Android asks you to let Intelligent Search display over other apps. " +
                "It only opens search: nothing on screen is read. On the next page, turn on Allow Display over Other Apps.",
            confirm = "Open Settings",
            onConfirm = {
                overlayRationaleFor = null
                pendingOverlay = request.name
                openSettingsPage(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
            },
            onDismiss = { overlayRationaleFor = null }
        )
    }
}

