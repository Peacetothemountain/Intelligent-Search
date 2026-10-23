package com.pixel.intelligentsearch.feature.widget

import com.pixel.intelligentsearch.core.data.widget.WidgetElementColors
import com.pixel.intelligentsearch.core.theme.SearchIconColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget picker shows the bar exactly as Widget Customization's preview draws it: every see-through part already
 * blended over the editor's page colour and drawn opaque, so the picker's own card can't show through and change it.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPickerPreviewTest {

    private val page = 0xFF110D10.toInt()
    private val rim = 0xFF6B2D5E.toInt()

    private fun config(backdrop: Float, rimAlpha: Float, pill: Int = 0xFF121212.toInt()) = SearchWidgetProvider.Companion.WidgetDesignConfig(
        isMaterialYou = true,
        rimColorOpaque = rim,
        pillColorOpaque = pill,
        circleColorOpaque = pill,
        backdrop = WidgetColorRules.BackdropAlphas(backdrop = backdrop, rim = rimAlpha),
        showGIcon = true,
        useOriginalG = false,
        themePColor = 0xFFFFB0E0.toInt(),
        iconAccents = SearchIconColors.Accents(0, 0, 0),
        effectiveIconTheme = "Material G Icon",
        actionIconStr = "Search",
        allActiveItems = emptyList(),
        isPillLight = false,
        customIcons = WidgetElementColors(0, 0, 0, 0, 0, 0),
        shape = WidgetBarShape.of(isMaterialDesign = true, outlineThickness = 8, cornerRadius = 24),
        plainIconTint = -1
    )

    @Test
    fun aSeeThroughOutlineIsDrawnAsTheEditorShowsItOverItsPage() {
        // Locked black pill, Transparency 72: the outline alone at 28%, the pill solid.
        val shown = SearchWidgetProvider.pickerPreviewConfig(config(backdrop = 1f, rimAlpha = 0.28f), page)
        assertEquals(WidgetColorRules.blendOver(rim, 0.28f, page), shown.rimColorOpaque)
        assertEquals(0xFF121212.toInt(), shown.pillColorOpaque)
        // Nothing left to see through.
        assertEquals(1f, shown.backdrop.backdrop, 0f)
        assertEquals(1f, shown.backdrop.rim, 0f)
    }

    @Test
    fun aWholeSeeThroughBarIsBlendedAsOneLikeTheEditorsGroup() {
        val pill = 0xFF443344.toInt()
        val shown = SearchWidgetProvider.pickerPreviewConfig(config(backdrop = 0.5f, rimAlpha = 1f, pill = pill), page)
        assertEquals(WidgetColorRules.blendOver(rim, 0.5f, page), shown.rimColorOpaque)
        assertEquals(WidgetColorRules.blendOver(pill, 0.5f, page), shown.pillColorOpaque)
        assertEquals(WidgetColorRules.blendOver(pill, 0.5f, page), shown.circleColorOpaque)
    }

    @Test
    fun blendingIsWhatTheEyeSees() {
        assertEquals(0xFF808080.toInt(), WidgetColorRules.blendOver(0xFFFFFFFF.toInt(), 0.5f, 0xFF000000.toInt()))
        assertEquals(0xFFFF0000.toInt(), WidgetColorRules.blendOver(0xFFFF0000.toInt(), 1f, 0xFF00FF00.toInt()))
        assertEquals(0xFF00FF00.toInt(), WidgetColorRules.blendOver(0xFFFF0000.toInt(), 0f, 0xFF00FF00.toInt()))
    }
}
