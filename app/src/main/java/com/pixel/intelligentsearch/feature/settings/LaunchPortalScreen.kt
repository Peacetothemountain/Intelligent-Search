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
import androidx.annotation.RequiresApi
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

private const val ADD_TILE_YOURSELF = "You can add it yourself: open Quick Settings, tap the pencil and drag Intelligent Search in."

/**
 * What to tell the user after asking Android to add the Search tile, from the request's [result] code. Android
 * shows its own dialog for a new tile, so adding it or turning it down needs no message; everything else does,
 * since otherwise the tap seems to do nothing.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun tileRequestMessage(result: Int): String? = when (result) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Search tile is already in Quick Settings"
    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND ->
        "Android only adds the tile while Intelligent Search is open. $ADD_TILE_YOURSELF"
    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS ->
        "Another request to add a tile is still open. $ADD_TILE_YOURSELF"
    StatusBarManager.TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE ->
        "This device can't add tiles for apps. $ADD_TILE_YOURSELF"
    else -> "Android couldn't add the tile. $ADD_TILE_YOURSELF"
}

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
                                            context.mainExecutor
                                        ) { result ->
                                            tileRequestMessage(result)?.let {
                                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                            }
                                        }
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
                            title = "Search from Selected Text",
                            subtitle = "Show Intelligent Search When You Select or Share Text.",
                            icon = Icons.Outlined.TextFields,
                            isChecked = fromSelectedText,
                            onCheckedChange = {
                                fromSelectedText = it
                                SelectedTextSearchActivity.syncWithSetting(context)
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
