package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.pixel.intelligentsearch.core.ui.TorBrowserInstallDialog
import com.pixel.intelligentsearch.App
import android.app.Activity
import android.content.Context
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    androidx.lifecycle.compose.LifecycleResumeEffect(exoPlayer) {
        exoPlayer?.play()
        onPauseOrDispose { exoPlayer?.pause() }
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

        if (showInfoDialog) SearchRoleInfoDialog(context = context, onDismiss = { showInfoDialog = false })
        
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
                val customEngineUrl = prefs[SettingsManager.CUSTOM_SEARCH_ENGINE_URL]
                // Resolved off the main thread (it asks PackageManager which browsers are installed and default).
                val historyDestination by produceState<com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.Destination?>(
                    initialValue = null, searchEngine, customEngineUrl
                ) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.forContext(context, searchEngine, customEngineUrl)
                    }
                }
                SettingsRow(
                    title = "Browser History",
                    subtitle = historyDestination?.subtitle ?: "View Your Search History.",
                    icon = Icons.Outlined.TravelExplore,
                    onClick = {
                        val resolver = com.pixel.intelligentsearch.core.search.BrowserHistoryResolver
                        val destination = historyDestination ?: resolver.forContext(context, searchEngine, customEngineUrl)
                        when (destination) {
                            com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.Destination.InstallTor -> showTorBrowserSettingsDialog = true
                            is com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.Destination.Unavailable ->
                                android.widget.Toast.makeText(context, destination.message, android.widget.Toast.LENGTH_LONG).show()
                            else -> {
                                (destination as? com.pixel.intelligentsearch.core.search.BrowserHistoryResolver.Destination.App)?.note?.let {
                                    android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
                                }
                                try {
                                    resolver.intentFor(context, destination)?.let(context::startActivity)
                                } catch (e: Exception) {
                                    android.util.Log.w("IntelligentSearch", "Could not open browser history", e)
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
                        clip = BugdroidClip.SETTINGS,
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
