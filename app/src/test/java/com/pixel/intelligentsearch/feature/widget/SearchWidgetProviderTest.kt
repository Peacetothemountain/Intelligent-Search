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

    @Test
    fun testDynamicThemeDetector_themeStateEnumIntegrity() {
        val states = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ThemeState.values()
        assertTrue(states.contains(com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ThemeState.DYNAMIC_ACTIVE))
        assertTrue(states.contains(com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ThemeState.DYNAMIC_DISABLED))
        assertTrue(states.contains(com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ThemeState.UNSUPPORTED))
    }

    @Test
    fun testResolvedWidgetColors_dataClassIntegrity() {
        val colors = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ResolvedWidgetColors(
            primary = 0xFF1973E8.toInt(),
            secondary = 0xFF5F6368.toInt(),
            tertiary = 0xFF188038.toInt(),
            primaryContainer = 0xFFD3E3FD.toInt(),
            surfaceContainer = 0xFFF1F3F4.toInt(),
            isDynamicActive = true
        )
        assertEquals(0xFF1973E8.toInt(), colors.primary)
        assertEquals(0xFF5F6368.toInt(), colors.secondary)
        assertEquals(0xFF188038.toInt(), colors.tertiary)
        assertEquals(0xFFD3E3FD.toInt(), colors.primaryContainer)
        assertEquals(0xFFF1F3F4.toInt(), colors.surfaceContainer)
        assertTrue(colors.isDynamicActive)
    }

    @Test
    fun testDynamicThemeDetector_jvmDefaultReturnsUnsupported() {
        val dummyContext = android.content.ContextWrapper(null)

        val state = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.getThemeState(dummyContext)
        assertEquals(com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.ThemeState.UNSUPPORTED, state)

        val isActive = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.isDynamicColorActive(dummyContext)
        assertFalse(isActive)
    }

    @Test
    fun testDynamicThemeDetector_resolveWidgetColorsFallback() {
        val dummyContext = android.content.ContextWrapper(null)

        val darkColors = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.resolveWidgetColors(dummyContext, isDark = true)
        assertFalse(darkColors.isDynamicActive)
        assertEquals(0xFF303134.toInt(), darkColors.surfaceContainer)
        assertEquals(0xFF8AB4F8.toInt(), darkColors.primary)
        assertEquals(0xFFBDC1C6.toInt(), darkColors.secondary)
        assertEquals(0xFF81C995.toInt(), darkColors.tertiary)
        assertEquals(0xFF1E3A8A.toInt(), darkColors.primaryContainer)

        val lightColors = com.pixel.intelligentsearch.core.theme.DynamicThemeDetector.resolveWidgetColors(dummyContext, isDark = false)
        assertFalse(lightColors.isDynamicActive)
        assertEquals(0xFFF1F3F4.toInt(), lightColors.surfaceContainer)
        assertEquals(0xFF1973E8.toInt(), lightColors.primary)
        assertEquals(0xFF5F6368.toInt(), lightColors.secondary)
        assertEquals(0xFF188038.toInt(), lightColors.tertiary)
        assertEquals(0xFFD3E3FD.toInt(), lightColors.primaryContainer)
    }
}
