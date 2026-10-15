package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.di.appEntryPoint
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.App
import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*
import com.pixel.intelligentsearch.core.data.SettingsManager

// -----------------------------------------------------------------------------------------
// MAIN SETTINGS
// -----------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    prefs: SharedPreferences,
    onNavigate: (com.pixel.intelligentsearch.core.navigation.Route) -> Unit,
    onBack: () -> Unit,
    context: Context,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null,
    showTutorial: Boolean = false
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                player?.play()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player?.play()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.pause()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Developer Note")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        val isTutorialActive = TutorialManager.isTutorialActive(prefs)

        if (showInfoDialog) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
            val coroutineScope = rememberCoroutineScope()
            val keystore = remember { context.appEntryPoint.strongBoxSecurityManager() }
            val attestationVerifier = remember { com.pixel.intelligentsearch.core.security.KeyAttestationVerifier(context) }
            val hardwareLevel by androidx.compose.runtime.produceState(initialValue = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.STRONGBOX else com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.TEE) {
                // A Keystore failure keeps the feature-flag estimate instead of crashing Settings.
                value = try {
                    withContext(Dispatchers.IO) { keystore.deviceKeySecurityLevel() }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w("SettingsScreens", "Could not read hardware security level", e)
                    value
                }
            }
            val hardwareInfo = remember(hardwareLevel) {
                com.pixel.intelligentsearch.core.security.HardwareSecurityDetector.detectSecurityHardware(context, hardwareLevel)
            }
            val attestationResult by androidx.compose.runtime.produceState<com.pixel.intelligentsearch.core.security.AttestationResult?>(initialValue = null) {
                value = withContext(Dispatchers.IO) {
                    attestationVerifier.generateAndVerifyAttestation()
                }
            }
            val view = androidx.compose.ui.platform.LocalView.current

            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (pagerState.currentPage) {
                                0 -> "Developer Note"
                                1 -> "Hardware & Security"
                                else -> "Battery & System Diagnostics"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { pageIndex ->
                                val isSelected = pagerState.currentPage == pageIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 10.dp else 6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                },
                text = {
                    // Window size rather than Configuration.screenHeightDp, so split-screen and
                    // freeform windows size the pager to the space the dialog actually has.
                    val windowHeightPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
                    val pagerDensity = androidx.compose.ui.platform.LocalDensity.current
                    val pagerHeight = remember(windowHeightPx, pagerDensity) {
                        with(pagerDensity) { (windowHeightPx.toDp().value * 0.54f).coerceIn(368f, 480f).dp }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(pagerHeight)
                    ) { page ->
                        when (page) {
                            0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.TravelExplore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "Default Search Engine",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Starting with Android 17 QPR2, you can choose Intelligent Search as your system-wide default search engine to natively power your home screen search bar.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (com.pixel.intelligentsearch.core.system.SearchRoleManager.isSearchRoleHeld(context)) {
                                        Surface(
                                            onClick = {
                                                com.pixel.intelligentsearch.core.system.SearchRoleManager.openDefaultSearchEngineSettings(context)
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Intelligent Search is your active Default Search Engine.",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "Natively active as your home search bar. Tap to manage in system settings.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                                    contentDescription = "Open Settings",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = "Select Intelligent Search in Android settings to activate it on your home screen:",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Button(
                                                    onClick = {
                                                        com.pixel.intelligentsearch.core.system.SearchRoleManager.syncAllSearchEngineSettings(context)
                                                        com.pixel.intelligentsearch.core.system.SearchRoleManager.openDefaultSearchEngineSettings(context)
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Set as Default Search Engine")
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Terminal,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        text = "Manual ADB Activation",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                val adbCommands = "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch\nadb shell settings put secure selected_search_engine com.pixel.intelligentsearch"
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ADB Commands", adbCommands))
                                                        Toast.makeText(context, "ADB commands copied to clipboard", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ContentCopy,
                                                        contentDescription = "Copy ADB Commands",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "If you're running Android 17 QPR2 or an older version, bypass the system package allow list and set Intelligent Search as default manually via ADB:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        "adb shell settings put secure user_setup_search_engine com.pixel.intelligentsearch",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Compatibility & Integration Notes",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "• Package Allow List: Settings.Secure.user_setup_search_engine bypasses the QPR2 package allow list so Intelligent Search always qualifies as the system search engine.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "• At a Glance: Configuring the default search engine on Android 17 QPR2 and newer will not affect At a Glance sports and finance integrations; however, configuring via ADB command will still bypass these integrations.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Security,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Text(
                                                text = hardwareInfo.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = hardwareInfo.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Device: ${hardwareInfo.deviceDisplayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Hardware Security: ${hardwareInfo.chipName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "TEE Architecture: ${hardwareInfo.teeName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "SoC Platform: ${hardwareInfo.socDisplayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): " + when {
                                                    attestationResult == null -> "Verifying..."
                                                    attestationResult?.isHardwareAttested == true -> "Hardware Verified ✓"
                                                    attestationResult?.securityLevel == com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.STRONGBOX -> "StrongBox Verified ✓"
                                                    attestationResult?.securityLevel == com.pixel.intelligentsearch.core.security.HardwareSecurityLevel.TEE -> "TEE Backed (TrustZone)"
                                                    else -> "Software Backed"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (attestationResult?.isHardwareAttested == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Certificate Chain Depth: ${attestationResult?.certificateCount ?: "..."} certificates",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                BatteryAndMemoryDiagnosticsPage(context = context)
                            }
                        }
                    }
                },
                confirmButton = {
                    when (pagerState.currentPage) {
                        0 -> {
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                }) {
                                    Text("Security Info")
                                }
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell settings put secure selected_search_engine com.pixel.intelligentsearch")
                                    clipboard?.setPrimaryClip(clip)
                                    com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context).click(view)
                                    Toast.makeText(context, "ADB command copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Copy Command")
                                }
                            }
                        }
                        1 -> {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }) {
                                Text("Diagnostics")
                            }
                        }
                        else -> {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }) {
                                Text("Developer Note")
                            }
                        }
                    }
                },
                dismissButton = when (pagerState.currentPage) {
                    1 -> {
                        {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }) {
                                Text("Developer Note")
                            }
                        }
                    }
                    2 -> {
                        {
                            TextButton(onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }) {
                                Text("Security Info")
                            }
                        }
                    }
                    else -> null
                }
            )
        }
        
                Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
                .graphicsLayer { alpha = if (showTutorial) 0f else 1f },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            SettingsCard {
                SettingsRow(
                    title = "Appearance",
                    subtitle = "Theme, Wallpaper, Material Design Layouts.",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Appearance) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Search Shortcuts",
                    subtitle = "Apps, Contacts, Files, Etc.",
                    icon = Icons.Outlined.SavedSearch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchSources) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Search Behavior",
                    subtitle = "Custom Search Overlay and Display Settings.",
                    icon = Icons.Outlined.Tune,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.SearchBehavior) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Widget Customization",
                    subtitle = "Customize Widget Colors, Themes, and Actions.",
                    icon = Icons.Outlined.Widgets,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.WidgetCustomization) },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Launch Portal",
                    subtitle = "Quick Search Tile and App Shortcuts.",
                    icon = Icons.Outlined.RocketLaunch,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.LaunchPortal) },
                    showDivider = false,
                )
            }

            SettingsCard {
                SettingsRow(
                    title = "Default Digital Assistant",
                    subtitle = "Manage Android Assistant Settings.",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                            // Fallback if the specific intent is not available
                            val fallbackIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                android.util.Log.w("IntelligentSearch", "Unexpected error", e2)
                            }
                        }
                    },
                    showDivider = true,
                )
                SettingsRow(
                    title = "Google Activity",
                    subtitle = "View and Manage Your Google Activity.",
                    icon = Icons.Outlined.History,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Throwable) {}
                    },
                    showDivider = true,
                )
                val rawDebugUnlocked by rememberSetting(SettingsManager.DEBUG_UNLOCKED)
                val isDebugUnlocked = com.pixel.intelligentsearch.BuildConfig.DEBUG && rawDebugUnlocked
                val searchEngine = prefs[SettingsManager.SEARCH_ENGINE]
                var showTorBrowserSettingsDialog by remember { mutableStateOf(false) }
                val browserHistorySubtitle = when (searchEngine) {
                    "Google" -> "View Your Chrome and Google History."
                    "Bing" -> "View Your Bing History."
                    "DuckDuckGo" -> "Open Your DuckDuckGo App History."
                    "Tor Project", "Tor Browser" -> "Open Tor Browser."
                    else -> "View Your $searchEngine History."
                }
                SettingsRow(
                    title = "Browser History",
                    subtitle = browserHistorySubtitle,
                    icon = Icons.Outlined.TravelExplore,
                    onClick = {
                        when (searchEngine) {
                            "Bing" -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.bing.com/profile/history")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                                }
                            }
                            "DuckDuckGo" -> {
                                try {
                                    val ddgIntent = context.packageManager.getLaunchIntentForPackage("com.duckduckgo.mobile.android")
                                    val intent = ddgIntent?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                                }
                            }
                            "Tor Project", "Tor Browser" -> {
                                try {
                                    val torIntent = context.packageManager.getLaunchIntentForPackage("org.torproject.torbrowser")
                                        ?: context.packageManager.getLaunchIntentForPackage("org.torproject.torbrowser_alpha")
                                    if (torIntent != null) {
                                        context.startActivity(torIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                    } else {
                                        showTorBrowserSettingsDialog = true
                                    }
                                } catch (e: Exception) {
                                    showTorBrowserSettingsDialog = true
                                }
                            }
                            else -> {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myactivity.google.com/myactivity?product=6")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Unexpected error", e)
                                }
                            }
                        }
                    },
                    showDivider = true,
                )

                if (showTorBrowserSettingsDialog) {
                    TorBrowserInstallDialog(
                        onDismiss = { showTorBrowserSettingsDialog = false }
                    )
                }
                SettingsRow(
                    title = "Encrypted Backup",
                    subtitle = "Import, Export, and Restore Backup App Data.",
                    icon = Icons.Outlined.Security,
                    onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.BackupRestore) },
                    showDivider = isDebugUnlocked
                )
                
                if (isDebugUnlocked) {
                    SettingsRow(
                        title = "Debug",
                        subtitle = "Developer Tools and Experiments.",
                        icon = Icons.Outlined.BugReport,
                        onClick = { onNavigate(com.pixel.intelligentsearch.core.navigation.Route.Debug) },
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val containerWidth = maxWidth
                val density = LocalDensity.current
                val fontScale = density.fontScale.coerceIn(0.85f, 1.5f)

                val textTargetWidth = (222.dp * (if (fontScale > 1f) (fontScale * 0.9f) else 1f)).coerceIn(205.dp, 255.dp)
                val remainingForPlayer = containerWidth - textTargetWidth - 12.dp
                val playerWidth = remainingForPlayer.coerceIn(80.dp, 160.dp)
                val playerHeight = playerWidth * (200f / 160f)

                val baseScale = (containerWidth / 390.dp).coerceIn(0.82f, 1.0f) / fontScale.coerceAtLeast(1.0f)
                val titleFontSize = (18.8f * baseScale).coerceIn(15.0f, 18.8f).sp
                val titleLineHeight = (titleFontSize.value * 1.28f).sp
                val subtextFontSize = (13.5f * baseScale).coerceIn(11.0f, 13.5f).sp
                val subtextLineHeight = (subtextFontSize.value * 1.36f).sp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BugdroidPlayer(
                        player = exoPlayer,
                        modifier = Modifier
                            .width(playerWidth)
                            .height(playerHeight),
                        scaleFactor = 0.69f
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Thank you for using\nIntelligent\u00A0Search.",
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = titleLineHeight,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Text(
                            text = "If you need support please email me at:\nsupport.ngdesigns@gmail.com",
                            fontSize = subtextFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = subtextLineHeight,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            maxLines = 2,
                            modifier = Modifier.bouncyClickable {
                                try {
                                    uriHandler.openUri("mailto:support.ngdesigns@gmail.com")
                                } catch (e: Exception) {
                                    // No mail app installed: copy the address instead of crashing.
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Support email", "support.ngdesigns@gmail.com"))
                                    android.widget.Toast.makeText(context, "No email app found. Address copied.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
