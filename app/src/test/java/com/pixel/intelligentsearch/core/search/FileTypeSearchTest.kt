package com.pixel.intelligentsearch.core.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.profile.MultiProfileManager
import com.pixel.intelligentsearch.core.system.SystemToggleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Searching only one type of file ("pdf invoice"); the index itself holds every type. */
@RunWith(RobolectricTestRunner::class)
class FileTypeSearchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun coordinator() = UnifiedSearchCoordinator(
        context,
        SystemActionRouter(context, SystemToggleManager(context)),
        MultiProfileManager(context),
        SettingsManager(context)
    )

    private fun file(name: String, mime: String, modified: Long = 0L, folder: String = "Documents") =
        FileItem(name, "$folder/$name", mime, "content://docs/document/$name", modified)

    private val invoicePdf = file("Invoice March.pdf", "application/pdf", modified = 10)
    private val invoiceDoc = file("Invoice March.docx", "application/octet-stream", modified = 20)
    private val taxPdf = file("Tax 2025.pdf", "application/pdf", modified = 30)
    private val beach = file("beach.jpg", "image/jpeg", modified = 40, folder = "Pictures/Holiday")
    private val all = listOf(invoicePdf, invoiceDoc, taxPdf, beach)

    @Test
    fun aTypedSearchFindsOnlyThatTypeMatchingTheRestOfTheQuery() {
        val coordinator = coordinator().apply { replaceFiles(all) }
        assertEquals(listOf(invoicePdf), coordinator.searchFilesOfType("invoice", FileTypeGroup.PDF))
        assertEquals(listOf(invoiceDoc), coordinator.searchFilesOfType("invoice", FileTypeGroup.DOCUMENTS))
        // The folder counts too: "photos holiday" finds the beach photo in Holiday.
        assertEquals(listOf(beach), coordinator.searchFilesOfType("holiday", FileTypeGroup.IMAGES))
        assertTrue(coordinator.searchFilesOfType("invoice", FileTypeGroup.VIDEO).isEmpty())
        assertTrue(coordinator.searchFilesOfType("receipt", FileTypeGroup.PDF).isEmpty())
    }

    @Test
    fun aTypeAloneListsItsLatestFiles() {
        val coordinator = coordinator().apply { replaceFiles(all) }
        assertEquals(listOf(taxPdf, invoicePdf), coordinator.searchFilesOfType("", FileTypeGroup.PDF))
        assertEquals(listOf(taxPdf), coordinator.searchFilesOfType(" ", FileTypeGroup.PDF, limit = 1))
    }

    @Test
    fun theTypedListFollowsTheIndex() {
        val coordinator = coordinator().apply { replaceFiles(all) }
        coordinator.replaceFiles(listOf(beach))
        assertTrue(coordinator.searchFilesOfType("", FileTypeGroup.PDF).isEmpty())
    }

    @Test
    fun ownMediaOfEveryTypeIsIndexedUnlessInASkippedFolder() {
        // File search covers every type; an older build's File Types choice leaves nothing out.
        SettingsManager(context).prefs.edit().clear().putStringSet("search.files.excluded_types", setOf("images")).commit()
        val keep = UserGrantedFiles::keepsOwnMedia
        assertTrue(keep(FileItem("a.pdf", "/storage/emulated/0/Download/a.pdf", "application/pdf", "content://media/external/file/1")))
        assertTrue(keep(FileItem("a.jpg", "/storage/emulated/0/DCIM/a.jpg", "image/jpeg", "content://media/external/file/2")))
        assertFalse(keep(FileItem("t.pdf", "/storage/emulated/0/DCIM/.thumbnails/t.pdf", "application/pdf", "content://media/external/file/3")))
    }
}
