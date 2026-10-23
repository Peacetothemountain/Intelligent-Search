package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.data.FileOpeners
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.OpenWithChoice
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.ui.fileTypeIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app offered under Open Files With, its icon ready to draw. */
internal class OpenerOption(val packageName: String, val label: String, val icon: ImageBitmap?)

internal const val ASK_EVERY_TIME_LABEL = "Ask Every Time"
internal const val DEFAULT_APP_LABEL = "Default App"

/**
 * The menu entries for one file type: Ask Every Time, Default App, then each app that opens the type. Two apps
 * with the same name are told apart by their package, so every entry is unique.
 */
internal fun openWithEntries(apps: List<OpenerOption>): List<Pair<String, OpenWithChoice>> {
    val repeated = apps.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
    return listOf(ASK_EVERY_TIME_LABEL to OpenWithChoice.AskEveryTime, DEFAULT_APP_LABEL to OpenWithChoice.DefaultApp) +
        apps.map { app ->
            val label = if (app.label in repeated || app.label == ASK_EVERY_TIME_LABEL || app.label == DEFAULT_APP_LABEL) {
                "${app.label} (${app.packageName})"
            } else app.label
            label to OpenWithChoice.App(app.packageName)
        }
}

/**
 * File Types: which kinds of file file search looks through, as chips that each switch a type on or off. At least
 * one type stays on, since search with none would find nothing.
 */
@Composable
internal fun FileTypesCard() {
    var excluded by rememberSetting(SettingsManager.FILES_EXCLUDED_TYPES)
    SettingsCard(lit = false) {
        Text(
            text = "Only the types chosen here are searched. Start a search with a type, like \"pdf invoice\" or \"photos beach\", to search just that type.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        FileTypeChips(
            selected = FileTypeGroup.entries.filter { it.id !in excluded }.toSet(),
            onToggle = { group ->
                val searched = FileTypeGroup.entries.count { it.id !in excluded }
                excluded = when {
                    group.id in excluded -> excluded - group.id
                    searched > 1 -> excluded + group.id
                    else -> excluded
                }
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Multi-select options as pills, in [ShellSelector]'s colours: a chosen type sits on the primary container with a
 * check, the others are outlined in the muted on-surface-variant. They wrap onto more lines on narrow screens and
 * at large text sizes.
 */
@Composable
internal fun FileTypeChips(
    selected: Set<FileTypeGroup>,
    onToggle: (FileTypeGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FileTypeGroup.entries.forEach { group ->
            val isOn = group in selected
            val shape = ShellSelectorDefaults.ItemShape
            val content = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .then(
                        if (isOn) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                        else Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
                    )
                    .semantics {
                        toggleableState = ToggleableState(isOn)
                        stateDescription = if (isOn) "Searched" else "Not searched"
                    }
                    .bouncyClickable(shape = shape, role = Role.Checkbox) { onToggle(group) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isOn) Icons.Outlined.Check else fileTypeIcon(group),
                    contentDescription = null, // the label says which type; the state says whether it is on
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(group.label, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

/**
 * Open Files With: for each file type, whether a file result opens in the system's "Open with" sheet, the phone's
 * default app, or an app chosen here from those that open the type.
 */
@Composable
internal fun OpenFilesWithCard() {
    val context = LocalContext.current
    // Null until the installed apps have been read (off the main thread).
    var appsByGroup by remember { mutableStateOf<Map<FileTypeGroup, List<OpenerOption>>?>(null) }
    LaunchedEffect(Unit) {
        appsByGroup = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            FileTypeGroup.entries.associateWith { group ->
                FileOpeners.appsFor(pm, group, context.packageName).map { app ->
                    val icon = app.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    OpenerOption(app.packageName, app.label, icon)
                }
            }
        }
    }
    SettingsCard {
        FileTypeGroup.entries.forEachIndexed { index, group ->
            OpenWithRow(group, appsByGroup?.get(group), showDivider = index < FileTypeGroup.entries.lastIndex)
        }
    }
}

@Composable
private fun OpenWithRow(group: FileTypeGroup, apps: List<OpenerOption>?, showDivider: Boolean) {
    var stored by rememberSetting(SettingsManager.FILES_OPEN_WITH.getValue(group))
    val choice = OpenWithChoice.parse(stored)
    val entries = remember(apps) { openWithEntries(apps.orEmpty()) }
    val iconFor = remember(apps) { apps.orEmpty().associate { it.packageName to it.icon } }
    val selectedLabel = entries.firstOrNull { it.second == choice }?.first ?: when {
        apps == null -> DEFAULT_APP_LABEL.takeIf { choice == OpenWithChoice.DefaultApp } ?: "Chosen App"
        // The chosen app is gone; files of this type open in the "Open with" sheet until another is chosen.
        else -> ASK_EVERY_TIME_LABEL
    }
    SettingsDropdownRow(
        title = group.label,
        subtitle = selectedLabel,
        icon = fileTypeIcon(group),
        options = entries.map { it.first },
        selectedOption = selectedLabel,
        onOptionSelected = { label -> entries.firstOrNull { it.first == label }?.let { stored = it.second.stored } },
        showDivider = showDivider,
        optionLeadingIcon = { label ->
            val option = entries.firstOrNull { it.first == label }?.second
            val bitmap = (option as? OpenWithChoice.App)?.let { iconFor[it.packageName] }
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (option == OpenWithChoice.AskEveryTime) Icons.Outlined.Apps else Icons.Outlined.TaskAlt,
                        contentDescription = null, // the option's name is beside it
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    )
}
