package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A decline is remembered until the permission is seen allowed, so a later prompt isn't read as declined for good. */
@RunWith(RobolectricTestRunner::class)
class PermissionDeclineMemoryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val declined get() = context.getSharedPreferences(DECLINED_PERMISSIONS, Context.MODE_PRIVATE)
    private val contacts = Manifest.permission.READ_CONTACTS
    private val location = Manifest.permission.ACCESS_COARSE_LOCATION

    @Test
    fun aNotedDeclineIsRemembered() {
        noteDecline(context, contacts)
        assertTrue(declined.getBoolean(contacts, false))
    }

    @Test
    fun forgettingRemovesOnlyThatPermission() {
        noteDecline(context, contacts)
        noteDecline(context, location)
        forgetDecline(context, contacts)
        assertFalse(declined.contains(contacts))
        assertTrue(declined.contains(location))
    }

    @Test
    fun aPermissionAllowedInSettingsIsForgottenWhenSearchComesBack() {
        noteDecline(context, contacts)
        noteDecline(context, location)
        shadowOf(context).grantPermissions(contacts)
        shadowOf(context).denyPermissions(location)
        forgetDeclinesNowAllowed(context)
        assertEquals(setOf(location), declined.all.keys)
    }

    @Test
    fun aPermissionStillNotAllowedStaysDeclined() {
        // "Ask every time" reads as not granted here, and must keep the decline.
        noteDecline(context, location)
        shadowOf(context).denyPermissions(location)
        forgetDeclinesNowAllowed(context)
        assertTrue(declined.contains(location))
    }
}
