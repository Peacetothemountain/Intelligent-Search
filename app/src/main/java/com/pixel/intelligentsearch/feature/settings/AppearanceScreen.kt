package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.ui.symbols.Symbols
import kotlin.math.roundToInt
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.answers.TemperatureChoice
import com.pixel.intelligentsearch.core.answers.TemperatureUnit
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.annotation.SuppressLint
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
import com.pixel.intelligentsearch.core.ui.icons.outlined.AutoAwesome
import com.pixel.intelligentsearch.core.ui.icons.outlined.BrightnessMedium
import com.pixel.intelligentsearch.core.ui.icons.outlined.Layers
import com.pixel.intelligentsearch.core.ui.icons.outlined.Palette
import com.pixel.intelligentsearch.core.ui.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@SuppressLint("MissingPermission")
@Composable
fun AppearanceScreen(prefs: SharedPreferences, onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar("Appearance", onBack)
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
                // Three sections, like Search Behavior's: the app as a whole, the search overlay (with the back swipe into it and
                // its wallpaper), and how the wallpaper behind it is shown.
                SettingsSectionHeader("App")
                SettingsCard {
                    var rawThemeMode by rememberSetting(SettingsManager.THEME)
                    val themeMode = ThemeMode.from(rawThemeMode).storedValue

                    SettingsDropdownRow(
                        title = "App Theme",
                        subtitle = themeMode,
                        icon = Symbols.Outlined.BrightnessMedium,
                        options = ThemeMode.entries.map { it.storedValue },
                        selectedOption = themeMode,
                        onOptionSelected = {
                            rawThemeMode = it
                            // The widgets follow the app theme, so redraw them all (both designs).
                            updateWidgets(context)
                        },
                        showDivider = true
                    )

                    // Weather in search answers and on the Discover Overlay. Automatic names the scale it picked.
                    var temperatureChoice by rememberSetting(SettingsManager.TEMPERATURE_UNIT)
                    val temperature = TemperatureChoice.from(temperatureChoice)
                    SettingsDropdownRow(
                        title = "Temperature",
                        subtitle = if (temperature == TemperatureChoice.AUTOMATIC) {
                            "Automatic (${TemperatureUnit.forLocale(androidx.compose.ui.platform.LocalLocale.current.platformLocale).symbol}), From Your Region"
                        } else temperature.label,
                        icon = Symbols.Outlined.Thermostat,
                        options = TemperatureChoice.entries.map { it.label },
                        selectedOption = temperature.label,
                        onOptionSelected = { label ->
                            TemperatureChoice.entries.firstOrNull { it.label == label }?.let { temperatureChoice = it.storedValue }
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
                                // The pack is no longer on the phone, so the system's icons are the ones showing.
                                "System Default"
                            }
                        }
                    }
                    SettingsRow(
                        title = "Custom Icons",
                        subtitle = activeIconPackLabel,
                        icon = Symbols.Outlined.Palette,
                        onClick = { onNavigate(Route.CustomIcons) },
                        showDivider = true
                    )

                    var vibrationEnabled by rememberSetting(SettingsManager.VIBRATION)
                    SettingsRowToggle(
                        title = "System Vibration",
                        subtitle = "Enable Haptic Vibrations Across Intelligent Search.",
                        icon = Symbols.Outlined.Vibration,
                        isChecked = vibrationEnabled,
                        // The haptic engines read this setting on every haptic, so saving it is enough.
                        onCheckedChange = { vibrationEnabled = it },
                        showDivider = false
                    )
                }

                // Read by the Search Overlay section (its switch) and the Background one (blur and transparency show with it).
                var showWall by rememberSetting(SettingsManager.SHOW_WALLPAPER)
                SettingsSectionHeader("Search Overlay")
                SettingsCard {
                    var enableSearchOverlay by rememberSetting(SettingsManager.SEARCH_OVERLAY_ENABLED) {
                        updateWidgets(context)
                        if (!prefs[SettingsManager.SEARCH_OVERLAY_ENABLED]) {
                            com.pixel.intelligentsearch.core.system.OverlayDismissal.request()
                        }
                    }
                    SettingsRowToggle(
                        title = "Enable Search Overlay Page",
                        subtitle = "When Turned Off, Search Bar Widget Opens Native Google Search.",
                        icon = Symbols.Outlined.Layers,
                        isChecked = enableSearchOverlay,
                        onCheckedChange = { enableSearchOverlay = it },
                        showDivider = true
                    )

                    var overlayShowMic by rememberSetting(SettingsManager.OVERLAY_SHOW_MIC)
                    SettingsRowToggle(
                        title = "Show Microphone in Search Bar",
                        subtitle = "Show Voice Search Button on Search Overlay Page.",
                        icon = Symbols.Outlined.Mic,
                        isChecked = overlayShowMic,
                        onCheckedChange = { overlayShowMic = it },
                        showDivider = true
                    )

                    var matrixAnimationEnabled by rememberSetting(SettingsManager.MATRIX_ANIMATION_ENABLED) {}
                    SettingsRowToggle(
                        title = "Enable Matrix Animation on Search Overlay Page",
                        subtitle = "Enable Search Overlay Page Animation.",
                        icon = Symbols.Outlined.AutoAwesome,
                        isChecked = matrixAnimationEnabled,
                        onCheckedChange = { matrixAnimationEnabled = it },
                        showDivider = true
                    )

                    var morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}
                    SettingsRowToggle(
                        title = "Enable Material Morph Animation",
                        subtitle = "Enable Material Expressive Bouncing Shapes.",
                        icon = Symbols.Outlined.Animation,
                        isChecked = morphAnimationEnabled,
                        onCheckedChange = { morphAnimationEnabled = it },
                        showDivider = true
                    )

                    var settingsBackToSearchOverlay by rememberSetting(SettingsManager.BACK_TO_SEARCH_OVERLAY) {}
                    SettingsRowToggle(
                        title = "Back Swipe to Enter Search Overlay Page",
                        subtitle = "Swiping Back from Settings Menu Directs to Search Overlay Screen.",
                        icon = Symbols.AutoMirrored.Outlined.ArrowBack,
                        isChecked = settingsBackToSearchOverlay,
                        onCheckedChange = { settingsBackToSearchOverlay = it },
                        showDivider = true
                    )

                    SettingsRowToggle(
                        title = "Show Wallpaper",
                        subtitle = "Show User's Wallpaper on Search Overlay Page.",
                        icon = Symbols.Outlined.Wallpaper,
                        isChecked = showWall,
                        onCheckedChange = { showWall = it },
                        showDivider = false
                    )
                }

                SettingsSectionHeader("Discover Overlay")
                SettingsCard {
                    TodaySettingsRows()
                }

                SettingsSectionHeader("Background")
                SettingsCard {

                    // Wallpaper, blur, transparency and pill opacity only style the search overlay, never the
                    // home-screen widgets, so changing them doesn't redraw the widgets.
                    var blur by rememberSetting(SettingsManager.BACKGROUND_BLUR)
                    var transparency by rememberSetting(SettingsManager.BACKGROUND_TRANSPARENCY)
                    var pillOpacity by rememberSetting(SettingsManager.PILL_OPACITY)

                    // Blur and transparency act on the wallpaper behind the overlay, so they show only with it.
                    AttachedRows(visible = showWall) {
                        Column {
                            SettingsSliderRow(
                                title = "Background Blur",
                                value = blur.toFloat(),
                                onValueChange = { blur = it.toInt() },
                                valueRange = 0f..100f,
                                icon = Symbols.Outlined.BlurOn,
                                showDivider = true,
                                steps = 9,
                                onReset = { blur = 50 },
                                valueText = ::percent
                            )

                            SettingsSliderRow(
                                title = "Background Transparency",
                                value = transparency.toFloat(),
                                onValueChange = { transparency = it.toInt() },
                                valueRange = 0f..100f,
                                icon = Symbols.Outlined.Opacity,
                                showDivider = true,
                                steps = 9,
                                onReset = { transparency = 50 },
                                valueText = ::percent
                            )
                        }
                    }
                    
                    SettingsSliderRow(
                        title = "Search Pill Opacity",
                        value = pillOpacity.toFloat(),
                        onValueChange = { pillOpacity = it.toInt() },
                        valueRange = 0f..100f,
                        icon = Symbols.Outlined.BrightnessMedium,
                        showDivider = false,
                        steps = 9,
                        onReset = { pillOpacity = SettingsManager.PILL_OPACITY.default },
                        valueText = ::percent
                    )
                }
            }
        }
    }
}

/** A 0–100 slider's value as it reads beside its title. */
private fun percent(value: Float): String = "${value.roundToInt()}%"
