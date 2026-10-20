package com.pixel.intelligentsearch.feature.search

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.pixel.intelligentsearch.core.data.SettingsManager
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * A made-up address book for overlay tests: two contacts named Robin, with numbers from the 555-01xx range kept for
 * fiction, plus the apps that answer the contact buttons (a dialer, an SMS app, an email app and WhatsApp).
 */
internal object FakeContactBook {

    const val QUERY = "Robin"

    private val phones = listOf(
        mapOf(Phone.DISPLAY_NAME to "Robin Example", Phone.NUMBER to "+1 202-555-0143", Phone.CONTACT_ID to 1L, Phone.LOOKUP_KEY to "robin-example"),
        mapOf(Phone.DISPLAY_NAME to "Robin Sample", Phone.NUMBER to "+1 202-555-0187", Phone.CONTACT_ID to 2L, Phone.LOOKUP_KEY to "robin-sample")
    )
    private val emails = listOf(mapOf(Email.CONTACT_ID to 1L, Email.ADDRESS to "robin@example.com"))

    /** Turns contact search on, grants contacts access and serves the two contacts from the contacts provider. */
    fun install(context: Application) {
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(SettingsManager.SEARCH_CONTACTS.name, true).commit()
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(Provider::class.java, "com.android.contacts")
    }

    /** Installs apps for Call, Message, Email and WhatsApp. */
    fun installContactApps(context: Context) {
        install(context, "org.example.dialer", IntentFilter(Intent.ACTION_DIAL).apply { addDataScheme("tel") })
        install(context, "org.example.sms", IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("smsto") })
        install(context, "org.example.mail", IntentFilter(Intent.ACTION_SENDTO).apply { addDataScheme("mailto") })
        install(context, "com.whatsapp", IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("https")
            addDataAuthority("wa.me", null)
        })
    }

    private fun install(context: Context, packageName: String, filter: IntentFilter) {
        val component = ComponentName(packageName, "$packageName.Main")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        pm.addIntentFilterForActivity(component, filter)
    }

    /** Phone rows (whole book, or those whose name contains a filter's text) and email rows. */
    class Provider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection ?: emptyArray()
            val cursor = MatrixCursor(columns)
            val rows = if (uri.pathSegments.contains("emails")) {
                emails.filter { args == null || it[Email.CONTACT_ID].toString() in args }
            } else {
                val filter = if (uri.pathSegments.contains("filter")) uri.lastPathSegment.orEmpty() else ""
                phones.filter { (it[Phone.DISPLAY_NAME] as String).contains(filter, ignoreCase = true) }
            }
            rows.forEach { row -> cursor.addRow(columns.map { row[it] }) }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
