package com.pixel.intelligentsearch.feature.settings

import android.app.Application
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.security.AttestationResult
import com.pixel.intelligentsearch.core.security.HardwareSecurityLevel
import com.pixel.intelligentsearch.core.security.SecurityPosture
import com.pixel.intelligentsearch.core.security.SecurityPosture.Axis
import com.pixel.intelligentsearch.core.security.SecurityPosture.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.math.PI

/**
 * The posture card's motion and what it is for: it opens on the check that most needs attention, says what it means
 * and offers Android's page to fix it where the phone can; a tap on the chart explains another check; and the radar's
 * lights (the gap runs, the edge light and its pings) follow pure rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h1600dp-xhdpi")
class SecurityPostureCardTest {

    @get:Rule val compose = createComposeRule()

    private val verified = AttestationResult(
        isHardwareAttested = true, securityLevel = HardwareSecurityLevel.STRONGBOX, attestationChallenge = "",
        certificateCount = 4, issuerName = "", verifiedBootState = "VERIFIED", deviceLocked = true,
        osPatchLevel = 202608, isRootVerified = true
    )

    private fun posture(deviceSecure: Boolean, patch: String = "2026-08-05") =
        SecurityPosture.score(HardwareSecurityLevel.STRONGBOX, verified, patch, LocalDate.of(2026, 10, 22), deviceSecure)

    private fun show(axes: List<SecurityPosture.AxisScore>) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { SecurityPostureCard(axes) } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun itOpensOnTheCheckThatMostNeedsAttention() {
        // No screen lock (0.15) is weaker than a two-month-old patch (0.9).
        assertEquals(Axis.ScreenLock, weakestAxis(posture(deviceSecure = false)))
        assertEquals(Axis.PatchLevel, weakestAxis(posture(deviceSecure = true)))
        // Every check at full marks (this month's patch, as the hardware attests it): nothing to point at.
        val perfect = SecurityPosture.score(
            HardwareSecurityLevel.STRONGBOX, verified.copy(osPatchLevel = 202610), null, LocalDate.of(2026, 10, 22), deviceSecure = true
        )
        assertNull(weakestAxis(perfect))
        // Pending checks aren't "weak", they just haven't answered.
        val pending = SecurityPosture.score(HardwareSecurityLevel.STRONGBOX, null, "2026-10-05", LocalDate.of(2026, 10, 22), true)
        assertNull(weakestAxis(pending))
    }

    @Test
    fun noScreenLockSaysWhyAndOpensAndroidsSecuritySettings() {
        show(posture(deviceSecure = false))
        compose.onNodeWithText("Screen Lock · None").assertExists()
        compose.onNodeWithText("There's no screen lock", substring = true).assertExists()
        compose.onNodeWithText("Set Screen Lock").performClick()
        compose.mainClock.advanceTimeByFrame()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, opened?.action)
    }

    @Test
    fun anOldPatchOffersToCheckForUpdates() {
        val old = SecurityPosture.patchScore(null, "2026-01-05", LocalDate.of(2026, 10, 22))
        val advice = postureAdvice(old)
        assertEquals(PostureFix.CheckForUpdates, advice.fix)
        assertTrue(advice.text, advice.text.contains("9 months old"))
        assertEquals("android.settings.SYSTEM_UPDATE_SETTINGS", PostureFix.CheckForUpdates.intents().first().action)
    }

    @Test
    fun checksThePhoneCantChangeHaveNoButton() {
        for (score in posture(deviceSecure = true)) {
            if (score.axis != Axis.PatchLevel) assertNull("${score.axis}", postureAdvice(score).fix)
        }
        val unlocked = SecurityPosture.AxisScore(Axis.Bootloader, 0.15f, "Unlocked", Rating.Weak)
        assertNull(postureAdvice(unlocked).fix)
        assertTrue(postureAdvice(unlocked).text.contains("unlocked"))
    }

    @Test
    fun everyCheckCanBeExplainedFromTalkBack() {
        show(posture(deviceSecure = true))
        val actions = compose.onNodeWithTag(POSTURE_RADAR_TAG).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        assertNotNull(actions)
        val custom = compose.onNodeWithTag(POSTURE_RADAR_TAG).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!
        assertEquals(Axis.entries.map { "Explain ${it.label}" }, custom.map { it.label })
        // Explaining Bootloader swaps the advice under the chart.
        compose.runOnIdle { custom.first { it.label == "Explain Bootloader" }.action() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Bootloader · Locked").assertExists()
    }

    @Test
    fun aTapPicksTheCheckWhoseSpokeIsNearest() {
        val angles = List(6) { i -> (-PI / 2 + i * 2 * PI / 6).toFloat() }
        assertEquals(0, axisAtAngle((-PI / 2).toFloat(), angles))
        assertEquals(1, axisAtAngle((-PI / 2 + PI / 3 - 0.2).toFloat(), angles))
        // Just left of 12 o'clock is still the top check, across the seam.
        assertEquals(0, axisAtAngle((-PI / 2 - 0.3).toFloat(), angles))
        assertEquals(3, axisAtAngle((PI / 2).toFloat(), angles))
    }

    @Test
    fun aCheckPingsJustAfterTheLightPassesItThenRests() {
        assertNull("not reached yet", vertexPing(trace = 0.30f, vertex = 0.31f))
        assertEquals(0f, vertexPing(0.31f, 0.31f)!!, 1e-5f)
        assertEquals(0.5f, vertexPing(0.31f + PING_SPAN / 2, 0.31f)!!, 1e-4f)
        assertNull("faded", vertexPing(0.31f + PING_SPAN + 0.01f, 0.31f))
        // Across the lap's seam.
        assertNotNull(vertexPing(0.02f, 0.95f))
    }

    @Test
    fun theTrailIsBrightestAtTheLightAndGoneAtItsTail() {
        assertEquals(1f, traceTrail(0f), 0f)
        assertEquals(0f, traceTrail(TRACE_TAIL), 1e-5f)
        assertEquals(0f, traceTrail(TRACE_TAIL + 0.1f), 0f)
        var last = 1f
        for (i in 0..20) {
            val v = traceTrail(TRACE_TAIL * i / 20f)
            assertTrue(v <= last)
            last = v
        }
    }
}
