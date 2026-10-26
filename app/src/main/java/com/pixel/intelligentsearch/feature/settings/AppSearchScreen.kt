package com.pixel.intelligentsearch.feature.settings

import androidx.core.content.edit
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
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                    subtitle = QUICK_APP_PANEL_SUBTITLE,
                    icon = Icons.Outlined.ViewCompact,
                    isChecked = quickAppPanel,
                    onCheckedChange = { quickAppPanel = it },
                    showDivider = true
                )

                val maxQuickPanelApps by rememberSetting(SettingsManager.SHORTCUT_RESULTS_COUNT)
                val pillProfiles = remember(prefs) { SearchPillProfiles(prefs) }
                // The slider moves freely; the max is set when the user lets go. A max below the apps chosen under
                // Application Search removes the extra apps from the bottom up, so it asks first unless told not to.
                var sliderMax by remember(maxQuickPanelApps) { mutableIntStateOf(maxQuickPanelApps) }
                var pendingMax by remember { mutableStateOf<Int?>(null) }
                fun requestMax(newMax: Int) {
                    sliderMax = newMax
                    if (newMax == maxQuickPanelApps) return
                    if (pillProfiles.appsRemovedByMax(newMax).isEmpty() || prefs[SettingsManager.QUICK_PANEL_TRIM_WARNING_DISMISSED]) {
                        pillProfiles.setMaxQuickPanelApps(newMax)
                    } else {
                        pendingMax = newMax
                    }
                }
                pendingMax?.let { newMax ->
                    val removed = remember(newMax) { pillProfiles.appsRemovedByMax(newMax).map { appLabel(context, it) } }
                    WarningCard(
                        title = "Remove Apps from Application Search?",
                        message = quickPanelTrimMessage(newMax, removed),
                        acceptLabel = "Accept",
                        onAccept = {
                            pendingMax = null
                            pillProfiles.setMaxQuickPanelApps(newMax)
                        },
                        onDiscard = {
                            pendingMax = null
                            sliderMax = maxQuickPanelApps
                        },
                        onDontShowAgain = {
                            prefs.edit { put(SettingsManager.QUICK_PANEL_TRIM_WARNING_DISMISSED, true) }
                            pendingMax = null
                            pillProfiles.setMaxQuickPanelApps(newMax)
                        },
                        onDismissRequest = {
                            pendingMax = null
                            sliderMax = maxQuickPanelApps
                        }
                    )
                }

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
                                requestMax(SettingsManager.SHORTCUT_RESULTS_COUNT.default)
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
                        Text("$sliderMax", modifier = Modifier.padding(end = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Android17Slider(
                            label = "Max Quick Panel Apps",
                            valueDescription = if (sliderMax == 1) "1 app" else "$sliderMax apps",
                            value = sliderMax.toFloat(),
                            onValueChange = { newValue ->
                                sliderMax = newValue.toInt().coerceIn(1, SearchPillProfiles.MAX_PILLS)
                            },
                            onValueChangeFinished = { requestMax(sliderMax) },
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

/** What the Quick App Panel switch does: its apps are the ones chosen under Application Search. */
internal const val QUICK_APP_PANEL_SUBTITLE =
    "Fixed Applications Sourced from Application Search Menu. Quickly Launch Your Favorite Apps Without Searching."

/** An installed app's name, or its package name when it isn't installed. */
private fun appLabel(context: android.content.Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

/** What the remove-apps card says when Max Quick Panel Apps is lowered to [newMax], removing the apps named [removed]. */
internal fun quickPanelTrimMessage(newMax: Int, removed: List<String>): String {
    val names = when (removed.size) {
        1 -> removed[0]
        2 -> "${removed[0]} and ${removed[1]}"
        else -> removed.dropLast(1).joinToString(", ") + " and " + removed.last()
    }
    val apps = if (newMax == 1) "1 app" else "$newMax apps"
    return if (removed.size == 1) {
        "Application Search can then hold only $apps, so the last app in its list, $names, will be removed."
    } else {
        "Application Search can then hold only $apps, so its last ${removed.size} apps will be removed, starting from the bottom: $names."
    }
}
