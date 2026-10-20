package com.pixel.intelligentsearch.core.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Contact results carry the contact's email address, for the Email button; contacts without one stay blank. */
@RunWith(RobolectricTestRunner::class)
class ContactEmailsTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(FakeContacts::class.java, "com.android.contacts")
        FakeContacts.emailQueries.clear()
    }

    @Test
    fun everyContactGetsItsOwnAddress() = runBlocking {
        val contacts = SystemDataProvider.getAllContacts(context).associateBy { it.name }
        assertEquals("alex@example.com", contacts.getValue("Alex").email)
        assertEquals("", contacts.getValue("Sam").email)
        assertEquals("+14155552671", contacts.getValue("Alex").phoneNumber)
    }

    @Test
    fun aSearchReadsOnlyTheMatchedContactsAddresses() = runBlocking {
        val contacts = SystemDataProvider.getContacts(context, "a")
        assertEquals("alex@example.com", contacts.single { it.name == "Alex" }.email)
        assertEquals(listOf(listOf("1", "2")), FakeContacts.emailQueries)
    }

    /** Two phone contacts (Alex with an email, Sam without) and their email rows. */
    class FakeContacts : ContentProvider() {
        companion object {
            val emailQueries = mutableListOf<List<String>>()
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: emptyArray()
            val cursor = MatrixCursor(columns)
            val isEmail = uri.pathSegments.contains("emails")
            if (isEmail) {
                emailQueries += args?.toList() ?: emptyList()
                val rows = listOf(mapOf(Email.CONTACT_ID to 1L, Email.ADDRESS to "alex@example.com"))
                rows.filter { args == null || it[Email.CONTACT_ID].toString() in args }
                    .forEach { row -> cursor.addRow(columns.map { row[it] }) }
            } else {
                listOf(
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
