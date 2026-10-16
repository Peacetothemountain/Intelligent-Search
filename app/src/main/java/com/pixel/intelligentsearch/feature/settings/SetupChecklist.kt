package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.navigation.Route
import com.pixel.intelligentsearch.core.system.SearchRoleManager

/** One thing a new user sets up: what it gives them, and whether it is done. */
enum class SetupStep { CONTACTS, CALENDAR, FILES, ASSISTANT, SEARCH_BAR }

/** Which setup steps apply on this phone and which are done; read fresh every time the page is shown. */
object SetupChecklist {
    private const val DISMISSED_KEY = "setup_checklist_dismissed"

    fun steps(context: Context): List<SetupStep> = SetupStep.entries.filter { step ->
        // The home-screen search bar setting only exists on Android 17 QPR2 and newer.
        step != SetupStep.SEARCH_BAR || SearchRoleManager.getAvailableSearchRole(context) != null
    }

    fun isDone(context: Context, prefs: SharedPreferences, step: SetupStep): Boolean = when (step) {
        SetupStep.CONTACTS -> granted(context, Manifest.permission.READ_CONTACTS) && prefs[SettingsManager.SEARCH_CONTACTS]
        SetupStep.CALENDAR -> granted(context, Manifest.permission.READ_CALENDAR) && prefs[SettingsManager.SEARCH_CALENDAR]
        SetupStep.FILES -> UserGrantedFiles.folderUris(prefs).isNotEmpty() || UserGrantedFiles.mediaUris(prefs).isNotEmpty()
        SetupStep.ASSISTANT -> runCatching {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        }.getOrDefault(false)
        SetupStep.SEARCH_BAR -> SearchRoleManager.isSearchRoleHeld(context)
    }

    fun doneCount(context: Context, prefs: SharedPreferences): Int = steps(context).count { isDone(context, prefs, it) }

    /** Whether Settings still offers "Finish Setting Up": until every step is done or the user says they're done. */
    fun shouldOffer(context: Context, prefs: SharedPreferences): Boolean =
        !prefs.getBoolean(DISMISSED_KEY, false) && doneCount(context, prefs) < steps(context).size

    fun dismiss(prefs: SharedPreferences) = prefs.edit { putBoolean(DISMISSED_KEY, true) }

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

private const val SEARCH_BAR_ADB_COMMANDS =
    "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch\n" +
        "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch"

/**
 * "Finish Setting Up": each kind of access Intelligent Search can use, why it helps, and one tap to grant it.
 * Everything here stays on the phone; the page says so where it matters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupChecklistScreen(prefs: SharedPreferences, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    // Bumped whenever the user may have changed something (a permission dialog, or back from system settings).
    var revision by remember { mutableIntStateOf(0) }
    var showSearchBarHelp by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.edit { put(SettingsManager.SEARCH_CONTACTS, true) }
            viewModel?.reindexContacts()
        } else {
            Toast.makeText(context, "Contacts access was declined. You can allow it in Android settings.", Toast.LENGTH_LONG).show()
            openAppSettings()
        }
        revision++
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.edit { put(SettingsManager.SEARCH_CALENDAR, true) }
        } else {
            Toast.makeText(context, "Calendar access was declined. You can allow it in Android settings.", Toast.LENGTH_LONG).show()
            openAppSettings()
        }
        revision++
    }

    val steps = remember(revision) { SetupChecklist.steps(context) }
    val done = remember(revision) { steps.associateWith { SetupChecklist.isDone(context, prefs, it) } }

    if (showSearchBarHelp) {
        AlertDialog(
            onDismissRequest = { showSearchBarHelp = false },
            title = { Text("Home Screen Search Bar", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Android only lists an app as a search engine once it's on the phone's search engine list. " +
                        "Adding Intelligent Search takes one step from a computer:\n\n" +
                        "1. Turn on USB debugging in Developer options.\n" +
                        "2. Connect your phone to a computer with ADB.\n" +
                        "3. Run the two commands you copy below.\n\n" +
                        "Then come back here and choose Intelligent Search.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ADB Commands", SEARCH_BAR_ADB_COMMANDS))
                    Toast.makeText(context, "Commands copied", Toast.LENGTH_SHORT).show()
                    showSearchBarHelp = false
                }) { Text("Copy Commands") }
            },
            dismissButton = { TextButton(onClick = { showSearchBarHelp = false }) { Text("Close") } }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Finish Setting Up", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "${done.values.count { it }} of ${steps.size} done. Each one is optional, and everything Intelligent Search reads stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            SettingsCard {
                steps.forEachIndexed { index, step ->
                    val isDone = done[step] == true
                    val (title, why, icon) = when (step) {
                        SetupStep.CONTACTS -> Triple("Contacts", "Find people as you type, then call or message them.", Icons.Outlined.Contacts)
                        SetupStep.CALENDAR -> Triple("Calendar", "See upcoming events and search them.", Icons.Outlined.Event)
                        SetupStep.FILES -> Triple("Files", "Choose the folders to search. Nothing else is read.", Icons.Outlined.Folder)
                        SetupStep.ASSISTANT -> Triple("Default Assistant", "Open Intelligent Search with your phone's assistant gesture.", Icons.Outlined.AutoAwesome)
                        SetupStep.SEARCH_BAR -> Triple("Home Screen Search Bar", "Power the Pixel search bar on your home screen.", Icons.Outlined.Search)
                    }
                    SettingsRow(
                        title = title,
                        subtitle = if (isDone) "Done" else why,
                        icon = if (isDone) Icons.Outlined.CheckCircle else icon,
                        onClick = {
                            when (step) {
                                SetupStep.CONTACTS ->
                                    if (isDone) onNavigate(Route.ContactSearch) else contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                                SetupStep.CALENDAR ->
                                    if (isDone) onNavigate(Route.SearchSources) else calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                                SetupStep.FILES -> onNavigate(Route.FileSearch)
                                SetupStep.ASSISTANT -> runCatching {
                                    context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.recoverCatching {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                SetupStep.SEARCH_BAR ->
                                    if (isDone || SearchRoleManager.isListedAsSearchEngine(context)) {
                                        SearchRoleManager.openDefaultSearchEngineSettings(context)
                                    } else {
                                        showSearchBarHelp = true
                                    }
                            }
                        },
                        showDivider = index < steps.lastIndex
                    )
                }
            }
            TextButton(
                onClick = {
                    SetupChecklist.dismiss(prefs)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("I'm Done for Now") }
        }
    }
}
