package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import android.widget.Toast
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (UserGrantedFiles.addFolder(context, prefs, uri)) {
                folderUris = UserGrantedFiles.folderUris(prefs)
                Toast.makeText(context, "Folder added to search", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Android didn't allow lasting access to that folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val added = UserGrantedFiles.addMedia(context, prefs, uris)
            mediaCount = UserGrantedFiles.mediaUris(prefs).size
            val message = if (added < uris.size && mediaCount >= UserGrantedFiles.MAX_PICKED_MEDIA) {
                "Added $added. You can choose up to ${UserGrantedFiles.MAX_PICKED_MEDIA} photos and videos."
            } else {
                "$added photos and videos added to search"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
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
                title = { Text("File Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
                Text(
                    text = "Intelligent Search only searches the folders and photos you choose here. Nothing else on your storage is read, so no storage permission is needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                folderUris.sortedBy { folderNames[it] ?: it }.forEach { uri ->
                    SettingsRow(
                        title = folderNames[uri] ?: "Folder",
                        subtitle = if (folderAccessLost[uri] == true) {
                            "Access was removed. Tap to remove, then add it again."
                        } else {
                            "Searching this folder. Tap to remove."
                        },
                        icon = Icons.Outlined.Folder,
                        onClick = { folderPendingRemoval = uri },
                        showDivider = true
                    )
                }
                SettingsRow(
                    title = "Add Folder",
                    subtitle = "Pick a folder such as Documents. Android doesn't allow the Download folder itself, but folders inside it work.",
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = { folderPicker.launch(null) },
                    showDivider = true
                )
                SettingsRow(
                    title = "Choose Photos & Videos",
                    subtitle = if (mediaCount > 0) "$mediaCount chosen. Tap to add more." else "Pick photos and videos with the Android photo picker.",
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
                        subtitle = "Stop searching the $mediaCount photos and videos chosen above.",
                        icon = Icons.Outlined.DeleteSweep,
                        onClick = {
                            UserGrantedFiles.clearMedia(context, prefs)
                            mediaCount = 0
                        },
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
                
                var fileResultsCount by rememberSetting(SettingsManager.FILE_RESULTS_COUNT)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("File Results: $fileResultsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                fileResultsCount = 5
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Android17Slider(
                        label = "File results",
                        value = fileResultsCount.toFloat(),
                        onValueChange = { fileResultsCount = it.toInt() },
                        valueRange = 1f..20f,
                        steps = 18,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                var fileWeight by rememberSetting(SettingsManager.SEARCH_WEIGHT_FILES)
                val fileLevelIndex = remember(fileWeight) { PriorityWeightHelper.weightToLevelIndex(fileWeight) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File Priority Weight: ${PriorityWeightHelper.levelLabel(fileLevelIndex)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                performClickHaptic(context)
                                fileWeight = PriorityWeightHelper.DEFAULT_WEIGHT
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adjust Ranking Priority of File Search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Android17Slider(
                        label = "File priority weight",
                        value = fileLevelIndex.toFloat(),
                        onValueChange = { newValue ->
                            val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                            fileWeight = PriorityWeightHelper.levelIndexToWeight(idx)
                        },
                        valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
                        steps = PriorityWeightHelper.LEVELS.size - 2,
                        isSquiggly = false,
                        modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}
