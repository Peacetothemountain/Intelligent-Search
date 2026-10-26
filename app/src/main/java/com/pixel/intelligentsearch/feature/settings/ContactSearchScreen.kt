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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Text("Contact Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                var directCall by rememberSetting(SettingsManager.CONTACT_DIRECT_CALL)
                SettingsRowToggle(
                    title = "Direct Call",
                    subtitle = "Tap Contact to Call Directly.",
                    icon = Icons.Outlined.Call,
                    isChecked = directCall,
                    onCheckedChange = { directCall = it },
                    showDivider = true
                )
                // Android doesn't tell the app which assistant is asking, so it can't check that assistant's
                // own contacts permission; sharing therefore needs this separate, off-by-default consent.
                var shareWithAssistants by rememberSetting(SettingsManager.APP_FUNCTIONS_SHARE_CONTACTS)
                SettingsRowToggle(
                    title = "Share with Assistants",
                    subtitle = "Let On-Device Assistants Search Your Contacts Through Intelligent Search.",
                    icon = Icons.Outlined.Assistant,
                    isChecked = shareWithAssistants,
                    onCheckedChange = { shareWithAssistants = it },
                    showDivider = true
                )

                ResultsCountSetting(SettingsManager.CONTACT_RESULTS_COUNT, "Contact", defaultCount = 5)

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                PrioritySetting(SettingsManager.SEARCH_WEIGHT_CONTACTS, "Contact")
            }
        }
    }
}
