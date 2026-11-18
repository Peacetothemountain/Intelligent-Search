package com.pixel.intelligentsearch

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Compose 1.13 draws a mesh gradient on Android 14 and up with [android.graphics.Mesh], whose shaders Robolectric's
 * graphics can't build. Under the tests it draws with vertices instead, as Compose itself does on a software canvas
 * and below Android 14.
 */
@Implements(className = "androidx.compose.ui.graphics.MeshGradientRenderer_androidKt", isInAndroidSdk = false)
class MeshGradientRendererShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun MeshGradientRenderer(): Any =
            Class.forName("androidx.compose.ui.graphics.MeshGradientRendererImpl").getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }
}
