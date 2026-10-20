package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.MainActivity
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.feature.search.SelectedTextSearchActivity
import com.pixel.intelligentsearch.feature.widget.SearchTileService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPortalScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val morphAnimationEnabled by rememberSetting(SettingsManager.MORPH_ANIMATION_ENABLED) {}
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Launch Portal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = oneLineAutoSize(MaterialTheme.typography.headlineSmall)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCard {
                        SettingsRow(
                            title = "Search Tile",
                            subtitle = "Add to Quick Settings Notification Panel.",
                            icon = Icons.Outlined.ViewAgenda,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    try {
                                        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                        val componentName = ComponentName(context, SearchTileService::class.java)
                                        val icon = Icon.createWithResource(context, R.drawable.ic_search_lens_expressive)
                                        statusBarManager?.requestAddTileService(
                                            componentName,
                                            "Intelligent Search",
                                            icon,
                                            context.mainExecutor,
                                            { _ -> }
                                        )
                                    } catch (e: Exception) {
                                        Log.w("IntelligentSearch", "Unexpected error", e)
                                    }
                                } else {
                                    Toast.makeText(context, "Please add the tile manually from your notification shade.", Toast.LENGTH_LONG).show()
                                }
                            },
                            showDivider = true
                        )
                        
                        SettingsRow(
                            title = "Home Screen Shortcut",
                            subtitle = "Add App Icon to Home Screen.",
                            icon = Icons.Outlined.AddHome,
                            onClick = {
                                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                                    try {
                                        val pinShortcutInfo = ShortcutInfo.Builder(context, "intelligent_search_main")
                                            .setShortLabel("Search")
                                            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                                            .setIntent(
                                                Intent(context, MainActivity::class.java).apply {
                                                    action = Intent.ACTION_MAIN
                                                }
                                            )
                                            .build()
                                        val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(pinShortcutInfo)
                                        val successCallback = PendingIntent.getBroadcast(
                                            context, 0,
                                            pinnedShortcutCallbackIntent,
                                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                        )
                                        shortcutManager.requestPinShortcut(pinShortcutInfo, successCallback.intentSender)
                                    } catch (e: Exception) {
                                        Log.w("IntelligentSearch", "Unexpected error", e)
                                    }
                                } else {
                                    Toast.makeText(context, "Pinning shortcuts not supported on this device.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            showDivider = true
                        )

                        var fromSelectedText by rememberSetting(SettingsManager.SEARCH_FROM_SELECTED_TEXT)
                        SettingsRowToggle(
                            title = "Search From Selected Text",
                            subtitle = "Show Intelligent Search When You Select or Share Text.",
                            icon = Icons.Outlined.TextFields,
                            isChecked = fromSelectedText,
                            onCheckedChange = {
                                fromSelectedText = it
                                SelectedTextSearchActivity.setEnabled(context, it)
                            },
                            showDivider = false
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) 
                        .clipToBounds() 
                ) {
                    if (morphAnimationEnabled) {
                        MaterialMorphAnimation(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
