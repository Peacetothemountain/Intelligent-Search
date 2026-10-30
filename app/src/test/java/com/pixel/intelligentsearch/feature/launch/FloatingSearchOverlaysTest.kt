package com.pixel.intelligentsearch.feature.launch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.R
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.data.settings.put
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The floating button draws nothing without "Display over other apps", whatever its switch says, and TalkBack can
 * move it as a drag does.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FloatingSearchOverlaysTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun setUp() {
        hilt.inject()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() {
        prefs.edit().clear().commit()
        FloatingSearchOverlays.sync(context)
    }

    @Test
    fun withoutThePermissionTheButtonDoesntShow() {
        ShadowSettings.setCanDrawOverlays(false)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun withThePermissionTheButtonFollowsItsSwitch() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        assertTrue(FloatingSearchOverlays.isButtonShowing)

        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, false).commit()
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun talkBackMovesTheButtonWithoutADragAndItIsRemembered() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        val view = FloatingSearchOverlays.buttonView!!
        val labels = view.createAccessibilityNodeInfo().actionList.mapNotNull { it.label?.toString() }
        assertTrue("actions $labels", labels.containsAll(listOf("Move to left edge", "Move to right edge", "Move up", "Move down")))
        val stored = SettingsManager.storedPrefs(context)

        assertTrue(view.performAccessibilityAction(R.id.floating_button_move_left, null))
        assertEquals("left", stored[SettingsManager.FLOATING_BUTTON_EDGE])

        val start = stored[SettingsManager.FLOATING_BUTTON_POSITION]
        view.performAccessibilityAction(R.id.floating_button_move_up, null)
        val raised = stored[SettingsManager.FLOATING_BUTTON_POSITION]
        assertTrue("$raised < $start", raised < start)
        assertEquals("left", stored[SettingsManager.FLOATING_BUTTON_EDGE])

        view.performAccessibilityAction(R.id.floating_button_move_down, null)
        assertTrue(stored[SettingsManager.FLOATING_BUTTON_POSITION] > raised)

        view.performAccessibilityAction(R.id.floating_button_move_right, null)
        assertEquals("right", stored[SettingsManager.FLOATING_BUTTON_EDGE])
    }

    /**
     * Out of touch mode (a key press anywhere) Android gives focus to the first view that takes it and draws its focus
     * highlight, a gray square over the whole window around the round button. Nothing in the window takes focus.
     */
    @Test
    fun nothingInTheButtonsWindowTakesFocusSoNoSquareIsDrawnAroundIt() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        val view = FloatingSearchOverlays.buttonView!!
        // A key press, and what Android then does to the window: focus its first view that takes it.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(view.isInTouchMode)
        assertFalse(view.restoreDefaultFocus())
        assertFalse(view.hasFocus())
        assertFalse(view.isFocused)
    }

    @Test
    fun hiddenUntilUnlockItHearsAnUnlockSentBySystemUi() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        assertTrue(FloatingSearchOverlays.buttonView!!.performLongClick())

        // Android 17 sends the unlock from System UI, an app: a receiver that isn't exported never heard it, so the
        // button stayed hidden.
        val unlock = shadowOf(context).registeredReceivers.single { it.intentFilter.hasAction(Intent.ACTION_USER_PRESENT) }
        assertEquals(ContextCompat.RECEIVER_EXPORTED, unlock.flags and ContextCompat.RECEIVER_EXPORTED)
        assertEquals(0, unlock.flags and ContextCompat.RECEIVER_NOT_EXPORTED)

        context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowOf(context).registeredReceivers.none { it.intentFilter.hasAction(Intent.ACTION_USER_PRESENT) })
        assertTrue(FloatingSearchOverlays.isButtonShowing)
    }

    @Test
    fun revokingThePermissionTakesItDown() {
        ShadowSettings.setCanDrawOverlays(true)
        prefs.edit().put(SettingsManager.FLOATING_SEARCH_BUTTON, true).commit()
        FloatingSearchOverlays.sync(context)
        assertTrue(FloatingSearchOverlays.isButtonShowing)

        ShadowSettings.setCanDrawOverlays(false)
        FloatingSearchOverlays.sync(context)
        assertFalse(FloatingSearchOverlays.isButtonShowing)
    }
}
