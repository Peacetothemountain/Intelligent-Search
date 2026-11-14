package com.pixel.intelligentsearch.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

/** Searching "battery" answers with the battery, not with "Normal battery consumption". */
class BatteryStatusTextTest {

    @Test
    fun theLevelComesFirstThenChargingThenBatterySaver() {
        assertEquals("40%", batteryStatusText(40, charging = false, saverOn = false))
        assertEquals("40% \u00b7 Charging", batteryStatusText(40, charging = true, saverOn = false))
        assertEquals("15% \u00b7 Battery Saver is on", batteryStatusText(15, charging = false, saverOn = true))
    }

    @Test
    fun anUnreadableLevelIsLeftOut() {
        assertEquals("Charging", batteryStatusText(null, charging = true, saverOn = false))
        assertEquals("Battery Saver is off", batteryStatusText(Int.MIN_VALUE, charging = false, saverOn = false))
    }
}
