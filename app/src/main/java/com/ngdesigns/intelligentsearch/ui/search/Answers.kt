package com.ngdesigns.intelligentsearch.ui.search

import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.engine.Instant
import com.ngdesigns.intelligentsearch.engine.actions.Action
import com.ngdesigns.intelligentsearch.engine.calc.Answer
import com.ngdesigns.intelligentsearch.engine.toggles.Toggle
import com.ngdesigns.intelligentsearch.engine.toggles.VolumeStream
import com.ngdesigns.intelligentsearch.ui.components.rememberHaptics
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What an instant answer can ask the screen to do. */
interface AnswerActions {
    fun copy(text: String)
    fun openCalculator()
    fun run(action: Action)
    fun toggle(toggle: Toggle, on: Boolean)
    fun shortcut(use: com.ngdesigns.intelligentsearch.engine.web.WebShortcuts.Use)
    fun address(url: String)
}

@Composable
fun InstantAnswer(instant: Instant, torchOn: Boolean, actions: AnswerActions, modifier: Modifier = Modifier) {
    val m = modifier.fillMaxWidth().padding(horizontal = 12.dp)
    when (instant) {
        is Instant.Calculation -> CalculationCard(instant.answer, actions, m)
        is Instant.Command -> CommandCard(instant.action, actions, m)
        is Instant.Switch -> ToggleRow(listOf(instant.request.toggle to (if (instant.request.toggle == Toggle.Flashlight) torchOn else null)), instant.request.wanted, actions, m)
        is Instant.Volume -> VolumeCard(instant.stream, m)
        is Instant.Shortcut -> LinkRow(Icons.Rounded.Search, "Search ${instant.use.shortcut.name}", instant.use.terms.ifBlank { "Open ${instant.use.shortcut.name}" }, { actions.shortcut(instant.use) }, m)
        is Instant.Address -> LinkRow(Icons.Rounded.Public, instant.url.removePrefix("https://").removeSuffix("/"), "Open in your browser", { actions.address(instant.url) }, m)
    }
}

/** A plain decimal result with the locale's digit grouping for reading ("1283.75" -> "1,283.75"); anything else as is. */
internal fun grouped(text: String): String {
    val m = Regex("^(-?)([0-9]+)([.][0-9]+)?$").find(text) ?: return text
    val whole = m.groupValues[2].toBigInteger()
    return m.groupValues[1] + java.text.NumberFormat.getIntegerInstance().format(whole) + m.groupValues[3].replace(".", java.text.DecimalFormatSymbols.getInstance().decimalSeparator.toString())
}

/** "= 1,284.75": light display type with the whole-number part in an emphasized weight. */
private fun figure(raw: String): AnnotatedString = buildAnnotatedString {
    val text = grouped(raw)
    val decimal = java.text.DecimalFormatSymbols.getInstance().decimalSeparator
    val cut = text.indexOfFirst { it == decimal || it == 'e' || it == ' ' }.let { if (it < 0) text.length else it }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, cut)) }
    append(text.substring(cut))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalculationCard(answer: Answer, actions: AnswerActions, modifier: Modifier) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    val (expression, result, note) = when (answer) {
        is Answer.Number -> Triple(answer.expression, answer.formatted, null)
        is Answer.Conversion -> Triple(
            "${answer.fromValue.toBigDecimal().stripTrailingZeros().toPlainString()} ${answer.from.name}",
            answer.formatted,
            if (answer.approximate) "Approximate rate" else null,
        )
        is Answer.Bits -> Triple(answer.expression, answer.hex, "${answer.unsigned} · ${answer.binary}")
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(expression, style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer.copy(alpha = 0.8f), maxLines = 2)
            Text(
                buildAnnotatedString { append("= "); append(figure(result)) },
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(vertical = 4.dp).semantics { contentDescription = "equals $result" },
            )
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer.copy(alpha = 0.8f)) }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { haptics.confirm(); actions.copy(result) }) { Text("Copy") }
                OutlinedButton(onClick = actions::openCalculator) { Text("Open Calculator") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommandCard(action: Action, actions: AnswerActions, modifier: Modifier) {
    val haptics = rememberHaptics()
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    data class Shown(val icon: ImageVector, val kind: String, val title: String, val detail: String?, val button: String)
    val shown = when (action) {
        is Action.Timer -> Shown(Icons.Rounded.Timer, "Timer", duration(action.seconds), null, "Start")
        is Action.Alarm -> Shown(Icons.Rounded.Alarm, "Alarm", action.time.format(time), action.label, "Set alarm")
        is Action.Reminder -> Shown(Icons.Rounded.TaskAlt, "Reminder", action.task, action.at?.format(dateTime), "Add")
        is Action.Event -> Shown(Icons.Rounded.Event, "Event", action.title, action.start.format(dateTime), "Add to calendar")
        is Action.Message -> Shown(Icons.Rounded.Sms, "Message", action.recipient, action.body, "Write")
        is Action.Call -> Shown(Icons.Rounded.Call, if (action.video) "Video call" else "Call", action.target, null, "Call")
        is Action.Navigate -> Shown(Icons.Rounded.Navigation, "Directions", action.destination, null, "Go")
    }
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                if (action is Action.Timer) {
                    CircularWavyProgressIndicator(progress = { 1f }, modifier = Modifier.size(64.dp))
                } else {
                    Box(Modifier.size(56.dp).clip(MaterialShapes.Sunny.toShape()).background(MaterialTheme.colorScheme.secondary))
                }
                Icon(shown.icon, contentDescription = null, tint = if (action is Action.Timer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(shown.kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                Text(shown.title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2)
                shown.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { haptics.confirm(); actions.run(action) }) { Text(shown.button) }
        }
    }
}

internal fun duration(seconds: Int): String {
    val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        else -> "%d:%02d".format(m, s)
    }
}

