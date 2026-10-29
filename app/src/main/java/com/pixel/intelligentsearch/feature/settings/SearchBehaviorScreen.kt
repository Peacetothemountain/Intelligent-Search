package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.AlignVerticalBottom
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.ViewCompact
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Search Behavior: the overlay's layout and suggestions, then the Keyboard and At a Glance cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBehaviorScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = settingsTopBarColors(),
                title = { SettingsPageTitle("Search Behavior") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSectionHeader("Layout and Suggestions")
            SettingsCard {
                var bottomSearch by rememberSetting(SettingsManager.BOTTOM_SEARCH)
                SettingsRowToggle(
                    title = "Bottom Search Bar",
                    subtitle = "Position Search Bar at Bottom of Search Overlay Page.",
                    icon = Icons.Outlined.VerticalAlignBottom,
                    isChecked = bottomSearch,
                    onCheckedChange = { bottomSearch = it },
                    showDivider = true
                )
                var bottomResult by rememberSetting(SettingsManager.BOTTOM_SEARCH_RESULT)
                SettingsRowToggle(
                    title = "Bottom Search Results",
                    subtitle = "Order List from Bottom Up Depending on Search Bar Placement.",
                    icon = Icons.Outlined.AlignVerticalBottom,
                    isChecked = bottomResult,
                    onCheckedChange = { bottomResult = it },
                    showDivider = true
                )
                var compactList by rememberSetting(SettingsManager.QUICK_SEARCH_HORIZONTAL)
                SettingsRowToggle(
                    title = "Quick App Panel",
                    subtitle = QUICK_APP_PANEL_SUBTITLE,
                    icon = Icons.Outlined.ViewCompact,
                    isChecked = compactList,
                    onCheckedChange = { compactList = it },
                    showDivider = true
                )
                var contextAwareApps by rememberSetting(SettingsManager.CONTEXT_AWARE_QUICK_APPS)
                SettingsRowToggle(
                    title = "Context Aware Quick Apps",
                    subtitle = "Dynamically Chosen Apps Based on User's App Opening Cycles.",
                    icon = Icons.Outlined.Insights,
                    isChecked = contextAwareApps,
                    onCheckedChange = { contextAwareApps = it },
                    showDivider = true
                )
                var smartClipboard by rememberSetting(SettingsManager.SMART_CLIPBOARD_SUGGESTIONS)
                var showClipboardWarning by remember { mutableStateOf(false) }

                if (showClipboardWarning) {
                    AlertDialog(
                        onDismissRequest = { showClipboardWarning = false },
                        title = { Text("Privacy Warning", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                        text = { Text("Smart Clipboard requires a foreground lifecycle observer to monitor your clipboard due to Android 10+ restrictions. Do you want to enable this feature?", style = MaterialTheme.typography.bodyLarge) },
                        confirmButton = {
                            TextButton(onClick = {
                                smartClipboard = true
                                showClipboardWarning = false
                            }) {
                                Text("Enable", style = MaterialTheme.typography.labelLarge)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClipboardWarning = false }) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }

                SettingsRowToggle(
                    title = "Smart Clipboard Suggestions",
                    subtitle = "Suggests Actions for What You Copy: Open a Link, Call a Number, Search Text or Search an Image with Lens.",
                    icon = Icons.Outlined.ContentPaste,
                    isChecked = smartClipboard,
                    onCheckedChange = {
                        if (it) {
                            showClipboardWarning = true
                        } else {
                            smartClipboard = false
                        }
                    },
                    showDivider = false
                )
            }
            KeyboardSettingsCard()
            AtAGlanceSettingsCard()
        }
    }
}
