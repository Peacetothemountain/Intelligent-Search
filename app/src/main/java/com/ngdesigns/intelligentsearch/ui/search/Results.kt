package com.ngdesigns.intelligentsearch.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ngdesigns.intelligentsearch.data.device.Target
import com.ngdesigns.intelligentsearch.engine.Kind
import com.ngdesigns.intelligentsearch.engine.Ranked
import com.ngdesigns.intelligentsearch.ui.components.AppIcon
import com.ngdesigns.intelligentsearch.ui.components.Monogram
import com.ngdesigns.intelligentsearch.ui.components.rememberHaptics
import com.ngdesigns.intelligentsearch.ui.theme.ContentColors

/** What a result row can ask the screen to do. */
interface ResultActions {
    fun open(item: Ranked)
    fun insert(text: String)
    fun openApp(app: com.ngdesigns.intelligentsearch.data.device.AppEntry)
    fun call(item: Ranked)
    fun message(item: Ranked)
    fun appInfo(item: Ranked)
    fun hide(item: Ranked)
    fun uninstall(item: Ranked)
    fun forget(query: String)
    fun searchWeb(text: String)
}

fun sectionTitle(kind: Kind) = when (kind) {
    Kind.App -> "Apps"
    Kind.Shortcut -> "Shortcuts"
    Kind.Contact -> "People"
    Kind.Event -> "Calendar"
    Kind.File -> "Files"
    Kind.Setting -> "Settings"
    Kind.History -> "Recent searches"
    Kind.Web -> "Web"
}

/** A group label: the name, and how many results it holds in the primary colour. */
@Composable
fun SectionHeader(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (count != null) Text("$count", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
    }
}

