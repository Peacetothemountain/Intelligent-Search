package com.pixel.intelligentsearch.feature.appfunctions

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.ContactEmailsTest.FakeContacts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Assistants get contacts' names and numbers only, so their email addresses aren't even read. */
@RunWith(RobolectricTestRunner::class)
class AssistantContactSearchTest {

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        Robolectric.setupContentProvider(FakeContacts::class.java, "com.android.contacts")
        FakeContacts.emailQueries.clear()
    }

    @Test
    fun namesAndNumbersWithoutAnEmailQuery() = runBlocking {
        val results = contactSearchResults(context, "a", limit = 10)
        assertEquals(
            listOf(ContactSearchResult("Alex", "+14155552671"), ContactSearchResult("Sam", "+14155550000")),
            results
        )
        assertTrue(FakeContacts.emailQueries.isEmpty())
    }

    @Test
    fun theLimitStaysBetweenOneAndTen() = runBlocking {
        assertEquals(1, contactSearchResults(context, "a", limit = 0).size)
        assertEquals(2, contactSearchResults(context, "a", limit = 50).size)
    }
}
