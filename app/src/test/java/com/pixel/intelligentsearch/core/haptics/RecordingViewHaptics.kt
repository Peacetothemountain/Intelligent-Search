package com.pixel.intelligentsearch.core.haptics

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * Records the haptic constants attached views pass to their window, which plays them as system haptics. Add it to a
 * test's shadows and read [played].
 */
@Implements(className = "android.view.ViewRootImpl", isInAndroidSdk = false)
class RecordingViewHaptics : ShadowViewRootImpl() {
    @Implementation
    protected fun performHapticFeedback(effectId: Int, always: Boolean): Boolean {
        played += effectId
        return true
    }

    companion object {
        @JvmStatic val played = mutableListOf<Int>()
    }
}
