package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.core.answers.AnswerPrivacy
import com.pixel.intelligentsearch.core.answers.GeoPlace
import com.pixel.intelligentsearch.core.answers.HomeLocationCodec
import com.pixel.intelligentsearch.core.answers.InstantAnswerEngine
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.security.SafeWebLaunch
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.search.LocationRationaleDialog
import kotlinx.coroutines.launch

/**
 * What a web instant answer's subtitle says: the service it uses, or why it can't run right now (the switches it
 * depends on). Each subtitle names its service, as every feature that reaches a third party must.
 */
internal fun webAnswerSubtitle(what: String, service: String, webSuggestions: Boolean, engine: String): String = when {
    SafeWebLaunch.isTorEngine(engine) -> "Off While Tor Is Your Search Engine. Uses $service."
    !webSuggestions -> "Needs Web Suggestions On. Uses $service."
    else -> "Show $what Inline from $service."
}

/**
 * The Web Search page's Instant Answers card: definitions from the Free Dictionary API and forecasts from Open-Meteo,
 * both off until turned on, with the weather's home location and approximate-location choice.
 */
@Composable
fun InstantAnswersCard() {
    val context = LocalContext.current
    val engine = remember { context.appEntryPoint.instantAnswerEngine() }
    val scope = rememberCoroutineScope()
    val webSuggestions by rememberSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS)
    val searchEngine by rememberSetting(SettingsManager.SEARCH_ENGINE)

    SettingsCard {
        var dictionary by rememberSetting(SettingsManager.DICTIONARY_ANSWERS)
        SettingsRowToggle(
            title = "Dictionary Answers",
            subtitle = webAnswerSubtitle("Definitions", "the Free Dictionary API (dictionaryapi.dev)", webSuggestions, searchEngine),
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            isChecked = dictionary,
            onCheckedChange = { on ->
                dictionary = on
                // Off means off: the definitions kept from before go too.
                if (!on) scope.launch { engine.clearCache(InstantAnswerEngine.Feature.DICTIONARY) }
            },
            showDivider = true
        )
        var weather by rememberSetting(SettingsManager.WEATHER_ANSWERS)
        SettingsRowToggle(
            title = "Weather Answers",
            subtitle = webAnswerSubtitle("Forecasts", "Open-Meteo (open-meteo.com)", webSuggestions, searchEngine),
            icon = Icons.Outlined.WbSunny,
            isChecked = weather,
            onCheckedChange = { on ->
                weather = on
                if (!on) scope.launch { engine.clearCache(InstantAnswerEngine.Feature.WEATHER) }
            },
            showDivider = weather
        )
        AnimatedVisibility(visible = weather) {
            Column {
                HomeLocationRow(engine)
                ApproximateLocationRow()
            }
        }
    }
}

@Composable
private fun HomeLocationRow(engine: InstantAnswerEngine) {
    var home by rememberSetting(SettingsManager.WEATHER_HOME_LOCATION)
    var choosing by remember { mutableStateOf(false) }
    val place = HomeLocationCodec.decode(home)
    SettingsRow(
        title = "Home Location",
        subtitle = place?.let { "${it.name}." } ?: "Not Set. Search for Your City.",
        icon = Icons.Outlined.Home,
        onClick = { choosing = true },
        showDivider = true
    )
    if (choosing) {
        HomeLocationDialog(
            engine = engine,
            hasHome = place != null,
            onChosen = { chosen ->
                home = HomeLocationCodec.encode(chosen)
                choosing = false
            },
            onCleared = {
                home = ""
                choosing = false
            },
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun ApproximateLocationRow() {
    val context = LocalContext.current
    var useDevice by rememberSetting(SettingsManager.WEATHER_USE_DEVICE_LOCATION)
    var showRationale by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        useDevice = granted
    }
    val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    SettingsRowToggle(
        title = "Use Approximate Location",
        subtitle = "Use Your Phone's Approximate Location Instead of Home.",
        icon = Icons.Outlined.MyLocation,
        // Without the permission the switch can't be doing anything, so it doesn't show as on.
        isChecked = useDevice && granted,
        onCheckedChange = { on ->
            when {
                !on -> useDevice = false
                granted -> useDevice = true
                else -> showRationale = true
            }
        },
        showDivider = false
    )
    if (showRationale) {
        LocationRationaleDialog(
            onAllow = {
                showRationale = false
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showRationale = false }
        )
    }
}

/** Searches Open-Meteo's place names for the home location; nothing is sent until Search is pressed. */
@Composable
private fun HomeLocationDialog(
    engine: InstantAnswerEngine,
    hasHome: Boolean,
    onChosen: (GeoPlace) -> Unit,
    onCleared: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val allowed = AnswerPrivacy.webAnswersAllowed(context.appEntryPoint.settingsManager().prefs, SettingsManager.WEATHER_ANSWERS)

    fun search() {
        if (name.isBlank() || searching) return
        searching = true
        failed = false
        scope.launch {
            val found = engine.findPlaces(name)
            failed = found == null
            results = found
            searching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (allowed) {
                        "Search for your city. The name you type is sent to Open-Meteo to find it."
                    } else {
                        "Place search needs Web Suggestions on, and is off while Tor is your search engine."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    enabled = allowed,
                    label = { Text("City") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    shape = ExpressiveCornerTokens.ShapeExtraLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    searching -> Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                    failed -> Text("Couldn't reach Open-Meteo. Try again in a moment.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    results?.isEmpty() == true -> Text("No places found with that name.", style = MaterialTheme.typography.bodyMedium)
                }
                results.orEmpty().forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpressiveCornerTokens.ShapeLarge)
                            .expressiveRowClickable { onChosen(place) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(place.fullName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::search, enabled = allowed && name.isNotBlank() && !searching) {
                Text("Search", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row {
                if (hasHome) TextButton(onClick = onCleared) { Text("Clear", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            }
        }
    )
}
