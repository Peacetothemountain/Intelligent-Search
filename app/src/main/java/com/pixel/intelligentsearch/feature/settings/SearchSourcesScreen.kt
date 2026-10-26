package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.navigation.Route
import com.pixel.intelligentsearch.di.appEntryPoint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourcesScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    Box(modifier = Modifier.fillMaxSize()) {
        val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Search Sources", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            CardsAboveMorphAnimation(
                morphAnimationEnabled = morphAnimationEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCard {
                        var searchApps by rememberSetting(SettingsManager.SEARCH_APPS)
                        SettingsRowToggle(
                            title = "Apps",
                            subtitle = "Search Installed Applications.",
                            icon = Icons.Outlined.Apps,
                            isChecked = searchApps,
                            onCheckedChange = { searchApps = it },
                            onClick = { 
                                if (searchApps) {
                                    onNavigate(Route.AppSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Apps first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        var searchWeb by rememberSetting(SettingsManager.SEARCH_WEB)
                        SettingsRowToggle(
                            title = "Web",
                            subtitle = "Web Searches, Suggestions and AI Answers.",
                            icon = Icons.Outlined.Language,
                            isChecked = searchWeb,
                            onCheckedChange = { searchWeb = it },
                            onClick = { 
                                if (searchWeb) {
                                    onNavigate(Route.WebSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Web first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        var searchContacts by rememberSetting(SettingsManager.SEARCH_CONTACTS)
                        var searchFiles by rememberSetting(SettingsManager.SEARCH_FILES)

                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                // Only follow a revoked permission downward: the user may have turned Contacts off
                                // with the permission still granted, and that choice must survive leaving the screen.
                                if (event == Lifecycle.Event.ON_RESUME && searchContacts &&
                                    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    searchContacts = false
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        // Declining once just leaves Contacts off; declined for good, the request offers Android's
                        // settings.
                        val contactsRequest = rememberContactsRequest { granted ->
                            searchContacts = granted
                            if (granted) viewModel?.reindexContacts()
                        }

                        SettingsRowToggle(
                            title = "Contacts",
                            subtitle = "Search Contacts.",
                            icon = Icons.Outlined.Contacts,
                            isChecked = searchContacts,
                            onCheckedChange = { isChecked -> 
                                if (isChecked) contactsRequest.request() else searchContacts = false
                            },
                            onClick = { 
                                if (searchContacts) {
                                    onNavigate(Route.ContactSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Contacts first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        
                        SettingsRowToggle(
                            title = "Files",
                            subtitle = "Search Local Files & Media via System Pickers.",
                            icon = Icons.Outlined.Folder,
                            isChecked = searchFiles,
                            onCheckedChange = { isChecked -> 
                                searchFiles = isChecked
                            },
                            onClick = { 
                                if (searchFiles) {
                                    onNavigate(Route.FileSearch) 
                                } else {
                                    Toast.makeText(context, "Enable Files first to configure settings.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )
                        // Either switch can stop or allow the daily rates download; bring it in line at once, off the
                        // main thread (it checks the saved rates and talks to WorkManager) so the switch never waits.
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val syncCurrencyRates: () -> Unit = {
                            val rates = context.appEntryPoint.currencyRateRepository()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { rates.syncSchedule() }
                        }
                        var searchCalc by rememberSetting(SettingsManager.SEARCH_CALCULATOR, onChanged = syncCurrencyRates)
                        SettingsRowToggle(
                            title = "Calculator",
                            subtitle = "Calculate Mathematical Equations Inside Search Bar.",
                            icon = Icons.Outlined.Calculate,
                            isChecked = searchCalc,
                            onCheckedChange = { searchCalc = it },
                            showDivider = searchCalc
                        )
                        // Currency answers come from the Calculator, so their rates only matter while it is on.
                        AttachedRows(visible = searchCalc) {
                            var liveCurrencyRates by rememberSetting(SettingsManager.LIVE_CURRENCY_RATES, onChanged = syncCurrencyRates)
                            SettingsRowToggle(
                                title = "Live Currency Rates",
                                subtitle = "Update Currency Rates Daily from the European Central Bank.",
                                icon = Icons.Outlined.CurrencyExchange,
                                isChecked = liveCurrencyRates,
                                onCheckedChange = { liveCurrencyRates = it },
                                showDivider = false
                            )
                        }
                    }
                }
            }
        }
    }
}
