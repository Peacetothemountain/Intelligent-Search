package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import android.content.SharedPreferences
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
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewCompact
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.SearchPillProfiles
import com.pixel.intelligentsearch.core.navigation.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchScreen(prefs: SharedPreferences, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("App & Shortcut Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
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
            SettingsSectionHeader("App Settings")
            SettingsCard {
                SettingsRow(
                    title = "Application Search",
                    subtitle = "Customize Quick Launch Apps in Search Overlay Screen.",
                    icon = Icons.Outlined.ViewCarousel,
                    onClick = { onNavigate(Route.SearchPills) },
                    showDivider = true
                )
                SettingsRow(
                    title = "Manage Hidden Apps",
                    subtitle = "Search Apps to Dynamically Hide from Search.",
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = { onNavigate(Route.ManageHiddenApps) },
                    showDivider = true
                )
                var fuzzySearch by rememberSetting(SettingsManager.APP_FUZZY_SEARCH)
                SettingsRowToggle(
                    title = "Fuzzy Search",
                    subtitle = "Allow Typos When Searching for Apps.",
                    icon = Icons.Outlined.Spellcheck,
                    isChecked = fuzzySearch,
                    onCheckedChange = { fuzzySearch = it },
                    showDivider = true
                )
                var appAnimation by rememberSetting(SettingsManager.APP_ANIMATIONS)
                SettingsRowToggle(
                    title = "App Animations",
                    subtitle = "Use Dynamic Animations When Launching Apps from Search Overlay Page.",
                    icon = Icons.Outlined.Animation,
                    isChecked = appAnimation,
                    onCheckedChange = { appAnimation = it },
                    showDivider = false
                )
            }

            // The overlay's quick-app panel: the same switch as Search Behavior's Quick App Panel, and how many of the
            // apps chosen under Application Search (or recent apps, when context aware) it shows.
            SettingsSectionHeader("Quick Panel Apps")
            SettingsCard {
                var quickAppPanel by rememberSetting(SettingsManager.QUICK_SEARCH_HORIZONTAL)
                SettingsRowToggle(
                    title = "Quick App Panel",
                    subtitle = "Show Your Quick Launch Apps Next to the Search Bar.",
                    icon = Icons.Outlined.ViewCompact,
                    isChecked = quickAppPanel,
                    onCheckedChange = { quickAppPanel = it },
                    showDivider = true
                )

                var maxQuickPanelApps by rememberSetting(SettingsManager.SHORTCUT_RESULTS_COUNT)

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Max Quick Panel Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        // Material's own IconButton: the click haptic below is this button's one haptic.
                        androidx.compose.material3.IconButton(
                            onClick = {
                                performClickHaptic(context)
                                maxQuickPanelApps = SettingsManager.SHORTCUT_RESULTS_COUNT.default
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset Max Quick Panel Apps",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 1 to 20, the most apps Application Search lets you choose: past six the pills shrink to fit, and
                    // the panel scrolls for the rest.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("$maxQuickPanelApps", modifier = Modifier.padding(end = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Android17Slider(
                            label = "Max Quick Panel Apps",
                            valueDescription = if (maxQuickPanelApps == 1) "1 app" else "$maxQuickPanelApps apps",
                            value = maxQuickPanelApps.toFloat(),
                            onValueChange = { newValue ->
                                maxQuickPanelApps = newValue.toInt().coerceIn(1, SearchPillProfiles.MAX_PILLS)
                            },
                            valueRange = 1f..SearchPillProfiles.MAX_PILLS.toFloat(),
                            steps = SearchPillProfiles.MAX_PILLS - 2,
                            isSquiggly = false,
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp)
                        )
                    }
                }
            }

            SettingsSectionHeader("Results Preferences")
            SettingsCard {
                PrioritySetting(SettingsManager.SEARCH_WEIGHT_APPS, "App")
            }
        }
    }
}
