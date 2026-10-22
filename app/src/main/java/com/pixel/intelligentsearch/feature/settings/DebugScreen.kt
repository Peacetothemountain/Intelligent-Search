package com.pixel.intelligentsearch.feature.settings
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import androidx.core.content.edit


import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.*

import androidx.compose.ui.semantics.*

import com.pixel.intelligentsearch.core.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(prefs: SharedPreferences, onBack: () -> Unit, onDisableDebug: () -> Unit) {
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader("Secret Debug Menu")
            // The menu's rows in one matte shell, like every other settings group.
            SettingsCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                val settingsViewModel = LocalSettingsViewModel.current
                var forceTutorial by rememberSetting(SettingsManager.FORCE_TUTORIAL)
                SettingsRowToggle(
                    title = "Force Tutorial Mode",
                    subtitle = "When checked, the tutorial will play every time the app opens",
                    icon = Icons.Outlined.Warning,
                    isChecked = forceTutorial,
                    onCheckedChange = { forceTutorial = it }
                )
            
                SettingsRow(
                    title = "Reset Tutorial Progress",
                    subtitle = "Mark tutorial as incomplete and restart step guide",
                    icon = Icons.Outlined.Refresh,
                    onClick = {
                        prefs.edit()
                            .put(SettingsManager.TUTORIAL_STEP, 0)
                            .put(SettingsManager.TUTORIAL_COMPLETED, false)
                            .apply()
                        // Settings screens read tutorial state from DataStore, not the prefs mirror.
                        settingsViewModel?.updateSetting(SettingsManager.TUTORIAL_STEP, 0)
                        settingsViewModel?.updateSetting(SettingsManager.TUTORIAL_COMPLETED, false)
                    },
                    showDivider = true
                )
                val context = androidx.compose.ui.platform.LocalContext.current

                SettingsRow(
                    title = "Clear Search History Cache",
                    subtitle = "Reset and clear all saved recent queries",
                    icon = Icons.Outlined.Delete,
                    onClick = {
                        settingsViewModel?.clearSearchHistory()
                        android.widget.Toast.makeText(context, "Search history cleared", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    showDivider = true
                )

                DoodlePreviewRow()
            
            
            
                var simulateLatency by rememberSetting(SettingsManager.DEBUG_SIMULATE_LATENCY)
                SettingsRowToggle(
                    title = "Simulate Web Latency",
                    subtitle = "Adds a 2-Second Artificial Delay to Search Suggestions.",
                    icon = Icons.Outlined.HourglassEmpty,
                    isChecked = simulateLatency,
                    onCheckedChange = { simulateLatency = it },
                    showDivider = true
                )

                var mockLargeDataset by rememberSetting(SettingsManager.DEBUG_MOCK_LARGE_DATASET)
                SettingsRowToggle(
                    title = "Mock Large Dataset",
                    subtitle = "Injects 50 Mock Contacts and Files into Search Lists.",
                    icon = Icons.Outlined.Layers,
                    isChecked = mockLargeDataset,
                    onCheckedChange = { mockLargeDataset = it },
                    showDivider = true
                )

                var verboseLogging by rememberSetting(SettingsManager.DEBUG_VERBOSE_LOGGING)
                SettingsRowToggle(
                    title = "Enable Verbose Logging",
                    subtitle = "Print Query Logs and Load Frame Times in Logcat.",
                    icon = Icons.Outlined.Code,
                    isChecked = verboseLogging,
                    onCheckedChange = { verboseLogging = it },
                    showDivider = true
                )
            

                var showPerfStats by rememberSetting(SettingsManager.DEBUG_SHOW_PERF_STATS)
                SettingsRowToggle(
                    title = "Show Performance HUD",
                    subtitle = "Render Search Latency and Results Count at Top of Overlay.",
                    icon = Icons.Outlined.Speed,
                    isChecked = showPerfStats,
                    onCheckedChange = { showPerfStats = it },
                    showDivider = true
                )
            
            
                var mockTrending by rememberSetting(SettingsManager.DEBUG_MOCK_TRENDING)
                SettingsRowToggle(
                    title = "Mock Trending Queries",
                    subtitle = "Force Mock Trending Query Topics When Search Bar Is Empty.",
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    isChecked = mockTrending,
                    onCheckedChange = { mockTrending = it },
                    showDivider = true
                )

                var forceSearchError by rememberSetting(SettingsManager.DEBUG_FORCE_SEARCH_ERROR)
                SettingsRowToggle(
                    title = "Force Search API Error",
                    subtitle = "Simulate Suggestion Fetch Failure and Display Error Banner.",
                    icon = Icons.Outlined.BugReport,
                    isChecked = forceSearchError,
                    onCheckedChange = { forceSearchError = it },
                    showDivider = true
                )
            
                SettingsRow(
                    title = "Disable Debug Mode",
                    subtitle = "Turn Off Developer Settings and Exit.",
                    icon = Icons.Outlined.Close,
                    onClick = onDisableDebug,
                    showDivider = false
                )
            }
        }
    }
}

/**
 * Locks Developer Settings again. A widget doodle preview ends first: its switch is on the page being hidden, so
 * left on it could never be turned off. The widgets are redrawn ([redraw]) only when a preview was showing.
 */
internal fun disableDebugMode(
    context: android.content.Context,
    prefs: SharedPreferences,
    redraw: () -> Unit = { com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.updateAllWidgets(context) }
) {
    val doodles = com.pixel.intelligentsearch.feature.widget.WidgetDoodles
    if (doodles.previewing(prefs) != null) doodles.preview(context, null, prefs, redraw)
    prefs.edit { put(SettingsManager.DEBUG_UNLOCKED, false) }
}

/**
 * Shows any doodle on the widgets, whatever the date: the switch turns the preview on and off, and tapping the row
 * opens the doodle gallery to browse, play and test all of them (see [DoodleGallery]). Off, the widgets show today's
 * doodle (if any) again.
 */
@Composable
private fun DoodlePreviewRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val doodles = com.pixel.intelligentsearch.feature.widget.WidgetDoodles
    val prefs = remember(context) { com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider.getSafeSharedPreferences(context) }
    var shown by remember { mutableStateOf(doodles.previewing(prefs)) }
    var lastPicked by remember { mutableStateOf(shown ?: com.pixel.intelligentsearch.core.occasion.Occasion.entries.first()) }
    var browsing by remember { mutableStateOf(false) }

    fun show(doodle: com.pixel.intelligentsearch.core.occasion.Occasion?) {
        doodles.preview(context, doodle, prefs)
        shown = doodle
        if (doodle != null) lastPicked = doodle
    }

    SettingsRowToggle(
        title = "Preview Widget Doodles",
        subtitle = shown?.let { "Showing ${resources.getString(it.title)} on Your Widgets. Tap to Open the Gallery." }
            ?: "Off. Tap to Browse and Test All ${com.pixel.intelligentsearch.core.occasion.Occasion.entries.size} Doodles.",
        icon = DoodleBrushIcon,
        isChecked = shown != null,
        onCheckedChange = { on -> show(if (on) lastPicked else null) },
        onClick = { browsing = true },
        showDivider = true
    )
    if (browsing) {
        DoodleGalleryDialog(previewing = shown, onPreview = ::show, onDismiss = { browsing = false })
    }
}