private fun toggleIcon(t: Toggle) = when (t) {
    Toggle.Wifi -> Icons.Rounded.Wifi
    Toggle.Bluetooth -> Icons.Rounded.Bluetooth
    Toggle.Flashlight -> Icons.Rounded.FlashlightOn
    Toggle.BatterySaver -> Icons.Rounded.BatterySaver
    Toggle.DoNotDisturb -> Icons.Rounded.DoNotDisturbOn
    Toggle.AirplaneMode -> Icons.Rounded.AirplanemodeActive
    Toggle.AutoRotate -> Icons.Rounded.ScreenRotation
    Toggle.Hotspot -> Icons.Rounded.WifiTethering
    Toggle.DarkTheme -> Icons.Rounded.DarkMode
    Toggle.Nfc -> Icons.Rounded.Nfc
    Toggle.Location -> Icons.Rounded.LocationOn
}

/**
 * System toggles as large icon toggle buttons: round when off, morphing to a rounded square when on. The flashlight
 * switches directly; the rest open the system panel for that setting, since apps can't switch them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToggleRow(toggles: List<Pair<Toggle, Boolean?>>, wanted: Boolean?, actions: AnswerActions, modifier: Modifier) {
    val haptics = rememberHaptics()
    // One surface level below the button, so the unchecked (surface container highest) button stands out.
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            toggles.forEach { (toggle, on) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = on == true,
                        onCheckedChange = { haptics.toggle(it); actions.toggle(toggle, wanted ?: it) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors = IconButtonDefaults.filledIconToggleButtonColors(),
                        modifier = Modifier.size(IconButtonDefaults.largeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) { Icon(toggleIcon(toggle), contentDescription = toggle.label, modifier = Modifier.size(IconButtonDefaults.largeIconSize)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(toggle.label, style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            when { on == true -> "On"; on == false -> "Off"; else -> "Opens the system setting" },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun streamOf(s: VolumeStream) = when (s) {
    VolumeStream.Media -> AudioManager.STREAM_MUSIC
    VolumeStream.Ring -> AudioManager.STREAM_RING
    VolumeStream.Alarm -> AudioManager.STREAM_ALARM
}

/** A volume stream as a tall vertical slider with its icon inset, changing the volume as it moves. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeCard(stream: VolumeStream, modifier: Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val type = streamOf(stream)
    val max = remember(type) { audio.getStreamMaxVolume(type).coerceAtLeast(1) }
    val haptics = rememberHaptics()
    val state = remember(type) { SliderState(value = audio.getStreamVolume(type).toFloat(), steps = (max - 1).coerceAtLeast(0), trackRange = 0f..max.toFloat()) }
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            VerticalSlider(
                state = state,
                onValueChange = { v ->
                    state.value = v
                    haptics.tick()
                    runCatching { audio.setStreamVolume(type, v.toInt(), 0) }
                },
                topToBottom = false,
                modifier = Modifier.height(180.dp).semantics { contentDescription = stream.label },
                colors = SliderDefaults.colors(),
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Icon(if (stream == VolumeStream.Alarm) Icons.Rounded.Alarm else if (stream == VolumeStream.Ring) Icons.Rounded.NotificationsActive else Icons.Rounded.MusicNote, contentDescription = null)
                Text(stream.label, style = MaterialTheme.typography.titleMediumEmphasized)
                Text("${(state.value / max * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(detail, maxLines = 1) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        modifier = modifier,
    ) { Text(title, maxLines = 1) }
}
