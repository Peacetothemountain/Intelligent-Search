package com.pixel.intelligentsearch.feature.settings

import androidx.core.content.edit
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.theme.pageTrack
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    val filteredApps = if (searchQuery.isBlank()) {
        installedApps
    } else {
        installedApps.filter { it.app.name.contains(searchQuery, ignoreCase = true) || it.app.packageName.contains(searchQuery, ignoreCase = true) }
    }
    val shownPackages = filteredApps.map { it.app.packageName }
    val allShownHidden = shownPackages.isNotEmpty() && hiddenApps.containsAll(shownPackages)

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

    // Back closes search before it leaves the page (or asks about unsaved changes).
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    Scaffold(containerColor = Color.Transparent, topBar = {
        Column {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Nothing to search or save until the page is unlocked.
                    if (isAuthenticated) {
                        SearchToggleButton(open = isSearching, onToggle = {
                            if (isSearching) searchQuery = ""
                            isSearching = !isSearching
                        })
                        // As on Application Search: switches edit a draft, Reset puts back the apps last saved and Save
                        // applies the draft.
                        SavedStateActions(
                            onReset = { hiddenApps = savedHiddenApps },
                            onSave = ::save,
                            resetMessage = "Hidden apps reset to their saved state.",
                            saveMessage = "Hidden apps saved.",
                            saveDescription = "Save Hidden Apps"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            // The title on its own line, flush left under Back, with Enable All just after it: beside the bar's
            // buttons there's no room for both at a readable size. Search opens at the bottom, so it stays put.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)
            ) {
                Text(
                    "Manage Hidden Apps",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    // Under the bar it no longer takes the bar's content color.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall),
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Enable All hides every app in the list; checked again, it shows them all.
                if (isAuthenticated && shownPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    EnableAllToggle(
                        checked = allShownHidden,
                        onCheckedChange = { hide ->
                            hiddenApps = if (hide) hiddenApps + shownPackages else hiddenApps - shownPackages.toSet()
                        }
                    )
                }
            }
        }
    }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        com.pixel.intelligentsearch.core.ui.expressive.MatteShell(
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.pageTrack(0.5f),
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
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp).underBottomSearch(isSearching),
                    contentPadding = PaddingValues(bottom = if (isSearching) PILL_CLEARANCE else 0.dp)
                ) {
                    items(
                        count = filteredApps.size,
                        key = { index -> filteredApps[index].app.packageName }
                    ) { index ->
                        val row = filteredApps[index]
                        val app = row.app
                        val isHidden = hiddenApps.contains(app.packageName)

                        SettingsRowToggle(
                            title = app.name,
                            // Breakable after each dot, so a long package name wraps there instead of mid-word.
                            subtitle = app.packageName.breakableAtDots(),
                            icon = row.icon,
                            isChecked = isHidden,
                            onCheckedChange = { hide ->
                                hiddenApps = if (hide) hiddenApps + app.packageName else hiddenApps - app.packageName
                            },
                            showDivider = index < filteredApps.size - 1
                        )
                    }
                    if (installedApps.isEmpty()) {
                        item(key = "loading") { AppsLoading() }
                    } else if (filteredApps.isEmpty()) {
                        item(key = "no_match") { NoAppsMatch(searchQuery) }
                    }
                }
            }
        }
        }
        BottomSearchPill(
            open = isSearching && isAuthenticated,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search Hidden Apps",
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        }
    }
}

/**
 * The check and "Enable All" beside the title: one pill-shaped tap target, the check filling in and both turning the
 * accent color while every app is hidden.
 */
@Composable
private fun EnableAllToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp)
    ) {
        Icon(
            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Check,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Enable All", style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}
