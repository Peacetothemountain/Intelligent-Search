package com.pixel.intelligentsearch.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixel.intelligentsearch.core.bangs.SearchBang
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.haptics.PixelHapticType
import com.pixel.intelligentsearch.core.icons.AdaptiveIconShape
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.weighting.SearchSectionConfig
import com.pixel.intelligentsearch.core.weighting.SearchSectionType
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------------------
// 1. SEARCH BANGS MANAGEMENT SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBangsScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    val bangs by (viewModel?.bangsFlow ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsStateWithLifecycle()
    val hapticEngine = remember { PixelHapticEngine.get(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var testQueryInput by remember { mutableStateOf("!yt lo-fi beats") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Bangs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Bang")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                    showAddDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Bang", fontFamily = GoogleSansFlex) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "DuckDuckGo-Style Bangs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = GoogleSansFlex
                            )
                            Text(
                                "Type !bang anywhere in your query (e.g. !yt music, !w quantum, or query !g) to dispatch directly to target apps or sites.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                SettingsCard {
                    Text(
                        "Test Bang Dispatch",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = GoogleSansFlex,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = testQueryInput,
                        onValueChange = { testQueryInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        placeholder = { Text("!yt lo-fi beats") }
                    )
                    Button(
                        onClick = {
                            hapticEngine.performHaptic(null, PixelHapticType.CONFIRM)
                            val parsed = com.pixel.intelligentsearch.core.bangs.SearchBangManager(context, com.pixel.intelligentsearch.core.data.SettingsManager(context))
                                .parseBangQuery(testQueryInput, bangs)
                            if (parsed != null) {
                                val intent = com.pixel.intelligentsearch.core.bangs.SearchBangManager(context, com.pixel.intelligentsearch.core.data.SettingsManager(context))
                                    .dispatchBangSearch(parsed)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not launch: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No matching bang found in '$testQueryInput'", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dispatch Test Query", fontFamily = GoogleSansFlex)
                    }
                }
            }

            item {
                Text(
                    "Configured Bangs (${bangs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            itemsIndexed(bangs, key = { _, bang -> bang.prefix }) { _, bang ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = bang.prefix,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    bang.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    fontFamily = GoogleSansFlex
                                )
                                if (bang.isBuiltIn) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            "BUILT-IN",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                bang.urlTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!bang.isBuiltIn) {
                            IconButton(onClick = {
                                hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                                viewModel?.deleteCustomBang(bang.prefix)
                                Toast.makeText(context, "Deleted ${bang.prefix}", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddBangDialog(
                onDismiss = { showAddDialog = false },
                onSave = { newBang ->
                    viewModel?.saveCustomBang(newBang)
                    showAddDialog = false
                    Toast.makeText(context, "Saved bang ${newBang.prefix}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun AddBangDialog(
    onDismiss: () -> Unit,
    onSave: (SearchBang) -> Unit
) {
    var prefix by remember { mutableStateOf("!") }
    var name by remember { mutableStateOf("") }
    var urlTemplate by remember { mutableStateOf("") }
    var targetPackage by remember { mutableStateOf("") }
    var appIntentUri by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Bang", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { if (it.startsWith("!")) prefix = it else prefix = "!$it" },
                    label = { Text("Prefix (e.g. !gh, !so)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name (e.g. GitHub)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("URL Template with %s") },
                    placeholder = { Text("https://example.com/search?q=%s") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = { targetPackage = it },
                    label = { Text("Target Package (Optional)") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = appIntentUri,
                    onValueChange = { appIntentUri = it },
                    label = { Text("App Scheme URI (Optional)") },
                    placeholder = { Text("example://search?q=%s") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prefix.length >= 2 && name.isNotBlank() && urlTemplate.isNotBlank()) {
                        val bang = SearchBang(
                            prefix = prefix.lowercase(),
                            name = name.trim(),
                            urlTemplate = urlTemplate.trim(),
                            targetPackage = targetPackage.trim().ifBlank { null },
                            appIntentUriTemplate = appIntentUri.trim().ifBlank { null },
                            isBuiltIn = false
                        )
                        onSave(bang)
                    }
                },
                enabled = prefix.length >= 2 && name.isNotBlank() && urlTemplate.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -----------------------------------------------------------------------------------------
// 2. GRANULAR SEARCH SOURCE WEIGHTING & CATEGORIZATION SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceWeightingScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    val configs by (viewModel?.sectionConfigsFlow ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsStateWithLifecycle()
    val hapticEngine = remember { PixelHapticEngine.get(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Weighting", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                        viewModel?.resetSectionConfigsToDefault()
                        Toast.makeText(context, "Reset to default priorities", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Reset Defaults")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Result Ranking & Categorization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = GoogleSansFlex
                            )
                            Text(
                                "Reorder sections to prioritize your workflow. Adjust priority multipliers (0.1x to 2.0x) and result limits per category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            itemsIndexed(configs, key = { _, cfg -> cfg.sectionType.name }) { index, cfg ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cfg.isEnabled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            hapticEngine.performHaptic(null, PixelHapticType.REORDER_SWAP)
                                            viewModel?.reorderSections(index, index - 1)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                }
                                IconButton(
                                    onClick = {
                                        if (index < configs.lastIndex) {
                                            hapticEngine.performHaptic(null, PixelHapticType.REORDER_SWAP)
                                            viewModel?.reorderSections(index, index + 1)
                                        }
                                    },
                                    enabled = index < configs.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    cfg.sectionType.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    fontFamily = GoogleSansFlex
                                )
                                Text(
                                    cfg.sectionType.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cfg.isEnabled,
                                onCheckedChange = { checked ->
                                    hapticEngine.performHaptic(null, if (checked) PixelHapticType.TOGGLE_ON else PixelHapticType.TOGGLE_OFF)
                                    viewModel?.updateSectionConfig(cfg.copy(isEnabled = checked))
                                }
                            )
                        }

                        if (cfg.isEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Weight Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Priority Weight", fontSize = 14.sp, fontFamily = GoogleSansFlex)
                                Text("${String.format("%.1f", cfg.weight)}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = cfg.weight,
                                onValueChange = { newWeight ->
                                    viewModel?.updateSectionConfig(cfg.copy(weight = (Math.round(newWeight * 10f) / 10f)))
                                },
                                valueRange = 0.1f..2.0f,
                                steps = 18
                            )

                            // Max Results Stepper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Max Display Results", fontSize = 14.sp, fontFamily = GoogleSansFlex)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (cfg.maxResults > 1) {
                                                hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                                                viewModel?.updateSectionConfig(cfg.copy(maxResults = cfg.maxResults - 1))
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = cfg.maxResults > 1
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        "${cfg.maxResults}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (cfg.maxResults < 25) {
                                                hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                                                viewModel?.updateSectionConfig(cfg.copy(maxResults = cfg.maxResults + 1))
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = cfg.maxResults < 25
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. UNIVERSAL ICON PACK & ADAPTIVE SHAPING SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveIconShapingScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = LocalSettingsViewModel.current
    val settings by (viewModel?.settingsState ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val hapticEngine = remember { PixelHapticEngine.get(context) }

    val currentShapeKey = settings?.adaptiveIconShape ?: "SYSTEM_DEFAULT"
    val isDynamicMasking = settings?.dynamicIconMasking ?: true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Icon Shaping & Packs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Live Geometry Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = GoogleSansFlex)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Chrome", "Maps", "Photos", "Settings").forEach { appName ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(appName, fontSize = 11.sp, fontFamily = GoogleSansFlex)
                            }
                        }
                    }
                }
            }

            Text("Select Adaptive Geometry", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

            AdaptiveIconShape.entries.forEach { shape ->
                val isSelected = currentShapeKey == shape.name
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                            viewModel?.updateSetting(SettingsManager.ADAPTIVE_ICON_SHAPE, shape.name)
                            viewModel?.clearIconCaches()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                                viewModel?.updateSetting(SettingsManager.ADAPTIVE_ICON_SHAPE, shape.name)
                                viewModel?.clearIconCaches()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(shape.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = GoogleSansFlex)
                            Text(shape.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SettingsCard {
                SettingsRowToggle(
                    title = "Dynamic Masking for Unthemed Apps",
                    subtitle = "Applies iconpack mask and backdrop layers to third-party icons lacking custom assets",
                    icon = Icons.Outlined.Layers,
                    isChecked = isDynamicMasking,
                    onCheckedChange = { checked ->
                        hapticEngine.performHaptic(null, if (checked) PixelHapticType.TOGGLE_ON else PixelHapticType.TOGGLE_OFF)
                        viewModel?.updateSetting(SettingsManager.DYNAMIC_ICON_MASKING, checked)
                        viewModel?.clearIconCaches()
                    },
                    showDivider = true
                )
                SettingsRow(
                    title = "Evict Icon Memory Caches",
                    subtitle = "Reclaims JVM bitmap heap memory and flushes cached shapes",
                    icon = Icons.Outlined.CleaningServices,
                    onClick = {
                        hapticEngine.performHaptic(null, PixelHapticType.CONFIRM)
                        viewModel?.clearIconCaches()
                        Toast.makeText(context, "Icon caches cleared", Toast.LENGTH_SHORT).show()
                    },
                    showDivider = false
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 4. ENCRYPTED BACKUP & RESTORE SCREEN
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel = LocalSettingsViewModel.current
    val hapticEngine = remember { PixelHapticEngine.get(context) }

    var passphrase by remember { mutableStateOf("") }
    var usePasswordProtection by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && activity != null) {
            viewModel?.exportBackup(
                activity = activity,
                uri = uri,
                passphrase = if (usePasswordProtection) passphrase else null,
                onSuccess = {
                    Toast.makeText(context, "Encrypted backup exported successfully!", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Export error: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && activity != null) {
            viewModel?.importBackup(
                activity = activity,
                uri = uri,
                passphrase = if (usePasswordProtection) passphrase else null,
                onSuccess = { count ->
                    Toast.makeText(context, "Restored $count configuration items!", Toast.LENGTH_LONG).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Restore error: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Backup & Restore", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("AES-GCM 256-Bit Hardware Keystore", fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = GoogleSansFlex)
                        Text(
                            "Protected by PBKDF2-HMAC-SHA256 key derivation with Titan M3+ / KeyMint StrongBox and biometric authentication gating.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            SettingsCard {
                Text(
                    "Encryption Passphrase",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = GoogleSansFlex,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "Optional passphrase for cross-device portability. If left blank, backup is bound to this device's Titan KeyStore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            // Export Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Export Configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                    Text(
                        "Includes settings, custom bangs, search history, shortcuts, and weighting matrix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                            exportLauncher.launch("intelligent_search_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Encrypted Backup", fontFamily = GoogleSansFlex)
                    }
                }
            }

            // Restore Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Restore from Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                    Text(
                        "Select an existing .json backup file to decrypt, verify SHA-256 integrity, and restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Backup to Restore", fontFamily = GoogleSansFlex)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 5. IN-APP DIAGNOSTIC & BENCHMARK DASHBOARD
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsDashboardScreen(
    prefs: SharedPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel = LocalSettingsViewModel.current
    val telemetry by (viewModel?.diagnosticsState ?: kotlinx.coroutines.flow.MutableStateFlow(com.pixel.intelligentsearch.core.diagnostics.DiagnosticsState())).collectAsStateWithLifecycle()
    val benchmark by (viewModel?.benchmarkState ?: kotlinx.coroutines.flow.MutableStateFlow(com.pixel.intelligentsearch.core.diagnostics.BenchmarkRunState())).collectAsStateWithLifecycle()
    val hapticEngine = remember { PixelHapticEngine.get(context) }

    DisposableEffect(activity) {
        if (activity != null) {
            com.pixel.intelligentsearch.core.diagnostics.PerformanceTelemetry.attachFrameMetrics(activity)
        }
        onDispose {
            if (activity != null) {
                com.pixel.intelligentsearch.core.diagnostics.PerformanceTelemetry.detachFrameMetrics(activity)
            }
        }
    }

    LaunchedEffect(Unit) {
        com.pixel.intelligentsearch.core.diagnostics.PerformanceTelemetry.refreshSystemMetrics(
            context = context,
            historyCount = 10,
            customBangsCount = 13,
            appsCount = 85
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics & Benchmark", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = viewModel?.generateDiagnosticReport() ?: "{}"
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Intelligent Search Diagnostics Report")
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Latency Metrics
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Query Latency Profiler", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(label = "p50 Latency", value = "${String.format("%.1f", telemetry.p50LatencyMs)}ms")
                            MetricBox(label = "p90 Latency", value = "${String.format("%.1f", telemetry.p90LatencyMs)}ms")
                            MetricBox(label = "p99 Latency", value = "${String.format("%.1f", telemetry.p99LatencyMs)}ms")
                        }
                    }
                }
            }

            // Frame Render Pacing (120Hz/144Hz)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("120Hz/144Hz Frame Pacing", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(label = "Refresh Rate", value = "${telemetry.frameStats.currentFps.toInt()} Hz")
                            MetricBox(label = "Avg Render", value = "${String.format("%.1f", telemetry.frameStats.avgFrameDurationMs)}ms")
                            MetricBox(label = "Jank Drops", value = "${String.format("%.1f", telemetry.frameStats.jankPercentage)}%")
                        }
                    }
                }
            }

            // Memory & Index Health
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Memory & Storage Telemetry", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(label = "Heap Used", value = "${telemetry.memoryStats.heapUsedMb} MB")
                            MetricBox(label = "Heap Max", value = "${telemetry.memoryStats.heapMaxMb} MB")
                            MetricBox(label = "DB Size", value = "${telemetry.indexHealth.databaseSizeBytes / 1024} KB")
                        }
                    }
                }
            }

            // Live Synthetic Benchmark
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Live 100-Query Stress Benchmark", fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = GoogleSansFlex)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Stress tests the tokenizer, bang regex engine, math evaluator, and AppSearch indexing pipelines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (benchmark.isRunning) {
                            LinearProgressIndicator(
                                progress = { benchmark.currentProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Running query ${benchmark.completedQueries} of ${benchmark.totalQueries}...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Button(
                                onClick = {
                                    hapticEngine.performHaptic(null, PixelHapticType.CLICK)
                                    viewModel?.runLiveBenchmark(100)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Execute 100-Query Benchmark", fontFamily = GoogleSansFlex)
                            }
                        }

                        val summary = benchmark.summary
                        if (summary != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricBox(label = "Throughput", value = "${summary.qps.toInt()} QPS")
                                MetricBox(label = "Benchmark p50", value = "${String.format("%.2f", summary.p50LatencyMs)}ms")
                                MetricBox(label = "Benchmark p99", value = "${String.format("%.2f", summary.p99LatencyMs)}ms")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = GoogleSansFlex)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontFamily = GoogleSansFlex)
    }
}
