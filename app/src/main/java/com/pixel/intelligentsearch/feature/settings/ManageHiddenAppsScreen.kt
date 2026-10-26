package com.pixel.intelligentsearch.feature.settings

import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An installed app with its icon already drawn at row size. */
private class HiddenAppRow(val app: AppItem, val icon: ImageBitmap?)

/** [loadApps] lists the installed apps; tests pass their own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHiddenAppsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit,
    loadApps: suspend (Context) -> List<AppItem> = { SystemDataProvider.getAllApps(it) }
) {
    val context = LocalContext.current
    // Switches edit a draft: Save writes it, Reset puts back the apps last saved.
    var savedHiddenApps by remember { mutableStateOf(prefs[SettingsManager.HIDDEN_APPS]) }
    var hiddenApps by remember { mutableStateOf(savedHiddenApps) }
    val hasUnsavedChanges = hiddenApps != savedHiddenApps
    var showUnsavedChangesCard by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<HiddenAppRow>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val iconPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    val isAuthenticated = rememberProtectedPageUnlock(
        title = "Unlock Private Search",
        subtitle = "Verify Identity to View Hidden Items.",
        onBack = onBack
    )

    fun save() {
        prefs.edit { put(SettingsManager.HIDDEN_APPS, hiddenApps) }
        savedHiddenApps = hiddenApps
    }

    // Leaving with unsaved changes asks first, as Widget Customization does, unless the user said not to.
    val asksBeforeLeaving = isAuthenticated && hasUnsavedChanges && !prefs[SettingsManager.HIDDEN_APPS_UNSAVED_WARNING_DISMISSED]
    val handleBack = { if (asksBeforeLeaving) showUnsavedChangesCard = true else onBack() }
    BackHandler(enabled = asksBeforeLeaving) { showUnsavedChangesCard = true }

    if (showUnsavedChangesCard) {
        WarningCard(
            title = "Discard unsaved changes?",
            message = "You changed which apps are hidden. If you leave now, none of your changes will be applied.",
            acceptLabel = "Save",
            onAccept = {
                showUnsavedChangesCard = false
                save()
                onBack()
            },
            onDiscard = {
                showUnsavedChangesCard = false
                onBack()
            },
            onDontShowAgain = {
                prefs.edit { put(SettingsManager.HIDDEN_APPS_UNSAVED_WARNING_DISMISSED, true) }
                showUnsavedChangesCard = false
                onBack()
            },
            onDismissRequest = { showUnsavedChangesCard = false }
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Hiding works per package, and work/private-profile copies share it; duplicate LazyColumn keys crash.
            // Each icon is drawn once here at row size, not on the main thread while the list scrolls.
            val apps = loadApps(context).distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
                .map { app -> HiddenAppRow(app, runCatching { app.icon.toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()) }
            withContext(Dispatchers.Main) {
                installedApps = apps
            }
        }
    }

    // Opening search puts the cursor in the field, so typing can start straight away.
    LaunchedEffect(isSearching) {
        if (isSearching) searchFocus.requestFocus()
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Manage Hidden Apps", 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    AnimatedVisibility(
                        visible = isSearching,
                        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search apps...", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocus)
                                        .semantics { contentDescription = "Search apps" }
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Nothing to search until the page is unlocked.
                if (isAuthenticated) {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            isSearching = true
                        }
                    }) {
                        Icon(if (isSearching) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = if (isSearching) "Close search" else "Search")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = null,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (!isAuthenticated) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Biometric Authentication Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Use Fingerprint, Face Unlock or Your Screen Lock to View and Manage Hidden Applications.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = com.pixel.intelligentsearch.core.theme.GoogleSansFlex,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                ) {
                    val filteredApps = if (searchQuery.isBlank()) {
                        installedApps
                    } else {
                        installedApps.filter { it.app.name.contains(searchQuery, ignoreCase = true) || it.app.packageName.contains(searchQuery, ignoreCase = true) }
                    }

                    // Select All hides every app the list shows (all of them, or those matching the search); off, it
                    // shows them all again.
                    if (filteredApps.isNotEmpty()) {
                        item(key = SELECT_ALL_KEY) {
                            val shownPackages = filteredApps.map { it.app.packageName }
                            SettingsRowToggle(
                                title = "Select All",
                                subtitle = if (searchQuery.isBlank()) "Hide Every App in This List." else "Hide Every App Matching Your Search.",
                                icon = Icons.Outlined.SelectAll,
                                isChecked = hiddenApps.containsAll(shownPackages),
                                onCheckedChange = { hide ->
                                    hiddenApps = if (hide) hiddenApps + shownPackages else hiddenApps - shownPackages.toSet()
                                },
                                showDivider = true
                            )
                        }
                    }
                    items(
                        count = filteredApps.size,
                        key = { index -> filteredApps[index].app.packageName }
                    ) { index ->
                        val row = filteredApps[index]
                        val app = row.app
                        val isHidden = hiddenApps.contains(app.packageName)

                        SettingsRowToggle(
                            title = app.name,
                            subtitle = app.packageName,
                            icon = row.icon,
                            isChecked = isHidden,
                            onCheckedChange = { hide ->
                                hiddenApps = if (hide) hiddenApps + app.packageName else hiddenApps - app.packageName
                            },
                            showDivider = index < filteredApps.size - 1
                        )
                    }
                    if (installedApps.isEmpty()) {
                        item {
                            Text(
                                "Loading apps...",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (filteredApps.isEmpty()) {
                        item {
                            Text(
                                "No Apps Match",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // Reset and Save, under the list once it is unlocked; both wait until a switch has changed.
        if (isAuthenticated) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { hiddenApps = savedHiddenApps },
                    enabled = hasUnsavedChanges,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        save()
                        android.widget.Toast.makeText(context, "Hidden apps saved.", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = hasUnsavedChanges,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }
    }
}

/** The Select All row's list key; no package is named this. */
private const val SELECT_ALL_KEY = "select_all"
