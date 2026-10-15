package com.pixel.intelligentsearch.feature.settings

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
    const val KEY_TUTORIAL_STEP = "tutorial_step"
    const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
    const val KEY_DEBUG_UNLOCKED = "debug_unlocked"
    const val KEY_FORCE_TUTORIAL = "force_tutorial"

    val targetStates = mutableStateMapOf<Int, TargetState>()
    
    fun getTargetState(step: Int): TargetState {
        return targetStates.getOrPut(step) { TargetState() }
    }

    fun clearTargetStates() {
        targetStates.clear()
    }

    fun getStep(prefs: SharedPreferences): Int {
        return prefs.getInt(KEY_TUTORIAL_STEP, 0)
    }

    fun setStep(prefs: SharedPreferences, step: Int) {
        prefs.edit { putInt(KEY_TUTORIAL_STEP, step) }
    }
    
    fun completeTutorial(prefs: SharedPreferences) {
        prefs.edit { putBoolean(KEY_TUTORIAL_COMPLETED, true) }
        clearTargetStates()
    }

    fun resetForForceTutorial(prefs: SharedPreferences) {
        prefs.edit {
            putInt(KEY_TUTORIAL_STEP, 0)
            putBoolean(KEY_TUTORIAL_COMPLETED, false)
        }
        clearTargetStates()
    }
    
    fun isTutorialActive(prefs: SharedPreferences): Boolean {
        val debugUnlocked = prefs.getBoolean(KEY_DEBUG_UNLOCKED, false)
        if (debugUnlocked && prefs.getBoolean(KEY_FORCE_TUTORIAL, false)) {
            return true
        }
        
        // Normal behavior: only play if not completed.
        return !prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }
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

