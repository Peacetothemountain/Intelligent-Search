package com.pixel.intelligentsearch.feature.search

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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.pixel.intelligentsearch.core.answers.GeoPlace
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens
import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.feature.settings.TextButton
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable
import kotlinx.coroutines.launch

/**
 * Set Location, from the weather card: a city search on Open-Meteo's place names. Nothing is sent until Search is
 * pressed, and the card only offers it while forecasts may be fetched (Web Suggestions on, Tor not the engine), which
 * the engine checks again. [onChosen] gets the place picked; [onCleared] (offered when [hasLocation]) removes the set
 * location so the phone's approximate location is used, or asked for, again.
 */
@Composable
internal fun WeatherLocationDialog(
    hasLocation: Boolean,
    onChosen: (GeoPlace) -> Unit,
    onCleared: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember(context) { context.appEntryPoint.liveAnswerEngine() }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

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
        title = { Text("Set Location", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Search for your city. What you type is sent to Open-Meteo to find it.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("City") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    shape = ExpressiveCornerTokens.ShapeExtraLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    searching -> Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                    failed -> Text(
                        "Couldn't reach Open-Meteo. Try again in a moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
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
            TextButton(onClick = ::search, enabled = name.isNotBlank() && !searching) {
                Text("Search", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            Row {
                if (hasLocation) TextButton(onClick = onCleared) { Text("Clear", style = MaterialTheme.typography.labelLarge) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
            }
        }
    )
}
