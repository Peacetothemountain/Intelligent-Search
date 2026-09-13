package com.pixel.intelligentsearch.core.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

class WindowInsetsAnimationCallback(
    private val onProgressUpdate: (progress: Float, imeHeight: Int, isImeVisible: Boolean) -> Unit
) {

    fun registerOnView(view: View) {
        val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            private var startBottom = 0
            private var endBottom = 0

            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    startBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                }
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    endBottom = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val imeAnim = runningAnimations.find {
                    (it.typeMask and WindowInsetsCompat.Type.ime()) != 0
                }
                if (imeAnim != null) {
                    val currentIme = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val isAppearing = endBottom >= startBottom
                    onProgressUpdate(imeAnim.fraction, currentIme, isAppearing && currentIme > 0)
                }
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                    val rootInsets = ViewCompat.getRootWindowInsets(view)
                    val finalIme = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    onProgressUpdate(1f, finalIme, finalIme > 0)
                }
            }
        }
        ViewCompat.setWindowInsetsAnimationCallback(view, callback)
    }
}
