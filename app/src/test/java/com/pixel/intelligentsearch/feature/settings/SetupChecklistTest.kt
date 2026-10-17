package com.pixel.intelligentsearch.feature.settings

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SetupChecklistTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun aFreshInstallIsOfferedSetupWithNothingDone() {
        assertEquals(0, SetupChecklist.doneCount(context, prefs))
        assertTrue(SetupChecklist.shouldOffer(context, prefs))
        // No search engine role on this test device, so that step isn't listed.
        assertFalse(SetupStep.SEARCH_BAR in SetupChecklist.steps(context))
    }

    @Test
    fun aStepIsDoneOnlyWithBothThePermissionAndTheSearchSourceOn() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, false).commit()
        assertFalse(SetupChecklist.isDone(context, prefs, SetupStep.CONTACTS))
        prefs.edit().put(SettingsManager.SEARCH_CONTACTS, true).commit()
        assertTrue(SetupChecklist.isDone(context, prefs, SetupStep.CONTACTS))
    }

    @Test
    fun choosingAFolderCompletesFiles() {
        assertFalse(SetupChecklist.isDone(context, prefs, SetupStep.FILES))
        prefs.edit().putStringSet("search.files.tree_uris", setOf("content://com.android.externalstorage.documents/tree/primary%3ADocuments")).commit()
        assertTrue(SetupChecklist.isDone(context, prefs, SetupStep.FILES))
    }

    @Test
    fun sayingDoneStopsTheOffer() {
        SetupChecklist.dismiss(prefs)
        assertFalse(SetupChecklist.shouldOffer(context, prefs))
    }
}
