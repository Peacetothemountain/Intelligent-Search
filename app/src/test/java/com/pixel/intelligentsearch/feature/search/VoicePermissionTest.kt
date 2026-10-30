package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * When the microphone request comes back refused, App Info opens only if Android won't ask again. A first request
 * the user just dismissed must ask again next time instead, and so must one Android asks afresh after a grant lapsed
 * ("Only this time"): the microphone shares the other permissions' decline record, forgotten once it is allowed.
 *
 * Android's rules, as modelled here: a first "Don't allow" sets its reason flag, a second clears it for good, a closed
 * prompt changes nothing, and a lapsed grant leaves the flag clear.
 */
@RunWith(RobolectricTestRunner::class)
class VoicePermissionTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = compose.activity.application
    private val mic = Manifest.permission.RECORD_AUDIO
    private val problems = mutableListOf<String>()
    private lateinit var voice: OverlayVoice

    /** What Android's prompt answers next: allowed or not, after what Android changes as the user answers. */
    private var answer: Pair<Boolean, () -> Unit> = false to {}

    private val prompt = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                answer.second()
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, answer.first as O)
            }
        }
    }

    @Before
    fun setUp() {
        app.getSharedPreferences("declined_permissions", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).denyPermissions(mic)
        androidShowsItsReason(false)
    }

    private fun androidShowsItsReason(shown: Boolean) =
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(mic, shown)

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides prompt) {
                voice = rememberOverlayVoice(onHeard = {}, onProblem = { problems += it })
            }
        }
        compose.waitForIdle()
    }

    /** Taps the microphone with it off; true when App Info opened. */
    private fun tapMic(allow: Boolean, whileAsking: () -> Unit = {}): Boolean {
        answer = allow to whileAsking
        shadowOf(app).clearNextStartedActivities()
        compose.runOnIdle { voice.start() }
        compose.waitForIdle()
        return shadowOf(app).nextStartedActivity?.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }

    private fun allowedOnlyThisTime() = tapMic(allow = true) {
        shadowOf(app).grantPermissions(mic)
        androidShowsItsReason(false)
    }

    private fun grantLapses() = shadowOf(app).denyPermissions(mic)

    @Test
    fun aFirstRequestDismissedWithoutAnAnswerIsNotPermanent() {
        show()
        assertEquals(false, tapMic(allow = false))
        assertEquals("Allow the microphone to search by voice.", problems.last())
    }

    @Test
    fun aFirstDenialIsNotPermanent() {
        show()
        assertEquals(false, tapMic(allow = false) { androidShowsItsReason(true) })
    }

    @Test
    fun aSecondDenialIsPermanent() {
        show()
        tapMic(allow = false) { androidShowsItsReason(true) }
        assertEquals(true, tapMic(allow = false) { androidShowsItsReason(false) })
        assertEquals("Turn on the microphone for Intelligent Search in Settings to search by voice.", problems.last())
    }

    @Test
    fun allowedOnAppInfoItListensOnceTheSearchIsBack() {
        show()
        tapMic(allow = false) { androidShowsItsReason(true) }
        assertEquals(true, tapMic(allow = false) { androidShowsItsReason(false) })

        // Allowed on App info, then Back: the tap that asked meant to search by voice.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        shadowOf(app).grantPermissions(mic)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertTrue(
            "listening starts: ${problems.last()}",
            voice.active || problems.last() == "Speech recognition service unavailable on device."
        )
    }

    @Test
    fun aRequestAndroidNoLongerShowsIsPermanent() {
        show()
        tapMic(allow = false) { androidShowsItsReason(true) }
        tapMic(allow = false) { androidShowsItsReason(false) }
        // Denied for good earlier: Android answers at once, with no reason flag before or after.
        assertEquals(true, tapMic(allow = false))
    }

    @Test
    fun aClosedPromptAfterALapsedGrantIsNotPermanent() {
        show()
        allowedOnlyThisTime()
        grantLapses()
        // Android asks afresh; the user only closes its prompt. "Asked before" alone sent them to App Info.
        assertEquals(false, tapMic(allow = false))
    }

    @Test
    fun anEarlierDeclineIsForgottenOnceTheMicrophoneIsAllowed() {
        show()
        tapMic(allow = false) { androidShowsItsReason(true) }
        allowedOnlyThisTime()
        grantLapses()
        assertEquals(false, tapMic(allow = false))
    }

    @Test
    fun theOldAskedBeforeRecordStillCountsWhileTheMicrophoneIsOff() {
        // Declined for good under the old record: App Info must still be offered, the only way back.
        app.getSharedPreferences("voice_permission_state", Context.MODE_PRIVATE).edit { putBoolean("mic_asked", true) }
        show()
        assertEquals(true, tapMic(allow = false))
    }

    @Test
    fun theOldAskedBeforeRecordIsDroppedWhileTheMicrophoneIsOn() {
        app.getSharedPreferences("voice_permission_state", Context.MODE_PRIVATE).edit { putBoolean("mic_asked", true) }
        shadowOf(app).grantPermissions(mic)
        show()
        grantLapses()
        assertEquals(false, tapMic(allow = false))
        assertEquals(false, app.getSharedPreferences("voice_permission_state", Context.MODE_PRIVATE).getBoolean("mic_asked", false))
    }
}
