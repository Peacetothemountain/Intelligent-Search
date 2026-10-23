package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.UserGrantedFiles
import com.pixel.intelligentsearch.core.data.settings.get
import com.pixel.intelligentsearch.core.theme.IntelligentSearchTheme
import com.pixel.intelligentsearch.screenshots.confinedEffects
import com.pixel.intelligentsearch.screenshots.settle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** File Search settings: no File Types picker, the skipped subfolders, and the one Open Files With row and its dialog. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h2600dp-xhdpi")
class FileSearchOptionsScreenTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule(confinedEffects())

    private val prefs
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val documents = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    private val documentsOld = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FOld"

    private fun show(setUp: android.content.SharedPreferences.Editor.() -> Unit = {}) {
        hilt.inject()
        prefs.edit().clear().apply(setUp).commit()
        compose.setContent { IntelligentSearchTheme(darkTheme = false) { FileSearchScreen(prefs) {} } }
        compose.settle(steps = 5)
    }

    @Test
    fun thereIsNoFileTypesPicker() {
        // File search covers every type; a search like "pdf invoice" narrows it to one.
        show()
        compose.onNodeWithText("File Types").assertDoesNotExist()
        compose.onNode(hasText("PDFs") and isToggleable()).assertDoesNotExist()
    }
    @Test
    fun skippedSubfoldersAreListedAndTappingOneSearchesItAgain() {
        show {
            putStringSet(UserGrantedFiles.KEY_FOLDER_URIS, setOf(documents))
            putStringSet(SettingsManager.FILES_EXCLUDED_FOLDERS.name, setOf(documentsOld))
        }
        compose.onNodeWithText("Skip a Subfolder").assertExists()
        compose.onNodeWithText("Documents/Old").performScrollTo().performClick()
        compose.settle(steps = 3)
        assertEquals(emptySet<String>(), UserGrantedFiles.excludedFolderUris(prefs))
        compose.onNodeWithText("Documents/Old").assertDoesNotExist()
    }

    @Test
    fun withoutAChosenFolderThereIsNoSubfolderToSkip() {
        show()
        compose.onNodeWithText("Skip a Subfolder").assertDoesNotExist()
    }

    @Test
    fun oneOpenFilesWithRowListsEveryTypeAndEachOpensInItsDefaultAppUntilChosenOtherwise() {
        show()
        // One row on the page, not a menu per type.
        compose.onNodeWithText("Presentations").assertDoesNotExist()
        compose.onNodeWithText("Open Files With").performScrollTo().performClick()
        compose.settle(steps = 3)
        for (group in FileTypeGroup.entries) compose.onNodeWithText(group.label).assertExists()
        compose.onNode(hasText("Presentations") and hasText("Default App")).performScrollTo().performClick()
        compose.settle(steps = 3)
        compose.onNodeWithText("Ask Every Time").performClick()
        compose.settle(steps = 3)
        assertEquals("ask", prefs[SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PRESENTATIONS)])
        assertEquals("default", prefs[SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PDF)])
        // Back on the list of types, with the new choice under Presentations.
        compose.onNode(hasText("Presentations") and hasText("Ask Every Time")).assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.settle(steps = 3)
        compose.onNodeWithText("Presentations").assertDoesNotExist()
    }
}
