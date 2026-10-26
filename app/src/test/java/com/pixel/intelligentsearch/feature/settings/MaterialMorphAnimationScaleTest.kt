package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Material Morph animation with Display size and Font size turned up: on Launch Portal and Search Sources, and on
 * its own in areas of many shapes and sizes. The animation keeps its rules however the screen is scaled: shapes stay
 * inside its area and below the bottom card, at most three at a time, sized for the area, each bounce a new shape and
 * a haptic tap.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
class MaterialMorphAnimationScaleTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    /** Large Display size on a typical phone makes it about 320 dp wide; on a big phone, about 360 dp. */
    private val largeScales = listOf(
        "w320dp-h640dp-xxhdpi" to 1f,
        "w320dp-h640dp-xxhdpi" to 1.3f,
        "w320dp-h640dp-xxhdpi" to 2f,
        "w360dp-h780dp-xxxhdpi" to 1.5f,
        "w360dp-h780dp-xxxhdpi" to 2f,
        "w411dp-h914dp-xxhdpi" to 2f,
        "w780dp-h360dp-xxxhdpi" to 1.3f
    )

    /** The last row on each page: the bottom card ends just under it. */
    private val pages = mapOf(
        "Launch Portal" to "Home Screen Shortcut",
        "Search Sources" to "Live Currency Rates"
    )

    @Before
    fun setUp() {
        hilt.inject()
    }

    /**
     * Shows [content] with the animation's random choices drawn from [seed], so every run of a test sees the same
     * shapes; [MorphBouncePhysicsTest] checks the rules over many seeds.
     */
    private fun launch(qualifiers: String, fontScale: Float, seed: Int = 1, content: @Composable () -> Unit): ActivityScenario<HiltTestActivity> {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        val scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        compose.mainClock.autoAdvance = false
        scenario.onActivity {
            it.setContent {
                CompositionLocalProvider(LocalMorphRandomSeed provides seed) {
                    IntelligentSearchTheme(darkTheme = false) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        return scenario
    }

    @Composable
    private fun Page(name: String) {
        when (name) {
            "Launch Portal" -> LaunchPortalScreen(prefs) {}
            else -> SearchSourcesScreen({}) {}
        }
    }

    private fun areaNode(): SemanticsNode = compose.onNodeWithTag(MORPH_ANIMATION_TAG, useUnmergedTree = true).fetchSemanticsNode()

    /** What the rules check found over a run. */
    private class Run(var bounces: Int = 0, var shapesSeen: Int = 0)

    /**
     * Steps the animation for [seconds] and checks the rules on every frame: at most four shapes, nothing NaN, every
     * shape sized for the area and inside it (or just coming in from under the card), every landing a bounce that
     * changes the shape, every bounce a haptic tap, and every shape that flies off having bounced at least twice and
     * touched down at most four times.
     */
    private fun checkRules(where: String, seconds: Float = 10f): Run {
        val run = Run()
        // Every shape seen, kept after it leaves, so a bounce in its last frame still counts.
        val seen = LinkedHashMap<Int, MorphBouncePhysics.Body>()
        val penetrated = HashMap<Int, Boolean>()
        val bouncesSoFar = HashMap<Int, Int>()
        // Taps felt before this run, by shapes that have since left.
        val start = areaNode().config[MorphSceneKey]
        for (b in start.physics.bodies) seen[b.id] = b
        // Shapes already in the air when the check begins may have had their area change under them.
        val inTheAirAtStart = start.physics.bodies.map { it.id }.toSet()
        val feltBefore = start.bouncesFelt - seen.values.sumOf { it.bounces }
        var t = 0f
        while (t < seconds) {
            compose.mainClock.advanceTimeBy(16)
            t += 0.016f
            val area = areaNode()
            val info = area.config[MorphSceneKey]
            val physics = info.physics
            val ppm = info.pxPerMetre
            val density = area.layoutInfo.density.density
            val widthPx = area.size.width.toFloat()
            val heightPx = area.size.height.toFloat()
            assertTrue("$where: ${physics.bodies.size} shapes", physics.bodies.size <= MorphBouncePhysics.MAX_SHAPES)
            if (physics.bodies.isNotEmpty()) {
                assertEquals("$where: scene width", widthPx, physics.width * ppm, 2f)
                assertEquals("$where: scene height", heightPx, physics.height * ppm, 2f)
            }
            for (b in physics.bodies) {
                seen[b.id] = b
                val values = listOf(b.x, b.y, b.vx, b.vy, b.spin, b.angle, b.radius, b.morphProgress, b.penetration, b.lightX, b.lightY, b.flash)
                assertTrue("$where: shape ${b.id} has $values", values.all { it.isFinite() })
                val diameterPx = 2f * b.radius * ppm
                assertTrue("$where: shape ${b.id} is ${diameterPx / density} dp in a ${heightPx / density} dp tall area", diameterPx <= heightPx * 0.25f)
                assertTrue("$where: shape ${b.id} is only ${diameterPx / density} dp", diameterPx >= 8f * density)
                // Coming in from under the card at most; once fully in, its top never rises above the area's.
                assertTrue("$where: shape ${b.id} at ${b.y - b.radius} m above the card", b.y + b.radius >= -1e-3f)
                if (b.entered) assertTrue("$where: shape ${b.id} rose ${(b.y - b.radius) * ppm} px above the card", (b.y - b.radius) * ppm >= -1f)
                // Pressed into the floor at most down to the bottom of the area, never through it, and never deeper than its height.
                assertTrue("$where: shape ${b.id} ${b.penetration} m into the floor, centre at ${b.y * ppm} px in a $heightPx px area", (b.y + b.radius) * ppm <= heightPx + 1f && b.penetration <= 2f * b.radius)
                assertTrue("$where: shape ${b.id} touched down ${b.contacts} times", b.contacts <= MorphBouncePhysics.MAX_LANDINGS)
                assertTrue("$where: shape ${b.id} still there after ${b.age} s", b.age < 10f)

                // Every landing is a new bounce, with a new shape, unless it touches down again straight after leaving
                // the rubber, which is the same bounce.
                val landed = b.inContact && penetrated[b.id] == false
                if (landed && b.bounces == (bouncesSoFar[b.id] ?: 0)) {
                    assertTrue("$where: shape ${b.id} landed after a ${b.sinceLift} s flight without bouncing", b.sinceLift < MorphBouncePhysics.MIN_FLIGHT)
                }
                penetrated[b.id] = b.inContact
                bouncesSoFar[b.id] = b.bounces
            }
            val alive = physics.bodies.map { it.id }.toSet()
            for (id in bouncesSoFar.keys - alive) {
                bouncesSoFar.remove(id)
                penetrated.remove(id)
                val body = seen.getValue(id)
                run.shapesSeen++
                // (One the area changed under, whose planned flight no longer leaves by itself, leaves with the change.)
                if (id !in inTheAirAtStart && (body.x < 0f || body.x > physics.width)) assertTrue("$where: shape $id left after ${body.bounces} bounces", body.bounces >= 2)
            }
            run.bounces = seen.values.sumOf { it.bounces }
            assertEquals("$where: bounces felt", run.bounces, info.bouncesFelt - feltBefore)
        }
        return run
    }

    @Test
    fun onTheThreePagesTheAnimationHasRoomUnderTheBottomCardAtEveryScale() {
        var belowTheFold = 0
        var seed = 0
        for ((qualifiers, fontScale) in largeScales) {
            for ((page, lastRow) in pages) {
                prefs.edit().clear().putBoolean(SettingsManager.MORPH_ANIMATION_ENABLED.name, true).commit()
                val where = "$page $qualifiers ${fontScale}x"
                val scenario = launch(qualifiers, fontScale, seed = ++seed) { Page(page) }
                try {
                    val area = areaNode()
                    val density = area.layoutInfo.density.density
                    assertTrue("$where: the area is ${area.size.height / density} dp tall", area.size.height >= 160f * density - 1f)
                    // Unclipped positions: a node scrolled out of sight has empty bounds.
                    val row = compose.onNodeWithText(lastRow).fetchSemanticsNode()
                    val cardBottom = row.positionInRoot.y + row.size.height
                    val areaTop = area.positionInRoot.y
                    assertTrue("$where: the area starts at $areaTop, above the card's last row at $cardBottom", areaTop >= cardBottom)
                    val rootHeight = compose.onRoot().fetchSemanticsNode().size.height
                    if (areaTop >= rootHeight) {
                        // Below the fold: nothing moves, and nothing taps, until it is scrolled into view.
                        belowTheFold++
                        repeat(180) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
                        val info = areaNode().config[MorphSceneKey]
                        assertTrue("$where: ${info.physics.bodies.size} shapes out of sight", info.physics.bodies.isEmpty())
                        assertEquals("$where: haptics out of sight", 0, info.bouncesFelt)
                        // Scrolled down by hand, as a person would; the clock is frozen, so no animated scroll.
                        var swipes = 0
                        while (areaNode().positionInRoot.y + areaNode().size.height > rootHeight && swipes++ < 10) {
                            compose.onRoot().performTouchInput { swipeUp() }
                            repeat(30) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
                        }
                        repeat(20) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
                    }
                    val run = checkRules(where, seconds = 6f)
                    assertTrue("$where: only ${run.bounces} bounces", run.bounces >= 4)
                } finally {
                    scenario.close()
                }
            }
        }
        assertTrue("no page had the area below the fold", belowTheFold > 0)
    }

    @Test
    fun onItsOwnTheAnimationKeepsItsRulesInAreasOfEverySize() {
        val cases = listOf(
            Triple("w320dp-h640dp-xxhdpi", 1f, 320 to 40),
            Triple("w320dp-h640dp-xxhdpi", 2f, 320 to 64),
            Triple("w360dp-h780dp-xxxhdpi", 2f, 360 to 160),
            Triple("w411dp-h914dp-xxhdpi", 1f, 411 to 300),
            Triple("w780dp-h360dp-xxxhdpi", 1f, 780 to 120),
            Triple("w1280dp-h800dp-xhdpi", 2f, 1280 to 500)
        )
        for ((i, case) in cases.withIndex()) {
            val (qualifiers, fontScale, area) = case
            val where = "${area.first}x${area.second} dp area, $qualifiers ${fontScale}x"
            val scenario = launch(qualifiers, fontScale, seed = i + 1) {
                Box(Modifier.size(area.first.dp, area.second.dp)) { MaterialMorphAnimation(Modifier.fillMaxSize()) }
            }
            try {
                val run = checkRules(where, seconds = 14f)
                assertTrue("$where: only ${run.bounces} bounces", run.bounces >= 6)
                assertTrue("$where: only ${run.shapesSeen} shapes came and went", run.shapesSeen >= 3)
            } finally {
                scenario.close()
            }
        }
    }

    @Test
    fun anAreaTooShortForABounceStaysEmpty() {
        val scenario = launch("w320dp-h640dp-xxhdpi", 2f) {
            Box(Modifier.size(320.dp, 20.dp)) { MaterialMorphAnimation(Modifier.fillMaxSize()) }
        }
        try {
            repeat(120) {
                compose.mainClock.advanceTimeBy(16)
                assertTrue(areaNode().config[MorphSceneKey].physics.bodies.isEmpty())
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun shapeSizesFollowTheAreaNotTheFontSize() {
        val scenario = launch("w360dp-h780dp-xxxhdpi", 1f) {
            Column {
                for (fontScale in listOf(1f, 2f)) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        Box(Modifier.size(360.dp, 300.dp)) { MaterialMorphAnimation(Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        try {
            repeat(90) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
            // The sizes in dp of the shapes in each area, at 100% and 200% text.
            val sizes = compose.onAllNodesWithTag(MORPH_ANIMATION_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { node ->
                val info = node.config[MorphSceneKey]
                val density = node.layoutInfo.density.density
                info.physics.bodies.map { 2f * it.radius * info.pxPerMetre / density }.distinct().sorted()
            }
            assertTrue("sizes $sizes", sizes.size == 2 && sizes[0].isNotEmpty())
            assertEquals("shape sizes at 100% and 200% text", sizes[0], sizes[1])
        } finally {
            scenario.close()
        }
    }

    @Test
    fun shapesStayInsideWhenTheAreaShrinksWhileTheyAreInTheAir() {
        var height by mutableStateOf(400)
        val scenario = launch("w411dp-h914dp-xxhdpi", 1f) {
            Box(Modifier.size(411.dp, height.dp)) { MaterialMorphAnimation(Modifier.fillMaxSize()) }
        }
        try {
            repeat(150) { compose.mainClock.advanceTimeBy(16); compose.waitForIdle() }
            assertTrue("no shapes in the air", areaNode().config[MorphSceneKey].physics.bodies.isNotEmpty())
            // As when the phone turns to landscape or the text grows while the page is open.
            height = 170
            checkRules("after shrinking", seconds = 6f)
        } finally {
            scenario.close()
        }
    }
}
