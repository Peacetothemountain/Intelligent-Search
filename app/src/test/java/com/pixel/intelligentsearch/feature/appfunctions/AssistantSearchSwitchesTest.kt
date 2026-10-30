package com.pixel.intelligentsearch.feature.appfunctions

import android.graphics.drawable.ColorDrawable
import com.pixel.intelligentsearch.core.data.AppItem
import com.pixel.intelligentsearch.core.data.IntelligentSearchSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Assistants get apps and sums only while the user has those searches on in the app, as contacts already did. */
@RunWith(RobolectricTestRunner::class)
class AssistantSearchSwitchesTest {

    private val apps = listOf(AppItem("Maps", "com.example.maps", ColorDrawable()), AppItem("Camera", "com.example.camera", ColorDrawable()))

    @Test
    fun appsOnlyWithAppSearchOn() = runBlocking {
        val on = IntelligentSearchSettings(searchApps = true)
        assertEquals(listOf(AppSearchResult("Maps", "com.example.maps")), appSearchResults(on, "map", 5) { apps })
        // Assistants were still handed the apps with app search off.
        assertEquals(emptyList<AppSearchResult>(), appSearchResults(on.copy(searchApps = false), "map", 5) { apps })
        // Settings that can't be read show nothing, so hidden apps never slip out.
        assertEquals(emptyList<AppSearchResult>(), appSearchResults(null, "map", 5) { apps })
        assertEquals(emptyList<AppSearchResult>(), appSearchResults(on.copy(hiddenApps = setOf("com.example.maps")), "map", 5) { apps })
    }

    @Test
    fun sumsOnlyWithTheCalculatorOn() {
        val on = IntelligentSearchSettings(searchCalculator = true)
        assertEquals(CalculationResult("2+2", "4"), calculation(on, " 2+2 "))
        assertNull(calculation(on.copy(searchCalculator = false), "2+2"))
        assertNull(calculation(null, "2+2"))
    }
}
