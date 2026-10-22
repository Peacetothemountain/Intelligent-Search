package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.ui.getThemedAppIcon
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.util.Collections
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The quick-app pills under the search bar. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class QuickAppPanelTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val maps = "org.example.maps"

    /** Whether each label lookup ran on the main thread. */
    private val lookupsOnMain: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())
    private val recordingLabel: (Context, String) -> String = { _, _ ->
        lookupsOnMain += Looper.myLooper() == Looper.getMainLooper()
        "Maps"
    }

    @Before fun setUp() {
        hilt.inject()
        shadowOf(context.packageManager).apply {
            installPackage(
                PackageInfo().apply {
                    packageName = maps
                    applicationInfo = ApplicationInfo().apply { packageName = maps }
                }
            )
            setApplicationIcon(maps, ColorDrawable(Color.GREEN))
        }
        // The icon is cached already, as it is once the overlay has been opened before.
        getThemedAppIcon(context, maps, activePackOverride = "system_default")
    }

    @Test
    fun anUnknownNameIsLookedUpOffTheMainThread() {
        compose.setContent {
            QuickAppPill(packageName = maps, activeIconPack = "system_default", scale = 1f, appLabel = recordingLabel) {}
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Maps").fetchSemanticsNodes().isNotEmpty() }

        assertTrue(lookupsOnMain.isNotEmpty())
        assertFalse("PackageManager was asked on the main thread", lookupsOnMain.any { it })
    }

    @Test
    fun aNameTheOverlayAlreadyHasNeedsNoLookup() {
        compose.setContent {
            QuickAppPill(packageName = maps, activeIconPack = "system_default", scale = 1f, knownName = "Maps", appLabel = recordingLabel) {}
        }
        compose.onNodeWithText("Maps").assertExists()
        compose.waitForIdle()

        assertTrue(lookupsOnMain.isEmpty())
    }

    @Test
    fun theIconIsLargeEnoughToMakeOut() {
        compose.setContent {
            QuickAppPill(packageName = maps, activeIconPack = "system_default", scale = 1f, knownName = "Maps", appLabel = recordingLabel) {}
        }
        // 18dp icons were too small to make out; the name beside them is unchanged.
        compose.onNodeWithTag(QUICK_APP_ICON_TAG, useUnmergedTree = true).assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
        compose.onNodeWithText("Maps").assertExists()
    }

    @Test
    fun aCrowdedPanelStillShrinksItsIcons() {
        compose.setContent {
            QuickAppPill(packageName = maps, activeIconPack = "system_default", scale = 0.5f, knownName = "Maps", appLabel = recordingLabel) {}
        }
        compose.onNodeWithTag(QUICK_APP_ICON_TAG, useUnmergedTree = true).assertWidthIsEqualTo(11.dp)
    }
}
