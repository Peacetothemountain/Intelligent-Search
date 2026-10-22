package com.pixel.intelligentsearch.feature.launch

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.pixel.intelligentsearch.MainActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.ui.expressive.ExpressiveMotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The floating search button and the edge swipe strip: small windows drawn over other apps.
 *
 * Permission: both need "Display over other apps" (SYSTEM_ALERT_WINDOW), which the user grants on Android's own page
 * after the in-app explanation. Google Play allows it for a user-visible utility like this one, a launcher for the
 * app's own search that the user turns on; it is a sensitive permission, so both options are off by default, nothing
 * is drawn without the grant, and a backup can never switch them on. No AccessibilityService is used: Play forbids
 * one for anything but accessibility.
 *
 * No foreground service: Android keeps a process that shows an overlay window at perceptible priority, and the
 * windows are added again at every app start (including the one after a reboot, through StartupSync), so a service
 * and its notification would add nothing. While the button is hidden (held down, a full-screen app, the search
 * itself open) its window stays attached but fully transparent and untouchable, so Android still delivers the
 * unlock that brings it back and taps pass through to the app below.
 *
 * Every method runs on the main thread.
 */
@SuppressLint("RtlHardcoded") // Positions are physical: the button and strip sit on the screen's left or right edge.
object FloatingSearchOverlays {

    private const val TAG = "FloatingSearch"

    /** The button's diameter, and how far it rests in from the edge. */
    internal const val BUTTON_SIZE_DP = 56
    private const val BUTTON_MARGIN_DP = 8
    /** The strip's width; under gesture navigation it is at least as wide as Android's back-gesture zone. */
    private const val STRIP_WIDTH_DP = 16
    /** Android keeps the back gesture off at most 200dp of each edge for an app; a taller strip would lose the rest. */
    private const val GESTURE_EXCLUSION_LIMIT_DP = 200
    /** How far a swipe must come in from the edge to open search. */
    internal const val SWIPE_THRESHOLD_DP = 48

    // The windows live as long as the process and hold only the application context (and the window context made
    // from it), so nothing shorter-lived can leak through these.
    @SuppressLint("StaticFieldLeak")
    private var button: ButtonWindow? = null
    @SuppressLint("StaticFieldLeak")
    private var strip: StripWindow? = null
    @SuppressLint("StaticFieldLeak")
    private var hooks: Hooks? = null

    /** Search screens started (the overlay itself): the windows stay out of its way. */
    private var searchScreens = 0
    private var hiddenUntilUnlock = false

    /** Whether the user has let this app display over other apps. */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Whether either window is up (for tests and the settings page). */
    internal val isButtonShowing: Boolean get() = button != null
    internal val isStripShowing: Boolean get() = strip != null

    /**
     * Brings the windows in line with the settings: each shows while its switch is on and the permission granted,
     * and follows a changed edge, position or height.
     */
    @MainThread
    fun sync(context: Context) {
        val app = context.applicationContext
        val prefs = LaunchSurfaces.prefs(app)
        val allowed = canDraw(app)
        val wantButton = allowed && prefs[SettingsManager.FLOATING_SEARCH_BUTTON]
        val wantStrip = allowed && prefs[SettingsManager.EDGE_SWIPE]
        try {
            if (wantButton || wantStrip) installHooks(app)
            when {
                !wantButton -> removeButton()
                button == null -> button = ButtonWindow(windowContext(app)).also { it.follow(prefs); it.add() }
                else -> button?.follow(prefs)
            }
            when {
                !wantStrip -> removeStrip()
                strip == null -> strip = StripWindow(windowContext(app)).also { it.follow(prefs); it.add() }
                else -> strip?.follow(prefs)
            }
        } catch (e: RuntimeException) {
            // WindowManager refuses the window (the grant was just revoked): show nothing rather than crash.
            Log.w(TAG, "Floating search windows unavailable", e)
            removeButton()
            removeStrip()
        }
    }

    private fun removeButton() {
        button?.remove()
        button = null
        hiddenUntilUnlock = false
        hooks?.stopListeningForUnlock()
    }

    private fun removeStrip() {
        strip?.remove()
        strip = null
    }

