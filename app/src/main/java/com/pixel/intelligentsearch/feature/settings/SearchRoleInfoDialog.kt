package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.di.appEntryPoint
import androidx.compose.ui.draw.clip
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*

/**
 * The "Developer Note" dialog of the main settings page: how the Android 17 QPR2 search engine role works, the
 * device's current state, and the one-time ADB steps that put this app on the device's search engine list.
 */
@Composable
internal fun SearchRoleInfoDialog(context: Context, onDismiss: () -> Unit) {
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
    // Read once per opening: a screen lock can't change while this dialog is in front.
    val deviceSecure = remember {
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)?.isDeviceSecure == true
    }
    val posture = remember(hardwareLevel, attestationResult, deviceSecure) {
        com.pixel.intelligentsearch.core.security.SecurityPosture.score(
            keyLevel = hardwareLevel,
            attestation = attestationResult,
            securityPatch = android.os.Build.VERSION.SECURITY_PATCH,
            today = java.time.LocalDate.now(),
            deviceSecure = deviceSecure
        )
    }
    val view = androidx.compose.ui.platform.LocalView.current

    AlertDialog(
        onDismissRequest = { onDismiss() },
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
                        1 -> "Battery & System Diagnostics"
                        else -> "Hardware & Security"
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
                                        text = "On Android 17 QPR2 and newer, the Android \"Search engine app\" setting decides which app powers your home screen search bar. Its picker only lists apps on your device's search engine list, so Intelligent Search has to be added to that list once (see Manual ADB Activation below) before it can be selected.",
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
                                val isListed = remember { com.pixel.intelligentsearch.core.system.SearchRoleManager.isListedAsSearchEngine(context) }
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
                                            text = if (isListed) {
                                                "Intelligent Search is on this device's search engine list. Select it in Android settings to activate it on your home screen:"
                                            } else {
                                                "Intelligent Search isn't on this device's search engine list yet, so it won't appear in the Android picker. Run the Manual ADB Activation commands below once, then select it here:"
                                            },
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
                                            Text(if (isListed) "Set as Default Search Engine" else "Open Search Engine Settings")
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
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ADB Commands", com.pixel.intelligentsearch.core.system.SearchRoleManager.ADB_ACTIVATION_COMMANDS))
                                                Toast.makeText(context, "ADB commands copied to clipboard.", Toast.LENGTH_SHORT).show()
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
                                        text = "Adds Intelligent Search to this device's search engine list and selects it, so it appears in the Android \"Search engine app\" picker and powers the home screen search bar. Needs Android 17 QPR2 or newer, USB debugging and a computer with ADB; run once:",
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
                                            // The same commands the copy button copies, one per line.
                                            com.pixel.intelligentsearch.core.system.SearchRoleManager.ADB_ACTIVATION_COMMANDS.lines().forEach { command ->
                                                Text(
                                                    command,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
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
                                        text = "• Search engine list: Android treats the app named in Settings.Secure.user_setup_search_engine as a listed search engine, so Intelligent Search stays selectable until that setting is cleared (for example by a factory reset), after which the ADB step is needed again.",
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
                        BatteryAndMemoryDiagnosticsPage(context = context)
                    }
                    else -> {
                        HardwareSecurityPage(hardwareInfo = hardwareInfo, attestationResult = attestationResult, posture = posture)
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
                            Text("Diagnostics")
                        }
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            // Both commands: selecting the app alone fails while it isn't on the search engine list.
                            val clip = android.content.ClipData.newPlainText("ADB Commands", com.pixel.intelligentsearch.core.system.SearchRoleManager.ADB_ACTIVATION_COMMANDS)
                            clipboard?.setPrimaryClip(clip)
                            com.pixel.intelligentsearch.core.haptics.TactileSonicEngine.get(context).click(view)
                            Toast.makeText(context, "ADB commands copied to clipboard.", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Copy Command")
                        }
                    }
                }
                1 -> {
                    TextButton(onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    }) {
                        Text("Security Info")
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
                        Text("Diagnostics")
                    }
                }
            }
            else -> null
        }
    )
}
