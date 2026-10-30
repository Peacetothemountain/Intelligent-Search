package com.pixel.intelligentsearch.feature.launch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.rememberSetting
import com.pixel.intelligentsearch.core.haptics.PixelHapticEngine
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.core.theme.ThemeMode
import com.pixel.intelligentsearch.core.ui.expressive.MatteShell
import com.pixel.intelligentsearch.core.ui.expressive.expressivePress
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating search button's window content: the app's search glyph on a Material You circle (a plain matte shell
 * in the primary container colour, as the app's other small containers are), pressed with Android 17's shape morph.
 */
@Composable
internal fun FloatingSearchButton(interactions: InteractionSource) {
    val theme by rememberSetting(SettingsManager.THEME)
    IntelligentSearchTheme(darkTheme = ThemeMode.from(theme).isDark(isSystemInDarkTheme())) {
        Box(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            MatteShell(
                modifier = Modifier.size(FloatingSearchOverlays.BUTTON_SIZE_DP.dp).expressivePress(interactions, CircleShape),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_lens_expressive),
                        contentDescription = null, // the window itself is the "Search" button to TalkBack
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * The floating button's window: drags with the finger (in screen coordinates, since the window moves under it),
 * opens search on a tap and hides until the next unlock on a long press. To TalkBack it is one button, "Search",
 * whose long-press action hides it and whose Move actions do what a drag does without one: to either edge, or up and
 * down by two buttons' heights, settling and remembered as a released drag is. Taps and the long press play the app's
 * one haptic.
 */
@SuppressLint("ViewConstructor")
internal class FloatingSearchButtonView(context: Context, private val host: Host) : FrameLayout(context) {

    interface Host {
        fun position(): Pair<Int, Int>
        fun dragTo(x: Int, y: Int)
        /** The drag ended: settle against the nearer edge. */
        fun release()
        fun open()
        fun hideUntilUnlock()
    }

    /** Feeds the press to the Compose content, which morphs its shape while held. */
    val interactions = MutableInteractionSource()
    private var press: PressInteraction.Press? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false

    private val longPress = Runnable {
        longPressed = true
        endPress(cancelled = true)
        performLongClick()
    }

    init {
        contentDescription = "Search"
        isClickable = true
        isLongClickable = true
        // The window never takes the keyboard, so nothing in it needs keyboard focus. Clickable, the button could
        // still take it once the screen left touch mode (a key press anywhere), and Android then drew its focus
        // highlight, a faint gray square, over the whole window around the circle.
        isFocusable = false
        defaultFocusHighlightEnabled = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        setOnClickListener {
            PixelHapticEngine.get(context).performPredictiveBackHaptic(this)
            host.open()
        }
        setOnLongClickListener {
            PixelHapticEngine.get(context).performPredictiveBackHaptic(this)
            host.hideUntilUnlock()
            true
        }
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Open search"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, "Hide until you unlock"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.floating_button_move_left, "Move to left edge"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.floating_button_move_right, "Move to right edge"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.floating_button_move_up, "Move up"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.floating_button_move_down, "Move down"))
    }

    /** How far Move Up and Move Down go: two buttons' heights. */
    private val moveStep = (FloatingSearchOverlays.BUTTON_SIZE_DP * 2 * resources.displayMetrics.density).roundToInt()

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val (x, y) = host.position()
        // A drag to there and its release: clamped onto the screen, settled against the nearer edge and remembered.
        val (toX, toY) = when (action) {
            R.id.floating_button_move_left -> Int.MIN_VALUE to y
            R.id.floating_button_move_right -> Int.MAX_VALUE to y
            R.id.floating_button_move_up -> x to y - moveStep
            R.id.floating_button_move_down -> x to y + moveStep
            else -> return super.performAccessibilityAction(action, arguments)
        }
        host.dragTo(toX, toY)
        host.release()
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // Taps go through performClick; drags have accessibility Move actions.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                host.position().let { (x, y) -> startX = x; startY = y }
                dragging = false
                longPressed = false
                startPress(event.x, event.y)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && !longPressed && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    removeCallbacks(longPress)
                    endPress(cancelled = true)
                }
                if (dragging) host.dragTo(startX + dx.toInt(), startY + dy.toInt())
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                when {
                    dragging -> host.release()
                    longPressed -> Unit
                    else -> {
                        endPress(cancelled = false)
                        performClick()
                    }
                }
                endPress(cancelled = true)
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                endPress(cancelled = true)
                if (dragging) host.release()
                dragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun startPress(x: Float, y: Float) {
        endPress(cancelled = true)
        press = PressInteraction.Press(Offset(x, y)).also { interactions.tryEmit(it) }
    }

    private fun endPress(cancelled: Boolean) {
        val current = press ?: return
        interactions.tryEmit(if (cancelled) PressInteraction.Cancel(current) else PressInteraction.Release(current))
        press = null
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }
}
