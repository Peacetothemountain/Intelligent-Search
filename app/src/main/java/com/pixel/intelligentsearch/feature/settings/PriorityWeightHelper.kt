package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.data.settings.SettingKey
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pixel.intelligentsearch.core.data.settings.get
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.semantics.*

// -----------------------------------------------------------------------------------------
// PRIORITY WEIGHT HELPER
// -----------------------------------------------------------------------------------------
object PriorityWeightHelper {
    data class PriorityLevel(val label: String, val weight: Int)

    val LEVELS = listOf(
        PriorityLevel("Low", 25),
        PriorityLevel("Medium", 50),
        PriorityLevel("High", 75),
        PriorityLevel("Very High", 100)
    )

    const val DEFAULT_WEIGHT = 50

    fun weightToLevelIndex(weight: Int): Int {
        var closestIdx = 1 // default to Medium
        var minDiff = Int.MAX_VALUE
        for (i in LEVELS.indices) {
            val diff = Math.abs(LEVELS[i].weight - weight)
            if (diff < minDiff) {
                minDiff = diff
                closestIdx = i
            }
        }
        return closestIdx
    }

    fun levelIndexToWeight(index: Int): Int {
        return LEVELS.getOrElse(index.coerceIn(0, LEVELS.size - 1)) { LEVELS[1] }.weight
    }

    fun levelLabel(index: Int): String {
        return LEVELS.getOrElse(index.coerceIn(0, LEVELS.size - 1)) { LEVELS[1] }.label
    }
}

/**
 * The "<label> Priority Weight" block shared by the App, Contact, File and Web search screens: title with the
 * current level and a reset button, a one-line explanation, and a four-step slider. The title uses the settings
 * row title style and wraps beside the reset button rather than pushing it out of the row.
 */
@Composable
fun PrioritySetting(key: SettingKey<Int>, label: String) {
    val context = LocalContext.current
    var weight by rememberSetting(key)
    val levelIndex = remember(weight) { PriorityWeightHelper.weightToLevelIndex(weight) }
    val levelLabel = PriorityWeightHelper.levelLabel(levelIndex)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label Priority Weight: $levelLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Material's own IconButton: the click haptic below is this button's one haptic.
            androidx.compose.material3.IconButton(
                onClick = {
                    performClickHaptic(context)
                    weight = PriorityWeightHelper.DEFAULT_WEIGHT
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset $label Priority Weight",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = "Adjust Ranking Priority of $label Search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Android17Slider(
            label = "$label priority weight",
            valueDescription = levelLabel,
            value = levelIndex.toFloat(),
            onValueChange = { newValue ->
                val idx = Math.round(newValue).coerceIn(0, PriorityWeightHelper.LEVELS.size - 1)
                weight = PriorityWeightHelper.levelIndexToWeight(idx)
            },
            valueRange = 0f..(PriorityWeightHelper.LEVELS.size - 1).toFloat(),
            steps = PriorityWeightHelper.LEVELS.size - 2,
            isSquiggly = false,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )
    }
}
