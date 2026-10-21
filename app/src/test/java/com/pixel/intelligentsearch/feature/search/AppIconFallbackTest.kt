package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import com.pixel.intelligentsearch.core.ui.peekThemedAppIcon
import com.pixel.intelligentsearch.screenshots.confinedEffects
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The plain app icon an app result falls back on while its themed icon loads. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class AppIconFallbackTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun inject() = hilt.inject()

    /** An app icon that counts how often it is rasterised. */
    private class CountingIcon : ColorDrawable(Color.RED) {
        var draws = 0
        override fun getIntrinsicWidth() = 192
        override fun getIntrinsicHeight() = 192
        override fun draw(canvas: Canvas) {
            draws++
            super.draw(canvas)
        }
    }

    @Test
    fun aCachedThemedIconSkipsRasterisingTheAppIcon() {
        // An installed app whose themed icon was loaded and cached by an earlier search.
        val packageName = "org.example.themed"
        shadowOf(context.packageManager).apply {
            installPackage(
                PackageInfo().apply {
                    this.packageName = packageName
                    applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
                }
            )
            setApplicationIcon(packageName, ColorDrawable(Color.BLUE))
        }
        getThemedAppIcon(context, packageName)
        assertNotNull(peekThemedAppIcon(packageName))

        val icon = CountingIcon()
        compose.setContent { AppGridItem(AppItem("Themed", packageName, icon)) {} }
        compose.onNodeWithContentDescription("Themed").assertExists()

        assertEquals(0, icon.draws)
    }

    @Test
    fun withoutACachedIconTheAppIconShowsAtOnce() {
        // A package nothing has loaded yet: the plain icon fills the slot while the themed one loads.
        val icon = CountingIcon()
        compose.setContent { AppGridItem(AppItem("Not Cached", "org.example.notcached", icon)) {} }
        compose.onNodeWithContentDescription("Not Cached").assertExists()

        assertEquals(1, icon.draws)
    }
}
