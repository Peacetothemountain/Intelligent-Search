package com.pixel.intelligentsearch.feature.settings

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.haptics.TactileSonicEngine
import com.pixel.intelligentsearch.core.navigation.Route
import com.pixel.intelligentsearch.feature.widget.SearchWidgetProvider

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(prefs: SharedPreferences, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
                SettingsCard {
                    val isSystemDark = isSystemInDarkTheme()
                    var rawThemeMode by rememberStringPreference(prefs, "night.mode", "System")
                    val themeMode = when (rawThemeMode) {
                        "System", "System App Theme", "System Default", "system" -> "System"
                        "Material Dark", "Dark mode", "Dark" -> "Material Dark"
                        "Material Light", "Light mode", "Light" -> "Material Light"
                        else -> "System"
                    }

                    SettingsDropdownRow(
                        title = "App Theme",
                        subtitle = themeMode,
                        icon = Icons.Outlined.BrightnessMedium,
                        options = listOf("System", "Material Dark", "Material Light"),
                        selectedOption = themeMode,
                        onOptionSelected = { 
                            rawThemeMode = it
                            val intent = Intent(context, SearchWidgetProvider::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                                    ComponentName(context, SearchWidgetProvider::class.java)
                                )
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            }
                            context.sendBroadcast(intent)
                        },
                        showDivider = true
                    )

                    var activeIconPack by rememberStringPreference(prefs, "active_icon_pack", "system_default")
                    val activeIconPackLabel = remember(activeIconPack) {
                        if (activeIconPack == "system_default") {
                            "System Default"
                        } else {
                            try {
                                val pm = context.packageManager
                                val info = pm.getApplicationInfo(activeIconPack, 0)
                                pm.getApplicationLabel(info).toString()
                            } catch (e: Exception) {
                                "Custom Pack"
                            }
                        }
                    }
                    SettingsRow(
                        title = "Custom Icons",
                        subtitle = activeIconPackLabel,
                        icon = Icons.Outlined.Palette,
                        onClick = { onNavigate(Route.CustomIcons) },
                        showDivider = true
                    )

                    var vibrationEnabled by rememberBooleanPreference(prefs, "vibration_enabled", true)
                    SettingsRowToggle(
                        title = "System Vibration",
                        subtitle = "Enable Haptic Vibrations Across Intelligent Search",
                        icon = Icons.Outlined.Vibration,
                        isChecked = vibrationEnabled,
                        onCheckedChange = { 
                            vibrationEnabled = it 
                            TactileSonicEngine.get(context).isHapticEnabled = it
                        },
                        showDivider = true
                    )

                    var settingsBackToSearchOverlay by rememberBooleanPreference(prefs, "settings_back_to_search_overlay", true) {}
                    SettingsRowToggle(
                        title = "Back Swipe to Enter Search Overlay Page",
                        subtitle = "Swiping Back from Settings Menu Directs to Search Overlay Screen.",
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        isChecked = settingsBackToSearchOverlay,
                        onCheckedChange = { settingsBackToSearchOverlay = it },
                        showDivider = true
                    )

                    var enableSearchOverlay by rememberBooleanPreference(prefs, "search_overlay_enabled", true) {
                        updateWidgets(context)
                        if (!prefs.getBoolean("search_overlay_enabled", true)) {
                            context.sendBroadcast(Intent("com.pixel.intelligentsearch.DISMISS_OVERLAY").setPackage(context.packageName))
                        }
                    }
                    SettingsRowToggle(
                        title = "Enable Search Overlay Page",
                        subtitle = "When Turned Off, Search Bar Widget Opens Native Google Search.",
                        icon = Icons.Outlined.Layers,
                        isChecked = enableSearchOverlay,
                        onCheckedChange = { enableSearchOverlay = it },
                        showDivider = true
                    )

                    var matrixAnimationEnabled by rememberBooleanPreference(prefs, "matrix_animation_enabled", true) {}
                    SettingsRowToggle(
                        title = "Enable Matrix Animation on Search Overlay Page",
                        subtitle = "Enable Search Overlay Page Animation.",
                        icon = Icons.Outlined.AutoAwesome,
                        isChecked = matrixAnimationEnabled,
                        onCheckedChange = { matrixAnimationEnabled = it },
                        showDivider = true
                    )

                    var morphAnimationEnabled by rememberBooleanPreference(prefs, "morph_animation_enabled", false) {}
                    SettingsRowToggle(
                        title = "Enable Material Morph Animation",
                        subtitle = "Enable Material Expressive Bouncing Shapes.",
                        icon = Icons.Outlined.Animation,
                        isChecked = morphAnimationEnabled,
                        onCheckedChange = { morphAnimationEnabled = it },
                        showDivider = true
                    )

                    var showWall by rememberBooleanPreference(prefs, "search.background.show.wall", true) { updateWidgets(context) }
                    SettingsRowToggle(
                        title = "Show Wallpaper",
                        subtitle = "Show User's Wallpaper on Search Overlay Page.",
                        icon = Icons.Outlined.Wallpaper,
                        isChecked = showWall,
                        onCheckedChange = { showWall = it },
                        showDivider = true
                    )
                    
                    var blur by rememberIntPreference(prefs, "search.background.blur", 50) { updateWidgets(context) }
                    var transparency by rememberIntPreference(prefs, "search.background.transparency", 50) { updateWidgets(context) }
                    var pillOpacity by rememberIntPreference(prefs, "search.pill.opacity", 50) { updateWidgets(context) }

                    SettingsSliderRow(
                        title = "Background Blur",
                        value = blur.toFloat(),
                        onValueChange = { blur = it.toInt() },
                        valueRange = 0f..100f,
                        icon = Icons.Outlined.BlurOn,
                        showDivider = true,
                        steps = 9,
                        onReset = { blur = 50 }
                    )
                    
                    SettingsSliderRow(
                        title = "Background Transparency",
                        value = transparency.toFloat(),
                        onValueChange = { transparency = it.toInt() },
                        valueRange = 0f..100f,
                        icon = Icons.Outlined.Opacity,
                        showDivider = true,
                        steps = 9,
                        onReset = { transparency = 50 }
                    )
                    
                    SettingsSliderRow(
                        title = "Search Pill Opacity",
                        value = pillOpacity.toFloat(),
                        onValueChange = { pillOpacity = it.toInt() },
                        valueRange = 0f..100f,
                        icon = Icons.Outlined.BrightnessMedium,
                        showDivider = false,
                        steps = 9,
                        onReset = { pillOpacity = 50 }
                    )
                }
            }
        }
    }
}
