package com.pixel.intelligentsearch.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An At a Glance switch shows as on only when its item can actually show. */
class GlancePrerequisitesTest {

    private val all = GlancePrerequisites(notificationAccess = true, contactsAllowed = true, searchContacts = true)

    @Test
    fun timersAndMediaControlsNeedNotificationAccess() {
        assertTrue(all.timers)
        assertTrue(all.media)
        assertFalse(all.copy(notificationAccess = false).timers)
        assertFalse(all.copy(notificationAccess = false).media)
    }

    @Test
    fun birthdaysNeedContactSearchAndTheContacts() {
        assertTrue(all.birthdays)
        assertFalse(all.copy(searchContacts = false).birthdays)
        assertFalse(all.copy(contactsAllowed = false).birthdays)
    }
}
