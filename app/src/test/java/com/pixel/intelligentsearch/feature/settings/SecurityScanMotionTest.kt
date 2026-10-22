package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hardware & Security page's entrance scan: its timeline, the details' decrypt, the chain's locking and the count-up. */
class SecurityScanMotionTest {

    private val lines = listOf(
        "Device: Pixel 11 Pro XL",
        "Hardware Security: Google Titan M3",
        "Hardware Attestation (OID 1.3.6.1.4.1.11129.2.1.17): Hardware Verified",
        "SoC Platform: Tensor G6 · 2026",
        ""
    )

    @Test
    fun aStageRunsFromZeroBeforeItToOneAfterIt() {
        assertEquals(0f, scanStage(-5f, 100f, 200f), 0f)
        assertEquals(0f, scanStage(100f, 100f, 200f), 0f)
        assertEquals(0.5f, scanStage(200f, 100f, 200f), 1e-6f)
        assertEquals(1f, scanStage(300f, 100f, 200f), 0f)
        assertEquals(1f, scanStage(10_000f, 100f, 200f), 0f)
        // A stage with no length is a step.
        assertEquals(0f, scanStage(99f, 100f, 0f), 0f)
        assertEquals(1f, scanStage(100f, 100f, 0f), 0f)
    }

    @Test
    fun theDecryptEndsOnTheRealText() {
        for (seed in 1..6) for (line in lines) {
            assertEquals(line, decryptFrame(line, 1f, seed, frame = 3))
            assertEquals(line, decryptFrame(line, 1.5f, seed, frame = 0, keep = 4))
        }
    }

    @Test
    fun theDecryptKeepsTheLineItsLengthAndItsSpacesAndPunctuation() {
        for (seed in 1..6) for (line in lines) for (frame in 0 until DECRYPT_FRAMES) {
            for (step in 0..10) {
                val shown = decryptFrame(line, step / 10f, seed, frame)
                assertEquals("'$line' at $step", line.length, shown.length)
                for (i in line.indices) {
                    if (!line[i].isLetterOrDigit()) assertEquals(line[i], shown[i])
                    // A scrambled glyph is the same kind of character, so the line keeps roughly its width.
                    if (line[i].isDigit()) assertTrue(shown[i].isDigit())
                    if (line[i].isUpperCase()) assertTrue(shown[i].isUpperCase())
                    if (line[i].isLowerCase()) assertTrue(shown[i].isLowerCase())
                }
            }
        }
    }

    @Test
    fun atTheStartMostOfTheLineIsScrambled() {
        val line = lines[2]
        val shown = decryptFrame(line, 0f, seed = 5, frame = 0)
        val letters = line.indices.filter { line[it].isLetterOrDigit() }
        val scrambled = letters.count { shown[it] != line[it] }
        assertTrue("only $scrambled of ${letters.size} scrambled", scrambled > letters.size / 2)
    }

    @Test
    fun aSettledCharacterStaysSettled() {
        val line = lines[1]
        for (frame in 0 until DECRYPT_FRAMES) {
            var settled = emptySet<Int>()
            for (step in 0..20) {
                val p = step / 20f
                val now = line.indices.filter { decryptResolveAt(it, line.length, 2) <= p }.toSet()
                assertTrue(now.containsAll(settled))
                for (i in now) assertEquals(line[i], decryptFrame(line, p, 2, frame)[i])
                settled = now
            }
        }
        for (i in line.indices) assertTrue(decryptResolveAt(i, line.length, 2) in 0f..<1f)
    }

    @Test
    fun aChangedLineOnlyScramblesWhatChanged() {
        val before = "Hardware Attestation: Verifying..."
        val after = "Hardware Attestation: Hardware Verified"
        val keep = before.commonPrefixWith(after).length
        val shown = decryptFrame(after, 0f, seed = 3, frame = 1, keep = keep)
        assertEquals(after.substring(0, keep), shown.substring(0, keep))
        assertTrue(shown.substring(keep) != after.substring(keep))
    }

