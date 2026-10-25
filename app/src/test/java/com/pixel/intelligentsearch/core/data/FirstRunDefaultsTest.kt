package com.pixel.intelligentsearch.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a fresh install starts with, as the owner chose it. */
class FirstRunDefaultsTest {

    @Test
    fun theMaterialMorphAnimationIsOnByDefault() {
        assertTrue(SettingsManager.MORPH_ANIMATION_ENABLED.default)
    }

    /** At a Glance itself is on, but none of its items: each is turned on by choice. */
    @Test
    fun atAGlanceIsOnButNoneOfItsItemsAre() {
        assertTrue(SettingsManager.GLANCE_ENABLED.default)
        for (item in listOf(
            SettingsManager.GLANCE_TIMERS, SettingsManager.GLANCE_ALARM, SettingsManager.GLANCE_MEDIA,
            SettingsManager.GLANCE_BIRTHDAYS, SettingsManager.GLANCE_CLOCK
        )) {
            assertFalse("${item.name} is on by default", item.default)
        }
    }
}
