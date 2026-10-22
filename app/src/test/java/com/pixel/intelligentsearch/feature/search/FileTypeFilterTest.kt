package com.pixel.intelligentsearch.feature.search

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.HiltTestActivity
import com.pixel.intelligentsearch.core.data.DeviceSearchSources
import com.pixel.intelligentsearch.core.data.DeviceSearchSourcesModule
import com.pixel.intelligentsearch.core.data.FileItem
import com.pixel.intelligentsearch.core.data.FileTypeGroup
import com.pixel.intelligentsearch.core.data.SettingsManager
import com.pixel.intelligentsearch.core.data.settings.put
import com.pixel.intelligentsearch.core.search.UnifiedSearchCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject
import javax.inject.Singleton

/** A device whose only files are a few documents, for searches that name a file type. */
class FileTypeSources : DeviceSearchSources by FakeDeviceSearchSources() {
    override suspend fun recentFiles(limit: Int, includeHidden: Boolean) = FILES
    override suspend fun upcomingEvents() = emptyList<com.pixel.intelligentsearch.core.data.CalendarEvent>()

    companion object {
        private fun file(name: String, mime: String, id: Int, modified: Long) =
            FileItem(name, "/storage/emulated/0/Documents/$name", mime, "content://media/external/file/$id", modified)

        val INVOICE_PDF = file("Invoice March.pdf", "application/pdf", 1, 3_000)
        val INVOICE_DOC = file("Invoice March.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2, 2_000)
        val TAX_PDF = file("Tax Return 2025.pdf", "application/pdf", 3, 1_000)
        val FILES = listOf(INVOICE_PDF, INVOICE_DOC, TAX_PDF)
    }
}

/** A query that starts with a file type ("pdf invoice") limits file results to that type until its chip is removed. */
@HiltAndroidTest
@UninstallModules(DeviceSearchSourcesModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class FileTypeFilterTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object FileSourcesModule {
        @Provides
        @Singleton
        fun provideSources(): DeviceSearchSources = FileTypeSources()
    }

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var coordinator: UnifiedSearchCoordinator

    private lateinit var viewModel: SearchViewModel
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SettingsManager.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        hilt.inject()
        prefs.edit().clear().put(SettingsManager.SEARCH_FILES, true).commit()
        coordinator.replaceFiles(FileTypeSources.FILES)
        val scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.onActivity { viewModel = ViewModelProvider(it)[SearchViewModel::class.java] }
    }

    /** Searches [query] and waits for its local results. */
    private fun search(query: String): SearchUiState {
        viewModel.onQueryChanged(query)
        var done = false
        viewModel.viewModelScope.launch { viewModel.awaitSearch(); done = true }
        return await(query) { done && it.query == query }
    }

    private fun await(what: String, condition: (SearchUiState) -> Boolean): SearchUiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            val state = viewModel.uiState.value
            if (condition(state)) return state
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for $what; state=${viewModel.uiState.value}")
    }

    @Test
    fun aTypeWordLimitsFilesToThatTypeAndShowsItsChip() {
        val state = search("pdf invoice")
        assertEquals(FileTypeGroup.PDF, state.fileTypeFilter)
        assertEquals(listOf(FileTypeSources.INVOICE_PDF), state.files)

        val docs = search("doc invoice")
        assertEquals(FileTypeGroup.DOCUMENTS, docs.fileTypeFilter)
        assertEquals(listOf(FileTypeSources.INVOICE_DOC), docs.files)

        // Without a type word every type is searched.
        val plain = search("invoice")
        assertNull(plain.fileTypeFilter)
        assertEquals(setOf(FileTypeSources.INVOICE_PDF, FileTypeSources.INVOICE_DOC), plain.files.toSet())
    }

    @Test
    fun aTypeAloneListsItsLatestFiles() {
        assertEquals(listOf(FileTypeSources.INVOICE_PDF, FileTypeSources.TAX_PDF), search("pdfs").files)
    }

    @Test
    fun removingTheChipSearchesEveryTypeUntilTheQueryNamesATypeAfresh() {
        assertEquals(FileTypeGroup.PDF, search("pdf invoice").fileTypeFilter)

        viewModel.clearFileTypeFilter()
        val cleared = await("chip removed") { it.fileTypeFilter == null }
        assertEquals("pdf invoice", cleared.query)

        // Typing on keeps it removed...
        assertNull(search("pdf invoice m").fileTypeFilter)
        // ...until the query stops naming that type and then names one again.
        assertNull(search("pd").fileTypeFilter)
        val again = search("pdf tax")
        assertEquals(FileTypeGroup.PDF, again.fileTypeFilter)
        assertEquals(listOf(FileTypeSources.TAX_PDF), again.files)
    }

    @Test
    fun withFileSearchOffNothingIsFiltered() {
        prefs.edit().put(SettingsManager.SEARCH_FILES, false).commit()
        val state = search("pdf invoice")
        assertNull(state.fileTypeFilter)
        assertEquals(emptyList<FileItem>(), state.files)
    }
}
