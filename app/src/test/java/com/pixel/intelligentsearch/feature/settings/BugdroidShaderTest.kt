package com.pixel.intelligentsearch.feature.settings

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

    /** Every uniform set must exist in its shader: setting one that doesn't throws, and the bugdroid would lose its key. */
    @Test
    fun everyUniformTheShadowsSetExists() {
        val shaders = BugdroidShaders(::olderCompiler)
        for (clip in BugdroidClip.entries) for (part in 0..2) {
            shaders.shadow.setShadowUniforms(clip, 1440f, 1190f, 1f, part, 0.5f)
        }
        shaders.edgeFade.setEdgeFadeUniforms(1440f, 1190f, 16f)
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