    private fun windowContext(app: Context): Context {
        val display = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return app
        return app.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    private fun refreshVisibility() {
        button?.applyVisibility()
        strip?.applyVisibility()
    }

    /** Opens the search overlay with the keyboard up, as the Quick Settings tile does. */
    private fun openSearch(context: Context) {
        try {
            // Allowed from the background: the app holds "Display over other apps" and its window was just touched.
            context.startActivity(SearchShortcutNotification.searchIntent(context.applicationContext))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open search", e)
        }
    }

    private fun hideButtonUntilUnlock() {
        hiddenUntilUnlock = true
        hooks?.listenForUnlock()
        refreshVisibility()
    }

    // ---- What the windows follow: search screens, rotation and the unlock ----

    /**
     * Installed once per process the first time a window is wanted, and kept: counting search screens has to start
     * before one opens, so it can't wait for a window to be switched on.
     */
    private fun installHooks(app: Context) {
        if (hooks?.app === app) return
        searchScreens = 0
        hooks = Hooks(app).also { it.install() }
    }

    private class Hooks(val app: Context) : Application.ActivityLifecycleCallbacks, ComponentCallbacks {
        private var unlockReceiver: BroadcastReceiver? = null

        fun install() {
            (app as? Application)?.registerActivityLifecycleCallbacks(this)
            app.registerComponentCallbacks(this)
        }

        fun listenForUnlock() {
            if (unlockReceiver != null) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    hiddenUntilUnlock = false
                    stopListeningForUnlock()
                    refreshVisibility()
                }
            }
            // Only Android sends USER_PRESENT; system broadcasts reach receivers that aren't exported.
            ContextCompat.registerReceiver(app, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
            unlockReceiver = receiver
        }

        fun stopListeningForUnlock() {
            unlockReceiver?.let { runCatching { app.unregisterReceiver(it) } }
            unlockReceiver = null
        }

        override fun onActivityStarted(activity: Activity) {
            if (activity is MainActivity) {
                searchScreens++
                refreshVisibility()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity is MainActivity) {
                searchScreens = max(0, searchScreens - 1)
                refreshVisibility()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            // Rotation or a new screen size: put both windows back where their settings say.
            button?.place()
            strip?.place()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }

    // ---- The windows ----

    private fun overlayParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes focus or the keyboard; touches outside go to the app below.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Positions are in screen pixels; the safe area already keeps clear of the bars.
        fitInsetsTypes = 0
    }

    /** The screen minus the status bar, navigation bar and cutouts. */
    private fun safeArea(wm: WindowManager): SafeArea {
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return SafeArea(bounds.left + insets.left, bounds.top + insets.top, bounds.right - insets.right, bounds.bottom - insets.bottom)
    }

    /** Shared by both windows: attached, transparent and untouchable while hidden, laid out from the settings. */
    private abstract class OverlayWindow(val context: Context) {
        val wm: WindowManager = context.getSystemService(WindowManager::class.java)
        val params = overlayParams()
        val density = context.resources.displayMetrics.density
        abstract val view: View
        var attached = false
        /** A full-screen app (status bar hidden) is showing. */
        var fullScreen = false

        open val hidden: Boolean get() = fullScreen || searchScreens > 0

        fun dp(value: Int): Int = (value * density).roundToInt()

        open fun add() {
            view.setOnApplyWindowInsetsListener { v, insets ->
                val nowFullScreen = !insets.isVisible(WindowInsets.Type.statusBars())
                if (nowFullScreen != fullScreen) {
                    fullScreen = nowFullScreen
                    applyVisibility()
                }
                v.onApplyWindowInsets(insets)
            }
            place()
            applyVisibility(update = false)
            wm.addView(view, params)
            attached = true
        }

        open fun remove() {
            if (attached) runCatching { wm.removeViewImmediate(view) }
            attached = false
        }

        abstract fun place()

        fun update() {
            if (attached) runCatching { wm.updateViewLayout(view, params) }
        }

        fun applyVisibility(update: Boolean = true) {
            val show = !hidden
            // Fully transparent and untouchable while hidden, so Android lets taps through to the app below.
            params.alpha = if (show) 1f else 0f
            params.flags = if (show) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (update) update()
        }
    }

    private class ButtonWindow(context: Context) : OverlayWindow(context), FloatingSearchButtonView.Host {
        private val lifecycle = OverlayLifecycleOwner()
        override val view = FloatingSearchButtonView(context, this)
        private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
        private var snap: Job? = null
        private val size = dp(BUTTON_SIZE_DP)
        private val margin = dp(BUTTON_MARGIN_DP)
        private var edge = ScreenEdge.RIGHT
        private var position = 0

        override val hidden: Boolean get() = super.hidden || hiddenUntilUnlock

        init {
            params.title = "Floating search button"
            params.width = size
            params.height = size
        }

        override fun add() {
            val compose = ComposeView(context).apply {
                setContent { FloatingSearchButton(view.interactions) }
            }
            view.addView(compose)
            lifecycle.attachTo(view)
            super.add()
            lifecycle.resume()
        }

        override fun remove() {
            snap?.cancel()
            scope.cancel()
            super.remove()
            lifecycle.destroy()
        }

        /** Follows the stored edge and height (a restore, another device's settings), unless it put them there itself. */
        fun follow(prefs: android.content.SharedPreferences) {
            val storedEdge = ScreenEdge.from(prefs[SettingsManager.FLOATING_BUTTON_EDGE])
            val storedPosition = prefs[SettingsManager.FLOATING_BUTTON_POSITION]
            if (storedEdge == edge && storedPosition == position && attached) return
            edge = storedEdge
            position = storedPosition
            place()
            update()
        }

        override fun place() {
            val area = safeArea(wm)
            snap?.cancel()
            params.x = FloatingButtonGeometry.restingX(edge, size, area, margin)
            params.y = FloatingButtonGeometry.yFor(position, size, area)
            update()
        }

        override fun position(): Pair<Int, Int> = params.x to params.y

        override fun dragTo(x: Int, y: Int) {
            snap?.cancel()
            val area = safeArea(wm)
            params.x = FloatingButtonGeometry.clampX(x, size, area)
            params.y = FloatingButtonGeometry.clampY(y, size, area)
            update()
        }

        override fun release() {
            val area = safeArea(wm)
            val (newEdge, target) = FloatingButtonGeometry.snapped(params.x, params.y, size, area, margin)
            edge = newEdge
            position = FloatingButtonGeometry.positionOf(target.y, size, area)
            // Remembered for next time; [follow] sees its own values and leaves the animation alone.
            LaunchSurfaces.prefs(context).edit {
                put(SettingsManager.FLOATING_BUTTON_EDGE, newEdge.stored)
                put(SettingsManager.FLOATING_BUTTON_POSITION, position)
            }
            val fromX = params.x
            val fromY = params.y
            snap?.cancel()
            if (!ValueAnimator.areAnimatorsEnabled()) {
                params.x = target.x
                params.y = target.y
                update()
                return
            }
            // Settles against the edge on the spatial spring, as the app's other moving surfaces do.
            snap = scope.launch {
                Animatable(0f).animateTo(1f, ExpressiveMotionTokens.Spatial.ExpressiveDefault()) {
                    params.x = fromX + ((target.x - fromX) * value).roundToInt()
                    params.y = fromY + ((target.y - fromY) * value).roundToInt()
                    update()
                }
            }
        }

        override fun open() = openSearch(context)

        override fun hideUntilUnlock() = hideButtonUntilUnlock()
    }

    private class StripWindow(context: Context) : OverlayWindow(context), EdgeSwipeStripView.Host {
        override val view = EdgeSwipeStripView(context, this)
        private var edge = ScreenEdge.RIGHT
        private var positionPercent = 50
        private var heightPercent = 25

        init {
            params.title = "Edge swipe for search"
        }

        fun follow(prefs: android.content.SharedPreferences) {
            val storedEdge = ScreenEdge.from(prefs[SettingsManager.EDGE_SWIPE_EDGE])
            val storedPosition = prefs[SettingsManager.EDGE_SWIPE_POSITION]
            val storedHeight = prefs[SettingsManager.EDGE_SWIPE_HEIGHT]
            if (storedEdge == edge && storedPosition == positionPercent && storedHeight == heightPercent && attached) return
            edge = storedEdge
            positionPercent = storedPosition
            heightPercent = storedHeight
            place()
        }

        override fun place() {
            val metrics = wm.currentWindowMetrics
            val gestures = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
            val tappable = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.tappableElement())
            val gestureNav = isGestureNavigation(gestures.left, gestures.right, tappable.left, tappable.right, tappable.bottom)
            val backZone = if (edge == ScreenEdge.LEFT) gestures.left else gestures.right
            val width = if (gestureNav) max(dp(STRIP_WIDTH_DP), backZone) else dp(STRIP_WIDTH_DP)
            val bounds = edgeStripBounds(
                edge, safeArea(wm), positionPercent, heightPercent, width,
                maxHeightPx = if (gestureNav) dp(GESTURE_EXCLUSION_LIMIT_DP) else null
            )
            view.edge = edge
            params.x = bounds.x
            params.y = bounds.y
            params.width = bounds.width
            params.height = bounds.height
            update()
        }

        override fun open() = openSearch(context)
    }
}

/**
 * Whether the phone uses gesture navigation: Back is a swipe from the side edges there (they have gesture insets),
 * and nothing at the bottom or sides is a tappable button. With 3-button navigation the buttons are tappable, and
 * a sideways navigation bar in landscape is what gives a side its gesture inset.
 */
internal fun isGestureNavigation(gestureLeft: Int, gestureRight: Int, tappableLeft: Int, tappableRight: Int, tappableBottom: Int): Boolean =
    (gestureLeft > 0 || gestureRight > 0) && tappableLeft == 0 && tappableRight == 0 && tappableBottom == 0
