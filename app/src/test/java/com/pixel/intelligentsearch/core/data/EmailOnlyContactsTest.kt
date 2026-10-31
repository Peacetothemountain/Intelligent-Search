package com.pixel.intelligentsearch.core.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.feature.search.ContactActionKind
import com.pixel.intelligentsearch.feature.search.ContactActionTargets
import com.pixel.intelligentsearch.feature.search.ContactActions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A contact saved with only an email address (common for ones synced from Gmail) is found like any other: it shows
 * its address and offers Email, not Call or Message. One with neither a number nor an address isn't shown.
 */
@RunWith(RobolectricTestRunner::class)
class EmailOnlyContactsTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(FakeContacts::class.java, "com.android.contacts")
    }

    @Test
    fun theIndexHasAContactWithOnlyAnEmailAddress() = runBlocking {
        val contacts = SystemDataProvider.getAllContacts(context).associateBy { it.name }
        // Only phone rows were read, so Pat was never found.
        val pat = contacts.getValue("Pat Example")
        assertEquals("", pat.phoneNumber)
        assertEquals("pat@example.com", pat.email)
        assertEquals("pat@example.com", pat.detail)
        assertTrue(pat.lookupUri.isNotEmpty())
        assertEquals("+14155552671", contacts.getValue("Alex").detail)
        assertEquals(setOf("Alex", "Pat Example"), contacts.keys)
    }

    @Test
    fun aSearchFindsAContactWithOnlyAnEmailAddress() = runBlocking {
        val found = SystemDataProvider.getContacts(context, "pat")
        assertEquals(listOf("pat@example.com"), found.map { it.email })
        assertEquals("Pat Example", found.single().name)
    }

    @Test
    fun itOffersEmailNotCallOrMessage() {
        val pat = ContactItem("Pat Example", "", "content://com.android.contacts/contacts/lookup/pat/7", email = "pat@example.com")
        val targets = ContactActionTargets(ContactActionKind.entries.toSet(), countryIso = "US")
        val actions = ContactActions.forContact(pat, targets)
        assertEquals(listOf(ContactActionKind.EMAIL), actions.map { it.kind })
        assertEquals(Intent.ACTION_SENDTO, actions.single().intent.action)
        assertEquals("mailto", actions.single().intent.data?.scheme)
        assertEquals("pat@example.com", actions.single().intent.data?.schemeSpecificPart)
        // Direct Call has no number to call: tapping it opens the contact's card.
        assertEquals(Intent.ACTION_VIEW, ContactActions.openIntent(pat, directCall = true).action)
    }

    /**
     * Alex has a number; Pat Example has only an email address; Nobody has neither. Answers the phone, contacts and
     * email tables with the columns asked for, filtering by name, "has a number" and contact ids as Android does.
     */
    class FakeContacts : ContentProvider() {
        private val people = listOf(
            mapOf(Contacts._ID to 1L, Contacts.DISPLAY_NAME to "Alex", Contacts.LOOKUP_KEY to "alex", Contacts.HAS_PHONE_NUMBER to 1),
            mapOf(Contacts._ID to 7L, Contacts.DISPLAY_NAME to "Pat Example", Contacts.LOOKUP_KEY to "pat", Contacts.HAS_PHONE_NUMBER to 0),
            mapOf(Contacts._ID to 8L, Contacts.DISPLAY_NAME to "Nobody", Contacts.LOOKUP_KEY to "nobody", Contacts.HAS_PHONE_NUMBER to 0)
        )
        private val phones = listOf(
            mapOf(Phone.CONTACT_ID to 1L, Phone.NUMBER to "+14155552671", Phone.DISPLAY_NAME to "Alex", Phone.LOOKUP_KEY to "alex")
        )
        private val emails = listOf(mapOf(Email.CONTACT_ID to 7L, Email.ADDRESS to "pat@example.com"))

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: emptyArray()
            val segments = uri.pathSegments
            val filter = if ("filter" in segments) uri.lastPathSegment.orEmpty() else null
            val rows = when {
                segments.first() == "contacts" -> people
                    .filter { selection == null || !selection.contains(Contacts.HAS_PHONE_NUMBER) || it[Contacts.HAS_PHONE_NUMBER] == 0 }
                    .filter { selection == null || !selection.contains(Contacts.STARRED) }
                    .filter { filter == null || (it[Contacts.DISPLAY_NAME] as String).contains(filter, ignoreCase = true) }
                "emails" in segments -> emails.filter { args == null || it[Email.CONTACT_ID].toString() in args }
                else -> phones
                    .filter { filter == null || (it[Phone.DISPLAY_NAME] as String).contains(filter, ignoreCase = true) }
                    .filter { args == null || it[Phone.CONTACT_ID].toString() in args }
            }
            return MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
