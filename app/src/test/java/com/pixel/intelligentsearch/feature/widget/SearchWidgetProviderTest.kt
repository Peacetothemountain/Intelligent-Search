package com.pixel.intelligentsearch.feature.widget

import android.appwidget.AppWidgetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWidgetProviderTest {

    @Test
    fun testForcedIsMaterial_defaultsToFalseForSystemWidget() {
        val systemProvider = SearchWidgetProvider()
        assertEquals(false, systemProvider.forcedIsMaterial)
    }

    @Test
    fun testForcedIsMaterial_defaultsToTrueForMaterialWidget() {
        val materialProvider = SearchWidgetMaterialProvider()
        assertEquals(true, materialProvider.forcedIsMaterial)
    }

    @Test
    fun testResolveIsMaterialYou_forcedFalseOverridesSavedMaterialThemeStyle() {
        // Even if SharedPreferences has "Material You (Minimal)" or "Material Design",
        // placing the System Search Bar (forcedIsMaterial = false) must return false
        val result = SearchWidgetProvider.resolveIsMaterialYou(
            appWidgetManager = null,
            appWidgetId = 101,
            forcedIsMaterial = false,
            widgetThemeStyle = "Material You (Minimal)"
        )
        assertFalse("System search bar must not transform into Material You", result)
    }

    @Test
    fun testResolveIsMaterialYou_forcedTrueOverridesSavedSystemThemeStyle() {
        // Placing the Material Search Bar (forcedIsMaterial = true) must return true
        val result = SearchWidgetProvider.resolveIsMaterialYou(
            appWidgetManager = null,
            appWidgetId = 102,
            forcedIsMaterial = true,
            widgetThemeStyle = "System Default"
        )
        assertTrue("Material search bar must remain Material You", result)
    }

    @Test
    fun testResolveIsMaterialYou_fallbackWhenForcedIsNull() {
        val resultMaterial = SearchWidgetProvider.resolveIsMaterialYou(
            appWidgetManager = null,
            appWidgetId = 103,
            forcedIsMaterial = null,
            widgetThemeStyle = "Material You (Minimal)"
        )
        assertTrue(resultMaterial)

        val resultSystem = SearchWidgetProvider.resolveIsMaterialYou(
            appWidgetManager = null,
            appWidgetId = 104,
            forcedIsMaterial = null,
            widgetThemeStyle = "System Default"
        )
        assertFalse(resultSystem)
    }
}
