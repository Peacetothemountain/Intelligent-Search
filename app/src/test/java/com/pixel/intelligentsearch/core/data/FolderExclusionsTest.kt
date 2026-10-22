package com.pixel.intelligentsearch.core.data

import com.pixel.intelligentsearch.core.data.FolderExclusions.Excluded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which folders inside the chosen ones file search skips. */
class FolderExclusionsTest {

    private val storage = "com.android.externalstorage.documents"
    private val drive = "com.google.android.apps.docs.storage"

    @Test
    fun aTreeUriNamesItsProviderAndFolder() {
        assertEquals(
            Excluded(storage, "primary:Documents/Old"),
            FolderExclusions.parseTreeUri("content://$storage/tree/primary%3ADocuments%2FOld")
        )
        // Trailing slashes, in the URI or the ID, name the same folder.
        assertEquals(
            Excluded(storage, "primary:Documents/Old"),
            FolderExclusions.parseTreeUri("content://$storage/tree/primary%3ADocuments%2FOld%2F/")
        )
        // A "+" in a folder name stays a plus; spaces arrive escaped.
        assertEquals(
            Excluded(storage, "primary:C++ Notes"),
            FolderExclusions.parseTreeUri("content://$storage/tree/primary%3AC++%20Notes")
        )
        assertNull(FolderExclusions.parseTreeUri("content://$storage/document/primary%3ADocuments"))
        assertNull(FolderExclusions.parseTreeUri("file:///sdcard/Documents"))
        assertNull(FolderExclusions.parseTreeUri(""))
    }

    @Test
    fun anExcludedFolderCoversItsSubfoldersButNotSiblingsWithTheSamePrefix() {
        val old = listOf(Excluded(storage, "primary:Documents/Old"))
        assertTrue(FolderExclusions.isExcluded(storage, "primary:Documents/Old", old))
        assertTrue(FolderExclusions.isExcluded(storage, "primary:Documents/Old/2019", old))
        assertTrue(FolderExclusions.isExcluded(storage, "primary:Documents/Old/", old))
        assertFalse(FolderExclusions.isExcluded(storage, "primary:Documents/Older", old))
        assertFalse(FolderExclusions.isExcluded(storage, "primary:Documents", old))
        // Same path on another volume or provider is another folder.
        assertFalse(FolderExclusions.isExcluded(storage, "1234-ABCD:Documents/Old", old))
        assertFalse(FolderExclusions.isExcluded("other.provider", "primary:Documents/Old", old))
    }

    @Test
    fun phoneStorageMatchesWhateverTheLetterCase() {
        val old = listOf(Excluded(storage, "primary:Documents/Old"))
        assertTrue(FolderExclusions.isExcluded(storage, "primary:documents/OLD/Scans", old))
    }

    @Test
    fun opaqueIdsMatchOnlyExactly() {
        val folder = listOf(Excluded(drive, "doc=abc123"))
        assertTrue(FolderExclusions.isExcluded(drive, "doc=abc123", folder))
        assertFalse(FolderExclusions.isExcluded(drive, "doc=abc1234", folder))
        assertFalse(FolderExclusions.isExcluded(drive, "DOC=ABC123", folder))
    }

    @Test
    fun aVolumeRootCoversTheWholeVolume() {
        val root = listOf(Excluded(storage, "primary:"))
        assertTrue(FolderExclusions.isExcluded(storage, "primary:Music", root))
        assertFalse(FolderExclusions.isExcluded(storage, "1234-ABCD:Music", root))
    }

    @Test
    fun onlyFoldersInsideAChosenFolderCanBeExcluded() {
        val documents = Excluded(storage, "primary:Documents")
        assertTrue(FolderExclusions.isInside(documents, Excluded(storage, "primary:Documents/Old")))
        assertTrue(FolderExclusions.isInside(documents, Excluded(storage, "primary:documents/old/2019")))
        assertFalse(FolderExclusions.isInside(documents, documents))
        assertFalse(FolderExclusions.isInside(documents, Excluded(storage, "primary:Documents/")))
        assertFalse(FolderExclusions.isInside(documents, Excluded(storage, "primary:Download/Old")))
        assertFalse(FolderExclusions.isInside(documents, Excluded(storage, "primary:DocumentsOld")))
        assertFalse(FolderExclusions.isInside(documents, Excluded(drive, "primary:Documents/Old")))
        // Opaque IDs can't be placed: another folder of the same provider is taken as inside.
        assertTrue(FolderExclusions.isInside(Excluded(drive, "root"), Excluded(drive, "doc=abc123")))
        assertFalse(FolderExclusions.isInside(Excluded(drive, "root"), Excluded(drive, "root")))
    }

    @Test
    fun hiddenFoldersThumbnailsAndAppDataAreAlwaysSkipped() {
        assertTrue(FolderExclusions.isAlwaysSkipped("Pixel/DCIM", ".thumbnails"))
        assertTrue(FolderExclusions.isAlwaysSkipped("Documents", ".git"))
        assertTrue(FolderExclusions.isAlwaysSkipped("Pixel/Android", "data"))
        assertTrue(FolderExclusions.isAlwaysSkipped("Pixel/android/", "Data"))
        assertFalse(FolderExclusions.isAlwaysSkipped("Pixel/Android", "media"))
        assertFalse(FolderExclusions.isAlwaysSkipped("Pixel/Work", "data"))
        assertFalse(FolderExclusions.isAlwaysSkipped("Pixel", "Android"))
        assertFalse(FolderExclusions.isAlwaysSkipped("Documents", "Old"))
    }

    @Test
    fun filesInSkippedFoldersAreLeftOutOfTheIndex() {
        assertTrue(FolderExclusions.isInAlwaysSkippedFolder("/storage/emulated/0/DCIM/.thumbnails/1.jpg"))
        assertTrue(FolderExclusions.isInAlwaysSkippedFolder("/storage/emulated/0/Android/data/com.app/cache/a.png"))
        assertFalse(FolderExclusions.isInAlwaysSkippedFolder("/storage/emulated/0/Download/report.pdf"))
        // A hidden file in a normal folder is the Show Hidden Files switch's business, not a skipped folder.
        assertFalse(FolderExclusions.isInAlwaysSkippedFolder("/storage/emulated/0/Download/.hidden.pdf"))
        assertFalse(FolderExclusions.isInAlwaysSkippedFolder(""))
    }

    @Test
    fun anExcludedFolderIsShownByItsPath() {
        assertEquals("Documents/Old", FolderExclusions.label(Excluded(storage, "primary:Documents/Old")))
        assertEquals("primary:", FolderExclusions.label(Excluded(storage, "primary:")))
        assertEquals("abc", FolderExclusions.label(Excluded(drive, "folders/abc")))
    }
}
