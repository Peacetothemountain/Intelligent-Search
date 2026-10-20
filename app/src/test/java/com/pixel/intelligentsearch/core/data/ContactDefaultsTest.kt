package com.pixel.intelligentsearch.core.data

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Which of a contact's numbers and addresses the buttons use: the ones marked as default, then (for chats) a mobile.
 * The contacts found, their order and the number shown don't change. The provider is a real SQLite table, so the
 * app's own selection and sort order decide what comes back, as on a device.
 */
@RunWith(RobolectricTestRunner::class)
class ContactDefaultsTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        SqlContacts.open()
        Robolectric.setupContentProvider(SqlContacts::class.java, "com.android.contacts")
    }

    @After fun tearDown() = SqlContacts.close()

    @Test
    fun theDefaultEmailAddressIsTheOneKept() = runBlocking {
        val robin = SystemDataProvider.getAllContacts(context).single { it.name == "Robin Example" }
        assertEquals("robin@example.com", robin.email)
        val found = SystemDataProvider.getContacts(context, "Robin").single { it.name == "Robin Example" }
        assertEquals("robin@example.com", found.email)
    }

    @Test
    fun chatsPreferTheDefaultNumberThenAMobile() = runBlocking {
        for (contacts in listOf(SystemDataProvider.getAllContacts(context), SystemDataProvider.getContacts(context, "Example"))) {
            val byName = contacts.associateBy { it.name }
            // Landline saved first, mobile second: chats go to the mobile.
            assertEquals("+1 202-555-0143", byName.getValue("Robin Example").chatNumber)
            // A mobile, then the number marked as default: chats go to the default.
            assertEquals("+1 202-555-0161", byName.getValue("Sam Example").chatNumber)
            // A mobile, then a primary number: the primary wins too.
            assertEquals("+1 202-555-0172", byName.getValue("Kim Example").chatNumber)
            // Only a landline: nothing better, so the shown number is used.
            assertEquals("", byName.getValue("Lee Example").chatNumber)
        }
    }

    @Test
    fun whichContactsAreFoundTheirOrderAndShownNumberDontChange() = runBlocking {
        val expected = listOf(
            "Kim Example" to "+1 202-555-0170",
            "Lee Example" to "+1 202-555-0180",
            "Robin Example" to "+1 202-555-0100",
            "Sam Example" to "+1 202-555-0150"
        )
        assertEquals(expected, SystemDataProvider.getAllContacts(context).map { it.name to it.phoneNumber })
        assertEquals(expected, SystemDataProvider.getContacts(context, "Example").map { it.name to it.phoneNumber })
    }

    /** The phone and email rows of four made-up contacts, served from SQLite like the real contacts provider. */
    class SqlContacts : ContentProvider() {
        companion object {
            private var db: SQLiteDatabase? = null

            fun open() {
                val db = SQLiteDatabase.create(null)
                this.db = db
                db.execSQL(
                    "CREATE TABLE phones (${Phone.DISPLAY_NAME} TEXT, ${Phone.NUMBER} TEXT, ${Phone.CONTACT_ID} INTEGER, " +
                        "${Phone.LOOKUP_KEY} TEXT, ${Phone.TYPE} INTEGER, ${Phone.IS_PRIMARY} INTEGER, ${Phone.IS_SUPER_PRIMARY} INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE emails (${Email.CONTACT_ID} INTEGER, ${Email.ADDRESS} TEXT, " +
                        "${Email.IS_PRIMARY} INTEGER, ${Email.IS_SUPER_PRIMARY} INTEGER)"
                )
                fun phone(name: String, id: Long, number: String, type: Int, primary: Int = 0, superPrimary: Int = 0) =
                    db.execSQL("INSERT INTO phones VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any>(name, number, id, "k$id", type, primary, superPrimary))
                phone("Robin Example", 1, "+1 202-555-0100", Phone.TYPE_HOME)
                phone("Robin Example", 1, "+1 202-555-0143", Phone.TYPE_MOBILE)
                phone("Sam Example", 2, "+1 202-555-0150", Phone.TYPE_MOBILE)
                phone("Sam Example", 2, "+1 202-555-0161", Phone.TYPE_WORK, primary = 1, superPrimary = 1)
                phone("Kim Example", 3, "+1 202-555-0170", Phone.TYPE_MOBILE)
                phone("Kim Example", 3, "+1 202-555-0172", Phone.TYPE_OTHER, primary = 1)
                phone("Lee Example", 4, "+1 202-555-0180", Phone.TYPE_HOME)
                // Robin's old address was saved first; the newer one is marked as default.
                db.execSQL("INSERT INTO emails VALUES (1, 'robin.old@example.com', 0, 0)")
                db.execSQL("INSERT INTO emails VALUES (1, 'robin@example.com', 1, 1)")
            }

            fun close() {
                db?.close()
                db = null
            }
        }

        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val db = checkNotNull(db)
            // Rows the app's order leaves tied come in the order they were saved; without an order, all of them do.
            val order = sortOrder?.let { "$it, rowid" }
            if (uri.pathSegments.contains("emails")) {
                return db.query("emails", projection, selection, args, null, null, order)
            }
            // A filter matches names containing its text; the real provider also matches numbers.
            val filter = if (uri.pathSegments.contains("filter")) uri.lastPathSegment.orEmpty() else ""
            return db.query("phones", projection, "${Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$filter%"), null, null, order)
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
