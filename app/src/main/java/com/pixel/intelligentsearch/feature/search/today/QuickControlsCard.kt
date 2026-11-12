package com.pixel.intelligentsearch.feature.search.today

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.provider.AlarmClock
import android.app.NotificationManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.pixel.intelligentsearch.feature.settings.reorderKeys
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import com.pixel.intelligentsearch.core.ui.icons.automirrored.outlined.VolumeUp
import com.pixel.intelligentsearch.core.ui.icons.filled.Alarm
import com.pixel.intelligentsearch.core.ui.icons.outlined.AirplanemodeActive
import com.pixel.intelligentsearch.core.ui.icons.outlined.BatterySaver
import com.pixel.intelligentsearch.core.ui.icons.outlined.Bluetooth
import com.pixel.intelligentsearch.core.ui.icons.outlined.Cast
import com.pixel.intelligentsearch.core.ui.icons.outlined.DarkMode
import com.pixel.intelligentsearch.core.ui.icons.outlined.DoNotDisturbOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.FlashlightOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.LocationOn
import com.pixel.intelligentsearch.core.ui.icons.outlined.Nfc
import com.pixel.intelligentsearch.core.ui.icons.outlined.ScreenRotation
import com.pixel.intelligentsearch.core.ui.icons.outlined.Timer
import com.pixel.intelligentsearch.core.ui.icons.outlined.Tune
import com.pixel.intelligentsearch.core.ui.icons.outlined.Wifi
import com.pixel.intelligentsearch.core.ui.icons.outlined.WifiTethering
import com.pixel.intelligentsearch.feature.search.today.cards.grid.EditIcons
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/*
 * Quick controls: the switches the user keeps on Discover, as many as they like, chosen from a list. Each switches
 * itself where Android lets an app (Do Not Disturb, the flashlight, auto-rotate once allowed) and otherwise opens the
 * panel or page that does; a tile shows on or off only where the app can read it.
 *
 * NG Designs
 */

/** The controls Discover offers, by the key the chosen list is kept under. */
internal enum class QuickControl(val key: String, val label: String) {
    DND("dnd", "Do Not Disturb"),
    FLASHLIGHT("flashlight", "Flashlight"),
    WIFI("wifi", "Internet"),
    BLUETOOTH("bluetooth", "Bluetooth"),
    HOTSPOT("hotspot", "Hotspot"),
    SAVER("saver", "Battery Saver"),
    AIRPLANE("airplane", "Airplane Mode"),
    LOCATION("location", "Location"),
    ROTATION("rotation", "Auto-rotate"),
    DARK("dark", "Dark Theme"),
    NFC("nfc", "NFC"),
    CAST("cast", "Cast"),
    VOLUME("volume", "Volume"),
    ALARM("alarm", "Alarms"),
    TIMER("timer", "Timers");

    companion object {
        /** The four the card has always had. */
        val DEFAULT = listOf(DND, FLASHLIGHT, HOTSPOT, SAVER)

        fun of(key: String): QuickControl? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The space between two controls, and the narrowest a control goes (at the default text size, wider for larger text)
 * before the card puts fewer in a row.
 */
private val TILE_GAP = 8.dp
private val CONTROL_MIN_WIDTH = 64.dp

/** Where the chosen controls are kept: Discover's own preferences file, with its cards' layout. */
private const val PREFS = "discover_cards"
private const val KEY = "controls"

/**
 * The controls chosen, in order, one list for the card and edit mode's Choose controls sheet, so a choice made in
 * either shows in the other at once. Null until read.
 */
private val chosenControls = MutableStateFlow<List<QuickControl>?>(null)

/** Counts choices made, so a read that finishes after one doesn't put back what was there before it. */
private val choicesMade = AtomicInteger()

/**
 * The controls chosen: read off the main thread the first time the card or the sheet comes, then kept in
 * [chosenControls]. Only these two and a restore ([restoreQuickControls]) write the file, so what is kept stays what is
 * saved.
 */
@Composable
private fun rememberChosenControls(context: Context): Pair<List<QuickControl>, (List<QuickControl>) -> Unit> {
    val chosen by chosenControls.collectAsState()
    LaunchedEffect(Unit) {
        if (chosenControls.value != null) return@LaunchedEffect
        val before = choicesMade.get()
        val saved = withContext(Dispatchers.IO) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) }
        if (choicesMade.get() == before) {
            chosenControls.value = saved?.split(',')?.mapNotNull(QuickControl::of)?.distinct() ?: QuickControl.DEFAULT
        }
    }
    val set: (List<QuickControl>) -> Unit = { list ->
        choicesMade.incrementAndGet()
        chosenControls.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, list.joinToString(",") { it.key }) }
    }
    return (chosen ?: QuickControl.DEFAULT) to set
}

