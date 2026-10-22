package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import android.content.SharedPreferences
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.di.appEntryPoint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSearchScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsViewModel = LocalSettingsViewModel.current
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text("Web Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
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
                // Tor stops the daily currency rates download (the request would leave Tor); another engine may allow it.
                var searchEngine by rememberSetting(SettingsManager.SEARCH_ENGINE) {
                    context.appEntryPoint.currencyRateRepository().syncSchedule()
                }
                var customEngineName by rememberSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_NAME)
                val effectiveSearchEngineName = if (searchEngine == "Custom") customEngineName else searchEngine
                var showTorDialog by remember { mutableStateOf(false) }

                SettingsDropdownRow(
                    title = "Primary Search App",
                    subtitle = effectiveSearchEngineName,
                    icon = Icons.Outlined.Search,
                    options = listOf("Google", "DuckDuckGo", "Bing", "Tor Project", "Custom"),
                    selectedOption = searchEngine,
                    onOptionSelected = { selected ->
                        if (selected == "Tor Project") {
                            val pm = context.packageManager
                            val isTorInstalled = pm.getLaunchIntentForPackage("org.torproject.torbrowser") != null ||
                                pm.getLaunchIntentForPackage("org.torproject.torbrowser_alpha") != null
                            if (!isTorInstalled) {
                                showTorDialog = true
                                return@SettingsDropdownRow
                            }
                        }
                        if (selected == "Custom" && settingsViewModel != null &&
                            prefs[SettingsManager.CUSTOM_SEARCH_ENGINE_URL].isNullOrBlank()
                        ) {
                            // The URL field shows this default; persist it so searches use what is displayed
                            // instead of silently falling back to the generic web-search intent.
                            val defaultUrl = "https://duckduckgo.com/?q=%s"
                            settingsViewModel.updateSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL, defaultUrl)
                        }
                        searchEngine = selected
                    },
                    showDivider = searchEngine != "Custom",
                    optionLeadingIcon = { engine -> SearchAppColorfulIcon(appName = engine, modifier = Modifier.fillMaxSize()) }
                )

                if (showTorDialog) {
                    TorBrowserInstallDialog(
                        onDismiss = { showTorDialog = false }
                    )
                }
                if (searchEngine == "Custom") {
                    var customUrl by rememberSetting(SettingsManager.CUSTOM_SEARCH_ENGINE_URL)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchAppColorfulIcon(appName = customEngineName, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = customEngineName,
                            onValueChange = { customEngineName = com.pixel.intelligentsearch.core.security.CustomSearchUrl.sanitizeEngineName(it) },
                            singleLine = true,
                            label = { Text("Search App Name") },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // The field keeps whatever is typed, but only an address that passes validation is saved
                    // (in its canonical https form). Searches re-check it anyway, so nothing unvalidated is opened.
                    var urlText by remember { mutableStateOf(customUrl) }
                    val urlCheck = remember(urlText) { com.pixel.intelligentsearch.core.security.CustomSearchUrl.validate(urlText) }
                    androidx.compose.material3.OutlinedTextField(
                        value = urlText,
                        onValueChange = { typed ->
                            urlText = typed.take(com.pixel.intelligentsearch.core.security.CustomSearchUrl.MAX_LENGTH)
                            val check = com.pixel.intelligentsearch.core.security.CustomSearchUrl.validate(urlText)
                            if (check is com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Valid) customUrl = check.template
                        },
                        label = { Text("Custom Search URL (use %s for query)") },
                        singleLine = true,
                        isError = urlText.isNotBlank() && urlCheck is com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Invalid,
                        supportingText = (urlCheck as? com.pixel.intelligentsearch.core.security.CustomSearchUrl.Validation.Invalid)
                            ?.takeIf { urlText.isNotBlank() }
                            ?.let { invalid -> { Text(invalid.reason) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                            autoCorrectEnabled = false
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                var webSuggestions by rememberSetting(SettingsManager.SEARCH_WEB_SUGGESTIONS)
                SettingsRowToggle(
                    title = "Web Suggestions",
                    subtitle = "Sends What You Type to Your Search Engine for Live Suggestions.",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    isChecked = webSuggestions,
                    onCheckedChange = { webSuggestions = it },
                    showDivider = true
                )

                var searchPreviousSearches by rememberSetting(SettingsManager.SEARCH_PREVIOUS_SEARCHES)
                SettingsRowToggle(
                    title = "Search History",
                    subtitle = "Previously Searched Web Queries.",
                    icon = Icons.Outlined.History,
                    isChecked = searchPreviousSearches,
                    onCheckedChange = { searchPreviousSearches = it },
                    showDivider = true
                )

                ResultsCountSetting(SettingsManager.WEB_RESULTS_COUNT, "Web", defaultCount = 5)

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                PrioritySetting(SettingsManager.SEARCH_WEIGHT_WEB, "Web")
            }

            SettingsSectionHeader("Instant Answers")
            InstantAnswersCard()

            SettingsSectionHeader("Quick Web Shortcuts")
            WebQuickShortcutsCard()
        }
    }
}

// -----------------------------------------------------------------------------------------