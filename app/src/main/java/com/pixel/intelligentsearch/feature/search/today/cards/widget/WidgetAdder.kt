package com.pixel.intelligentsearch.feature.search.today.cards.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.pixel.intelligentsearch.core.system.ComesBackContext
import com.pixel.intelligentsearch.core.system.WaitsForPermissionGrant
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.util.findActivity
import com.pixel.intelligentsearch.feature.search.today.cards.WidgetChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Adding an app card, and setting one up again. Both open other apps' pages (Android's "allow to create widgets"
 * question, a widget's own setup page), so the Discover screen waits behind them instead of closing.
 *
 * NG Designs
 */

private const val BIND_REFUSED = "To add this card, tap Create when Android asks."
private const val SETUP_CANCELED = "To add this card, finish setting up the widget."
private const val CANT_ADD = "This widget can't be added right now."

/** The request code for a setup page Android opens for the host; its answer isn't read (see [startForHost]). */
private const val REQUEST_HOST_SETUP = 0x15D1

/**
 * Adds an app card from a [WidgetChoice]: binds a new widget to Discover's host, asking Android's "allow to create
 * widgets" question when the app may not yet, then runs the widget's own setup page when it has one. On success
 * [onAdded] gets the new widget id and provider (flattened component name) and the card is read; when the user says no
 * or leaves setup, the widget is deleted again and [onFailed] gets a message to show. One add at a time; the flow
 * survives the screen being recreated while Android's pages are open.
 */
@Composable
internal fun rememberWidgetAdder(
    onAdded: (widgetId: Int, provider: String) -> Unit,
    onFailed: (message: String) -> Unit
): (WidgetChoice) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val added = rememberUpdatedState(onAdded)
    val failed = rememberUpdatedState(onFailed)
    val pendingId = rememberSaveable { mutableIntStateOf(0) }
    val pendingProvider = rememberSaveable { mutableStateOf<String?>(null) }
    val flow = remember(context) { AdderFlow(context, scope, added, failed, pendingId, pendingProvider) }
    flow.bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.onBound(it.resultCode == Activity.RESULT_OK)
    }
    flow.configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.onConfigured(it.resultCode == Activity.RESULT_OK)
    }
    return flow::add
}

/**
 * Runs a SETUP card's widget setup page again; when it is finished, the card stops waiting and is read again. A widget
 * with no setup page asks for setup in its app, so its app opens instead.
 */
@Composable
internal fun rememberWidgetSetup(): (widgetId: Int) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pending.intValue
        pending.intValue = 0
        if (id == 0) return@rememberLauncherForActivityResult
        val cards = androidWidgetCards(context)
        if (result.resultCode == Activity.RESULT_OK) cards.setupDone(id) else cards.refresh(listOf(id))
    }
    return remember(context, launcher) {
        { widgetId: Int ->
            scope.launch {
                val cards = androidWidgetCards(context)
                val info = withContext(Dispatchers.IO) {
                    runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) }.getOrNull()
                }
                val configure = info?.configure
                when {
                    info == null -> cards.refresh(listOf(widgetId)) // the app is gone; the card offers to remove itself
                    configure == null -> withContext(Dispatchers.IO) {
                        context.packageManager.getLaunchIntentForPackage(info.provider.packageName)
                    }?.let { launchSafeIntent(context, it) }
                    else -> {
                        pending.intValue = widgetId
                        if (!launchWaiting(context, launcher, configureIntent(widgetId, configure))) {
                            pending.intValue = 0
                            startForHost(context, cards.host, widgetId)
                        }
                    }
                }
            }
        }
    }
}

