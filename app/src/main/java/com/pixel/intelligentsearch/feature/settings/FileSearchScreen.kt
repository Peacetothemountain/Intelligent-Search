package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import android.widget.Toast
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    // File search only reads what the user picks with the system folder picker and photo picker, so it
    // needs no storage or media permission (see UserGrantedFiles for the Play policy background).
    var folderUris by remember { mutableStateOf(UserGrantedFiles.folderUris(prefs)) }
    var mediaCount by remember { mutableIntStateOf(UserGrantedFiles.mediaUris(prefs).size) }
    val folderNames = remember { mutableStateMapOf<String, String>() }
    val folderAccessLost = remember { mutableStateMapOf<String, Boolean>() }
    var folderPendingRemoval by remember { mutableStateOf<String?>(null) }
    var confirmClearMedia by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Subfolders of the chosen folders that search skips; removing a chosen folder drops its own.
    val excludedFolders by rememberSetting(SettingsManager.FILES_EXCLUDED_FOLDERS)
    val excludedNames = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(folderUris) {
        val details = withContext(Dispatchers.IO) {
            folderUris.associateWith { uri ->
                UserGrantedFiles.folderDisplayName(context, uri) to !UserGrantedFiles.hasReadGrant(context, uri)
            }
        }
        details.forEach { (uri, detail) ->
            folderNames[uri] = detail.first
            folderAccessLost[uri] = detail.second
        }
    }

    LaunchedEffect(excludedFolders) {
        val names = withContext(Dispatchers.IO) {
            excludedFolders.associateWith { UserGrantedFiles.excludedFolderName(context, prefs, it) }
        }
        excludedNames.putAll(names)
    }

    // The same system folder picker, opened in a chosen folder; what is picked must lie inside one of them. Its
    // access isn't kept: indexing reaches it through the chosen folder's own.
    val excludePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val message = when (UserGrantedFiles.addExcludedFolder(prefs, uri.toString())) {
                UserGrantedFiles.ExcludeResult.ADDED -> "Subfolder skipped in search."
                UserGrantedFiles.ExcludeResult.ALREADY_EXCLUDED -> "That folder is already skipped."
                UserGrantedFiles.ExcludeResult.NOT_INSIDE_A_CHOSEN_FOLDER -> "Pick a folder inside one of the folders you search."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (UserGrantedFiles.addFolder(context, prefs, uri)) {
                folderUris = UserGrantedFiles.folderUris(prefs)
                Toast.makeText(context, "Folder added to search.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Android didn't allow lasting access to that folder.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Taking or releasing a grant is a Binder call per photo (up to MAX_PICKED_MEDIA), so it runs off the main thread.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) { UserGrantedFiles.addMedia(context, prefs, uris) }
                mediaCount = UserGrantedFiles.mediaUris(prefs).size
                val message = if (added < uris.size && mediaCount >= UserGrantedFiles.MAX_PICKED_MEDIA) {
                    "Added $added. You can choose up to ${UserGrantedFiles.MAX_PICKED_MEDIA} photos and videos."
                } else {
                    "$added photos and videos added to search."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (confirmClearMedia) {
        AlertDialog(
            onDismissRequest = { confirmClearMedia = false },
            title = { Text("Stop searching chosen photos and videos?") },
            text = { Text("The $mediaCount photos and videos you chose will no longer appear in search results. Nothing in them is changed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearMedia = false
                    scope.launch {
                        withContext(Dispatchers.IO) { UserGrantedFiles.clearMedia(context, prefs) }
                        mediaCount = 0
                    }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMedia = false }) { Text("Cancel") }
            }
        )
    }

    folderPendingRemoval?.let { uri ->
        AlertDialog(
            onDismissRequest = { folderPendingRemoval = null },
            title = { Text("Stop searching this folder?") },
            text = { Text("\"${folderNames[uri] ?: "This folder"}\" will no longer appear in search results. Nothing in the folder is changed.") },
            confirmButton = {
                TextButton(onClick = {
                    UserGrantedFiles.removeFolder(context, prefs, uri)
                    folderUris = UserGrantedFiles.folderUris(prefs)
                    folderPendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                colors = settingsTopBarColors(),
                title = { SettingsPageTitle("File Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                FileSearchScopeNote()
                folderUris.sortedBy { folderNames[it] ?: it }.forEach { uri ->
                    SettingsRow(
                        title = folderNames[uri] ?: "Folder",
                        subtitle = if (folderAccessLost[uri] == true) {
                            "Access Was Removed. Tap to Remove, Then Add It Again."
                        } else {
                            "Searching This Folder. Tap to Remove."
                        },
                        icon = Icons.Outlined.Folder,
                        onClick = { folderPendingRemoval = uri },
                        showDivider = true
                    )
                }
                excludedFolders.sortedBy { excludedNames[it] ?: it }.forEach { uri ->
                    SettingsRow(
                        title = excludedNames[uri] ?: "Subfolder",
                        subtitle = "Skipped in Search. Tap to Search It Again.",
                        icon = Icons.Outlined.FolderOff,
                        onClick = { UserGrantedFiles.removeExcludedFolder(prefs, uri) },
                        showDivider = true
                    )
                }
                SettingsRow(
                    title = "Add Folder",
                    subtitle = "Pick a Folder Such as Documents. Android Doesn't Allow the Download Folder Itself, but Folders Inside It Work.",
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = { folderPicker.launch(null) },
                    showDivider = true
                )
                if (folderUris.isNotEmpty()) {
                    SettingsRow(
                        title = "Skip a Subfolder",
                        subtitle = "Leave a Folder Inside a Chosen One Out of Search. Hidden Folders and App Data Are Always Skipped.",
                        icon = Icons.Outlined.FolderOff,
                        onClick = {
                            val start = folderUris.minByOrNull { folderNames[it] ?: it }
                            excludePicker.launch(start?.let(UserGrantedFiles::folderRootDocumentUri))
                        },
                        showDivider = true
                    )
                }
                SettingsRow(
                    title = "Choose Photos & Videos",
                    subtitle = if (mediaCount > 0) "$mediaCount Chosen. Tap to Add More." else "Pick Photos and Videos with the Android Photo Picker.",
                    icon = Icons.Outlined.Image,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    showDivider = true
                )
                if (mediaCount > 0) {
                    SettingsRow(
                        title = "Clear Chosen Photos & Videos",
                        subtitle = "Stop Searching the $mediaCount Photos and Videos Chosen Above.",
                        icon = Icons.Outlined.DeleteSweep,
                        onClick = { confirmClearMedia = true },
                        showDivider = true
                    )
                }
                var hiddenFiles by rememberSetting(SettingsManager.FILES_HIDDEN_FILES)
                SettingsRowToggle(
                    title = "Show Hidden Files",
                    subtitle = "Include Files Hidden from Android Index.",
                    icon = Icons.Outlined.Visibility,
                    isChecked = hiddenFiles,
                    onCheckedChange = { hiddenFiles = it },
                    showDivider = true
                )
                var thumbnails by rememberSetting(SettingsManager.FILES_THUMBNAILS)
                SettingsRowToggle(
                    title = "Show Thumbnails",
                    subtitle = "Show Images and Video Thumbnails.",
                    icon = Icons.Outlined.Image,
                    isChecked = thumbnails,
                    onCheckedChange = { thumbnails = it },
                    showDivider = true
                )
                OpenFilesWithRow(showDivider = true)

                ResultsCountSetting(SettingsManager.FILE_RESULTS_COUNT, "File", defaultCount = 5)

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                PrioritySetting(SettingsManager.SEARCH_WEIGHT_FILES, "File")
            }
        }
    }
}

/** The note atop File Search: an info icon, then what it searches, in the subtext style every settings row uses. */
@Composable
private fun FileSearchScopeNote() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null, // decorative: the note beside it says it all
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).testTag(FILE_SEARCH_NOTE_ICON_TAG)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = FILE_SEARCH_SCOPE_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

internal const val FILE_SEARCH_NOTE_ICON_TAG = "file_search_note_icon"

internal const val FILE_SEARCH_SCOPE_NOTE =
    "Intelligent Search Only Searches the Folders and Photos You Choose Here. Nothing Else on Your Storage Is Read, " +
        "so No Storage Permission Is Needed. Start a Search with a File Type, like \"pdf invoice\", to Search Only That Type."
