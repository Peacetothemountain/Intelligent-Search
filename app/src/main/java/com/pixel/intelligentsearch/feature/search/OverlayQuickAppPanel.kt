package com.pixel.intelligentsearch.feature.search

import android.content.Context
import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.core.ui.getAppName
import com.pixel.intelligentsearch.core.ui.AppIconResult
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixel.intelligentsearch.core.data.*

/** The row of quick-app pills under the search bar; each searches its app for the current query. */
@Composable
internal fun OverlayQuickAppPanel(s: OverlayScope) {
    val viewModel = s.viewModel
    val uiState = s.uiState
    val settingsState = s.settingsState
    val performLaunch = s.performLaunch
    val launchIcons = s.launchIcons
    val appLaunch = s.appLaunch
    var hasStartedTyping by s.ui::hasStartedTyping
    val context = LocalContext.current

    if (settingsState.quickSearchHorizontal) {
        // Keyed on plain scope values (not snapshot state), so it follows recent apps and settings as they change.
        val pillPackages = remember(settingsState.contextAwareQuickApps, settingsState.searchPills, settingsState.shortcutResultsCount, uiState.recentApps) {
            // Chosen pills are shown as listed; recent apps are capped by the shortcut count.
            if (settingsState.contextAwareQuickApps) {
                uiState.recentApps.map { it.packageName }.distinct().take(settingsState.shortcutResultsCount)
            } else {
                settingsState.searchPills.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
            }
        }
        // Names the overlay already has for its apps, so most pills never ask PackageManager for one.
        val knownNames = remember(uiState.recentApps, uiState.allApps) {
            buildMap { for (app in uiState.recentApps + uiState.allApps) putIfAbsent(app.packageName, app.name) }
        }
        val dynamicScale = if (pillPackages.size > 6) (6f / pillPackages.size.toFloat()).coerceIn(0.6f, 1f) else 1f
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uniqueItems(pillPackages, key = { pkg -> "pill_$pkg" }, contentType = { "pill" }) { packageName ->
                        val iconKey = LaunchIconKeys.pill(packageName)
                        QuickAppPill(
                            packageName, settingsState.activeIconPack, dynamicScale, knownName = knownNames[packageName],
                            launchIcon = LaunchIconTarget(launchIcons, iconKey)
                        ) {
                            if (uiState.query.isNotEmpty()) viewModel.addSearchHistory(uiState.query)

                            val searchStr = uiState.query
                            if (searchStr.isEmpty()) {
                                performLaunch(packageName, null, null, LaunchSource.tap(iconKey))
                            } else {
                                val intent = quickAppSearchIntent(context, packageName, searchStr)
                                if (intent != null) {
                                    hasStartedTyping = false
                                    // The app opens on the query out of its pill's icon, as when it is just opened.
                                    val launch = appLaunch(LaunchSource.tap(iconKey))
                                    launch?.sourceBounds?.let { intent.sourceBounds = it }
                                    launchSafeIntent(context, intent, launch?.options)
                                }
                            }
                        }
                    }
        }
    }
}

/**
 * One quick-app pill: [packageName]'s themed icon and name. The name is [knownName], from the app lists the overlay
 * already holds, when it has one; otherwise [appLabel] looks it up off the main thread like the icon, since a
 * PackageManager call during composition stalls the frame. The pill shows once it has both.
 */
@Composable
internal fun QuickAppPill(
    packageName: String,
    activeIconPack: String,
    scale: Float,
    knownName: String? = null,
    appLabel: (Context, String) -> String = ::getAppName,
    launchIcon: LaunchIconTarget? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appIconState = remember(packageName, activeIconPack) {
        mutableStateOf<AppIconResult?>(peekThemedAppIcon(packageName, activeIconPack))
    }
    val appNameState = remember(packageName) { mutableStateOf(knownName) }
    LaunchedEffect(packageName, activeIconPack) {
        if (appIconState.value == null || appNameState.value == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (appIconState.value == null) {
                    appIconState.value = getThemedAppIcon(context, packageName, activePackOverride = activeIconPack)
                }
                if (appNameState.value == null) appNameState.value = appLabel(context, packageName)
            }
        }
    }
    val appIcon = appIconState.value
    val appName = appNameState.value
    if (appIcon != null && appName != null) {
        SearchPill(iconBitmap = appIcon, title = appName, scale = scale, launchIcon = launchIcon, onClick = onClick)
    }
}
