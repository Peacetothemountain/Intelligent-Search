package com.pixel.intelligentsearch.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.data.FileOpeners
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.OpenWithChoice
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.core.ui.fileTypeIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app offered under Open Files With, its icon ready to draw. */
internal class OpenerOption(val packageName: String, val label: String, val icon: ImageBitmap?)

internal const val ASK_EVERY_TIME_LABEL = "Ask Every Time"
internal const val DEFAULT_APP_LABEL = "Default App"

/**
 * The choices for one file type: Ask Every Time, Default App, then each app that opens the type. Two apps with the
 * same name are told apart by their package, so every entry is unique.
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
 * What a type's stored [choice] reads as, given the [apps] that open it (null while they are still being read). A
 * chosen app that is gone reads as Ask Every Time, which is what opening such a file does until another is chosen.
 */
internal fun openWithLabel(choice: OpenWithChoice, apps: List<OpenerOption>?): String =
    openWithEntries(apps.orEmpty()).firstOrNull { it.second == choice }?.first ?: when {
        apps == null -> "Chosen App"
        else -> ASK_EVERY_TIME_LABEL
    }

/**
 * File Search's Open Files With row: one row for every file type, opening [OpenFilesWithDialog], where each type
 * shows the app it opens in and can be changed.
 */
@Composable
internal fun OpenFilesWithRow(showDivider: Boolean) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(
        title = "Open Files With",
        subtitle = "Choose the App Each Type of File Opens In.",
        icon = Icons.AutoMirrored.Outlined.OpenInNew,
        onClick = { open = true },
        showDivider = showDivider
    )
    if (open) OpenFilesWithDialog(onDismiss = { open = false })
}

/**
 * The file types with the app each opens in (the system's "Open with" sheet, the phone's default app, or an app
 * chosen here). Tapping a type lists its choices in the same dialog; choosing one saves it and goes back to the types.
 * The installed apps are read off the main thread, once, when the dialog opens.
 */
@Composable
internal fun OpenFilesWithDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Null until the installed apps have been read.
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
    var editing by remember { mutableStateOf<FileTypeGroup?>(null) }
    val group = editing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = group?.let { "Open ${it.label} With" } ?: "Open Files With",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (group == null) {
                    FileTypeGroup.entries.forEach { type ->
                        val stored by rememberSetting(SettingsManager.FILES_OPEN_WITH.getValue(type))
                        OpenWithDialogRow(
                            icon = { DialogRowIcon(fileTypeIcon(type)) },
                            title = type.label,
                            subtitle = openWithLabel(OpenWithChoice.parse(stored), appsByGroup?.get(type)),
                            role = Role.Button,
                            onClick = { editing = type }
                        )
                    }
                } else {
                    OpenWithChoices(group, appsByGroup?.get(group), onChosen = { editing = null })
                }
            }
        },
        confirmButton = {
            if (group == null) {
                TextButton(onClick = onDismiss) { Text("Done", style = MaterialTheme.typography.labelLarge) }
            } else {
                TextButton(onClick = { editing = null }) { Text("Back", style = MaterialTheme.typography.labelLarge) }
            }
        }
    )
}

/** One type's choices, the chosen one checked; picking one saves it. */
@Composable
private fun OpenWithChoices(group: FileTypeGroup, apps: List<OpenerOption>?, onChosen: () -> Unit) {
    var stored by rememberSetting(SettingsManager.FILES_OPEN_WITH.getValue(group))
    val choice = OpenWithChoice.parse(stored)
    val entries = remember(apps) { openWithEntries(apps.orEmpty()) }
    val iconFor = remember(apps) { apps.orEmpty().associate { it.packageName to it.icon } }
    // A chosen app that is gone opens the "Open with" sheet, so that is what shows as chosen.
    val current = entries.firstOrNull { it.second == choice }?.second ?: OpenWithChoice.AskEveryTime.takeIf { apps != null }
    entries.forEach { (label, option) ->
        val isChosen = option == current
        OpenWithDialogRow(
            icon = {
                val bitmap = (option as? OpenWithChoice.App)?.let { iconFor[it.packageName] }
                if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(24.dp))
                } else {
                    DialogRowIcon(if (option == OpenWithChoice.AskEveryTime) Icons.Outlined.Apps else Icons.Outlined.TaskAlt)
                }
            },
            title = label,
            subtitle = null,
            role = Role.RadioButton,
            selected = isChosen,
            onClick = {
                stored = option.stored
                onChosen()
            }
        )
    }
}

@Composable
private fun DialogRowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
}

/** A row in the Open Files With dialog: an icon, a title and the current choice under it, or a check when [selected]. */
@Composable
private fun OpenWithDialogRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    role: Role,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ExpressiveCornerTokens.ShapeLarge)
            .semantics(mergeDescendants = true) {
                this.role = role
                if (role == Role.RadioButton) this.selected = selected
            }
            .expressiveRowClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}
