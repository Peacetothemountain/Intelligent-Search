package com.pixel.intelligentsearch.feature.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class QuickAppSearchIntentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun knownAppsGetTheirOwnSearch() {
        val chrome = quickAppSearchIntent(context, "com.android.chrome", "a b")!!
        assertEquals("https://google.com/search?q=a%20b", chrome.dataString)
        assertEquals("com.android.chrome", chrome.`package`)

        assertEquals("geo:0,0?q=pizza", quickAppSearchIntent(context, "com.google.android.apps.maps", "pizza")!!.dataString)
        assertEquals("lofi", quickAppSearchIntent(context, "com.google.android.youtube", "lofi")!!.getStringExtra("query"))
        assertEquals("market://search?q=maps", quickAppSearchIntent(context, "com.android.vending", "maps")!!.dataString)
    }

    @Test
    fun thePlayStoreSearchOpensOnlyInThePlayStore() {
        // Other stores also answer market:// links; the pill names the Play Store.
        assertEquals("com.android.vending", quickAppSearchIntent(context, "com.android.vending", "maps")!!.`package`)
    }

    @Test
    fun contactsAndFilesFallBackToAPickerWhenNotInstalled() {
        assertEquals(Intent.ACTION_PICK, quickAppSearchIntent(context, "com.google.android.contacts", "x")!!.action)
        assertEquals(Intent.ACTION_GET_CONTENT, quickAppSearchIntent(context, "com.google.android.apps.nbu.files", "x")!!.action)
    }

    @Test
    fun otherAppsUseAnExportedSearchActivityOnly() {
        assertNull(quickAppSearchIntent(context, "org.example.notes", "x"))

        val component = ComponentName("org.example.notes", "org.example.notes.Search")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_SEARCH).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        pm.addOrUpdateActivity(context.packageManager.getActivityInfo(component, 0).apply { exported = true })

        val intent = quickAppSearchIntent(context, "org.example.notes", "groceries")!!
        assertEquals(Intent.ACTION_SEARCH, intent.action)
        assertEquals("groceries", intent.getStringExtra("query"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