/** Segmented rows sit on their own tonal surface, one step above the screen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rowColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** One result in a segmented group: each row its own surface, large corners on the group's ends. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultRow(item: Ranked, target: Target?, index: Int, count: Int, iconShape: String, actions: ResultActions, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val c = item.candidate
    Box(modifier.padding(horizontal = 12.dp)) {
        SegmentedListItem(
            onClick = { haptics.confirm(); actions.open(item) },
            onLongClick = { haptics.longPress(); menu = true },
            onLongClickLabel = "More options",
            shapes = ListItemDefaults.segmentedShapes(index, count),
            colors = rowColors(),
            leadingContent = { Leading(item, target, iconShape) },
            supportingContent = c.subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = trailingFor(item, target, actions),
        ) {
            Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ResultMenu(menu, onDismiss = { menu = false }, item, target, actions)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Leading(item: Ranked, target: Target?, iconShape: String) {
    val size = 40.dp
    when {
        target is Target.App || target is Target.Shortcut -> AppIcon(target, size, shapeName = iconShape)
        item.candidate.kind == Kind.Contact -> Monogram(item.candidate.title, size)
        else -> {
            val icon = when (item.candidate.kind) {
                Kind.Event -> Icons.Rounded.Event
                Kind.File -> Icons.Rounded.Description
                Kind.Setting -> Icons.Rounded.Settings
                Kind.History -> Icons.Rounded.History
                else -> Icons.Rounded.Search
            }
            Box(
                Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun trailingFor(item: Ranked, target: Target?, actions: ResultActions): (@Composable () -> Unit)? = when {
    target is Target.Contact && target.phone != null -> {
        { FilledTonalIconButton(onClick = { actions.call(item) }) { Icon(Icons.Rounded.Call, contentDescription = "Call ${item.candidate.title}") } }
    }
    item.candidate.kind == Kind.History -> {
        { IconButton(onClick = { actions.insert(item.candidate.title) }) { Icon(Icons.Rounded.NorthWest, contentDescription = "Put in search bar") } }
    }
    else -> null
}

/**
 * The long-press menu: an expressive vertical menu with groups separated by gaps. The first action is shown in the
 * vibrant (selected) style.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultMenu(open: Boolean, onDismiss: () -> Unit, item: Ranked, target: Target?, actions: ResultActions) {
    data class Entry(val label: String, val icon: ImageVector, val danger: Boolean = false, val run: () -> Unit)
    val groups = buildList {
        add(listOf(Entry("Open", Icons.AutoMirrored.Rounded.OpenInNew) { actions.open(item) }))
        when (target) {
            is Target.App -> {
                add(listOf(Entry("App info", Icons.Rounded.Info) { actions.appInfo(item) }, Entry("Hide from search", Icons.Rounded.VisibilityOff) { actions.hide(item) }))
                add(listOf(Entry("Uninstall", Icons.Rounded.Delete, danger = true) { actions.uninstall(item) }))
            }
            is Target.Contact -> if (target.phone != null) add(listOf(Entry("Call", Icons.Rounded.Call) { actions.call(item) }, Entry("Message", Icons.Rounded.Sms) { actions.message(item) }))
            is Target.Query -> add(listOf(Entry("Remove from history", Icons.Rounded.Delete, danger = true) { actions.forget(item.candidate.title) }))
            else -> Unit
        }
        add(listOf(Entry("Search the web for this", Icons.Rounded.Search) { actions.searchWeb(item.candidate.title) }))
    }
    DropdownMenuPopup(expanded = open, onDismissRequest = onDismiss) {
        groups.forEachIndexed { g, entries ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, groups.size)) {
                entries.forEachIndexed { i, e ->
                    CheckableDropdownMenuItem(
                        checked = g == 0 && i == 0,
                        onCheckedChange = { onDismiss(); e.run() },
                        text = { Text(e.label, color = if (e.danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
                        shapes = MenuDefaults.itemShape(i, entries.size),
                        leadingIcon = { Icon(e.icon, contentDescription = null) },
                        checkedLeadingIcon = { Icon(e.icon, contentDescription = null) },
                    )
                }
            }
            if (g < groups.lastIndex) Spacer(Modifier.size(MenuDefaults.GroupSpacing))
        }
    }
}

/**
 * The best match, a hero card coloured from the result's own icon (content-based colour). An app's icon sits in a
 * large Material cookie shape; Enter or the split button opens it, the menu half offers more.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BestMatchCard(item: Ranked, target: Target?, actions: ResultActions, modifier: Modifier = Modifier) {
    var seed by remember(item.candidate.id) { mutableStateOf<Color?>(null) }
    var menu by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    ContentColors(seed) {
        Card(
            onClick = { haptics.confirm(); actions.open(item) },
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (target) {
                        is Target.App, is Target.Shortcut -> AppIcon(target, 64.dp, shapeName = "Cookie", onLoaded = { seed = it.seed })
                        else -> Monogram(item.candidate.title, 64.dp, polygon = MaterialShapes.Cookie9Sided)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.candidate.title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Best match · " + (item.candidate.subtitle ?: sectionTitle(item.candidate.kind).removeSuffix("s").lowercase()),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        SplitButtonLayout(
                            leadingButton = {
                                SplitButtonDefaults.LeadingButton(onClick = { haptics.confirm(); actions.open(item) }) { Text("Open") }
                            },
                            trailingButton = {
                                SplitButtonDefaults.TrailingButton(checked = menu, onCheckedChange = { menu = it; haptics.tick() }) {
                                    Icon(if (menu) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = "More options")
                                }
                            },
                        )
                        ResultMenu(menu, onDismiss = { menu = false }, item, target, actions)
                    }
                    when (target) {
                        is Target.App -> FilledTonalButton(onClick = { actions.appInfo(item) }, contentPadding = ButtonDefaults.SmallContentPadding) { Text("App info") }
                        is Target.Contact -> if (target.phone != null) FilledTonalButton(onClick = { actions.message(item) }) { Text("Message") }
                        else -> Unit
                    }
                }
            }
        }
    }
}
