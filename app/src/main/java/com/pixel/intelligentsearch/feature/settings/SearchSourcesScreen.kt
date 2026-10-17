package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourcesScreen(prefs: SharedPreferences, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    Box(modifier = Modifier.fillMaxSize()) {
        val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Search Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCard {
                        var searchApps by rememberSetting(SettingsManager.SEARCH_APPS)
                        SettingsRowToggle(
                            title = "Apps",
                            subtitle = "Search Installed Applications.",
                            icon = Icons.Outlined.Apps,
                            isChecked = searchApps,
                            onCheckedChange = { searchApps = it },
                            onClick = { 
                                if (searchApps) {
                                    onNavigate(Route.AppSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Apps first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        var searchWeb by rememberSetting(SettingsManager.SEARCH_WEB)
                        SettingsRowToggle(
                            title = "Web",
                            subtitle = "View Search Suggestions from Websites.",
                            icon = Icons.Outlined.Language,
                            isChecked = searchWeb,
                            onCheckedChange = { searchWeb = it },
                            onClick = { 
                                if (searchWeb) {
                                    onNavigate(Route.WebSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Web first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        var showContactsRationaleDialog by remember { mutableStateOf(false) }
                        var showContactsSettingsDialog by remember { mutableStateOf(false) }
                        var searchContacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
                        var searchFiles by rememberSetting(SettingsManager.SEARCH_FILES)

                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                // Only follow a revoked permission downward: the user may have turned Contacts off
                                // with the permission still granted, and that choice must survive leaving the screen.
                                if (event == Lifecycle.Event.ON_RESUME && searchContacts &&
                                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    searchContacts = false
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                            val granted = results.values.isNotEmpty() && results.values.all { it }
                            if (granted) {
                                searchContacts = true
                                viewModel?.reindexContacts()
                            } else {
                                searchContacts = false
                                showContactsSettingsDialog = true
                            }
                        }

                        if (showContactsRationaleDialog) {
                            AlertDialog(
                                onDismissRequest = { showContactsRationaleDialog = false },
                                title = { Text("Allow Contacts Access?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                                text = {
                                    Text(
                                        "Intelligent Search needs permission to access your contacts so you can search, call, message, and view contact details directly from the search bar. Your contacts remain securely on your device.",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showContactsRationaleDialog = false
                                            contactsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                        }
                                    ) {
                                        Text("Allow", style = MaterialTheme.typography.labelLarge)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showContactsRationaleDialog = false
                                            searchContacts = false
                                        }
                                    ) {
                                        Text("Not Now", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            )
                        }

                        if (showContactsSettingsDialog) {
                            AlertDialog(
                                onDismissRequest = { showContactsSettingsDialog = false },
                                title = { Text("Contacts Permission Required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                                text = {
                                    Text(
                                        "Contacts permission is required to search your contacts. Please enable Contacts permission in Android App Settings.",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showContactsSettingsDialog = false
                                            try {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Throwable) {}
                                        }
                                    ) {
                                        Text("Open Settings", style = MaterialTheme.typography.labelLarge)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showContactsSettingsDialog = false }) {
                                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            )
                        }

                        SettingsRowToggle(
                            title = "Contacts",
                            subtitle = "Search Contacts.",
                            icon = Icons.Outlined.Contacts,
                            isChecked = searchContacts,
                            onCheckedChange = { isChecked -> 
                                if (isChecked) {
                                    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        searchContacts = true
                                        viewModel?.reindexContacts()
                                    } else {
                                        showContactsRationaleDialog = true
                                    }
                                } else {
                                    searchContacts = false
                                }
                            },
                            onClick = { 
                                if (searchContacts) {
                                    onNavigate(Route.ContactSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Contacts first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        
                        SettingsRowToggle(
                            title = "Files",
                            subtitle = "Search Local Files & Media via System Pickers.",
                            icon = Icons.Outlined.Folder,
                            isChecked = searchFiles,
                            onCheckedChange = { isChecked -> 
                                searchFiles = isChecked
                            },
                            onClick = { 
                                if (searchFiles) {
                                    onNavigate(Route.FileSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Files first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        var searchCalc by rememberSetting(SettingsManager.SEARCH_CALCULATOR)
                        SettingsRowToggle(
                            title = "Calculator",
                            subtitle = "Calculate Mathematical Equations Inside Search Bar.",
                            icon = Icons.Outlined.Calculate,
                            isChecked = searchCalc,
                            onCheckedChange = { searchCalc = it },
                            showDivider = true
                        )
                        var searchCalendar by rememberSetting(SettingsManager.SEARCH_CALENDAR)
                        val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                            if (results.values.all { it }) {
                                searchCalendar = true
                            } else {
                                Toast.makeText(context, "Permission denied. Please enable in Settings.", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Throwable) {}
                            }
                        }
                        SettingsRowToggle(
                            title = "Calendar",
                            subtitle = "Show Calendar Events.",
                            icon = Icons.Outlined.Event,
                            isChecked = searchCalendar,
                            onCheckedChange = { isChecked -> 
                                if (isChecked) {
                                    if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        searchCalendar = true
                                    } else {
                                        calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                                    }
                                } else {
                                    searchCalendar = false
                                }
                            },
                            showDivider = false
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) 
                        .clipToBounds() 
                ) {
                    if (morphAnimationEnabled) {
                        MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
