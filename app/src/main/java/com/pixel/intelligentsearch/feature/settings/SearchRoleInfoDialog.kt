package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.di.appEntryPoint
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.expressive.matteShell
import androidx.compose.ui.draw.clip
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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

    // The app's own container rather than the stock dialog's: a settings card's colour, hairline and matte surface,
    // with the light behind it.
    val shellShape = com.pixel.intelligentsearch.core.ui.expressive.ExpressiveCornerTokens.ShapeExtraLarge
    val shellLook = com.pixel.intelligentsearch.core.ui.expressive.rememberMatteShellLook(lit = true)
    AlertDialog(
        onDismissRequest = { onDismiss() },
        modifier = Modifier.matteShell(shellLook, shellShape),
        shape = shellShape,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
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
                    0 -> DeveloperNotePage(context)
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

/** The Developer Note dialog's cards: the Diagnostics and Hardware & Security pages' own, matte with a hairline. */
private val NoteCardShape = RoundedCornerShape(16.dp)

@Composable
private fun noteCardBorder() = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

/**
 * The Developer Note page: how the search engine role works, whether this app holds it (and the way to it when it
 * doesn't), the ADB commands that list it, and notes on what it affects. Its cards are matte shells like the
 * dialog's other two pages ([DiagnosticsCard]) and every container in the app.
 */
@Composable
internal fun DeveloperNotePage(context: Context) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DiagnosticsCardSpacing)
    ) {
        DiagnosticsCard(icon = Icons.Outlined.TravelExplore, title = "Default Search Engine", accent = scheme.primary) {
            Text(
                text = "On Android 17 QPR2 and newer, the Android \"Search engine app\" setting decides which app powers your home screen search bar. Its picker only lists apps on your device's search engine list, so Intelligent Search has to be added to that list once (see Search Engine List below) before it can be selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        if (com.pixel.intelligentsearch.core.system.SearchRoleManager.isSearchRoleHeld(context)) {
            MatteShell(
                modifier = Modifier.fillMaxWidth(),
                shape = NoteCardShape,
                containerColor = scheme.primaryContainer,
                border = noteCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            com.pixel.intelligentsearch.core.system.SearchRoleManager.openDefaultSearchEngineSettings(context)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Intelligent Search is your active Default Search Engine.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onPrimaryContainer
                        )
                        Text(
                            text = "Natively active as your home search bar. Tap to manage in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            val isListed = remember { com.pixel.intelligentsearch.core.system.SearchRoleManager.isListedAsSearchEngine(context) }
            DiagnosticsCard(
                icon = Icons.Outlined.CheckCircle,
                title = "Search Engine List",
                accent = scheme.primary,
                trailing = {
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
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            ) {
                Text(
                    text = "The Intelligent Search widget is always there for you to use. To have Intelligent Search replace the Pixel Launcher's own search bar instead, turn on USB debugging, connect your phone to a computer with ADB and run these commands once (Android 17 QPR2 or newer):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                MatteShell(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    containerColor = scheme.surfaceContainerLowest,
                    border = null
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
                                color = scheme.primary
                            )
                        }
                    }
                }
                // Already listed (the commands were run before): picking it in Android's settings is all that's left.
                if (isListed) {
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

        DiagnosticsCard(icon = Icons.Outlined.Info, title = "Compatibility Note", accent = scheme.primary) {
            Text(
                text = "On Pixel phones, making Intelligent Search the Pixel Launcher's search bar removes the Sports and Finance cards from Google's At a Glance.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}