    @Test
    fun theCountUpRunsFromZeroToExactlyTheValue() {
        for (target in listOf(0, 1, 7, 42, 98, 100)) {
            assertEquals(0, countUpValue(0f, target))
            assertEquals(0, countUpValue(-1f, target))
            assertEquals(target, countUpValue(1f, target))
            assertEquals(target, countUpValue(2f, target))
            var last = 0
            for (step in 0..200) {
                val v = countUpValue(step / 200f, target)
                assertTrue("$target at $step: $v", v in last..target)
                last = v
            }
            assertEquals(target, last)
        }
    }

    @Test
    fun theCountUpLastsAsLongAsTheRadarTakesToDrawOut() {
        assertTrue(countUpMillis(5) > countUpMillis(3))
        assertEquals(countUpMillis(1) + 4 * RADAR_ENTRY_STAGGER_MILLIS.toInt(), countUpMillis(5))
    }

    @Test
    fun chainLinksLockOneAfterAnother() {
        val steps = 5
        assertEquals(0f, chainStepProgress(0f, 0, steps), 0f)
        for (k in 0 until steps) assertEquals(1f, chainStepProgress(1f, k, steps), 0f)
        // Halfway down the chain the first links have locked and the last haven't started.
        assertEquals(1f, chainStepProgress(0.5f, 1, steps), 0f)
        assertEquals(0.5f, chainStepProgress(0.5f, 2, steps), 1e-5f)
        assertEquals(0f, chainStepProgress(0.5f, 4, steps), 0f)
        assertEquals(0f, chainStepProgress(1f, 0, 0), 0f)
        for (step in 0..50) {
            val p = step / 50f
            for (k in 1 until steps) assertTrue(chainStepProgress(p, k, steps) <= chainStepProgress(p, k - 1, steps))
        }
    }

    @Test
    fun theChainDrawsInOrderWithinTheScan() {
        for (count in listOf(1, 2, 4, 8, 20)) {
            for (k in 1..count) assertTrue(chainLinkStart(k, count) > chainLinkStart(k - 1, count))
            // Every link, and the root key after them, has drawn by the end of the scan.
            assertTrue(chainLinkStart(count, count) + SCAN_LINK_DRAW_MILLIS <= SCAN_END_MILLIS + 1e-3f)
            assertTrue(chainLinkStart(0, count) >= detailLineStart(SCAN_CHAIN_LINE))
        }
        // Verification only reaches a link after it has started to draw.
        assertTrue(SCAN_LOCK_START >= chainLinkStart(0, 4))
    }

    @Test
    fun theScanRunsInOrder() {
        assertTrue(SCAN_CHECK_START > SCAN_SHIELD_START)
        assertTrue(SCAN_DESCRIPTION_START > SCAN_SHIELD_START)
        assertTrue(SCAN_DETAILS_START > SCAN_DESCRIPTION_START)
        for (i in 1..SCAN_CHAIN_LINE) assertEquals(SCAN_LINE_STAGGER, detailLineStart(i) - detailLineStart(i - 1), 1e-4f)
        assertTrue(SCAN_SHEEN_START + SCAN_SHEEN_MILLIS <= SCAN_END_MILLIS)
        assertTrue(detailLineStart(SCAN_CHAIN_LINE) + DECRYPT_MILLIS <= SCAN_END_MILLIS)
    }

    @Test
    fun theShieldGlowsOnceACycleAndOnlyGently() {
        assertEquals(0f, shieldGlow(0f), 0f)
        assertEquals(0f, shieldGlow(0.5f), 0f)
        assertEquals(0f, shieldGlow(1f), 1e-5f)
        assertEquals(1f, shieldGlow(0.9f), 1e-4f)
        for (step in 0..400) {
            val g = shieldGlow(step / 100f)
            assertTrue("$step: $g", g in 0f..1f)
        }
        // The first glow comes after the entrance has finished.
        assertTrue(0.8f * SHIELD_GLOW_PERIOD_MILLIS > SCAN_END_MILLIS)
    }
}
