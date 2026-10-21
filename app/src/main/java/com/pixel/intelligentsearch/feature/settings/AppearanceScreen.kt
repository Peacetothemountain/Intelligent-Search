package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
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
                    title = { Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
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
                    var rawThemeMode by rememberSetting(SettingsManager.THEME)
                    val themeMode = ThemeMode.from(rawThemeMode).storedValue

                    SettingsDropdownRow(
                        title = "App Theme",
                        subtitle = themeMode,
                        icon = Icons.Outlined.BrightnessMedium,
                        options = ThemeMode.entries.map { it.storedValue },
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

                    var activeIconPack by rememberSetting(SettingsManager.ACTIVE_ICON_PACK)
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

                    var vibrationEnabled by rememberSetting(SettingsManager.VIBRATION)
                    SettingsRowToggle(
                        title = "System Vibration",
                        subtitle = "Enable Haptic Vibrations Across Intelligent Search",
                        icon = Icons.Outlined.Vibration,
                        isChecked = vibrationEnabled,
                        // The haptic engines read this setting on every haptic, so saving it is enough.
                        onCheckedChange = { vibrationEnabled = it },
                        showDivider = true
                    )

                    var settingsBackToSearchOverlay by rememberSetting(SettingsManager.BACK_TO_SEARCH_OVERLAY) {}
                    SettingsRowToggle(
                        title = "Back Swipe to Enter Search Overlay Page",
                        subtitle = "Swiping Back from Settings Menu Directs to Search Overlay Screen.",
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        isChecked = settingsBackToSearchOverlay,
                        onCheckedChange = { settingsBackToSearchOverlay = it },
                        showDivider = true
                    )

                    var enableSearchOverlay by rememberSetting(SettingsManager.SEARCH_OVERLAY_ENABLED) {
                        updateWidgets(context)
                        if (!prefs[SettingsManager.SEARCH_OVERLAY_ENABLED]) {
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

                    var matrixAnimationEnabled by rememberSetting(SettingsManager.MATRIX_ANIMATION_ENABLED) {}
                    SettingsRowToggle(
                        title = "Enable Matrix Animation on Search Overlay Page",
                        subtitle = "Enable Search Overlay Page Animation.",
                        icon = Icons.Outlined.AutoAwesome,
                        isChecked = matrixAnimationEnabled,
                        onCheckedChange = { matrixAnimationEnabled = it },
                        showDivider = true
                    )

                    var morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}
                    SettingsRowToggle(
                        title = "Enable Material Morph Animation",
                        subtitle = "Enable Material Expressive Bouncing Shapes.",
                        icon = Icons.Outlined.Animation,
                        isChecked = morphAnimationEnabled,
                        onCheckedChange = { morphAnimationEnabled = it },
                        showDivider = true
                    )

                    var showWall by rememberSetting(SettingsManager.SHOW_WALLPAPER) { updateWidgets(context) }
                    SettingsRowToggle(
                        title = "Show Wallpaper",
                        subtitle = "Show User's Wallpaper on Search Overlay Page.",
                        icon = Icons.Outlined.Wallpaper,
                        isChecked = showWall,
                        onCheckedChange = { showWall = it },
                        showDivider = true
                    )
                    
                    var blur by rememberSetting(SettingsManager.BACKGROUND_BLUR) { updateWidgets(context) }
                    var transparency by rememberSetting(SettingsManager.BACKGROUND_TRANSPARENCY) { updateWidgets(context) }
                    var pillOpacity by rememberSetting(SettingsManager.PILL_OPACITY) { updateWidgets(context) }

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
