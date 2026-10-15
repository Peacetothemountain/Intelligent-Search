package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.core.content.edit

import androidx.compose.ui.semantics.stateDescription

class TargetState {
    var rect by mutableStateOf<Rect?>(null)
}

object TutorialManager {
    const val TOTAL_STEPS = 5

    val targetStates = mutableStateMapOf<Int, TargetState>()
    
    fun getTargetState(step: Int): TargetState {
        return targetStates.getOrPut(step) { TargetState() }
    }

    fun clearTargetStates() {
        targetStates.clear()
    }

    fun completeTutorial(prefs: SharedPreferences) {
        prefs.edit().put(SettingsManager.TUTORIAL_COMPLETED, true).apply()
        clearTargetStates()
    }

    fun resetForForceTutorial(prefs: SharedPreferences) {
        prefs.edit()
            .put(SettingsManager.TUTORIAL_STEP, 0)
            .put(SettingsManager.TUTORIAL_COMPLETED, false)
            .apply()
        clearTargetStates()
    }

    /** Plays until completed; with debug unlocked, "force tutorial" replays it. */
    fun isTutorialActive(prefs: SharedPreferences): Boolean =
        (prefs[SettingsManager.DEBUG_UNLOCKED] && prefs[SettingsManager.FORCE_TUTORIAL]) ||
            !prefs[SettingsManager.TUTORIAL_COMPLETED]
}

/**
 * Attaches spotlight targeting and TalkBack accessibility metadata to a tutorial target element.
 * Skips layout coordinate tracking and semantics when tutorial is completed to prevent recomposition/layout
 * overhead and preserve original accessibility descriptions.
 */
fun Modifier.tutorialTarget(step: Int, prefs: SharedPreferences? = null): Modifier = this.composed {
    val isActive = prefs?.let { TutorialManager.isTutorialActive(it) } ?: true
    if (!isActive) return@composed Modifier

    DisposableEffect(step) {
        onDispose {
            TutorialManager.targetStates[step]?.rect = null
        }
    }
    Modifier
        .onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) {
                TutorialManager.getTargetState(step).rect = coordinates.boundsInWindow()
            }
        }
        .semantics {
            stateDescription = "Highlighted in tutorial step ${step + 1}"
        }
}

