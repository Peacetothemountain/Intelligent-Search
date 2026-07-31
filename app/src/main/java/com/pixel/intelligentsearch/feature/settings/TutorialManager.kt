package com.pixel.intelligentsearch.feature.settings
import androidx.core.content.edit
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

class TargetState {
    var rect by mutableStateOf<Rect?>(null)
}

object TutorialManager {
    const val TOTAL_STEPS = 5
    val targetStates = mutableStateMapOf<Int, TargetState>()
    
    fun getTargetState(step: Int): TargetState {
        return targetStates.getOrPut(step) { TargetState() }
    }

    fun getStep(prefs: SharedPreferences): Int {
        return prefs.getInt("tutorial_step", 0)
    }

    fun setStep(prefs: SharedPreferences, step: Int) {
        prefs.edit().putInt("tutorial_step", step).apply()
    }
    
    fun completeTutorial(prefs: SharedPreferences) {
        prefs.edit().putBoolean("tutorial_completed", true).apply()
    }

    fun resetForForceTutorial(prefs: SharedPreferences) {
        prefs.edit()
            .putInt("tutorial_step", 0)
            .putBoolean("tutorial_completed", false)
            .apply()
    }
    
    fun isTutorialActive(prefs: SharedPreferences): Boolean {
        val debugUnlocked = prefs.getBoolean("debug_unlocked", false)
        if (debugUnlocked && prefs.getBoolean("force_tutorial", false)) {
            return true
        }
        
        // Normal behavior: only play if not completed.
        return !prefs.getBoolean("tutorial_completed", false)
    }
}

fun Modifier.tutorialTarget(step: Int, prefs: SharedPreferences): Modifier = this.then(
    Modifier.onGloballyPositioned { coordinates ->
        TutorialManager.getTargetState(step).rect = coordinates.boundsInRoot()
    }
)
