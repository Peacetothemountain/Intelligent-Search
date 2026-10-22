package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.SystemDataProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * Contact rows show the contact's own photo, read from the contacts provider through the contact's lookup URI; a
 * contact without one keeps the person icon. Every contact used to get the icon, whatever its photo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactPhotosTest {

    @get:Rule val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        ResultThumbnails.clear()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(PhotoContacts::class.java, ContactsContract.AUTHORITY)
    }

    @After fun tearDown() = ResultThumbnails.clear()

    @Test
    fun aContactsPhotoIsReadAndOneWithoutAPhotoHasNone() = runBlocking {
        val contacts = SystemDataProvider.getAllContacts(context).associateBy { it.name }
        val alex = contactPhotoSource(contacts.getValue("Alex"))
        val sam = contactPhotoSource(contacts.getValue("Sam"))
        assertNotNull(ResultThumbnails.load(context, alex!!, px = 80))
        assertNull(ResultThumbnails.load(context, sam!!, px = 80))
    }

    @Test
    fun theRowShowsThePhotoInsteadOfThePersonIcon() {
        val contacts = runBlocking { SystemDataProvider.getAllContacts(context) }
        compose.setContent {
            Column {
                for (contact in contacts) ContactAvatar(contact, 40.dp) { Text("person icon") }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag(RESULT_THUMBNAIL_TAG, useUnmergedTree = true).assertCountEquals(1)
        // Sam has no photo and keeps the icon.
        compose.onAllNodesWithText("person icon").assertCountEquals(1)
    }

    @Test
    fun aContactWithoutALookupUriHasNoPhotoToRead() {
        assertNull(contactPhotoSource(com.pixel.intelligentsearch.core.data.ContactItem("Voicemail", "*86#", "")))
    }

    /** Alex has a photo, Sam has none; phone rows and each contact's photo row, as the contacts provider serves them. */
    class PhotoContacts : ContentProvider() {
        private val photo: ByteArray by lazy {
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: emptyArray()
            val cursor = MatrixCursor(columns)
            val segments = uri.pathSegments
            when {
                // content://com.android.contacts/contacts/lookup/<key>/<id>/photo
                segments.lastOrNull() == ContactsContract.Contacts.Photo.CONTENT_DIRECTORY -> {
                    if (segments.getOrNull(segments.size - 2) == "1") cursor.addRow(arrayOf<Any?>(photo))
                }
                segments.contains("emails") -> Unit
                else -> listOf(
                    mapOf(Phone.DISPLAY_NAME to "Alex", Phone.NUMBER to "+14155552671", Phone.CONTACT_ID to 1L, Phone.LOOKUP_KEY to "a"),
                    mapOf(Phone.DISPLAY_NAME to "Sam", Phone.NUMBER to "+14155550000", Phone.CONTACT_ID to 2L, Phone.LOOKUP_KEY to "s")
                ).forEach { row -> cursor.addRow(columns.map { row[it] }) }
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
