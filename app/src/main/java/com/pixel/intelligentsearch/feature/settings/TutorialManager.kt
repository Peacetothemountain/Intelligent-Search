package com.pixel.intelligentsearch.feature.settings

import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.SettingsManager
import android.content.SharedPreferences
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.edit

class TargetState {
    var rect by mutableStateOf<Rect?>(null)
}

object TutorialManager {
    const val TOTAL_STEPS = 7

    /** On the search screen: the Settings button ringed, Next opening Settings. */
    const val SETTINGS_BUTTON_STEP = 4
    /** The first step shown over Settings: the overlay's Settings step opens Settings for it. */
    const val FIRST_SETTINGS_STEP = 5
    /** Over Settings' Gallery tab, the swatch fan in the spotlight. */
    const val GALLERY_STEP = 5
    /** Over Settings' Wellbeing tab, today's screen time (or the card that turns usage access on) in the spotlight. */
    const val WELLBEING_STEP = 6

    /** The step showing over Settings right now, or null: Gallery and Wellbeing mark their spotlit part only then. */
    var liveStep by mutableStateOf<Int?>(null)

    /** The card's top edge in the window (px) while a step shows over Settings, for a page to keep its spotlit part above. */
    var cardTop by mutableFloatStateOf(0f)

    val targetStates = mutableStateMapOf<Int, TargetState>()
    
    fun getTargetState(step: Int): TargetState {
        return targetStates.getOrPut(step) { TargetState() }
    }

    fun clearTargetStates() {
        targetStates.clear()
    }

    fun completeTutorial(prefs: SharedPreferences) {
        prefs.edit { put(SettingsManager.TUTORIAL_COMPLETED, true) }
        clearTargetStates()
    }

    fun resetForForceTutorial(prefs: SharedPreferences) {
        prefs.edit {
            put(SettingsManager.TUTORIAL_STEP, 0)
            put(SettingsManager.TUTORIAL_COMPLETED, false)
        }
        clearTargetStates()
    }

    /** Plays until completed; with debug unlocked, "force tutorial" replays it. */
    fun isTutorialActive(prefs: SharedPreferences): Boolean =
        (prefs[SettingsManager.DEBUG_UNLOCKED] && prefs[SettingsManager.FORCE_TUTORIAL]) ||
            !prefs[SettingsManager.TUTORIAL_COMPLETED]
}

/**
 * Marks the part of a Settings tab that [step] spotlights. Nothing is tracked unless that step is showing, so the page
 * pays nothing for it otherwise.
 */
fun Modifier.tutorialSpot(step: Int): Modifier = this.composed {
    if (TutorialManager.liveStep != step) return@composed Modifier
    DisposableEffect(step) {
        onDispose { TutorialManager.targetStates[step]?.rect = null }
    }
    Modifier.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) TutorialManager.getTargetState(step).rect = coordinates.boundsInWindow()
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

