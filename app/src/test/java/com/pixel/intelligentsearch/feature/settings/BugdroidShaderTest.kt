package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The key and shadow shaders are compiled by the device at run time, where a mistake would only show as an unkeyed
 * black box. Native graphics compiles them with Android's own shader compiler, so a broken shader fails here instead.
 * Its Skia predates Android 13's `child.eval(coords)`, so the calls are given in the older `sample(child, coords)` form.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BugdroidShaderTest {

    private fun olderCompiler(source: String) = source.replace(Regex("""(\w+)\.eval\("""), "sample($1, ")

    @Test
    fun theShadersCompile() {
        BugdroidShaders(::olderCompiler)
    }

    /**
     * Every uniform set must exist in its shader: setting one that doesn't throws, and the bugdroid would lose its key.
     * The key places the floor (view, scale, ground) to key out the renders' floor light under the feet.
     */
    @Test
    fun everyUniformTheShadowsSetExists() {
        val shaders = BugdroidShaders(::olderCompiler)
        for (clip in BugdroidClip.entries) for (part in 0..2) {
            shaders.shadow.setShadowUniforms(clip, 1440f, 1190f, 1f, part, 0.5f)
        }
        shaders.edgeFade.setEdgeFadeUniforms(1440f, 1190f, 16f)
        for (clip in BugdroidClip.entries) shaders.key.setKeyUniforms(clip, 1440f, 1190f, 1f)
    }

    /** The shadow is cast in a copy small enough to be cheap but never so small that its sharpest blur shows steps. */
    @Test
    fun theShadowIsCastInASmallCopyOfTheView() {
        // The settings page's bugdroid (about 0.33 view pixels per frame pixel) and the tutorial's (about 0.85).
        assertEquals(0.24f, shadowScale(0.33f), 0.01f)
        assertEquals(0.2f, shadowScale(0.85f), 0.0001f)
        assertEquals(1f, shadowScale(0.05f), 0.0001f)
        assertEquals(1f, shadowScale(0f), 0.0001f)
    }

    /** Shadows read deeper on a dark page, and stay translucent on both. */
    @Test
    fun darkPagesGetDeeperShadows() {
        val light = shadowStrengths(dark = false)
        val dark = shadowStrengths(dark = true)
        for (i in light.indices) {
            assertTrue("part $i", dark[i] >= light[i])
            assertTrue("part $i", light[i] > 0f && dark[i] < 1f)
        }
    }
}
