package com.pixel.intelligentsearch.feature.search

import androidx.core.net.toUri
import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.AppItem
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import androidx.compose.runtime.Composable

/** The Best Match card at the top of the results: the most likely app, contact or file, with its quick actions. */
@Composable
internal fun OverlayBestMatchCard(s: OverlayScope, bestMatch: Any) {
    val settingsState = s.settingsState
    val performAppLaunch = s.performAppLaunch
    val launchIcons = s.launchIcons
    var hasStartedTyping by s.ui::hasStartedTyping
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Expressive "BEST MATCH" Pill & "Press Enter to Open" Action Hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BEST MATCH",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlex,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Press ↵ to open",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GoogleSansFlex
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val match = bestMatch) {
                is ContactItem -> Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                hasStartedTyping = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                launchSafeIntent(context, ContactActions.openIntent(match, settingsState.contactDirectCall))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(match, 48.dp) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                match.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GoogleSansFlex
                            )
                            Text(
                                match.phoneNumber,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontFamily = GoogleSansFlex
                            )
                        }
                        val isDirectCall = settingsState.contactDirectCall
                        Icon(
                            imageVector = if (isDirectCall) Icons.Default.Call else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isDirectCall) "Call" else "Open",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    val contactActions = remember(match, s.contactActionTargets, settingsState.contactActions) {
                        ContactActions.forContact(match, s.contactActionTargets, settingsState.contactActions)
                    }
                    if (contactActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ContactActionChips(contactActions, settingsState.activeIconPack) { hasStartedTyping = false }
                    }
                }
                is AppItem -> {
                    // Another profile's copy keeps its own (badged) icon and has no quick actions: those would open
                    // the personal app, putting the event or alarm in the wrong profile.
                    val otherProfile = match.userHandle != null && match.userHandle != android.os.Process.myUserHandle()
                    val appIconState = remember(match.packageName, settingsState.activeIconPack, otherProfile) {
                        mutableStateOf<AppIconResult?>(if (otherProfile) null else peekThemedAppIcon(match.packageName, settingsState.activeIconPack))
                    }
                    LaunchedEffect(match.packageName, settingsState.activeIconPack, otherProfile) {
                        if (appIconState.value == null && !otherProfile) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val icon = getThemedAppIcon(context, match.packageName, activePackOverride = settingsState.activeIconPack)
                                appIconState.value = icon
                            }
                        }
                    }
                    val appIcon = appIconState.value
                    // Rasterised only while the themed icon loads, and at the size shown (see AppGridItem).
                    val iconPx = with(LocalDensity.current) { 52.dp.roundToPx() }
                    val fallbackBitmap = if (appIcon == null) {
                        remember(match.packageName, iconPx) {
                            runCatching { match.icon.toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()
                        }
                    } else null

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable { performAppLaunch(match, LaunchSource.tap(LaunchIconKeys.BEST_MATCH)) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon.bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp).launchIcon(launchIcons, LaunchIconKeys.BEST_MATCH),
                                    colorFilter = if (appIcon.isMonochrome) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null
                                )
                            } else if (fallbackBitmap != null) {
                                Image(bitmap = fallbackBitmap, contentDescription = null, modifier = Modifier.size(52.dp).launchIcon(launchIcons, LaunchIconKeys.BEST_MATCH))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    match.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontFamily = GoogleSansFlex,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Application",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSansFlex
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (match.actions.isNotEmpty() && !otherProfile) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                match.actions.forEach { action ->
                                    AssistChip(
                                        onClick = {
                                            hasStartedTyping = false
                                            val intent = Intent(action.action)
                                            if (action.dataUri != null) intent.data = action.dataUri.toUri()
                                            intent.setPackage(match.packageName)
                                            if (!launchSafeIntent(context, intent)) {
                                                intent.setPackage(null)
                                                launchSafeIntent(context, intent)
                                            }
                                        },
                                        label = { Text(action.title, color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = GoogleSansFlex) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                        border = null,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                is FileItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                hasStartedTyping = false
                                val intent = buildOpenFileIntent(match.uri, match.mimeType)
                                if (!launchSafeIntent(context, intent)) {
                                    intent.setPackage(null)
                                    launchSafeIntent(context, intent)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settingsState.filesThumbnails) {
                            FileIconThumbnail(match.uri, match.mimeType, size = 48.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                match.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontFamily = GoogleSansFlex,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Local Document",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = GoogleSansFlex
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
