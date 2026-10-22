package com.pixel.intelligentsearch.feature.settings

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.data.SavedLink
import com.pixel.intelligentsearch.core.data.SavedLinks
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.security.NetworkPolicy
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saved Links, reached from Search Sources: named web addresses that come up in search by name and open in the
 * browser (or the site's own app). Each shows its site icon once the user fetched it, a link symbol until then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { context.appEntryPoint.savedLinkStore() }
    val icons = remember(context) { context.appEntryPoint.savedLinkIcons() }
    // The page reads the setting the store writes, so a save shows at once.
    val json by rememberSetting(SettingsManager.SAVED_LINKS)
    val links = remember(json) { SavedLinks.fromJson(json) }
    val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED)
    // null: no dialog; a link: editing it; the blank link: adding one.
    var editing by remember { mutableStateOf<SavedLink?>(null) }
    var iconVersion by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Saved Links", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
                )
            }
        ) { padding ->
            CardsAboveMorphAnimation(morphAnimationEnabled = morphAnimationEnabled, modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsSectionHeader("Your Links")
                    SettingsCard {
                        links.forEach { link ->
                            val icon by produceState<ImageBitmap?>(null, link.url, iconVersion) {
                                value = withContext(Dispatchers.IO) { icons.load(link.url)?.asImageBitmap() }
                            }
                            SettingsRow(
                                title = link.name,
                                subtitle = "${link.host}.",
                                icon = icon ?: Icons.Outlined.Link,
                                onClick = { editing = link },
                                showDivider = true
                            )
                        }
                        SettingsRow(
                            title = "Add Link",
                            subtitle = if (links.size >= SavedLinks.MAX_LINKS) "Up to ${SavedLinks.MAX_LINKS} Links." else "Name a Web Address to Open It from Search.",
                            icon = Icons.Outlined.AddLink,
                            onClick = { if (links.size < SavedLinks.MAX_LINKS) editing = BLANK },
                            showDivider = false
                        )
                    }
                    SettingsSectionHeader("Using Them")
                    SettingsCard(lit = false) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Type a Link's Name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "It Shows in Search and Opens in Your Browser or the Site's App. Long-Press It There to Pin It, Add a Nickname or Set a Trigger Word.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        SavedLinkDialog(
            link = current.takeIf { it != BLANK },
            taken = links.map { it.url }.toSet() - current.url,
            onSave = { saved ->
                store.update { list ->
                    if (current == BLANK) list + saved else list.map { if (it == current) saved else it }
                }
                // A changed address is a different site: its old icon goes.
                if (current != BLANK && current.url != saved.url) icons.delete(current.url)
                editing = null
            },
            onDelete = {
                store.update { list -> list - current }
                icons.delete(current.url)
                editing = null
            },
            onIconFetched = { iconVersion++ },
            onDismiss = { editing = null }
        )
    }
}

/** The "add a link" stand-in while the dialog is for a new one. */
private val BLANK = SavedLink("", "")

/**
 * Adds or edits a Saved Link: its name and address (https only, checked as typed), and for a saved one Fetch Icon,
 * the only thing that downloads anything (the site's own icon, not while Tor is the search engine), and Delete.
 */
@Composable
internal fun SavedLinkDialog(
    link: SavedLink?,
    taken: Set<String>,
    onSave: (SavedLink) -> Unit,
    onDelete: () -> Unit,
    onIconFetched: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine by rememberSetting(SettingsManager.SEARCH_ENGINE)
    var name by remember(link) { mutableStateOf(link?.name.orEmpty()) }
    var address by remember(link) { mutableStateOf(link?.url.orEmpty()) }
    var fetching by remember { mutableStateOf(false) }
    val check = remember(address) { if (address.isBlank()) null else SavedLinks.check(address) }
    val validUrl = (check as? SavedLinks.Check.Valid)?.url
    val duplicate = validUrl != null && validUrl in taken
    val cleanName = SavedLinks.cleanName(name)
    val addressError = when {
        check is SavedLinks.Check.Invalid -> check.reason
        duplicate -> "This Address Is Already Saved."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (link == null) "Add Link" else "Edit Link", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SavedLinks.MAX_NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim().take(com.pixel.intelligentsearch.core.security.CustomSearchUrl.MAX_LENGTH) },
                    label = { Text("Web Address") },
                    singleLine = true,
                    isError = addressError != null,
                    supportingText = { Text(addressError ?: "Opens over HTTPS, for Example github.com/notifications.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                if (link != null) {
                    val tor = NetworkPolicy.isTorEngine(engine)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        enabled = !tor && !fetching,
                        onClick = {
                            fetching = true
                            scope.launch {
                                val icons = context.appEntryPoint.savedLinkIcons()
                                val ok = withContext(Dispatchers.IO) { icons.fetch(link.url) }
                                fetching = false
                                if (ok) onIconFetched() else Toast.makeText(context, "This Site Has No Icon to Fetch.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Fetch Icon")
                    }
                    Text(
                        text = if (tor) NetworkPolicy.blockSubtitle(NetworkPolicy.Block.TOR) else "Downloads the Icon Once from ${link.host}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanName != null && validUrl != null && !duplicate,
                onClick = { if (cleanName != null && validUrl != null) onSave(SavedLink(cleanName, validUrl)) }
            ) { Text("Save", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            Row {
                if (link != null) TextButton(onClick = onDelete) { Text("Delete", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            }
        }
    )
}
