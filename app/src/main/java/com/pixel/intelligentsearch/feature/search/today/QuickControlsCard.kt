package com.pixel.intelligentsearch.feature.search.today

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pixel.intelligentsearch.core.ui.icons.filled.Check
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

/** The controls chosen: read off the main thread each time the card or sheet comes (a restored backup shows too). */
@Composable
private fun rememberChosenControls(context: Context): Pair<List<QuickControl>, (List<QuickControl>) -> Unit> {
    val chosen by chosenControls.collectAsState()
    LaunchedEffect(Unit) {
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
 * The user's quick controls, four to a row, and a last tile to add or take some away. Live, so a switch flipped in the
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
        tiles.chunked(4).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (control in row) {
                    if (control == null) {
                        AddControlTile(Modifier.weight(1f)) { picking = true }
                    } else {
                        ControlTile(control.label, icon(control), state(control), Modifier.weight(1f)) { tap(control) }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
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

/** The last tile: a dashed square with a plus, which opens the list of controls. */
@Composable
private fun AddControlTile(modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(BorderStroke(1.5.dp, scheme.outlineVariant), RoundedCornerShape(18.dp))
                .bouncyClickable(shape = RoundedCornerShape(18.dp), onClick = onClick)
                .semantics { contentDescription = "Add or remove controls" },
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
 * Every control the phone has, as tiles to switch on or off for the card; the card's order is the order they were
 * chosen in. Changes show at once.
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
            available.chunked(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (control in row) {
                        val on = control in chosen
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) scheme.primaryContainer else scheme.surfaceContainerHigh)
                                .toggleable(value = on, role = Role.Checkbox) { add ->
                                    onChange(if (add) chosen + control else chosen - control)
                                }
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon(control),
                                    contentDescription = null,
                                    tint = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                control.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) scheme.onPrimaryContainer else scheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp).size(16.dp))
                            else Spacer(Modifier.height(20.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
