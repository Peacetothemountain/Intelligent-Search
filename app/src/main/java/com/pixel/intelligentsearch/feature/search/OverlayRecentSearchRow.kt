package com.pixel.intelligentsearch.feature.search

import com.pixel.intelligentsearch.feature.settings.IconButton
import com.pixel.intelligentsearch.feature.settings.bouncyClickable
import com.pixel.intelligentsearch.core.haptics.rememberTactileSonicEngine
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.intelligentsearch.core.data.*
import com.pixel.intelligentsearch.core.theme.GoogleSansFlex
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyItemScope

/** One recent search: tap to search again, swipe to remove, or fill it into the search bar. */
@Composable
internal fun LazyItemScope.OverlayRecentSearchRow(s: OverlayScope, recentQuery: String) {
    val viewModel = s.viewModel
    val launchWebSearch = s.launchWebSearch
    var hasStartedTyping by s.ui::hasStartedTyping
    var textFieldValue by s.ui::textFieldValue
    val sensoryEngine = rememberTactileSonicEngine()
    val view = androidx.compose.ui.platform.LocalView.current
    val swipe = rememberSwipeToDismiss(sensoryEngine, view, threshold = 120f, fadeDistance = 500f, maxDragFade = 0.7f) { viewModel.removeSearchHistory(recentQuery) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(
                placementSpec = ExpressiveMotionTokens.Spatial.ExpressiveDefault(),
                fadeInSpec = ExpressiveMotionTokens.Effects.FastSpatial(),
                fadeOutSpec = ExpressiveMotionTokens.Effects.FastSpatial()
            )
            .swipeOffset(swipe)
            .swipeToDismiss(swipe, recentQuery)
    ) {
        val pillShape = RoundedCornerShape(28.dp)
        ResultMenuBox(s, ResultMenuTarget.History(recentQuery)) { openMenu ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f),
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
                            )
                        ),
                        shape = pillShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        shape = pillShape
                    )
                    .clip(pillShape)
                    .keyboardHighlight(s, ResultListKeys.recent(recentQuery), pillShape)
                    .moreOptions(openMenu)
                    .bouncyClickable(onLongClick = openMenu) { launchWebSearch(recentQuery) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = recentQuery,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontFamily = GoogleSansFlex,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { viewModel.removeSearchHistory(recentQuery) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        hasStartedTyping = true
                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                            text = recentQuery,
                            selection = androidx.compose.ui.text.TextRange(recentQuery.length)
                        )
                        viewModel.onQueryChanged(recentQuery)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NorthWest,
                        contentDescription = "Insert query",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
