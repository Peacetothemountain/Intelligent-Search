package com.pixel.intelligentsearch.core.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.RequiresApi

class WindowInsetsAnimationCallback(
    private val onProgressUpdate: (progress: Float, isImeVisible: Boolean) -> Unit
) {

    fun registerOnView(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val callback = object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsets,
                    runningAnimations: MutableList<WindowInsetsAnimation>
                ): WindowInsets {
                    val imeAnimation = runningAnimations.find {
                        (it.typeMask and WindowInsets.Type.ime()) != 0
                    }
                    if (imeAnimation != null) {
                        onProgressUpdate(imeAnimation.fraction, imeAnimation.fraction > 0.01f)
                    }
                    return insets
                }
            }
            view.setWindowInsetsAnimationCallback(callback)
        }
    }
}
