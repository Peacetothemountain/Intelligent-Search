package com.pixel.intelligentsearch.feature.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    var hiddenApps by remember { mutableStateOf(prefs[SettingsManager.HIDDEN_APPS]) }
    var installedApps by remember { mutableStateOf<List<HiddenAppRow>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val iconPx = with(LocalDensity.current) { 24.dp.roundToPx() }

    val isAuthenticated = rememberProtectedPageUnlock(
        title = "Unlock Private Search",
        subtitle = "Verify identity to view hidden items",
        onBack = onBack
    )

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
                IconButton(onClick = onBack) {
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
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
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
                        "Use Fingerprint, Face Unlock or your screen lock to view and manage hidden applications.",
                        style = MaterialTheme.typography.bodyMedium,
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
                                val newSet = hiddenApps.toMutableSet()
                                if (hide) newSet.add(app.packageName) else newSet.remove(app.packageName)
                                hiddenApps = newSet
                                prefs.edit().put(SettingsManager.HIDDEN_APPS, newSet).apply()
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
    }
}
