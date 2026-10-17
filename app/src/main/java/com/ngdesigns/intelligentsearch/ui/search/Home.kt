package com.ngdesigns.intelligentsearch.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.data.device.AppEntry
import com.ngdesigns.intelligentsearch.data.history.PastSearch
import com.ngdesigns.intelligentsearch.ui.components.AppIcon
import com.ngdesigns.intelligentsearch.ui.components.rememberHaptics
import java.time.LocalTime

/** The greeting for the time of day, set in light display type (Neural Expressive). */
@Composable
fun Greeting(modifier: Modifier = Modifier, now: LocalTime = LocalTime.now()) {
    val text = when (now.hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Text(text, style = MaterialTheme.typography.displayMedium, modifier = modifier.padding(horizontal = 24.dp))
}

/** Recent searches as suggestion chips: tap to search again. (Removing one is in its long-press menu in results.) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentChips(recents: List<PastSearch>, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    if (recents.isEmpty()) return
    val haptics = rememberHaptics()
    FlowRow(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        recents.forEach { r ->
            SuggestionChip(
                onClick = { haptics.tick(); onSearch(r.query) },
                label = { Text(r.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                modifier = Modifier.semantics { contentDescription = "Search again for ${r.query}" },
            )
        }
    }
}

/** Suggested apps in rows of [columns], each icon in the user's chosen shape with its name underneath. */
@Composable
fun SuggestedApps(apps: List<AppEntry>, iconShape: String, columns: Int, onOpen: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) return
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Column(
                        Modifier.weight(1f).combinedClickable(onClick = { haptics.confirm(); onOpen(app) }).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app.target, 56.dp, shapeName = iconShape)
                        Spacer(Modifier.height(6.dp))
                        Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A command the create menu starts, by filling the search bar with its first words. */
data class Starter(val label: String, val text: String)

val starters = listOf(
    Starter("New event", "meeting tomorrow at "),
    Starter("Alarm", "alarm "),
    Starter("Timer", "timer "),
    Starter("Note", ""),
    Starter("Directions", "directions to "),
)

/**
 * The create menu: a FAB menu (tertiary) whose items start a command in the search bar ("timer ", "alarm "),
 * which also teaches the commands. Note opens the notes app directly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateMenu(expanded: Boolean, onExpanded: (Boolean) -> Unit, onStart: (Starter) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { haptics.tick(); onExpanded(it) },
                modifier = Modifier.semantics { contentDescription = if (expanded) "Close create menu" else "Create" },
            ) {
                val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                Icon(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        starters.forEach { s ->
            FloatingActionButtonMenuItem(
                onClick = { haptics.confirm(); onExpanded(false); onStart(s) },
                text = { Text(s.label) },
                icon = {
                    Icon(
                        when (s.label) { "New event" -> Icons.Rounded.Event; "Alarm" -> Icons.Rounded.Alarm; "Timer" -> Icons.Rounded.Timer; "Note" -> Icons.Rounded.EditNote; else -> Icons.Rounded.Navigation },
                        contentDescription = null,
                    )
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
    }
}
