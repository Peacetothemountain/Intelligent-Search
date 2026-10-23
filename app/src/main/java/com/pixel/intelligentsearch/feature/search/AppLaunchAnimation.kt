package com.pixel.intelligentsearch.feature.search

import android.app.ActivityOptions
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
import kotlin.math.roundToInt

/**
 * How an app opened from the overlay starts: the ActivityOptions for its opening animation, and its source bounds,
 * the tapped icon's bounds on screen, which the app receives as `Intent.getSourceBounds()` just as from a launcher.
 * Null when it came from no icon on screen.
 */
class AppLaunchOptions(val options: Bundle?, val sourceBounds: android.graphics.Rect?)

/**
 * The App Animations setting for apps and shortcuts opened from the overlay. On, the app opens out of the icon that
 * was tapped, as it does from a launcher icon; off, Android's default app-open transition plays.
 *
 * The Pixel launcher's own icon-to-window animation is a remote transition only the launcher may run (it needs a
 * system permission), so this does what an ordinary app can that comes closest, as the launcher itself does when it
 * cannot run its own: the app's window is revealed out of the icon's bounds on screen (a clip-reveal launch from the
 * icon's position and size), on the overlay's display, and the app is told the icon's bounds as its source. From
 * Android 13 its splash screen also shows the app's icon, as it does when an app is opened from the launcher, rather
 * than the plain splash Android gives apps opened from another app.
 */
internal object AppLaunchAnimation {

    /**
     * The launch from [source] (the overlay's view) out of [icon], the tapped icon's bounds in that view's coordinates.
     * Without an icon on screen the app opens out of [touch], the point touched (null for Enter on a keyboard), and
     * failing that out of the overlay's middle. Null when [enabled] is off, which leaves the system default.
     */
    fun launch(source: View, enabled: Boolean, icon: Rect?, touch: Offset?): AppLaunchOptions? {
        if (!enabled) return null
        val iconBounds = icon?.takeIf { it.width > 0f && it.height > 0f }?.let {
            // Whole pixels covering all of the icon.
            android.graphics.Rect(floor(it.left).toInt(), floor(it.top).toInt(), ceil(it.right).toInt(), ceil(it.bottom).toInt())
        }
        val reveal = iconBounds ?: run {
            val x = touch?.x?.roundToInt() ?: (source.width / 2)
            val y = touch?.y?.roundToInt() ?: (source.height / 2)
            android.graphics.Rect(x, y, x, y)
        }
        val options = ActivityOptions.makeClipRevealAnimation(source, reveal.left, reveal.top, reveal.width(), reveal.height())
        source.display?.let { options.setLaunchDisplayId(it.displayId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
        }
        val sourceBounds = iconBounds?.let {
            val onScreen = IntArray(2).also(source::getLocationOnScreen)
            android.graphics.Rect(it).apply { offset(onScreen[0], onScreen[1]) }
        }
        return AppLaunchOptions(options.toBundle(), sourceBounds)
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
