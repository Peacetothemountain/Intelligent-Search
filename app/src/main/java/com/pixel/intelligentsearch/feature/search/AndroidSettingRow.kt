package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.feature.settings.expressiveRowClickable

/** Marks an App Info row's app icon, for tests. */
internal const val ANDROID_SETTING_APP_ICON_TAG = "android_setting_app_icon"

/** One Android Settings result, laid out like a contact or file row; TalkBack reads it as e.g. "Battery settings". */
@Composable
internal fun AndroidSettingRow(result: AndroidSettingResult, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = result.spokenLabel }
            .moreOptions(onLongClick)
            .expressiveRowClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // An App Info page shows the app's own icon, as the app results draw it; a settings page its symbol.
        val app = result.app
        val appIcon = remember(app) { app?.let { runCatching { it.icon.toBitmap().asImageBitmap() }.getOrNull() } }
        if (appIcon != null) {
            Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(40.dp).testTag(ANDROID_SETTING_APP_ICON_TAG))
        } else {
            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = androidSettingIcon(result.iconType), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = GoogleSansFlex, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The Material Symbols icon for a catalogue page's icon type. */
internal fun androidSettingIcon(iconType: String): ImageVector = when (iconType) {
    "wifi" -> Icons.Outlined.Wifi
    "network" -> Icons.Outlined.Language
    "mobile_network" -> Icons.Outlined.SignalCellularAlt
    "data_usage" -> Icons.Outlined.DataUsage
    "airplane" -> Icons.Outlined.AirplanemodeActive
    "bluetooth" -> Icons.Outlined.Bluetooth
    "nfc" -> Icons.Outlined.Nfc
    "contactless" -> Icons.Outlined.Contactless
    "cast" -> Icons.Outlined.Cast
    "vpn" -> Icons.Outlined.VpnKey
    "battery" -> Icons.Outlined.BatteryStd
    "battery_saver" -> Icons.Outlined.BatterySaver
    "display" -> Icons.Outlined.BrightnessMedium
    "night_light" -> Icons.Outlined.Nightlight
    "auto_rotate" -> Icons.Outlined.ScreenRotation
    "screen_saver" -> Icons.Outlined.Wallpaper
    "sound" -> Icons.AutoMirrored.Outlined.VolumeUp
    "dnd" -> Icons.Outlined.DoNotDisturbOn
    "notifications" -> Icons.Outlined.Notifications
    "notification_access" -> Icons.Outlined.NotificationsActive
    "apps" -> Icons.Outlined.Apps
    "default_apps" -> Icons.Outlined.AppSettingsAlt
    "home_app" -> Icons.Outlined.Home
    "overlay" -> Icons.Outlined.Layers
    "write_settings" -> Icons.Outlined.Tune
    "unknown_apps" -> Icons.Outlined.InstallMobile
    "all_files_access" -> Icons.Outlined.FolderOpen
    "usage_access" -> Icons.Outlined.Insights
    "storage" -> Icons.Outlined.Storage
    "location" -> Icons.Outlined.LocationOn
    "privacy" -> Icons.Outlined.PrivacyTip
    "security" -> Icons.Outlined.Security
    "passwords" -> Icons.Outlined.Password
    "accounts" -> Icons.Outlined.AccountCircle
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "accessibility" -> Icons.Outlined.Accessibility
    "captions" -> Icons.Outlined.ClosedCaption
    "languages" -> Icons.Outlined.Translate
    "regional" -> Icons.Outlined.Public
    "keyboard" -> Icons.Outlined.Keyboard
    "dictionary" -> Icons.Outlined.Spellcheck
    "voice_input" -> Icons.Outlined.KeyboardVoice
    "tts" -> Icons.Outlined.RecordVoiceOver
    "system_update" -> Icons.Outlined.SystemUpdate
    "wallpaper" -> Icons.Outlined.Palette
    "date_time" -> Icons.Outlined.Schedule
    "printing" -> Icons.Outlined.Print
    "developer" -> Icons.Outlined.DeveloperMode
    "about" -> Icons.Outlined.PhoneAndroid
    "satellite" -> Icons.Outlined.SatelliteAlt
    "app_info" -> Icons.Outlined.Info
    else -> Icons.Outlined.Settings
}

/**
 * Opens a settings result, or says it couldn't. An app in another profile (work) opens its own App Info through
 * LauncherApps; the plain intent would show the personal profile's copy, so it is never the fallback for one.
 */
internal fun openAndroidSetting(context: Context, result: AndroidSettingResult) {
    val app = result.app
    val user = app?.userHandle
    val opened = if (app != null && user != null && user != android.os.Process.myUserHandle()) {
        val activity = app.activityName
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        try {
            if (activity != null && launcherApps != null) {
                launcherApps.startAppDetailsActivity(ComponentName(app.packageName, activity), user, null, null)
                true
            } else false
        } catch (e: RuntimeException) {
            android.util.Log.w("IntelligentSearch", "Could not open App Info in its profile", e)
            false
        }
    } else {
        // A copy: launching adds flags, and the result's intent is reused while the query stands.
        launchSafeIntent(context, Intent(result.intent))
    }
    if (!opened) Toast.makeText(context, "Couldn’t open ${result.title}", Toast.LENGTH_SHORT).show()
}
