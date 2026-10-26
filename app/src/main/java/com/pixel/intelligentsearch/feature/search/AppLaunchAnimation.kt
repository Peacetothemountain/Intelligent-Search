package com.pixel.intelligentsearch.feature.search

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.window.SplashScreen
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import com.pixel.intelligentsearch.core.data.AppItem
import kotlin.math.ceil
import kotlin.math.floor

/**
 * How an app opened from the overlay starts: the ActivityOptions it is started with, and its source bounds, the tapped
 * icon's bounds on screen, which the app receives as `Intent.getSourceBounds()` just as from a launcher. Null when it
 * came from no icon on screen.
 */
class AppLaunchOptions(val options: Bundle?, val sourceBounds: android.graphics.Rect?, val intentFlags: Int = 0)

/**
 * The ActivityOptions for an app opened from the overlay, once [AppOpeningLayer] has grown the tapped icon into the
 * app's splash screen.
 *
 * Android 17 drops an ordinary app's own opening animations (clip reveal, scale up) when it opens another app's task:
 * only the launcher, with a system permission, may move another app's window. So the overlay draws the motion itself,
 * in its own window, and only then starts the app, on the overlay's display, with its icon on the splash screen (from
 * Android 13), as when it is opened from the launcher, so the real splash takes over from the one drawn.
 */
internal object AppLaunchAnimation {

    /**
     * The launch from [source] (the overlay's view) out of [icon], the tapped icon's bounds in that view (null: none).
     * When the overlay has [drawnSplash], the app's window comes up without Android's own opening motion, so its real
     * splash screen replaces the drawn one exactly, with nothing moving.
     */
    fun launch(source: View, icon: Rect?, drawnSplash: Boolean = false): AppLaunchOptions {
        val options = ActivityOptions.makeBasic()
        source.display?.let { options.setLaunchDisplayId(it.displayId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
        }
        val sourceBounds = icon?.takeIf { it.width > 0f && it.height > 0f }?.let {
            // Whole pixels covering all of the icon, on screen.
            val onScreen = IntArray(2).also(source::getLocationOnScreen)
            android.graphics.Rect(floor(it.left).toInt(), floor(it.top).toInt(), ceil(it.right).toInt(), ceil(it.bottom).toInt())
                .apply { offset(onScreen[0], onScreen[1]) }
        }
        return AppLaunchOptions(options.toBundle(), sourceBounds, if (drawnSplash) Intent.FLAG_ACTIVITY_NO_ANIMATION else 0)
    }
}

/**
 * What an app is opened from: the icons it may open out of, the first one on screen winning (Enter opens the Best
 * Match out of its card's icon, or out of the same app's icon in the app row when the card is scrolled away), and
 * whether a touch opened it, so a launch whose icon is not on screen can still open out of the touch.
 */
internal class LaunchSource(val iconKeys: List<String>, val byTouch: Boolean) {
    companion object {
        /** A tap on the icon under [key]. */
        fun tap(key: String) = LaunchSource(listOf(key), byTouch = true)

        /** A launch with no touch, out of the first of [keys] that is on screen. */
        fun keyboard(vararg keys: String) = LaunchSource(keys.toList(), byTouch = false)
    }
}

/** The keys the overlay's app icons are known by, one per place an app can be opened from. */
internal object LaunchIconKeys {
    const val BEST_MATCH = "best_match"

    /** The big icon in the detail pane beside the results (large screens). */
    const val DETAIL = "detail_app"

    /** [app]'s icon in the app row (its work or Private Space copy is a different icon). */
    fun app(app: AppItem) = "app_${app.packageName}_${app.userHandle?.hashCode() ?: 0}_${app.profileType}"

    /** The quick-app pill for [packageName]. */
    fun pill(packageName: String) = "pill_$packageName"
}

/** The test tag of the icon under [key]. */
internal fun launchIconTag(key: String) = "launch_icon_$key"

/** The overlay's app icons that are on screen, so an app can open out of the one it was opened from. */
internal class LaunchIcons {
    private val icons = HashMap<String, LaunchIconNode>()

    /** The first of [keys] whose icon is on screen now, in the overlay view's coordinates; null when none is. */
    fun bounds(keys: List<String>): Rect? = keys.firstNotNullOfOrNull { key ->
        icons[key]?.coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.takeIf { it.width > 0f && it.height > 0f }
    }

    internal fun put(key: String, icon: LaunchIconNode) {
        icons[key] = icon
    }

    internal fun remove(key: String, icon: LaunchIconNode) {
        icons.remove(key, icon)
    }
}

/** An app icon for a row or pill to mark: the overlay's [LaunchIcons] and the icon's key there. */
class LaunchIconTarget internal constructor(internal val icons: LaunchIcons, internal val key: String)

/** Marks this element as the icon under [key] in [icons]: an app opened from it opens out of these bounds. */
internal fun Modifier.launchIcon(icons: LaunchIcons, key: String): Modifier =
    this.then(LaunchIconElement(icons, key)).testTag(launchIconTag(key))

/** Marks this element as [target]'s icon; no change when there is none. */
internal fun Modifier.launchIcon(target: LaunchIconTarget?): Modifier =
    if (target == null) this else launchIcon(target.icons, target.key)

private data class LaunchIconElement(val icons: LaunchIcons, val key: String) : ModifierNodeElement<LaunchIconNode>() {
    override fun create() = LaunchIconNode(icons, key)

    override fun InspectorInfo.inspectableProperties() {
        name = "launchIcon"
        properties["key"] = key
    }

    override fun update(node: LaunchIconNode) {
        if (node.icons !== icons || node.key != key) {
            node.unregister()
            node.icons = icons
            node.key = key
            node.coordinates?.let { node.onGloballyPositioned(it) }
        }
    }
}

internal class LaunchIconNode(var icons: LaunchIcons, var key: String) : Modifier.Node(), GlobalPositionAwareModifierNode {
    var coordinates: LayoutCoordinates? = null
        private set

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        icons.put(key, this)
    }

    override fun onDetach() {
        unregister()
        coordinates = null
    }

    fun unregister() = icons.remove(key, this)
}

/** Where the user last touched the overlay, in its view's coordinates. */
internal class LaunchOrigin {
    var point: Offset? = null
    internal var layout: LayoutCoordinates? = null
}

/** Records each touch-down on this element into [origin], without taking the touch from anything below it. */
internal fun Modifier.recordLaunchOrigin(origin: LaunchOrigin): Modifier = this
    .onGloballyPositioned { origin.layout = it }
    .pointerInput(origin) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val layout = origin.layout?.takeIf { it.isAttached }
            origin.point = layout?.localToRoot(down.position) ?: down.position
        }
    }