/** The controls chosen, as the keys the file keeps ("dnd,flashlight"); null when none were ever chosen. Off the main thread. */
internal fun savedQuickControls(context: Context): String? =
    chosenControls.value?.joinToString(",") { it.key }
        ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

/**
 * Makes [keys] (a backup's [savedQuickControls]) the controls chosen, shown at once if the card is up. Keys this
 * version doesn't know are left out; false, and nothing changed, when none is known.
 */
internal fun restoreQuickControls(context: Context, keys: String): Boolean {
    val list = keys.split(',').mapNotNull { QuickControl.of(it.trim()) }.distinct()
    if (list.isEmpty()) return false
    choicesMade.incrementAndGet()
    chosenControls.value = list
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putString(KEY, list.joinToString(",") { it.key }) }
    return true
}

/** Counts up each time the page comes back to the front, so states read from Android are read again after its pages. */
@Composable
private fun rememberResumes(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(0, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { value += 1 }
    }.value
}

/** Whether [control] is there on this phone (the flashlight and NFC need the hardware). */
private fun available(context: Context, control: QuickControl, hasFlash: Boolean): Boolean = when (control) {
    QuickControl.FLASHLIGHT -> hasFlash
    QuickControl.NFC -> NfcAdapter.getDefaultAdapter(context) != null
    else -> true
}

/** Whether [control] is on, where Android lets the app read it; null where it can't, so the tile claims neither. */
private fun readState(context: Context, control: QuickControl): Boolean? = runCatching {
    when (control) {
        QuickControl.WIFI -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled
        QuickControl.BLUETOOTH -> context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled
        QuickControl.AIRPLANE -> Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        QuickControl.LOCATION -> context.getSystemService(LocationManager::class.java)?.isLocationEnabled
        QuickControl.ROTATION -> Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        QuickControl.DARK -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ||
            context.getSystemService(UiModeManager::class.java)?.nightMode == UiModeManager.MODE_NIGHT_YES
        QuickControl.NFC -> NfcAdapter.getDefaultAdapter(context)?.isEnabled
        else -> null
    }
}.getOrNull()

/** Where a tap on [control] goes when the app can't switch it itself. */
private fun panel(control: QuickControl): Intent? = when (control) {
    QuickControl.WIFI -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    QuickControl.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    QuickControl.HOTSPOT -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
    QuickControl.SAVER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
    QuickControl.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    QuickControl.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    QuickControl.ROTATION, QuickControl.DARK -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
    QuickControl.NFC -> Intent(Settings.Panel.ACTION_NFC)
    QuickControl.CAST -> Intent(Settings.ACTION_CAST_SETTINGS)
    QuickControl.VOLUME -> Intent(Settings.Panel.ACTION_VOLUME)
    QuickControl.ALARM -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    QuickControl.TIMER -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
    QuickControl.DND, QuickControl.FLASHLIGHT -> null
}

private fun icon(control: QuickControl): ImageVector = when (control) {
    QuickControl.DND -> Icons.Outlined.DoNotDisturbOn
    QuickControl.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    QuickControl.WIFI -> Icons.Outlined.Wifi
    QuickControl.BLUETOOTH -> Icons.Outlined.Bluetooth
    QuickControl.HOTSPOT -> Icons.Outlined.WifiTethering
    QuickControl.SAVER -> Icons.Outlined.BatterySaver
    QuickControl.AIRPLANE -> Icons.Outlined.AirplanemodeActive
    QuickControl.LOCATION -> Icons.Outlined.LocationOn
    QuickControl.ROTATION -> Icons.Outlined.ScreenRotation
    QuickControl.DARK -> Icons.Outlined.DarkMode
    QuickControl.NFC -> Icons.Outlined.Nfc
    QuickControl.CAST -> Icons.Outlined.Cast
    QuickControl.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
    QuickControl.ALARM -> Icons.Filled.Alarm
    QuickControl.TIMER -> Icons.Outlined.Timer
}

