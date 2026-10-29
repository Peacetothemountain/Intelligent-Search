package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.rememberAppIcon
import com.pixel.intelligentsearch.core.ui.AppIconImage
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import com.pixel.intelligentsearch.core.theme.isDark
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex

/**
 * An app in the app row: its icon and name. The icon is marked as [launchIcon], so the app opens out of it. A long
 * press calls [onLongClick] (the result's menu), which TalkBack offers as "More options".
 */
@Composable
fun AppGridItem(
    app: AppItem,
    iconPack: String?,
    modifier: Modifier = Modifier,
    launchIcon: LaunchIconTarget? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    // Another profile's copy keeps its own (badged) icon: themed icons are looked up by package, i.e. the personal app.
    val otherProfile = app.userHandle != null && app.userHandle != android.os.Process.myUserHandle()
    val label = if (otherProfile) "${app.name}, Work" else app.name
    val iconPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val appIcon = rememberAppIcon(app.packageName, iconPack, otherProfile, fallback = app.icon, sizePx = iconPx)

    Column(
        modifier = modifier
            .width(80.dp)
            .moreOptions(onLongClick)
            .bouncyClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(appIcon, Modifier.size(48.dp).launchIcon(launchIcon), contentDescription = label)
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
        Image(painter = themedPainterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
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

/**
 * [painterResource] resolved in the overlay's own light or dark, not the phone's. The shortcut icons' colors are
 * night-qualified resources, so with App Theme set against the phone's mode they'd come out in the other theme's
 * tones: dark blue on the dark overlay.
 */
@Composable
private fun themedPainterResource(id: Int): Painter {
    val dark = MaterialTheme.colorScheme.isDark
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val vector = remember(context, base, dark, id) {
        val config = Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val themed = context.createConfigurationContext(config)
        ImageVector.vectorResource(themed.theme, themed.resources, id)
    }
    return rememberVectorPainter(vector)
}
