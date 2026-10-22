package com.pixel.intelligentsearch.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Skipping subfolders of the chosen folders: what can be added, and what goes when a chosen folder is removed. */
@RunWith(RobolectricTestRunner::class)
class ExcludedSubfoldersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val tree = "content://com.android.externalstorage.documents/tree/"
    private val documents = "${tree}primary%3ADocuments"
    private val music = "${tree}primary%3AMusic"
    private val documentsOld = "${tree}primary%3ADocuments%2FOld"
    private val musicDemos = "${tree}primary%3AMusic%2FDemos"

    @Before fun choose() {
        prefs.edit().clear().putStringSet(UserGrantedFiles.KEY_FOLDER_URIS, setOf(documents, music)).commit()
    }

    @Test
    fun aSubfolderOfAChosenFolderIsSkippedOnce() {
        assertEquals(UserGrantedFiles.ExcludeResult.ADDED, UserGrantedFiles.addExcludedFolder(prefs, documentsOld))
        assertEquals(setOf(documentsOld), UserGrantedFiles.excludedFolderUris(prefs))
        // The same folder again, however its URI is spelled, is already skipped.
        assertEquals(UserGrantedFiles.ExcludeResult.ALREADY_EXCLUDED, UserGrantedFiles.addExcludedFolder(prefs, "$documentsOld%2F"))
        assertEquals(setOf(documentsOld), UserGrantedFiles.excludedFolderUris(prefs))
    }

    @Test
    fun aFolderOutsideTheChosenOnesOrAChosenOneItselfIsRefused() {
        for (uri in listOf("${tree}primary%3ADownload%2FJunk", documents, "content://other.provider/tree/primary%3ADocuments%2FOld", "nonsense")) {
            assertEquals(uri, UserGrantedFiles.ExcludeResult.NOT_INSIDE_A_CHOSEN_FOLDER, UserGrantedFiles.addExcludedFolder(prefs, uri))
        }
        assertEquals(emptySet<String>(), UserGrantedFiles.excludedFolderUris(prefs))
    }

    @Test
    fun removingAChosenFolderDropsOnlyItsOwnSkippedSubfolders() {
        UserGrantedFiles.addExcludedFolder(prefs, documentsOld)
        UserGrantedFiles.addExcludedFolder(prefs, musicDemos)
        UserGrantedFiles.removeFolder(context, prefs, documents)
        assertEquals(setOf(musicDemos), UserGrantedFiles.excludedFolderUris(prefs))

        UserGrantedFiles.removeExcludedFolder(prefs, musicDemos)
        assertEquals(emptySet<String>(), UserGrantedFiles.excludedFolderUris(prefs))
    }

    @Test
    fun anExcludedFolderIsNamedByItsPath() {
        assertEquals("Documents/Old", UserGrantedFiles.excludedFolderName(context, prefs, documentsOld))
    }
}
