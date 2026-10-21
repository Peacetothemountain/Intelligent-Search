package com.pixel.intelligentsearch.core.data

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.CorpusIndexManager
import com.pixel.intelligentsearch.feature.search.FakeDeviceSearchSources
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton

/** A contact row in the system's global search dials the contact's whole number when it has no Contacts card. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class GlobalSearchContactRowTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FakeSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FakeDeviceSearchSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var sources: DeviceSearchSources
    @Inject lateinit var corpusIndexManager: CorpusIndexManager

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hilt.inject()
        context.getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .clear().put(SettingsManager.SEARCH_CONTACTS, true).commit()
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    /** The intent data of the global search row titled [title] for [query]. */
    private fun rowData(query: String, title: String): String {
        corpusIndexManager.initialize()
        runBlocking { withTimeout(10_000) { corpusIndexManager.isIndexReady.first { it } } }
        val provider = Robolectric.buildContentProvider(GlobalSearchProvider::class.java).create().get()
        provider.query(GlobalSearchProvider.CONTENT_URI, null, null, arrayOf(query), null).use { cursor ->
            val text = cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_TEXT_1)
            val data = cursor.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_INTENT_DATA)
            while (cursor.moveToNext()) {
                if (cursor.getString(text) == title) return cursor.getString(data)
            }
        }
        throw AssertionError("no row titled $title for $query")
    }

    @Test
    fun aServiceCodeWithoutACardIsDialledWhole() {
        (sources as FakeDeviceSearchSources).contactBook = listOf(ContactItem("Voicemail", "*86#", ""))

        val data = Uri.parse(rowData("Voicemail", "Voicemail"))

        assertEquals("tel", data.scheme)
        // Read as a "tel:" link, '#' starts the fragment and the number was cut to "*86".
        assertEquals("*86#", data.schemeSpecificPart)
    }

    @Test
    fun aContactWithACardOpensIt() {
        (sources as FakeDeviceSearchSources).contactBook = listOf(ContactItem("Voicemail", "*86#", "content://contacts/voicemail"))

        assertEquals("content://contacts/voicemail", rowData("Voicemail", "Voicemail"))
    }
}
