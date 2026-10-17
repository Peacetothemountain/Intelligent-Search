package com.pixel.intelligentsearch.feature.search

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
    var hasStartedTyping by s.ui::hasStartedTyping
    val context = LocalContext.current

    if (settingsState.quickSearchHorizontal) {
        val pillPackages by remember {
            derivedStateOf {
                // Chosen pills are shown as listed; recent apps are capped by the shortcut count.
                if (settingsState.contextAwareQuickApps) {
                    uiState.recentApps.map { it.packageName }.distinct().take(settingsState.shortcutResultsCount)
                } else {
                    settingsState.searchPills.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
                }
            }
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
                        val appIconState = remember(packageName, settingsState.activeIconPack) {
                            mutableStateOf<AppIconResult?>(peekThemedAppIcon(packageName, settingsState.activeIconPack))
                        }
                        val appNameState = remember(packageName) { mutableStateOf(getAppName(context, packageName)) }
                        LaunchedEffect(packageName, settingsState.activeIconPack) {
                            if (appIconState.value == null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val icon = getThemedAppIcon(context, packageName, activePackOverride = settingsState.activeIconPack)
                                    val name = getAppName(context, packageName)
                                    appIconState.value = icon
                                    appNameState.value = name
                                }
                            }
                        }
                        val appIcon = appIconState.value
                        val appName = appNameState.value
                        if (appIcon != null) {
                            SearchPill(iconBitmap = appIcon, title = appName, scale = dynamicScale) {
                                if (uiState.query.isNotEmpty()) viewModel.addSearchHistory(uiState.query)

                                val searchStr = uiState.query
                                if (searchStr.isEmpty()) {
                                    performLaunch(packageName, null, null)
                                } else {
                                    val intent = quickAppSearchIntent(context, packageName, searchStr)
                                    if (intent != null) {
                                        hasStartedTyping = false
                                        launchSafeIntent(context, intent)
                                    }
                                }
                            }
                        }
                    }
        }
    }
}
