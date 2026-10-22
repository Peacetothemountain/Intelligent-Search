package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.core.ui.rememberAppIcon
import com.pixel.intelligentsearch.core.system.launchSafeIntent
import android.content.Context
import com.pixel.intelligentsearch.core.ui.uniqueItems
import com.pixel.intelligentsearch.core.ui.getAppName
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

/**
 * The row of quick-app pills beside the search bar; each searches its app for the current query. Shown while the
 * Quick App Panel setting is on, with up to Max Quick Panel Apps pills (see [quickPanelApps]).
 */
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
            quickPanelApps(
                settingsState.contextAwareQuickApps,
                settingsState.searchPills,
                uiState.recentApps.map { it.packageName },
                settingsState.shortcutResultsCount
            )
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
 * The packages the quick-app panel shows: the recent apps with Context Aware Quick Apps on, otherwise the chosen
 * Quick Launch apps ([chosenApps], comma separated) in their order. Either way at most [maxApps], the Max Quick Panel
 * Apps setting; past six the pills shrink to fit, and the row scrolls for the rest.
 */
internal fun quickPanelApps(contextAware: Boolean, chosenApps: String, recentApps: List<String>, maxApps: Int): List<String> {
    val apps = if (contextAware) recentApps else chosenApps.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return apps.distinct().take(maxApps)
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
    val appIcon = rememberAppIcon(packageName, activeIconPack).themed
    val appNameState = remember(packageName) { mutableStateOf(knownName) }
    LaunchedEffect(packageName) {
        if (appNameState.value == null) {
            appNameState.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { appLabel(context, packageName) }
        }
    }
    val appName = appNameState.value
    if (appIcon != null && appName != null) {
        SearchPill(iconBitmap = appIcon, title = appName, scale = scale, launchIcon = launchIcon, onClick = onClick)
    }
}
