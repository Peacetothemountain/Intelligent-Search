package com.pixel.intelligentsearch.feature.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileOpeners
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.OpenWithChoice
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Opening a file result with the app chosen for its type under Open Files With. */
@RunWith(RobolectricTestRunner::class)
class OpenFileIntentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    private val pdf = FileItem("Invoice.pdf", "Documents/Invoice.pdf", "application/pdf", "content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments%2FInvoice.pdf")
    private val ownMedia = FileItem("clip.mp4", "/storage/emulated/0/Movies/clip.mp4", "video/mp4", "content://media/external/file/7")

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    /** Installs [pkg] with a viewer for [mime]. */
    private fun installViewer(pkg: String, mime: String, label: String = pkg) {
        val component = ComponentName(pkg, "$pkg.Viewer")
        val pm = shadowOf(context.packageManager)
        pm.installPackage(android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg
                nonLocalizedLabel = label
            }
        })
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataType(mime)
        })
        pm.addOrUpdateActivity(context.packageManager.getActivityInfo(component, 0).apply { exported = true })
    }

    @Test
    fun theDefaultAppIsAskedForWithReadAccess() {
        val intent = buildOpenFileIntent(pdf.uri, pdf.mimeType, OpenWithChoice.DefaultApp)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(pdf.uri, intent.dataString)
        assertEquals("application/pdf", intent.type)
        assertNull(intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun aChosenAppIsTheOneThatOpensIt() {
        val intent = buildOpenFileIntent(pdf.uri, pdf.mimeType, OpenWithChoice.App("com.adobe.reader"))
        assertEquals("com.adobe.reader", intent.`package`)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun askEveryTimeShowsTheOpenWithSheetCarryingTheGrant() {
        val chooser = buildOpenFileIntent(pdf.uri, pdf.mimeType, OpenWithChoice.AskEveryTime)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_VIEW, target.action)
        assertEquals(pdf.uri, target.dataString)
        assertNull(target.`package`)
    }

    @Test
    fun phoneStorageFilesNoLongerAlwaysGoToFilesByGoogle() {
        val intent = openFileIntent(context.packageManager, prefs, ownMedia)
        assertNull(intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun theChoiceForTheFilesTypeIsUsed() {
        installViewer("com.example.pdfviewer", "application/pdf")
        prefs.edit().put(SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PDF), OpenWithChoice.App("com.example.pdfviewer").stored).commit()
        prefs.edit().put(SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.VIDEO), OpenWithChoice.AskEveryTime.stored).commit()

        assertEquals("com.example.pdfviewer", openFileIntent(context.packageManager, prefs, pdf).`package`)
        assertEquals(Intent.ACTION_CHOOSER, openFileIntent(context.packageManager, prefs, ownMedia).action)
    }

    @Test
    fun anUninstalledChosenAppFallsBackToTheOpenWithSheet() {
        prefs.edit().put(SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.PDF), OpenWithChoice.App("com.example.gone").stored).commit()
        assertEquals(Intent.ACTION_CHOOSER, openFileIntent(context.packageManager, prefs, pdf).action)
    }

    @Test
    fun aChosenAppThatCantOpenThisFileFallsBackToTheOpenWithSheet() {
        // Chosen for documents, but it only opens plain text, not this Word file.
        installViewer("com.example.notes", "text/plain")
        prefs.edit().put(SettingsManager.FILES_OPEN_WITH.getValue(FileTypeGroup.DOCUMENTS), OpenWithChoice.App("com.example.notes").stored).commit()
        val letter = FileItem("letter.docx", "Documents/letter.docx", "application/octet-stream", "content://x/document/1")

        val intent = openFileIntent(context.packageManager, prefs, letter)
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        @Suppress("DEPRECATION")
        val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        // Offered as what its extension says, since its provider only said "bytes".
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", target.type)
    }

    @Test
    fun storedChoicesReadBackAndJunkMeansTheDefaultApp() {
        assertEquals(OpenWithChoice.AskEveryTime, OpenWithChoice.parse("ask"))
        assertEquals(OpenWithChoice.DefaultApp, OpenWithChoice.parse("default"))
        assertEquals(OpenWithChoice.App("com.adobe.reader"), OpenWithChoice.parse("app:com.adobe.reader"))
        for (junk in listOf("", "app:", "app:not a package", "app:noDots", "ask ", "app:com.x;rm -rf")) {
            assertEquals(junk, OpenWithChoice.DefaultApp, OpenWithChoice.parse(junk))
        }
        for (choice in listOf(OpenWithChoice.AskEveryTime, OpenWithChoice.DefaultApp, OpenWithChoice.App("org.example.app"))) {
            assertEquals(choice, OpenWithChoice.parse(choice.stored))
        }
    }

    @Test
    fun theAppsOfferedAreThoseThatOpenTheType() {
        installViewer("com.example.pdfviewer", "application/pdf", "Zed PDF")
        installViewer("com.example.reader", "application/pdf", "Acme Reader")
        installViewer("com.example.player", "video/mp4")

        val apps = FileOpeners.appsFor(context.packageManager, FileTypeGroup.PDF, context.packageName)
        assertEquals(listOf("Acme Reader", "Zed PDF"), apps.map { it.label })
        assertTrue(FileOpeners.appsFor(context.packageManager, FileTypeGroup.ARCHIVES, context.packageName).isEmpty())
    }
}
