package com.pixel.intelligentsearch.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A frame's work duration carries everything the performance hint session needs to accept it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrameWorkDurationTest {

    @Test
    fun carriesTheFrameStartAndSplitsCpuFromGpu() {
        val work = frameWorkDuration(startNanos = 1_000, totalNanos = 8_000_000, gpuNanos = 2_000_000)

        assertNotNull(work)
        work!!
        assertEquals(1_000L, work.workPeriodStartTimestampNanos)
        assertEquals(8_000_000L, work.actualTotalDurationNanos)
        assertEquals(6_000_000L, work.actualCpuDurationNanos)
        assertEquals(2_000_000L, work.actualGpuDurationNanos)
    }

    @Test
    fun aFrameWithoutAStartTimeIsNotReportedThisWay() {
        assertNull(frameWorkDuration(startNanos = 0, totalNanos = 8_000_000, gpuNanos = 2_000_000))
    }
}
