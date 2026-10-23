package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.di.appEntryPoint
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.search.QuickKeys
import com.pixel.intelligentsearch.core.search.ResultKind
import com.pixel.intelligentsearch.core.search.ResultShortcut
import com.pixel.intelligentsearch.core.search.ResultShortcuts

/**
 * The summary under a result on Nicknames & Triggers: its nicknames and its trigger word, e.g.
 * "Nicknames: Dad, Father. Trigger Word: sp." Ends with a period like every settings subtitle.
 */
internal fun shortcutSummary(entry: ResultShortcut): String {
    val parts = buildList {
        if (entry.nicknames.isNotEmpty()) add("${if (entry.nicknames.size == 1) "Nickname" else "Nicknames"}: ${entry.nicknames.joinToString(", ")}")
        if (entry.trigger.isNotBlank()) add("Trigger Word: ${entry.trigger}")
    }
    return parts.joinToString(". ") + "."
}

/** The outlined symbol for a result of [kind] on the page. */
private fun kindIcon(kind: ResultKind?): ImageVector = when (kind) {
    ResultKind.APP -> Icons.Outlined.Apps
    ResultKind.CONTACT -> Icons.Outlined.Person
    ResultKind.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
    ResultKind.SETTING, null -> Icons.Outlined.Settings
}

/**
 * Nicknames & Triggers, reached from Search Behavior: every result the user gave a nickname or a trigger word, each
 * opening an editor to change or delete them. They are added from a result's long-press menu in search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknamesTriggersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { context.appEntryPoint.resultShortcutStore() }
    val shortcuts by store.flow.collectAsState(store.current)
    val bangSymbol by rememberSetting(SettingsManager.WEB_SHORTCUT_TRIGGER_SYMBOL)
    var editing by remember { mutableStateOf<String?>(null) }
    val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Nicknames & Triggers",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            CardsAboveMorphAnimation(
                morphAnimationEnabled = morphAnimationEnabled,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsSectionHeader("Your Names for Results")
                    val named = shortcuts.named
                    // With nothing named yet the card only tells how, so it isn't lit like a card of settings.
                    SettingsCard(lit = named.isNotEmpty()) {
                        if (named.isEmpty()) {
                            HowToRow()
                        } else {
                            named.forEachIndexed { index, entry ->
                                SettingsRow(
                                    title = entry.title.ifBlank { "Result" },
                                    subtitle = shortcutSummary(entry),
                                    icon = kindIcon(entry.kind),
                                    onClick = { editing = entry.key },
                                    showDivider = index < named.lastIndex
                                )
                            }
                        }
                    }
                    if (shortcuts.named.isNotEmpty()) {
                        SettingsSectionHeader("Adding More")
                        SettingsCard(lit = false) { HowToRow() }
                    }
                }
            }
        }
    }

    val entry = shortcuts[editing]
    if (entry != null) {
        EditShortcutDialog(
            entry = entry,
            shortcuts = shortcuts,
            bangSymbol = bangSymbol,
            onSave = { nicknames, trigger ->
                // The dialog only lets a valid trigger word through; an entry left with nothing set (and no pin) goes.
                val cleaned = nicknames.mapNotNull(ResultShortcuts::cleanNickname)
                    .distinctBy { it.lowercase() }.take(ResultShortcuts.MAX_NICKNAMES)
                store.update { it.update(entry.key, entry.title) { e -> e.copy(nicknames = cleaned, trigger = QuickKeys.normalize(trigger)) } }
                editing = null
            },
            onDelete = {
                // The pin stays: only the names go.
                store.update { it.update(entry.key, entry.title) { e -> e.copy(nicknames = emptyList(), trigger = "") } }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** How nicknames and trigger words are added: from a result's long-press menu. */
@Composable
private fun HowToRow() {
    SettingsInfoRow(
        title = "Long-Press a Result",
        subtitle = NICKNAMES_HOW_TO,
        icon = Icons.Outlined.TouchApp,
        showDivider = false
    )
}

/** How nicknames, trigger words and pins are added, matching what each result's long-press menu offers. */
internal const val NICKNAMES_HOW_TO =
    "In Search, Long-Press an App, Contact, File or Settings Page to Pin It or Set a Trigger Word. " +
        "Apps and Contacts Can Also Get a Nickname Such as Dad or Bank."

/**
 * Edits one result's nicknames and trigger word: remove a nickname with its button, add one with the field, change
 * the trigger word (checked as it is typed). Delete clears both.
 */
@Composable
internal fun EditShortcutDialog(
    entry: ResultShortcut,
    shortcuts: ResultShortcuts,
    bangSymbol: String,
    onSave: (nicknames: List<String>, trigger: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var nicknames by remember(entry.key) { mutableStateOf(entry.nicknames) }
    var newNickname by remember(entry.key) { mutableStateOf("") }
    var trigger by remember(entry.key) { mutableStateOf(entry.trigger) }
    val triggerCheck = if (trigger.isBlank()) QuickKeys.Check.Ok else QuickKeys.check(trigger, entry.key, shortcuts, bangSymbol)
    val triggerError = QuickKeys.message(triggerCheck, bangSymbol)
    val addable = ResultShortcuts.canAddNickname(newNickname, nicknames)
    val nicknameError = ResultShortcuts.nicknameError(newNickname, nicknames, entry.title.ifBlank { "This Result" })
    val add = { if (addable != null) { nicknames = nicknames + addable; newNickname = "" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title.ifBlank { "Result" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Nicknames", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                nicknames.forEach { nickname ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(nickname, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { nicknames = nicknames - nickname }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove Nickname $nickname")
                        }
                    }
                }
                OutlinedTextField(
                    value = newNickname,
                    onValueChange = { newNickname = it.take(ResultShortcuts.MAX_NICKNAME_LENGTH) },
                    label = { Text("Add Nickname") },
                    singleLine = true,
                    isError = nicknameError != null,
                    supportingText = nicknameError?.let { { Text(it) } },
                    trailingIcon = {
                        IconButton(onClick = add, enabled = addable != null) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Nickname")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Trigger Word", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    QuickKeys.howTo(entry.title.ifBlank { "the result" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it.trim().take(QuickKeys.MAX_LENGTH + 1) },
                    label = { Text("Trigger Word") },
                    singleLine = true,
                    isError = triggerError != null,
                    leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                    supportingText = { Text(triggerError ?: QuickKeys.HINT) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nicknames, trigger) }, enabled = triggerError == null) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Remove", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            }
        }
    )
}
