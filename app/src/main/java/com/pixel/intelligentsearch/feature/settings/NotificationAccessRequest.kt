package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.system.openSettingsPage
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.glance.NotificationAccess

/**
 * What the notification access dialog says before Android's own Notification Access screen opens. One grant covers
 * all three features, so every one of them explains all three the same way.
 */
internal const val NOTIFICATION_ACCESS_RATIONALE =
    "One Notification Access grant covers Search Your Notifications and the Media Controls in At a Glance and on " +
        "the widget. Notifications are read only while Search Your Notifications is on, so you can search them " +
        "and see running timers above the search. It sees only what each notification shows, never your messages " +
        "or emails themselves, and holds them in memory only while they are showing. Media Controls " +
        "see only what is playing. Nothing is stored and nothing leaves your phone. On the next screen, allow " +
        "notification access for Intelligent Search."

/**
 * The app's one way to ask for Notification Access ([NotificationAccess]), shared by Search Your Notifications, At a
 * Glance's Media Controls and the widget's Media Controls: with access already granted it answers at once; otherwise
 * [request] shows the rationale ([NotificationAccessDialog]), "Open Settings" goes to Android's Notification Access
 * screen, and coming back says whether the user granted it. "Not Now" answers no.
 */
@Stable
internal class NotificationAccessRequest internal constructor(
    private val isGranted: () -> Boolean,
    /** Whether the user is on Android's settings screen and the answer comes when they return; saved state. */
    awaitingGrant: MutableState<Boolean>,
    private val answer: (Boolean) -> Unit
) {
    /** Whether the rationale dialog is up. */
    var showRationale by mutableStateOf(false)
        internal set

    internal var awaitingGrant by awaitingGrant

    /** Asks for access, answering through the callback given to [rememberNotificationAccessRequest]. */
    fun request() {
        if (isGranted()) answer(true) else showRationale = true
    }

    internal fun decline() {
        showRationale = false
        answer(false)
    }

    internal fun answerWith(granted: Boolean) = answer(granted)
}

/**
 * A [NotificationAccessRequest] for this screen; [onResult] hears whether access was granted. Show its dialog with
 * [NotificationAccessDialog]. Waiting for the user to come back from Android's settings survives this screen being
 * recreated meanwhile.
 */
@Composable
internal fun rememberNotificationAccessRequest(onResult: (granted: Boolean) -> Unit): NotificationAccessRequest {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val awaitingGrant = rememberSaveable { mutableStateOf(false) }
    val request = remember(context, awaitingGrant) {
        NotificationAccessRequest({ NotificationAccess.isGranted(context) }, awaitingGrant) { currentOnResult(it) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, request) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && request.awaitingGrant) {
                request.awaitingGrant = false
                request.answerWith(NotificationAccess.isGranted(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return request
}

/** The rationale [request] shows before Android's Notification Access screen; draws nothing while it's down. */
@Composable
internal fun NotificationAccessDialog(request: NotificationAccessRequest) {
    if (!request.showRationale) return
    val context = LocalContext.current
    PermissionRationaleDialog(
        title = "Allow Notification Access?",
        text = NOTIFICATION_ACCESS_RATIONALE,
        confirm = "Open Settings",
        onConfirm = {
            request.showRationale = false
            request.awaitingGrant = true
            if (!openSettingsPage(context, *NotificationAccess.settingsIntents(context).toTypedArray())) {
                request.awaitingGrant = false
                request.answerWith(false)
                Toast.makeText(context, "Notification access settings aren't available on this phone.", Toast.LENGTH_LONG).show()
            }
        },
        onDismiss = request::decline
    )
}
