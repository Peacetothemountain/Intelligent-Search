package com.pixel.intelligentsearch.feature.settings

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            Toast.makeText(context, "Directory selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
            prefs.edit().putString("search.files.uri", uri.toString()).apply()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {}
            }
            Toast.makeText(context, "${uris.size} media items selected via Photo Picker", Toast.LENGTH_SHORT).show()
            prefs.edit().putInt("search.files.photo_count", uris.size).apply()
        }
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
                val currentFileUri = prefs.getString("search.files.uri", null)
                val fileUriSubtitle = if (currentFileUri.isNullOrBlank()) "None Selected (Uses System Directory Picker)." else currentFileUri
                SettingsRow(
                    title = "Select Indexing Directory",
                    subtitle = fileUriSubtitle,
                    icon = Icons.Outlined.FolderOpen,
                    onClick = { launcher.launch(null) },
                    showDivider = true
                )
                val photoCount = prefs.getInt("search.files.photo_count", 0)
                val photoSubtitle = if (photoCount > 0) "$photoCount media items selected" else "Select photos & videos via Android Photo Picker"
                SettingsRow(
                    title = "System Photo Picker",
                    subtitle = photoSubtitle,
                    icon = Icons.Outlined.Image,
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    showDivider = true
                )
                var hiddenFiles by rememberBooleanPreference(prefs, "search.files.hidden.files", false)
                SettingsRowToggle(
                    title = "Show Hidden Files",
                    subtitle = "Include Files Hidden from Android Index.",
                    icon = Icons.Outlined.Visibility,
                    isChecked = hiddenFiles,
                    onCheckedChange = { hiddenFiles = it },
                    showDivider = true
                )
                var thumbnails by rememberBooleanPreference(prefs, "search.files.thumbnails", true)
                SettingsRowToggle(
                    title = "Show Thumbnails",
                    subtitle = "Show Images and Video Thumbnails.",
                    icon = Icons.Outlined.Image,
                    isChecked = thumbnails,
                    onCheckedChange = { thumbnails = it },
                    showDivider = true
                )
                
                var fileResultsCount by rememberIntPreference(prefs, "file_results_count", 5)
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

                var fileWeight by rememberIntPreference(prefs, "search_weight_files", 50)
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
