package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pixel.intelligentsearch.core.theme.DynamicThemeDetector
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The widget picker takes only a few previews an hour, so a preview is pushed again only when the widget it shows has
 * changed, and one the picker refused is pushed again next time. (Robolectric runs Android 14 here, which has no
 * setWidgetPreview, so the picker is stood in for.)
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPickerPreviewTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("picker_preview_test", Context.MODE_PRIVATE) }
    private val published = mutableListOf<String>()
    private var pickerAccepts = true

    @Before fun forgetEarlierPreviews() = SearchWidgetProvider.publishedPreviews.clear()

    private fun redraw() {
        val colors = DynamicThemeDetector.resolveWidgetColors(context, isDark = false)
        SearchWidgetProvider.updateWidgetPickerPreviews(context, prefs, isDark = false, widgetColors = colors) { provider, _ ->
            published += provider.className.substringAfterLast('.')
            pickerAccepts
        }
    }

    @Test
    fun anUnchangedWidgetIsNotPushedAgain() {
        redraw()
        redraw()
        assertEquals(listOf("SearchWidgetProvider", "SearchWidgetMaterialProvider"), published)
    }

    @Test
    fun aChangedDesignPushesOnlyItsOwnWidget() {
        redraw()
        published.clear()
        prefs.edit().putString("widget_system_subtheme", "Dark").commit()
        redraw()
        assertEquals(listOf("SearchWidgetProvider"), published)
    }

    @Test
    fun aRefusedPreviewIsPushedAgainNextTime() {
        pickerAccepts = false
        redraw()
        pickerAccepts = true
        redraw()
        redraw()
        // Both refused, both taken on the next redraw, then nothing new to push.
        assertEquals(4, published.size)
    }
}
