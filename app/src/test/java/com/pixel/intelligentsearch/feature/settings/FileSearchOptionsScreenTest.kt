package com.pixel.intelligentsearch.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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

/** File Search settings' File Types chips, skipped subfolders and Open Files With menus. */
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
    fun aTypeChipSwitchesThatTypeOutOfSearchAndBackIn() {
        show()
        compose.onNode(hasText("PDFs") and isToggleable()).performScrollTo().assertIsOn().performClick()
        compose.settle(steps = 3)
        assertEquals(setOf(FileTypeGroup.PDF), UserGrantedFiles.excludedTypes(prefs))
        compose.onNode(hasText("PDFs") and isToggleable()).assertIsOff().performClick()
        compose.settle(steps = 3)
        assertEquals(emptySet<FileTypeGroup>(), UserGrantedFiles.excludedTypes(prefs))
    }

    @Test
    fun theLastSearchedTypeStaysOn() {
        val allButPdfs = FileTypeGroup.entries.filter { it != FileTypeGroup.PDF }.map { it.id }.toSet()
        show { putStringSet(SettingsManager.FILES_EXCLUDED_TYPES.name, allButPdfs) }
        compose.onNode(hasText("PDFs") and isToggleable()).performScrollTo().assertIsOn().performClick()
        compose.settle(steps = 3)
        compose.onNode(hasText("PDFs") and isToggleable()).assertIsOn()
        assertEquals(allButPdfs, prefs[SettingsManager.FILES_EXCLUDED_TYPES])
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
    fun eachTypeOpensInItsDefaultAppUntilChosenOtherwise() {
        show()
        compose.onNodeWithText("Open Files With").performScrollTo()
        compose.onNode(hasText("Presentations") and hasText("Default App")).performScrollTo().performClick()
        compose.settle(steps = 3)
        compose.onNodeWithText("Ask Every Time").performClick()
        compose.settle(steps = 3)
        assertEquals("ask", prefs[SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PRESENTATIONS)])
        assertEquals("default", prefs[SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PDF)])
    }
}
