package com.pixel.intelligentsearch.feature.settings

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
