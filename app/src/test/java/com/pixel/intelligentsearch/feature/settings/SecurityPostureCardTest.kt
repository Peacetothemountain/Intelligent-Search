package com.pixel.intelligentsearch.feature.settings

import android.app.Application
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The posture card: a ring of an arc per check and a tile per check, from the hardware up, for improving them: what
 * to improve comes ranked by what it wins back, the top one explained with Android's page to fix it where the phone
 * can, and a tap on any tile or arc explains that one. The ring's wave follows pure rules.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h1600dp-xhdpi")
class SecurityPostureCardTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Before fun setUp() = hilt.inject()

    private val verified = AttestationResult(
        isHardwareAttested = true, securityLevel = HardwareSecurityLevel.STRONGBOX, attestationChallenge = "",
        certificateCount = 4, issuerName = "", verifiedBootState = "VERIFIED", deviceLocked = true,
        osPatchLevel = 202608, isRootVerified = true
    )

    private fun posture(deviceSecure: Boolean, keys: HardwareSecurityLevel = HardwareSecurityLevel.STRONGBOX) =
        SecurityPosture.score(keys, verified, "2026-08-05", LocalDate.of(2026, 10, 22), deviceSecure)

    private fun show(axes: List<SecurityPosture.AxisScore>) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { SecurityPostureCard(axes) } }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theRingAndTilesRunFromTheHardwareUpToTheScreenLock() {
        show(posture(deviceSecure = true))
        assertEquals(Axis.KeyStorage, POSTURE_ORDER.first())
        assertEquals(Axis.ScreenLock, POSTURE_ORDER.last())
        assertEquals(Axis.entries.toSet(), POSTURE_ORDER.toSet())
        for (axis in POSTURE_ORDER) compose.onNodeWithTag(POSTURE_TILE_TAG_PREFIX + axis.name).assertExists()
        compose.onNodeWithTag(POSTURE_RING_TAG).assertExists()
        // The overall score sits still in the ring's centre.
        compose.onNodeWithTag(POSTURE_RING_TAG).assertContentDescriptionContains("98 percent", substring = true)
    }

    @Test
    fun aTapOnTheRingChoosesTheArcsCheck() {
        // Six arcs clockwise from 12: just right of 12 is the first, just left of it the last, 6 o'clock the fourth.
        assertEquals(0, ringArcAt(5f, 6))
        assertEquals(5, ringArcAt(355f, 6))
        assertEquals(3, ringArcAt(185f, 6))
        assertEquals("a full turn on is the first again", 0, ringArcAt(361f, 6))
        // The arcs leave even gaps between them and fill the circle with them.
        assertEquals(360f, (ringSweep(6) + RING_GAP_DEGREES) * 6, 1e-3f)
        assertEquals(RING_GAP_DEGREES / 2f, ringStart(0, 6), 0f)
    }

    @Test
    fun theWaveFlowsButEachArcStartsAndEndsClean() {
        val sweep = ringSweep(6)
        assertEquals(0f, ringWave(0f, sweep, 0.3f), 1e-5f)
        assertEquals(0f, ringWave(sweep, sweep, 0.3f), 1e-5f)
        // Mid-arc it moves as the flow runs.
        assertTrue(kotlin.math.abs(ringWave(sweep / 2, sweep, 0f) - ringWave(sweep / 2, sweep, 0.25f)) > 0.3f)
        for (i in 0..100) assertTrue(ringWave(sweep * i / 100f, sweep, i / 37f) in -1f..1f)
    }

    @Test
    fun eachCheckHasItsOwnMaterialShape() {
        val shapes = Axis.entries.map(::postureShape)
        assertEquals(shapes.size, shapes.toSet().size)
        for (name in shapes) assertTrue(name, name in MaterialMorphShapes.byName)
    }

    @Test
    fun whatToImproveIsRankedByWhatItWinsBack() {
        // No screen lock (0.15) and TEE keys (0.75) and a two-month-old patch (0.9).
        val plan = improvementPlan(posture(deviceSecure = false, keys = HardwareSecurityLevel.TEE))
        assertEquals(listOf(Axis.ScreenLock, Axis.KeyStorage, Axis.PatchLevel), plan.map { it.first.axis })
        // Six checks: the screen lock's missing 0.85 is 14% of the average, the keys' 0.25 is 4%, the patch's 2%.
        assertEquals(listOf(14, 4, 2), plan.map { it.second })
        // Everything at full marks, or not yet checked: nothing to improve.
        val perfect = SecurityPosture.score(
            HardwareSecurityLevel.STRONGBOX, verified.copy(osPatchLevel = 202610), null, LocalDate.of(2026, 10, 22), deviceSecure = true
        )
        assertTrue(improvementPlan(perfect).isEmpty())
        val pending = SecurityPosture.score(HardwareSecurityLevel.STRONGBOX, null, "2026-10-05", LocalDate.of(2026, 10, 22), true)
        assertTrue(improvementPlan(pending).isEmpty())
    }

    @Test
    fun itOpensOnTheTopFixWithAndroidsPageForIt() {
        show(posture(deviceSecure = false))
        compose.onNodeWithText("To Improve").assertExists()
        compose.onNodeWithText("+14%").assertExists()
        compose.onNodeWithText("There's no screen lock", substring = true).assertExists()
        compose.onNodeWithText("Set Screen Lock").performClick()
        compose.mainClock.advanceTimeByFrame()
        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, opened?.action)
    }

    @Test
    fun aTapOnALayerExplainsIt() {
        show(posture(deviceSecure = true))
        compose.onNodeWithTag(POSTURE_TILE_TAG_PREFIX + Axis.Bootloader.name).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("The bootloader is locked", substring = true).assertExists()
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
}