/** The steps of one add, kept across recompositions; the pending widget is saved state so a recreated screen finishes it. */
private class AdderFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val added: State<(Int, String) -> Unit>,
    private val failed: State<(String) -> Unit>,
    private val pendingId: MutableIntState,
    private val pendingProvider: MutableState<String?>
) {
    lateinit var bindLauncher: ActivityResultLauncher<Intent>
    lateinit var configureLauncher: ActivityResultLauncher<Intent>
    private val cards = androidWidgetCards(context)
    private val manager get() = AppWidgetManager.getInstance(context)

    /** Whether an add is under way, from the tap on: a second tap while the first is allocating does nothing. */
    private var busy = false

    init {
        // A screen recreated in the middle of an add still owns its widget: the clean-up mustn't take it.
        if (pendingId.intValue != 0) cards.holdForAdd(pendingId.intValue)
    }

    fun add(choice: WidgetChoice) {
        if (busy || pendingId.intValue != 0) return
        busy = true
        scope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { cards.allocateForAdd() }.getOrNull() }
            if (id == null) {
                busy = false
                failed.value(CANT_ADD)
                return@launch
            }
            pendingId.intValue = id
            pendingProvider.value = choice.provider.flattenToString()
            val bound = withContext(Dispatchers.IO) {
                runCatching { manager.bindAppWidgetIdIfAllowed(id, choice.provider) }.getOrDefault(false)
            }
            if (bound) {
                configureOrFinish(id)
                return@launch
            }
            // Android's own question: may this app create widgets (and read what they show)?
            val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, choice.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, Process.myUserHandle())
            if (!launchWaiting(context, bindLauncher, ask)) fail(id, CANT_ADD)
        }
    }

    fun onBound(ok: Boolean) {
        val id = pendingId.intValue
        if (id == 0) return
        if (ok) scope.launch { configureOrFinish(id) } else fail(id, BIND_REFUSED)
    }

    fun onConfigured(ok: Boolean) {
        val id = pendingId.intValue
        if (id == 0) return
        if (ok) finish(id, needsSetup = false) else fail(id, SETUP_CANCELED)
    }

    /** Runs the widget's setup page when it has one it needs, else finishes the add. */
    private suspend fun configureOrFinish(id: Int) {
        val info = withContext(Dispatchers.IO) { runCatching { manager.getAppWidgetInfo(id) }.getOrNull() }
        if (info == null) {
            fail(id, CANT_ADD)
            return
        }
        val configure = info.configure
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (configure == null || optional) {
            finish(id, needsSetup = false)
            return
        }
        if (launchWaiting(context, configureLauncher, configureIntent(id, configure))) return
        // The setup page isn't open to other apps: Android opens it for the host instead but doesn't say how it ended,
        // so the card waits for setup until the widget shows real values.
        if (startForHost(context, cards.host, id)) finish(id, needsSetup = true) else fail(id, CANT_ADD)
    }

    private fun finish(id: Int, needsSetup: Boolean) {
        val provider = pendingProvider.value.orEmpty()
        clear()
        cards.added(id, needsSetup)
        added.value(id, provider)
    }

    private fun fail(id: Int, message: String) {
        clear()
        cards.remove(id)
        failed.value(message)
    }

    private fun clear() {
        busy = false
        pendingId.intValue = 0
        pendingProvider.value = null
    }
}

private fun configureIntent(widgetId: Int, configure: android.content.ComponentName): Intent =
    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setComponent(configure)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

/** Launches [intent] for a result, the Discover screen waiting behind it; false when it couldn't be opened. */
private fun launchWaiting(context: Context, launcher: ActivityResultLauncher<Intent>, intent: Intent): Boolean {
    val screen = context.waitingScreen()
    screen?.waitWhileGranting(true)
    return try {
        launcher.launch(intent)
        true
    } catch (_: RuntimeException) {
        screen?.waitWhileGranting(false)
        false
    }
}

/**
 * Has Android open widget [widgetId]'s setup page for [host], for a page not open to other apps. Its answer goes to
 * the activity, which can't hand it on, so the caller can't tell how it ended.
 */
private fun startForHost(context: Context, host: AppWidgetHost, widgetId: Int): Boolean {
    val activity = context.findActivity() ?: return false
    val screen = context.waitingScreen()
    screen?.waitWhileGranting(true)
    return try {
        host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, REQUEST_HOST_SETUP, null)
        true
    } catch (_: ActivityNotFoundException) {
        screen?.waitWhileGranting(false)
        false
    } catch (_: SecurityException) {
        screen?.waitWhileGranting(false)
        false
    }
}

/** The screen that closes once out of sight and can be asked to wait behind what it opens. */
private tailrec fun Context.waitingScreen(): WaitsForPermissionGrant? = when (this) {
    is ComesBackContext -> screen
    is WaitsForPermissionGrant -> this
    is ContextWrapper -> baseContext?.waitingScreen()
    else -> null
}
