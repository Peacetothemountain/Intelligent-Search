package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.core.data.AppItem
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex

@Composable
fun AppGridItem(app: AppItem, onClick: () -> Unit) {
    val context = LocalContext.current
    // Another profile's copy keeps its own (badged) icon: themed icons are looked up by package, i.e. the personal app.
    val otherProfile = app.userHandle != null && app.userHandle != android.os.Process.myUserHandle()
    val label = if (otherProfile) "${app.name}, Work" else app.name
    val appIconState = remember(app.packageName, otherProfile) {
        mutableStateOf<AppIconResult?>(if (otherProfile) null else peekThemedAppIcon(app.packageName))
    }
    LaunchedEffect(app.packageName, otherProfile) {
        if (appIconState.value == null && !otherProfile) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val icon = getThemedAppIcon(context, app.packageName)
                if (icon != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appIconState.value = icon
                    }
                }
            }
        }
    }
    val appIcon = appIconState.value
    // The app's own icon fills the slot only while the themed one loads, so it is rasterised only then, and at the
    // size shown rather than full size: on the main thread, for every result, the full-size copy was wasted work.
    // It stays on the main thread so a cold cache never leaves the slot blank for a frame.
    val iconPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val fallbackBitmap = if (appIcon == null) {
        remember(app.packageName, iconPx) {
            runCatching { app.icon.toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()
        }
    } else null

    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon.bitmap,
                contentDescription = label,
                modifier = Modifier.size(48.dp),
                colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
            )
        } else if (fallbackBitmap != null) {
            Image(
                bitmap = fallbackBitmap,
                contentDescription = label,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun SearchSettingsItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Search",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSansFlex
        )
    }
}

@Composable
fun ShortcutRow(iconRes: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GoogleSansFlex
        )
    }
}