/**
 * The user's quick controls, one to four to a row as the card's width and the text size allow, and a last tile to add,
 * remove or move them. Live, so a switch flipped in the
 * notification shade while the page is open shows here too; Android's own pages come back here.
 */
@Composable
internal fun QuickControls(controls: TodayControls) {
    val context = LocalContext.current
    val (chosen, _) = rememberChosenControls(context)
    var picking by remember { mutableStateOf(false) }
    val dnd by remember(controls) { controls.doNotDisturb() }.collectAsState(initial = false)
    val flashlight = remember(controls) { controls.flashlight() }
    val torch = flashlight?.collectAsState(initial = false)?.value
    val saver by remember(controls) { controls.batterySaver() }.collectAsState(initial = false)
    val resumes = rememberResumes()
    var flips by remember { mutableIntStateOf(0) }
    val shown = chosen.filter { available(context, it, flashlight != null) }
    // Read off the main thread (each is a call to Android), and again when the page comes back or its window is looked
    // at again: the notification shade covers the page without pausing it.
    val focused = LocalWindowInfo.current.isWindowFocused
    val states by produceState(emptyMap<QuickControl, Boolean?>(), shown, resumes, flips, focused) {
        value = withContext(Dispatchers.IO) { shown.associateWith { readState(context, it) } }
    }

    fun state(control: QuickControl): Boolean? = when (control) {
        QuickControl.DND -> dnd
        QuickControl.FLASHLIGHT -> torch
        QuickControl.SAVER -> saver
        else -> states[control]
    }

    // Which of the controls an app may switch it can switch now, read with the states: Do Not Disturb once the app has
    // Do Not Disturb access, Auto-rotate once it may change system settings. Until then a tap opens Android's page.
    val switchable by produceState<Set<QuickControl>?>(null, resumes, flips, focused) {
        value = withContext(Dispatchers.IO) {
            buildSet {
                val dndAccess = runCatching { context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted }
                if (dndAccess.getOrNull() == true) add(QuickControl.DND)
                if (runCatching { Settings.System.canWrite(context) }.getOrDefault(false)) add(QuickControl.ROTATION)
            }
        }
    }

    // Whether a tap opens Android's panel or page rather than switching it here: everything but the few an app may switch.
    fun opens(control: QuickControl): Boolean = when (control) {
        QuickControl.FLASHLIGHT -> false
        QuickControl.DND, QuickControl.ROTATION -> switchable?.let { control !in it } ?: false
        else -> true
    }

    fun tap(control: QuickControl) {
        when (control) {
            QuickControl.DND -> controls.setDoNotDisturb(!dnd)?.let { launchSafeIntent(context, it) }
            QuickControl.FLASHLIGHT -> torch?.let { controls.setFlashlight(!it) }
            // Auto-rotate switches here once the app may change system settings; until then its page opens.
            QuickControl.ROTATION -> if (Settings.System.canWrite(context)) {
                val on = state(control) == true
                runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1) }
                flips++
            } else {
                panel(control)?.let { launchSafeIntent(context, it) }
            }
            else -> panel(control)?.let { launchSafeIntent(context, it) }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = CARD_ALPHA))
            .padding(14.dp)
    ) {
        CardLabel(Icons.Outlined.Tune, "Quick controls", modifier = Modifier.padding(bottom = 12.dp))
        val tiles: List<QuickControl?> = shown + listOf(null)
        // As many to a row as fit at a readable size, up to four, so a narrow card keeps its controls full size.
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Larger text needs wider controls for two-line labels: fewer to a row, never a cut-off name.
            val least = CONTROL_MIN_WIDTH * fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth + TILE_GAP) / (least + TILE_GAP)).toInt().coerceIn(1, 4)
            Column {
                tiles.chunked(columns).forEachIndexed { i, row ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                        for (control in row) {
                            if (control == null) {
                                AddControlTile(Modifier.weight(1f)) { picking = true }
                            } else {
                                ControlTile(control.label, icon(control), state(control), opens(control), Modifier.weight(1f)) { tap(control) }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (picking) ChooseControlsSheet(controls) { picking = false }
}

/**
 * The list of controls to switch on or off for the card: opened by the card's Edit tile, and by Choose controls on the
 * card's toolbar in edit mode, where the card's own tiles take no touches.
 */
@Composable
internal fun ChooseControlsSheet(controls: TodayControls, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (chosen, setChosen) = rememberChosenControls(context)
    val hasFlash = remember(controls) { controls.flashlight() != null }
    ControlsSheet(
        chosen = chosen,
        available = QuickControl.entries.filter { available(context, it, hasFlash) },
        onChange = setChosen,
        onDismiss = onDismiss
    )
}

/** The last tile: an outlined control with a plus, which opens the list of controls. */
@Composable
private fun AddControlTile(modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CONTROL_ASPECT)
                .clip(ControlShape)
                .border(BorderStroke(1.5.dp, scheme.outlineVariant), ControlShape)
                .bouncyClickable(shape = ControlShape, onClick = onClick)
                .semantics { contentDescription = "Edit controls" },
            contentAlignment = Alignment.Center
        ) {
            Icon(EditIcons.Add, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Text(
            "Edit",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {}
        )
    }
}

/**
 * The card's controls, as Android's own Quick Settings editor has them: the user's, in the card's order, to hold and
 * drag into a new place or tap to take off; then the rest the phone has, to tap to add at the end. Changes show at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsSheet(
    chosen: List<QuickControl>,
    available: List<QuickControl>,
    onChange: (List<QuickControl>) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val yours = chosen.filter { it in available }
    val others = available.filter { it !in chosen }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                "Quick controls",
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp).semantics { heading() }
            )
            SheetSection("Your controls", if (yours.size > 1) "Hold and drag to move. Tap to remove." else "Tap to remove.")
            if (yours.isEmpty()) {
                Text("None yet", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                // A move is kept on the whole list; controls this phone lacks stay at its end.
                MovableControls(yours) { moved -> onChange(moved + chosen.filter { it !in available }) }
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SheetSection("Add controls", "Tap to add.")
                ControlGrid(others) { control ->
                    SheetControl(control, on = false, clickLabel = "Add to Quick controls") { onChange(chosen + control) }
                }
            }
        }
    }
}

/** A section of the sheet: its [title] and how to use it. */
@Composable
private fun SheetSection(title: String, hint: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
}

/** [controls] three to a row. */
@Composable
private fun ControlGrid(controls: List<QuickControl>, tile: @Composable (QuickControl) -> Unit) {
    controls.chunked(SHEET_COLUMNS).forEachIndexed { i, row ->
        if (i > 0) Spacer(Modifier.height(SHEET_ROW_GAP))
        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            for (control in row) Box(Modifier.weight(1f)) { tile(control) }
            repeat(SHEET_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * One control in the sheet: the card's oval with its icon, lit while it is one of the user's, and its name under it.
 * [clickLabel] is what TalkBack says a double tap does (add or remove); [actions] are the moves it offers.
 */
@Composable
private fun SheetControl(
    control: QuickControl,
    on: Boolean,
    clickLabel: String,
    actions: List<CustomAccessibilityAction> = emptyList(),
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CONTROL_ASPECT)
                .clip(ControlShape)
                .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHigh)
                .bouncyClickable(shape = ControlShape, onClickLabel = clickLabel, onClick = onClick)
                // The node TalkBack stops on (the clickable merges its children), so the moves are offered here.
                .semantics {
                    contentDescription = control.label
                    if (actions.isNotEmpty()) customActions = actions
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon(control), contentDescription = null, tint = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Text(
            control.label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics {}
        )
    }
}

/**
 * The user's controls in a grid of their own, so each keeps its place in the composition as it moves: held, one lifts
 * and follows the finger, the others making way as it passes over them, and the new order is kept when it is let go.
 * A tap takes one off. TalkBack and keyboards move them one place at a time.
 */
@Composable
private fun MovableControls(controls: List<QuickControl>, onChange: (List<QuickControl>) -> Unit) {
    // The app's one haptic, under its Vibration setting, as a control lifts and as it passes each place.
    val view = LocalView.current
    val haptic = { PixelHapticEngine.playButtonTap(view.context, view) }
    // The drag's handlers live as long as their control does, so they read the list and the callback as they are now,
    // not as they were when the handler started: a control removed by a tap must not come back with the next drop.
    val current by rememberUpdatedState(controls)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf<QuickControl?>(null) }
    // The order while one is held, the others making way; otherwise the user's choices as they are.
    var held by remember { mutableStateOf(controls) }
    val order = if (dragging != null) held else controls
    var cell by remember { mutableStateOf(IntSize.Zero) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grip by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val gapX = with(density) { TILE_GAP.toPx() }
    val gapY = with(density) { SHEET_ROW_GAP.toPx() }

    fun origin(index: Int) = Offset(
        (index % SHEET_COLUMNS) * (cell.width + gapX),
        (index / SHEET_COLUMNS) * (cell.height + gapY)
    )

    fun indexAt(p: Offset): Int {
        val column = (p.x / (cell.width + gapX)).toInt().coerceIn(0, SHEET_COLUMNS - 1)
        val row = (p.y / (cell.height + gapY)).toInt().coerceAtLeast(0)
        return (row * SHEET_COLUMNS + column).coerceIn(0, held.lastIndex)
    }

    fun move(control: QuickControl, by: Int) {
        val list = current
        val from = list.indexOf(control)
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        change(list.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun remove(control: QuickControl) {
        // A press held still lifts the tile; letting go of it is the drag's end, not a tap that removes it. The tap
        // arrives before the drag's end clears [dragging].
        if (dragging != null) return
        change(current - control)
    }

    fun drop() {
        // The order as dragged, of the controls still chosen, then any added while it was held (a second finger can
        // tap Add controls during a drag): a drop never undoes a choice made meanwhile.
        val now = current
        val dropped = held.filter { it in now } + now.filter { it !in held }
        if (dragging != null && dropped != now) change(dropped)
        dragging = null
    }

    Layout(
        content = {
            for (control in order) {
                key(control) {
                    val lifted = dragging == control
                    val index = order.indexOf(control)
                    Box(
                        Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            // Ahead of the lifted tile's scale: inside it, every move would arrive 1.06 times smaller
                            // and the tile would fall behind the finger.
                            .pointerInput(control) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at ->
                                        haptic()
                                        held = current
                                        grip = at
                                        finger = origin(held.indexOf(control)) + at
                                        dragging = control
                                    },
                                    onDrag = { pointer, amount ->
                                        pointer.consume()
                                        finger += amount
                                        val to = indexAt(finger)
                                        val from = held.indexOf(control)
                                        if (from >= 0 && to != from) {
                                            held = held.toMutableList().apply { add(to, removeAt(from)) }
                                            haptic()
                                        }
                                    },
                                    onDragEnd = { drop() },
                                    onDragCancel = { drop() }
                                )
                            }
                            .graphicsLayer {
                                if (lifted) {
                                    val at = finger - grip - origin(index)
                                    translationX = at.x
                                    translationY = at.y
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            }
                            .reorderKeys(
                                up = if (index > 0) ({ move(control, -1) }) else null,
                                down = if (index < order.lastIndex) ({ move(control, 1) }) else null,
                                remove = { remove(control) }
                            )
                    ) {
                        SheetControl(
                            control,
                            on = true,
                            clickLabel = "Remove from Quick controls",
                            actions = listOfNotNull(
                                CustomAccessibilityAction("Move earlier") { move(control, -1); true }.takeIf { index > 0 },
                                CustomAccessibilityAction("Move later") { move(control, 1); true }.takeIf { index < order.lastIndex }
                            )
                        ) { remove(control) }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = TILE_GAP.roundToPx()
        val rowGap = SHEET_ROW_GAP.roundToPx()
        val tileWidth = (width - gap * (SHEET_COLUMNS - 1)) / SHEET_COLUMNS
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(tileWidth)) }
        val tileHeight = placeables.maxOfOrNull { it.height } ?: 0
        if (cell != IntSize(tileWidth, tileHeight)) cell = IntSize(tileWidth, tileHeight)
        val rows = (placeables.size + SHEET_COLUMNS - 1) / SHEET_COLUMNS
        layout(width, rows * tileHeight + (rows - 1).coerceAtLeast(0) * rowGap) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place((i % SHEET_COLUMNS) * (tileWidth + gap), (i / SHEET_COLUMNS) * (tileHeight + rowGap))
            }
        }
    }
}

/** The sheet's grid: three controls to a row, and the space between rows. */
private const val SHEET_COLUMNS = 3
private val SHEET_ROW_GAP = 14.dp
