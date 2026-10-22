package com.pixel.intelligentsearch.feature.search

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.ContactItem
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.search.AndroidSettingResult
import com.pixel.intelligentsearch.core.search.ResultShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What each kind of result's long-press menu offers. */
@RunWith(RobolectricTestRunner::class)
class ResultMenuCommandsTest {

    private val spotify = AppItem("Spotify", "com.spotify.music", ColorDrawable(), activityName = "com.spotify.music.Main")
    private val john = ContactItem("John", "555", "content://com.android.contacts/contacts/lookup/john/1")
    private val file = FileItem("Notes.txt", "/Documents", "text/plain", "content://files/1")
    private val battery = AndroidSettingResult("battery", "Battery", "Settings", "battery", Intent())

    @Test
    fun appsOfferEverything() {
        val app = ResultMenuTarget.App(spotify)
        assertEquals(
            listOf(
                listOf(ResultCommand.PIN, ResultCommand.ADD_NICKNAME, ResultCommand.SET_TRIGGER),
                listOf(ResultCommand.SPLIT_SCREEN),
                listOf(ResultCommand.APP_INFO, ResultCommand.HIDE, ResultCommand.UNINSTALL)
            ),
            resultMenuCommands(app, ResultShortcuts.EMPTY, splitScreen = true, canUninstall = true)
        )
        // No split screen where the device can't, and no Uninstall for a system app.
        assertEquals(
            listOf(
                listOf(ResultCommand.PIN, ResultCommand.ADD_NICKNAME, ResultCommand.SET_TRIGGER),
                listOf(ResultCommand.APP_INFO, ResultCommand.HIDE)
            ),
            resultMenuCommands(app, ResultShortcuts.EMPTY)
        )
    }

    @Test
    fun aPinnedResultOffersUnpinAndASetTriggerOffersChange() {
        val app = ResultMenuTarget.App(spotify)
        val set = ResultShortcuts.EMPTY.withPinned(app.key, "Spotify", true).withTrigger(app.key, "Spotify", "sp")
        assertEquals(
            listOf(ResultCommand.UNPIN, ResultCommand.ADD_NICKNAME, ResultCommand.CHANGE_TRIGGER),
            resultMenuCommands(app, set).first()
        )
    }

    @Test
    fun contactsOfferPinNicknameAndTrigger() {
        assertEquals(
            listOf(listOf(ResultCommand.PIN, ResultCommand.ADD_NICKNAME, ResultCommand.SET_TRIGGER)),
            resultMenuCommands(ResultMenuTarget.Contact(john), ResultShortcuts.EMPTY, splitScreen = true, canUninstall = true)
        )
    }

    @Test
    fun filesAndSettingsOfferPinAndTrigger() {
        val expected = listOf(listOf(ResultCommand.PIN, ResultCommand.SET_TRIGGER))
        assertEquals(expected, resultMenuCommands(ResultMenuTarget.File(file), ResultShortcuts.EMPTY))
        assertEquals(expected, resultMenuCommands(ResultMenuTarget.Setting(battery), ResultShortcuts.EMPTY))
    }

    @Test
    fun pastSearchesOfferRemoveFromHistory() {
        assertEquals(
            listOf(listOf(ResultCommand.REMOVE_FROM_HISTORY)),
            resultMenuCommands(ResultMenuTarget.History("cats"), ResultShortcuts.EMPTY)
        )
    }

    @Test
    fun appInfoRowsAndContactsWithoutALookupKeyCantBePinned() {
        val appInfo = AndroidSettingResult("app_info:x", "Spotify App Info", "Settings", "app_info", Intent(), app = spotify)
        assertNull(ResultMenuTarget.Setting(appInfo).key)
        val noKey = ResultMenuTarget.Contact(john.copy(lookupUri = ""))
        assertEquals(emptyList<List<ResultCommand>>(), resultMenuCommands(noKey, ResultShortcuts.EMPTY))
    }

    @Test
    fun splitScreenIsOfferedInMultiWindowOrOnALargeScreenForThisProfilesApps() {
        assertTrue(splitScreenOffered(inMultiWindow = true, smallestWidthDp = 411, otherProfile = false))
        assertTrue(splitScreenOffered(inMultiWindow = false, smallestWidthDp = 600, otherProfile = false))
        assertFalse(splitScreenOffered(inMultiWindow = false, smallestWidthDp = 411, otherProfile = false))
        assertFalse(splitScreenOffered(inMultiWindow = true, smallestWidthDp = 800, otherProfile = true))
    }

    @Test
    fun splitScreenOpensTheLauncherActivityAdjacent() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = splitScreenIntent(context, spotify)!!
        assertEquals("com.spotify.music.Main", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
