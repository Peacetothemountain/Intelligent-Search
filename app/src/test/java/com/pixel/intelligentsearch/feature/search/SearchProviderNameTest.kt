package com.pixel.intelligentsearch.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The engine's name in the search bar and on "Search …": a custom engine goes by the Search App Name the user typed in
 * Web Search ("My SearX", not "Searx" from its address), by its site's name while that field is left at its default,
 * and by Google until its address is valid, as searches do.
 */
@RunWith(RobolectricTestRunner::class)
class SearchProviderNameTest {

    private val searx = "https://searx.example.org/search?q=%s"

    @Test
    fun aCustomEngineGoesByTheNameTheUserGaveIt() {
        assertEquals("My SearX", searchProviderName("Custom", searx, "My SearX"))
        assertEquals("My SearX", searchProviderName("Custom", searx, "  My SearX "))
    }

    @Test
    fun leftUnnamedItGoesByItsSitesName() {
        assertEquals("Searx", searchProviderName("Custom", searx, "Custom"))
        assertEquals("Searx", searchProviderName("Custom", searx, " "))
    }

    @Test
    fun untilItsAddressIsValidSearchesAndTheNameAreGoogles() {
        assertEquals("Google", searchProviderName("Custom", "", "My SearX"))
        assertEquals("Google", searchProviderName("Custom", "notaurl", "My SearX"))
    }

    @Test
    fun theBuiltInEnginesKeepTheirNames() {
        assertEquals("DuckDuckGo", searchProviderName("DuckDuckGo", searx, "My SearX"))
        assertEquals("Google", searchProviderName("Google", "", "Custom"))
        assertEquals("Google", searchProviderName("", "", "Custom"))
    }
}
